package com.budgetflow.core.policy;

import java.time.Duration;

public record OptionalTaskPlanningContext(
    SystemPressureSnapshot pressureSnapshot,
    Duration remainingBudget,
    double latencyRatio,
    boolean lowBudget,
    boolean veryLowBudget,
    boolean highPressure,
    double optionalDegradeThreshold,
    double optionalOmitThreshold
) {
}
