package com.budgetflow.core.policy;

import java.time.Duration;

public record OptionalTaskPlanningContext(
    SystemPressureSnapshot pressureSnapshot,
    Duration remainingBudget,
    double primaryLatencyRatio,
    double cheapestDegradedLatencyRatio,
    double degradedSavingsRatio,
    long degradedSavingsMillis,
    boolean degradedPathAvailable,
    boolean lowBudget,
    boolean veryLowBudget,
    boolean highPressure,
    double optionalDegradeThreshold,
    double optionalOmitThreshold
) {
}
