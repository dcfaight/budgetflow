package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

public final class EfficiencyOptionalTaskModeSelector implements OptionalTaskModeSelector {
    @Override
    public ExecutionMode chooseMode(TaskDescriptor task, OptionalTaskPlanningContext context) {
        boolean stressConditions = context.highPressure()
            || context.lowBudget()
            || context.primaryLatencyRatio() >= context.optionalDegradeThreshold();
        boolean shouldOmit = context.highPressure()
            || context.veryLowBudget()
            || context.primaryLatencyRatio() >= context.optionalOmitThreshold();

        if (shouldOmit) {
            return ExecutionMode.OMIT;
        }

        if (stressConditions && task.approximateSupported()) {
            return ExecutionMode.EXECUTE_APPROXIMATE;
        }

        if (stressConditions && task.fallbackSupported()) {
            return ExecutionMode.EXECUTE_WITH_FALLBACK;
        }

        return ExecutionMode.EXECUTE;
    }
}
