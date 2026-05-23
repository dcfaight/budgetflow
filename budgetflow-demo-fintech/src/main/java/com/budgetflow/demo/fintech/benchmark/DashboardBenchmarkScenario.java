package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.policy.SystemPressureSnapshot;

import java.time.Duration;

public record DashboardBenchmarkScenario(
    String name,
    Duration requestBudget,
    SystemPressureSnapshot pressureSnapshot
) {
}
