package com.budgetflow.core.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompositeAndRuntimePressureProviderTest {

    @Test
    void compositeProviderUsesMaxAcrossAllSources() {
        CompositeSystemPressureProvider provider = CompositeSystemPressureProvider.of(
            () -> new SystemPressureSnapshot(0.2, 0.3, 0.4),
            () -> new SystemPressureSnapshot(0.7, 0.1, 0.2),
            () -> new SystemPressureSnapshot(0.5, 0.8, 0.6)
        );

        SystemPressureSnapshot snapshot = provider.currentPressure();
        assertEquals(0.7, snapshot.executorUtilization());
        assertEquals(0.8, snapshot.dbPressure());
        assertEquals(0.6, snapshot.downstreamPressure());
    }

    @Test
    void compositeProviderRejectsEmptyProviderList() {
        assertThrows(IllegalArgumentException.class, () -> new CompositeSystemPressureProvider(java.util.List.of()));
    }

    @Test
    void runtimeSignalProviderNormalizesOutOfRangeValues() {
        RuntimeSignalPressureProvider provider = new RuntimeSignalPressureProvider(
            () -> 1.7,
            () -> -0.4,
            () -> Double.NaN
        );

        SystemPressureSnapshot snapshot = provider.currentPressure();
        assertEquals(1.0, snapshot.executorUtilization());
        assertEquals(0.0, snapshot.dbPressure());
        assertEquals(0.0, snapshot.downstreamPressure());
    }

    @Test
    void normalizedSnapshotHandlesInfinity() {
        SystemPressureSnapshot snapshot = SystemPressureSnapshot.normalized(Double.POSITIVE_INFINITY, 0.3, Double.NEGATIVE_INFINITY);

        assertEquals(1.0, snapshot.executorUtilization());
        assertEquals(0.3, snapshot.dbPressure());
        assertEquals(0.0, snapshot.downstreamPressure());
    }
}
