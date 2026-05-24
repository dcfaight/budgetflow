package com.budgetflow.demo.fintech.benchmark;

public final class DashboardWalkthrough {
    private DashboardWalkthrough() {
    }

    public static void main(String[] args) {
        DashboardScenarioPack defaultPack = PressureScenarios.defaultPack();
        DashboardScenarioPack policyPack = PressureScenarios.policyPack();
        DashboardScenarioPack realismPack = PressureScenarios.realismPack();

        System.out.println("""
            BudgetFlow prototype walkthrough

            1) Run the sample app end to end
               ./gradlew :budgetflow-demo-fintech:bootRun
               curl http://localhost:8080/api/accounts/acc-123/dashboard
               Observe: diagnostics.degraded, executionSummary, and decisionTrace[*].plannedExecutionLatency.

            2) Run the first comparison pack
               %s
               Why: %s.
               Observe: baseline convergence first, then constrained-budget omission and mixed-constraint degradation.

            3) Compare planner profiles deliberately
               %s
               Why: %s.
               Observe: continuity keeps more response coverage, efficiency protects more headroom, balanced stays conservative.

            4) Export a shareable JSON report
               %s --out=/tmp/budgetflow-realism.json
               Why: %s.
               Observe: scenario narratives, real-world pattern mapping, and confidenceSummary before drawing conclusions.

            Docs:
            - docs/quickstart.md
            - docs/evaluate.md
            - docs/architecture.md

            Prototype reminder: BudgetFlow is a sophisticated prototype for explainable adaptive execution, not a production-ready platform.
            """.formatted(
            defaultPack.suggestedCommand(),
            defaultPack.bestFor(),
            policyPack.suggestedCommand(),
            policyPack.bestFor(),
            realismPack.suggestedCommand().replace(" --json", " --json"),
            realismPack.bestFor()
        ));
    }
}
