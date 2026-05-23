package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveRequest;
import com.budgetflow.core.api.TaskKey;
import com.budgetflow.core.classification.ExecutionMode;

import java.time.Duration;
import java.util.List;

/**
 * Demo helper that defines dashboard task keys and grouped request construction.
 * <p>
 * This is fintech-demo-specific utility code and not part of BudgetFlow's
 * reusable core API contracts.
 */
public final class DashboardTaskSpecs {
    public static final String BALANCE_TASK = "balance";
    public static final String TRANSACTIONS_TASK = "transactions";
    public static final String REWARDS_TASK = "rewards";
    public static final String OFFERS_TASK = "offers";
    public static final String INSIGHTS_TASK = "insights";

    public static final TaskKey<Balance> BALANCE_KEY = TaskKey.of(BALANCE_TASK);
    public static final TaskKey<List<Transaction>> TRANSACTIONS_KEY = TaskKey.of(TRANSACTIONS_TASK);
    public static final TaskKey<RewardsSummary> REWARDS_KEY = TaskKey.of(REWARDS_TASK);
    public static final TaskKey<List<Offer>> OFFERS_KEY = TaskKey.of(OFFERS_TASK);
    public static final TaskKey<SpendingInsights> INSIGHTS_KEY = TaskKey.of(INSIGHTS_TASK);

    public static final Duration BALANCE_PRIMARY_LATENCY = Duration.ofMillis(40);
    public static final Duration TRANSACTIONS_PRIMARY_LATENCY = Duration.ofMillis(65);
    public static final Duration REWARDS_PRIMARY_LATENCY = Duration.ofMillis(90);
    public static final Duration REWARDS_FALLBACK_LATENCY = Duration.ofMillis(10);
    public static final Duration OFFERS_PRIMARY_LATENCY = Duration.ofMillis(110);
    public static final Duration OFFERS_FALLBACK_LATENCY = Duration.ofMillis(12);
    public static final Duration OFFERS_APPROXIMATE_LATENCY = Duration.ofMillis(8);
    public static final Duration INSIGHTS_PRIMARY_LATENCY = Duration.ofMillis(140);

    private DashboardTaskSpecs() {
    }

    /**
     * Builds an {@link AdaptiveRequest} for the given account, grouping all
     * dashboard tasks for request-scoped adaptive planning.
     */
    public static AdaptiveRequest forAccount(
        String accountId,
        BalanceClient balanceClient,
        TransactionClient transactionClient,
        RewardsClient rewardsClient,
        OffersClient offersClient,
        InsightsClient insightsClient
    ) {
        return AdaptiveRequest.builder()
            .mandatory(BALANCE_KEY, BALANCE_PRIMARY_LATENCY,
                () -> balanceClient.getBalance(accountId))
            .mandatory(TRANSACTIONS_KEY, TRANSACTIONS_PRIMARY_LATENCY,
                () -> transactionClient.getTransactions(accountId))
            .importantWithFallback(REWARDS_KEY, REWARDS_PRIMARY_LATENCY,
                () -> rewardsClient.getRewards(accountId),
                () -> rewardsClient.getCachedRewards(accountId))
            .optionalWithFallbackAndApproximate(OFFERS_KEY, OFFERS_PRIMARY_LATENCY,
                () -> offersClient.getOffers(accountId),
                () -> offersClient.getCachedOffers(accountId),
                () -> offersClient.getApproximateOffers(accountId))
            .optional(INSIGHTS_KEY, INSIGHTS_PRIMARY_LATENCY,
                () -> insightsClient.getInsights(accountId))
            .build();
    }

    public static Duration totalPrimaryLatency() {
        return BALANCE_PRIMARY_LATENCY
            .plus(TRANSACTIONS_PRIMARY_LATENCY)
            .plus(REWARDS_PRIMARY_LATENCY)
            .plus(OFFERS_PRIMARY_LATENCY)
            .plus(INSIGHTS_PRIMARY_LATENCY);
    }

    public static Duration expectedLatency(String taskName, ExecutionMode executionMode) {
        return switch (taskName) {
            case BALANCE_TASK -> BALANCE_PRIMARY_LATENCY;
            case TRANSACTIONS_TASK -> TRANSACTIONS_PRIMARY_LATENCY;
            case REWARDS_TASK -> executionMode == ExecutionMode.EXECUTE_WITH_FALLBACK
                ? REWARDS_FALLBACK_LATENCY
                : executionMode == ExecutionMode.OMIT ? Duration.ZERO : REWARDS_PRIMARY_LATENCY;
            case OFFERS_TASK -> switch (executionMode) {
                case EXECUTE_WITH_FALLBACK -> OFFERS_FALLBACK_LATENCY;
                case EXECUTE_APPROXIMATE -> OFFERS_APPROXIMATE_LATENCY;
                case OMIT -> Duration.ZERO;
                case EXECUTE -> OFFERS_PRIMARY_LATENCY;
            };
            case INSIGHTS_TASK -> executionMode == ExecutionMode.OMIT ? Duration.ZERO : INSIGHTS_PRIMARY_LATENCY;
            default -> throw new IllegalArgumentException("Unknown dashboard task: " + taskName);
        };
    }
}
