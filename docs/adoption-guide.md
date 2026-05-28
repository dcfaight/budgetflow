# BudgetFlow adoption guide

This guide helps you map real endpoint and service design goals to BudgetFlow concepts.
It is intended for engineers evaluating whether and how to adopt BudgetFlow for a specific
service or endpoint class.

For framework quickstart see [quickstart.md](quickstart.md).
For fit/framing guidance (when to use and when not to) see [README — Why BudgetFlow](../README.md#why-budgetflow-vs-simpler-alternatives).
For the canonical runnable showcase flow see [showcase-reference-path.md](showcase-reference-path.md).
For profile configuration see [planner-customization.md](planner-customization.md).
For evaluation runbook see [evaluate.md](evaluate.md).
For complete end-to-end playbooks, see [reference-journeys.md](reference-journeys.md).

---

## 1) Work partitioning first: mandatory, important, optional

Before choosing a profile, partition endpoint work by **user-visible value**, **continuity needs**,
**safety/correctness**, and **latency sensitivity**.

| Tier | Meaning | If omitted |
|------|---------|------------|
| **Mandatory** | The response is incorrect or unsafe without this work | Fail the request/work item rather than returning a misleading result |
| **Important** | The response is still useful with a degraded substitute | Prefer fallback/approximate path before omission |
| **Optional** | Enrichment/polish that can be lost without breaking the core response | Approximate or omit under stress |

### Practical classification rubric

For each task, answer these checks in order:

1. **Safety/correctness gate:** does omission create wrong, unsafe, or policy-violating output?
   - Yes → `MANDATORY`
2. **Continuity gate:** can downstream logic continue usefully with a cheaper substitute?
   - Yes, with substitute → `IMPORTANT` + fallback path
3. **User-value gate:** does omission remove enhancement only (not core utility)?
   - Yes → `OPTIONAL`

Use this anti-shallow check before finalizing classifications:

- If a task is marked `MANDATORY`, document what becomes incorrect without it.
- If a task is marked `IMPORTANT`, define a concrete fallback output and latency hint.
- If a task is marked `OPTIONAL`, describe expected degraded UX when omitted.

### Defensible partitioning signals (what to classify where)

| Decision question | Usually classify as | Why |
|---|---|---|
| "If this disappears, is the response wrong/unsafe/non-compliant?" | `MANDATORY` | This is the non-negotiable correctness boundary |
| "If this degrades, does the endpoint still deliver the core answer?" | `IMPORTANT` | Preserve continuity with a cheaper substitute |
| "Is this primarily enrichment/polish/explainer detail?" | `OPTIONAL` | Valuable, but should be pressure-absorbing |

### Misclassification warning signs

- **Too much `MANDATORY`:** constrained scenarios fail hard or lose graceful degradation room.
- **`IMPORTANT` without fallback semantics:** scorecards trend `cautionary` because continuity intent cannot be satisfied.
- **`OPTIONAL` hiding contract logic:** omitted optional work causes user-visible correctness complaints.
- **Repeated "unexpected omission" comments in review:** work likely belongs in `IMPORTANT` with a concrete fallback.

---

## 2) Profile-first decision support (choose intentionally)

Start from endpoint intent, then validate with `adoption` + `policy`/`agent` evidence.

| If your endpoint/workflow is... | Start profile | Why this is a strong first choice | First validation signal to check |
|---|---|---|---|
| Customer-facing and continuity-sensitive | `balanced` (then compare `continuity`) | Preserves conservative defaults while you confirm continuity needs with evidence | Optional work should degrade before omission in mixed-constraint scenarios |
| Latency-sensitive request path where optional enrichment can drop | `latency_first` (or `efficiency`) | Protects headroom by intentionally omitting optional work earlier | Headroom improves while mandatory/important behavior stays stable |
| Background-oriented and budget/throughput sensitive | `continuity` (then compare `efficiency`) | Keeps partial output continuity as pressure rises | Fallback usage rises before omission spikes on important work |

If you cannot explain why a non-`balanced` profile is better for endpoint intent, stay on `balanced` until evaluation evidence says otherwise.

---

## 3) Choose a reference journey (recommended starting point)

Use the canonical end-to-end playbooks in [reference-journeys.md](reference-journeys.md).
They provide concise, checklist-driven flows from endpoint design through evaluation, review,
and baseline refresh decisions.

| Endpoint/service intent | Journey |
|---|---|
| Customer-facing assistant with quality + continuity goals | Journey A — Customer-facing assistant |
| Real-time API where headroom is the top priority | Journey B — Real-time API path |
| Background enrichment where partial outputs still matter | Journey C — Background enrichment workflow |

---

## 4) Compact walkthrough (journey → partitioning → profile → evidence)

Use this lightweight flow for a new endpoint:

1. Pick the closest reference journey (for example, real-time API path).
2. Partition each planned task with the rubric in section 1.
3. Choose a starting profile by endpoint intent (`latency_first` for strict headroom, `balanced` otherwise).
4. Run:
   - `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=adoption"`
   - `./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--compare-to=mainline"` (if baseline exists)
5. Confirm acceptance signals:
   - mandatory tasks never omitted
   - degraded behavior matches chosen profile intent
   - no unexplained `regression-risk` / `cautionary` hotspots in relevant scenarios
6. Record endpoint intent + expected profile behavior in PR notes so reviewers can evaluate deltas against intent.
7. Use the journey's review checklist to focus reviewer packet inspection.

---

## 5) Readiness self-check (before rollout or baseline refresh)

Use this as a lightweight "are we ready?" rubric:

- [ ] **Classification clarity:** every task has a clear `MANDATORY`/`IMPORTANT`/`OPTIONAL` justification
- [ ] **Profile intent clarity:** we can state why this endpoint prefers `balanced`/`continuity`/`efficiency`/`latency_first`
- [ ] **Evidence literacy:** reviewers know to inspect `agent-eval-delta.md` (`Top changes` → `Hotspots`) before broad conclusions
- [ ] **Baseline governance:** we have a documented plan for when to hold vs refresh baseline snapshots
- [ ] **Expectation guardrails:** team understands that profile-intent behavior (for example `latency_first` optional omission) is not an automatic regression

If two or more boxes are unchecked, run another evaluation/review loop before treating current behavior as adoption-ready.

---

## 6) Observability and evaluation wiring in real services

BudgetFlow adoption is strongest when execution behavior is directly inspectable.

### What to surface in traces/diagnostics

- Request-level: budget, remaining budget, degraded flag, profile, pressure summary
- Task-level: execution mode (primary/fallback/approximate/omitted), selected-path latency, decision reason
- Outcome-level: omitted/fallback/approximated task lists and endpoint response impact

### What evidence artifacts to keep

- Scenario comparison outputs (`--json`/`--markdown` with `--out=...`) for reproducible review evidence
- `agent-eval-report.md` / `agent-eval-report.json` for periodic endpoint-behavior snapshots
- `agent-eval-delta.md` when behavior changes are introduced and reviewed

### Service-evolution loop

1. Modify endpoint partitioning/profile behavior.
2. Run relevant scenario packs (`adoption`, `policy`, `agent`, `realism`).
3. Inspect scorecards + decision traces before merging.
4. Attach reviewer packet (`agent-eval-delta.md`) for behavior-changing PRs.
5. Refresh baseline only after accepted intentional changes (see [baseline-management.md](baseline-management.md)).

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

**Treating every omitted optional task as failure.** Optional omissions are often the intended
protective behavior under stress. Review omission trends against endpoint intent and selected profile
before classifying as regression.

**Refreshing baselines too quickly.** If you refresh after every delta without intent review, you can
mask real regressions. Keep the baseline stable until the change is reviewed and explicitly accepted.

**Ignoring reviewer severity in context.** `expected` profile-intent deltas and `regression-risk`
deltas should not be treated the same. Prioritize investigation by severity and by endpoint goal.

---

## 7) Lightweight adoption checklist

- [ ] Identified which tasks are mandatory, important, and optional for my endpoint
- [ ] Provided `fallbackLatencyHint` for important tasks and optional tasks with cheap fallback paths
- [ ] Chose a starting profile (`balanced` unless there is a clear reason for another)
- [ ] Ran `--pack=default` to verify baseline convergence under healthy conditions
- [ ] Ran `--pack=adoption` or `--pack=policy` to validate behavior under realistic constraints
- [ ] Verified mandatory tasks are never omitted in any scenario
- [ ] Checked scorecard assessments — no `mismatched` dispositions in normal scenarios
- [ ] Reviewed decision trace reasons to confirm degradation choices are explainable
