package com.budgetflow.demo.fintech.benchmark;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEvalReporterTest {

    @Test
    void defaultRunWritesCanonicalAgentEvidenceFiles() throws IOException {
        Path outputDir = Files.createTempDirectory(Path.of("/tmp"), "budgetflow-agent-eval-default-");

        AgentEvalReporter.main(new String[] {"--out=" + outputDir});

        Path jsonPath = outputDir.resolve("agent-eval-report.json");
        Path mdPath = outputDir.resolve("agent-eval-report.md");
        assertTrue(Files.exists(jsonPath));
        assertTrue(Files.exists(mdPath));
        assertTrue(Files.readString(jsonPath).contains("\"name\":\"agent\""));
    }

    @Test
    void nonAgentPackRunUsesPackScopedStableFilenames() throws IOException {
        Path outputDir = Files.createTempDirectory(Path.of("/tmp"), "budgetflow-agent-eval-pack-");

        AgentEvalReporter.main(new String[] {"--pack=adoption", "--out=" + outputDir});

        Path jsonPath = outputDir.resolve("adoption-eval-report.json");
        Path mdPath = outputDir.resolve("adoption-eval-report.md");
        assertTrue(Files.exists(jsonPath));
        assertTrue(Files.exists(mdPath));
        assertTrue(Files.readString(jsonPath).contains("\"name\":\"adoption\""));
    }

    @Test
    void nonAgentPackCanOverridePolicies() throws IOException {
        Path outputDir = Files.createTempDirectory(Path.of("/tmp"), "budgetflow-agent-eval-policy-");

        AgentEvalReporter.main(new String[] {
            "--pack=adoption",
            "--policies=balanced,efficiency",
            "--out=" + outputDir
        });

        String json = Files.readString(outputDir.resolve("adoption-eval-report.json"));
        assertTrue(json.contains("\"policyProfile\":\"balanced\""));
        assertTrue(json.contains("\"policyProfile\":\"efficiency\""));
    }

    @Test
    void comparePacksWritesCrossPackComparisonArtifacts() throws IOException {
        Path outputDir = Files.createTempDirectory(Path.of("/tmp"), "budgetflow-agent-eval-cross-pack-");

        AgentEvalReporter.main(new String[] {
            "--compare-packs=default,adoption,agent",
            "--out=" + outputDir
        });

        Path jsonPath = outputDir.resolve(AgentEvalBaselineSupport.PACK_COMPARE_JSON_FILE_NAME);
        Path mdPath = outputDir.resolve(AgentEvalBaselineSupport.PACK_COMPARE_MD_FILE_NAME);
        assertTrue(Files.exists(jsonPath));
        assertTrue(Files.exists(mdPath));
        assertTrue(Files.readString(jsonPath).contains("\"packOrder\""));
        assertTrue(Files.readString(mdPath).contains("## Trend interpretation"));
    }
}
