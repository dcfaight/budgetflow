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
    private static final double IMPORTANT_FALLBACK_LATENCY_RATIO = 0.52;
    private static final double OPTIONAL_DEGRADE_LATENCY_RATIO = 0.58;
    private static final double OPTIONAL_OMIT_LATENCY_RATIO = 0.78;
    private static final double MIN_DYNAMIC_LATENCY_RATIO = 0.20;
    private static final double MAX_DYNAMIC_LATENCY_RATIO = 0.95;
    private final OptionalTaskModeSelector optionalTaskModeSelector;
    private final String policyProfileName;
    private final OptionalTaskPlanningSignalsFactory planningSignalsFactory;
    private final PolicyReasonFormatter reasonFormatter;

    public DefaultBudgetPolicyEngine() {
        this(
            PlannerPolicyProfiles.optionalTaskSelector(PlannerPolicyProfile.defaultProfile()),
            PlannerPolicyProfile.defaultProfile().configName(),
            new OptionalTaskPlanningSignalsFactory(),
            new PolicyReasonFormatter()
        );
    }

    public DefaultBudgetPolicyEngine(PlannerPolicyProfile profile) {
        this(
            PlannerPolicyProfiles.optionalTaskSelector(profile),
            Objects.requireNonNull(profile, "profile must not be null").configName(),
            new OptionalTaskPlanningSignalsFactory(),
            new PolicyReasonFormatter()
        );
    }

    public DefaultBudgetPolicyEngine(OptionalTaskModeSelector optionalTaskModeSelector) {
        this(
            optionalTaskModeSelector,
            "custom",
            new OptionalTaskPlanningSignalsFactory(),
            new PolicyReasonFormatter()
        );
    }

    public DefaultBudgetPolicyEngine(
        OptionalTaskModeSelector optionalTaskModeSelector,
        String policyProfileName
    ) {
        this(optionalTaskModeSelector, policyProfileName, new OptionalTaskPlanningSignalsFactory(), new PolicyReasonFormatter());
    }

    DefaultBudgetPolicyEngine(
        OptionalTaskModeSelector optionalTaskModeSelector,
        String policyProfileName,
        OptionalTaskPlanningSignalsFactory planningSignalsFactory,
        PolicyReasonFormatter reasonFormatter
    ) {
        this.optionalTaskModeSelector = Objects.requireNonNull(
            optionalTaskModeSelector,
            "optionalTaskModeSelector must not be null"
        );
        this.policyProfileName = Objects.requireNonNull(policyProfileName, "policyProfileName must not be null");
        this.planningSignalsFactory = Objects.requireNonNull(
            planningSignalsFactory,
            "planningSignalsFactory must not be null"
        );
        this.reasonFormatter = Objects.requireNonNull(reasonFormatter, "reasonFormatter must not be null");
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
        PolicyPlanningSignals planningSignals = planningSignalsFactory.analyze(
            task,
            snapshot,
            remainingBudget,
            OPTIONAL_DEGRADE_LATENCY_RATIO,
            OPTIONAL_OMIT_LATENCY_RATIO
        );
        TaskCostSignals costSignals = planningSignals.taskCostSignals();
        double latencyRatio = costSignals.primaryLatencyRatio();
        if (task.importance() == Importance.MANDATORY) {
            return new PolicySelection(
                ExecutionMode.EXECUTE,
                reasonFormatter.format(
                    policyProfileName,
                    ExecutionMode.EXECUTE,
                    snapshot,
                    remainingBudget,
                    latencyRatio,
                    planningSignals.stressedSignalCount(),
                    planningSignals.mixedConstraintBand(),
                    planningSignals.suggestedDegradedMode()
                )
            );
        }

        if (task.importance() == Importance.IMPORTANT) {
            double importantFallbackThreshold = adjustedLatencyThreshold(
                IMPORTANT_FALLBACK_LATENCY_RATIO,
                snapshot.peakPressure(),
                planningSignals.lowBudget(),
                planningSignals.veryLowBudget()
            );
            boolean importantStress = planningSignals.highPressure()
                || planningSignals.lowBudget()
                || !costSignals.primaryFitsBudget()
                || latencyRatio >= importantFallbackThreshold
                || costSignals.primaryHeadroomMillis() < 20;
            boolean fallbackClearlyCheaper = costSignals.fallbackLatencyRatio() <= latencyRatio;
            boolean fallbackImprovesBudgetFit = costSignals.fallbackFitsBudget() && !costSignals.primaryFitsBudget();
            ExecutionMode mode = importantStress
                && task.fallbackSupported()
                && (fallbackClearlyCheaper
                || fallbackImprovesBudgetFit
                || planningSignals.highPressure()
                || planningSignals.lowBudget())
                ? ExecutionMode.EXECUTE_WITH_FALLBACK
                : ExecutionMode.EXECUTE;
            return new PolicySelection(
                mode,
                reasonFormatter.format(
                    policyProfileName,
                    mode,
                    snapshot,
                    remainingBudget,
                    latencyRatio,
                    planningSignals.stressedSignalCount(),
                    planningSignals.mixedConstraintBand(),
                    planningSignals.suggestedDegradedMode()
                )
            );
        }

        ExecutionMode mode = Objects.requireNonNull(
            optionalTaskModeSelector.chooseMode(
                task,
                planningSignals.optionalTaskPlanningContext(task, snapshot, remainingBudget)
            ),
            "optionalTaskModeSelector must return a mode"
        );
        return new PolicySelection(
            mode,
            reasonFormatter.format(
                policyProfileName,
                mode,
                snapshot,
                remainingBudget,
                latencyRatio,
                planningSignals.stressedSignalCount(),
                planningSignals.mixedConstraintBand(),
                planningSignals.suggestedDegradedMode()
            )
        );
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

    private record PolicySelection(ExecutionMode mode, String reason) {
    }
}
