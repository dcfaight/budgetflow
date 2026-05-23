package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBudgetPolicyEngineTest {

    @Test
    void optionalTaskCanBeOmittedUnderLowBudget() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(100),
            List.of(new TaskDescriptor("insights", Importance.OPTIONAL, Duration.ofMillis(140), false, false)),
            new SystemPressureSnapshot(0.1, 0.1, 0.1)
        ));

        assertEquals(ExecutionMode.OMIT, decision.directives().get(0).executionMode());
        assertEquals(true, decision.directives().get(0).omitted());
    }

    @Test
    void approximationIsNotAlwaysSelectedWhenSupported() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofSeconds(2),
            List.of(new TaskDescriptor("offers", Importance.OPTIONAL, Duration.ofMillis(50), false, true)),
            new SystemPressureSnapshot(0.1, 0.1, 0.1)
        ));

        assertEquals(ExecutionMode.EXECUTE, decision.directives().get(0).executionMode());
    }

    @Test
    void multiTaskPlanningOmitsOptionalWhilePreservingMandatoryWork() {
        DefaultBudgetPolicyEngine engine = new DefaultBudgetPolicyEngine();

        PolicyDecision decision = engine.evaluate(new PolicyEvaluationInput(
            Duration.ofMillis(100),
            List.of(
                new TaskDescriptor("balance", Importance.MANDATORY, Duration.ofMillis(40), false, false),
                new TaskDescriptor("transactions", Importance.MANDATORY, Duration.ofMillis(60), false, false),
                new TaskDescriptor("insights", Importance.OPTIONAL, Duration.ofMillis(140), false, false)
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
                new TaskDescriptor("optional-first", Importance.OPTIONAL, Duration.ofMillis(100), false, false),
                new TaskDescriptor("mandatory-core", Importance.MANDATORY, Duration.ofMillis(80), false, false)
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
                new TaskDescriptor("optional-a", Importance.OPTIONAL, Duration.ofMillis(60), false, true),
                new TaskDescriptor("mandatory-a", Importance.MANDATORY, Duration.ofMillis(70), false, false),
                new TaskDescriptor("important-a", Importance.IMPORTANT, Duration.ofMillis(70), true, false),
                new TaskDescriptor("optional-b", Importance.OPTIONAL, Duration.ofMillis(60), false, true),
                new TaskDescriptor("mandatory-b", Importance.MANDATORY, Duration.ofMillis(70), false, false),
                new TaskDescriptor("important-b", Importance.IMPORTANT, Duration.ofMillis(70), true, false)
            ),
            new SystemPressureSnapshot(0.1, 0.1, 0.1)
        ));

        assertEquals(
            List.of("mandatory-a", "mandatory-b", "important-a", "important-b", "optional-a", "optional-b"),
            decision.directives().stream().map(TaskExecutionDirective::taskName).collect(Collectors.toList())
        );
    }
}
