package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.TaskResult;
import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.classification.ExecutionMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void degradationMetadataMatchesTaskResults() {
        AdaptiveExecutor adaptiveExecutor = mock(AdaptiveExecutor.class);
        BalanceClient balanceClient = mock(BalanceClient.class);
        TransactionClient transactionClient = mock(TransactionClient.class);
        RewardsClient rewardsClient = mock(RewardsClient.class);
        OffersClient offersClient = mock(OffersClient.class);
        InsightsClient insightsClient = mock(InsightsClient.class);

        when(adaptiveExecutor.execute(any())).thenAnswer(invocation -> {
            TaskSpec<?> task = invocation.getArgument(0);
            CompletionStage result = switch (task.taskName()) {
                case "balance" -> CompletableFuture.completedFuture(
                    TaskResult.executed(new Balance("acc-123", BigDecimal.TEN), ExecutionMode.EXECUTE, "normal")
                );
                case "transactions" -> CompletableFuture.completedFuture(
                    TaskResult.executed(List.of(new Transaction("t-1", "Coffee", BigDecimal.ONE)), ExecutionMode.EXECUTE, "normal")
                );
                case "rewards" -> CompletableFuture.completedFuture(
                    TaskResult.executed(new RewardsSummary(900), ExecutionMode.EXECUTE_WITH_FALLBACK, "fallback_selected_by_policy")
                );
                case "offers" -> CompletableFuture.completedFuture(
                    TaskResult.executed(List.of(new Offer("Approximate offer")), ExecutionMode.EXECUTE_APPROXIMATE, "approximate_selected_by_policy")
                );
                case "insights" -> CompletableFuture.completedFuture(TaskResult.omitted("omitted_by_policy"));
                default -> throw new IllegalStateException("Unexpected task: " + task.taskName());
            };
            return result;
        });

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
    }
}
