package com.budgetflow.demo.fintech.agent;

import com.budgetflow.core.api.AdaptiveRequestResult;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
import com.budgetflow.core.metadata.RequestExecutionDiagnosticsFormatter;
import com.budgetflow.core.policy.DecisionTraceEntry;
import com.budgetflow.core.policy.FixedPressureProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTurnDemoTest {

    @Test
    void healthyScenarioExecutesAllStepsNormally() {
        AdaptiveRequestResult result = AgentTurnDemo.runScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero());

        assertTrue(result.diagnostics().degraded(), "healthy scenario should include optional-path adaptation");
        assertTrue(result.diagnostics().omittedTaskNames().isEmpty(), "no steps should be omitted");
        assertTrue(result.diagnostics().fallbackTaskNames().isEmpty(), "no steps should use fallback");
        assertTrue(result.diagnostics().approximatedTaskNames().contains("draft-follow-up-actions"),
            "healthy scenario should still choose the lightweight optional follow-up path");

        List<DecisionTraceEntry> trace = result.decisionTrace();
        assertEquals(4, trace.size(), "all 4 agent steps should appear in trace");
        assertEquals(1, trace.stream().filter(e -> e.selectedExecutionMode() == ExecutionMode.EXECUTE_APPROXIMATE).count(),
            "healthy scenario should include one approximated optional step");
    }

    @Test
    void healthyScenarioProducesExpectedStepResults() {
        AdaptiveRequestResult result = AgentTurnDemo.runScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero());

        assertTrue(result.get(AgentTurnDemo.RETRIEVE_CONTEXT_KEY).isPresent());
        assertTrue(result.get(AgentTurnDemo.VERIFY_SOURCES_KEY).isPresent());
        assertTrue(result.get(AgentTurnDemo.ENRICH_WITH_EXAMPLES_KEY).isPresent());
        assertTrue(result.get(AgentTurnDemo.DRAFT_FOLLOW_UP_ACTIONS_KEY).isPresent());
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
        assertTrue(result.diagnostics().omittedTaskNames().contains("draft-follow-up-actions"),
            "draft-follow-up-actions should be omitted when budget is very tight");
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
    void pressureSpikeScenarioDegradesOptionalWork() {
        AdaptiveRequestResult result = AgentTurnDemo.runScenario(
            Duration.ofMillis(220), FixedPressureProvider.maximum());

        List<DecisionTraceEntry> optionalEntries = result.decisionTrace().stream()
            .filter(entry -> entry.taskImportance() == Importance.OPTIONAL)
            .toList();
        assertEquals(2, optionalEntries.size(), "agent turn should include two optional steps");
        assertTrue(optionalEntries.stream().anyMatch(entry -> entry.selectedExecutionMode() != ExecutionMode.EXECUTE),
            "at least one optional step should be downgraded or omitted under high pressure");
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
        assertEquals(Duration.ofMillis(135), totalPlanned,
            "total planned latency should include approximate follow-up path (50+30+45+10)");
    }
}
