package com.budgetflow.core.execution;

import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.budget.DefaultExecutionBudget;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.context.BudgetContext;
import com.budgetflow.core.context.BudgetContextHolder;
import com.budgetflow.core.policy.BudgetPolicyEngine;
import com.budgetflow.core.policy.DefaultBudgetPolicyEngine;
import com.budgetflow.core.policy.PolicyDecision;
import com.budgetflow.core.policy.TaskExecutionDirective;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAdaptiveExecutorTest {

    @AfterEach
    void cleanBudgetContext() {
        BudgetContextHolder.clear();
    }

    @Test
    void mandatoryTasksExecuteEvenUnderLowBudget() {
        BudgetContextHolder.set(new BudgetContext(
            new DefaultExecutionBudget(Duration.ofMillis(1), Instant.now().minusMillis(10))
        ));

        DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(new DefaultBudgetPolicyEngine());
        var result = executor.execute(TaskSpec.mandatory("balance", Duration.ofMillis(40), () -> "ok"))
            .toCompletableFuture()
            .join();

        assertEquals("ok", result.value().orElseThrow());
        assertEquals(ExecutionMode.EXECUTE, result.executionMode());
        assertFalse(result.omitted());
    }

    @Test
    void optionalTasksCanBeOmitted() {
        DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(policyFor("insights", ExecutionMode.OMIT, true, "omitted_by_policy"));

        var result = executor.execute(TaskSpec.optional("insights", Duration.ofMillis(120), () -> "expensive"))
            .toCompletableFuture()
            .join();

        assertTrue(result.omitted());
        assertTrue(result.value().isEmpty());
        assertEquals(ExecutionMode.OMIT, result.executionMode());
    }

    @Test
    void fallbackIsUsedWhenDirectiveSaysFallback() {
        AtomicBoolean primaryCalled = new AtomicBoolean(false);
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(
            policyFor("rewards", ExecutionMode.EXECUTE_WITH_FALLBACK, false, "fallback_selected_by_policy")
        );

        var result = executor.execute(
                TaskSpec.important("rewards", Duration.ofMillis(90), () -> {
                        primaryCalled.set(true);
                        return "primary";
                    })
                    .withFallback(() -> {
                        fallbackCalled.set(true);
                        return "fallback";
                    })
            )
            .toCompletableFuture()
            .join();

        assertEquals("fallback", result.value().orElseThrow());
        assertEquals(ExecutionMode.EXECUTE_WITH_FALLBACK, result.executionMode());
        assertFalse(primaryCalled.get());
        assertTrue(fallbackCalled.get());
    }

    @Test
    void approximatePathIsUsedOnlyWhenSelectedByPolicy() {
        TaskSpec<String> spec = TaskSpec.optional("offers", Duration.ofMillis(110), () -> "primary")
            .withApproximate(() -> "approximate");

        DefaultAdaptiveExecutor executeNormally = new DefaultAdaptiveExecutor(
            policyFor("offers", ExecutionMode.EXECUTE, false, "normal")
        );
        var normalResult = executeNormally.execute(spec).toCompletableFuture().join();
        assertEquals("primary", normalResult.value().orElseThrow());
        assertEquals(ExecutionMode.EXECUTE, normalResult.executionMode());

        DefaultAdaptiveExecutor executeApproximate = new DefaultAdaptiveExecutor(
            policyFor("offers", ExecutionMode.EXECUTE_APPROXIMATE, false, "approximate_selected_by_policy")
        );
        var approximateResult = executeApproximate.execute(spec).toCompletableFuture().join();
        assertEquals("approximate", approximateResult.value().orElseThrow());
        assertEquals(ExecutionMode.EXECUTE_APPROXIMATE, approximateResult.executionMode());
    }

    private BudgetPolicyEngine policyFor(String taskName, ExecutionMode mode, boolean omitted, String reason) {
        return input -> new PolicyDecision(
            List.of(new TaskExecutionDirective(taskName, mode, Duration.ZERO, omitted, reason)),
            mode != ExecutionMode.EXECUTE,
            List.of()
        );
    }
}
