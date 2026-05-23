package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.metadata.DegradationMetadata;

import java.util.List;

public record DashboardResponse(
    Balance balance,
    List<Transaction> transactions,
    RewardsSummary rewards,
    List<Offer> offers,
    SpendingInsights insights,
    DegradationMetadata degradationMetadata
) {
}
