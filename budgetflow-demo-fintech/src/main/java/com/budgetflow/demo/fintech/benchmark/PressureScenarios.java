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

    public static final SystemPressureSnapshot COMMUTER_SPIKE_PRESSURE =
        new SystemPressureSnapshot(0.68, 0.74, 0.70);

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

    public static DashboardBenchmarkScenario tightBudgetLowPressure() {
        return scenario(
            "extended",
            "tight_budget_low_pressure",
            "Tight budget / low pressure",
            "The system is healthy, but the request budget is tight enough that degraded paths matter on their own.",
            "Path-aware budget rescue: validates fallback/approximate latency hints without runtime-pressure noise.",
            "Use this when you want the clearest budget-only proof that degraded-path planning preserves more useful work than primary-path-only reasoning.",
            "Aggressive mobile latency target on an otherwise healthy platform.",
            "Look for budget-driven fallback/approximate choices and plannedExecutionLatency reductions before attributing anything to pressure.",
            "tight_budget",
            "low_pressure",
            Duration.ofMillis(250),
            LOW_PRESSURE
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

    public static DashboardBenchmarkScenario commuterSpikeMixedPressure() {
        return scenario(
            "adoption",
            "commuter_spike_mixed_pressure",
            "Commuter spike / mixed pressure",
            "Request budget is moderate while executor, DB, and downstream pressure all rise together.",
            "Realistic multi-signal stress: validates deterministic mixed-constraint behavior in recognizable traffic spikes.",
            "Use this to verify that adaptive planning keeps mandatory data stable while optional fidelity shifts are explicit and explainable.",
            "Morning commute burst where account refreshes, card notifications, and partner APIs all contend at once.",
            "Expect fallback/approximate optional decisions before omission where degraded paths still fit the request budget.",
            "moderate_budget",
            "mixed_spike",
            Duration.ofMillis(360),
            COMMUTER_SPIKE_PRESSURE
        );
    }

    public static DashboardBenchmarkScenario agentProfileComparison() {
        return scenario(
            "agent",
            "agent_profile_comparison",
            "Agent profile comparison / moderate pressure",
            "The same agent coordination turn run under balanced, continuity, efficiency, and latency_first to surface deliberate profile tradeoffs side-by-side.",
            "Profile selection evidence: shows how profile intent translates to measurable differences in optional coverage, fallback usage, and budget headroom.",
            "Lower optional coverage is not a failure here. latency_first omits optional work by design to protect headroom. continuity preserves more coverage by preferring fallback paths. Compare profiles by their endpoint-goal alignment, not by which profile executes the most tasks.",
            "Cross-team profile review for agent coordination endpoints where latency SLAs and coverage requirements differ by product surface.",
            "Compare executed/fallback/approx/omitted counts and headroom across all four profiles. Verify that latency_first and efficiency omit more optional work than balanced, and that continuity uses more fallback/approx paths."
                + " Read the 'Profile comparison summary' table for a concise side-by-side view.",
            "moderate_budget",
            "moderate_pressure",
            Duration.ofMillis(180),
            MODERATE_PRESSURE
        );
    }

    public static DashboardBenchmarkScenario agentCoordinationHealthy() {
        return scenario(
            "agent",
            "agent_coordination_healthy",
            "Agent coordination / healthy",
            "A coordination-style agent turn (plan → two parallel fetches → consolidate → polish) under a generous budget with no system pressure.",
            "Agent-coordination baseline: verifies all coordination steps execute normally under comfortable conditions.",
            "Use as a control case for coordination semantics. If important steps degrade here, revisit latency hints.",
            "Agent assistant turn with parallel sub-agent fetches under normal operating conditions.",
            "All important coordination steps should execute at primary path; optional polish may adapt gracefully.",
            "generous_budget",
            "low_pressure",
            Duration.ofMillis(300),
            LOW_PRESSURE
        );
    }

    public static DashboardBenchmarkScenario agentCoordinationDegradedCascade() {
        return scenario(
            "agent",
            "agent_coordination_degraded_cascade",
            "Agent coordination / degraded-cascade",
            "Severe joint budget + pressure constraint causes all important coordination steps to fall back simultaneously, demonstrating a full degradation cascade.",
            "Cascade-failure boundary case: validates that joint stress drives deterministic, traceable cascade degradation across multiple important steps.",
            "This is a boundary case, not a typical production condition. A cascade result here is the correct and expected outcome — not a failure. Inspect trace reasons to verify each degradation is deliberate and explainable. The mandatory plan step should still execute normally.",
            "Agent assistant turn under a traffic spike with degraded sub-agent infrastructure.",
            "All important fetch and consolidate steps should fall back to degraded paths; optional step omitted; mandatory plan step still executes. The cascade is expected behavior for this severity of joint constraint.",
            "constrained_budget",
            "elevated_pressure",
            Duration.ofMillis(70),
            ELEVATED_PRESSURE
        );
    }

    public static DashboardScenarioPack defaultPack() {
        return new DashboardScenarioPack(
            "default",
            "Core scenarios for first-time comparison runs.",
            "first-time repo evaluation and baseline adaptive-vs-naive comparison",
            "./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=default\"",
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
            "broader scenario exploration after the default pack",
            "./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=extended\"",
            List.of(
                generousBudgetLowPressure(),
                constrainedBudgetLowPressure(),
                constrainedBudgetElevatedPressure(),
                tightBudgetLowPressure(),
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
            "recognizable real-world pressure patterns and JSON-friendly sharing",
            "./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=realism --json\"",
            List.of(
                constrainedBudgetLowPressure(),
                tightBudgetLowPressure(),
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
            "choosing between balanced, continuity, and efficiency planner profiles",
            "./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=policy --policies=balanced,continuity,efficiency\"",
            List.of(
                constrainedBudgetLowPressure(),
                constrainedBudgetElevatedPressure(),
                moderateBudgetElevatedPressure(),
                tightBudgetModerateDbPressure()
            )
        );
    }

    public static DashboardScenarioPack adoptionPack() {
        return new DashboardScenarioPack(
            "adoption",
            "Compact end-to-end evaluator flow that mirrors recognizable traffic phases.",
            "responsible first-pass adoption evaluation with realistic but maintainable scenarios",
            "./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=adoption\"",
            List.of(
                generousBudgetLowPressure(),
                commuterSpikeMixedPressure(),
                tightBudgetModerateDbPressure()
            )
        );
    }

    public static DashboardScenarioPack agentPack() {
        return new DashboardScenarioPack(
            "agent",
            "Agent-oriented boundary cases: coordination, degraded-cascade, profile comparison, and four-way profile inspection under moderate pressure.",
            "evaluating agent-step orchestration semantics, coordination fallback behavior, and profile tradeoffs (balanced vs continuity vs efficiency vs latency_first)",
            "./gradlew :budgetflow-demo-fintech:runDashboardComparison --args=\"--pack=agent --policies=balanced,continuity,efficiency,latency_first\"",
            List.of(
                agentCoordinationHealthy(),
                agentCoordinationDegradedCascade(),
                moderateBudgetElevatedPressure(),
                agentProfileComparison()
            )
        );
    }

    public static DashboardScenarioPack packNamed(String packName) {
        return switch (packName) {
            case "default" -> defaultPack();
            case "extended" -> extendedPack();
            case "realism" -> realismPack();
            case "policy" -> policyPack();
            case "adoption" -> adoptionPack();
            case "agent" -> agentPack();
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
