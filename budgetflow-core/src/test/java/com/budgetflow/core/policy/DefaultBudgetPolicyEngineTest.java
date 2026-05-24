package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultBudgetPolicyEngineTest {

    @Test
    void optionalTaskCanBeOmittedUnderLowBudget() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(100),
            List.of(descriptor("insights", Importance.OPTIONAL, 140)),
            new SystemPressureSnapshot(0.1, 0.1, 0.1)
        ));

        assertEquals(ExecutionMode.OMIT, decision.directives().get(0).executionMode());
        assertEquals(true, decision.directives().get(0).omitted());
    }

    @Test
    void optionalTaskWithApproximatePathPrefersApproximateBeforeOmissionUnderHighPressure() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(200),
            List.of(descriptorWithApproximate("offers", Importance.OPTIONAL, 100, 20)),
            new SystemPressureSnapshot(0.86, 0.20, 0.20)
        ));

        assertEquals(ExecutionMode.EXECUTE_APPROXIMATE, decision.directives().get(0).executionMode());
        assertFalse(decision.directives().get(0).omitted());
        assertTrue(decision.directives().get(0).reason().startsWith("approximate_selected_by_policy["));
    }

    @Test
    void optionalTaskWithApproximatePathCanStillExecuteUnderModeratePressureWhenLatencyRatioIsLow() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(1000),
            List.of(descriptorWithApproximate("offers", Importance.OPTIONAL, 80, 20)),
            new SystemPressureSnapshot(0.62, 0.20, 0.20)
        ));

        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(0).executionMode());
        assertFalse(decision.directives().get(0).omitted());
        assertTrue(decision.directives().get(0).reason().startsWith("normal["));
    }

    @Test
    void optionalTaskStaysOnPrimaryPathWhenModerateStressSavingsAreMarginal() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(200),
            List.of(descriptorWithApproximate("tips", Importance.OPTIONAL, 70, 65)),
            new SystemPressureSnapshot(0.62, 0.20, 0.20)
        ));

        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(0).executionMode());
        assertTrue(decision.directives().get(0).reason().contains("savings=low"));
        assertTrue(decision.directives().get(0).reason().contains("fit=primary"));
    }

    @Test
    void optionalTaskWithoutDegradedPathIsOmittedUnderHighPressure() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(200),
            List.of(descriptor("insights", Importance.OPTIONAL, 100)),
            new SystemPressureSnapshot(0.86, 0.20, 0.20)
        ));

        assertEquals(ExecutionMode.OMIT, decision.directives().get(0).executionMode());
        assertTrue(decision.directives().get(0).omitted());
    }

    @Test
    void approximationIsNotAlwaysSelectedWhenSupported() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofSeconds(2),
            List.of(descriptorWithApproximate("offers", Importance.OPTIONAL, 50, 15)),
            new SystemPressureSnapshot(0.1, 0.1, 0.1)
        ));

        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(0).executionMode());
    }

    @Test
    void importantTaskWithFallbackCanStillExecuteNormallyWhenStressIsLow() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(1000),
            List.of(descriptorWithFallback("rewards", Importance.IMPORTANT, 120, 30)),
            new SystemPressureSnapshot(0.15, 0.10, 0.10)
        ));

        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(0).executionMode());
        assertFalse(decision.directives().get(0).omitted());
    }

    @Test
    void importantTaskFallsBackWhenPrimaryDoesNotFitButFallbackDoes() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(60),
            List.of(descriptorWithFallback("rewards", Importance.IMPORTANT, 95, 20)),
            new SystemPressureSnapshot(0.15, 0.10, 0.10)
        ));

        assertEquals(ExecutionMode.EXECUTE_WITH_FALLBACK, decision.directives().get(0).executionMode());
        assertTrue(decision.directives().get(0).reason().startsWith("fallback_selected_by_policy["));
    }

    @Test
    void multiTaskPlanningOmitsOptionalWhilePreservingMandatoryWork() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(100),
            List.of(
                descriptor("balance", Importance.MANDATORY, 40),
                descriptor("transactions", Importance.MANDATORY, 60),
                descriptor("insights", Importance.OPTIONAL, 140)
            ),
            new SystemPressureSnapshot(0.2, 0.2, 0.2)
        ));

        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(0).executionMode());
        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(1).executionMode());
        assertEquals(ExecutionMode.OMIT, decision.directives().get(2).executionMode());
        assertTrue(decision.directives().get(2).omitted());
        assertEquals(3, decision.decisionTrace().size());
    }

    @Test
    void mandatoryReserveIsAppliedBeforeOptionalPlanning() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(80),
            List.of(
                descriptor("optional-first", Importance.OPTIONAL, 100),
                descriptor("mandatory-core", Importance.MANDATORY, 80)
            ),
            new SystemPressureSnapshot(0.1, 0.1, 0.1)
        ));

        assertEquals("mandatory-core", decision.directives().get(0).taskName());
        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(0).executionMode());
        assertEquals("optional-first", decision.directives().get(1).taskName());
        assertEquals(ExecutionMode.OMIT, decision.directives().get(1).executionMode());
        assertEquals(Duration.ZERO, decision.directives().get(1).allocatedBudget());
    }

    @Test
    void planningOrderIsDeterministicByImportanceWithStableWithinClassOrder() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(400),
            List.of(
                descriptorWithApproximate("optional-a", Importance.OPTIONAL, 60, 20),
                descriptor("mandatory-a", Importance.MANDATORY, 70),
                descriptorWithFallback("important-a", Importance.IMPORTANT, 70, 25),
                descriptorWithApproximate("optional-b", Importance.OPTIONAL, 60, 20),
                descriptor("mandatory-b", Importance.MANDATORY, 70),
                descriptorWithFallback("important-b", Importance.IMPORTANT, 70, 25)
            ),
            new SystemPressureSnapshot(0.1, 0.1, 0.1)
        ));

        assertEquals(
            List.of("mandatory-a", "mandatory-b", "important-a", "important-b", "optional-a", "optional-b"),
            decision.directives().stream().map(TaskExecutionDirective::taskName).collect(Collectors.toList())
        );
    }

    @Test
    void degradationReasonsRemainDeterministicAndExplainable() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(300),
            List.of(descriptorWithFallbackAndApproximate("offers", Importance.OPTIONAL, 160, 20, 12)),
            new SystemPressureSnapshot(0.30, 0.22, 0.84)
        ));

        String reason = decision.directives().get(0).reason();
        assertTrue(reason.startsWith("fallback_selected_by_policy["));
        assertTrue(reason.contains("pressure=moderate:downstream"));
        assertTrue(reason.contains("active_signals=1"));
        assertTrue(reason.contains("mixed=low"));
        assertTrue(reason.contains("budget=available"));
        assertTrue(reason.contains("fit=primary"));
        assertTrue(reason.contains("savings=high"));
        assertFalse(reason.isBlank());
    }

    @Test
    void optionalTaskTreatsStackedRuntimeSignalsAsHighStressInput() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(400),
            List.of(descriptor("insights", Importance.OPTIONAL, 150)),
            new SystemPressureSnapshot(0.72, 0.74, 0.20)
        ));

        assertEquals(ExecutionMode.OMIT, decision.directives().get(0).executionMode());
        assertTrue(decision.directives().get(0).reason().contains("active_signals=2"));
    }

    @Test
    void optionalApproximateLatencyHintPreservesBudgetForLaterTasks() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(150),
            List.of(
                descriptorWithApproximate("offers", Importance.OPTIONAL, 110, 8),
                descriptor("insights", Importance.OPTIONAL, 70)
            ),
            new SystemPressureSnapshot(0.1, 0.1, 0.1)
        ));

        assertEquals(ExecutionMode.EXECUTE_APPROXIMATE, decision.directives().get(0).executionMode());
        assertEquals(Duration.ofMillis(8), decision.decisionTrace().get(0).plannedExecutionLatency());
        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(1).executionMode());
    }

    @Test
    void optionalFallbackLatencyHintPreservesBudgetForLaterTasks() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(150),
            List.of(
                descriptorWithFallback("offers", Importance.OPTIONAL, 110, 10),
                descriptor("insights", Importance.OPTIONAL, 70)
            ),
            new SystemPressureSnapshot(0.10, 0.10, 0.10)
        ));

        assertEquals(ExecutionMode.EXECUTE_WITH_FALLBACK, decision.directives().get(0).executionMode());
        assertEquals(Duration.ofMillis(10), decision.decisionTrace().get(0).plannedExecutionLatency());
        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(1).executionMode());
    }

    @Test
    void optionalTaskDegradesWhenPrimaryOverrunsBudgetButApproximateFits() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(75),
            List.of(descriptorWithApproximate("offers", Importance.OPTIONAL, 110, 18)),
            new SystemPressureSnapshot(0.58, 0.65, 0.56)
        ));

        assertEquals(ExecutionMode.EXECUTE_APPROXIMATE, decision.directives().get(0).executionMode());
        assertFalse(decision.directives().get(0).omitted());
    }

    @Test
    void equivalentPlanningInputsProduceStableDecisionsAndTrace() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();
        PolicyEvaluationInput input = new PolicyEvaluationInput(
            Duration.ofMillis(260),
            List.of(
                descriptor("balance", Importance.MANDATORY, 40),
                descriptorWithFallback("rewards", Importance.IMPORTANT, 110, 18),
                descriptorWithFallbackAndApproximate("offers", Importance.OPTIONAL, 120, 16, 9),
                descriptor("insights", Importance.OPTIONAL, 85)
            ),
            new SystemPressureSnapshot(0.30, 0.22, 0.66)
        );

        PolicyDecision first = engine.evaluate(input);
        PolicyDecision second = engine.evaluate(input);

        assertEquals(first.directives(), second.directives());
        assertEquals(first.decisionTrace(), second.decisionTrace());
        assertEquals(first.degradationReasons(), second.degradationReasons());
    }

    @Test
    void optionalStrategyCanBeOverriddenWithoutLosingExplainableReasons() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine((task, context) -> {
            if (task.fallbackSupported()) {
                return ExecutionMode.EXECUTE_WITH_FALLBACK;
            }
            return ExecutionMode.EXECUTE;
        });

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(350),
            List.of(
                descriptorWithFallbackAndApproximate("offers", Importance.OPTIONAL, 120, 14, 8),
                descriptor("insights", Importance.OPTIONAL, 70)
            ),
            new SystemPressureSnapshot(0.05, 0.05, 0.05)
        ));

        assertEquals(ExecutionMode.EXECUTE_WITH_FALLBACK, decision.directives().get(0).executionMode());
        assertTrue(decision.directives().get(0).reason().startsWith("fallback_selected_by_policy["));
        assertEquals(Duration.ofMillis(14), decision.decisionTrace().get(0).plannedExecutionLatency());
        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(1).executionMode());
    }

    @Test
    void namedPolicyProfilesProducePurposefulOptionalBehaviorDifferences() {
        PolicyEvaluationInput input = new PolicyEvaluationInput(
            Duration.ofMillis(260),
            List.of(descriptorWithFallbackAndApproximate("offers", Importance.OPTIONAL, 120, 16, 9)),
            new SystemPressureSnapshot(0.90, 0.20, 0.20)
        );

        PolicyDecision balanced = new DefaultBudgetPolicyEngine(PlannerPolicyProfile.BALANCED).evaluate(input);
        PolicyDecision continuity = new DefaultBudgetPolicyEngine(PlannerPolicyProfile.CONTINUITY).evaluate(input);
        PolicyDecision efficiency = new DefaultBudgetPolicyEngine(PlannerPolicyProfile.EFFICIENCY).evaluate(input);

        assertEquals(ExecutionMode.EXECUTE_APPROXIMATE, balanced.directives().get(0).executionMode());
        assertEquals(ExecutionMode.EXECUTE_WITH_FALLBACK, continuity.directives().get(0).executionMode());
        assertEquals(ExecutionMode.OMIT, efficiency.directives().get(0).executionMode());

        assertTrue(balanced.directives().get(0).reason().contains("policy=balanced"));
        assertTrue(continuity.directives().get(0).reason().contains("policy=continuity"));
        assertTrue(efficiency.directives().get(0).reason().contains("policy=efficiency"));
    }

    @Test
    void balancedProfilePrefersFallbackWhenStressIsModerateAndQualityCanBePreserved() {
        PolicyEvaluationInput input = new PolicyEvaluationInput(
            Duration.ofMillis(220),
            List.of(descriptorWithFallbackAndApproximate("offers", Importance.OPTIONAL, 120, 34, 16)),
            new SystemPressureSnapshot(0.62, 0.20, 0.20)
        );

        PolicyDecision decision = new DefaultBudgetPolicyEngine(PlannerPolicyProfile.BALANCED).evaluate(input);

        assertEquals(ExecutionMode.EXECUTE_WITH_FALLBACK, decision.directives().get(0).executionMode());
        assertTrue(decision.directives().get(0).reason().contains("mixed=low"));
        assertTrue(decision.directives().get(0).reason().contains("degrade_pref=fallback"));
    }

    @Test
    void continuityProfilePrioritizesFallbackEvenWhenApproximateIsCheaper() {
        PolicyEvaluationInput input = new PolicyEvaluationInput(
            Duration.ofMillis(200),
            List.of(descriptorWithFallbackAndApproximate("offers", Importance.OPTIONAL, 120, 30, 12)),
            new SystemPressureSnapshot(0.62, 0.20, 0.20)
        );

        PolicyDecision continuity = new DefaultBudgetPolicyEngine(PlannerPolicyProfile.CONTINUITY).evaluate(input);
        PolicyDecision efficiency = new DefaultBudgetPolicyEngine(PlannerPolicyProfile.EFFICIENCY).evaluate(input);

        assertEquals(ExecutionMode.EXECUTE_WITH_FALLBACK, continuity.directives().get(0).executionMode());
        assertEquals(ExecutionMode.EXECUTE_APPROXIMATE, efficiency.directives().get(0).executionMode());
    }

    @Test
    void continuityProfilePrefersDegradedPathOverOmissionForNoisyButNotSevereStress() {
        PolicyEvaluationInput input = new PolicyEvaluationInput(
            Duration.ofMillis(220),
            List.of(descriptorWithFallback("offers", Importance.OPTIONAL, 170, 12)),
            new SystemPressureSnapshot(0.86, 0.30, 0.20)
        );

        PolicyDecision balanced = new DefaultBudgetPolicyEngine(PlannerPolicyProfile.BALANCED).evaluate(input);
        PolicyDecision continuity = new DefaultBudgetPolicyEngine(PlannerPolicyProfile.CONTINUITY).evaluate(input);

        assertEquals(ExecutionMode.OMIT, balanced.directives().get(0).executionMode());
        assertEquals(ExecutionMode.EXECUTE_WITH_FALLBACK, continuity.directives().get(0).executionMode());
    }

    private TaskDescriptor descriptor(String taskName, Importance importance, long expectedLatencyMs) {
        Duration latency = Duration.ofMillis(expectedLatencyMs);
        return new TaskDescriptor(taskName, importance, latency, false, false, latency, latency);
    }

    private TaskDescriptor descriptorWithFallback(String taskName, Importance importance, long expectedLatencyMs, long fallbackLatencyMs) {
        Duration latency = Duration.ofMillis(expectedLatencyMs);
        return new TaskDescriptor(taskName, importance, latency, true, false, Duration.ofMillis(fallbackLatencyMs), latency);
    }

    private TaskDescriptor descriptorWithApproximate(String taskName, Importance importance, long expectedLatencyMs, long approximateLatencyMs) {
        Duration latency = Duration.ofMillis(expectedLatencyMs);
        return new TaskDescriptor(taskName, importance, latency, false, true, latency, Duration.ofMillis(approximateLatencyMs));
    }

    private TaskDescriptor descriptorWithFallbackAndApproximate(
        String taskName,
        Importance importance,
        long expectedLatencyMs,
        long fallbackLatencyMs,
        long approximateLatencyMs
    ) {
        Duration latency = Duration.ofMillis(expectedLatencyMs);
        return new TaskDescriptor(
            taskName,
            importance,
            latency,
            true,
            true,
            Duration.ofMillis(fallbackLatencyMs),
            Duration.ofMillis(approximateLatencyMs)
        );
    }
}
