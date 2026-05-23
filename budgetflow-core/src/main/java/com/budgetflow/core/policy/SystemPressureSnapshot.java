package com.budgetflow.core.policy;

public record SystemPressureSnapshot(
    double executorUtilization,
    double dbPressure,
    double downstreamPressure
) {
    public static SystemPressureSnapshot normalized(
        double executorUtilization,
        double dbPressure,
        double downstreamPressure
    ) {
        return new SystemPressureSnapshot(
            clamp(executorUtilization),
            clamp(dbPressure),
            clamp(downstreamPressure)
        );
    }

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

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (Double.isInfinite(value)) {
            return value > 0.0 ? 1.0 : 0.0;
        }
        return Math.max(0.0, Math.min(value, 1.0));
    }
}
