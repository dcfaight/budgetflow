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
        List<TaskSpec<?>> taskSpecs = DashboardTaskSpecs.forAccount(
            accountId,
            balanceClient,
            transactionClient,
            rewardsClient,
            offersClient,
            insightsClient
        );

        RequestExecutionResult executionResult = adaptiveExecutor.executeRequest(taskSpecs).toCompletableFuture().join();

        TaskResult<Balance> balanceResult = executionResult.taskResult(DashboardTaskSpecs.BALANCE_TASK);
        TaskResult<List<Transaction>> transactionsResult = executionResult.taskResult(DashboardTaskSpecs.TRANSACTIONS_TASK);
        TaskResult<RewardsSummary> rewardsResult = executionResult.taskResult(DashboardTaskSpecs.REWARDS_TASK);
        TaskResult<List<Offer>> offersResult = executionResult.taskResult(DashboardTaskSpecs.OFFERS_TASK);
        TaskResult<SpendingInsights> insightsResult = executionResult.taskResult(DashboardTaskSpecs.INSIGHTS_TASK);

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
