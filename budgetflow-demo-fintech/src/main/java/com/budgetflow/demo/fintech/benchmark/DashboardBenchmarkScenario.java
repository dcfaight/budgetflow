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

    public ScenarioTaxonomy taxonomy() {
        return new ScenarioTaxonomy(
            endpointIntent(),
            pressureMode(),
            degradationStyle(),
            coordinationPattern(),
            scenarioType()
        );
    }

    private String endpointIntent() {
        return switch (name) {
            case "agent_coordination_healthy", "agent_coordination_degraded_cascade" -> "agent_coordination";
            case "agent_profile_comparison" -> "agent_profile_review";
            default -> "dashboard_endpoint";
        };
    }

    private String pressureMode() {
        return switch (name) {
            case "generous_budget_low_pressure", "agent_coordination_healthy" -> "control";
            case "constrained_budget_low_pressure", "tight_budget_low_pressure" -> "budget_only";
            case "generous_budget_elevated_pressure" -> "pressure_only";
            case "tight_budget_moderate_db_pressure", "moderate_budget_downstream_spike" -> "dominant_signal";
            case "agent_coordination_degraded_cascade" -> "boundary_joint_stress";
            default -> "mixed_constraint";
        };
    }

    private String degradationStyle() {
        return switch (name) {
            case "generous_budget_low_pressure", "agent_coordination_healthy" -> "baseline_convergence";
            case "constrained_budget_low_pressure" -> "optional_pruning";
            case "tight_budget_low_pressure" -> "path_aware_budget_rescue";
            case "agent_coordination_degraded_cascade" -> "cascade_boundary";
            case "moderate_budget_elevated_pressure", "agent_profile_comparison" -> "profile_tradeoff";
            default -> "fallback_then_prune";
        };
    }

    private String coordinationPattern() {
        return switch (name) {
            case "agent_coordination_healthy", "agent_coordination_degraded_cascade" -> "plan_fanout_consolidate";
            case "agent_profile_comparison" -> "profile_side_by_side";
            default -> "single_endpoint";
        };
    }

    private String scenarioType() {
        return switch (name) {
            case "generous_budget_low_pressure", "agent_coordination_healthy" -> "control";
            case "constrained_budget_low_pressure", "tight_budget_low_pressure" -> "budget_stress";
            case "generous_budget_elevated_pressure", "tight_budget_moderate_db_pressure", "moderate_budget_downstream_spike" ->
                "pressure_stress";
            case "moderate_budget_elevated_pressure", "agent_profile_comparison" -> "profile_review";
            case "agent_coordination_degraded_cascade" -> "boundary_case";
            default -> "mixed_stress";
        };
    }

    public record ScenarioTaxonomy(
        String endpointIntent,
        String pressureMode,
        String degradationStyle,
        String coordinationPattern,
        String scenarioType
    ) {
        public String summary() {
            return "endpoint=" + endpointIntent
                + " | pressure_mode=" + pressureMode
                + " | degradation_style=" + degradationStyle
                + " | coordination=" + coordinationPattern
                + " | scenario_type=" + scenarioType;
        }
    }
}
