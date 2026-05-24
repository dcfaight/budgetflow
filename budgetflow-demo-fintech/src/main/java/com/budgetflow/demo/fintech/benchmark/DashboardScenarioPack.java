package com.budgetflow.demo.fintech.benchmark;

import java.util.List;

public record DashboardScenarioPack(
    String name,
    String description,
    String bestFor,
    String suggestedCommand,
    List<DashboardBenchmarkScenario> scenarios
) {
    public DashboardScenarioPack {
        scenarios = List.copyOf(scenarios);
    }
}
