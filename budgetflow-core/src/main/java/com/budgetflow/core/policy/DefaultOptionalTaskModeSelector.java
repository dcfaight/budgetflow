package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

public final class DefaultOptionalTaskModeSelector implements OptionalTaskModeSelector {
    private static final double OPTIONAL_EXTREME_OMIT_LATENCY_RATIO = 0.90;

    @Override
    public ExecutionMode chooseMode(TaskDescriptor task, OptionalTaskPlanningContext context) {
        boolean severeBudgetOrPressure = context.veryLowBudget() || context.highPressure();
        boolean degradedPathProtectsHeadroom = context.degradedPathAvailable()
            && !context.highPressure()
            && context.cheapestDegradedLatencyRatio() < context.optionalOmitThreshold();
        boolean stressConditions = severeBudgetOrPressure
            || context.lowBudget()
            || context.primaryLatencyRatio() >= context.optionalDegradeThreshold();
        boolean omitDueToExtremeRatio = severeBudgetOrPressure
            && context.primaryLatencyRatio() >= context.optionalOmitThreshold()
            && !degradedPathProtectsHeadroom;
        boolean omitDueToNoDegradedPath = context.primaryLatencyRatio() >= OPTIONAL_EXTREME_OMIT_LATENCY_RATIO
            && !task.approximateSupported()
            && !task.fallbackSupported();

        if (omitDueToExtremeRatio || omitDueToNoDegradedPath) {
            return ExecutionMode.OMIT;
        }

        if (stressConditions && task.approximateSupported()) {
            return ExecutionMode.EXECUTE_APPROXIMATE;
        }

        if (stressConditions && task.fallbackSupported()) {
            return ExecutionMode.EXECUTE_WITH_FALLBACK;
        }

        if ((severeBudgetOrPressure || context.primaryLatencyRatio() >= context.optionalOmitThreshold())
            && !degradedPathProtectsHeadroom) {
            return ExecutionMode.OMIT;
        }

        return ExecutionMode.EXECUTE;
    }
}
