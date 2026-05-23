package com.budgetflow.core.policy;

import com.budgetflow.core.budget.DefaultExecutionBudget;
import com.budgetflow.core.context.BudgetContext;
import com.budgetflow.core.context.BudgetContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSystemPressureProviderTest {

    private final DefaultSystemPressureProvider provider = new DefaultSystemPressureProvider();

    @AfterEach
    void clearContext() {
        BudgetContextHolder.clear();
    }

    @Test
    void returnsZeroPressureWhenNoBudgetContext() {
        SystemPressureSnapshot snapshot = provider.currentPressure();

        assertEquals(0.0, snapshot.executorUtilization());
        assertEquals(0.0, snapshot.dbPressure());
        assertEquals(0.0, snapshot.downstreamPressure());
    }

    @Test
    void returnsMaxPressureWhenBudgetFullyConsumed() {
        BudgetContextHolder.set(new BudgetContext(
            new DefaultExecutionBudget(Duration.ofMillis(100), Instant.now().minusMillis(200))
        ));

        SystemPressureSnapshot snapshot = provider.currentPressure();

        assertEquals(1.0, snapshot.executorUtilization());
        assertEquals(1.0, snapshot.dbPressure());
        assertEquals(1.0, snapshot.downstreamPressure());
    }

    @Test
    void returnsModeratePressureWhenBudgetPartiallyConsumed() {
        BudgetContextHolder.set(new BudgetContext(
            new DefaultExecutionBudget(Duration.ofMillis(200), Instant.now().minusMillis(100))
        ));

        SystemPressureSnapshot snapshot = provider.currentPressure();

        // ~50% consumed
        assertTrue(snapshot.executorUtilization() > 0.0 && snapshot.executorUtilization() < 1.0);
    }

    @Test
    void returnsMaxPressureWhenTotalBudgetIsZero() {
        BudgetContextHolder.set(new BudgetContext(
            new DefaultExecutionBudget(Duration.ZERO, Instant.now())
        ));

        SystemPressureSnapshot snapshot = provider.currentPressure();

        assertEquals(1.0, snapshot.executorUtilization());
    }
}
