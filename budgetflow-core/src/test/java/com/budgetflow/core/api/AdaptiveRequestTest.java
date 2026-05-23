package com.budgetflow.core.api;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
import com.budgetflow.core.metadata.RequestExecutionDiagnostics;
import com.budgetflow.core.policy.DecisionTraceEntry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveRequestTest {

    private static final TaskKey<String> BALANCE_KEY = TaskKey.of("balance");
    private static final TaskKey<String> REWARDS_KEY = TaskKey.of("rewards");
    private static final TaskKey<String> INSIGHTS_KEY = TaskKey.of("insights");

    // -------------------------------------------------------------------------
    // TaskKey
    // -------------------------------------------------------------------------

    @Test
    void taskKeyExposesName() {
        TaskKey<String> key = TaskKey.of("my-task");
        assertEquals("my-task", key.name());
    }

    @Test
    void taskKeyRejectsNullName() {
        assertThrows(NullPointerException.class, () -> TaskKey.of(null));
    }

    @Test
    void taskKeysWithSameNameAreEqual() {
        assertEquals(TaskKey.of("x"), TaskKey.of("x"));
    }

    // -------------------------------------------------------------------------
    // AdaptiveRequest.Builder
    // -------------------------------------------------------------------------

    @Test
    void builderAcceptsMandatoryTask() {
        AdaptiveRequest request = AdaptiveRequest.builder()
            .mandatory(BALANCE_KEY, Duration.ofMillis(40), () -> "ok")
            .build();

        assertEquals(1, request.taskSpecs().size());
        TaskSpec<?> spec = request.taskSpecs().get(0);
        assertEquals("balance", spec.taskName());
        assertEquals(Importance.MANDATORY, spec.importance());
    }

    @Test
    void builderAcceptsImportantAndOptionalTasks() {
        AdaptiveRequest request = AdaptiveRequest.builder()
            .important(REWARDS_KEY, Duration.ofMillis(90), () -> "rewards")
            .optional(INSIGHTS_KEY, Duration.ofMillis(140), () -> "insights")
            .build();

        assertEquals(2, request.taskSpecs().size());
        assertEquals(Importance.IMPORTANT, request.taskSpecs().get(0).importance());
        assertEquals(Importance.OPTIONAL, request.taskSpecs().get(1).importance());
    }

    @Test
    void builderAcceptsPrebuiltTaskSpecViaTaskMethod() {
        TaskSpec<String> spec = TaskSpec.important("rewards", Duration.ofMillis(90), () -> "primary")
            .withFallback(() -> "fallback");

        AdaptiveRequest request = AdaptiveRequest.builder()
            .task(REWARDS_KEY, spec)
            .build();

        assertEquals(1, request.taskSpecs().size());
        assertTrue(request.taskSpecs().get(0).fallbackSupplier().isPresent());
    }

    @Test
    void builderRejectsTaskWhenKeyAndSpecNameMismatch() {
        TaskSpec<String> spec = TaskSpec.optional("other-name", Duration.ofMillis(40), () -> "v");
        assertThrows(IllegalArgumentException.class,
            () -> AdaptiveRequest.builder().task(BALANCE_KEY, spec).build());
    }

    @Test
    void builderRejectsEmptyRequest() {
        assertThrows(IllegalStateException.class,
            () -> AdaptiveRequest.builder().build());
    }

    @Test
    void builderRejectsNullKey() {
        assertThrows(NullPointerException.class,
            () -> AdaptiveRequest.builder().mandatory(null, Duration.ofMillis(40), () -> "v"));
    }

    @Test
    void builderRejectsNullSupplier() {
        assertThrows(NullPointerException.class,
            () -> AdaptiveRequest.builder().mandatory(BALANCE_KEY, Duration.ofMillis(40), null));
    }

    // -------------------------------------------------------------------------
    // AdaptiveRequest.execute – delegates to executor.executeRequest
    // -------------------------------------------------------------------------

    @Test
    void executePassesAllTaskSpecsToExecutor() {
        AdaptiveRequest request = AdaptiveRequest.builder()
            .mandatory(BALANCE_KEY, Duration.ofMillis(40), () -> "balance")
            .optional(INSIGHTS_KEY, Duration.ofMillis(140), () -> "insights")
            .build();

        AdaptiveExecutor executor = new AdaptiveExecutor() {
            @Override
            public <T> java.util.concurrent.CompletionStage<TaskResult<T>> execute(TaskSpec<T> taskSpec) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletionStage<RequestExecutionResult> executeRequest(java.util.List<TaskSpec<?>> taskSpecs) {
                assertEquals(2, taskSpecs.size());
                return CompletableFuture.completedFuture(simpleResult(
                    Map.of(
                        "balance", TaskResult.executed("balance", ExecutionMode.EXECUTE, "normal"),
                        "insights", TaskResult.executed("insights", ExecutionMode.EXECUTE, "normal")
                    )
                ));
            }
        };

        AdaptiveRequestResult result = request.execute(executor).toCompletableFuture().join();
        assertNotNull(result);
    }

    @Test
    void executeRejectsNullExecutor() {
        AdaptiveRequest request = AdaptiveRequest.builder()
            .mandatory(BALANCE_KEY, Duration.ofMillis(40), () -> "v")
            .build();
        assertThrows(NullPointerException.class, () -> request.execute(null));
    }

    // -------------------------------------------------------------------------
    // AdaptiveRequestResult – typed access
    // -------------------------------------------------------------------------

    @Test
    void requireReturnsValueForExecutedTask() {
        AdaptiveRequestResult result = resultWith(
            "balance", TaskResult.executed("balance-value", ExecutionMode.EXECUTE, "normal")
        );
        assertEquals("balance-value", result.require(BALANCE_KEY));
    }

    @Test
    void requireThrowsWhenTaskWasOmitted() {
        AdaptiveRequestResult result = resultWith(
            "balance", TaskResult.omitted("budget_exhausted")
        );
        assertThrows(IllegalStateException.class, () -> result.require(BALANCE_KEY));
    }

    @Test
    void getReturnsEmptyForOmittedTask() {
        AdaptiveRequestResult result = resultWith(
            "insights", TaskResult.omitted("budget_exhausted")
        );
        assertEquals(Optional.empty(), result.get(INSIGHTS_KEY));
    }

    @Test
    void getReturnsPresentValueForExecutedTask() {
        AdaptiveRequestResult result = resultWith(
            "rewards", TaskResult.executed("pts", ExecutionMode.EXECUTE_WITH_FALLBACK, "fallback")
        );
        assertEquals(Optional.of("pts"), result.get(REWARDS_KEY));
    }

    @Test
    void taskResultMethodExposesFullTaskResult() {
        TaskResult<String> expected = TaskResult.executed("v", ExecutionMode.EXECUTE_APPROXIMATE, "approx");
        AdaptiveRequestResult result = resultWith("balance", expected);
        TaskResult<String> actual = result.taskResult(BALANCE_KEY);
        assertEquals(expected.value(), actual.value());
        assertEquals(expected.executionMode(), actual.executionMode());
        assertEquals(expected.reason(), actual.reason());
    }

    @Test
    void diagnosticsAndDecisionTraceAreDelegated() {
        RequestExecutionDiagnostics diag = new RequestExecutionDiagnostics(
            Duration.ofMillis(200), Duration.ofMillis(50), true,
            List.of("insights"), List.of("rewards"), List.of()
        );
        DecisionTraceEntry traceEntry = new DecisionTraceEntry(
            "insights", Importance.OPTIONAL, ExecutionMode.OMIT,
            "omitted_by_policy", Duration.ofMillis(140), Duration.ZERO, Duration.ofMillis(60)
        );

        RequestExecutionResult raw = new RequestExecutionResult(
            Map.of("insights", TaskResult.omitted("omitted_by_policy")),
            List.of(traceEntry),
            diag
        );
        AdaptiveRequestResult result = new AdaptiveRequestResult(raw);

        assertSame(diag, result.diagnostics());
        assertEquals(List.of(traceEntry), result.decisionTrace());
        assertSame(raw, result.raw());
    }

    // -------------------------------------------------------------------------
    // Backward compatibility – raw RequestExecutionResult still accessible
    // -------------------------------------------------------------------------

    @Test
    void rawDelegateAllowsLegacyStringLookup() {
        AdaptiveRequestResult result = resultWith(
            "balance", TaskResult.executed("v", ExecutionMode.EXECUTE, "normal")
        );
        // String-based access through the raw result still works
        TaskResult<?> legacy = result.raw().taskResult("balance");
        assertEquals(Optional.of("v"), legacy.value());
    }

    // -------------------------------------------------------------------------
    // Grouped execution via real DefaultAdaptiveExecutor
    // -------------------------------------------------------------------------

    @Test
    void groupedRequestExecutionProducesTypedResults() {
        com.budgetflow.core.execution.DefaultAdaptiveExecutor realExecutor =
            new com.budgetflow.core.execution.DefaultAdaptiveExecutor(
                new com.budgetflow.core.policy.DefaultBudgetPolicyEngine()
            );

        AdaptiveRequest request = AdaptiveRequest.builder()
            .mandatory(BALANCE_KEY, Duration.ofMillis(40), () -> "balance-ok")
            .optional(INSIGHTS_KEY, Duration.ofMillis(140), () -> "insights-ok")
            .build();

        AdaptiveRequestResult result = request.execute(realExecutor).toCompletableFuture().join();

        assertEquals("balance-ok", result.require(BALANCE_KEY));
        assertFalse(result.diagnostics().degraded());
        assertNotNull(result.decisionTrace());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AdaptiveRequestResult resultWith(String taskName, TaskResult<String> taskResult) {
        return new AdaptiveRequestResult(simpleResult(Map.of(taskName, taskResult)));
    }

    private RequestExecutionResult simpleResult(Map<String, TaskResult<?>> results) {
        return new RequestExecutionResult(results, List.of());
    }
}
