package com.budgetflow.core.policy;

import com.budgetflow.core.api.AdaptiveRequestResult;
import com.budgetflow.core.api.AgentWorkSpec;
import com.budgetflow.core.api.TaskKey;
import com.budgetflow.core.budget.DefaultExecutionBudget;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.context.BudgetContext;
import com.budgetflow.core.context.BudgetContextHolder;
import com.budgetflow.core.execution.DefaultAdaptiveExecutor;
import com.budgetflow.core.api.AdaptiveRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LatencyFirstOptionalTaskModeSelector} and the {@code latency_first}
 * planner policy profile.
 */
class LatencyFirstOptionalTaskModeSelectorTest {

    private static final TaskKey<String> MANDATORY_KEY = TaskKey.of("mandatory-step");
    private static final TaskKey<String> OPTIONAL_KEY  = TaskKey.of("optional-step");
    private static final TaskKey<String> OPTIONAL_APPROX_KEY = TaskKey.of("optional-approx-step");

    @AfterEach
    void clearContext() {
        BudgetContextHolder.clear();
    }

    @Test
    void latencyFirstProfileResolvesFromConfigName() {
        PlannerPolicyProfile profile = PlannerPolicyProfile.fromConfigName("latency_first");
        assertEquals(PlannerPolicyProfile.LATENCY_FIRST, profile);
    }

    @Test
    void latencyFirstProfileResolvesFromAliasLatency() {
        PlannerPolicyProfile profile = PlannerPolicyProfile.fromConfigName("latency");
        assertEquals(PlannerPolicyProfile.LATENCY_FIRST, profile);
    }

    @Test
    void latencyFirstProfileResolvesFromAliasFast() {
        PlannerPolicyProfile profile = PlannerPolicyProfile.fromConfigName("fast");
        assertEquals(PlannerPolicyProfile.LATENCY_FIRST, profile);
    }

    @Test
    void latencyFirstSelectorIsRegisteredInPlannerPolicyProfiles() {
        OptionalTaskModeSelector selector = PlannerPolicyProfiles.optionalTaskSelector(PlannerPolicyProfile.LATENCY_FIRST);
        assertTrue(selector instanceof LatencyFirstOptionalTaskModeSelector,
            "latency_first profile should use LatencyFirstOptionalTaskModeSelector");
    }

    @Test
    void latencyFirstOmitsOptionalWorkUnderComfortableBudgetWhenRatioExceedsThreshold() {
        // Budget 300ms, optional step 130ms → ratio 0.43, just over the 0.42 threshold.
        // BALANCED would execute this; LATENCY_FIRST should omit.
        BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(Duration.ofMillis(300))));
        AdaptiveRequest request = AdaptiveRequest.builder()
            .mandatory(MANDATORY_KEY, Duration.ofMillis(40), () -> "mandatory-result")
            .optional(OPTIONAL_KEY, Duration.ofMillis(130), () -> "optional-result")
            .build();
        AdaptiveRequestResult result = request.execute(
            new DefaultAdaptiveExecutor(new DefaultBudgetPolicyEngine(PlannerPolicyProfile.LATENCY_FIRST), FixedPressureProvider.zero())
        ).toCompletableFuture().join();

        assertTrue(result.diagnostics().omittedTaskNames().contains("optional-step"),
            "latency_first should omit optional step when ratio exceeds 0.42 threshold");
        assertTrue(result.diagnostics().degraded(), "result should be degraded when optional work is omitted");
    }

    @Test
    void latencyFirstOmitsOptionalWorkUnderHighPressure() {
        // High pressure (>= 0.85) triggers immediate omission under latency_first.
        BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(Duration.ofMillis(500))));
        AdaptiveRequest request = AdaptiveRequest.builder()
            .mandatory(MANDATORY_KEY, Duration.ofMillis(40), () -> "mandatory-result")
            .optional(OPTIONAL_KEY, Duration.ofMillis(80), () -> "optional-result")
            .build();
        AdaptiveRequestResult result = request.execute(
            new DefaultAdaptiveExecutor(
                new DefaultBudgetPolicyEngine(PlannerPolicyProfile.LATENCY_FIRST),
                FixedPressureProvider.of(0.90, 0.0, 0.0))
        ).toCompletableFuture().join();

        assertTrue(result.diagnostics().omittedTaskNames().contains("optional-step"),
            "latency_first should omit optional step under high pressure (>= 0.85)");
    }

    @Test
    void latencyFirstOmitsOptionalApproximateStepsInsteadOfDegrading() {
        // BALANCED and CONTINUITY would degrade to approximate; LATENCY_FIRST should omit directly.
        BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(Duration.ofMillis(300))));
        AdaptiveRequest request = AdaptiveRequest.builder()
            .mandatory(MANDATORY_KEY, Duration.ofMillis(40), () -> "mandatory-result")
            .optionalWithApproximate(OPTIONAL_APPROX_KEY, Duration.ofMillis(130),
                () -> "optional-full", Duration.ofMillis(30), () -> "optional-approx")
            .build();
        AdaptiveRequestResult result = request.execute(
            new DefaultAdaptiveExecutor(new DefaultBudgetPolicyEngine(PlannerPolicyProfile.LATENCY_FIRST), FixedPressureProvider.zero())
        ).toCompletableFuture().join();

        assertTrue(result.diagnostics().omittedTaskNames().contains("optional-approx-step"),
            "latency_first should omit optional step (not approximate it) when ratio exceeds threshold");
        assertTrue(result.diagnostics().approximatedTaskNames().isEmpty(),
            "latency_first should not choose approximate paths for optional steps");
    }

    @Test
    void latencyFirstExecutesMandatoryWorkRegardlessOfConditions() {
        BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(Duration.ofMillis(50))));
        AdaptiveRequest request = AdaptiveRequest.builder()
            .mandatory(MANDATORY_KEY, Duration.ofMillis(40), () -> "mandatory-result")
            .build();
        AdaptiveRequestResult result = request.execute(
            new DefaultAdaptiveExecutor(new DefaultBudgetPolicyEngine(PlannerPolicyProfile.LATENCY_FIRST), FixedPressureProvider.maximum())
        ).toCompletableFuture().join();

        assertEquals(ExecutionMode.EXECUTE,
            result.decisionTrace().stream()
                .filter(e -> e.taskName().equals("mandatory-step"))
                .findFirst().orElseThrow().selectedExecutionMode(),
            "mandatory steps must always execute regardless of latency_first profile");
    }

    @Test
    void latencyFirstExecutesOptionalWorkUnderVeryComfortableBudget() {
        // Budget 500ms, optional step 50ms → ratio 0.10, well below 0.42 threshold.
        // No pressure. LATENCY_FIRST should execute normally.
        BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(Duration.ofMillis(500))));
        AdaptiveRequest request = AdaptiveRequest.builder()
            .mandatory(MANDATORY_KEY, Duration.ofMillis(40), () -> "mandatory-result")
            .optional(OPTIONAL_KEY, Duration.ofMillis(50), () -> "optional-result")
            .build();
        AdaptiveRequestResult result = request.execute(
            new DefaultAdaptiveExecutor(new DefaultBudgetPolicyEngine(PlannerPolicyProfile.LATENCY_FIRST), FixedPressureProvider.zero())
        ).toCompletableFuture().join();

        List<String> omitted = result.diagnostics().omittedTaskNames();
        assertTrue(!omitted.contains("optional-step"),
            "latency_first should execute optional work when budget ratio is very comfortable and no pressure");
    }

    @Test
    void latencyFirstProfileHasExpectedMetadata() {
        PlannerPolicyProfile profile = PlannerPolicyProfile.LATENCY_FIRST;
        assertEquals("latency_first", profile.configName());
        assertEquals("Latency-first", profile.displayName());
        assertTrue(profile.intent().contains("optional"),
            "intent should describe optional-work behavior");
        assertTrue(profile.aliases().contains("latency"),
            "latency alias should be registered");
        assertTrue(profile.aliases().contains("fast"),
            "fast alias should be registered");
    }
}
