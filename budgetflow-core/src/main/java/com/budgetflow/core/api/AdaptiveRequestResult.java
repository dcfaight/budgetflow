package com.budgetflow.core.api;

import com.budgetflow.core.metadata.RequestExecutionDiagnostics;
import com.budgetflow.core.policy.DecisionTraceEntry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A higher-level wrapper around {@link RequestExecutionResult} that provides
 * typed task result access via {@link TaskKey TaskKey&lt;T&gt;}.
 * <p>
 * Eliminates the need for string-based map lookups and unchecked casts that
 * are otherwise required when consuming a raw {@link RequestExecutionResult}.
 * <p>
 * Example:
 * <pre>{@code
 * AdaptiveRequestResult result = request.execute(executor).toCompletableFuture().join();
 * Balance balance   = result.require(BALANCE_KEY);
 * Optional<Rewards> rewards = result.get(REWARDS_KEY);
 * }</pre>
 */
public final class AdaptiveRequestResult {

    private final RequestExecutionResult delegate;

    public AdaptiveRequestResult(RequestExecutionResult delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Returns the task value, throwing if it is absent.
     *
     * @throws IllegalStateException if the task was omitted or produced no value
     */
    public <T> T require(TaskKey<T> key) {
        return this.<T>taskResult(key).value()
            .orElseThrow(() -> new IllegalStateException(
                "Task did not produce a value: " + key.name()));
    }

    /**
     * Returns the task value as an {@link Optional}, which is empty when the
     * task was omitted or produced no value.
     */
    public <T> Optional<T> get(TaskKey<T> key) {
        return this.<T>taskResult(key).value();
    }

    /**
     * Returns the full {@link TaskResult} for the given key, including
     * execution mode and omission metadata.
     */
    public <T> TaskResult<T> taskResult(TaskKey<T> key) {
        return delegate.taskResult(key.name());
    }

    /** Decision trace from the underlying policy evaluation. */
    public List<DecisionTraceEntry> decisionTrace() {
        return delegate.decisionTrace();
    }

    /** Request-scoped execution diagnostics (degradation, omitted/fallback/approx tasks). */
    public RequestExecutionDiagnostics diagnostics() {
        return delegate.diagnostics();
    }

    /** The underlying {@link RequestExecutionResult} for advanced or legacy access. */
    public RequestExecutionResult raw() {
        return delegate;
    }
}
