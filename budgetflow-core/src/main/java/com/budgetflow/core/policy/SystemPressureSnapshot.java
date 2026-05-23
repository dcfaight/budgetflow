package com.budgetflow.core.policy;

public record SystemPressureSnapshot(
    double executorUtilization,
    double dbPressure,
    double downstreamPressure
) {
    public double peakPressure() {
        return Math.max(executorUtilization, Math.max(dbPressure, downstreamPressure));
    }

    public String dominantSignal() {
        double peak = peakPressure();
        if (peak == executorUtilization) {
            return "executor";
        }
        if (peak == dbPressure) {
            return "db";
        }
        return "downstream";
    }
}
