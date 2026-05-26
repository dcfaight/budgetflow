package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.policy.PlannerPolicyProfile;
import com.budgetflow.demo.fintech.dashboard.DashboardTaskSpecs;
import com.budgetflow.demo.fintech.dashboard.SimulationSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEvalBaselineSupportTest {

    @Test
    void baselineSnapshotCanBeSavedLoadedAndCompared() throws IOException {
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(new NoDelaySimulationSupport())) {
            DashboardScenarioPack pack = PressureScenarios.agentPack();
            List<DashboardBenchmarkSummary> baselineSummaries = harness.run(
                pack.scenarios(),
                AgentEvalReporter.AGENT_EVAL_PROFILES
            );

            AgentEvalBaselineSupport.Snapshot baselineSnapshot =
                AgentEvalBaselineSupport.snapshot(pack, baselineSummaries);

            DashboardBenchmarkSummary healthyBalanced = baselineSummaries.stream()
                .filter(summary -> "agent_coordination_healthy".equals(summary.scenarioName()))
                .filter(summary -> "budgetflow_adaptive".equals(summary.executionStrategy()))
                .filter(summary -> "balanced".equals(summary.policyProfile()))
                .findFirst()
                .orElseThrow();

            List<DashboardBenchmarkSummary> currentSummaries = baselineSummaries.stream()
                .map(summary -> summary == healthyBalanced
                    ? new DashboardBenchmarkSummary(
                        summary.scenario(),
                        summary.executionStrategy(),
                        summary.policyProfile(),
                        3,
                        List.of(DashboardTaskSpecs.BALANCE_TASK, DashboardTaskSpecs.INSIGHTS_TASK),
                        summary.fallbackTasks(),
                        summary.approximatedTasks(),
                        true,
                        summary.projectedWork().plusMillis(20),
                        List.of("balance=omitted_for_delta_test")
                    )
                    : summary
                )
                .toList();

            AgentEvalBaselineSupport.Snapshot currentSnapshot =
                AgentEvalBaselineSupport.snapshot(pack, currentSummaries);
            AgentEvalBaselineSupport.Comparison comparison =
                AgentEvalBaselineSupport.compare("golden", baselineSnapshot, currentSnapshot);

            String markdown = AgentEvalBaselineSupport.formatDeltaMarkdown(comparison);
            String json = AgentEvalBaselineSupport.formatDeltaJson(comparison);

            assertTrue(markdown.contains("# BudgetFlow evidence delta"));
            assertTrue(markdown.contains("## Top changes (inspect first)"));
            assertTrue(markdown.contains("agent_coordination_healthy"));
            assertTrue(markdown.contains("| budgetflow_adaptive | balanced |"));
            assertTrue(markdown.contains("regression-risk"));
            assertTrue(json.contains("\"changedScenarioCount\""));
            assertTrue(json.contains("\"severity\" : \"regression-risk\"")
                || json.contains("\"severity\":\"regression-risk\""));

            Path outDir = Files.createTempDirectory("agent-eval-baseline-test");
            Path baselineDir = AgentEvalBaselineSupport.saveBaseline(
                outDir,
                "golden",
                "{\"report\":true}",
                "# report",
                baselineSnapshot
            );
            Path resolved = AgentEvalBaselineSupport.resolveSnapshotPath(outDir, "golden");
            AgentEvalBaselineSupport.Snapshot reloaded = AgentEvalBaselineSupport.readSnapshot(resolved);

            assertTrue(Files.exists(baselineDir.resolve("agent-eval-report.json")));
            assertTrue(Files.exists(baselineDir.resolve("agent-eval-report.md")));
            assertTrue(Files.exists(baselineDir.resolve(AgentEvalBaselineSupport.SNAPSHOT_FILE_NAME)));
            assertEquals(baselineSnapshot.packName(), reloaded.packName());
        }
    }

    @Test
    void explicitSnapshotPathCanBeResolved() throws IOException {
        Path outDir = Files.createTempDirectory("agent-eval-baseline-paths");
        Path explicit = outDir.resolve("custom").resolve(AgentEvalBaselineSupport.SNAPSHOT_FILE_NAME);
        Files.createDirectories(explicit.getParent());
        Files.writeString(explicit, "{}");

        assertEquals(explicit, AgentEvalBaselineSupport.resolveSnapshotPath(outDir, explicit.toString()));
        assertEquals(explicit, AgentEvalBaselineSupport.resolveSnapshotPath(outDir, explicit.getParent().toString()));
    }

    private static final class NoDelaySimulationSupport extends SimulationSupport {
        @Override
        public void delay(long millis) {
        }
    }
}
