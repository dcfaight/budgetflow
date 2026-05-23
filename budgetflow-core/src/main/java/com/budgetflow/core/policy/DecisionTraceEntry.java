package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

import java.time.Duration;

public record DecisionTraceEntry(
    String taskName,
    ExecutionMode selectedExecutionMode,
    String reason,
    Duration expectedLatency,
    Duration remainingBudgetAtPlanningTime
) {
}
