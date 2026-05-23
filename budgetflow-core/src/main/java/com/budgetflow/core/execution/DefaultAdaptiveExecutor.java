package com.budgetflow.core.execution;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.ExecutionBudget;
import com.budgetflow.core.api.RequestExecutionResult;
import com.budgetflow.core.api.TaskResult;
import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.context.BudgetContextHolder;
import com.budgetflow.core.metadata.RequestExecutionDiagnostics;
import com.budgetflow.core.policy.BudgetPolicyEngine;
import com.budgetflow.core.policy.DefaultBudgetPolicyEngine;
import com.budgetflow.core.policy.DefaultSystemPressureProvider;
import com.budgetflow.core.policy.PolicyDecision;
import com.budgetflow.core.policy.PolicyEvaluationInput;
import com.budgetflow.core.policy.SystemPressureProvider;
import com.budgetflow.core.policy.TaskDescriptor;
import com.budgetflow.core.policy.TaskExecutionDirective;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final SystemPressureProvider pressureProvider;

    public DefaultAdaptiveExecutor() {
        this(ForkJoinPool.commonPool(), new DefaultBudgetPolicyEngine(), new DefaultSystemPressureProvider());
    }

    public DefaultAdaptiveExecutor(BudgetPolicyEngine budgetPolicyEngine) {
        this(ForkJoinPool.commonPool(), budgetPolicyEngine, new DefaultSystemPressureProvider());
    }

    public DefaultAdaptiveExecutor(Executor executor) {
        this(executor, new DefaultBudgetPolicyEngine(), new DefaultSystemPressureProvider());
    }

    public DefaultAdaptiveExecutor(Executor executor, BudgetPolicyEngine budgetPolicyEngine) {
        this(executor, budgetPolicyEngine, new DefaultSystemPressureProvider());
    }

    public DefaultAdaptiveExecutor(BudgetPolicyEngine budgetPolicyEngine, SystemPressureProvider pressureProvider) {
        this(ForkJoinPool.commonPool(), budgetPolicyEngine, pressureProvider);
    }

    public DefaultAdaptiveExecutor(Executor executor, BudgetPolicyEngine budgetPolicyEngine, SystemPressureProvider pressureProvider) {
        this.executor = executor;
        this.budgetPolicyEngine = Objects.requireNonNull(budgetPolicyEngine, "budgetPolicyEngine must not be null");
        this.pressureProvider = Objects.requireNonNull(pressureProvider, "pressureProvider must not be null");
    }

    @Override
    public <T> CompletionStage<TaskResult<T>> execute(TaskSpec<T> taskSpec) {
        return executeRequest(List.of(taskSpec))
            .thenApply(result -> result.taskResult(taskSpec.taskName()));
    }

    @Override
    public CompletionStage<RequestExecutionResult> executeRequest(List<TaskSpec<?>> taskSpecs) {
        validateTaskNames(taskSpecs);
        Optional<ExecutionBudget> requestBudget = currentExecutionBudget();
        Duration availableBudget = requestBudget.map(ExecutionBudget::remaining).orElse(DEFAULT_REMAINING_BUDGET);
        PolicyDecision decision = evaluateDecision(taskSpecs, availableBudget);
        Map<String, TaskExecutionDirective> directivesByName = directivesByTaskName(decision, taskSpecs);
        List<CompletableFuture<TaskResult<?>>> futures = taskSpecs.stream()
            .map(taskSpec -> executeWithDirective(taskSpec, directivesByName.get(taskSpec.taskName())).toCompletableFuture())
            .toList();

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return allDone.thenApply(ignored -> {
            Map<String, TaskResult<?>> results = new LinkedHashMap<>();
            for (int index = 0; index < taskSpecs.size(); index++) {
                results.put(taskSpecs.get(index).taskName(), futures.get(index).join());
            }
            Duration totalRequestBudget = requestBudget.map(ExecutionBudget::totalBudget).orElse(availableBudget);
            Duration remainingRequestBudget = requestBudget.map(ExecutionBudget::remaining).orElse(DEFAULT_REMAINING_BUDGET);
            RequestExecutionDiagnostics diagnostics = RequestExecutionDiagnostics.from(
                results,
                decision.decisionTrace(),
                totalRequestBudget,
                remainingRequestBudget
            );
            return new RequestExecutionResult(results, decision.decisionTrace(), diagnostics);
        });
    }

    private void validateTaskNames(List<TaskSpec<?>> taskSpecs) {
        long distinctNames = taskSpecs.stream().map(TaskSpec::taskName).distinct().count();
        if (distinctNames != taskSpecs.size()) {
            throw new IllegalArgumentException("Task names must be unique for request-scoped planning.");
        }
    }

    private PolicyDecision evaluateDecision(List<TaskSpec<?>> taskSpecs, Duration availableBudget) {
        PolicyEvaluationInput evaluationInput = new PolicyEvaluationInput(
            availableBudget,
            taskSpecs.stream()
                .map(this::toTaskDescriptor)
                .toList(),
            pressureProvider.currentPressure()
        );
        return budgetPolicyEngine.evaluate(evaluationInput);
    }

    private Optional<ExecutionBudget> currentExecutionBudget() {
        return BudgetContextHolder.current().map(context -> context.getExecutionBudget());
    }

    private Map<String, TaskExecutionDirective> directivesByTaskName(PolicyDecision decision, List<TaskSpec<?>> taskSpecs) {
        Map<String, TaskExecutionDirective> directivesByName = new LinkedHashMap<>();
        for (TaskExecutionDirective directive : decision.directives()) {
            directivesByName.put(directive.taskName(), directive);
        }
        for (TaskSpec<?> taskSpec : taskSpecs) {
            directivesByName.computeIfAbsent(taskSpec.taskName(), name -> defaultDirective(name));
        }
        return directivesByName;
    }

    private TaskDescriptor toTaskDescriptor(TaskSpec<?> taskSpec) {
        return new TaskDescriptor(
            taskSpec.taskName(),
            taskSpec.importance(),
            taskSpec.expectedLatency(),
            taskSpec.fallbackSupplier().isPresent(),
            taskSpec.approximateSupplier().isPresent()
        );
    }

    private <T> CompletionStage<TaskResult<?>> executeWithDirective(TaskSpec<T> taskSpec, TaskExecutionDirective directive) {
        if (directive.omitted() || directive.executionMode() == ExecutionMode.OMIT) {
            return CompletableFuture.completedFuture(TaskResult.omitted(nonEmptyReason(directive.reason(), "omitted_by_policy")));
        }
        return CompletableFuture.supplyAsync(() -> executeDirective(taskSpec, directive), executor);
    }

    private <T> TaskResult<?> executeDirective(TaskSpec<T> taskSpec, TaskExecutionDirective directive) {
        return switch (directive.executionMode()) {
            case EXECUTE -> TaskResult.executed(
                taskSpec.primarySupplier().get(),
                ExecutionMode.EXECUTE,
                nonEmptyReason(directive.reason(), "normal_execution")
            );
            case EXECUTE_WITH_FALLBACK -> {
                Supplier<T> fallbackSupplier = taskSpec.fallbackSupplier().orElse(taskSpec.primarySupplier());
                yield TaskResult.executed(
                    fallbackSupplier.get(),
                    ExecutionMode.EXECUTE_WITH_FALLBACK,
                    nonEmptyReason(directive.reason(), "fallback_selected")
                );
            }
            case EXECUTE_APPROXIMATE -> {
                Supplier<T> approximateSupplier = taskSpec.approximateSupplier().orElse(taskSpec.primarySupplier());
                yield TaskResult.executed(
                    approximateSupplier.get(),
                    ExecutionMode.EXECUTE_APPROXIMATE,
                    nonEmptyReason(directive.reason(), "approximate_selected")
                );
            }
            case OMIT -> TaskResult.omitted(nonEmptyReason(directive.reason(), "omitted_by_policy"));
        };
    }

    private TaskExecutionDirective defaultDirective(String taskName) {
        return new TaskExecutionDirective(taskName, ExecutionMode.EXECUTE, Duration.ZERO, false, "normal");
    }

    private String nonEmptyReason(String reason, String fallbackReason) {
        return reason == null || reason.isBlank() ? fallbackReason : reason;
    }
}
