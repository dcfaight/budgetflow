# BudgetFlow showcase reference path (concept → run → evidence → review)

Use this as the canonical first runnable story for demos, evaluations, and first-time repository walkthroughs.

For endpoint-specific playbooks, see [reference-journeys.md](reference-journeys.md).  
For full evaluator mechanics, see [evaluate.md](evaluate.md).  
For baseline refresh decisions, see [baseline-management.md](baseline-management.md).

---

## What this proves

In one short flow, you can show:

1. **Endpoint intent** (what kind of service behavior matters)
2. **Work partitioning** (`MANDATORY`/`IMPORTANT`/`OPTIONAL`)
3. **Profile choice** (default `balanced`, then compare if needed)
4. **Expected adaptive behavior** (degrade/fallback/omit shape)
5. **Evidence artifacts** (`agent-eval-report.*`, optional `agent-eval-delta.*`)
6. **Reviewer interpretation** (severity + hotspot triage)

---

## 10–15 minute runnable flow

From repository root:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=adoption"
./gradlew :budgetflow-demo-fintech:runAgentEvalReport
```

Artifacts are written to:

- `budgetflow-demo-fintech/build/eval-reports/agent-eval-report.md`
- `budgetflow-demo-fintech/build/eval-reports/agent-eval-report.json`

Optional baseline/delta loop for PR-style review:

```bash
./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--save-baseline=mainline"
./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--compare-to=mainline"
```

Delta artifacts:

- `budgetflow-demo-fintech/build/eval-reports/agent-eval-delta.md`
- `budgetflow-demo-fintech/build/eval-reports/agent-eval-delta.json`

---

## Endpoint intent + partitioning + profile framing

Use this framing while presenting the run:

| Step | What to say | Where to verify |
|---|---|---|
| Endpoint intent | "This endpoint must preserve correctness, but optional enrichments can degrade under stress." | `docs/reference-journeys.md` |
| Partitioning | "Mandatory tasks stay stable; important tasks should prefer fallback; optional tasks absorb most stress." | `docs/adoption-guide.md` |
| Profile choice | "Start with `balanced`; compare only when endpoint intent demands a stronger continuity/headroom bias." | `docs/planner-customization.md`, `docs/interpreting-profiles.md` |
| Expected behavior | "Under realistic stress, optional work degrades first; mandatory correctness remains intact." | `--pack=adoption` output + evaluator dashboard |

---

## Compact case-study framing (public-facing walkthrough)

Use this short storyline when you need to explain outcomes to external evaluators quickly.

- **Problem shape:** commuter-facing dashboard endpoint sees bursty mixed constraints (budget pressure + downstream pressure).
- **Design choice:** keep account correctness `MANDATORY`, make continuity-heavy reward context `IMPORTANT` with fallback, keep recommendation polish `OPTIONAL`.
- **Expected adaptive behavior:** under spike conditions, optional polish degrades/omits first while core dashboard correctness stays stable.
- **Evidence generated:** `agent-eval-report.md` for full run story; `agent-eval-delta.md` for before/after severity and hotspots.
- **How to judge "good":** mandatory remains preserved, profile-intent behavior is explainable in trace reasons, and no unexplained balanced-profile `regression-risk` deltas appear.

---

## Polished reviewer packet example (baseline → delta severity)

Use this compact interpretation pattern when reviewing `agent-eval-delta.md`:

1. Read **Top changes** first.
2. Prioritize **`regression-risk`**, then **`cautionary`** hotspots.
3. Treat **`expected`** profile-intent differences as evidence, not automatic failures.
4. Verify balanced/default scenarios did not gain unexplained omission/degradation.

Representative reviewer notes pattern:

- **Expected:** `latency_first` optional omissions increased in headroom-sensitive scenarios.
- **Cautionary:** optional coverage dropped in a mixed-constraint scenario where intent was continuity-sensitive.
- **Regression-risk:** balanced profile gained a new omission in a previously stable scenario.

This baseline → delta pattern is the preferred "good evidence packet" shape for PR review.

---

## CI evidence surfacing

On each push/PR to `develop`, the `eval-report` workflow:

1. runs `runAgentEvalReport`
2. uploads the `agent-eval-report` artifact
3. posts a compact PR evidence summary comment
4. writes a workflow summary with artifact and local reproduction pointers

Use CI artifacts as the shared evidence source; use local `--compare-to=mainline` when deeper baseline deltas are needed.
