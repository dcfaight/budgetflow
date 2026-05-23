package com.budgetflow.core.policy;

public interface BudgetPolicyEngine {
    PolicyDecision evaluate(PolicyEvaluationInput input);
}
