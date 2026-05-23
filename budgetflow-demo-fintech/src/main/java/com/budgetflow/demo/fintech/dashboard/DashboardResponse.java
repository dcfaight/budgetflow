package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.metadata.RequestExecutionDiagnostics;
import com.budgetflow.core.policy.DecisionTraceEntry;

import java.util.List;

public record DashboardResponse(
    Balance balance,
    List<Transaction> transactions,
    RewardsSummary rewards,
    List<Offer> offers,
    SpendingInsights insights,
    RequestExecutionDiagnostics diagnostics,
    List<DecisionTraceEntry> decisionTrace,
    String executionSummary
) {
}
