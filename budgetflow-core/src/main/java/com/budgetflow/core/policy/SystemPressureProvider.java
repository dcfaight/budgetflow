package com.budgetflow.core.policy;

/**
 * Supplies a {@link SystemPressureSnapshot} that reflects current runtime pressure.
 *
 * <p>Implementations may derive pressure from the active request budget, external metrics,
 * or any other source. The executor uses the snapshot returned here when evaluating
 * policy decisions for each request.
 */
@FunctionalInterface
public interface SystemPressureProvider {
    /**
     * Returns a snapshot of the current system pressure.
     *
     * @return a non-null {@link SystemPressureSnapshot}
     */
    SystemPressureSnapshot currentPressure();
}
