# BudgetFlow concept (prototype)

BudgetFlow is a Spring Boot-oriented prototype for reusable adaptive orchestration under latency budgets and runtime pressure. The framework modules are the reusable core; the fintech application in this repository is the reference workload used to demonstrate and evaluate that core.

## Core claim

A request-level latency budget can drive deterministic planning decisions so:
- mandatory work is preserved first,
- important/optional work degrades explicitly when needed,
- and every change stays explainable through diagnostics + decision trace.

## Current scope

- request-scoped grouped planning (`AdaptiveRequest`/`AdaptiveRequestResult`)
- deterministic optional-task policy profiles (`balanced`, `continuity`, `efficiency`)
- mixed-constraint planning with path-aware latency hints (fallback/approximate)
- runtime pressure integration hooks (`RuntimeSignalPressureProvider`, `ExecutionLifecycleListener`)
- transparent reason semantics (`mixed`, `degrade_pref`, `fit`, `savings`, `latency_ratio`)
- realistic but compact fintech reference workload + scenario comparison packs

## Prototype boundaries

- not production hardened
- not benchmark-certified
- not a heavyweight optimization or plugin platform

## Evaluation-first path

1. `./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough`
2. `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=default"`
3. `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=adoption"`
4. `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=balanced,continuity,efficiency"`
