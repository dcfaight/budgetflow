package com.budgetflow.core.policy;

import java.util.Objects;

/**
 * A {@link SystemPressureProvider} that always returns a fixed {@link SystemPressureSnapshot}.
 *
 * <p>Useful for testing, benchmark scenarios, and any context where system pressure is
 * known in advance and does not change during request execution.
 *
 * <p>Use the static factory methods for the most common configurations:
 * <ul>
 *   <li>{@link #zero()} — no pressure on any dimension</li>
 *   <li>{@link #maximum()} — full pressure on every dimension</li>
 *   <li>{@link #uniform(double)} — equal pressure on all dimensions</li>
 *   <li>{@link #of(double, double, double)} — individual dimension values</li>
 * </ul>
 */
public final class FixedPressureProvider implements SystemPressureProvider {

    private final SystemPressureSnapshot snapshot;

    public FixedPressureProvider(SystemPressureSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    /**
     * Returns a provider with zero pressure on every dimension.
     */
    public static FixedPressureProvider zero() {
        return uniform(0.0);
    }

    /**
     * Returns a provider with maximum pressure (1.0) on every dimension.
     */
    public static FixedPressureProvider maximum() {
        return uniform(1.0);
    }

    /**
     * Returns a provider with the given uniform pressure on all three dimensions.
     *
     * @param pressure a value in [0.0, 1.0]
     */
    public static FixedPressureProvider uniform(double pressure) {
        return of(pressure, pressure, pressure);
    }

    /**
     * Returns a provider with individually specified pressure values.
     *
     * @param executorUtilization  executor thread-pool utilization in [0.0, 1.0]
     * @param dbPressure           database pressure in [0.0, 1.0]
     * @param downstreamPressure   downstream service pressure in [0.0, 1.0]
     */
    public static FixedPressureProvider of(double executorUtilization, double dbPressure, double downstreamPressure) {
        return new FixedPressureProvider(new SystemPressureSnapshot(executorUtilization, dbPressure, downstreamPressure));
    }

    @Override
    public SystemPressureSnapshot currentPressure() {
        return snapshot;
    }
}
