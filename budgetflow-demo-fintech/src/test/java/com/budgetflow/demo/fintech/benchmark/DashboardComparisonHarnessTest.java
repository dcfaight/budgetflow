package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.demo.fintech.dashboard.SimulationSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardComparisonHarnessTest {

    @Test
    void comparisonHighlightsNaiveVsAdaptiveTradeoffsAcrossDefaultScenarios() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            List<DashboardBenchmarkSummary> summaries = harness.runDefaultScenarios();

            DashboardBenchmarkSummary generousAdaptive = summaryFor(summaries, "generous_budget_low_pressure", "budgetflow_adaptive");
            assertEquals(5, generousAdaptive.totalTasksExecuted());
            assertFalse(generousAdaptive.degraded());
            assertEquals(List.of(), generousAdaptive.omittedTasks());
            assertEquals(List.of(), generousAdaptive.fallbackTasks());
            assertEquals(List.of(), generousAdaptive.approximatedTasks());

            DashboardBenchmarkSummary constrainedNaive = summaryFor(summaries, "constrained_budget_low_pressure", "naive_parallel");
            assertEquals(5, constrainedNaive.totalTasksExecuted());
            assertTrue(constrainedNaive.degraded());
            assertEquals(List.of(), constrainedNaive.omittedTasks());
            assertEquals(List.of(), constrainedNaive.fallbackTasks());
            assertEquals(List.of(), constrainedNaive.approximatedTasks());

            DashboardBenchmarkSummary constrainedAdaptive = summaryFor(summaries, "constrained_budget_low_pressure", "budgetflow_adaptive");
            assertEquals(4, constrainedAdaptive.totalTasksExecuted());
            assertTrue(constrainedAdaptive.degraded());
            assertEquals(List.of("insights"), constrainedAdaptive.omittedTasks());
            assertEquals(List.of(), constrainedAdaptive.fallbackTasks());
            assertEquals(List.of("offers"), constrainedAdaptive.approximatedTasks());

            DashboardBenchmarkSummary elevatedAdaptive = summaryFor(summaries, "constrained_budget_elevated_pressure", "budgetflow_adaptive");
            assertEquals(3, elevatedAdaptive.totalTasksExecuted());
            assertTrue(elevatedAdaptive.degraded());
            assertEquals(List.of("offers", "insights"), elevatedAdaptive.omittedTasks());
            assertEquals(List.of("rewards"), elevatedAdaptive.fallbackTasks());
            assertEquals(List.of(), elevatedAdaptive.approximatedTasks());
        }
    }

    @Test
    void formatterIncludesRequestedComparisonFields() {
        String output = DashboardBenchmarkFormatter.format(List.of(
            new DashboardBenchmarkSummary(
                "scenario-a",
                "budgetflow_adaptive",
                3,
                List.of("insights"),
                List.of("rewards"),
                List.of("offers"),
                true,
                java.time.Duration.ofMillis(430),
                java.time.Duration.ofMillis(115),
                new com.budgetflow.core.policy.SystemPressureSnapshot(0.90, 0.88, 0.92)
            )
        ));

        assertTrue(output.contains("Scenario | Strategy | Executed | Omitted | Fallback | Approximated | Degraded | Budget/Work | Pressure"));
        assertTrue(output.contains("scenario-a | budgetflow_adaptive | 3 | insights | rewards | offers | true | 430ms/115ms | exec=0.90 db=0.88 down=0.92"));
    }

    @Test
    void generousBudgetElevatedPressureAdaptiveDegradesOptionalAndEnrichesImportantWithFallback() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            List<DashboardBenchmarkSummary> summaries = harness.run(List.of(
                PressureScenarios.generousBudgetElevatedPressure()
            ));

            DashboardBenchmarkSummary adaptive = summaryFor(summaries, "generous_budget_elevated_pressure", "budgetflow_adaptive");
            // Mandatory tasks (balance, transactions) always execute
            // Important task (rewards) gets fallback under high pressure
            // Optional tasks (offers, insights) are omitted under high pressure
            assertEquals(List.of("rewards"), adaptive.fallbackTasks());
            assertEquals(List.of("offers", "insights"), adaptive.omittedTasks());
            assertTrue(adaptive.degraded());

            DashboardBenchmarkSummary naive = summaryFor(summaries, "generous_budget_elevated_pressure", "naive_parallel");
            // Naive always runs everything — generous budget means no degradation flag
            assertEquals(5, naive.totalTasksExecuted());
            assertFalse(naive.degraded());
        }
    }

    @Test
    void extendedScenariosFromPressureScenariosAreRunnable() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            List<DashboardBenchmarkScenario> extended = List.of(
                PressureScenarios.generousBudgetLowPressure(),
                PressureScenarios.constrainedBudgetLowPressure(),
                PressureScenarios.constrainedBudgetElevatedPressure(),
                PressureScenarios.generousBudgetElevatedPressure(),
                PressureScenarios.tightBudgetModerateDbPressure()
            );

            List<DashboardBenchmarkSummary> summaries = harness.run(extended);

            // 5 scenarios × 2 strategies = 10 summaries
            assertEquals(10, summaries.size());
            summaries.forEach(s -> assertTrue(s.totalTasksExecuted() >= 0));
        }
    }

    private DashboardBenchmarkSummary summaryFor(
        List<DashboardBenchmarkSummary> summaries,
        String scenarioName,
        String strategy
    ) {
        return summaries.stream()
            .filter(summary -> summary.scenarioName().equals(scenarioName))
            .filter(summary -> summary.executionStrategy().equals(strategy))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing summary for " + scenarioName + " / " + strategy));
    }

    private static final class NoDelaySimulationSupport extends SimulationSupport {
        @Override
        public void delay(long millis) {
        }
    }
}
