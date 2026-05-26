# BudgetFlow baseline management

This guide covers when and how to save, refresh, and interpret baseline snapshots
for the agent evaluation pack.  It is deliberately lightweight — the baseline system
is a diff-and-review aid, not a strict gate.

For evaluation setup see [evaluate.md](evaluate.md).
For profile interpretation see [interpreting-profiles.md](interpreting-profiles.md).

---

## What a baseline is

A baseline snapshot is a lightweight JSON file
(`build/eval-reports/baselines/<name>/agent-eval-snapshot.json`) that records
per-scenario, per-profile evaluation metrics for one known-good commit or branch state.
The accompanying delta commands compare the current run against that snapshot and
emit severity-classified change summaries (`agent-eval-delta.md`, `agent-eval-delta.json`).

Baselines are local to each developer and CI run by default; they are not committed to
the repository.  Treat them as local scratch that aids review, not as certified correctness
proofs.

---

## When to save a baseline

| Situation | Action |
|-----------|--------|
| Starting a feature branch from a stable develop state | Save `mainline` before your first commit |
| After merging a PR that intentionally changes planner behavior | Refresh the `mainline` baseline on develop |
| Before beginning a multi-step refactor | Save a named checkpoint (e.g., `pre-refactor`) |
| After accepting a PR that shifts optional-coverage or fallback patterns deliberately | Refresh `mainline` so future delta reports start from the new intended state |
| Exploratory / draft branch work | No baseline required; rely on `git diff build/eval-reports/` instead |

**Quick save command:**

```bash
./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--save-baseline=mainline"
```

---

## When to compare against a baseline

| Situation | Action |
|-----------|--------|
| Reviewing a PR that touches the planner, profiles, or task specs | Run `--compare-to=mainline` and attach delta to the PR |
| Debugging unexpected degradation changes | Compare against a pre-change checkpoint |
| Verifying a fix did not introduce new regressions | Compare before and after the fix |
| CI evidence pass on a PR | The `eval-report` workflow runs `runAgentEvalReport` automatically; attach the artifact |

**Quick compare command:**

```bash
./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--compare-to=mainline"
```

Delta artifacts appear in `build/eval-reports/`:

| File | Purpose |
|------|---------|
| `agent-eval-delta.md` | Compact reviewer packet — start here |
| `agent-eval-delta.json` | Structured diff with severity fields for tooling |

---

## How to interpret delta severity

Delta entries carry one of four severity labels:

| Severity | Meaning | Action |
|----------|---------|--------|
| `regression-risk` | New degradation, scorecard worsening, or balanced/default omission increases | **Inspect first.** Verify whether the change is intentional or a side-effect. |
| `cautionary` | Optional coverage dropped outside clear profile intent, or scenario drift | **Inspect soon.** Could be intentional (e.g., a threshold change) but warrants a note. |
| `informative` | Plan shape shifted; still within acceptable range | Notable.  Validate the intent matches the PR goal. |
| `expected` | Profile-intent-consistent difference (e.g., `latency_first` omitted more optional work) | Keep as evidence; usually needs no action. |

**Review order:** read `Top changes` first, then `Hotspots`, then per-scenario tables for root-cause detail.

---

## Distinguishing intentional changes from suspicious drift

Use the following heuristics to classify a delta entry before accepting or raising it:

### Likely intentional
- The change matches the stated PR goal (e.g., "relax optional omission threshold").
- The affected scenarios align with the change (budget-only or pressure-only scenarios for a policy tweak).
- The severity is `expected` or `informative`, and the affected profile is `latency_first` or `efficiency`.
- The `whyDiffersFromBalanced` column in the profile comparison table explains the shift in plain terms.

### Likely suspicious drift
- Mandatory tasks (`balance`, `transactions`) appear in `omittedTasks` for any scenario or profile.
- A previously-`expected` scorecard disposition regresses to `cautionary` or `mismatched`.
- `balanced` now omits or degrades tasks that were stable in the baseline without a matching policy change.
- The regression-risk count increased and no planner or profile change was intended.

### Gray area (needs a reviewer note)
- Optional coverage dropped in a profile that previously preserved it, but the PR touched latency hints.
- `informative` deltas cluster around one scenario — could be legitimate scenario boundary drift.

---

## When a baseline should remain stable

A baseline should **not** be refreshed on:
- exploratory or draft PRs that have not been merged
- test-only changes
- documentation-only changes
- CI configuration changes with no planner code impact

Only refresh `mainline` after a change has been reviewed, accepted, and merged into develop.

---

## Expected vs unexpected profile-intent changes

Some delta entries are normal and expected.  Do not over-react to them:

| Profile | Common expected delta | Not a regression because |
|---------|-----------------------|--------------------------|
| `latency_first` | Higher optional omission counts | Designed to protect budget headroom by proactively omitting optional work |
| `efficiency` | Earlier optional omission, lower projected work | Designed to preserve latency headroom over optional fidelity |
| `continuity` | More fallback/approximate usage | Designed to maximise coverage by preferring degraded paths before omission |
| `balanced` | No structural change | If `balanced` shows regression-risk entries, investigate first |

If the `balanced` profile shows new `regression-risk` entries without a clear planner change, treat it as a
potential regression and inspect the decision trace before merging.

---

## Handling intentional profile-intent changes without review noise

When you intentionally change a threshold or profile behavior:

1. Run `--save-baseline=<name>` **before** the change so you have a pre-change checkpoint.
2. Make the change and run `--compare-to=<name>` to capture the delta.
3. Include the delta in the PR description or attach it as a review comment.
4. After the PR is merged and accepted, refresh `mainline`:
   ```bash
   ./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--save-baseline=mainline"
   ```
5. Future delta reports will now start from the new intended state.

This keeps future reviews clean: only genuine regressions will appear as `regression-risk` entries.

---

## Baseline file layout reference

```
build/eval-reports/
  agent-eval-report.json          ← current run full report
  agent-eval-report.md            ← current run Markdown review packet
  agent-eval-delta.json           ← delta vs last --compare-to baseline (if run)
  agent-eval-delta.md             ← compact delta reviewer packet (if run)
  baselines/
    mainline/
      agent-eval-snapshot.json    ← snapshot metrics for this named baseline
      agent-eval-report.json      ← copied full report at baseline time
      agent-eval-report.md        ← copied Markdown report at baseline time
    pre-refactor/
      agent-eval-snapshot.json
      ...
```

Baselines are local scratch — they are in `build/` and not committed to the repository.
