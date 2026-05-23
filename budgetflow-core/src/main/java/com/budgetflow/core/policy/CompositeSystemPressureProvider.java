package com.budgetflow.core.policy;

import java.util.List;
import java.util.Objects;

/**
 * Merges multiple pressure providers into a single request-time snapshot.
 *
 * <p>Each pressure dimension is merged using max semantics to keep behavior
 * conservative under mixed signal sources (for example, request-budget pressure
 * plus runtime platform pressure).
 */
public final class CompositeSystemPressureProvider implements SystemPressureProvider {
    private final List<SystemPressureProvider> providers;

    public CompositeSystemPressureProvider(List<SystemPressureProvider> providers) {
        Objects.requireNonNull(providers, "providers must not be null");
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        this.providers = List.copyOf(providers);
    }

    public static CompositeSystemPressureProvider of(
        SystemPressureProvider primary,
        SystemPressureProvider... additional
    ) {
        Objects.requireNonNull(primary, "primary must not be null");
        Objects.requireNonNull(additional, "additional must not be null");
        List<SystemPressureProvider> allProviders = new java.util.ArrayList<>();
        allProviders.add(primary);
        for (SystemPressureProvider provider : additional) {
            allProviders.add(Objects.requireNonNull(provider, "additional provider must not be null"));
        }
        return new CompositeSystemPressureProvider(allProviders);
    }

    @Override
    public SystemPressureSnapshot currentPressure() {
        double executorUtilization = 0.0;
        double dbPressure = 0.0;
        double downstreamPressure = 0.0;

        for (SystemPressureProvider provider : providers) {
            SystemPressureSnapshot snapshot = Objects.requireNonNull(
                provider.currentPressure(),
                "provider.currentPressure() must not be null"
            );
            executorUtilization = Math.max(executorUtilization, snapshot.executorUtilization());
            dbPressure = Math.max(dbPressure, snapshot.dbPressure());
            downstreamPressure = Math.max(downstreamPressure, snapshot.downstreamPressure());
        }

        return SystemPressureSnapshot.normalized(executorUtilization, dbPressure, downstreamPressure);
    }
}
