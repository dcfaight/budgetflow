package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

public final class EfficiencyOptionalTaskModeSelector implements OptionalTaskModeSelector {
    @Override
    public ExecutionMode chooseMode(TaskDescriptor task, OptionalTaskPlanningContext context) {
        boolean stressConditions = context.highPressure()
            || context.multiSignalStress()
            || context.lowBudget()
            || context.primaryLatencyRatio() >= context.optionalDegradeThreshold();
        boolean shouldOmit = context.highPressure()
            || context.veryLowBudget()
            || (context.multiSignalStress() && context.primaryLatencyRatio() >= context.optionalDegradeThreshold())
            || context.primaryLatencyRatio() >= context.optionalOmitThreshold();

        if (shouldOmit) {
            return ExecutionMode.OMIT;
        }

        if (stressConditions) {
            return context.degradedMode(task, false);
        }

        return ExecutionMode.EXECUTE;
    }
}
