package com.budgetflow.core.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class FixedPressureProviderTest {

    @Test
    void zeroReturnsAllZeroSnapshot() {
        SystemPressureSnapshot snapshot = FixedPressureProvider.zero().currentPressure();

        assertEquals(0.0, snapshot.executorUtilization());
        assertEquals(0.0, snapshot.dbPressure());
        assertEquals(0.0, snapshot.downstreamPressure());
    }

    @Test
    void maximumReturnsAllOneSnapshot() {
        SystemPressureSnapshot snapshot = FixedPressureProvider.maximum().currentPressure();

        assertEquals(1.0, snapshot.executorUtilization());
        assertEquals(1.0, snapshot.dbPressure());
        assertEquals(1.0, snapshot.downstreamPressure());
    }

    @Test
    void uniformSetsAllDimensionsToGivenValue() {
        SystemPressureSnapshot snapshot = FixedPressureProvider.uniform(0.6).currentPressure();

        assertEquals(0.6, snapshot.executorUtilization());
        assertEquals(0.6, snapshot.dbPressure());
        assertEquals(0.6, snapshot.downstreamPressure());
    }

    @Test
    void ofSetsIndividualDimensions() {
        SystemPressureSnapshot snapshot = FixedPressureProvider.of(0.3, 0.7, 0.5).currentPressure();

        assertEquals(0.3, snapshot.executorUtilization());
        assertEquals(0.7, snapshot.dbPressure());
        assertEquals(0.5, snapshot.downstreamPressure());
    }

    @Test
    void constructorWithSnapshotPreservesSnapshot() {
        SystemPressureSnapshot given = new SystemPressureSnapshot(0.4, 0.5, 0.6);
        FixedPressureProvider provider = new FixedPressureProvider(given);

        assertSame(given, provider.currentPressure());
    }

    @Test
    void currentPressureIsIdempotent() {
        FixedPressureProvider provider = FixedPressureProvider.uniform(0.45);

        SystemPressureSnapshot first = provider.currentPressure();
        SystemPressureSnapshot second = provider.currentPressure();

        assertEquals(first, second);
    }

    @Test
    void constructorRejectsNullSnapshot() {
        assertThrows(NullPointerException.class, () -> new FixedPressureProvider(null));
    }

    @Test
    void implementsSystemPressureProvider() {
        assertNotNull((SystemPressureProvider) FixedPressureProvider.zero());
    }
}
