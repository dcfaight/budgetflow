package com.budgetflow.core.api;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents the active request execution budget.
 * <p>
 * This is a core foundational contract used during planning and execution.
 */
public interface ExecutionBudget {
    Instant startedAt();

    Duration totalBudget();

    Duration elapsed();

    Duration remaining();

    boolean isExpired();
}
