package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

public interface OptionalTaskModeSelector {
    ExecutionMode chooseMode(TaskDescriptor task, OptionalTaskPlanningContext context);
}
