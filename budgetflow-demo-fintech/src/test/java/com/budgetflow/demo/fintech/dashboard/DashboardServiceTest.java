package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.RequestExecutionResult;
import com.budgetflow.core.api.TaskResult;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
import com.budgetflow.core.metadata.RequestExecutionDiagnostics;
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
    void diagnosticsAndSummaryMatchExecutionResultsAndTrace() {
        AdaptiveExecutor adaptiveExecutor = mock(AdaptiveExecutor.class);
        BalanceClient balanceClient = mock(BalanceClient.class);
        TransactionClient transactionClient = mock(TransactionClient.class);
        RewardsClient rewardsClient = mock(RewardsClient.class);
        OffersClient offersClient = mock(OffersClient.class);
        InsightsClient insightsClient = mock(InsightsClient.class);

        when(adaptiveExecutor.executeRequest(any())).thenReturn(CompletableFuture.completedFuture(
            executionResultFixture()
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

        assertTrue(response.diagnostics().degraded());
        assertEquals(Duration.ofMillis(250), response.diagnostics().totalRequestBudget());
        assertEquals(Duration.ofMillis(60), response.diagnostics().remainingRequestBudget());
        assertEquals(List.of("insights"), response.diagnostics().omittedTaskNames());
        assertEquals(List.of("rewards"), response.diagnostics().fallbackTaskNames());
        assertEquals(List.of("offers"), response.diagnostics().approximatedTaskNames());
        assertTrue(response.executionSummary().contains("degraded=true"));
        assertTrue(response.executionSummary().contains("offers=EXECUTE_APPROXIMATE"));
    }

    @Test
    void diagnosticsAreConsistentWithTraceAndVisibleInResponse() {
        AdaptiveExecutor adaptiveExecutor = mock(AdaptiveExecutor.class);
        when(adaptiveExecutor.executeRequest(any())).thenReturn(CompletableFuture.completedFuture(
            executionResultFixture()
        ));

        DashboardService service = new DashboardService(
            adaptiveExecutor,
            mock(BalanceClient.class),
            mock(TransactionClient.class),
            mock(RewardsClient.class),
            mock(OffersClient.class),
            mock(InsightsClient.class)
        );

        DashboardResponse response = service.getDashboard("acc-123");

        assertEquals(
            response.decisionTrace().stream()
                .filter(entry -> entry.selectedExecutionMode() == ExecutionMode.OMIT)
                .map(DecisionTraceEntry::taskName)
                .toList(),
            response.diagnostics().omittedTaskNames()
        );
        assertEquals(
            response.decisionTrace().stream()
                .filter(entry -> entry.selectedExecutionMode() == ExecutionMode.EXECUTE_WITH_FALLBACK)
                .map(DecisionTraceEntry::taskName)
                .toList(),
            response.diagnostics().fallbackTaskNames()
        );
        assertEquals(
            response.decisionTrace().stream()
                .filter(entry -> entry.selectedExecutionMode() == ExecutionMode.EXECUTE_APPROXIMATE)
                .map(DecisionTraceEntry::taskName)
                .toList(),
            response.diagnostics().approximatedTaskNames()
        );
    }

    private RequestExecutionResult executionResultFixture() {
        return new RequestExecutionResult(
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
                new DecisionTraceEntry(
                    "balance",
                    Importance.MANDATORY,
                    ExecutionMode.EXECUTE,
                    "normal",
                    Duration.ofMillis(40),
                    Duration.ofMillis(40),
                    Duration.ofMillis(250)
                ),
                new DecisionTraceEntry(
                    "rewards",
                    Importance.IMPORTANT,
                    ExecutionMode.EXECUTE_WITH_FALLBACK,
                    "fallback_selected_by_policy",
                    Duration.ofMillis(90),
                    Duration.ofMillis(60),
                    Duration.ofMillis(210)
                ),
                new DecisionTraceEntry(
                    "offers",
                    Importance.OPTIONAL,
                    ExecutionMode.EXECUTE_APPROXIMATE,
                    "approximate_selected_by_policy",
                    Duration.ofMillis(110),
                    Duration.ofMillis(40),
                    Duration.ofMillis(150)
                ),
                new DecisionTraceEntry(
                    "insights",
                    Importance.OPTIONAL,
                    ExecutionMode.OMIT,
                    "omitted_by_policy",
                    Duration.ofMillis(140),
                    Duration.ZERO,
                    Duration.ofMillis(60)
                )
            ),
            new RequestExecutionDiagnostics(
                Duration.ofMillis(250),
                Duration.ofMillis(60),
                true,
                List.of("insights"),
                List.of("rewards"),
                List.of("offers")
            )
        );
    }
}
