package com.budgetflow.core.api;

import java.util.Objects;

/**
 * A typed handle that pairs a task name with its expected result type.
 * <p>
 * {@code TaskKey<T>} serves as both a lookup token and a compile-time type
 * witness, eliminating the need for string-based map lookups and unchecked
 * casts when retrieving results from an {@link AdaptiveRequestResult}.
 * <p>
 * Example:
 * <pre>{@code
 * TaskKey<Balance> BALANCE_KEY = TaskKey.of("balance");
 * Balance balance = result.require(BALANCE_KEY);
 * Optional<Balance> maybeBalance = result.get(BALANCE_KEY);
 * }</pre>
 */
public record TaskKey<T>(String name) {

    public TaskKey {
        Objects.requireNonNull(name, "name must not be null");
    }

    public static <T> TaskKey<T> of(String name) {
        return new TaskKey<>(name);
    }
}
