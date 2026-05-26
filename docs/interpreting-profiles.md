# Interpreting profile differences responsibly

This guide helps you read BudgetFlow profile comparison output without drawing misleading conclusions.
It applies to the CLI harness (`--pack=agent --policies=...`), the evaluator dashboard profile comparison table,
and any exported JSON from `DashboardBenchmarkFormatter`.

For milestone context see [milestone-public-prototype.md](milestone-public-prototype.md).
For profile configuration see [planner-customization.md](planner-customization.md).

---

## The four profiles at a glance

| Profile | Optional behavior | Degraded-path usage | Best for |
|---|---|---|---|
| `balanced` | Degrade then omit under stress | Full degraded-path exploration | Conservative default for most services |
| `continuity` | Strongly prefers degraded paths before omission | Maximises fallback/approx use | Preserving response coverage when partial is better than absent |
| `efficiency` | Omits earlier to protect headroom | Lean degraded-path use | Endpoints with strict latency SLAs that need headroom buffer |
| `latency_first` | Omits at a low ratio threshold; no degraded paths for optional work | None for optional steps | Real-time or high-frequency agent turns where headroom is the priority |

---

## Rule 1: Lower optional coverage is not always worse

When `latency_first` or `efficiency` omits optional work that `balanced` or `continuity` degrades to a fallback, that is **the intended outcome, not a failure**.

- `latency_first` sacrifices optional coverage to preserve remaining budget headroom for mandatory/important steps.
- `efficiency` omits optional work earlier than `balanced` to keep projected work lean.
- `continuity` accepts extra fallback/approx work to preserve as much optional coverage as possible.

A row with `omittedTasks=["format-polished-response"]` under `latency_first` is correct and expected behavior.
A row with the same task in `fallbackTasks` under `continuity` is also correct — just a different tradeoff.

**Avoid:** concluding that a profile is "worse" because it executes fewer tasks or has more omissions.

---

## Rule 2: Continuity and latency_first optimize different outcomes

These two profiles are near-opposites:

- **continuity** goal: maximize response coverage, even if it means more projected work, by preferring fallback or approximate paths before omitting anything.
- **latency_first** goal: maximize remaining budget headroom by proactively omitting optional work, even under low pressure, so mandatory steps always have room to run.

When you compare them directly, the differences in executed/fallback/omitted counts reflect this intent — not one profile being objectively better.

**Practical guidance:**

- Use `continuity` when a partial optional response is more useful than no optional response.
- Use `latency_first` when endpoint SLAs or agent turn budgets are strict and optional enrichment is a luxury.
- Neither profile is correct in absolute terms: correctness depends on your endpoint's goal.

---

## Rule 3: Compare profiles by endpoint goals, not by one scenario in isolation

A single scenario cannot prove that one profile is superior. Profiles are designed for different endpoint classes, and a profile that looks "aggressive" in a generous-budget scenario may be exactly right for a tight real-time agent turn.

**How to compare responsibly:**

1. Run the same scenario under multiple profiles: `--pack=agent --policies=balanced,continuity,efficiency,latency_first`.
2. Inspect the "Profile comparison summary" table for side-by-side counts and headroom.
3. Check `whyDiffersFromBalanced` in JSON output for a compact explanation of each profile's decision.
3. Check `scorecards[*]` in JSON/Markdown evidence for mandatory preservation, optional/fallback intent alignment, and final assessment classification.
4. Confirm the profile's behavior matches your endpoint's priority (coverage vs headroom vs balance).
5. Do not pick the profile with the most executed tasks or least degradation by default.

**Pack guidance:**

- `default` and `adoption`: use `balanced` to establish the baseline behavior.
- `policy`: directly compares `balanced`, `continuity`, and `efficiency` under scenarios where they diverge.
- `agent`: adds `latency_first` and shows coordination/cascade boundary cases.
- Use `--pack=agent --policies=balanced,continuity,efficiency,latency_first` for a full four-way comparison.

---

## Rule 4: How to read agent-step traces and summary differences

### Reading the profile comparison summary table

Each row is one adaptive profile for the same scenario. Columns:

- **exec**: total tasks executed (non-omitted). Higher does not always mean better.
- **fb (fallback)**: tasks that took a cheaper but still useful degraded path.
- **approx**: tasks that returned reduced-fidelity output.
- **omit**: tasks skipped entirely. For `latency_first`, this is expected at lower thresholds.
- **degraded?**: whether the planner judged the response degraded at the scenario level.
- **headroom**: request budget minus projected work. Higher headroom is the latency_first design goal.
- **why it differs from balanced**: a concise explanation derived from the actual execution metadata.

### Reading scenario scorecards

Scorecards are lightweight intent checks derived from scenario metadata plus observed diagnostics/trace behavior:

- **mandatory preserved** — mandatory work stayed intact.
- **optional aligned** — optional degrade/omit behavior matched the scenario goal.
- **fallback aligned** — fallback/degraded-path usage matched expectations.
- **intent matched** — all checks aligned for that result.
- **assessment** — `expected`, `acceptable`, `cautionary`, or `mismatched`.

Use scorecards as interpretation structure, not as a universal numeric score.

### Reading the decision trace reasons

Trace reasons follow the format:
```
<mode>[pressure=<level>,layer=<layer>,fit=<fit>,savings=<Nms>]
```

Fields to focus on:
- `layer=optional_*`: decisions that affect optional work — compare across profiles here first.
- `pressure=mixed` or `pressure=high`: indicates both budget and runtime signals contributed.
- `savings=Nms`: how much projected work was saved by taking the degraded path.
- `fit=tight` or `fit=over`: whether the task's primary path fit the remaining budget.

### When to inspect changed decisions first

Start from the "Trace compression: changed decisions first" section in the evaluator dashboard or
the `compactChangedDecisions` output in the CLI. Changed decisions (non-EXECUTE modes) are the
meaningful differences between profiles. Unchanged (EXECUTE) rows confirm stable mandatory/important
behavior and usually need no further review.

---

## Rule 5: Use scenario packs as storylines, not isolated data points

| Pack | Purpose |
|---|---|
| `default` | Control case + baseline constrained scenarios |
| `adoption` | Compact realistic storyline: control → spike → dominant bottleneck |
| `realism` | Broader pressure variety with recognizable patterns |
| `policy` | Designed specifically for profile tradeoff inspection |
| `agent` | Agent-step coordination, degraded-cascade boundary case, four-way profile comparison |

A result from `agent_coordination_degraded_cascade` is a **boundary case** — not a production-typical scenario.
A cascade of fallbacks under severe joint budget + pressure is the expected and correct outcome there.
Interpret it as proof that the planner is deterministic and traceable under extreme conditions, not as evidence of systemic failure.

---

## Endpoint-intent mapping quick reference

Use profile selection criteria that match the endpoint class:

- **Customer-facing assistant**
  - Usually start `balanced`.
  - Move to `continuity` when optional context continuity matters.
- **Real-time endpoint**
  - Prefer `latency_first` (or `efficiency`) when strict headroom and consistent response time are primary.
- **Background enrichment**
  - Prefer `continuity` if partial enrichment remains useful.
  - Prefer `efficiency` if throughput/queue headroom dominates.

This mapping should guide interpretation: a profile that omits more optional work can still be the *correct* result for real-time endpoints.

---

## Summary checklist for a responsible profile comparison

- [ ] Did you run the same scenario under all profiles you want to compare?
- [ ] Did you check the "why it differs from balanced" column or field before drawing conclusions?
- [ ] Did you verify that omissions under `latency_first`/`efficiency` are intentional, not accidental?
- [ ] Did you check headroom alongside coverage, not just coverage alone?
- [ ] Did you read the `evaluationFocus` and `interpretationGuidance` fields for the scenario?
- [ ] Did you treat the result as scenario evidence rather than benchmark proof?

If all of these are yes, you are ready to make a profile selection decision with appropriate confidence for a prototype evaluation.
