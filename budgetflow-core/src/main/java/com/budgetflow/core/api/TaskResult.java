package com.budgetflow.core.api;

import com.budgetflow.core.classification.ExecutionMode;

import java.util.Objects;
import java.util.Optional;

/**
 * Per-task execution outcome.
 * <p>
 * Core and advanced integrations often consume this directly.
 * Application-facing grouped usage usually accesses this through
 * {@link AdaptiveRequestResult#taskResult(TaskKey)}.
 */
public record TaskResult<T>(
    Optional<T> value,
    ExecutionMode executionMode,
    boolean omitted,
    String reason
) {
    public TaskResult {
        value = value == null ? Optional.empty() : value;
        Objects.requireNonNull(executionMode, "executionMode must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }

    public static <T> TaskResult<T> executed(T value, ExecutionMode executionMode, String reason) {
        return new TaskResult<>(Optional.ofNullable(value), executionMode, false, reason);
    }

    public static <T> TaskResult<T> omitted(String reason) {
        return new TaskResult<>(Optional.empty(), ExecutionMode.OMIT, true, reason);
    }
}
