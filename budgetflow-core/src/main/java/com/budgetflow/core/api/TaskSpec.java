package com.budgetflow.core.api;

import com.budgetflow.core.classification.Importance;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Low-level task specification used by the core planner and executor.
 * <p>
 * For application-facing grouped usage, prefer {@link AdaptiveRequest.Builder}
 * with {@link TaskKey}. Use {@code TaskSpec} directly when you need explicit
 * low-level control or framework-style integrations.
 */
public record TaskSpec<T>(
    String taskName,
    Importance importance,
    Duration expectedLatency,
    Supplier<T> primarySupplier,
    Optional<Supplier<T>> fallbackSupplier,
    Optional<Supplier<T>> approximateSupplier
) {
    public TaskSpec {
        Objects.requireNonNull(taskName, "taskName must not be null");
        Objects.requireNonNull(importance, "importance must not be null");
        Objects.requireNonNull(expectedLatency, "expectedLatency must not be null");
        Objects.requireNonNull(primarySupplier, "primarySupplier must not be null");
        fallbackSupplier = fallbackSupplier == null ? Optional.empty() : fallbackSupplier;
        approximateSupplier = approximateSupplier == null ? Optional.empty() : approximateSupplier;
    }

    public static <T> TaskSpec<T> mandatory(String taskName, Duration expectedLatency, Supplier<T> primarySupplier) {
        return new TaskSpec<>(taskName, Importance.MANDATORY, expectedLatency, primarySupplier, Optional.empty(), Optional.empty());
    }

    public static <T> TaskSpec<T> mandatory(TaskKey<T> key, Duration expectedLatency, Supplier<T> primarySupplier) {
        Objects.requireNonNull(key, "key must not be null");
        return mandatory(key.name(), expectedLatency, primarySupplier);
    }

    public static <T> TaskSpec<T> important(String taskName, Duration expectedLatency, Supplier<T> primarySupplier) {
        return new TaskSpec<>(taskName, Importance.IMPORTANT, expectedLatency, primarySupplier, Optional.empty(), Optional.empty());
    }

    public static <T> TaskSpec<T> important(TaskKey<T> key, Duration expectedLatency, Supplier<T> primarySupplier) {
        Objects.requireNonNull(key, "key must not be null");
        return important(key.name(), expectedLatency, primarySupplier);
    }

    public static <T> TaskSpec<T> optional(String taskName, Duration expectedLatency, Supplier<T> primarySupplier) {
        return new TaskSpec<>(taskName, Importance.OPTIONAL, expectedLatency, primarySupplier, Optional.empty(), Optional.empty());
    }

    public static <T> TaskSpec<T> optional(TaskKey<T> key, Duration expectedLatency, Supplier<T> primarySupplier) {
        Objects.requireNonNull(key, "key must not be null");
        return optional(key.name(), expectedLatency, primarySupplier);
    }

    public TaskSpec<T> withFallback(Supplier<T> fallback) {
        return new TaskSpec<>(taskName, importance, expectedLatency, primarySupplier, Optional.ofNullable(fallback), approximateSupplier);
    }

    public TaskSpec<T> withApproximate(Supplier<T> approximate) {
        return new TaskSpec<>(taskName, importance, expectedLatency, primarySupplier, fallbackSupplier, Optional.ofNullable(approximate));
    }
}
