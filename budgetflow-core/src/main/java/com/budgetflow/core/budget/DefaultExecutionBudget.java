package com.budgetflow.core.budget;

import com.budgetflow.core.api.ExecutionBudget;

import java.time.Duration;
import java.time.Instant;

public final class DefaultExecutionBudget implements ExecutionBudget {
    private final Instant startedAt;
    private final Duration totalBudget;

    public DefaultExecutionBudget(Duration totalBudget) {
        this(totalBudget, Instant.now());
    }

    public DefaultExecutionBudget(Duration totalBudget, Instant startedAt) {
        this.totalBudget = totalBudget;
        this.startedAt = startedAt;
    }

    @Override
    public Instant startedAt() {
        return startedAt;
    }

    @Override
    public Duration totalBudget() {
        return totalBudget;
    }

    @Override
    public Duration elapsed() {
        return Duration.between(startedAt, Instant.now());
    }

    @Override
    public Duration remaining() {
        Duration remaining = totalBudget.minus(elapsed());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    @Override
    public boolean isExpired() {
        return remaining().isZero();
    }
}
