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
