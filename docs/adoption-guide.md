# BudgetFlow adoption guide

This guide helps you map real endpoint and service design goals to BudgetFlow concepts.
It is intended for engineers evaluating whether and how to adopt BudgetFlow for a specific
service or endpoint class.

For framework quickstart see [quickstart.md](quickstart.md).
For profile configuration see [planner-customization.md](planner-customization.md).
For evaluation runbook see [evaluate.md](evaluate.md).

---

## The core decision: what work is mandatory, important, or optional?

Before choosing a profile, partition the work your endpoint does:

| Tier | Meaning | If omitted |
|------|---------|------------|
| **Mandatory** | The response is meaningless without this | Return an error or empty response — do not send a degraded answer |
| **Important** | The response is significantly less useful without this, but a fallback/cached version still has value | Use a fallback path (cached, cheaper, reduced-fidelity) before omitting |
| **Optional** | The response is still useful without this; it enriches or personalizes the core answer | Approximate or omit under pressure; the core answer stands |

**Key design question:** "If this task is skipped, does the user/caller still get a useful response?"

- If no → `MANDATORY`
- If yes with a cached/cheaper substitute → `IMPORTANT` with fallback
- If yes with no substitute needed → `OPTIONAL`

---

## Endpoint-type mapping

### Customer-facing assistant

**Characteristics:** conversational or session-bound, user-visible, moderate latency tolerance,
optional context (user history, personalization) is valuable but not blocking.

**Recommended starting profile:** `balanced`

**When to move to a different profile:**
- Move to `continuity` if preserving optional context (previous conversation state, personalization
  signals) matters more than strict budget headroom.
- Move to `efficiency` if the assistant is embedded in a latency-sensitive path and optional
  enrichment is secondary to consistent response time.

**Partition guidance:**
- Mandatory: core answer generation, session context that would make the reply incoherent without it
- Important (with fallback): recent history retrieval (fall back to cached session summary)
- Optional: personalization signals, speculative tool calls, format polish

**Evaluation pack to use:** `adoption` for a realistic storyline, then `policy` to compare
`balanced` vs `continuity` if continuity matters for your use case.

---

### Real-time request path

**Characteristics:** tight latency SLA, high-frequency, budget headroom is the primary goal,
optional enrichment is a luxury that must not block the mandatory response.

**Recommended starting profile:** `latency_first`

**When to consider `efficiency` instead:**
- `latency_first` never explores degraded paths for optional work (proactive omission).
- `efficiency` omits optional work earlier than `balanced` but still explores fallback paths.
- Use `efficiency` if your optional work has a fast fallback that is cheap enough to not threaten headroom.

**Partition guidance:**
- Mandatory: the core data or decision that the caller requires in every case
- Important (with fallback): secondary enrichment that has a cheap cached path (≤10–15 ms)
- Optional: all remaining enrichment; assume it will be omitted under load

**Evaluation pack to use:** `agent` with `--policies=balanced,latency_first` to see headroom
differences side by side.

---

### Background enrichment

**Characteristics:** asynchronous or batch, latency tolerance is higher, partial enrichment
often still has value, queue pressure is more likely to be the binding constraint than wall-clock latency.

**Recommended starting profile:** `continuity`

**When to consider `efficiency` instead:**
- `continuity` maximizes fallback/approximate usage to preserve partial enrichment.
- If batch throughput or queue drain speed is the primary concern over enrichment fidelity, prefer `efficiency`.

**Partition guidance:**
- Mandatory: the identifier or record that must be enriched (no value emitting an empty enrichment)
- Important (with fallback): primary enrichment signals (fall back to cached or reduced-fidelity version)
- Optional: secondary signals, speculative annotations

**Evaluation pack to use:** `realism` for broad pressure variety, `policy` for `continuity` vs `efficiency` comparison.

---

### Continuity-sensitive workflow

**Characteristics:** multi-step or stateful, partial results from earlier steps feed later steps,
losing continuity mid-workflow is worse than degrading to a slower path.

**Recommended starting profile:** `continuity`

**Design guidance:**
- Use `importantWithFallback(...)` for steps that feed downstream steps — a cached result is
  significantly better than no result when subsequent steps depend on it.
- Be explicit about what "no result" means for downstream steps: if a downstream step cannot
  run without an upstream result, the upstream step should be `MANDATORY` or `IMPORTANT`, not `OPTIONAL`.
- Avoid `OPTIONAL` for steps whose absence silently corrupts downstream state.

**Evaluation pack to use:** `adoption` for the basic storyline, then `agent` for coordination-style
multi-step boundary cases (`agent_coordination_healthy` and `agent_coordination_degraded_cascade`).

---

### Budget-sensitive high-frequency endpoint

**Characteristics:** many short requests per second, per-request budget is very tight,
any optional work must have explicit fallback/approximate paths with cheap latency hints,
global pressure is likely to be the dominant signal.

**Recommended starting profile:** `latency_first` (or `efficiency` if optional fallbacks are cheap enough)

**Design guidance:**
- Provide `fallbackLatencyHint` and `approximateLatencyHint` for all important and optional tasks.
  Without these hints the planner can only use primary-path costs, which means it may omit work
  that a cheaper path could fit.
- Keep mandatory task latency estimates tight and realistic.  Over-estimating mandatory work
  leaves no room for important fallbacks even when time is available.
- Use `optionalWithFallbackAndApproximate(...)` wherever a fast approximate path exists.

**Evaluation pack to use:** `extended` (especially `tight_budget_low_pressure` for path-aware planning)
and `agent` for profile headroom comparison.

---

## Tradeoff summary by profile

| Profile | Optional coverage | Budget headroom | Best fit |
|---------|------------------|-----------------|----------|
| `balanced` | Medium — degrades then omits | Medium | General-purpose default; most new endpoints |
| `continuity` | High — prefers fallback/approx over omission | Lower (accepts more projected work) | Continuity-sensitive; background enrichment where partial > absent |
| `efficiency` | Low-medium — omits earlier | Higher | Strict SLA with some fallback tolerance |
| `latency_first` | Lowest — proactive omission, no optional fallbacks | Highest | Real-time or high-frequency; headroom is the priority |

There is no globally superior profile.  Choose by endpoint goal, not by which profile executes more tasks.

---

## How evaluation artifacts support design and review decisions

### During design
- Use `runAgentEvalReport` (or `runDashboardComparison`) on a prototype task list to validate
  that your importance classifications produce expected behavior before writing production code.
- Run `--pack=policy --policies=balanced,continuity,efficiency` to compare profiles side by side
  under your intended constraint conditions.
- Inspect `evaluationFocus` and `interpretationGuidance` fields in scenario output to understand
  whether a scenario's intent matches your endpoint class.

### During PR review
- A reviewer can run `runAgentEvalReport --args="--compare-to=mainline"` to see whether the PR
  changed planner behavior in expected or unexpected ways.
- The CI `eval-report` workflow generates evidence automatically and posts a summary to the PR.
  See [evaluate.md](evaluate.md#reviewer-workflow-for-prs) for the review loop.

### During adoption validation
- Run the `adoption` pack first (`--pack=adoption`) for a compact realistic storyline.
- Verify that mandatory tasks are never omitted across all scenarios in the output.
- Check that optional degradation follows the expected pattern for your chosen profile.
- If any scorecard shows `cautionary` or `mismatched`, inspect the decision trace before shipping.

---

## Common adoption pitfalls

**Over-classifying as mandatory.** If many tasks are `MANDATORY`, the planner has nothing to
degrade gracefully and will return an error or incomplete response under pressure rather than a
useful degraded one.  Reserve `MANDATORY` for tasks whose absence makes the response meaningless.

**Missing fallback latency hints.** If important or optional tasks lack `fallbackLatencyHint`
and `approximateLatencyHint`, the planner cannot use path-aware budgeting.  The result is more
aggressive omission than necessary.  Always provide latency hints for tasks that have cheaper paths.

**Picking a profile without running the evaluation.** Profile selection by intuition often leads to
surprising behavior under mixed constraints.  Run the `policy` or `agent` pack to see actual
profile differences before committing to one.

**Treating `latency_first` omissions as failures.** Proactive optional omission is the design
intent of `latency_first`.  If your evaluation shows more omissions under `latency_first` than
`balanced`, that is correct behavior.

**Comparing profiles by task-execution count.** A profile that executes fewer tasks is not worse
by default.  `latency_first` sacrifices optional coverage to protect headroom; that is its goal.
Compare profiles by how well they match your endpoint's priority (coverage vs headroom vs balance).

---

## Lightweight adoption checklist

- [ ] Identified which tasks are mandatory, important, and optional for my endpoint
- [ ] Provided `fallbackLatencyHint` for important tasks and optional tasks with cheap fallback paths
- [ ] Chose a starting profile (`balanced` unless there is a clear reason for another)
- [ ] Ran `--pack=default` to verify baseline convergence under healthy conditions
- [ ] Ran `--pack=adoption` or `--pack=policy` to validate behavior under realistic constraints
- [ ] Verified mandatory tasks are never omitted in any scenario
- [ ] Checked scorecard assessments — no `mismatched` dispositions in normal scenarios
- [ ] Reviewed decision trace reasons to confirm degradation choices are explainable
