package com.budgetflow.core.execution;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.ExecutionBudget;
import com.budgetflow.core.api.TaskResult;
import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.context.BudgetContextHolder;
import com.budgetflow.core.policy.BudgetPolicyEngine;
import com.budgetflow.core.policy.DefaultBudgetPolicyEngine;
import com.budgetflow.core.policy.PolicyDecision;
import com.budgetflow.core.policy.PolicyEvaluationInput;
import com.budgetflow.core.policy.SystemPressureSnapshot;
import com.budgetflow.core.policy.TaskDescriptor;
import com.budgetflow.core.policy.TaskExecutionDirective;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

public class DefaultAdaptiveExecutor implements AdaptiveExecutor {
    private static final Duration DEFAULT_REMAINING_BUDGET = Duration.ofMillis(1_000);

    private final Executor executor;
    private final BudgetPolicyEngine budgetPolicyEngine;

    public DefaultAdaptiveExecutor() {
        this(ForkJoinPool.commonPool(), new DefaultBudgetPolicyEngine());
    }

    public DefaultAdaptiveExecutor(BudgetPolicyEngine budgetPolicyEngine) {
        this(ForkJoinPool.commonPool(), budgetPolicyEngine);
    }

    public DefaultAdaptiveExecutor(Executor executor) {
        this(executor, new DefaultBudgetPolicyEngine());
    }

    public DefaultAdaptiveExecutor(Executor executor, BudgetPolicyEngine budgetPolicyEngine) {
        this.executor = executor;
        this.budgetPolicyEngine = Objects.requireNonNull(budgetPolicyEngine, "budgetPolicyEngine must not be null");
    }

    @Override
    public <T> CompletionStage<TaskResult<T>> execute(TaskSpec<T> taskSpec) {
        TaskExecutionDirective directive = evaluateDirective(taskSpec);
        if (directive.omitted() || directive.executionMode() == ExecutionMode.OMIT) {
            return CompletableFuture.completedFuture(TaskResult.omitted(nonEmptyReason(directive.reason(), "omitted_by_policy")));
        }

        return CompletableFuture.supplyAsync(() -> executeDirective(taskSpec, directive), executor);
    }

    private <T> TaskResult<T> executeDirective(TaskSpec<T> taskSpec, TaskExecutionDirective directive) {
        return switch (directive.executionMode()) {
            case EXECUTE -> TaskResult.executed(taskSpec.primarySupplier().get(), ExecutionMode.EXECUTE, nonEmptyReason(directive.reason(), "normal_execution"));
            case EXECUTE_WITH_FALLBACK -> {
                Supplier<T> fallbackSupplier = taskSpec.fallbackSupplier().orElse(taskSpec.primarySupplier());
                yield TaskResult.executed(fallbackSupplier.get(), ExecutionMode.EXECUTE_WITH_FALLBACK, nonEmptyReason(directive.reason(), "fallback_selected"));
            }
            case EXECUTE_APPROXIMATE -> {
                Supplier<T> approximateSupplier = taskSpec.approximateSupplier().orElse(taskSpec.primarySupplier());
                yield TaskResult.executed(approximateSupplier.get(), ExecutionMode.EXECUTE_APPROXIMATE, nonEmptyReason(directive.reason(), "approximate_selected"));
            }
            case OMIT -> TaskResult.omitted(nonEmptyReason(directive.reason(), "omitted_by_policy"));
        };
    }

    private <T> TaskExecutionDirective evaluateDirective(TaskSpec<T> taskSpec) {
        PolicyEvaluationInput evaluationInput = new PolicyEvaluationInput(
            currentRemainingBudget(),
            List.of(new TaskDescriptor(
                taskSpec.taskName(),
                taskSpec.importance(),
                taskSpec.expectedLatency(),
                taskSpec.fallbackSupplier().isPresent(),
                taskSpec.approximateSupplier().isPresent()
            )),
            currentPressureSnapshot()
        );

        PolicyDecision decision = budgetPolicyEngine.evaluate(evaluationInput);
        return decision.directives().stream()
            .filter(directive -> directive.taskName().equals(taskSpec.taskName()))
            .findFirst()
            .orElseGet(() -> defaultDirective(taskSpec.taskName()));
    }

    private TaskExecutionDirective defaultDirective(String taskName) {
        return new TaskExecutionDirective(taskName, ExecutionMode.EXECUTE, Duration.ZERO, false, "normal");
    }

    private Duration currentRemainingBudget() {
        return BudgetContextHolder.current()
            .map(context -> context.getExecutionBudget().remaining())
            .orElse(DEFAULT_REMAINING_BUDGET);
    }

    private SystemPressureSnapshot currentPressureSnapshot() {
        Optional<ExecutionBudget> executionBudget = BudgetContextHolder.current().map(context -> context.getExecutionBudget());
        if (executionBudget.isEmpty()) {
            return new SystemPressureSnapshot(0.0, 0.0, 0.0);
        }

        Duration total = executionBudget.get().totalBudget();
        Duration remaining = executionBudget.get().remaining();

        if (total.isZero() || total.isNegative()) {
            return new SystemPressureSnapshot(1.0, 1.0, 1.0);
        }

        double utilization = 1.0 - (double) remaining.toMillis() / (double) total.toMillis();
        double normalized = Math.max(0.0, Math.min(utilization, 1.0));
        return new SystemPressureSnapshot(normalized, normalized, normalized);
    }

    private String nonEmptyReason(String reason, String fallbackReason) {
        return reason == null || reason.isBlank() ? fallbackReason : reason;
    }
}
