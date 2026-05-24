# BudgetFlow evaluation guide (prototype)

This guide is for responsible, quick evaluation of BudgetFlow as an adaptive execution prototype.

## Scope and maturity

- BudgetFlow is a sophisticated prototype, not a production-ready platform.
- Treat results as scenario evidence and architecture signal, not benchmark certification.
- Focus on explainability and tradeoff shape, not absolute throughput claims.

## 1) Run baseline app flow

From repository root:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough
./gradlew :budgetflow-demo-fintech:bootRun
curl http://localhost:8080/api/accounts/acc-123/dashboard
```

Observe:
- `diagnostics.degraded`
- omitted/fallback/approximated task lists
- decision trace reasons and `plannedExecutionLatency`
- `executionSummary` for a compact human-readable interpretation of the response

## 2) Run scenario comparison packs

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=default"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=realism --json"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=balanced,continuity,efficiency"
```

Observe per scenario:
- pack-level `Best for` / `Suggested run` guidance at the top of the output
- `Pattern` (real-world mapping)
- `Observe` guidance
- adaptive vs naive projected work delta
- profile deltas vs balanced

## 3) Interpret profile behavior conservatively

- `balanced`: start here for most evaluations.
- `continuity`: favors fallback to preserve response continuity when possible.
- `efficiency`: favors cheaper paths/earlier omission to preserve latency headroom.

Choose profile by endpoint goals, not single-scenario wins.

## 4) What good evaluation evidence looks like

- Baseline convergence under generous budget + low pressure.
- Explainable degradation under constrained/mixed stress.
- Stable, deterministic trace semantics across repeated runs.
- Profile deltas that match intended tradeoffs for your workload.
- Sample-app and harness observations that tell the same story instead of diverging.

## 5) What this guide does not claim

- Production hardening or SLO readiness.
- Full observability integration maturity.
- Cross-environment performance guarantees.
