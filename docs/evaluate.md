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
- lightweight scenario scorecards (`mandatory`, `optional alignment`, `fallback alignment`, `intent matched`, `assessment`)
- `confidenceSummary` as a pack-level orientation aid

Suggested progression:
- `default` first for the control case and the core constrained scenarios
- `adoption` next for a compact, realistic storyline (control -> commuter mixed spike -> dominant DB bottleneck)
- `realism` next for broader scenario evidence, including the clean budget-only path-aware case
- `policy` when you need to choose a planner profile deliberately
- `agent` for agent-step coordination, degraded-cascade boundary cases, and four-way profile comparison; pair with `--policies=balanced,continuity,efficiency,latency_first`
- dashboard UI query params mirror this flow (`pack`, `scenario`, `profile`, `compareProfiles`) for quick visual exploration

### Reproducible agent evaluation pack (one-command report)

Run all four agent profiles in one command and produce stable, reviewable artifacts:

```bash
./gradlew :budgetflow-demo-fintech:runAgentEvalReport
```

This generates two files with stable names to `budgetflow-demo-fintech/build/eval-reports/`:

| File | Contents |
|------|----------|
| `agent-eval-report.json` | Structured evidence: scorecards, profile comparisons, confidence summary |
| `agent-eval-report.md` | Compact review packet: scenario narratives, scorecard tables, interpretation section |

Save a reusable baseline snapshot for later PR review:

```bash
./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--save-baseline=mainline"
```

This stores a lightweight baseline snapshot plus copied review artifacts under
`budgetflow-demo-fintech/build/eval-reports/baselines/mainline/`.

Compare the current run against that saved baseline:

```bash
./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--compare-to=mainline"
```

That comparison writes stable delta artifacts beside the current report:

| File | Contents |
|------|----------|
| `agent-eval-delta.json` | Structured baseline-vs-current deltas with severity (`expected`, `informative`, `cautionary`, `regression-risk`) and review-focus hints |
| `agent-eval-delta.md` | Compact reviewer packet with top changes first, hotspot callouts, and severity-oriented review guidance |

Override the output directory:

```bash
./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--out=/tmp/my-eval"
```

**Comparing across runs or PRs:** because file names are stable (no timestamp), you can diff them directly:

```bash
git diff build/eval-reports/agent-eval-report.md
git diff build/eval-reports/agent-eval-report.json
```

**Artifact layout:** the Markdown report includes a `## Review interpretation` section at the end that summarizes:
- Mandatory preservation count across all adaptive runs
- Scorecard disposition breakdown (`expected`, `acceptable`, `cautionary`, `mismatched`)
- Whether profile differentiation was observed
- A reviewer note about `latency_first` optional-coverage intent

The JSON report includes `scorecards`, `profileComparisonSummary`, and `confidenceSummary` fields.
Both formats are intentionally compact — readable in a review thread or design document.

### Reviewer workflow for PRs

Use the evaluation pack as a lightweight before/after review loop:

1. **Create a known-good baseline** on the branch or commit you trust:
   `./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--save-baseline=mainline"`
2. **Run the current branch comparison**:
   `./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--compare-to=mainline"`
3. **Review `agent-eval-delta.md` first** for compact change highlights, then open `agent-eval-report.md` if you need full scenario context.
4. **Use severity to prioritize**:
   - `regression-risk`: inspect first (new degradation, scorecard worsening, or balanced/default omission increases)
   - `cautionary`: inspect soon (possible scenario drift, optional-coverage drops outside clear profile intent)
   - `informative`: notable but often acceptable (plan shape shifted; validate intent)
   - `expected`: profile-intent-consistent differences; keep as evidence but do not overreact
5. **Treat expected profile-specific changes as review items, not automatic failures**:
   - `latency_first`: lower optional coverage can be intentional when headroom improves.
   - `continuity`: more fallback/approximate work can be intentional when omissions stay controlled.
   - `efficiency`: earlier omission or lower projected work can be intentional when latency headroom is the goal.
6. **Use taxonomy to choose what to inspect**:
   - `endpoint=...` tells you whether you are reviewing dashboard behavior or agent coordination behavior
   - `pressure_mode=...` tells you whether the scenario is a control, budget-only, dominant-signal, mixed-constraint, or boundary case
   - `degradation_style=...` tells you whether to expect convergence, pruning, profile tradeoff, or cascade behavior
   - `coordination=...` helps distinguish single-endpoint scenarios from agent coordination flows

### Interpreting delta severity responsibly

- **Increased omission/degradation is concerning** when it appears in `balanced`, causes new degraded states, or downgrades scorecard assessment (`expected/acceptable` → `cautionary/mismatched`).
- **Lower optional coverage can be acceptable** when it is profile-intent aligned (`latency_first`/`efficiency`) and mandatory/important behavior remains preserved.
- **Profile-specific behavior changes are not failures by default**: treat them as intent checks (does this still match endpoint goals?) rather than global regressions.
- **Distinguish expected intent from likely regressions** by combining severity with taxonomy and scorecard rationale:
  - expected + profile-intent difference => usually intentional
  - cautionary/regression-risk + balanced/default drift => investigate as possible regression
- **Review order for PRs**: `Top changes` section first, then `Hotspots`, then scenario tables for root-cause detail.

### Shareable evidence exports

Use CLI output export when you need artifacts outside the evaluator UI:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=agent --policies=balanced,continuity,efficiency,latency_first --json --out=/tmp/budgetflow-agent-evidence.json"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=agent --policies=balanced,continuity,efficiency,latency_first --markdown --out=/tmp/budgetflow-agent-evidence.md"
```

The JSON export includes per-result `scorecards` and rationale fields so reviewers can inspect assessment evidence without opening the dashboard UI.
The Markdown export is intentionally compact for review threads, design notes, and architecture discussions.

From the evaluator UI, use **Evidence export** links in the profile-comparison section for scenario-scoped downloads:

- `/dashboard/evaluator/evidence?...&format=json`
- `/dashboard/evaluator/evidence?...&format=markdown`

## 3) Interpret profile behavior conservatively

- `balanced`: start here for most evaluations.
- `continuity`: favors fallback to preserve response continuity when possible.
- `efficiency`: favors cheaper paths/earlier omission to preserve latency headroom.
- `latency_first`: omits optional work at a lower threshold than `efficiency`; does not explore degraded paths for optional steps. Use for real-time agent turns or when remaining budget headroom is the highest priority.

Choose profile by endpoint goals, not single-scenario wins.

### Endpoint-intent mapping (quick guide)

- **Customer-facing assistant:** start with `balanced`; move to `continuity` when preserving optional context/fidelity is more important than strict headroom.
- **Real-time endpoint:** prefer `latency_first` (or `efficiency`) when predictable headroom and fast response are the primary goals, and optional omissions are acceptable.
- **Background enrichment:** `continuity` is useful when partial enrichment still provides value; `efficiency` is useful when batch latency or queue pressure dominates.

There is no universal profile winner. A “better” outcome is one that matches endpoint intent and expected tradeoffs for that endpoint class.

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
- Scorecard assessments that align with scenario intent (`expected`, `acceptable`, `cautionary`, `mismatched`) and are explainable from trace + diagnostics evidence.

## 5) What this guide does not claim

- Production hardening or SLO readiness.
- Full observability integration maturity.
- Cross-environment performance guarantees.

## 6) Lightweight regression protections

The agent evaluation pack includes lightweight test assertions that protect the current evaluation story from drift.
These live in `AgentEvalPackRegressionTest` and cover:

| Protection | What it asserts |
|------------|-----------------|
| Mandatory preservation | `balance` and `transactions` are never omitted across all agent scenarios and all four profiles |
| Healthy vs cascade distinction | The healthy coordination scenario's degraded plan still fits within budget; the cascade scenario's mandatory work alone exceeds the deliberately-small budget (boundary case) |
| Cascade determinism | `agent_coordination_degraded_cascade` with balanced always produces: `degraded=true`, `insights` omitted, `rewards` fallen back |
| Profile differentiation | `latency_first` omits at least as many optional tasks as `balanced` in the profile comparison scenario |
| No mismatched dispositions | No scorecard across any agent scenario and profile reaches `MISMATCHED` disposition |

These assertions are intentionally lightweight. They protect semantics, not performance numbers.
If a future change causes any of these to fail, inspect the planner trace and scorecard rationale before changing the assertions.
