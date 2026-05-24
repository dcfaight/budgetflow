# BudgetFlow public prototype milestone (May 2026)

This milestone is a polish-and-confidence pass for public evaluation.

## What this milestone strengthens

- **Public entry clarity:** clearer README framing and a direct evaluation entry path.
- **Planner explainability depth:** decision reasons now expose explicit decision layers (`layer=...`) in addition to policy, pressure, budget fit, and savings markers.
- **Evaluator confidence:** comparison output now includes a concrete evaluator next-step line per scenario and compact reason summaries that are easier to interpret.

## Fast evaluation entry flow

From repository root:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=default"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=adoption"
```

Then (only when profile selection matters):

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=balanced,continuity,efficiency"
```

## How to interpret this milestone responsibly

- Treat outputs as **scenario evidence**, not benchmark certification.
- Keep `balanced` as the default starting point unless scenario evidence suggests a clear continuity/headroom tradeoff.
- Use decision trace + diagnostics summaries as the primary explanation surface before drawing conclusions.

## Scope guardrails

- BudgetFlow remains a **polished prototype**, not a production-ready platform.
- The planner/runtime architecture is intentionally incremental and explainable, not a heavyweight optimization engine.
