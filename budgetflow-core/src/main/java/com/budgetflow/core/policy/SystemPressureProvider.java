package com.budgetflow.core.policy;

/**
 * Supplies a {@link SystemPressureSnapshot} that reflects current runtime pressure.
 *
 * <p>Implementations may derive pressure from the active request budget, external metrics,
 * or any other source. The executor uses the snapshot returned here when evaluating
 * policy decisions for each request.
 *
 * <p>Built-in adapters include:
 * <ul>
 *   <li>{@link DefaultSystemPressureProvider} for budget-derived pressure</li>
 *   <li>{@link RuntimeSignalPressureProvider} for runtime signal suppliers</li>
 *   <li>{@link CompositeSystemPressureProvider} for combining multiple sources</li>
 * </ul>
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
