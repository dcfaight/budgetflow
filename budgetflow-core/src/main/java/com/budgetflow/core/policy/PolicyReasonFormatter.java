package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

import java.time.Duration;

final class PolicyReasonFormatter {
    private static final double HIGH_PRESSURE = 0.85;
    private static final double MODERATE_PRESSURE = 0.60;
    private static final Duration LOW_BUDGET_THRESHOLD = Duration.ofMillis(200);
    private static final Duration VERY_LOW_BUDGET_THRESHOLD = Duration.ofMillis(120);

    String format(
        String policyProfileName,
        ExecutionMode mode,
        SystemPressureSnapshot snapshot,
        Duration remainingBudget,
        double latencyRatio,
        int stressedSignalCount,
        String mixedConstraintBand,
        ExecutionMode suggestedDegradedMode
    ) {
        String pressureBand = pressureBand(snapshot.peakPressure());
        String budgetBand = budgetBand(remainingBudget);
        String ratio = String.format("%.2f", latencyRatio);
        String degradedPreference = degradedPreferenceLabel(suggestedDegradedMode);
        return switch (mode) {
            case EXECUTE ->
                "normal[policy=%s,pressure=%s:%s,active_signals=%d,mixed=%s,budget=%s,degrade_pref=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        mixedConstraintBand,
                        budgetBand,
                        degradedPreference,
                        ratio
                    );
            case EXECUTE_WITH_FALLBACK ->
                "fallback_selected_by_policy[policy=%s,pressure=%s:%s,active_signals=%d,mixed=%s,budget=%s,degrade_pref=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        mixedConstraintBand,
                        budgetBand,
                        degradedPreference,
                        ratio
                    );
            case EXECUTE_APPROXIMATE ->
                "approximate_selected_by_policy[policy=%s,pressure=%s:%s,active_signals=%d,mixed=%s,budget=%s,degrade_pref=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        mixedConstraintBand,
                        budgetBand,
                        degradedPreference,
                        ratio
                    );
            case OMIT ->
                "omitted_by_policy[policy=%s,pressure=%s:%s,active_signals=%d,mixed=%s,budget=%s,degrade_pref=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        mixedConstraintBand,
                        budgetBand,
                        degradedPreference,
                        ratio
                    );
        };
    }

    private String pressureBand(double pressureLevel) {
        if (pressureLevel >= HIGH_PRESSURE) {
            return "high";
        }
        if (pressureLevel >= MODERATE_PRESSURE) {
            return "moderate";
        }
        return "low";
    }

    private String budgetBand(Duration remainingBudget) {
        if (remainingBudget.compareTo(VERY_LOW_BUDGET_THRESHOLD) < 0) {
            return "very_low";
        }
        if (remainingBudget.compareTo(LOW_BUDGET_THRESHOLD) < 0) {
            return "tight";
        }
        return "available";
    }

    private String degradedPreferenceLabel(ExecutionMode mode) {
        return switch (mode) {
            case EXECUTE_WITH_FALLBACK -> "fallback";
            case EXECUTE_APPROXIMATE -> "approximate";
            default -> "none";
        };
    }
}
