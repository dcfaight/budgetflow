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
        String decisionLayer,
        SystemPressureSnapshot snapshot,
        Duration remainingBudget,
        double latencyRatio,
        int stressedSignalCount,
        String mixedConstraintBand,
        ExecutionMode suggestedDegradedMode,
        TaskCostSignals costSignals
    ) {
        String pressureBand = pressureBand(snapshot.peakPressure());
        String budgetBand = budgetBand(remainingBudget);
        String ratio = String.format("%.2f", latencyRatio);
        String degradedPreference = degradedPreferenceLabel(suggestedDegradedMode);
        String fitLabel = fitLabel(costSignals);
        String savingsBand = savingsBand(costSignals.degradedSavingsRatio(), costSignals.degradedSavingsMillis());
        return switch (mode) {
            case EXECUTE ->
                "normal[policy=%s,layer=%s,pressure=%s:%s,active_signals=%d,mixed=%s,budget=%s,degrade_pref=%s,fit=%s,savings=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        decisionLayer,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        mixedConstraintBand,
                        budgetBand,
                        degradedPreference,
                        fitLabel,
                        savingsBand,
                        ratio
                    );
            case EXECUTE_WITH_FALLBACK ->
                "fallback_selected_by_policy[policy=%s,layer=%s,pressure=%s:%s,active_signals=%d,mixed=%s,budget=%s,degrade_pref=%s,fit=%s,savings=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        decisionLayer,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        mixedConstraintBand,
                        budgetBand,
                        degradedPreference,
                        fitLabel,
                        savingsBand,
                        ratio
                    );
            case EXECUTE_APPROXIMATE ->
                "approximate_selected_by_policy[policy=%s,layer=%s,pressure=%s:%s,active_signals=%d,mixed=%s,budget=%s,degrade_pref=%s,fit=%s,savings=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        decisionLayer,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        mixedConstraintBand,
                        budgetBand,
                        degradedPreference,
                        fitLabel,
                        savingsBand,
                        ratio
                    );
            case OMIT ->
                "omitted_by_policy[policy=%s,layer=%s,pressure=%s:%s,active_signals=%d,mixed=%s,budget=%s,degrade_pref=%s,fit=%s,savings=%s,latency_ratio=%s]"
                    .formatted(
                        policyProfileName,
                        decisionLayer,
                        pressureBand,
                        snapshot.dominantSignal(),
                        stressedSignalCount,
                        mixedConstraintBand,
                        budgetBand,
                        degradedPreference,
                        fitLabel,
                        savingsBand,
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

    private String fitLabel(TaskCostSignals costSignals) {
        if (costSignals.primaryFitsBudget()) {
            return "primary";
        }
        if (costSignals.cheapestDegradedFitsBudget()) {
            return "degraded";
        }
        return "none";
    }

    private String savingsBand(double degradedSavingsRatio, long degradedSavingsMillis) {
        if (degradedSavingsRatio >= 0.45 || degradedSavingsMillis >= 60) {
            return "high";
        }
        if (degradedSavingsRatio >= 0.20 || degradedSavingsMillis >= 25) {
            return "medium";
        }
        return "low";
    }
}
