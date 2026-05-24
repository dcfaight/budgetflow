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
            "Sanity baseline: verifies no unnecessary degradation under comfortable constraints.",
            "Treat this as a control case. If adaptive diverges materially here, revisit task hints or policy selection.",
            "Steady weekday traffic with healthy dependencies.",
            "Adaptive and naive should both execute all tasks with no meaningful policy deltas.",
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
            "Budget-only stress: shows whether adaptive planning prioritizes mandatory work and optional pruning.",
            "Expect optional work reductions before mandatory/important behavior changes. Use this to validate budget semantics.",
            "Mobile dashboard request with strict tail-latency SLA during normal operations.",
            "Optional work should be pruned first while mandatory and important tasks stay stable.",
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
            "Mixed-constraint stress: validates combined budget + runtime pressure behavior.",
            "Interpret differences conservatively: this demonstrates policy reaction shape, not production throughput ceilings.",
            "Traffic surge plus dependency stress during peak payment windows.",
            "Look for important-task fallback and optional-task approximation/omission with clear trace reasons.",
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
            "Pressure-only stress: confirms runtime signals can influence planning beyond budget math.",
            "Use this to isolate pressure semantics. Any degradation here should be traceable to pressure bands/signals.",
            "Background contention from unrelated workloads while request budgets remain generous.",
            "Degradation should be pressure-driven, not budget-driven.",
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
            "Dominant-signal stress: demonstrates behavior when one pressure dimension drives decisions.",
            "Compare this against multi-signal scenarios to ensure dominant DB pressure is reflected in trace reasons.",
            "Read-heavy account dashboard when database pools are near saturation.",
            "Decision trace should show DB-dominant pressure while preserving as much response coverage as possible.",
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
            "Dependency-instability stress: evaluates downstream-driven degradation behavior.",
            "Use this to inspect whether optional/important work shifts align with downstream pressure dominance.",
            "Promotions service instability during campaign launch.",
            "Observe downstream-dominant pressure with optional degradations that keep core account data intact.",
            "moderate_budget",
            "downstream_spike",
            Duration.ofMillis(500),
            DOWNSTREAM_SPIKE_PRESSURE
        );
    }

    public static DashboardBenchmarkScenario moderateBudgetElevatedPressure() {
        return scenario(
            "policy",
            "moderate_budget_elevated_pressure",
            "Moderate budget / elevated pressure",
            "Designed for policy-profile comparison: balanced, continuity, and efficiency can diverge clearly under pressure.",
            "Profile comparison scenario: highlights deliberate tradeoffs between continuity and efficiency.",
            "Use deltas for policy selection guidance only; do not treat one scenario as proof of global superiority.",
            "Cross-team profile tuning for high-traffic but user-visible dashboard endpoints.",
            "Compare profile deltas to choose continuity vs headroom priorities for your endpoint class.",
            "moderate_budget",
            "elevated_pressure",
            Duration.ofMillis(520),
            ELEVATED_PRESSURE
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

    public static DashboardScenarioPack policyPack() {
        return new DashboardScenarioPack(
            "policy",
            "Profile-comparison scenarios that highlight planner-policy trade-offs under the same workload.",
            List.of(
                constrainedBudgetLowPressure(),
                constrainedBudgetElevatedPressure(),
                moderateBudgetElevatedPressure(),
                tightBudgetModerateDbPressure()
            )
        );
    }

    public static DashboardScenarioPack packNamed(String packName) {
        return switch (packName) {
            case "default" -> defaultPack();
            case "extended" -> extendedPack();
            case "realism" -> realismPack();
            case "policy" -> policyPack();
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
        String evaluationFocus,
        String interpretationGuidance,
        String realWorldPattern,
        String whatToObserve,
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
            evaluationFocus,
            interpretationGuidance,
            realWorldPattern,
            whatToObserve,
            budgetProfile,
            pressureProfile,
            requestBudget,
            pressureSnapshot
        );
    }
}
