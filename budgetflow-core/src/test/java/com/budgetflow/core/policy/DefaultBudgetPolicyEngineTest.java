package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
