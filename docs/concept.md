# BudgetFlow concept (prototype)

BudgetFlow is a Spring Boot-oriented prototype for reusable adaptive orchestration under latency budgets and runtime pressure. The framework modules are the reusable core; the fintech application in this repository is the reference workload used to demonstrate and evaluate that core.

## Core claim

A request-level latency budget can drive deterministic planning decisions so:
- mandatory work is preserved first,
- important/optional work degrades explicitly when needed,
- and every change stays explainable through diagnostics + decision trace.

## Current scope

- request-scoped grouped planning (`AdaptiveRequest`/`AdaptiveRequestResult`)
- deterministic optional-task planner profiles (`balanced`, `continuity`, `efficiency`, `latency_first`)
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

## What to read next

- New readers: [showcase-reference-path.md](showcase-reference-path.md)
- Fit and framing (when to use, when not to use, what benefits most): [README.md#why-budgetflow-vs-simpler-alternatives](../README.md#why-budgetflow-vs-simpler-alternatives)
- Endpoint adoption path: [adoption-guide.md](adoption-guide.md), [reference-journeys.md](reference-journeys.md)
- Reviewer evidence loop: [evaluate.md](evaluate.md), [baseline-management.md](baseline-management.md)
