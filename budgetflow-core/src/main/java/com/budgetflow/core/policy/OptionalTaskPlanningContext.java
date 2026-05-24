package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

import java.time.Duration;

/**
 * Request-scoped planning signals passed to {@link OptionalTaskModeSelector}.
 * <p>
 * The planner computes this context before selector invocation so selectors can
 * focus on policy choice rather than recomputing cost, budget-fit, or pressure
 * analysis.
 */
public record OptionalTaskPlanningContext(
    SystemPressureSnapshot pressureSnapshot,
    Duration remainingBudget,
    int stressedSignalCount,
    boolean multiSignalStress,
    double averagePressure,
    double mixedConstraintScore,
    double primaryLatencyRatio,
    double cheapestDegradedLatencyRatio,
    double degradedSavingsRatio,
    long degradedSavingsMillis,
    boolean degradedPathAvailable,
    boolean primaryFitsBudget,
    boolean fallbackFitsBudget,
    boolean approximateFitsBudget,
    boolean cheapestDegradedFitsBudget,
    long primaryOverrunMillis,
    long fallbackOverrunMillis,
    long approximateOverrunMillis,
    long cheapestDegradedOverrunMillis,
    boolean lowBudget,
    boolean veryLowBudget,
    boolean highPressure,
    String mixedConstraintBand,
    ExecutionMode suggestedDegradedMode,
    double optionalDegradeThreshold,
    double optionalOmitThreshold
) {
    public ExecutionMode degradedMode(TaskDescriptor task, boolean preferFallback) {
        if (preferFallback) {
            if (task.fallbackSupported()) {
                return ExecutionMode.EXECUTE_WITH_FALLBACK;
            }
            if (task.approximateSupported()) {
                return ExecutionMode.EXECUTE_APPROXIMATE;
            }
            return ExecutionMode.EXECUTE;
        }
        if (task.approximateSupported()) {
            return ExecutionMode.EXECUTE_APPROXIMATE;
        }
        if (task.fallbackSupported()) {
            return ExecutionMode.EXECUTE_WITH_FALLBACK;
        }
        return ExecutionMode.EXECUTE;
    }

    public ExecutionMode suggestedDegradedMode(TaskDescriptor task) {
        if (suggestedDegradedMode == ExecutionMode.EXECUTE_WITH_FALLBACK && task.fallbackSupported()) {
            return ExecutionMode.EXECUTE_WITH_FALLBACK;
        }
        if (suggestedDegradedMode == ExecutionMode.EXECUTE_APPROXIMATE && task.approximateSupported()) {
            return ExecutionMode.EXECUTE_APPROXIMATE;
        }
        return degradedMode(task, false);
    }
}
