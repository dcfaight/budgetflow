package com.budgetflow.core.api;

import com.budgetflow.core.classification.Importance;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

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

    public static <T> TaskSpec<T> important(String taskName, Duration expectedLatency, Supplier<T> primarySupplier) {
        return new TaskSpec<>(taskName, Importance.IMPORTANT, expectedLatency, primarySupplier, Optional.empty(), Optional.empty());
    }

    public static <T> TaskSpec<T> optional(String taskName, Duration expectedLatency, Supplier<T> primarySupplier) {
        return new TaskSpec<>(taskName, Importance.OPTIONAL, expectedLatency, primarySupplier, Optional.empty(), Optional.empty());
    }

    public TaskSpec<T> withFallback(Supplier<T> fallback) {
        return new TaskSpec<>(taskName, importance, expectedLatency, primarySupplier, Optional.ofNullable(fallback), approximateSupplier);
    }

    public TaskSpec<T> withApproximate(Supplier<T> approximate) {
        return new TaskSpec<>(taskName, importance, expectedLatency, primarySupplier, fallbackSupplier, Optional.ofNullable(approximate));
    }
}
