package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.RequestExecutionResult;
import com.budgetflow.core.api.TaskResult;
import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.metadata.RequestExecutionDiagnostics;
import com.budgetflow.core.metadata.RequestExecutionDiagnosticsFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class DashboardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardService.class);

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

        RewardsSummary rewards = rewardsResult.value().orElseGet(() -> new RewardsSummary(0));
        List<Offer> offers = offersResult.value().orElseGet(List::of);
        SpendingInsights insights = insightsResult.value()
            .orElseGet(() -> new SpendingInsights("Insights omitted due to budget constraints."));

        RequestExecutionDiagnostics diagnostics = executionResult.diagnostics();
        String executionSummary = RequestExecutionDiagnosticsFormatter.formatSummary(diagnostics, executionResult.decisionTrace());
        LOGGER.info("dashboard_execution accountId={} {}", accountId, executionSummary);

        return new DashboardResponse(
            balance,
            transactions,
            rewards,
            offers,
            insights,
            diagnostics,
            executionResult.decisionTrace(),
            executionSummary
        );
    }
}
