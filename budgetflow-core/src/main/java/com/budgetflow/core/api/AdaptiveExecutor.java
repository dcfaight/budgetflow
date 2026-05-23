package com.budgetflow.core.api;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public interface AdaptiveExecutor {
    <T> CompletionStage<T> executeMandatory(String taskName, Supplier<T> task);

    <T> CompletionStage<Optional<T>> executeImportant(String taskName, Supplier<T> task);

    <T> CompletionStage<Optional<T>> executeOptional(String taskName, Supplier<T> task);
}
