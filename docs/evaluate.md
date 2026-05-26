# BudgetFlow evaluation guide (prototype)

This guide is for responsible, quick evaluation of BudgetFlow as an adaptive execution prototype.

For milestone context, start with [milestone-public-prototype.md](milestone-public-prototype.md).  
For likely next directions, see [status-roadmap.md](status-roadmap.md).

## Scope and maturity

- BudgetFlow is a polished prototype milestone, not a production-ready platform.
- The evaluator is attached to a fintech reference workload so the reusable orchestration model can be reviewed through a realistic scenario surface.
- Treat results as scenario evidence and architecture signal, not benchmark certification.
- Focus on explainability and tradeoff shape, not absolute throughput claims.

## Preferred evaluator entry path

1. [docs/quickstart.md](quickstart.md) for the app-facing integration path
2. `./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough` for guided local flow
3. This guide for scenario packs and interpretation

## 1) Run baseline app flow

From repository root:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough
./gradlew :budgetflow-demo-fintech:bootRun
curl http://localhost:8080/api/accounts/acc-123/dashboard
```

Then open the lightweight evaluator UI:

```text
http://localhost:8080/dashboard/evaluator
```

To run the same flow against a specific synthetic dataset pack:

```bash
./gradlew :budgetflow-demo-fintech:bootRun --args="--budgetflow.demo.dataset=scenarios/overspending-user"
```

Dataset-pack structure and schema notes are documented in
`budgetflow-demo-fintech/src/main/resources/demo-data/README.md`.

Use it to browse scenarios, switch profiles, inspect request-level outcomes, and review planner trace reasoning in a visual prototype surface.
The page now provides first-time “Start here” guidance plus walkthrough-mode progression, narrative phase panels, recommended next comparisons, profile recommendation callouts, compact analytics cards, multi-scenario storyline synthesis, and deeper planner explainability sections.
It also includes an **Agent-step view (compact explainability)** panel that renders the same decision trace in an agent-style step format for quick degrade/omit scanning.

Observe:
- guided progression and profile-chooser recommendations (`default` then `adoption`/`realism`, `balanced` first)
- walkthrough steps (`start`, `compare`, `profile`, `trace`) for intentional evaluator flow
- recommended next comparison links for next scenario/profile moves
- storyline compare sets that summarize what changed across several scenarios
- pack-level overview cards and compact cross-scenario progression tables
- `diagnostics.degraded`
- omitted/fallback/approximated task lists
- budget-fit bars (planned work vs request budget, remaining headroom)
- compact analytics snapshot cards (selected fit, adaptive-vs-naive work delta, degraded count)
- decision trace reasons and `plannedExecutionLatency`
- decision-path summary chips for quick path reading before table inspection
- planner lanes grouped by importance (mandatory / important / optional)
- explicit signal-to-mode summaries (`pressure`/`layer` plus selected-mode counts)
- agent-step formatted trace summary (compact explainability view)
- baseline-vs-selected branch/path summaries for changed decisions
- trace compression section listing changed decisions before full table review
- decision layer + fit/savings markers in reasons (`layer=...`, `fit=...`, `savings=...`)
- profile comparison deltas vs balanced (`Δwork`, `Δexec`, `Δdegrade`) plus compact visual diff chips as directional evidence
- `executionSummary` for a compact human-readable interpretation of the response

If you want a minimal non-UI proof point for agent-style work, run:

```bash
./gradlew :budgetflow-demo-fintech:runAgentTurnDemo
```

This demo shows one agent turn with mandatory/important/optional steps across healthy, constrained-budget, and pressure-spike scenarios.

For boundary-case scenarios (multi-step coordination, degraded-cascade, and profile comparison), run:

```bash
./gradlew :budgetflow-demo-fintech:runAgentCoordinationDemo
```

This demo covers:
- **Coordination**: a plan step fans out to two parallel sub-agent fetches with cached fallbacks, then consolidates and polishes the result.
- **Degraded-cascade**: the same work items under severe joint budget + pressure, causing all important steps to fall back simultaneously.
- **Profile comparison**: the same healthy coordination turn under `balanced` vs `latency_first` to show how profile choice affects optional work.

If this path is not clear yet, stop here and review `docs/quickstart.md` before moving into harness output.

## 2) Run scenario comparison packs

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=default"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=adoption"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=realism --json"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=balanced,continuity,efficiency"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=agent --policies=balanced,latency_first"
```

Observe per scenario:
- pack-level `Best for` / `Suggested run` guidance at the top of the output
- `Pattern` (real-world mapping)
- `Observe` guidance
- `Comparison takeaway`
- adaptive vs naive projected work delta
- profile deltas vs balanced
- `confidenceSummary` as a pack-level orientation aid

Suggested progression:
- `default` first for the control case and the core constrained scenarios
- `adoption` next for a compact, realistic storyline (control -> commuter mixed spike -> dominant DB bottleneck)
- `realism` next for broader scenario evidence, including the clean budget-only path-aware case
- `policy` when you need to choose a planner profile deliberately
- `agent` for agent-step coordination, degraded-cascade boundary cases, and four-way profile comparison; pair with `--policies=balanced,continuity,efficiency,latency_first`
- dashboard UI query params mirror this flow (`pack`, `scenario`, `profile`, `compareProfiles`) for quick visual exploration

## 3) Interpret profile behavior conservatively

- `balanced`: start here for most evaluations.
- `continuity`: favors fallback to preserve response continuity when possible.
- `efficiency`: favors cheaper paths/earlier omission to preserve latency headroom.
- `latency_first`: omits optional work at a lower threshold than `efficiency`; does not explore degraded paths for optional steps. Use for real-time agent turns or when remaining budget headroom is the highest priority.

Choose profile by endpoint goals, not single-scenario wins.

**Common interpretation pitfalls to avoid:**

- Do not treat `latency_first` omissions as failures. Proactive optional omission is the design intent — it protects budget headroom for mandatory/important steps.
- Do not rank profiles by how many tasks they execute. `continuity` may execute more fallback tasks than `balanced`, which is correct behavior for preserving coverage — not evidence that `balanced` is inadequate.
- Do not compare `agent_coordination_degraded_cascade` results to production expectations. That scenario is a boundary case designed to verify deterministic cascade behavior under severe conditions.
- Do not conclude from one scenario that one profile is globally superior. Use the `policy` and `agent` packs to see where profiles meaningfully diverge and why.

For a full interpretation guide, see [docs/interpreting-profiles.md](interpreting-profiles.md).

If none of the built-in profiles fit your endpoint class well, review `docs/planner-customization.md` before introducing a custom selector.

## 4) What good evaluation evidence looks like

- Baseline convergence under generous budget + low pressure.
- Explainable degradation under constrained/mixed stress.
- Stable, deterministic trace semantics across repeated runs.
- Decision-trace reason fields (`fit=...`, `savings=...`) that align with observed degradation choices.
- Profile deltas that match intended tradeoffs for your workload.
- Sample-app and harness observations that tell the same story instead of diverging.
- Tight-budget/path-aware scenarios that show degraded-path latency hints matter even when runtime pressure is calm.
- Profile comparison summaries that show `whyDiffersFromBalanced` aligned with each profile's stated intent.

## 5) What this guide does not claim

- Production hardening or SLO readiness.
- Full observability integration maturity.
- Cross-environment performance guarantees.
