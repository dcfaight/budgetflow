# BudgetFlow public prototype milestone (May 2026)

This milestone consolidates BudgetFlow from a sequence of maturity-focused increments into a clearer public prototype story.

## Why this milestone matters

The project is no longer just a collection of isolated capabilities. It now has a coherent execution model, readable decision semantics, and a practical evaluation path that outside readers can follow without digging through incremental PR history.

This milestone establishes BudgetFlow as a polished prototype that is credible to discuss, demo, and evaluate conservatively.

## What reached milestone-level maturity

### 1) Adaptive execution model maturity

- request-scoped planning is the default orchestration shape
- deterministic degradation behavior is explicit and inspectable
- grouped request ergonomics make app-facing usage clearer without hiding planner semantics

### 2) Planner sophistication with explicit boundaries

- path-aware planning uses selected-path latency to reason about degraded options more realistically
- mixed-constraint semantics make budget and runtime-pressure interaction clearer
- configurable planner profiles and targeted extension points keep customization intentional rather than open-ended

### 3) Diagnostics, explainability, and evaluation confidence

- decision reasons expose compact markers (`layer=...`, `fit=...`, `savings=...`) that are easier to review quickly
- request diagnostics and trace output make degradation choices explainable at request scope
- walkthrough and comparison packs provide practical scenario evidence for evaluator discussion

### 4) Adoption/readability/usability improvements

- starter-first entry remains the preferred app integration path
- quickstart, evaluation flow, and customization docs now align on the same conservative framing
- scenario narratives and evaluator guidance are easier to share without overclaiming rigor

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
- Use [docs/status-roadmap.md](status-roadmap.md) to separate likely next-step exploration from longer-term ideas.

## Scope guardrails

- BudgetFlow remains a **polished prototype**, not a production-ready platform.
- The planner/runtime architecture is intentionally incremental and explainable, not a heavyweight optimization engine.
