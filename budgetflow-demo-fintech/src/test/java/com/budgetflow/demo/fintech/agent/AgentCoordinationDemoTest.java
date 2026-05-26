package com.budgetflow.demo.fintech.agent;

import com.budgetflow.core.api.AdaptiveRequestResult;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
import com.budgetflow.core.metadata.RequestExecutionDiagnosticsFormatter;
import com.budgetflow.core.policy.DecisionTraceEntry;
import com.budgetflow.core.policy.FixedPressureProvider;
import com.budgetflow.core.policy.PlannerPolicyProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentCoordinationDemoTest {

    // --- Coordination turn structure ---

    @Test
    void coordinationTurnContainsFiveWorkItems() {
        assertEquals(5, AgentCoordinationDemo.buildCoordinationTurn().taskSpecs().size(),
            "coordination turn should contain 5 work items: plan + 2 fetches + consolidate + format");
    }

    // --- Healthy scenario (A1): generous budget, no pressure ---

    @Test
    void healthyScenarioMandatoryPlanStepAlwaysExecutes() {
        AdaptiveRequestResult result = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero(), PlannerPolicyProfile.BALANCED);

        DecisionTraceEntry plan = result.decisionTrace().stream()
            .filter(e -> e.taskName().equals("plan-coordination"))
            .findFirst().orElseThrow();
        assertEquals(ExecutionMode.EXECUTE, plan.selectedExecutionMode(),
            "mandatory plan-coordination step must always execute");
    }

    @Test
    void healthyScenarioAllImportantStepsExecute() {
        AdaptiveRequestResult result = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero(), PlannerPolicyProfile.BALANCED);

        List<DecisionTraceEntry> importantEntries = result.decisionTrace().stream()
            .filter(e -> e.taskImportance() == Importance.IMPORTANT)
            .toList();
        assertEquals(3, importantEntries.size(), "coordination turn should have 3 important steps");
        // Under generous budget and no pressure, important steps should not be omitted
        assertTrue(importantEntries.stream().noneMatch(e -> e.selectedExecutionMode() == ExecutionMode.OMIT),
            "important steps should not be omitted under generous budget and no pressure");
    }

    // --- Sub-agent fallback scenario (A2): tight budget, no pressure ---

    @Test
    void subAgentFallbackScenarioFetchStepsFallBack() {
        AdaptiveRequestResult result = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(100), FixedPressureProvider.zero(), PlannerPolicyProfile.BALANCED);

        assertTrue(result.diagnostics().degraded(), "sub-agent fallback scenario should be degraded");
        assertTrue(
            result.diagnostics().fallbackTaskNames().contains("fetch-source-a")
                || result.diagnostics().fallbackTaskNames().contains("fetch-source-b")
                || result.diagnostics().approximatedTaskNames().contains("consolidate-results")
                || !result.diagnostics().omittedTaskNames().isEmpty(),
            "tight budget should cause at least one fetch step to fall back, consolidate to approximate, or optional to be omitted");
    }

    @Test
    void subAgentFallbackScenarioMandatoryPlanAlwaysExecutes() {
        AdaptiveRequestResult result = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(100), FixedPressureProvider.zero(), PlannerPolicyProfile.BALANCED);

        DecisionTraceEntry plan = result.decisionTrace().stream()
            .filter(e -> e.taskName().equals("plan-coordination"))
            .findFirst().orElseThrow();
        assertEquals(ExecutionMode.EXECUTE, plan.selectedExecutionMode(),
            "mandatory plan-coordination must always execute even under tight budget");
    }

    // --- Degraded-cascade scenario (B1): severe budget + maximum pressure ---

    @Test
    void degradedCascadeScenarioMandatoryPlanAlwaysExecutes() {
        AdaptiveRequestResult result = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(70), FixedPressureProvider.maximum(), PlannerPolicyProfile.BALANCED);

        DecisionTraceEntry plan = result.decisionTrace().stream()
            .filter(e -> e.taskName().equals("plan-coordination"))
            .findFirst().orElseThrow();
        assertEquals(ExecutionMode.EXECUTE, plan.selectedExecutionMode(),
            "mandatory plan step must still execute during degraded-cascade");
    }

    @Test
    void degradedCascadeScenarioIsDegradedWithClearReasons() {
        AdaptiveRequestResult result = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(70), FixedPressureProvider.maximum(), PlannerPolicyProfile.BALANCED);

        assertTrue(result.diagnostics().degraded(), "degraded-cascade scenario should be flagged as degraded");
        // Verify that at least the optional format step is omitted under maximum pressure + tight budget
        assertTrue(result.diagnostics().omittedTaskNames().contains("format-polished-response"),
            "format-polished-response should be omitted under maximum pressure + tight budget");
    }

    @Test
    void degradedCascadeScenarioTraceHasFiveEntries() {
        AdaptiveRequestResult result = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(70), FixedPressureProvider.maximum(), PlannerPolicyProfile.BALANCED);

        assertEquals(5, result.decisionTrace().size(),
            "decision trace should contain 5 entries (one per work item)");
    }

    // --- Policy comparison: balanced vs latency_first ---

    @Test
    void latencyFirstOmitsOptionalFormattingThatBalancedMayAdapt() {
        AdaptiveRequestResult balanced = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero(), PlannerPolicyProfile.BALANCED);
        AdaptiveRequestResult latencyFirst = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero(), PlannerPolicyProfile.LATENCY_FIRST);

        boolean balancedOmitsFormat = balanced.diagnostics().omittedTaskNames().contains("format-polished-response");
        boolean latencyFirstOmitsFormat = latencyFirst.diagnostics().omittedTaskNames().contains("format-polished-response");

        // latency_first should omit the optional format step; balanced may adapt it or execute it
        assertTrue(latencyFirstOmitsFormat,
            "latency_first profile should omit optional format-polished-response to protect latency headroom");
        // The policy profiles should produce observably different results in at least one dimension
        assertTrue(
            latencyFirstOmitsFormat && !balancedOmitsFormat
                || latencyFirst.diagnostics().omittedTaskNames().size() > balanced.diagnostics().omittedTaskNames().size()
                || latencyFirst.diagnostics().fallbackTaskNames().size() != balanced.diagnostics().fallbackTaskNames().size(),
            "latency_first profile should produce an observably different decision than balanced for the same scenario"
        );
    }

    @Test
    void latencyFirstAlwaysPreservesMandatoryWork() {
        AdaptiveRequestResult result = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero(), PlannerPolicyProfile.LATENCY_FIRST);

        DecisionTraceEntry plan = result.decisionTrace().stream()
            .filter(e -> e.taskName().equals("plan-coordination"))
            .findFirst().orElseThrow();
        assertEquals(ExecutionMode.EXECUTE, plan.selectedExecutionMode(),
            "mandatory plan step must always execute under latency_first profile");
    }

    // --- Formatting and explainability ---

    @Test
    void formatAgentStepsProducesReadableCoordinationOutput() {
        AdaptiveRequestResult result = AgentCoordinationDemo.runCoordinationScenario(
            Duration.ofMillis(100), FixedPressureProvider.zero(), PlannerPolicyProfile.BALANCED);

        String summary = RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            result.diagnostics(), result.decisionTrace());

        assertTrue(summary.contains("Agent turn:"), "summary should include header");
        assertTrue(summary.contains("plan-coordination"), "summary should include plan step name");
        assertTrue(summary.contains("fetch-source-a"), "summary should include fetch-source-a step name");
        assertTrue(summary.contains("fetch-source-b"), "summary should include fetch-source-b step name");
        assertTrue(summary.contains("consolidate-results"), "summary should include consolidate step name");
        assertTrue(summary.contains("format-polished-response"), "summary should include format step name");
    }
}
