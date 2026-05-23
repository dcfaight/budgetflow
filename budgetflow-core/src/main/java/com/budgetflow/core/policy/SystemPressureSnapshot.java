package com.budgetflow.core.policy;

public record SystemPressureSnapshot(
    double executorUtilization,
    double dbPressure,
    double downstreamPressure
) {
}
