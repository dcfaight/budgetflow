package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.policy.PlannerPolicyProfile;
import com.budgetflow.demo.fintech.dashboard.DashboardTaskSpecs;
import com.budgetflow.demo.fintech.dashboard.SimulationSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight regression assertions for the agent-oriented evaluation pack.
 *
 * <p>These checks protect the current agent evaluation story from semantic drift:
 * <ul>
 *   <li>Mandatory work (balance, transactions) must always be preserved across all agent scenarios.</li>
 *   <li>The healthy coordination scenario must not produce a cascade fallback under balanced profile.</li>
 *   <li>The degraded-cascade scenario must produce the expected deterministic cascade behavior.</li>
 *   <li>Profile comparison must show observable differentiation between balanced and latency_first.</li>
 *   <li>No agent pack scorecard must reach a {@code MISMATCHED} disposition across any profile.</li>
 * </ul>
 */
class AgentEvalPackRegressionTest {

    @Test
    void mandatoryWorkPreservedAcrossAllAgentScenariosAndProfiles() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.agentPack();
            List<DashboardBenchmarkSummary> summaries = harness.run(
                pack.scenarios(),
                AgentEvalReporter.AGENT_EVAL_PROFILES
            );

            List<DashboardBenchmarkSummary> adaptiveSummaries = summaries.stream()
                .filter(s -> "budgetflow_adaptive".equals(s.executionStrategy()))
                .toList();

            for (DashboardBenchmarkSummary summary : adaptiveSummaries) {
                assertFalse(
                    summary.omittedTasks().contains(DashboardTaskSpecs.BALANCE_TASK),
                    "balance must never be omitted — scenario=" + summary.scenarioName()
                        + " profile=" + summary.policyProfile()
                );
                assertFalse(
                    summary.omittedTasks().contains(DashboardTaskSpecs.TRANSACTIONS_TASK),
                    "transactions must never be omitted — scenario=" + summary.scenarioName()
                        + " profile=" + summary.policyProfile()
                );
            }
        }
    }

    @Test
    void agentCoordinationHealthyDegradedPlanFitsWithinBudgetUnlikesCascade() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            List<DashboardBenchmarkSummary> healthySummaries = harness.run(
                List.of(PressureScenarios.agentCoordinationHealthy()),
                List.of(PlannerPolicyProfile.BALANCED)
            );
            List<DashboardBenchmarkSummary> cascadeSummaries = harness.run(
                List.of(PressureScenarios.agentCoordinationDegradedCascade()),
                List.of(PlannerPolicyProfile.BALANCED)
            );

            DashboardBenchmarkSummary healthy = summaryFor(healthySummaries, "agent_coordination_healthy", "budgetflow_adaptive");
            DashboardBenchmarkSummary cascade = summaryFor(cascadeSummaries, "agent_coordination_degraded_cascade", "budgetflow_adaptive");

            assertFalse(healthy.omittedTasks().contains(DashboardTaskSpecs.BALANCE_TASK),
                "balance must not be omitted in healthy coordination");
            assertFalse(healthy.omittedTasks().contains(DashboardTaskSpecs.TRANSACTIONS_TASK),
                "transactions must not be omitted in healthy coordination");

            // Healthy: degraded plan still fits within budget (planner found a viable plan)
            assertTrue(
                healthy.projectedWork().compareTo(healthy.requestBudget()) <= 0,
                "healthy scenario degraded plan must fit within budget"
                    + " (projected=" + healthy.projectedWork().toMillis() + "ms"
                    + ", budget=" + healthy.requestBudget().toMillis() + "ms)"
            );
            // Cascade: mandatory work alone exceeds the deliberately-too-small budget (boundary case)
            assertTrue(
                cascade.projectedWork().compareTo(cascade.requestBudget()) > 0,
                "cascade scenario projected work must exceed budget (mandatory tasks cannot fit)"
                    + " (projected=" + cascade.projectedWork().toMillis() + "ms"
                    + ", budget=" + cascade.requestBudget().toMillis() + "ms)"
            );
        }
    }

    @Test
    void agentCoordinationDegradedCascadeProducesDeterministicCascade() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            List<DashboardBenchmarkSummary> summaries = harness.run(
                List.of(PressureScenarios.agentCoordinationDegradedCascade()),
                List.of(PlannerPolicyProfile.BALANCED)
            );

            DashboardBenchmarkSummary adaptive = summaryFor(summaries, "agent_coordination_degraded_cascade", "budgetflow_adaptive");
            assertTrue(adaptive.degraded(), "cascade scenario must be degraded under joint budget+pressure constraint");
            assertTrue(
                adaptive.omittedTasks().contains(DashboardTaskSpecs.INSIGHTS_TASK),
                "insights must be omitted in cascade scenario"
            );
            assertTrue(
                adaptive.fallbackTasks().contains(DashboardTaskSpecs.REWARDS_TASK),
                "rewards must fall back in cascade scenario"
            );
            assertFalse(
                adaptive.omittedTasks().contains(DashboardTaskSpecs.BALANCE_TASK),
                "balance must not be omitted even in cascade scenario"
            );
            assertFalse(
                adaptive.omittedTasks().contains(DashboardTaskSpecs.TRANSACTIONS_TASK),
                "transactions must not be omitted even in cascade scenario"
            );
        }
    }

    @Test
    void agentProfileComparisonShowsLatencyFirstOmitsAtLeastAsMuchAsBalanced() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            List<DashboardBenchmarkSummary> summaries = harness.run(
                List.of(PressureScenarios.agentProfileComparison()),
                AgentEvalReporter.AGENT_EVAL_PROFILES
            );

            DashboardBenchmarkSummary balanced = summaryForProfile(summaries, "agent_profile_comparison", "balanced");
            DashboardBenchmarkSummary latencyFirst = summaryForProfile(summaries, "agent_profile_comparison", "latency_first");

            assertTrue(
                latencyFirst.omittedTasks().size() >= balanced.omittedTasks().size(),
                "latency_first must omit >= optional tasks compared to balanced"
                    + " (latency_first=" + latencyFirst.omittedTasks().size()
                    + ", balanced=" + balanced.omittedTasks().size() + ")"
            );
        }
    }

    @Test
    void agentPackScorecardsContainNoMismatchedDispositionsAcrossAllProfiles() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.agentPack();
            List<DashboardBenchmarkSummary> summaries = harness.run(
                pack.scenarios(),
                AgentEvalReporter.AGENT_EVAL_PROFILES
            );

            summaries.stream()
                .collect(Collectors.groupingBy(
                    DashboardBenchmarkSummary::scenarioName,
                    java.util.LinkedHashMap::new,
                    Collectors.toList()
                ))
                .forEach((scenarioName, group) -> {
                    List<ScenarioAssessmentScorer.ScenarioScorecard> scorecards =
                        ScenarioAssessmentScorer.scorecards(group);
                    for (ScenarioAssessmentScorer.ScenarioScorecard scorecard : scorecards) {
                        assertNotEquals(
                            ScenarioAssessmentScorer.AssessmentDisposition.MISMATCHED,
                            scorecard.disposition(),
                            "scorecard must not be MISMATCHED — scenario=" + scenarioName
                                + " strategy=" + scorecard.executionStrategy()
                                + " profile=" + scorecard.policyProfile()
                        );
                    }
                });
        }
    }

    @Test
    void agentEvalReporterGeneratesAgentPackWithAllFourProfiles() {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.agentPack();
            List<DashboardBenchmarkSummary> summaries = harness.run(
                pack.scenarios(),
                AgentEvalReporter.AGENT_EVAL_PROFILES
            );

            String json = DashboardBenchmarkFormatter.formatJson(pack, summaries);
            String markdown = DashboardBenchmarkFormatter.formatMarkdown(pack, summaries);

            assertTrue(json.contains("\"tool\":\"budgetflow_dashboard_comparison\""));
            assertTrue(json.contains("\"name\":\"agent\""));
            assertTrue(markdown.contains("## Review interpretation"));
            assertTrue(markdown.contains("**Mandatory preservation:**"));
            assertTrue(markdown.contains("**Scorecard dispositions:**"));
            assertTrue(markdown.contains("**Profile differentiation:**"));
            assertTrue(markdown.contains("**Reviewer note:**"));
            assertTrue(markdown.contains("latency_first"));
        }
    }

    private DashboardBenchmarkSummary summaryFor(
        List<DashboardBenchmarkSummary> summaries,
        String scenarioName,
        String strategy
    ) {
        return summaries.stream()
            .filter(s -> s.scenarioName().equals(scenarioName))
            .filter(s -> s.executionStrategy().equals(strategy))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing summary for " + scenarioName + " / " + strategy));
    }

    private DashboardBenchmarkSummary summaryForProfile(
        List<DashboardBenchmarkSummary> summaries,
        String scenarioName,
        String profile
    ) {
        return summaries.stream()
            .filter(s -> s.scenarioName().equals(scenarioName))
            .filter(s -> "budgetflow_adaptive".equals(s.executionStrategy()))
            .filter(s -> s.policyProfile().equals(profile))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "Missing summary for " + scenarioName + " / budgetflow_adaptive / " + profile));
    }

    private static final class NoDelaySimulationSupport extends SimulationSupport {
        @Override
        public void delay(long millis) {
        }
    }
}
