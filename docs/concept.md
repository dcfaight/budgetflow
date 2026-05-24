# BudgetFlow concept (prototype)

BudgetFlow is a Spring Boot-oriented prototype for latency-budget-aware adaptive execution.

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
- realistic but compact fintech sample app + scenario comparison packs

## Prototype boundaries

- not production hardened
- not benchmark-certified
- not a heavyweight optimization or plugin platform

## Evaluation-first path

1. `./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough`
2. `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=default"`
3. `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=adoption"`
4. `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=balanced,continuity,efficiency"`
