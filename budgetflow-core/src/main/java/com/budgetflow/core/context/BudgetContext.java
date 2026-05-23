package com.budgetflow.core.context;

import com.budgetflow.core.api.ExecutionBudget;

public class BudgetContext {
    private final ExecutionBudget executionBudget;

    public BudgetContext(ExecutionBudget executionBudget) {
        this.executionBudget = executionBudget;
    }

    public ExecutionBudget getExecutionBudget() {
        return executionBudget;
    }
}
