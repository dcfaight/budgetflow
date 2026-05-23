package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.api.RequestExecutionResult;
import com.budgetflow.core.api.TaskResult;
import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.budget.DefaultExecutionBudget;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.context.BudgetContext;
import com.budgetflow.core.context.BudgetContextHolder;
import com.budgetflow.core.execution.DefaultAdaptiveExecutor;
import com.budgetflow.core.metadata.RequestExecutionDiagnostics;
import com.budgetflow.core.policy.DefaultBudgetPolicyEngine;
import com.budgetflow.demo.fintech.dashboard.BalanceClient;
import com.budgetflow.demo.fintech.dashboard.DashboardTaskSpecs;
import com.budgetflow.demo.fintech.dashboard.InsightsClient;
import com.budgetflow.demo.fintech.dashboard.OffersClient;
import com.budgetflow.demo.fintech.dashboard.RewardsClient;
import com.budgetflow.demo.fintech.dashboard.SimulationSupport;
import com.budgetflow.demo.fintech.dashboard.TransactionClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DashboardComparisonHarness implements AutoCloseable {
    private static final String ACCOUNT_ID = "acc-123";
    private static final String NAIVE_PARALLEL = "naive_parallel";
    private static final String BUDGETFLOW_ADAPTIVE = "budgetflow_adaptive";

    private final ExecutorService executorService;
    private final BalanceClient balanceClient;
    private final TransactionClient transactionClient;
    private final RewardsClient rewardsClient;
    private final OffersClient offersClient;
    private final InsightsClient insightsClient;

    public DashboardComparisonHarness() {
        this(Executors.newFixedThreadPool(8), new SimulationSupport());
    }

    public DashboardComparisonHarness(SimulationSupport simulationSupport) {
        this(Executors.newFixedThreadPool(8), simulationSupport);
    }

    DashboardComparisonHarness(ExecutorService executorService, SimulationSupport simulationSupport) {
        this.executorService = executorService;
        this.balanceClient = new BalanceClient(simulationSupport);
        this.transactionClient = new TransactionClient(simulationSupport);
        this.rewardsClient = new RewardsClient(simulationSupport);
        this.offersClient = new OffersClient(simulationSupport);
        this.insightsClient = new InsightsClient(simulationSupport);
    }

    public static void main(String[] args) {
        HarnessOptions options = HarnessOptions.parse(args);
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness()) {
            DashboardScenarioPack pack = PressureScenarios.packNamed(options.packName());
            List<DashboardBenchmarkSummary> summaries = harness.run(pack.scenarios());
            String output = options.json()
                ? DashboardBenchmarkFormatter.formatJson(pack, summaries)
                : DashboardBenchmarkFormatter.format(pack, summaries);
            System.out.println(output);
        }
    }

    public List<DashboardBenchmarkSummary> runDefaultScenarios() {
        return run(PressureScenarios.defaultScenarios());
    }

    public List<DashboardBenchmarkSummary> run(List<DashboardBenchmarkScenario> scenarios) {
        return scenarios.stream()
            .flatMap(scenario -> List.of(runNaiveParallel(scenario), runAdaptive(scenario)).stream())
            .toList();
    }

    @Override
    public void close() {
        BudgetContextHolder.clear();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    private DashboardBenchmarkSummary runNaiveParallel(DashboardBenchmarkScenario scenario) {
        List<TaskSpec<?>> taskSpecs = taskSpecs();
        List<CompletableFuture<TaskResult<Object>>> futures = taskSpecs.stream()
            .map(taskSpec -> CompletableFuture.supplyAsync(
                () -> TaskResult.executed(taskSpec.primarySupplier().get(), ExecutionMode.EXECUTE, "naive_parallel"),
                executorService
            ))
            .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        Map<String, TaskResult<?>> results = new LinkedHashMap<>();
        for (int index = 0; index < taskSpecs.size(); index++) {
            results.put(taskSpecs.get(index).taskName(), futures.get(index).join());
        }

        Duration projectedWork = DashboardTaskSpecs.totalPrimaryLatency();
        boolean degraded = projectedWork.compareTo(scenario.requestBudget()) > 0;
        List<String> degradationReasons = degraded
            ? List.of("projected_work_exceeds_request_budget_by_%dms"
                .formatted(projectedWork.minus(scenario.requestBudget()).toMillis()))
            : List.of();
        return summaryFor(
            scenario,
            NAIVE_PARALLEL,
            results,
            List.of(),
            List.of(),
            List.of(),
            degraded,
            projectedWork,
            degradationReasons
        );
    }

    private DashboardBenchmarkSummary runAdaptive(DashboardBenchmarkScenario scenario) {
        BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(scenario.requestBudget())));
        try {
            RequestExecutionResult result = new DefaultAdaptiveExecutor(
                executorService,
                new DefaultBudgetPolicyEngine(),
                scenario::pressureSnapshot
            ).executeRequest(taskSpecs()).toCompletableFuture().join();

            Duration projectedWork = result.taskResults().entrySet().stream()
                .map(entry -> DashboardTaskSpecs.expectedLatency(entry.getKey(), entry.getValue().executionMode()))
                .reduce(Duration.ZERO, Duration::plus);

            List<String> degradationReasons = result.decisionTrace().stream()
                .filter(entry -> entry.selectedExecutionMode() != ExecutionMode.EXECUTE)
                .map(entry -> entry.taskName() + "=" + entry.reason())
                .toList();

            return summaryFor(
                scenario,
                BUDGETFLOW_ADAPTIVE,
                result.taskResults(),
                result.diagnostics().omittedTaskNames(),
                result.diagnostics().fallbackTaskNames(),
                result.diagnostics().approximatedTaskNames(),
                result.diagnostics().degraded(),
                projectedWork,
                degradationReasons
            );
        } finally {
            BudgetContextHolder.clear();
        }
    }

    private DashboardBenchmarkSummary summaryFor(
        DashboardBenchmarkScenario scenario,
        String strategy,
        Map<String, TaskResult<?>> taskResults,
        List<String> omittedTasks,
        List<String> fallbackTasks,
        List<String> approximatedTasks,
        boolean degraded,
        Duration projectedWork,
        List<String> degradationReasons
    ) {
        int executedTasks = (int) taskResults.values().stream()
            .filter(taskResult -> !taskResult.omitted())
            .count();
        return new DashboardBenchmarkSummary(
            scenario,
            strategy,
            executedTasks,
            omittedTasks,
            fallbackTasks,
            approximatedTasks,
            degraded,
            projectedWork,
            degradationReasons
        );
    }

    private List<TaskSpec<?>> taskSpecs() {
        return DashboardTaskSpecs.forAccount(
            ACCOUNT_ID,
            balanceClient,
            transactionClient,
            rewardsClient,
            offersClient,
            insightsClient
        ).taskSpecs();
    }

    private record HarnessOptions(String packName, boolean json) {
        private static HarnessOptions parse(String[] args) {
            String packName = "default";
            boolean json = false;
            for (String arg : args) {
                if ("--json".equals(arg)) {
                    json = true;
                    continue;
                }
                if (arg.startsWith("--pack=")) {
                    packName = arg.substring("--pack=".length());
                }
            }
            return new HarnessOptions(packName, json);
        }
    }
}
