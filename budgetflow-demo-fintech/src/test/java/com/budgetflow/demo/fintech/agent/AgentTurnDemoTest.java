package com.budgetflow.demo.fintech.agent;

import com.budgetflow.core.api.AdaptiveRequestResult;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.metadata.RequestExecutionDiagnosticsFormatter;
import com.budgetflow.core.policy.DecisionTraceEntry;
import com.budgetflow.core.policy.FixedPressureProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTurnDemoTest {

    @Test
    void healthyScenarioExecutesAllStepsNormally() {
        AdaptiveRequestResult result = AgentTurnDemo.runScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero());

        assertFalse(result.diagnostics().degraded(), "healthy scenario should not be degraded");
        assertTrue(result.diagnostics().omittedTaskNames().isEmpty(), "no steps should be omitted");
        assertTrue(result.diagnostics().fallbackTaskNames().isEmpty(), "no steps should use fallback");

        List<DecisionTraceEntry> trace = result.decisionTrace();
        assertEquals(3, trace.size(), "all 3 agent steps should appear in trace");
        assertTrue(trace.stream().allMatch(e -> e.selectedExecutionMode() == ExecutionMode.EXECUTE),
            "all steps should execute normally under healthy conditions");
    }

    @Test
    void healthyScenarioProducesExpectedStepResults() {
        AdaptiveRequestResult result = AgentTurnDemo.runScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero());

        assertTrue(result.get(AgentTurnDemo.RETRIEVE_CONTEXT_KEY).isPresent());
        assertTrue(result.get(AgentTurnDemo.VERIFY_SOURCES_KEY).isPresent());
        assertTrue(result.get(AgentTurnDemo.ENRICH_WITH_EXAMPLES_KEY).isPresent());
    }

    @Test
    void constrainedScenarioFallsBackVerifyAndOmitsEnrich() {
        AdaptiveRequestResult result = AgentTurnDemo.runScenario(
            Duration.ofMillis(70), FixedPressureProvider.zero());

        assertTrue(result.diagnostics().degraded(), "constrained scenario should be degraded");
        assertTrue(result.diagnostics().omittedTaskNames().contains("enrich-with-examples"),
            "enrich-with-examples should be omitted when budget is tight");
        assertTrue(result.diagnostics().fallbackTaskNames().contains("verify-sources"),
            "verify-sources should fall back to cheaper heuristic path when budget is tight");
    }

    @Test
    void constrainedScenarioMandatoryStepAlwaysExecutes() {
        AdaptiveRequestResult result = AgentTurnDemo.runScenario(
            Duration.ofMillis(70), FixedPressureProvider.zero());

        DecisionTraceEntry retrieveEntry = result.decisionTrace().stream()
            .filter(e -> e.taskName().equals("retrieve-context"))
            .findFirst()
            .orElseThrow();

        assertEquals(ExecutionMode.EXECUTE, retrieveEntry.selectedExecutionMode(),
            "mandatory retrieve-context must always execute normally");
    }

    @Test
    void formatAgentStepsProducesReadableOutput() {
        AdaptiveRequestResult constrained = AgentTurnDemo.runScenario(
            Duration.ofMillis(70), FixedPressureProvider.zero());

        String summary = RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            constrained.diagnostics(), constrained.decisionTrace());

        assertTrue(summary.contains("Agent turn:"), "summary should include header");
        assertTrue(summary.contains("retrieve-context"), "summary should include step names");
        assertTrue(summary.contains("NORMAL"), "mandatory step should show as NORMAL");
        assertTrue(summary.contains("FALLBACK"), "degraded step should show as FALLBACK");
        assertTrue(summary.contains("OMIT"), "omitted step should show as OMIT");
        assertTrue(summary.contains("← fallback used"), "fallback annotation should appear");
        assertTrue(summary.contains("← omitted"), "omit annotation should appear");
    }

    @Test
    void agentTurnBudgetCostsAreCorrect() {
        AdaptiveRequestResult healthy = AgentTurnDemo.runScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero());

        assertEquals(Duration.ofMillis(300), healthy.diagnostics().totalRequestBudget());

        Duration totalPlanned = healthy.decisionTrace().stream()
            .map(DecisionTraceEntry::plannedExecutionLatency)
            .reduce(Duration.ZERO, Duration::plus);
        assertEquals(Duration.ofMillis(125), totalPlanned,
            "total planned latency should equal sum of primary latencies (50+30+45)");
    }
}
