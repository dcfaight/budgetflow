package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.policy.SystemPressureSnapshot;

import java.time.Duration;

public record DashboardBenchmarkScenario(
    String packName,
    String name,
    String displayName,
    String narrative,
    String evaluationFocus,
    String interpretationGuidance,
    String realWorldPattern,
    String whatToObserve,
    String budgetProfile,
    String pressureProfile,
    Duration requestBudget,
    SystemPressureSnapshot pressureSnapshot
) {
    public String pressureSummary() {
        return String.format(
            "exec=%.2f db=%.2f down=%.2f",
            pressureSnapshot.executorUtilization(),
            pressureSnapshot.dbPressure(),
            pressureSnapshot.downstreamPressure()
        );
    }
}
