package com.budgetflow.demo.fintech.benchmark;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowcaseReferencePathDocumentationTest {

    @Test
    void showcaseReferencePathKeepsCanonicalRunnableCommandsAndEvidenceTargets() throws IOException {
        String showcase = Files.readString(resolveRepoFile("docs/showcase-reference-path.md"));

        assertTrue(showcase.contains("./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough"));
        assertTrue(showcase.contains("./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=adoption\""));
        assertTrue(showcase.contains("./gradlew :budgetflow-demo-fintech:runAgentEvalReport"));
        assertTrue(showcase.contains("budgetflow-demo-fintech/build/eval-reports/agent-eval-report.md"));
        assertTrue(showcase.contains("agent-eval-delta.md"));
    }

    @Test
    void readmeKeepsDirectLinkToShowcaseReferencePath() throws IOException {
        String readme = Files.readString(resolveRepoFile("README.md"));
        assertTrue(readme.contains("docs/showcase-reference-path.md"));
    }

    @Test
    void canonicalShowcaseEvidenceEntryPointsRemainRunnable() throws IOException {
        Path outputDir = Files.createTempDirectory(Path.of("/tmp"), "budgetflow-showcase-path-test-");
        Path comparisonOutput = outputDir.resolve("showcase-adoption.md");

        DashboardWalkthrough.main(new String[0]);
        DashboardComparisonHarness.main(new String[] {
            "--pack=adoption",
            "--markdown",
            "--out=" + comparisonOutput
        });
        AgentEvalReporter.main(new String[] {"--out=" + outputDir});

        assertTrue(Files.exists(comparisonOutput));
        String comparisonMarkdown = Files.readString(comparisonOutput);
        assertTrue(comparisonMarkdown.contains("# BudgetFlow comparison evidence"));

        Path agentReport = outputDir.resolve("agent-eval-report.md");
        assertTrue(Files.exists(agentReport));
        String agentMarkdown = Files.readString(agentReport);
        assertTrue(agentMarkdown.contains("## Review interpretation"));
    }

    private Path resolveRepoFile(String relativePath) {
        List<Path> candidates = List.of(
            Path.of(relativePath),
            Path.of("..", relativePath),
            Path.of("..", "..", relativePath)
        );
        return candidates.stream()
            .map(Path::toAbsolutePath)
            .filter(Files::exists)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Could not locate " + relativePath + " from " + Path.of("").toAbsolutePath()));
    }
}
