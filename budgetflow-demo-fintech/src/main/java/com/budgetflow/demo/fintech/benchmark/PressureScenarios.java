package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.policy.SystemPressureSnapshot;

import java.time.Duration;
import java.util.List;

/**
 * Factory for deterministic dashboard comparison scenarios and scenario packs.
 */
public final class PressureScenarios {
    public static final SystemPressureSnapshot LOW_PRESSURE =
        new SystemPressureSnapshot(0.15, 0.10, 0.20);

    public static final SystemPressureSnapshot MODERATE_PRESSURE =
        new SystemPressureSnapshot(0.55, 0.62, 0.58);

    public static final SystemPressureSnapshot ELEVATED_PRESSURE =
        new SystemPressureSnapshot(0.90, 0.88, 0.92);

    public static final SystemPressureSnapshot MODERATE_DB_PRESSURE =
        new SystemPressureSnapshot(0.20, 0.65, 0.15);

    public static final SystemPressureSnapshot DOWNSTREAM_SPIKE_PRESSURE =
        new SystemPressureSnapshot(0.30, 0.22, 0.84);

    private PressureScenarios() {
    }

    public static DashboardBenchmarkScenario generousBudgetLowPressure() {
        return scenario(
            "default",
            "generous_budget_low_pressure",
            "Generous budget / low pressure",
            "Healthy system headroom. Adaptive and naive should converge on the same plan.",
            "generous_budget",
            "low_pressure",
            Duration.ofMillis(650),
            LOW_PRESSURE
        );
    }

    public static DashboardBenchmarkScenario constrainedBudgetLowPressure() {
        return scenario(
            "default",
            "constrained_budget_low_pressure",
            "Constrained budget / low pressure",
            "Budget is the binding constraint while infrastructure remains healthy.",
            "constrained_budget",
            "low_pressure",
            Duration.ofMillis(430),
            LOW_PRESSURE
        );
    }

    public static DashboardBenchmarkScenario constrainedBudgetElevatedPressure() {
        return scenario(
            "default",
            "constrained_budget_elevated_pressure",
            "Constrained budget / elevated pressure",
            "Both the request budget and system pressure are tight, so adaptive planning should degrade more aggressively.",
            "constrained_budget",
            "elevated_pressure",
            Duration.ofMillis(430),
            ELEVATED_PRESSURE
        );
    }

    public static DashboardBenchmarkScenario generousBudgetElevatedPressure() {
        return scenario(
            "extended",
            "generous_budget_elevated_pressure",
            "Generous budget / elevated pressure",
            "Budget is available, but system pressure alone should still trigger graceful degradation.",
            "generous_budget",
            "elevated_pressure",
            Duration.ofMillis(650),
            ELEVATED_PRESSURE
        );
    }

    public static DashboardBenchmarkScenario tightBudgetModerateDbPressure() {
        return scenario(
            "extended",
            "tight_budget_moderate_db_pressure",
            "Tight budget / moderate DB pressure",
            "A slow database is the dominant bottleneck, simulating query queue pressure rather than global platform overload.",
            "tight_budget",
            "moderate_db_pressure",
            Duration.ofMillis(300),
            MODERATE_DB_PRESSURE
        );
    }

    public static DashboardBenchmarkScenario moderateBudgetDownstreamSpike() {
        return scenario(
            "extended",
            "moderate_budget_downstream_spike",
            "Moderate budget / downstream spike",
            "A downstream dependency spikes while the rest of the platform stays relatively healthy.",
            "moderate_budget",
            "downstream_spike",
            Duration.ofMillis(500),
            DOWNSTREAM_SPIKE_PRESSURE
        );
    }

    public static DashboardScenarioPack defaultPack() {
        return new DashboardScenarioPack(
            "default",
            "Core scenarios for first-time comparison runs.",
            List.of(
                generousBudgetLowPressure(),
                constrainedBudgetLowPressure(),
                constrainedBudgetElevatedPressure()
            )
        );
    }

    public static DashboardScenarioPack extendedPack() {
        return new DashboardScenarioPack(
            "extended",
            "Broader demo scenarios covering budget-only, DB-bound, and downstream-spike pressure conditions.",
            List.of(
                generousBudgetLowPressure(),
                constrainedBudgetLowPressure(),
                constrainedBudgetElevatedPressure(),
                generousBudgetElevatedPressure(),
                tightBudgetModerateDbPressure(),
                moderateBudgetDownstreamSpike()
            )
        );
    }

    public static DashboardScenarioPack realismPack() {
        return new DashboardScenarioPack(
            "realism",
            "Scenario pack emphasizing more varied pressure narratives while remaining deterministic and explainable.",
            List.of(
                constrainedBudgetLowPressure(),
                tightBudgetModerateDbPressure(),
                moderateBudgetDownstreamSpike(),
                constrainedBudgetElevatedPressure()
            )
        );
    }

    public static DashboardScenarioPack packNamed(String packName) {
        return switch (packName) {
            case "default" -> defaultPack();
            case "extended" -> extendedPack();
            case "realism" -> realismPack();
            default -> throw new IllegalArgumentException("Unknown dashboard scenario pack: " + packName);
        };
    }

    public static List<DashboardBenchmarkScenario> defaultScenarios() {
        return defaultPack().scenarios();
    }

    private static DashboardBenchmarkScenario scenario(
        String packName,
        String name,
        String displayName,
        String narrative,
        String budgetProfile,
        String pressureProfile,
        Duration requestBudget,
        SystemPressureSnapshot pressureSnapshot
    ) {
        return new DashboardBenchmarkScenario(
            packName,
            name,
            displayName,
            narrative,
            budgetProfile,
            pressureProfile,
            requestBudget,
            pressureSnapshot
        );
    }
}
