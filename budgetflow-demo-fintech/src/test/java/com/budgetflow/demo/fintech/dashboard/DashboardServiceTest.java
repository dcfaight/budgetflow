package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.RequestExecutionResult;
import com.budgetflow.core.api.TaskResult;
import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.policy.DecisionTraceEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void degradationMetadataMatchesTaskResults() {
        AdaptiveExecutor adaptiveExecutor = mock(AdaptiveExecutor.class);
        BalanceClient balanceClient = mock(BalanceClient.class);
        TransactionClient transactionClient = mock(TransactionClient.class);
        RewardsClient rewardsClient = mock(RewardsClient.class);
        OffersClient offersClient = mock(OffersClient.class);
        InsightsClient insightsClient = mock(InsightsClient.class);

        when(adaptiveExecutor.executeRequest(any())).thenReturn(CompletableFuture.completedFuture(
            new RequestExecutionResult(
                Map.of(
                    "balance", TaskResult.executed(new Balance("acc-123", BigDecimal.TEN), ExecutionMode.EXECUTE, "normal"),
                    "transactions", TaskResult.executed(
                        List.of(new Transaction("t-1", "Coffee", BigDecimal.ONE)),
                        ExecutionMode.EXECUTE,
                        "normal"
                    ),
                    "rewards", TaskResult.executed(
                        new RewardsSummary(900),
                        ExecutionMode.EXECUTE_WITH_FALLBACK,
                        "fallback_selected_by_policy"
                    ),
                    "offers", TaskResult.executed(
                        List.of(new Offer("Approximate offer")),
                        ExecutionMode.EXECUTE_APPROXIMATE,
                        "approximate_selected_by_policy"
                    ),
                    "insights", TaskResult.omitted("omitted_by_policy")
                ),
                List.of(
                    new DecisionTraceEntry("balance", ExecutionMode.EXECUTE, "normal", Duration.ofMillis(40), Duration.ofMillis(250)),
                    new DecisionTraceEntry("insights", ExecutionMode.OMIT, "omitted_by_policy", Duration.ofMillis(140), Duration.ofMillis(60))
                )
            )
        ));

        DashboardService service = new DashboardService(
            adaptiveExecutor,
            balanceClient,
            transactionClient,
            rewardsClient,
            offersClient,
            insightsClient
        );

        DashboardResponse response = service.getDashboard("acc-123");

        assertTrue(response.degradationMetadata().degraded());
        assertEquals(List.of("insights"), response.degradationMetadata().omittedTasks());
        assertEquals(List.of("rewards"), response.degradationMetadata().fallbackTasks());
        assertEquals(List.of("offers"), response.degradationMetadata().approximatedTasks());
        assertEquals(2, response.decisionTrace().size());
    }
}
