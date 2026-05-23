package com.budgetflow.core.api;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public interface AdaptiveExecutor {
    <T> CompletionStage<TaskResult<T>> execute(TaskSpec<T> taskSpec);

    default CompletionStage<RequestExecutionResult> executeRequest(List<TaskSpec<?>> taskSpecs) {
        List<CompletableFuture<TaskResult<?>>> futures = taskSpecs.stream()
            .map(taskSpec -> executeUnchecked(taskSpec).toCompletableFuture())
            .toList();

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return all.thenApply(ignored -> RequestExecutionResult.fromTaskSpecs(taskSpecs, futures.stream()
            .map(CompletableFuture::join)
            .toList()));
    }

    @SuppressWarnings("unchecked")
    private <T> CompletionStage<TaskResult<?>> executeUnchecked(TaskSpec<?> taskSpec) {
        return (CompletionStage<TaskResult<?>>) (CompletionStage<?>) execute((TaskSpec<T>) taskSpec);
    }

    default <T> CompletionStage<T> executeMandatory(String taskName, Supplier<T> task) {
        return execute(TaskSpec.mandatory(taskName, Duration.ZERO, task))
            .thenApply(result -> result.value()
                .orElseThrow(() -> new IllegalStateException("Mandatory task did not produce a value: " + taskName)));
    }

    default <T> CompletionStage<Optional<T>> executeImportant(String taskName, Supplier<T> task) {
        return execute(TaskSpec.important(taskName, Duration.ZERO, task))
            .handle((result, throwable) -> throwable == null && !result.omitted() ? result.value() : Optional.empty());
    }

    default <T> CompletionStage<Optional<T>> executeOptional(String taskName, Supplier<T> task) {
        return execute(TaskSpec.optional(taskName, Duration.ZERO, task))
            .handle((result, throwable) -> throwable == null && !result.omitted() ? result.value() : Optional.empty());
    }
}
