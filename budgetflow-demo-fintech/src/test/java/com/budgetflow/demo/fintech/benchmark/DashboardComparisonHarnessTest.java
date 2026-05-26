package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.demo.fintech.dashboard.SimulationSupport;
import com.budgetflow.core.policy.PlannerPolicyProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

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
            assertEquals(List.of(), constrainedAdaptive.approximatedTasks());

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
                "balanced",
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
        assertTrue(output.contains("Best for: first-time repo evaluation and baseline adaptive-vs-naive comparison"));
        assertTrue(output.contains("Suggested run: ./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=default\""));
        assertTrue(output.contains("Evaluation entry: ./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough"));
        assertTrue(output.contains("Scenario: constrained_budget_elevated_pressure — Constrained budget / elevated pressure"));
        assertTrue(output.contains("Focus: Mixed-constraint stress: validates combined budget + runtime pressure behavior."));
        assertTrue(output.contains("Interpretation: Interpret differences conservatively: this demonstrates policy reaction shape, not production throughput ceilings."));
        assertTrue(output.contains("Pattern: Traffic surge plus dependency stress during peak payment windows."));
        assertTrue(output.contains("Observe: Look for important-task fallback and optional-task approximation/omission with clear trace reasons."));
        assertTrue(output.contains("Strategy | Policy | Executed | Degraded | Work | Omitted | Fallback | Approx | Assessment | Why"));
        assertTrue(output.contains("budgetflow_adaptive | balanced | 4 | true | 430ms/123ms | insights | rewards | offers |"));
        assertTrue(output.contains("Scorecards:"));
    }

    @Test
    void formatterAddsComparisonTakeawayAndRicherConfidenceSummary() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.extendedPack();
            String output = DashboardBenchmarkFormatter.format(pack, harness.run(pack.scenarios()));

            assertTrue(output.contains("Scenario: tight_budget_low_pressure"));
            assertTrue(output.contains("Comparison takeaway:"));
            assertTrue(output.contains("baseline_convergence="));
            assertTrue(output.contains("adaptive_plan_changes="));
            assertTrue(output.contains("profile_comparison_scenarios="));
        }
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

            assertEquals(14, summaries.size());
            summaries.forEach(s -> assertTrue(s.totalTasksExecuted() >= 0));
        }
    }

    @Test
    void policyProfileComparisonRunProducesVariantRowsAndDeltaLine() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.policyPack();
            String output = DashboardBenchmarkFormatter.format(
                pack,
                harness.run(
                    pack.scenarios(),
                    List.of(
                        PlannerPolicyProfile.BALANCED,
                        PlannerPolicyProfile.CONTINUITY,
                        PlannerPolicyProfile.EFFICIENCY
                    )
                )
            );

            assertTrue(output.contains("budgetflow_adaptive | balanced"));
            assertTrue(output.contains("budgetflow_adaptive | continuity"));
            assertTrue(output.contains("budgetflow_adaptive | efficiency"));
            assertTrue(output.contains("Policy delta vs balanced"));
            assertTrue(output.contains("Scenario summary:"));
            assertTrue(output.contains("Profile guidance:"));
            assertTrue(output.contains("Evaluator next step:"));
        }
    }

    @Test
    void formatterCanEmitMachineReadableJson() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.realismPack();
            String json = DashboardBenchmarkFormatter.formatJson(pack, harness.run(pack.scenarios()));

            assertTrue(json.contains("\"tool\":\"budgetflow_dashboard_comparison\""));
            assertTrue(json.contains("\"scenarioPack\":{\"name\":\"realism\""));
            assertTrue(json.contains("\"bestFor\":\"recognizable real-world pressure patterns and JSON-friendly sharing\""));
            assertTrue(json.contains("\"suggestedCommand\":\"./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\\\"--pack=realism --json\\\"\""));
            assertTrue(json.contains("\"name\":\"budgetflow_adaptive\""));
            assertTrue(json.contains("\"policyProfile\":\"balanced\""));
            assertTrue(json.contains("\"comparison\":{"));
            assertTrue(json.contains("\"comparisonTakeaway\":"));
            assertTrue(json.contains("\"adaptiveChanges\":"));
            assertTrue(json.contains("\"profileSummary\":"));
            assertTrue(json.contains("\"scorecards\":"));
            assertTrue(json.contains("\"profileGuidance\":"));
            assertTrue(json.contains("\"evaluatorNextStep\":"));
            assertTrue(json.contains("\"evaluationFocus\":"));
            assertTrue(json.contains("\"interpretationGuidance\":"));
            assertTrue(json.contains("\"realWorldPattern\":"));
            assertTrue(json.contains("\"whatToObserve\":"));
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
            assertTrue(output.contains("Comparison takeaway:"));
            assertTrue(output.contains("adaptive_changes="));
            assertTrue(output.contains("Confidence summary: scenarios_compared="));
            assertTrue(output.contains("Prototype reminder: comparison output is for exploratory evaluation, not benchmark certification."));
        }
    }

    @Test
    void formatterConfidenceSummaryAppearsInJsonOutput() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.defaultPack();
            String json = DashboardBenchmarkFormatter.formatJson(pack, harness.run(pack.scenarios()));

            assertTrue(json.contains("\"confidenceSummary\":{"));
            assertTrue(json.contains("\"scenariosCompared\":"));
            assertTrue(json.contains("\"adaptiveLowerProjectedWorkCount\":"));
            assertTrue(json.contains("\"baselineConvergenceCount\":"));
            assertTrue(json.contains("\"adaptivePlanChangeCount\":"));
        }
    }

    @Test
    void harnessMainCanExportJsonReportToFile() throws IOException {
        Path outputPath = Path.of("/tmp/budgetflow-harness-export.json");
        Files.deleteIfExists(outputPath);

        DashboardComparisonHarness.main(new String[] {"--pack=default", "--json", "--out=" + outputPath});

        assertTrue(Files.exists(outputPath));
        String exported = Files.readString(outputPath);
        assertTrue(exported.contains("\"tool\":\"budgetflow_dashboard_comparison\""));
        assertTrue(exported.contains("\"scenarioPack\":{\"name\":\"default\""));
    }

    @Test
    void formatterCanEmitMarkdownEvidenceWithScorecards() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.agentPack();
            String markdown = DashboardBenchmarkFormatter.formatMarkdown(
                pack,
                harness.run(pack.scenarios(), List.of(
                    PlannerPolicyProfile.BALANCED,
                    PlannerPolicyProfile.CONTINUITY,
                    PlannerPolicyProfile.EFFICIENCY,
                    PlannerPolicyProfile.LATENCY_FIRST
                ))
            );

            assertTrue(markdown.contains("# BudgetFlow comparison evidence"));
            assertTrue(markdown.contains("| Strategy | Policy | Mandatory preserved | Optional aligned | Fallback aligned | Intent matched | Assessment |"));
            assertTrue(markdown.contains("Scorecard summary:"));
        }
    }

    @Test
    void harnessMainCanExportMarkdownReportToFile() throws IOException {
        Path outputPath = Path.of("/tmp/budgetflow-harness-export.md");
        Files.deleteIfExists(outputPath);

        DashboardComparisonHarness.main(new String[] {"--pack=agent", "--markdown", "--out=" + outputPath});

        assertTrue(Files.exists(outputPath));
        String exported = Files.readString(outputPath);
        assertTrue(exported.contains("# BudgetFlow comparison evidence"));
        assertTrue(exported.contains("Scorecard summary:"));
    }

    @Test
    void walkthroughProvidesGuidedAdoptionFlow() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured));
            DashboardWalkthrough.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }

        String output = captured.toString();
        assertTrue(output.contains("BudgetFlow prototype walkthrough"));
        assertTrue(output.contains("./gradlew :budgetflow-demo-fintech:bootRun"));
        assertTrue(output.contains("./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=default\""));
        assertTrue(output.contains("./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=adoption\""));
        assertTrue(output.contains("./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=policy --policies=balanced,continuity,efficiency\""));
        assertTrue(output.contains("/tmp/budgetflow-realism.json"));
        assertTrue(output.contains("docs/planner-customization.md"));
        assertTrue(output.contains("not a production-ready platform"));
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
