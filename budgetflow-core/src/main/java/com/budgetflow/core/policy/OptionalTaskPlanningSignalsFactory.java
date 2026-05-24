package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

import java.time.Duration;

final class OptionalTaskPlanningSignalsFactory {
    private static final double HIGH_PRESSURE = 0.85;
    private static final double MODERATE_PRESSURE = 0.60;
    private static final double STACKED_SIGNAL_PRESSURE = 0.70;
    private static final double MIN_DYNAMIC_LATENCY_RATIO = 0.20;
    private static final double MAX_DYNAMIC_LATENCY_RATIO = 0.95;
    private static final Duration LOW_BUDGET_THRESHOLD = Duration.ofMillis(200);
    private static final Duration VERY_LOW_BUDGET_THRESHOLD = Duration.ofMillis(120);

    PolicyPlanningSignals analyze(
        TaskDescriptor task,
        SystemPressureSnapshot snapshot,
        Duration remainingBudget,
        double optionalDegradeThresholdBase,
        double optionalOmitThresholdBase
    ) {
        TaskCostSignals costSignals = taskCostSignals(task, remainingBudget);
        double pressureLevel = snapshot.peakPressure();
        int stressedSignalCount = snapshot.signalsAtOrAbove(STACKED_SIGNAL_PRESSURE);
        boolean multiSignalStress = stressedSignalCount >= 2;
        boolean lowBudget = remainingBudget.compareTo(LOW_BUDGET_THRESHOLD) < 0;
        boolean veryLowBudget = remainingBudget.compareTo(VERY_LOW_BUDGET_THRESHOLD) < 0;
        boolean highPressure = pressureLevel >= HIGH_PRESSURE || multiSignalStress;
        double mixedConstraintScore = mixedConstraintScore(
            pressureLevel,
            multiSignalStress,
            lowBudget,
            veryLowBudget,
            costSignals.primaryLatencyRatio()
        );
        return new PolicyPlanningSignals(
            costSignals,
            stressedSignalCount,
            multiSignalStress,
            lowBudget,
            veryLowBudget,
            highPressure,
            mixedConstraintScore,
            mixedConstraintBand(mixedConstraintScore),
            suggestedDegradedMode(task, costSignals, highPressure, multiSignalStress, lowBudget, mixedConstraintScore),
            adjustedLatencyThreshold(optionalDegradeThresholdBase, pressureLevel, lowBudget, veryLowBudget),
            adjustedLatencyThreshold(optionalOmitThresholdBase, pressureLevel, lowBudget, veryLowBudget)
        );
    }

    private double adjustedLatencyThreshold(
        double baseThreshold,
        double pressureLevel,
        boolean lowBudget,
        boolean veryLowBudget
    ) {
        double pressureAdjustment = pressureLevel >= HIGH_PRESSURE
            ? -0.20
            : pressureLevel >= MODERATE_PRESSURE ? -0.10 : 0.10;
        double budgetAdjustment = veryLowBudget
            ? -0.18
            : lowBudget ? -0.10 : 0.04;
        double adjusted = baseThreshold + pressureAdjustment + budgetAdjustment;
        return Math.max(MIN_DYNAMIC_LATENCY_RATIO, Math.min(MAX_DYNAMIC_LATENCY_RATIO, adjusted));
    }

    private Duration minPositive(Duration first, Duration second) {
        if (first == null || first.isZero() || first.isNegative()) {
            return second.isNegative() ? Duration.ZERO : second;
        }
        if (second == null || second.isZero() || second.isNegative()) {
            return first;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }

    private Duration expectedLatencyOrZero(TaskDescriptor task) {
        return task.expectedLatency() == null || task.expectedLatency().isNegative() ? Duration.ZERO : task.expectedLatency();
    }

    private Duration latencyOrPrimary(Duration candidate, TaskDescriptor task) {
        if (candidate == null || candidate.isNegative() || candidate.isZero()) {
            return expectedLatencyOrZero(task);
        }
        return candidate;
    }

    private Duration nonNegative(Duration value) {
        return value == null || value.isNegative() ? Duration.ZERO : value;
    }

    private TaskCostSignals taskCostSignals(TaskDescriptor task, Duration remainingBudget) {
        Duration primaryLatency = expectedLatencyOrZero(task);
        Duration fallbackLatency = latencyOrPrimary(task.fallbackExpectedLatency(), task);
        Duration approximateLatency = latencyOrPrimary(task.approximateExpectedLatency(), task);
        Duration cheapestDegradedLatency = task.fallbackSupported() && task.approximateSupported()
            ? minPositive(fallbackLatency, approximateLatency)
            : task.fallbackSupported()
                ? fallbackLatency
                : task.approximateSupported() ? approximateLatency : primaryLatency;
        double primaryRatio = latencyRatio(primaryLatency, remainingBudget);
        double fallbackRatio = latencyRatio(fallbackLatency, remainingBudget);
        double approximateRatio = latencyRatio(approximateLatency, remainingBudget);
        double degradedRatio = latencyRatio(cheapestDegradedLatency, remainingBudget);
        long savingsMillis = Math.max(primaryLatency.toMillis() - cheapestDegradedLatency.toMillis(), 0L);
        double savingsRatio = primaryLatency.isZero()
            ? 0.0
            : (double) savingsMillis / Math.max(primaryLatency.toMillis(), 1L);
        long primaryHeadroomMillis = Math.max(remainingBudget.toMillis() - primaryLatency.toMillis(), 0L);
        boolean primaryFitsBudget = fitsBudget(primaryLatency, remainingBudget);
        boolean fallbackFitsBudget = fitsBudget(fallbackLatency, remainingBudget);
        boolean approximateFitsBudget = fitsBudget(approximateLatency, remainingBudget);
        boolean cheapestDegradedFitsBudget = fitsBudget(cheapestDegradedLatency, remainingBudget);
        long primaryOverrunMillis = Math.max(primaryLatency.toMillis() - nonNegative(remainingBudget).toMillis(), 0L);
        long fallbackOverrunMillis = Math.max(fallbackLatency.toMillis() - nonNegative(remainingBudget).toMillis(), 0L);
        long approximateOverrunMillis = Math.max(
            approximateLatency.toMillis() - nonNegative(remainingBudget).toMillis(),
            0L
        );
        long degradedOverrunMillis = Math.max(
            cheapestDegradedLatency.toMillis() - nonNegative(remainingBudget).toMillis(),
            0L
        );

        return new TaskCostSignals(
            primaryRatio,
            fallbackRatio,
            approximateRatio,
            degradedRatio,
            savingsMillis,
            savingsRatio,
            primaryHeadroomMillis,
            task.fallbackSupported() || task.approximateSupported(),
            primaryFitsBudget,
            fallbackFitsBudget,
            approximateFitsBudget,
            cheapestDegradedFitsBudget,
            primaryOverrunMillis,
            fallbackOverrunMillis,
            approximateOverrunMillis,
            degradedOverrunMillis
        );
    }

    private double latencyRatio(Duration latency, Duration remainingBudget) {
        long remainingMillis = Math.max(nonNegative(remainingBudget).toMillis(), 1L);
        long latencyMillis = Math.max(nonNegative(latency).toMillis(), 0L);
        return (double) latencyMillis / (double) remainingMillis;
    }

    private boolean fitsBudget(Duration latency, Duration remainingBudget) {
        return nonNegative(latency).compareTo(nonNegative(remainingBudget)) <= 0;
    }

    private double mixedConstraintScore(
        double pressureLevel,
        boolean multiSignalStress,
        boolean lowBudget,
        boolean veryLowBudget,
        double latencyRatio
    ) {
        double pressureContribution = pressureLevel * 0.45;
        double signalContribution = multiSignalStress ? 0.20 : 0.0;
        double budgetContribution = veryLowBudget ? 0.20 : lowBudget ? 0.15 : 0.0;
        double ratioContribution = Math.min(latencyRatio, 1.5) * 0.20;
        double rawScore = pressureContribution + signalContribution + budgetContribution + ratioContribution;
        return Math.max(0.0, Math.min(rawScore, 1.5));
    }

    private String mixedConstraintBand(double mixedConstraintScore) {
        if (mixedConstraintScore >= 1.10) {
            return "severe";
        }
        if (mixedConstraintScore >= 0.85) {
            return "high";
        }
        if (mixedConstraintScore >= 0.55) {
            return "moderate";
        }
        return "low";
    }

    private ExecutionMode suggestedDegradedMode(
        TaskDescriptor task,
        TaskCostSignals costSignals,
        boolean highPressure,
        boolean multiSignalStress,
        boolean lowBudget,
        double mixedConstraintScore
    ) {
        if (task.approximateSupported() && !task.fallbackSupported()) {
            return ExecutionMode.EXECUTE_APPROXIMATE;
        }
        if (task.fallbackSupported() && !task.approximateSupported()) {
            return ExecutionMode.EXECUTE_WITH_FALLBACK;
        }
        if (!task.approximateSupported() && !task.fallbackSupported()) {
            return ExecutionMode.EXECUTE;
        }

        if (!costSignals.primaryFitsBudget()) {
            if (costSignals.approximateFitsBudget() && !costSignals.fallbackFitsBudget()) {
                return ExecutionMode.EXECUTE_APPROXIMATE;
            }
            if (costSignals.fallbackFitsBudget() && !costSignals.approximateFitsBudget()) {
                return ExecutionMode.EXECUTE_WITH_FALLBACK;
            }
        }

        boolean severeJointStress = highPressure || multiSignalStress || mixedConstraintScore >= 1.0;
        if (severeJointStress || lowBudget) {
            if (costSignals.approximateLatencyRatio() <= costSignals.fallbackLatencyRatio()) {
                return ExecutionMode.EXECUTE_APPROXIMATE;
            }
            return ExecutionMode.EXECUTE_WITH_FALLBACK;
        }

        return ExecutionMode.EXECUTE_WITH_FALLBACK;
    }
}
