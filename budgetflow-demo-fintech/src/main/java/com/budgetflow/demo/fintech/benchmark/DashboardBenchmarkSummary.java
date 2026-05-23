package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.policy.SystemPressureSnapshot;

import java.time.Duration;
import java.util.List;

public record DashboardBenchmarkSummary(
    String scenarioName,
    String executionStrategy,
    int totalTasksExecuted,
    List<String> omittedTasks,
    List<String> fallbackTasks,
    List<String> approximatedTasks,
    boolean degraded,
    Duration requestBudget,
    Duration projectedWork,
    SystemPressureSnapshot pressureSnapshot
) {
    public DashboardBenchmarkSummary {
        omittedTasks = List.copyOf(omittedTasks);
        fallbackTasks = List.copyOf(fallbackTasks);
        approximatedTasks = List.copyOf(approximatedTasks);
    }
}
