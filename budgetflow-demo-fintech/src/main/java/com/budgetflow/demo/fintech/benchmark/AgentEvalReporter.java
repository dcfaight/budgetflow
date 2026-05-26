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
        Path outDir = resolveOutDir(args);
        DashboardScenarioPack pack = PressureScenarios.agentPack();

        try (DashboardComparisonHarness harness = new DashboardComparisonHarness()) {
            List<DashboardBenchmarkSummary> summaries = harness.run(pack.scenarios(), AGENT_EVAL_PROFILES);

            Path jsonPath = outDir.resolve("agent-eval-report.json");
            Path mdPath = outDir.resolve("agent-eval-report.md");

            writeArtifact(jsonPath, DashboardBenchmarkFormatter.formatJson(pack, summaries));
            writeArtifact(mdPath, DashboardBenchmarkFormatter.formatMarkdown(pack, summaries));

            System.out.println("Agent evaluation pack complete.");
            System.out.println("  Pack    : " + pack.name() + " — " + pack.description());
            System.out.println("  Profiles: balanced, continuity, efficiency, latency_first");
            System.out.println("  JSON    : " + jsonPath.toAbsolutePath());
            System.out.println("  Markdown: " + mdPath.toAbsolutePath());
            System.out.println();
            System.out.println("Diff across runs: git diff build/eval-reports/agent-eval-report.{json,md}");
            System.out.println("Review packet  : open " + mdPath.toAbsolutePath());
        }
    }

    private static Path resolveOutDir(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--out=")) {
                String raw = arg.substring("--out=".length());
                if (!raw.isBlank()) {
                    return Path.of(raw);
                }
            }
        }
        return Path.of(DEFAULT_OUT_DIR);
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
}
