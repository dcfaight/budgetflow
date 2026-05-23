package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

import java.time.Duration;

public record TaskExecutionDirective(
    String taskName,
    ExecutionMode executionMode,
    Duration allocatedBudget,
    boolean omitted,
    String reason
) {
}
