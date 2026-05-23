package com.budgetflow.core.policy;

import com.budgetflow.core.api.ExecutionBudget;
import com.budgetflow.core.context.BudgetContextHolder;

import java.time.Duration;
import java.util.Optional;

/**
 * Default implementation of {@link SystemPressureProvider} that derives system pressure
 * from the active request's {@link ExecutionBudget}.
 *
 * <p>When a budget context is present, pressure is proportional to budget consumption:
 * a fully consumed budget yields maximum pressure (1.0) while a fully available budget
 * yields no pressure (0.0). When no budget context is active, zero pressure is returned.
 */
public class DefaultSystemPressureProvider implements SystemPressureProvider {

    @Override
    public SystemPressureSnapshot currentPressure() {
        Optional<ExecutionBudget> executionBudget =
                BudgetContextHolder.current().map(ctx -> ctx.getExecutionBudget());

        if (executionBudget.isEmpty()) {
            return new SystemPressureSnapshot(0.0, 0.0, 0.0);
        }

        Duration total = executionBudget.get().totalBudget();
        Duration remaining = executionBudget.get().remaining();

        if (total.isZero() || total.isNegative()) {
            return new SystemPressureSnapshot(1.0, 1.0, 1.0);
        }

        double utilization = 1.0 - (double) remaining.toMillis() / (double) total.toMillis();
        double normalized = Math.max(0.0, Math.min(utilization, 1.0));
        return new SystemPressureSnapshot(normalized, normalized, normalized);
    }
}
