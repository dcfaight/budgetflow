package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

public final class DefaultOptionalTaskModeSelector implements OptionalTaskModeSelector {
    private static final double OPTIONAL_EXTREME_OMIT_LATENCY_RATIO = 0.90;

    @Override
    public ExecutionMode chooseMode(TaskDescriptor task, OptionalTaskPlanningContext context) {
        boolean severeBudgetOrPressure = context.veryLowBudget() || context.highPressure();
        boolean mixedConstraintPressure = context.mixedConstraintScore() >= 0.95;
        boolean primaryDoesNotFitBudget = !context.primaryFitsBudget();
        boolean degradedPathRecoversBudgetFit = context.degradedPathAvailable() && context.cheapestDegradedFitsBudget();
        boolean noPathFitsBudget = primaryDoesNotFitBudget && !degradedPathRecoversBudgetFit;
        boolean degradedPathProtectsHeadroom = context.degradedPathAvailable()
            && !context.highPressure()
            && context.cheapestDegradedLatencyRatio() < context.optionalOmitThreshold();
        boolean stressConditions = severeBudgetOrPressure
            || context.multiSignalStress()
            || context.lowBudget()
            || primaryDoesNotFitBudget
            || context.primaryLatencyRatio() >= context.optionalDegradeThreshold();
        boolean degradeUnderStress = stressConditions || mixedConstraintPressure;
        boolean omitDueToExtremeRatio = severeBudgetOrPressure
            && context.primaryLatencyRatio() >= context.optionalOmitThreshold()
            && !degradedPathProtectsHeadroom;
        boolean omitDueToStackedSignals = context.multiSignalStress()
            && context.primaryLatencyRatio() >= context.optionalOmitThreshold()
            && !context.degradedPathAvailable();
        boolean omitDueToNoPathFit = noPathFitsBudget
            && (severeBudgetOrPressure
            || context.multiSignalStress()
            || context.primaryLatencyRatio() >= context.optionalOmitThreshold());
        boolean omitDueToNoDegradedPath = context.primaryLatencyRatio() >= OPTIONAL_EXTREME_OMIT_LATENCY_RATIO
            && !task.approximateSupported()
            && !task.fallbackSupported();

        if (omitDueToExtremeRatio || omitDueToStackedSignals || omitDueToNoDegradedPath || omitDueToNoPathFit) {
            return ExecutionMode.OMIT;
        }

        if (degradeUnderStress) {
            if (!context.degradedPathAvailable()
                && (context.highPressure()
                || context.multiSignalStress()
                || context.veryLowBudget()
                || context.primaryLatencyRatio() >= context.optionalOmitThreshold())) {
                return ExecutionMode.OMIT;
            }
            return context.suggestedDegradedMode(task);
        }

        if ((severeBudgetOrPressure || context.primaryLatencyRatio() >= context.optionalOmitThreshold())
            && !degradedPathProtectsHeadroom) {
            return ExecutionMode.OMIT;
        }

        return ExecutionMode.EXECUTE;
    }
}
