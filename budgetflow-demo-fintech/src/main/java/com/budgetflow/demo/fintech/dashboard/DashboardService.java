package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.AdaptiveRequest;
import com.budgetflow.core.api.AdaptiveRequestResult;
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
        AdaptiveRequest request = DashboardTaskSpecs.forAccount(
            accountId,
            balanceClient,
            transactionClient,
            rewardsClient,
            offersClient,
            insightsClient
        );

        AdaptiveRequestResult result = request.execute(adaptiveExecutor).toCompletableFuture().join();

        Balance balance = result.require(DashboardTaskSpecs.BALANCE_KEY);
        List<Transaction> transactions = result.require(DashboardTaskSpecs.TRANSACTIONS_KEY);
        RewardsSummary rewards = result.get(DashboardTaskSpecs.REWARDS_KEY)
            .orElseGet(() -> new RewardsSummary(0));
        List<Offer> offers = result.get(DashboardTaskSpecs.OFFERS_KEY)
            .orElseGet(List::of);
        SpendingInsights insights = result.get(DashboardTaskSpecs.INSIGHTS_KEY)
            .orElseGet(() -> new SpendingInsights("Insights omitted due to budget constraints."));

        RequestExecutionDiagnostics diagnostics = result.diagnostics();
        String executionSummary = RequestExecutionDiagnosticsFormatter.formatSummary(diagnostics, result.decisionTrace());
        LOGGER.info("dashboard_execution accountId={} {}", accountId, executionSummary);

        return new DashboardResponse(
            balance,
            transactions,
            rewards,
            offers,
            insights,
            diagnostics,
            result.decisionTrace(),
            executionSummary
        );
    }
}
