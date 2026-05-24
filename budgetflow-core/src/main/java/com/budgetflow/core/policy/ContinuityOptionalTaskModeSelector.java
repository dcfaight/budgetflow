package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

public final class ContinuityOptionalTaskModeSelector implements OptionalTaskModeSelector {
    private static final double OPTIONAL_EXTREME_OMIT_LATENCY_RATIO = 0.92;

    @Override
    public ExecutionMode chooseMode(TaskDescriptor task, OptionalTaskPlanningContext context) {
        boolean stressConditions = context.highPressure()
            || context.multiSignalStress()
            || context.lowBudget()
            || context.primaryLatencyRatio() >= context.optionalDegradeThreshold();
        boolean omitDueToNoDegradedPath = context.primaryLatencyRatio() >= OPTIONAL_EXTREME_OMIT_LATENCY_RATIO
            && !task.approximateSupported()
            && !task.fallbackSupported();
        boolean severeJointStress = context.highPressure()
            && context.veryLowBudget()
            && context.primaryLatencyRatio() >= context.optionalOmitThreshold()
            && !context.degradedPathAvailable();

        if (omitDueToNoDegradedPath || severeJointStress) {
            return ExecutionMode.OMIT;
        }

        if (stressConditions) {
            return context.degradedMode(task, true);
        }

        if (context.primaryLatencyRatio() >= context.optionalOmitThreshold()
            && !task.approximateSupported()
            && !task.fallbackSupported()) {
            return ExecutionMode.OMIT;
        }

        return ExecutionMode.EXECUTE;
    }
}
