package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.metadata.DegradationMetadata;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {
    private final AdaptiveExecutor adaptiveExecutor;
    private final BalanceClient balanceClient;
    private final TransactionClient transactionClient;
    private final RewardsClient rewardsClient;
    private final OffersClient offersClient;
    private final InsightsClient insightsClient;

    public DashboardService(
        AdaptiveExecutor adaptiveExecutor,
        BalanceClient balanceClient,
        TransactionClient transactionClient,
        RewardsClient rewardsClient,
        OffersClient offersClient,
        InsightsClient insightsClient
    ) {
        this.adaptiveExecutor = adaptiveExecutor;
        this.balanceClient = balanceClient;
        this.transactionClient = transactionClient;
        this.rewardsClient = rewardsClient;
        this.offersClient = offersClient;
        this.insightsClient = insightsClient;
    }

    public DashboardResponse getDashboard(String accountId) {
        var balanceFuture = adaptiveExecutor.executeMandatory("balance", () -> balanceClient.getBalance(accountId)).toCompletableFuture();
        var transactionsFuture = adaptiveExecutor.executeMandatory("transactions", () -> transactionClient.getTransactions(accountId)).toCompletableFuture();
        var rewardsFuture = adaptiveExecutor.executeImportant("rewards", () -> rewardsClient.getRewards(accountId)).toCompletableFuture();
        var offersFuture = adaptiveExecutor.executeImportant("offers", () -> offersClient.getOffers(accountId)).toCompletableFuture();
        var insightsFuture = adaptiveExecutor.executeOptional("insights", () -> insightsClient.getInsights(accountId)).toCompletableFuture();

        Balance balance = balanceFuture.join();
        List<Transaction> transactions = transactionsFuture.join();

        List<String> omitted = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        List<String> approximated = new ArrayList<>();

        RewardsSummary rewards = rewardsFuture.join().orElseGet(() -> {
            fallback.add("rewards");
            return new RewardsSummary(0);
        });

        List<Offer> offers = offersFuture.join().orElseGet(() -> {
            approximated.add("offers");
            return List.of();
        });

        SpendingInsights insights = insightsFuture.join().orElseGet(() -> {
            omitted.add("insights");
            return new SpendingInsights("Insights omitted due to budget constraints.");
        });

        DegradationMetadata metadata = new DegradationMetadata(
            !omitted.isEmpty() || !fallback.isEmpty() || !approximated.isEmpty(),
            omitted,
            fallback,
            approximated
        );

        return new DashboardResponse(balance, transactions, rewards, offers, insights, metadata);
    }
}
