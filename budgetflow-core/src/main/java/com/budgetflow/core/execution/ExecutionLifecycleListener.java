package com.budgetflow.core.execution;

import com.budgetflow.core.api.RequestExecutionResult;
import com.budgetflow.core.policy.PolicyDecision;
import com.budgetflow.core.policy.PolicyEvaluationInput;

/**
 * Optional lifecycle hooks for request-scoped adaptive execution.
 *
 * <p>This extension point is intentionally lightweight so applications can
 * integrate runtime observability or custom diagnostics without coupling
 * BudgetFlow to a specific telemetry platform.
 */
public interface ExecutionLifecycleListener {
    default void beforePolicyEvaluation(PolicyEvaluationInput input) {
    }

    default void afterPolicyEvaluation(PolicyEvaluationInput input, PolicyDecision decision) {
    }

    default void afterRequestExecution(RequestExecutionResult result) {
    }
}
