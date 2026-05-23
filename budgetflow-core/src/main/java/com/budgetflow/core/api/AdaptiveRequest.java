package com.budgetflow.core.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * A higher-level, grouped adaptive request that composes multiple
 * {@link TaskSpec TaskSpec&lt;?&gt;} instances and executes them together under a
 * single request budget via {@link AdaptiveExecutor#executeRequest}.
 * <p>
 * This is the preferred application-facing entry point for multi-task request
 * orchestration.
 * <p>
 * Use {@link #builder()} to assemble the request, then {@link #execute} to run
 * it and obtain a typed {@link AdaptiveRequestResult}.
 * <p>
 * Example:
 * <pre>{@code
 * static final TaskKey<Balance>           BALANCE      = TaskKey.of("balance");
 * static final TaskKey<RewardsSummary>    REWARDS      = TaskKey.of("rewards");
 * static final TaskKey<List<Offer>>       OFFERS       = TaskKey.of("offers");
 *
 * AdaptiveRequest request = AdaptiveRequest.builder()
 *     .mandatory(BALANCE, Duration.ofMillis(40), () -> balanceClient.getBalance(id))
 *     .importantWithFallback(REWARDS, Duration.ofMillis(90),
 *                            () -> rewardsClient.getRewards(id),
 *                            () -> rewardsClient.getCached(id))
 *     .optionalWithApproximate(OFFERS, Duration.ofMillis(110),
 *                              () -> offersClient.getOffers(id),
 *                              () -> offersClient.getApproximate(id))
 *     .build();
 *
 * AdaptiveRequestResult result = request.execute(adaptiveExecutor).toCompletableFuture().join();
 *
 * Balance  balance = result.require(BALANCE);
 * Optional<RewardsSummary> rewards = result.get(REWARDS);
 * }</pre>
 *
 * <p>The existing {@link AdaptiveExecutor}, {@link TaskSpec}, and
 * {@link RequestExecutionResult} model is unchanged; this class is purely an
 * ergonomics layer on top.
 */
public final class AdaptiveRequest {

    private final List<TaskSpec<?>> taskSpecs;

    private AdaptiveRequest(List<TaskSpec<?>> taskSpecs) {
        this.taskSpecs = List.copyOf(taskSpecs);
    }

    /**
     * Returns the immutable list of task specs that make up this request.
     * This is the same list that will be passed to
     * {@link AdaptiveExecutor#executeRequest}.
     */
    public List<TaskSpec<?>> taskSpecs() {
        return taskSpecs;
    }

    /**
     * Executes all grouped tasks via the given executor and returns a
     * {@link CompletionStage} that resolves to a typed
     * {@link AdaptiveRequestResult}.
     */
    public CompletionStage<AdaptiveRequestResult> execute(AdaptiveExecutor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        return executor.executeRequest(taskSpecs).thenApply(AdaptiveRequestResult::new);
    }

    /** Returns a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Fluent builder for composing an {@link AdaptiveRequest}.
     * <p>
     * For tasks that require a fallback or approximate supplier, use
     * {@link #task(TaskKey, TaskSpec)} to supply a fully configured
     * {@link TaskSpec} (built with {@link TaskSpec#withFallback} /
     * {@link TaskSpec#withApproximate}).  The key's name must match the spec's
     * task name.
     */
    public static final class Builder {

        private final List<TaskSpec<?>> specs = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds a {@link com.budgetflow.core.classification.Importance#MANDATORY MANDATORY}
         * task.
         */
        public <T> Builder mandatory(TaskKey<T> key, Duration expectedLatency, Supplier<T> supplier) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(expectedLatency, "expectedLatency must not be null");
            Objects.requireNonNull(supplier, "supplier must not be null");
            specs.add(TaskSpec.mandatory(key.name(), expectedLatency, supplier));
            return this;
        }

        /**
         * Adds an {@link com.budgetflow.core.classification.Importance#IMPORTANT IMPORTANT}
         * task.
         */
        public <T> Builder important(TaskKey<T> key, Duration expectedLatency, Supplier<T> supplier) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(expectedLatency, "expectedLatency must not be null");
            Objects.requireNonNull(supplier, "supplier must not be null");
            specs.add(TaskSpec.important(key.name(), expectedLatency, supplier));
            return this;
        }

        /**
         * Adds an {@link com.budgetflow.core.classification.Importance#IMPORTANT IMPORTANT}
         * task with an explicit fallback path.
         */
        public <T> Builder importantWithFallback(
            TaskKey<T> key,
            Duration expectedLatency,
            Supplier<T> supplier,
            Supplier<T> fallbackSupplier
        ) {
            Objects.requireNonNull(fallbackSupplier, "fallbackSupplier must not be null");
            specs.add(TaskSpec.important(key, expectedLatency, supplier).withFallback(fallbackSupplier));
            return this;
        }

        /**
         * Adds an {@link com.budgetflow.core.classification.Importance#OPTIONAL OPTIONAL}
         * task.
         */
        public <T> Builder optional(TaskKey<T> key, Duration expectedLatency, Supplier<T> supplier) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(expectedLatency, "expectedLatency must not be null");
            Objects.requireNonNull(supplier, "supplier must not be null");
            specs.add(TaskSpec.optional(key.name(), expectedLatency, supplier));
            return this;
        }

        /**
         * Adds an {@link com.budgetflow.core.classification.Importance#OPTIONAL OPTIONAL}
         * task with an explicit fallback path.
         */
        public <T> Builder optionalWithFallback(
            TaskKey<T> key,
            Duration expectedLatency,
            Supplier<T> supplier,
            Supplier<T> fallbackSupplier
        ) {
            Objects.requireNonNull(fallbackSupplier, "fallbackSupplier must not be null");
            specs.add(TaskSpec.optional(key, expectedLatency, supplier).withFallback(fallbackSupplier));
            return this;
        }

        /**
         * Adds an {@link com.budgetflow.core.classification.Importance#OPTIONAL OPTIONAL}
         * task with an explicit approximate path.
         */
        public <T> Builder optionalWithApproximate(
            TaskKey<T> key,
            Duration expectedLatency,
            Supplier<T> supplier,
            Supplier<T> approximateSupplier
        ) {
            Objects.requireNonNull(approximateSupplier, "approximateSupplier must not be null");
            specs.add(TaskSpec.optional(key, expectedLatency, supplier).withApproximate(approximateSupplier));
            return this;
        }

        /**
         * Adds an {@link com.budgetflow.core.classification.Importance#OPTIONAL OPTIONAL}
         * task with both fallback and approximate execution paths.
         */
        public <T> Builder optionalWithFallbackAndApproximate(
            TaskKey<T> key,
            Duration expectedLatency,
            Supplier<T> supplier,
            Supplier<T> fallbackSupplier,
            Supplier<T> approximateSupplier
        ) {
            Objects.requireNonNull(fallbackSupplier, "fallbackSupplier must not be null");
            Objects.requireNonNull(approximateSupplier, "approximateSupplier must not be null");
            specs.add(TaskSpec.optional(key, expectedLatency, supplier)
                .withFallback(fallbackSupplier)
                .withApproximate(approximateSupplier));
            return this;
        }

        /**
         * Adds a fully configured {@link TaskSpec}.  Use this variant when
         * the spec has been customised with
         * {@link TaskSpec#withFallback(Supplier)} or
         * {@link TaskSpec#withApproximate(Supplier)}.
         *
         * @throws IllegalArgumentException if the key's name does not match
         *                                  the spec's task name
         */
        public <T> Builder task(TaskKey<T> key, TaskSpec<T> spec) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(spec, "spec must not be null");
            if (!key.name().equals(spec.taskName())) {
                throw new IllegalArgumentException(
                    "TaskKey name '" + key.name() + "' does not match TaskSpec task name '" + spec.taskName() + "'");
            }
            specs.add(spec);
            return this;
        }

        /** Builds the immutable {@link AdaptiveRequest}. */
        public AdaptiveRequest build() {
            if (specs.isEmpty()) {
                throw new IllegalStateException("AdaptiveRequest must contain at least one task");
            }
            return new AdaptiveRequest(specs);
        }
    }
}
