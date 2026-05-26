package com.budgetflow.demo.fintech.benchmark;

public final class DashboardWalkthrough {
    private DashboardWalkthrough() {
    }

    public static void main(String[] args) {
        DashboardScenarioPack defaultPack = PressureScenarios.defaultPack();
        DashboardScenarioPack adoptionPack = PressureScenarios.adoptionPack();
        DashboardScenarioPack policyPack = PressureScenarios.policyPack();
        DashboardScenarioPack realismPack = PressureScenarios.realismPack();

        System.out.println("""
            BudgetFlow prototype walkthrough

            Goal: understand the default path first, then compare profiles, then inspect richer scenario evidence.

            1) Run the sample app end to end
               ./gradlew :budgetflow-demo-fintech:bootRun
               curl http://localhost:8080/api/accounts/acc-123/dashboard
               Why: see the preferred starter-facing API and response metadata before reading harness output.
               Observe: diagnostics.degraded, executionSummary, and decisionTrace[*].plannedExecutionLatency.

            2) Run the first comparison pack
               %s
               Why: %s.
               Observe: baseline convergence first, then constrained-budget omission and mixed-constraint degradation.

            3) Run the compact adoption pack
               %s
               Why: %s.
               Observe: control case -> mixed commuter spike -> DB-bound pressure, then compare trace reasons across the sequence.

            4) Compare planner profiles deliberately
               %s
               Why: %s.
               Observe: continuity keeps more response coverage, efficiency protects more headroom, balanced stays conservative.

            5) Export a shareable JSON report
               ./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=realism --json --out=/tmp/budgetflow-realism.json"
               ./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=agent --policies=balanced,continuity,efficiency,latency_first --markdown --out=/tmp/budgetflow-agent-evidence.md"
               Why: %s.
               Observe: scenario narratives, scorecards, comparisonTakeaway, endpoint-intent tradeoff signals, and confidenceSummary before drawing conclusions.

            5b) Save a baseline snapshot, then compare later runs
               ./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--save-baseline=mainline"
               ./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--compare-to=mainline"
               Why: preserve a known-good evidence packet, then review scorecard and profile deltas instead of relying on ad hoc diffing.
               Observe: agent-eval-delta.md for regressions, improvements, expected profile-specific shifts, and taxonomy-guided review scope.

            6) If you want to customize planner behavior, stay incremental
               - Start with budgetflow.planner.profile=balanced|continuity|efficiency
               - Only drop to OptionalTaskModeSelector when the built-in profiles are not enough
               - Keep diagnostics and decision trace as the primary review surface for custom behavior

            Docs:
            - docs/quickstart.md
            - docs/evaluate.md
            - docs/planner-customization.md
            - docs/architecture.md

            Prototype reminder: BudgetFlow is a sophisticated prototype for explainable adaptive execution, not a production-ready platform.
            """.formatted(
            defaultPack.suggestedCommand(),
            defaultPack.bestFor(),
                adoptionPack.suggestedCommand(),
                adoptionPack.bestFor(),
                policyPack.suggestedCommand(),
                policyPack.bestFor(),
                realismPack.bestFor()
            ));
    }
}
