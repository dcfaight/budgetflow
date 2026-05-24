package com.budgetflow.demo.fintech.benchmark;

import java.time.Duration;
import java.util.List;

public record DashboardBenchmarkSummary(
    DashboardBenchmarkScenario scenario,
    String executionStrategy,
    String policyProfile,
    int totalTasksExecuted,
    List<String> omittedTasks,
    List<String> fallbackTasks,
    List<String> approximatedTasks,
    boolean degraded,
    Duration projectedWork,
    List<String> degradationReasons
) {
    public DashboardBenchmarkSummary {
        policyProfile = policyProfile == null || policyProfile.isBlank() ? "-" : policyProfile;
        omittedTasks = List.copyOf(omittedTasks);
        fallbackTasks = List.copyOf(fallbackTasks);
        approximatedTasks = List.copyOf(approximatedTasks);
        degradationReasons = List.copyOf(degradationReasons);
    }

    public Duration requestBudget() {
        return scenario.requestBudget();
    }

    public String scenarioName() {
        return scenario.name();
    }
}
