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

    public int signalsAtOrAbove(double threshold) {
        int count = 0;
        if (executorUtilization >= threshold) {
            count++;
        }
        if (dbPressure >= threshold) {
            count++;
        }
        if (downstreamPressure >= threshold) {
            count++;
        }
        return count;
    }

    public double averagePressure() {
        return (executorUtilization + dbPressure + downstreamPressure) / 3.0;
    }

    public double pressureSpread() {
        double max = peakPressure();
        double min = Math.min(executorUtilization, Math.min(dbPressure, downstreamPressure));
        return max - min;
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
