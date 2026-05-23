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
            assertEquals(4, elevatedAdaptive.totalTasksExecuted());
            assertTrue(elevatedAdaptive.degraded());
            assertEquals(List.of("insights"), elevatedAdaptive.omittedTasks());
            assertEquals(List.of("rewards"), elevatedAdaptive.fallbackTasks());
            assertEquals(List.of("offers"), elevatedAdaptive.approximatedTasks());
        }
    }

    @Test
    void formatterIncludesRequestedComparisonFields() {
        DashboardBenchmarkScenario scenario = PressureScenarios.constrainedBudgetElevatedPressure();
        String output = DashboardBenchmarkFormatter.format(PressureScenarios.defaultPack(), List.of(
            new DashboardBenchmarkSummary(
                scenario,
                "budgetflow_adaptive",
                4,
                List.of("insights"),
                List.of("rewards"),
                List.of("offers"),
                true,
                java.time.Duration.ofMillis(123),
                List.of("offers=approximate_selected_by_policy[pressure=high:downstream,budget=tight,latency_ratio=0.49]")
            )
        ));

        assertTrue(output.contains("BudgetFlow dashboard comparison"));
        assertTrue(output.contains("Scenario: constrained_budget_elevated_pressure — Constrained budget / elevated pressure"));
        assertTrue(output.contains("Strategy | Executed | Degraded | Work | Omitted | Fallback | Approx | Why"));
        assertTrue(output.contains("budgetflow_adaptive | 4 | true | 430ms/123ms | insights | rewards | offers | offers=approximate_selected_by_policy"));
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
            // Optional tasks prefer degraded execution first, with omission used more selectively
            assertEquals(List.of("rewards"), adaptive.fallbackTasks());
            assertEquals(List.of("insights"), adaptive.omittedTasks());
            assertEquals(List.of("offers"), adaptive.approximatedTasks());
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
            List<DashboardBenchmarkScenario> extended = PressureScenarios.extendedPack().scenarios();

            List<DashboardBenchmarkSummary> summaries = harness.run(extended);

            assertEquals(12, summaries.size());
            summaries.forEach(s -> assertTrue(s.totalTasksExecuted() >= 0));
        }
    }

    @Test
    void formatterCanEmitMachineReadableJson() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.realismPack();
            String json = DashboardBenchmarkFormatter.formatJson(pack, harness.run(pack.scenarios()));

            assertTrue(json.contains("\"tool\":\"budgetflow_dashboard_comparison\""));
            assertTrue(json.contains("\"scenarioPack\":{\"name\":\"realism\""));
            assertTrue(json.contains("\"name\":\"budgetflow_adaptive\""));
            assertTrue(json.contains("\"comparison\":{"));
            assertTrue(json.contains("\"adaptiveChanges\":"));
        }
    }

    @Test
    void formatterOutputRetainsStableScenarioGroupingAndComparisonLine() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.defaultPack();
            String output = DashboardBenchmarkFormatter.format(pack, harness.run(pack.scenarios()));

            int generousIndex = output.indexOf("Scenario: generous_budget_low_pressure");
            int constrainedIndex = output.indexOf("Scenario: constrained_budget_low_pressure");
            int elevatedIndex = output.indexOf("Scenario: constrained_budget_elevated_pressure");

            assertTrue(generousIndex >= 0);
            assertTrue(constrainedIndex > generousIndex);
            assertTrue(elevatedIndex > constrainedIndex);
            assertTrue(output.contains("Comparison: adaptive projected work delta="));
            assertTrue(output.contains("adaptive_changes="));
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
