package com.budgetflow.core.execution;

import com.budgetflow.core.api.AdaptiveExecutor;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

public class DefaultAdaptiveExecutor implements AdaptiveExecutor {
    private final Executor executor;

    public DefaultAdaptiveExecutor() {
        this(ForkJoinPool.commonPool());
    }

    public DefaultAdaptiveExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public <T> CompletionStage<T> executeMandatory(String taskName, Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    @Override
    public <T> CompletionStage<Optional<T>> executeImportant(String taskName, Supplier<T> task) {
        return safeExecute(task);
    }

    @Override
    public <T> CompletionStage<Optional<T>> executeOptional(String taskName, Supplier<T> task) {
        return safeExecute(task);
    }

    private <T> CompletionStage<Optional<T>> safeExecute(Supplier<T> task) {
        return CompletableFuture
            .supplyAsync(task, executor)
            .handle((value, throwable) -> throwable == null ? Optional.ofNullable(value) : Optional.empty());
    }
}
