package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

import java.time.Duration;

record PolicyPlanningSignals(
    TaskCostSignals taskCostSignals,
    int stressedSignalCount,
    boolean multiSignalStress,
    boolean lowBudget,
    boolean veryLowBudget,
    boolean highPressure,
    double mixedConstraintScore,
    String mixedConstraintBand,
    ExecutionMode suggestedDegradedMode,
    double optionalDegradeThreshold,
    double optionalOmitThreshold
) {
    OptionalTaskPlanningContext optionalTaskPlanningContext(
        TaskDescriptor task,
        SystemPressureSnapshot snapshot,
        Duration remainingBudget
    ) {
        return new OptionalTaskPlanningContext(
            snapshot,
            remainingBudget,
            stressedSignalCount,
            multiSignalStress,
            snapshot.averagePressure(),
            mixedConstraintScore,
            taskCostSignals.primaryLatencyRatio(),
            taskCostSignals.cheapestDegradedLatencyRatio(),
            taskCostSignals.degradedSavingsRatio(),
            taskCostSignals.degradedSavingsMillis(),
            taskCostSignals.degradedPathAvailable(),
            taskCostSignals.primaryFitsBudget(),
            taskCostSignals.fallbackFitsBudget(),
            taskCostSignals.approximateFitsBudget(),
            taskCostSignals.cheapestDegradedFitsBudget(),
            taskCostSignals.primaryOverrunMillis(),
            taskCostSignals.fallbackOverrunMillis(),
            taskCostSignals.approximateOverrunMillis(),
            taskCostSignals.cheapestDegradedOverrunMillis(),
            lowBudget,
            veryLowBudget,
            highPressure,
            mixedConstraintBand,
            suggestedDegradedMode,
            optionalDegradeThreshold,
            optionalOmitThreshold
        );
    }
}
