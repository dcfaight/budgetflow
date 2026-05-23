package com.budgetflow.demo.fintech.benchmark;

import java.util.List;

public record DashboardScenarioPack(
    String name,
    String description,
    List<DashboardBenchmarkScenario> scenarios
) {
    public DashboardScenarioPack {
        scenarios = List.copyOf(scenarios);
    }
}
