package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DefaultBudgetPolicyEngine implements BudgetPolicyEngine {
    private static final double HIGH_PRESSURE = 0.85;
    private static final double MODERATE_PRESSURE = 0.60;

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
            ExecutionMode mode = chooseExecutionMode(task, snapshot, budgetAtPlanningTime);
            Duration allocated = mode == ExecutionMode.OMIT ? Duration.ZERO : minPositive(expectedLatencyOrZero(task), perTaskBudget);
            boolean omitted = mode == ExecutionMode.OMIT;
            boolean degraded = mode != ExecutionMode.EXECUTE;
            String reason = reasonFor(mode);

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

    private ExecutionMode chooseExecutionMode(
        TaskDescriptor task,
        SystemPressureSnapshot snapshot,
        Duration remainingBudget
    ) {
        Duration expectedLatency = expectedLatencyOrZero(task);
        long remainingMillis = Math.max(remainingBudget.toMillis(), 1L);
        long expectedMillis = Math.max(expectedLatency.toMillis(), 0L);
        double latencyRatio = (double) expectedMillis / (double) remainingMillis;

        double pressureLevel = Math.max(snapshot.executorUtilization(), Math.max(snapshot.dbPressure(), snapshot.downstreamPressure()));
        if (task.importance() == Importance.MANDATORY) {
            return ExecutionMode.EXECUTE;
        }

        boolean highPressure = pressureLevel >= HIGH_PRESSURE;
        boolean moderatePressure = pressureLevel >= MODERATE_PRESSURE;
        boolean lowBudget = remainingBudget.compareTo(Duration.ofMillis(200)) < 0;
        boolean veryLowBudget = remainingBudget.compareTo(Duration.ofMillis(120)) < 0;

        if (task.importance() == Importance.IMPORTANT) {
            return (highPressure || lowBudget || latencyRatio >= 0.35) && task.fallbackSupported()
                ? ExecutionMode.EXECUTE_WITH_FALLBACK
                : ExecutionMode.EXECUTE;
        }

        if (veryLowBudget || highPressure || latencyRatio >= 0.55) {
            return ExecutionMode.OMIT;
        }

        if ((moderatePressure || lowBudget || latencyRatio >= 0.45) && task.approximateSupported()) {
            return ExecutionMode.EXECUTE_APPROXIMATE;
        }

        if ((moderatePressure || lowBudget || latencyRatio >= 0.45) && task.fallbackSupported()) {
            return ExecutionMode.EXECUTE_WITH_FALLBACK;
        }

        return ExecutionMode.EXECUTE;
    }

    private String reasonFor(ExecutionMode mode) {
        return switch (mode) {
            case EXECUTE -> "normal";
            case EXECUTE_WITH_FALLBACK -> "fallback_selected_by_policy";
            case EXECUTE_APPROXIMATE -> "approximate_selected_by_policy";
            case OMIT -> "omitted_by_policy";
        };
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
}
