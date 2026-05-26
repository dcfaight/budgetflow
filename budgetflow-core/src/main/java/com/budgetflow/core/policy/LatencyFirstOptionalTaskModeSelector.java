package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

/**
 * An optional-task mode selector that aggressively omits optional work to protect
 * latency headroom.
 *
 * <p>Compared with {@link EfficiencyOptionalTaskModeSelector}, this selector omits
 * optional work at a lower latency-ratio threshold and does not explore degraded paths
 * for optional steps. It is intended for latency-sensitive agent turns or endpoints
 * where preserving remaining budget headroom is the highest priority.
 *
 * <p>Key behavior:
 * <ul>
 *   <li>Omit optional work whenever the primary latency ratio exceeds a tight threshold
 *       ({@value #LATENCY_FIRST_DEGRADE_THRESHOLD}), regardless of degraded-path availability.</li>
 *   <li>Under any high-pressure or multi-signal stress condition, omit immediately.</li>
 *   <li>Only execute optional work when budget is very comfortable and pressure is absent.</li>
 * </ul>
 *
 * <p>Use this profile when:
 * <ul>
 *   <li>Agent turns must stay inside a strict wall-clock deadline.</li>
 *   <li>Optional enrichment steps should not consume budget that may be needed later.</li>
 *   <li>You want a clear demonstrable contrast against the {@code balanced} profile.</li>
 * </ul>
 *
 * @see PlannerPolicyProfile#LATENCY_FIRST
 */
public final class LatencyFirstOptionalTaskModeSelector implements OptionalTaskModeSelector {

    /**
     * Latency ratio above which optional work is omitted regardless of degraded-path availability.
     * Lower than the {@code efficiency} threshold to ensure earlier omission under mild stress.
     */
    private static final double LATENCY_FIRST_DEGRADE_THRESHOLD = 0.42;

    @Override
    public ExecutionMode chooseMode(TaskDescriptor task, OptionalTaskPlanningContext context) {
        boolean anyPressureOrStress = context.highPressure()
            || context.multiSignalStress()
            || context.lowBudget()
            || context.veryLowBudget();
        boolean ratioExceedsThreshold = context.primaryLatencyRatio() >= LATENCY_FIRST_DEGRADE_THRESHOLD;

        if (anyPressureOrStress || ratioExceedsThreshold || !context.primaryFitsBudget()) {
            return ExecutionMode.OMIT;
        }

        return ExecutionMode.EXECUTE;
    }
}
