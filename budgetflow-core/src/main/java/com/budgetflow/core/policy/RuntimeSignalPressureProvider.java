package com.budgetflow.core.policy;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Pressure provider backed by runtime signal suppliers.
 *
 * <p>This adapter makes it easy to plug in lightweight signals such as queue
 * utilization, DB saturation, or downstream health ratios from existing runtime
 * instrumentation.
 */
public final class RuntimeSignalPressureProvider implements SystemPressureProvider {
    private final DoubleSupplier executorUtilizationSupplier;
    private final DoubleSupplier dbPressureSupplier;
    private final DoubleSupplier downstreamPressureSupplier;

    public RuntimeSignalPressureProvider(
        DoubleSupplier executorUtilizationSupplier,
        DoubleSupplier dbPressureSupplier,
        DoubleSupplier downstreamPressureSupplier
    ) {
        this.executorUtilizationSupplier = Objects.requireNonNull(
            executorUtilizationSupplier,
            "executorUtilizationSupplier must not be null"
        );
        this.dbPressureSupplier = Objects.requireNonNull(dbPressureSupplier, "dbPressureSupplier must not be null");
        this.downstreamPressureSupplier = Objects.requireNonNull(
            downstreamPressureSupplier,
            "downstreamPressureSupplier must not be null"
        );
    }

    @Override
    public SystemPressureSnapshot currentPressure() {
        return SystemPressureSnapshot.normalized(
            executorUtilizationSupplier.getAsDouble(),
            dbPressureSupplier.getAsDouble(),
            downstreamPressureSupplier.getAsDouble()
        );
    }
}
