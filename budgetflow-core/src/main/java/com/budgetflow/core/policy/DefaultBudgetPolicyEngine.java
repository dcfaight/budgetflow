package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DefaultBudgetPolicyEngine implements BudgetPolicyEngine {
    private static final double HIGH_PRESSURE = 0.85;
    private static final double MODERATE_PRESSURE = 0.60;
    private static final Duration LOW_BUDGET_THRESHOLD = Duration.ofMillis(200);
    private static final Duration VERY_LOW_BUDGET_THRESHOLD = Duration.ofMillis(120);

    @Override
    public PolicyDecision evaluate(PolicyEvaluationInput input) {
        List<TaskExecutionDirective> directives = new ArrayList<>();
        List<String> degradationReasons = new ArrayList<>();
        List<DecisionTraceEntry> decisionTrace = new ArrayList<>();

        List<TaskDescriptor> mandatoryTasks = tasksByImportance(input.tasks(), Importance.MANDATORY);
        List<TaskDescriptor> importantTasks = tasksByImportance(input.tasks(), Importance.IMPORTANT);
        List<TaskDescriptor> optionalTasks = tasksByImportance(input.tasks(), Importance.OPTIONAL);

        Duration mandatoryReserve = reserveMandatoryBudget(mandatoryTasks, input.remainingBudget());
        Duration remainingMandatoryReserve = planTasks(
            mandatoryTasks,
            mandatoryReserve,
            input.pressureSnapshot(),
            directives,
            degradationReasons,
            decisionTrace
        );

        Duration discretionaryBudget = nonNegative(input.remainingBudget()
            .minus(mandatoryReserve)
            .plus(remainingMandatoryReserve));

        Duration remainingAfterImportant = planTasks(
            importantTasks,
            discretionaryBudget,
            input.pressureSnapshot(),
            directives,
            degradationReasons,
            decisionTrace
        );

        planTasks(
            optionalTasks,
            remainingAfterImportant,
            input.pressureSnapshot(),
            directives,
            degradationReasons,
            decisionTrace
        );

        return new PolicyDecision(directives, !degradationReasons.isEmpty(), degradationReasons, decisionTrace);
    }

    private Duration planTasks(
        List<TaskDescriptor> tasks,
        Duration classBudget,
        SystemPressureSnapshot snapshot,
        List<TaskExecutionDirective> directives,
        List<String> degradationReasons,
        List<DecisionTraceEntry> decisionTrace
    ) {
        Duration rollingRemainingBudget = nonNegative(classBudget);
        int totalTasks = tasks.size();
        int index = 0;

        for (TaskDescriptor task : tasks) {
            int tasksRemaining = Math.max(totalTasks - index, 1);
            Duration perTaskBudget = rollingRemainingBudget.dividedBy(tasksRemaining);
            Duration budgetAtPlanningTime = rollingRemainingBudget;
            PolicySelection selection = chooseExecutionMode(task, snapshot, budgetAtPlanningTime);
            ExecutionMode mode = selection.mode();
            Duration allocated = mode == ExecutionMode.OMIT ? Duration.ZERO : minPositive(expectedLatencyOrZero(task), perTaskBudget);
            boolean omitted = mode == ExecutionMode.OMIT;
            boolean degraded = mode != ExecutionMode.EXECUTE;
            String reason = selection.reason();

            if (degraded) {
                degradationReasons.add(task.taskName() + ": " + mode);
            }

            directives.add(new TaskExecutionDirective(
                task.taskName(),
                mode,
                allocated,
                omitted,
                reason
            ));

            decisionTrace.add(new DecisionTraceEntry(
                task.taskName(),
                task.importance(),
                mode,
                reason,
                expectedLatencyOrZero(task),
                allocated,
                budgetAtPlanningTime
            ));

            rollingRemainingBudget = nonNegative(rollingRemainingBudget.minus(allocated));
            index++;
        }

        return rollingRemainingBudget;
    }

    private List<TaskDescriptor> tasksByImportance(List<TaskDescriptor> tasks, Importance importance) {
        return tasks.stream()
            .filter(task -> task.importance() == importance)
            .toList();
    }

    private Duration reserveMandatoryBudget(List<TaskDescriptor> mandatoryTasks, Duration totalBudget) {
        Duration mandatoryExpected = Duration.ZERO;
        for (TaskDescriptor task : mandatoryTasks) {
            mandatoryExpected = mandatoryExpected.plus(expectedLatencyOrZero(task));
        }
        return minPositive(nonNegative(totalBudget), mandatoryExpected);
    }

    private PolicySelection chooseExecutionMode(
        TaskDescriptor task,
        SystemPressureSnapshot snapshot,
        Duration remainingBudget
    ) {
        Duration expectedLatency = expectedLatencyOrZero(task);
        long remainingMillis = Math.max(remainingBudget.toMillis(), 1L);
        long expectedMillis = Math.max(expectedLatency.toMillis(), 0L);
        double latencyRatio = (double) expectedMillis / (double) remainingMillis;

        double pressureLevel = snapshot.peakPressure();
        if (task.importance() == Importance.MANDATORY) {
            return new PolicySelection(ExecutionMode.EXECUTE, explainReason(ExecutionMode.EXECUTE, snapshot, remainingBudget, latencyRatio));
        }

        boolean highPressure = pressureLevel >= HIGH_PRESSURE;
        boolean moderatePressure = pressureLevel >= MODERATE_PRESSURE;
        boolean lowBudget = remainingBudget.compareTo(LOW_BUDGET_THRESHOLD) < 0;
        boolean veryLowBudget = remainingBudget.compareTo(VERY_LOW_BUDGET_THRESHOLD) < 0;

        if (task.importance() == Importance.IMPORTANT) {
            ExecutionMode mode = (highPressure || lowBudget || latencyRatio >= 0.35) && task.fallbackSupported()
                ? ExecutionMode.EXECUTE_WITH_FALLBACK
                : ExecutionMode.EXECUTE;
            return new PolicySelection(mode, explainReason(mode, snapshot, remainingBudget, latencyRatio));
        }

        if (veryLowBudget || highPressure || latencyRatio >= 0.55) {
            return new PolicySelection(ExecutionMode.OMIT, explainReason(ExecutionMode.OMIT, snapshot, remainingBudget, latencyRatio));
        }

        if ((moderatePressure || lowBudget || latencyRatio >= 0.45) && task.approximateSupported()) {
            return new PolicySelection(
                ExecutionMode.EXECUTE_APPROXIMATE,
                explainReason(ExecutionMode.EXECUTE_APPROXIMATE, snapshot, remainingBudget, latencyRatio)
            );
        }

        if ((moderatePressure || lowBudget || latencyRatio >= 0.45) && task.fallbackSupported()) {
            return new PolicySelection(
                ExecutionMode.EXECUTE_WITH_FALLBACK,
                explainReason(ExecutionMode.EXECUTE_WITH_FALLBACK, snapshot, remainingBudget, latencyRatio)
            );
        }

        return new PolicySelection(ExecutionMode.EXECUTE, explainReason(ExecutionMode.EXECUTE, snapshot, remainingBudget, latencyRatio));
    }

    private String explainReason(
        ExecutionMode mode,
        SystemPressureSnapshot snapshot,
        Duration remainingBudget,
        double latencyRatio
    ) {
        String pressureBand = pressureBand(snapshot.peakPressure());
        String budgetBand = budgetBand(remainingBudget);
        String ratio = String.format("%.2f", latencyRatio);
        return switch (mode) {
            case EXECUTE -> "normal[pressure=%s:%s,budget=%s,latency_ratio=%s]"
                .formatted(pressureBand, snapshot.dominantSignal(), budgetBand, ratio);
            case EXECUTE_WITH_FALLBACK -> "fallback_selected_by_policy[pressure=%s:%s,budget=%s,latency_ratio=%s]"
                .formatted(pressureBand, snapshot.dominantSignal(), budgetBand, ratio);
            case EXECUTE_APPROXIMATE -> "approximate_selected_by_policy[pressure=%s:%s,budget=%s,latency_ratio=%s]"
                .formatted(pressureBand, snapshot.dominantSignal(), budgetBand, ratio);
            case OMIT -> "omitted_by_policy[pressure=%s:%s,budget=%s,latency_ratio=%s]"
                .formatted(pressureBand, snapshot.dominantSignal(), budgetBand, ratio);
        };
    }

    private String pressureBand(double pressureLevel) {
        if (pressureLevel >= HIGH_PRESSURE) {
            return "high";
        }
        if (pressureLevel >= MODERATE_PRESSURE) {
            return "moderate";
        }
        return "low";
    }

    private String budgetBand(Duration remainingBudget) {
        if (remainingBudget.compareTo(VERY_LOW_BUDGET_THRESHOLD) < 0) {
            return "very_low";
        }
        if (remainingBudget.compareTo(LOW_BUDGET_THRESHOLD) < 0) {
            return "tight";
        }
        return "available";
    }

    private Duration minPositive(Duration first, Duration second) {
        if (first == null || first.isZero() || first.isNegative()) {
            return second.isNegative() ? Duration.ZERO : second;
        }
        if (second == null || second.isZero() || second.isNegative()) {
            return first;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }

    private Duration expectedLatencyOrZero(TaskDescriptor task) {
        return task.expectedLatency() == null || task.expectedLatency().isNegative() ? Duration.ZERO : task.expectedLatency();
    }

    private Duration nonNegative(Duration value) {
        return value == null || value.isNegative() ? Duration.ZERO : value;
    }

    private record PolicySelection(ExecutionMode mode, String reason) {
    }
}
