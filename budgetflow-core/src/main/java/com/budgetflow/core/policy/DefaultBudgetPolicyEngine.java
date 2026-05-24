package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefaultBudgetPolicyEngine implements BudgetPolicyEngine {
    private static final double HIGH_PRESSURE = 0.85;
    private static final double MODERATE_PRESSURE = 0.60;
    private static final double STACKED_SIGNAL_PRESSURE = 0.70;
    private static final double IMPORTANT_FALLBACK_LATENCY_RATIO = 0.52;
    private static final double OPTIONAL_DEGRADE_LATENCY_RATIO = 0.58;
    private static final double OPTIONAL_OMIT_LATENCY_RATIO = 0.78;
    private static final double MIN_DYNAMIC_LATENCY_RATIO = 0.20;
    private static final double MAX_DYNAMIC_LATENCY_RATIO = 0.95;
    private static final Duration LOW_BUDGET_THRESHOLD = Duration.ofMillis(200);
    private static final Duration VERY_LOW_BUDGET_THRESHOLD = Duration.ofMillis(120);
    private final OptionalTaskModeSelector optionalTaskModeSelector;
    private final String policyProfileName;

    public DefaultBudgetPolicyEngine() {
        this(
            PlannerPolicyProfiles.optionalTaskSelector(PlannerPolicyProfile.defaultProfile()),
            PlannerPolicyProfile.defaultProfile().configName()
        );
    }

    public DefaultBudgetPolicyEngine(PlannerPolicyProfile profile) {
        this(
            PlannerPolicyProfiles.optionalTaskSelector(profile),
            Objects.requireNonNull(profile, "profile must not be null").configName()
        );
    }

    public DefaultBudgetPolicyEngine(OptionalTaskModeSelector optionalTaskModeSelector) {
        this(optionalTaskModeSelector, "custom");
    }

    public DefaultBudgetPolicyEngine(
        OptionalTaskModeSelector optionalTaskModeSelector,
        String policyProfileName
    ) {
        this.optionalTaskModeSelector = Objects.requireNonNull(
            optionalTaskModeSelector,
            "optionalTaskModeSelector must not be null"
        );
        this.policyProfileName = Objects.requireNonNull(policyProfileName, "policyProfileName must not be null");
    }

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
            Duration plannedLatency = plannedLatency(task, mode);
            Duration allocated = mode == ExecutionMode.OMIT ? Duration.ZERO : minPositive(plannedLatency, perTaskBudget);
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
                plannedLatency,
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
        TaskCostSignals costSignals = taskCostSignals(task, remainingBudget);
        double latencyRatio = costSignals.primaryLatencyRatio();

        double pressureLevel = snapshot.peakPressure();
        int stressedSignalCount = snapshot.signalsAtOrAbove(STACKED_SIGNAL_PRESSURE);
        boolean multiSignalStress = stressedSignalCount >= 2;
        if (task.importance() == Importance.MANDATORY) {
            return new PolicySelection(
                ExecutionMode.EXECUTE,
                explainReason(ExecutionMode.EXECUTE, snapshot, remainingBudget, latencyRatio, stressedSignalCount)
            );
        }

        boolean highPressure = pressureLevel >= HIGH_PRESSURE || multiSignalStress;
        boolean lowBudget = remainingBudget.compareTo(LOW_BUDGET_THRESHOLD) < 0;
        boolean veryLowBudget = remainingBudget.compareTo(VERY_LOW_BUDGET_THRESHOLD) < 0;
        double mixedConstraintScore = mixedConstraintScore(
            pressureLevel,
            multiSignalStress,
            lowBudget,
            veryLowBudget,
            latencyRatio
        );

        if (task.importance() == Importance.IMPORTANT) {
            double importantFallbackThreshold = adjustedLatencyThreshold(
                IMPORTANT_FALLBACK_LATENCY_RATIO,
                pressureLevel,
                lowBudget,
                veryLowBudget
            );
            boolean importantStress = highPressure
                || lowBudget
                || !costSignals.primaryFitsBudget()
                || latencyRatio >= importantFallbackThreshold
                || costSignals.primaryHeadroomMillis() < 20;
            boolean fallbackClearlyCheaper = costSignals.fallbackLatencyRatio() <= latencyRatio;
            boolean fallbackImprovesBudgetFit = costSignals.fallbackFitsBudget() && !costSignals.primaryFitsBudget();
            ExecutionMode mode = importantStress
                && task.fallbackSupported()
                && (fallbackClearlyCheaper || fallbackImprovesBudgetFit || highPressure || lowBudget)
                ? ExecutionMode.EXECUTE_WITH_FALLBACK
                : ExecutionMode.EXECUTE;
            return new PolicySelection(mode, explainReason(mode, snapshot, remainingBudget, latencyRatio, stressedSignalCount));
        }

        double optionalDegradeThreshold = adjustedLatencyThreshold(
            OPTIONAL_DEGRADE_LATENCY_RATIO,
            pressureLevel,
            lowBudget,
            veryLowBudget
        );
        double optionalOmitThreshold = adjustedLatencyThreshold(
            OPTIONAL_OMIT_LATENCY_RATIO,
            pressureLevel,
            lowBudget,
            veryLowBudget
        );
        ExecutionMode mode = Objects.requireNonNull(
            optionalTaskModeSelector.chooseMode(
                task,
                new OptionalTaskPlanningContext(
                    snapshot,
                    remainingBudget,
                    stressedSignalCount,
                    multiSignalStress,
                    snapshot.averagePressure(),
                    mixedConstraintScore,
                    latencyRatio,
                    costSignals.cheapestDegradedLatencyRatio(),
                    costSignals.degradedSavingsRatio(),
                    costSignals.degradedSavingsMillis(),
                    costSignals.degradedPathAvailable(),
                    costSignals.primaryFitsBudget(),
                    costSignals.cheapestDegradedFitsBudget(),
                    costSignals.primaryOverrunMillis(),
                    costSignals.cheapestDegradedOverrunMillis(),
                    lowBudget,
                    veryLowBudget,
                    highPressure,
                    optionalDegradeThreshold,
                    optionalOmitThreshold
                )
            ),
            "optionalTaskModeSelector must return a mode"
        );
        return new PolicySelection(mode, explainReason(mode, snapshot, remainingBudget, latencyRatio, stressedSignalCount));
    }

    private String explainReason(
        ExecutionMode mode,
        SystemPressureSnapshot snapshot,
        Duration remainingBudget,
        double latencyRatio,
        int stressedSignalCount
    ) {
        String pressureBand = pressureBand(snapshot.peakPressure());
        String budgetBand = budgetBand(remainingBudget);
        String ratio = String.format("%.2f", latencyRatio);
        return switch (mode) {
            case EXECUTE -> "normal[policy=%s,pressure=%s:%s,active_signals=%d,budget=%s,latency_ratio=%s]"
                .formatted(policyProfileName, pressureBand, snapshot.dominantSignal(), stressedSignalCount, budgetBand, ratio);
            case EXECUTE_WITH_FALLBACK ->
                "fallback_selected_by_policy[policy=%s,pressure=%s:%s,active_signals=%d,budget=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        budgetBand,
                        ratio
                    );
            case EXECUTE_APPROXIMATE ->
                "approximate_selected_by_policy[policy=%s,pressure=%s:%s,active_signals=%d,budget=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        budgetBand,
                        ratio
                    );
            case OMIT -> "omitted_by_policy[policy=%s,pressure=%s:%s,active_signals=%d,budget=%s,latency_ratio=%s]"
                .formatted(policyProfileName, pressureBand, snapshot.dominantSignal(), stressedSignalCount, budgetBand, ratio);
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

    private double adjustedLatencyThreshold(
        double baseThreshold,
        double pressureLevel,
        boolean lowBudget,
        boolean veryLowBudget
    ) {
        double pressureAdjustment = pressureLevel >= HIGH_PRESSURE
            ? -0.20
            : pressureLevel >= MODERATE_PRESSURE ? -0.10 : 0.10;
        double budgetAdjustment = veryLowBudget
            ? -0.18
            : lowBudget ? -0.10 : 0.04;
        double adjusted = baseThreshold + pressureAdjustment + budgetAdjustment;
        return Math.max(MIN_DYNAMIC_LATENCY_RATIO, Math.min(MAX_DYNAMIC_LATENCY_RATIO, adjusted));
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

    private Duration plannedLatency(TaskDescriptor task, ExecutionMode executionMode) {
        return switch (executionMode) {
            case EXECUTE -> expectedLatencyOrZero(task);
            case EXECUTE_WITH_FALLBACK -> latencyOrPrimary(task.fallbackExpectedLatency(), task);
            case EXECUTE_APPROXIMATE -> latencyOrPrimary(task.approximateExpectedLatency(), task);
            case OMIT -> Duration.ZERO;
        };
    }

    private Duration latencyOrPrimary(Duration candidate, TaskDescriptor task) {
        if (candidate == null || candidate.isNegative() || candidate.isZero()) {
            return expectedLatencyOrZero(task);
        }
        return candidate;
    }

    private Duration nonNegative(Duration value) {
        return value == null || value.isNegative() ? Duration.ZERO : value;
    }

    private TaskCostSignals taskCostSignals(TaskDescriptor task, Duration remainingBudget) {
        Duration primaryLatency = expectedLatencyOrZero(task);
        Duration fallbackLatency = latencyOrPrimary(task.fallbackExpectedLatency(), task);
        Duration approximateLatency = latencyOrPrimary(task.approximateExpectedLatency(), task);
        Duration cheapestDegradedLatency = task.fallbackSupported() && task.approximateSupported()
            ? minPositive(fallbackLatency, approximateLatency)
            : task.fallbackSupported()
                ? fallbackLatency
                : task.approximateSupported() ? approximateLatency : primaryLatency;
        double primaryRatio = latencyRatio(primaryLatency, remainingBudget);
        double fallbackRatio = latencyRatio(fallbackLatency, remainingBudget);
        double approximateRatio = latencyRatio(approximateLatency, remainingBudget);
        double degradedRatio = latencyRatio(cheapestDegradedLatency, remainingBudget);
        long savingsMillis = Math.max(primaryLatency.toMillis() - cheapestDegradedLatency.toMillis(), 0L);
        double savingsRatio = primaryLatency.isZero()
            ? 0.0
            : (double) savingsMillis / Math.max(primaryLatency.toMillis(), 1L);
        long primaryHeadroomMillis = Math.max(remainingBudget.toMillis() - primaryLatency.toMillis(), 0L);
        boolean primaryFitsBudget = fitsBudget(primaryLatency, remainingBudget);
        boolean fallbackFitsBudget = fitsBudget(fallbackLatency, remainingBudget);
        boolean approximateFitsBudget = fitsBudget(approximateLatency, remainingBudget);
        boolean cheapestDegradedFitsBudget = fitsBudget(cheapestDegradedLatency, remainingBudget);
        long primaryOverrunMillis = Math.max(primaryLatency.toMillis() - nonNegative(remainingBudget).toMillis(), 0L);
        long degradedOverrunMillis = Math.max(
            cheapestDegradedLatency.toMillis() - nonNegative(remainingBudget).toMillis(),
            0L
        );

        return new TaskCostSignals(
            primaryRatio,
            fallbackRatio,
            approximateRatio,
            degradedRatio,
            savingsMillis,
            savingsRatio,
            primaryHeadroomMillis,
            task.fallbackSupported() || task.approximateSupported(),
            primaryFitsBudget,
            fallbackFitsBudget,
            approximateFitsBudget,
            cheapestDegradedFitsBudget,
            primaryOverrunMillis,
            degradedOverrunMillis
        );
    }

    private double latencyRatio(Duration latency, Duration remainingBudget) {
        long remainingMillis = Math.max(nonNegative(remainingBudget).toMillis(), 1L);
        long latencyMillis = Math.max(nonNegative(latency).toMillis(), 0L);
        return (double) latencyMillis / (double) remainingMillis;
    }

    private boolean fitsBudget(Duration latency, Duration remainingBudget) {
        return nonNegative(latency).compareTo(nonNegative(remainingBudget)) <= 0;
    }

    private double mixedConstraintScore(
        double pressureLevel,
        boolean multiSignalStress,
        boolean lowBudget,
        boolean veryLowBudget,
        double latencyRatio
    ) {
        double pressureContribution = pressureLevel * 0.45;
        double signalContribution = multiSignalStress ? 0.20 : 0.0;
        double budgetContribution = veryLowBudget ? 0.20 : lowBudget ? 0.15 : 0.0;
        double ratioContribution = Math.min(latencyRatio, 1.5) * 0.20;
        double rawScore = pressureContribution + signalContribution + budgetContribution + ratioContribution;
        return Math.max(0.0, Math.min(rawScore, 1.5));
    }

    private record TaskCostSignals(
        double primaryLatencyRatio,
        double fallbackLatencyRatio,
        double approximateLatencyRatio,
        double cheapestDegradedLatencyRatio,
        long degradedSavingsMillis,
        double degradedSavingsRatio,
        long primaryHeadroomMillis,
        boolean degradedPathAvailable,
        boolean primaryFitsBudget,
        boolean fallbackFitsBudget,
        boolean approximateFitsBudget,
        boolean cheapestDegradedFitsBudget,
        long primaryOverrunMillis,
        long cheapestDegradedOverrunMillis
    ) {
    }

    private record PolicySelection(ExecutionMode mode, String reason) {
    }
}
