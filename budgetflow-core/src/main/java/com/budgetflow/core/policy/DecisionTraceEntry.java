package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;

import java.time.Duration;

public record DecisionTraceEntry(
    String taskName,
    Importance taskImportance,
    ExecutionMode selectedExecutionMode,
    String reason,
    Duration expectedLatency,
    Duration plannedExecutionLatency,
    Duration allocatedBudget,
    Duration remainingBudgetAtPlanningTime
) {
}
