# BudgetFlow status and roadmap (prototype)

## Current status

BudgetFlow is in advanced prototype maturity:

- request-scoped adaptive planning is implemented and test-covered
- deterministic degradation and decision trace are first-class
- planner profiles and runtime-signal integrations are available
- demo + scenario harness support practical local evaluation

BudgetFlow is **not** production hardened, benchmark-certified, or API-stable.

## What this maturity pass strengthens

- release-facing framing and evaluation guidance
- clearer planner separation between signal analysis, policy selection, and trace/orchestration output
- scenario mapping to recognizable real-world pressure patterns
- default-vs-advanced planner customization guidance
- a guided walkthrough path for first-time local exploration

## Near-term roadmap (incremental)

1. tighten API ergonomics while preserving starter-first adoption path
2. expand scenario packs with compact, realistic endpoint patterns and clearer comparison summaries
3. deepen runtime-signal adapters and observability-friendly hooks
4. continue planner refinement with deterministic, explainable semantics
5. improve docs coherence across quickstart, usage, and evaluation flow

## Non-goals (for now)

- large planner architecture rewrite
- heavyweight release management pipeline
- opaque optimization engine
- production-readiness claims
