package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.policy.PlannerPolicyProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates stable review artifacts for the agent-oriented evaluation pack in one command.
 *
 * <p>Runs the named {@code agent} scenario pack with all four planner profiles
 * (balanced, continuity, efficiency, latency_first) and writes predictable JSON and
 * Markdown output to {@code build/eval-reports/} (or a custom directory via {@code --out=}).
 *
 * <p>Output files have stable names so they are easy to diff across runs and PRs:
 * <ul>
 *   <li>{@code agent-eval-report.json} — full structured evidence including scorecards and
 *       profile comparison summaries</li>
 *   <li>{@code agent-eval-report.md} — compact Markdown review packet including scenario
 *       narratives, scorecards, and an interpretation section</li>
 * </ul>
 *
 * <p>Run via Gradle:
 * <pre>
 *   ./gradlew :budgetflow-demo-fintech:runAgentEvalReport
 * </pre>
 *
 * <p>Override output directory:
 * <pre>
 *   ./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--out=/tmp/my-eval"
 * </pre>
 */
public final class AgentEvalReporter {

    static final List<PlannerPolicyProfile> AGENT_EVAL_PROFILES = List.of(
        PlannerPolicyProfile.BALANCED,
        PlannerPolicyProfile.CONTINUITY,
        PlannerPolicyProfile.EFFICIENCY,
        PlannerPolicyProfile.LATENCY_FIRST
    );

    private static final String DEFAULT_OUT_DIR = "build/eval-reports";

    private AgentEvalReporter() {
    }

    public static void main(String[] args) {
        ReporterOptions options = ReporterOptions.parse(args);
        Path outDir = options.outDir();
        DashboardScenarioPack pack = PressureScenarios.agentPack();

        try (DashboardComparisonHarness harness = new DashboardComparisonHarness()) {
            List<DashboardBenchmarkSummary> summaries = harness.run(pack.scenarios(), AGENT_EVAL_PROFILES);

            Path jsonPath = outDir.resolve("agent-eval-report.json");
            Path mdPath = outDir.resolve("agent-eval-report.md");
            AgentEvalBaselineSupport.Snapshot snapshot = AgentEvalBaselineSupport.snapshot(pack, summaries);
            String reportJson = DashboardBenchmarkFormatter.formatJson(pack, summaries);
            String reportMarkdown = DashboardBenchmarkFormatter.formatMarkdown(pack, summaries);

            writeArtifact(jsonPath, reportJson);
            writeArtifact(mdPath, reportMarkdown);

            Path baselineDir = null;
            if (options.saveBaselineName() != null) {
                baselineDir = AgentEvalBaselineSupport.saveBaseline(
                    outDir,
                    options.saveBaselineName(),
                    reportJson,
                    reportMarkdown,
                    snapshot
                );
            }

            Path deltaJsonPath = null;
            Path deltaMdPath = null;
            if (options.compareTo() != null) {
                Path baselineSnapshotPath = AgentEvalBaselineSupport.resolveSnapshotPath(outDir, options.compareTo());
                AgentEvalBaselineSupport.Snapshot baselineSnapshot =
                    AgentEvalBaselineSupport.readSnapshot(baselineSnapshotPath);
                AgentEvalBaselineSupport.Comparison comparison =
                    AgentEvalBaselineSupport.compare(options.compareTo(), baselineSnapshot, snapshot);
                deltaJsonPath = outDir.resolve(AgentEvalBaselineSupport.DELTA_JSON_FILE_NAME);
                deltaMdPath = outDir.resolve(AgentEvalBaselineSupport.DELTA_MD_FILE_NAME);
                writeArtifact(deltaJsonPath, AgentEvalBaselineSupport.formatDeltaJson(comparison));
                writeArtifact(deltaMdPath, AgentEvalBaselineSupport.formatDeltaMarkdown(comparison));
            }

            System.out.println("Agent evaluation pack complete.");
            System.out.println("  Pack    : " + pack.name() + " — " + pack.description());
            System.out.println("  Profiles: balanced, continuity, efficiency, latency_first");
            System.out.println("  JSON    : " + jsonPath.toAbsolutePath());
            System.out.println("  Markdown: " + mdPath.toAbsolutePath());
            if (baselineDir != null) {
                System.out.println("  Baseline: " + baselineDir.toAbsolutePath());
            }
            if (deltaJsonPath != null && deltaMdPath != null) {
                System.out.println("  Delta   : " + deltaJsonPath.toAbsolutePath());
                System.out.println("  Review  : " + deltaMdPath.toAbsolutePath());
            }
            System.out.println();
            System.out.println("Diff across runs: git diff build/eval-reports/agent-eval-report.{json,md}");
            if (options.compareTo() != null) {
                System.out.println("Compare baseline: open " + deltaMdPath.toAbsolutePath());
            }
            if (options.saveBaselineName() != null) {
                System.out.println("Saved baseline : " + baselineDir.toAbsolutePath());
            }
            System.out.println("Review packet  : open " + mdPath.toAbsolutePath());
        }
    }

    private static void writeArtifact(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to write artifact: " + path, ioException);
        }
    }

    private record ReporterOptions(
        Path outDir,
        String saveBaselineName,
        String compareTo
    ) {
        private static ReporterOptions parse(String[] args) {
            Path outDir = Path.of(DEFAULT_OUT_DIR);
            String saveBaselineName = null;
            String compareTo = null;
            for (String arg : args) {
                if (arg.startsWith("--out=")) {
                    String raw = arg.substring("--out=".length());
                    if (!raw.isBlank()) {
                        outDir = Path.of(raw);
                    }
                    continue;
                }
                if (arg.startsWith("--save-baseline=")) {
                    String raw = arg.substring("--save-baseline=".length()).trim();
                    saveBaselineName = raw.isBlank() ? null : raw;
                    continue;
                }
                if (arg.startsWith("--compare-to=")) {
                    String raw = arg.substring("--compare-to=".length()).trim();
                    compareTo = raw.isBlank() ? null : raw;
                }
            }
            return new ReporterOptions(outDir, saveBaselineName, compareTo);
        }
    }
}
