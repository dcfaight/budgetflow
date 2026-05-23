package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.metadata.DegradationMetadata;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
        var balanceFuture = adaptiveExecutor.execute(
            TaskSpec.mandatory("balance", Duration.ofMillis(40), () -> balanceClient.getBalance(accountId))
        ).toCompletableFuture();
        var transactionsFuture = adaptiveExecutor.execute(
            TaskSpec.mandatory("transactions", Duration.ofMillis(65), () -> transactionClient.getTransactions(accountId))
        ).toCompletableFuture();
        var rewardsFuture = adaptiveExecutor.execute(
            TaskSpec.important("rewards", Duration.ofMillis(90), () -> rewardsClient.getRewards(accountId))
                .withFallback(() -> rewardsClient.getCachedRewards(accountId))
        ).toCompletableFuture();
        var offersFuture = adaptiveExecutor.execute(
            TaskSpec.optional("offers", Duration.ofMillis(110), () -> offersClient.getOffers(accountId))
                .withFallback(() -> offersClient.getCachedOffers(accountId))
                .withApproximate(() -> offersClient.getApproximateOffers(accountId))
        ).toCompletableFuture();
        var insightsFuture = adaptiveExecutor.execute(
            TaskSpec.optional("insights", Duration.ofMillis(140), () -> insightsClient.getInsights(accountId))
        ).toCompletableFuture();

        var balanceResult = balanceFuture.join();
        var transactionsResult = transactionsFuture.join();
        var rewardsResult = rewardsFuture.join();
        var offersResult = offersFuture.join();
        var insightsResult = insightsFuture.join();

        Balance balance = balanceResult.value().orElseThrow(() -> new IllegalStateException("balance must be present"));
        List<Transaction> transactions = transactionsResult.value()
            .orElseThrow(() -> new IllegalStateException("transactions must be present"));

        List<String> omitted = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        List<String> approximated = new ArrayList<>();

        RewardsSummary rewards = rewardsResult.value().orElseGet(() -> new RewardsSummary(0));
        List<Offer> offers = offersResult.value().orElseGet(List::of);
        SpendingInsights insights = insightsResult.value()
            .orElseGet(() -> new SpendingInsights("Insights omitted due to budget constraints."));

        collectDegradation("rewards", rewardsResult.executionMode(), rewardsResult.omitted(), omitted, fallback, approximated);
        collectDegradation("offers", offersResult.executionMode(), offersResult.omitted(), omitted, fallback, approximated);
        collectDegradation("insights", insightsResult.executionMode(), insightsResult.omitted(), omitted, fallback, approximated);

        DegradationMetadata metadata = new DegradationMetadata(
            !omitted.isEmpty() || !fallback.isEmpty() || !approximated.isEmpty(),
            omitted,
            fallback,
            approximated
        );

        return new DashboardResponse(balance, transactions, rewards, offers, insights, metadata);
    }

    private void collectDegradation(
        String taskName,
        ExecutionMode mode,
        boolean omittedByPolicy,
        List<String> omitted,
        List<String> fallback,
        List<String> approximated
    ) {
        if (omittedByPolicy || mode == ExecutionMode.OMIT) {
            omitted.add(taskName);
            return;
        }

        if (mode == ExecutionMode.EXECUTE_WITH_FALLBACK) {
            fallback.add(taskName);
            return;
        }

        if (mode == ExecutionMode.EXECUTE_APPROXIMATE) {
            approximated.add(taskName);
        }
    }
}
