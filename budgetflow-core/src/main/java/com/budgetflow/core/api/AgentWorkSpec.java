package com.budgetflow.core.api;

import com.budgetflow.core.classification.Importance;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thin, agent-oriented descriptor that maps directly to {@link TaskSpec}.
 * <p>
 * This is intentionally a vocabulary adapter, not a second planning model.
 * Use {@link #toTaskSpec()} to execute through the existing
 * {@link AdaptiveExecutor} / {@link AdaptiveRequest} flow.
 */
public final class AgentWorkSpec<T> {

    private final TaskSpec<T> taskSpec;

    private AgentWorkSpec(TaskSpec<T> taskSpec) {
        this.taskSpec = Objects.requireNonNull(taskSpec, "taskSpec must not be null");
    }

    public static <T> AgentWorkSpec<T> mandatory(String workName, Duration expectedLatency, Supplier<T> supplier) {
        return new AgentWorkSpec<>(TaskSpec.mandatory(workName, expectedLatency, supplier));
    }

    public static <T> AgentWorkSpec<T> mandatory(TaskKey<T> key, Duration expectedLatency, Supplier<T> supplier) {
        return new AgentWorkSpec<>(TaskSpec.mandatory(key, expectedLatency, supplier));
    }

    public static <T> AgentWorkSpec<T> important(String workName, Duration expectedLatency, Supplier<T> supplier) {
        return new AgentWorkSpec<>(TaskSpec.important(workName, expectedLatency, supplier));
    }

    public static <T> AgentWorkSpec<T> important(TaskKey<T> key, Duration expectedLatency, Supplier<T> supplier) {
        return new AgentWorkSpec<>(TaskSpec.important(key, expectedLatency, supplier));
    }

    public static <T> AgentWorkSpec<T> optional(String workName, Duration expectedLatency, Supplier<T> supplier) {
        return new AgentWorkSpec<>(TaskSpec.optional(workName, expectedLatency, supplier));
    }

    public static <T> AgentWorkSpec<T> optional(TaskKey<T> key, Duration expectedLatency, Supplier<T> supplier) {
        return new AgentWorkSpec<>(TaskSpec.optional(key, expectedLatency, supplier));
    }

    public static <T> AgentWorkSpec<T> fromTaskSpec(TaskSpec<T> taskSpec) {
        return new AgentWorkSpec<>(taskSpec);
    }

    public AgentWorkSpec<T> withFallback(Supplier<T> fallbackSupplier) {
        return withFallback(fallbackSupplier, taskSpec.expectedLatency());
    }

    public AgentWorkSpec<T> withFallback(Supplier<T> fallbackSupplier, Duration fallbackExpectedLatency) {
        return new AgentWorkSpec<>(taskSpec.withFallback(fallbackSupplier, fallbackExpectedLatency));
    }

    public AgentWorkSpec<T> withApproximate(Supplier<T> approximateSupplier) {
        return withApproximate(approximateSupplier, taskSpec.expectedLatency());
    }

    public AgentWorkSpec<T> withApproximate(Supplier<T> approximateSupplier, Duration approximateExpectedLatency) {
        return new AgentWorkSpec<>(taskSpec.withApproximate(approximateSupplier, approximateExpectedLatency));
    }

    public TaskSpec<T> toTaskSpec() {
        return taskSpec;
    }

    public String workName() {
        return taskSpec.taskName();
    }

    public Importance importance() {
        return taskSpec.importance();
    }

    public Duration expectedLatency() {
        return taskSpec.expectedLatency();
    }

    public Optional<Supplier<T>> fallbackSupplier() {
        return taskSpec.fallbackSupplier();
    }

    public Optional<Supplier<T>> approximateSupplier() {
        return taskSpec.approximateSupplier();
    }

    public Optional<Duration> fallbackExpectedLatency() {
        return taskSpec.fallbackExpectedLatency();
    }

    public Optional<Duration> approximateExpectedLatency() {
        return taskSpec.approximateExpectedLatency();
    }
}
