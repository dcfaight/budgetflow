package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.RequestExecutionResult;
import com.budgetflow.core.api.TaskResult;
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
        TaskSpec<Balance> balanceTask = TaskSpec.mandatory("balance", Duration.ofMillis(40), () -> balanceClient.getBalance(accountId));
        TaskSpec<List<Transaction>> transactionsTask = TaskSpec.mandatory(
            "transactions",
            Duration.ofMillis(65),
            () -> transactionClient.getTransactions(accountId)
        );
        TaskSpec<RewardsSummary> rewardsTask = TaskSpec.important(
            "rewards",
            Duration.ofMillis(90),
            () -> rewardsClient.getRewards(accountId)
        ).withFallback(() -> rewardsClient.getCachedRewards(accountId));
        TaskSpec<List<Offer>> offersTask = TaskSpec.optional(
            "offers",
            Duration.ofMillis(110),
            () -> offersClient.getOffers(accountId)
        )
            .withFallback(() -> offersClient.getCachedOffers(accountId))
            .withApproximate(() -> offersClient.getApproximateOffers(accountId));
        TaskSpec<SpendingInsights> insightsTask = TaskSpec.optional(
            "insights",
            Duration.ofMillis(140),
            () -> insightsClient.getInsights(accountId)
        );

        RequestExecutionResult executionResult = adaptiveExecutor.executeRequest(List.of(
            balanceTask,
            transactionsTask,
            rewardsTask,
            offersTask,
            insightsTask
        )).toCompletableFuture().join();

        TaskResult<Balance> balanceResult = executionResult.taskResult("balance");
        TaskResult<List<Transaction>> transactionsResult = executionResult.taskResult("transactions");
        TaskResult<RewardsSummary> rewardsResult = executionResult.taskResult("rewards");
        TaskResult<List<Offer>> offersResult = executionResult.taskResult("offers");
        TaskResult<SpendingInsights> insightsResult = executionResult.taskResult("insights");

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

        return new DashboardResponse(balance, transactions, rewards, offers, insights, metadata, executionResult.decisionTrace());
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
