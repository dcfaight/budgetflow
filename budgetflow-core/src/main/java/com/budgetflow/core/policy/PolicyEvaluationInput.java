package com.budgetflow.core.policy;

import java.time.Duration;
import java.util.List;

public record PolicyEvaluationInput(
    Duration remainingBudget,
    List<TaskDescriptor> tasks,
    SystemPressureSnapshot pressureSnapshot
) {
}
