package com.budgetflow.core.execution;

import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.budget.DefaultExecutionBudget;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
import com.budgetflow.core.context.BudgetContext;
import com.budgetflow.core.context.BudgetContextHolder;
import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.policy.BudgetPolicyEngine;
import com.budgetflow.core.policy.DecisionTraceEntry;
import com.budgetflow.core.policy.DefaultBudgetPolicyEngine;
import com.budgetflow.core.policy.PolicyDecision;
import com.budgetflow.core.policy.PolicyEvaluationInput;
import com.budgetflow.core.policy.TaskDescriptor;
import com.budgetflow.core.policy.TaskExecutionDirective;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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

    @Test
    void executeRequestEvaluatesAllTasksInSinglePolicyInput() {
        AtomicReference<List<TaskDescriptor>> capturedTasks = new AtomicReference<>();
        BudgetPolicyEngine policyEngine = input -> {
            capturedTasks.set(input.tasks());
            return new PolicyDecision(
                List.of(
                    new TaskExecutionDirective("balance", ExecutionMode.EXECUTE, Duration.ofMillis(40), false, "normal"),
                    new TaskExecutionDirective("offers", ExecutionMode.OMIT, Duration.ofMillis(20), true, "omitted_by_policy")
                ),
                true,
                List.of("offers: OMIT"),
                List.of(
                    new DecisionTraceEntry(
                        "balance",
                        Importance.MANDATORY,
                        ExecutionMode.EXECUTE,
                        "normal",
                        Duration.ofMillis(40),
                        Duration.ofMillis(40),
                        input.remainingBudget()
                    ),
                    new DecisionTraceEntry(
                        "offers",
                        Importance.OPTIONAL,
                        ExecutionMode.OMIT,
                        "omitted_by_policy",
                        Duration.ofMillis(120),
                        Duration.ZERO,
                        input.remainingBudget()
                    )
                )
            );
        };

        DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(policyEngine);

        var response = executor.executeRequest(List.of(
            TaskSpec.mandatory("balance", Duration.ofMillis(40), () -> "ok"),
            TaskSpec.optional("offers", Duration.ofMillis(120), () -> "expensive")
        )).toCompletableFuture().join();

        assertNotNull(capturedTasks.get());
        assertEquals(2, capturedTasks.get().size());
        assertEquals("ok", response.taskResult("balance").value().orElseThrow());
        assertTrue(response.taskResult("offers").omitted());
        assertEquals(2, response.decisionTrace().size());
        assertTrue(response.diagnostics().degraded());
        assertEquals(List.of("offers"), response.diagnostics().omittedTaskNames());
        assertEquals(List.of(), response.diagnostics().fallbackTaskNames());
        assertEquals(List.of(), response.diagnostics().approximatedTaskNames());
    }

    @Test
    void requestExecutionResultPreservesPolicyTrace() {
        DecisionTraceEntry traceEntry = new DecisionTraceEntry(
            "offers",
            Importance.OPTIONAL,
            ExecutionMode.EXECUTE_APPROXIMATE,
            "approximate_selected_by_policy",
            Duration.ofMillis(80),
            Duration.ofMillis(40),
            Duration.ofMillis(100)
        );

        BudgetPolicyEngine policyEngine = input -> new PolicyDecision(
            List.of(new TaskExecutionDirective("offers", ExecutionMode.EXECUTE_APPROXIMATE, Duration.ofMillis(40), false, "approximate_selected_by_policy")),
            true,
            List.of("offers: EXECUTE_APPROXIMATE"),
            List.of(traceEntry)
        );

        DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(policyEngine);

        var response = executor.executeRequest(List.of(
            TaskSpec.optional("offers", Duration.ofMillis(80), () -> "primary").withApproximate(() -> "approx")
        )).toCompletableFuture().join();

        assertEquals(List.of(traceEntry), response.decisionTrace());
        assertEquals(List.of("offers"), response.diagnostics().approximatedTaskNames());
    }

    @Test
    void diagnosticsCaptureRequestBudgetAndDegradedSummary() {
        Duration totalBudget = Duration.ofMillis(250);
        BudgetContextHolder.set(new BudgetContext(
            new DefaultExecutionBudget(totalBudget, Instant.now().minusMillis(15))
        ));

        BudgetPolicyEngine policyEngine = input -> new PolicyDecision(
            List.of(
                new TaskExecutionDirective("balance", ExecutionMode.EXECUTE, Duration.ofMillis(40), false, "normal"),
                new TaskExecutionDirective("offers", ExecutionMode.EXECUTE_APPROXIMATE, Duration.ofMillis(20), false, "approximate_selected_by_policy"),
                new TaskExecutionDirective("insights", ExecutionMode.OMIT, Duration.ZERO, true, "omitted_by_policy")
            ),
            true,
            List.of("offers: EXECUTE_APPROXIMATE", "insights: OMIT"),
            List.of(
                new DecisionTraceEntry(
                    "balance",
                    Importance.MANDATORY,
                    ExecutionMode.EXECUTE,
                    "normal",
                    Duration.ofMillis(40),
                    Duration.ofMillis(40),
                    input.remainingBudget()
                ),
                new DecisionTraceEntry(
                    "offers",
                    Importance.OPTIONAL,
                    ExecutionMode.EXECUTE_APPROXIMATE,
                    "approximate_selected_by_policy",
                    Duration.ofMillis(120),
                    Duration.ofMillis(20),
                    input.remainingBudget()
                ),
                new DecisionTraceEntry(
                    "insights",
                    Importance.OPTIONAL,
                    ExecutionMode.OMIT,
                    "omitted_by_policy",
                    Duration.ofMillis(140),
                    Duration.ZERO,
                    input.remainingBudget()
                )
            )
        );

        DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(policyEngine);
        var response = executor.executeRequest(List.of(
            TaskSpec.mandatory("balance", Duration.ofMillis(40), () -> "ok"),
            TaskSpec.optional("offers", Duration.ofMillis(120), () -> "expensive").withApproximate(() -> "approx"),
            TaskSpec.optional("insights", Duration.ofMillis(140), () -> "insight")
        )).toCompletableFuture().join();

        assertEquals(totalBudget, response.diagnostics().totalRequestBudget());
        assertTrue(response.diagnostics().remainingRequestBudget().toMillis() <= totalBudget.toMillis());
        assertTrue(response.diagnostics().degraded());
        assertEquals(List.of("insights"), response.diagnostics().omittedTaskNames());
        assertEquals(List.of("offers"), response.diagnostics().approximatedTaskNames());
        assertEquals(List.of(), response.diagnostics().fallbackTaskNames());
    }

    @Test
    void diagnosticsRemainConsistentWithDecisionTraceModes() {
        BudgetPolicyEngine policyEngine = input -> new PolicyDecision(
            List.of(
                new TaskExecutionDirective("rewards", ExecutionMode.EXECUTE_WITH_FALLBACK, Duration.ofMillis(50), false, "fallback_selected_by_policy"),
                new TaskExecutionDirective("offers", ExecutionMode.EXECUTE_APPROXIMATE, Duration.ofMillis(30), false, "approximate_selected_by_policy"),
                new TaskExecutionDirective("insights", ExecutionMode.OMIT, Duration.ZERO, true, "omitted_by_policy")
            ),
            true,
            List.of("rewards: EXECUTE_WITH_FALLBACK", "offers: EXECUTE_APPROXIMATE", "insights: OMIT"),
            List.of(
                new DecisionTraceEntry(
                    "rewards",
                    Importance.IMPORTANT,
                    ExecutionMode.EXECUTE_WITH_FALLBACK,
                    "fallback_selected_by_policy",
                    Duration.ofMillis(90),
                    Duration.ofMillis(50),
                    input.remainingBudget()
                ),
                new DecisionTraceEntry(
                    "offers",
                    Importance.OPTIONAL,
                    ExecutionMode.EXECUTE_APPROXIMATE,
                    "approximate_selected_by_policy",
                    Duration.ofMillis(110),
                    Duration.ofMillis(30),
                    input.remainingBudget()
                ),
                new DecisionTraceEntry(
                    "insights",
                    Importance.OPTIONAL,
                    ExecutionMode.OMIT,
                    "omitted_by_policy",
                    Duration.ofMillis(140),
                    Duration.ZERO,
                    input.remainingBudget()
                )
            )
        );

        DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(policyEngine);
        var response = executor.executeRequest(List.of(
            TaskSpec.important("rewards", Duration.ofMillis(90), () -> "primary").withFallback(() -> "fallback"),
            TaskSpec.optional("offers", Duration.ofMillis(110), () -> "primary").withApproximate(() -> "approx"),
            TaskSpec.optional("insights", Duration.ofMillis(140), () -> "insight")
        )).toCompletableFuture().join();

        assertEquals(List.of("insights"), response.diagnostics().omittedTaskNames());
        assertEquals(List.of("rewards"), response.diagnostics().fallbackTaskNames());
        assertEquals(List.of("offers"), response.diagnostics().approximatedTaskNames());
    }

    @Test
    void adaptiveExecutorRequestExecutionIsNotDefaulted() throws NoSuchMethodException {
        assertFalse(AdaptiveExecutor.class.getMethod("executeRequest", List.class).isDefault());
    }

    @Test
    void lifecycleListenersReceivePolicyAndResultEvents() {
        List<String> events = new ArrayList<>();
        AtomicReference<PolicyEvaluationInput> capturedInput = new AtomicReference<>();
        AtomicReference<PolicyDecision> capturedDecision = new AtomicReference<>();

        ExecutionLifecycleListener listener = new ExecutionLifecycleListener() {
            @Override
            public void beforePolicyEvaluation(PolicyEvaluationInput input) {
                events.add("before");
                capturedInput.set(input);
            }

            @Override
            public void afterPolicyEvaluation(PolicyEvaluationInput input, PolicyDecision decision) {
                events.add("after_policy");
                capturedDecision.set(decision);
            }

            @Override
            public void afterRequestExecution(com.budgetflow.core.api.RequestExecutionResult result) {
                events.add("after_result");
            }
        };

        DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(
            Runnable::run,
            new DefaultBudgetPolicyEngine(),
            () -> new com.budgetflow.core.policy.SystemPressureSnapshot(0.1, 0.1, 0.1),
            List.of(listener)
        );

        var response = executor.executeRequest(List.of(
            TaskSpec.mandatory("balance", Duration.ofMillis(40), () -> "ok")
        )).toCompletableFuture().join();

        assertEquals(List.of("before", "after_policy", "after_result"), events);
        assertNotNull(capturedInput.get());
        assertNotNull(capturedDecision.get());
        assertFalse(response.diagnostics().degraded());
    }

    @Test
    void listenerFailuresDoNotBreakRequestExecution() {
        ExecutionLifecycleListener failingListener = new ExecutionLifecycleListener() {
            @Override
            public void beforePolicyEvaluation(PolicyEvaluationInput input) {
                throw new IllegalStateException("listener failure");
            }
        };

        DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(
            Runnable::run,
            new DefaultBudgetPolicyEngine(),
            () -> new com.budgetflow.core.policy.SystemPressureSnapshot(0.0, 0.0, 0.0),
            List.of(failingListener)
        );

        assertDoesNotThrow(() -> executor.executeRequest(List.of(
            TaskSpec.mandatory("balance", Duration.ofMillis(40), () -> "ok")
        )).toCompletableFuture().join());
    }

    private BudgetPolicyEngine policyFor(String taskName, ExecutionMode mode, boolean omitted, String reason) {
        return input -> new PolicyDecision(
            List.of(new TaskExecutionDirective(taskName, mode, Duration.ZERO, omitted, reason)),
            mode != ExecutionMode.EXECUTE,
            List.of(),
            List.of()
        );
    }
}
