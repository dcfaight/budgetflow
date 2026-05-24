# BudgetFlow status and roadmap (prototype)

## Current status

BudgetFlow is at a polished prototype milestone:

- request-scoped adaptive execution is real and test-covered
- deterministic degradation semantics and decision trace are first-class
- planner profiles, path-aware reasoning, and runtime-signal integration are available
- walkthrough + scenario packs support practical local evaluation and discussion

BudgetFlow is **not** production hardened, benchmark-certified, or API-stable.

## What this milestone consolidated

- a clearer public story across README, milestone, roadmap, and evaluation entry docs
- stronger explainability framing around decision reasons and request-level diagnostics
- clearer boundaries between default profile usage and deeper planner customization
- a more intentional evaluator journey from walkthrough to scenario packs

## Roadmap themes

### 1) Planner/runtime sophistication

**Likely next-step exploration**
- refine mixed-constraint and path-aware planner behavior while keeping deterministic semantics
- deepen runtime-signal adapters and lifecycle hooks that improve realism without heavy infrastructure coupling
- improve planner reasoning summaries for faster evaluator interpretation

**Longer-term ideas**
- broaden endpoint-shape coverage for profile behavior beyond dashboard-style workloads
- evaluate tighter coupling between runtime signals and adaptive path selection heuristics

### 2) Adoption and packaging story

**Likely next-step exploration**
- continue improving starter-first ergonomics and app-facing API readability
- tighten quickstart/customization guidance around default (`balanced`) usage before advanced tuning
- improve small-scope integration examples that show credible first adoption steps

**Longer-term ideas**
- sharper packaging/release conventions once API evolution is more stable
- broader integration samples for additional Spring service patterns

### 3) Evaluation realism and evidence

**Likely next-step exploration**
- extend scenario evidence with compact, recognizable production-like pressure patterns
- improve comparison summaries that remain conservative and explainable
- strengthen repeatable evaluator workflows for sharing scenario outputs

**Longer-term ideas**
- deeper evidence gathering across varied runtime environments (still as prototype exploration)
- more structured evaluation artifacts if they can stay lightweight and non-benchmark-claiming

## Non-goals (for now)

- large planner architecture rewrite
- heavyweight release management pipeline
- opaque optimization engine
- production-readiness claims
