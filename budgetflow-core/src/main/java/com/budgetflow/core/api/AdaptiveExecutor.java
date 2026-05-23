package com.budgetflow.core.api;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Core execution contract for BudgetFlow.
 * <p>
 * For most application code, prefer the grouped request path:
 * {@link TaskKey} + {@link AdaptiveRequest} + {@link AdaptiveRequestResult}.
 * <p>
 * This interface remains the foundational execution API for framework, advanced,
 * and integration-oriented usage.
 */
public interface AdaptiveExecutor {
    /**
     * Executes a single task spec.
     * <p>
     * This is a lower-level API intended for fine-grained or infrastructure usage.
     */
    <T> CompletionStage<TaskResult<T>> execute(TaskSpec<T> taskSpec);

    /**
     * Executes a group of task specs under one request-scoped planning decision.
     * <p>
     * This is the foundational grouped execution path used by both
     * {@link AdaptiveRequest} and lower-level integrations.
     */
    CompletionStage<RequestExecutionResult> executeRequest(List<TaskSpec<?>> taskSpecs);

    /**
     * Convenience helper for single mandatory work with no expected-latency signal.
     * <p>
     * Prefer {@link AdaptiveRequest} for normal multi-task application flows.
     */
    default <T> CompletionStage<T> executeMandatory(String taskName, Supplier<T> task) {
        return execute(TaskSpec.mandatory(taskName, Duration.ZERO, task))
            .thenApply(result -> result.value()
                .orElseThrow(() -> new IllegalStateException("Mandatory task did not produce a value: " + taskName)));
    }

    /**
     * Convenience helper for single important work with no expected-latency signal.
     * <p>
     * Prefer {@link AdaptiveRequest} for normal multi-task application flows.
     */
    default <T> CompletionStage<Optional<T>> executeImportant(String taskName, Supplier<T> task) {
        return execute(TaskSpec.important(taskName, Duration.ZERO, task))
            .handle((result, throwable) -> throwable == null && !result.omitted() ? result.value() : Optional.empty());
    }

    /**
     * Convenience helper for single optional work with no expected-latency signal.
     * <p>
     * Prefer {@link AdaptiveRequest} for normal multi-task application flows.
     */
    default <T> CompletionStage<Optional<T>> executeOptional(String taskName, Supplier<T> task) {
        return execute(TaskSpec.optional(taskName, Duration.ZERO, task))
            .handle((result, throwable) -> throwable == null && !result.omitted() ? result.value() : Optional.empty());
    }
}
