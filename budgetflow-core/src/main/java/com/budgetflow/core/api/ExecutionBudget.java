package com.budgetflow.core.api;

import java.time.Duration;
import java.time.Instant;

public interface ExecutionBudget {
    Instant startedAt();

    Duration totalBudget();

    Duration elapsed();

    Duration remaining();

    boolean isExpired();
}
