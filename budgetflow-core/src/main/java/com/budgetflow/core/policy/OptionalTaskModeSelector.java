package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;

public interface OptionalTaskModeSelector {
    /**
     * Chooses how an optional task should execute after the planner has already
     * computed request-budget and pressure signals for the task.
     * <p>
     * This is the primary extension point for optional-task policy variation.
     * Request orchestration, directive allocation, diagnostics, and decision
     * trace remain owned by the core planner.
     */
    ExecutionMode chooseMode(TaskDescriptor task, OptionalTaskPlanningContext context);
}
