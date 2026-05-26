# BudgetFlow reference journeys (design → evaluation → review)

Use this guide when you want an opinionated, end-to-end path instead of piecing guidance together across multiple docs.
If you first need one short runnable concept→evidence walkthrough, start with
[showcase-reference-path.md](showcase-reference-path.md).

For core classification rules, see [adoption-guide.md](adoption-guide.md).  
For full evaluator mechanics, see [evaluate.md](evaluate.md).  
For baseline refresh governance, see [baseline-management.md](baseline-management.md).

---

## Journey chooser (start here)

| If your endpoint looks like... | Start with this journey | Default profile |
|---|---|---|
| User-facing assistant response where continuity and quality both matter | [Journey A — Customer-facing assistant](#journey-a--customer-facing-assistant) | `balanced` |
| Tight latency, high-frequency request path where headroom protection is critical | [Journey B — Real-time API path](#journey-b--real-time-api-path) | `latency_first` |
| Async/batch enrichment where partial output is still valuable | [Journey C — Background enrichment workflow](#journey-c--background-enrichment-workflow) | `continuity` |

---

## Journey A — Customer-facing assistant

**Endpoint intent:** keep responses safe and coherent while preserving as much contextual quality as budgets allow.

**Workload partitioning (starting point):**
- **Mandatory:** safety policy checks, core response framing, minimum retrieval for factual grounding
- **Important:** conversation memory retrieval with cached-summary fallback
- **Optional:** personalization enrichments, speculative tool calls, polish layers

**Profile choice and rationale:**
- Start `balanced` to avoid over-optimizing prematurely.
- Compare `continuity` when context fidelity is more important than strict headroom.

**Expected adaptive behavior:**
- Mandatory path remains stable.
- Important memory lookups favor fallback before omission.
- Optional enrichments degrade/omit first under pressure.

### Playbook checklist

#### Design checklist
- [ ] Confirm which assistant failures are unsafe or incorrect if missing (`MANDATORY` only)
- [ ] Define acceptable degraded response quality for memory and context steps
- [ ] Document which enrichments can disappear without breaking usefulness

#### Classification checklist
- [ ] Every `IMPORTANT` step has a concrete fallback payload and latency hint
- [ ] Every `OPTIONAL` step has clear omit/degrade UX expectation
- [ ] Mandatory set is minimal and justified in endpoint notes

#### Evaluation checklist
- [ ] Run `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=adoption"`
- [ ] Run `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=balanced,continuity"`
- [ ] If behavior changed from baseline, run `./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--compare-to=mainline"`

#### Review checklist (what to inspect)
- [ ] `agent-eval-delta.md`: `Top changes` and `Hotspots` for mixed-constraint scenarios
- [ ] Scorecards: no new `cautionary`/`regression-risk` in balanced baseline scenarios
- [ ] Trace reasons: fallback/approx choices should match continuity intent (`layer=...`, `fit=...`, `savings=...`)

#### Baseline refresh checklist
- [ ] Refresh baseline only after reviewer agrees continuity-vs-headroom tradeoff is intentional
- [ ] Keep baseline stable if balanced begins omitting/degrading unexpectedly

---

## Journey B — Real-time API path

**Endpoint intent:** preserve predictable latency headroom for request-critical output under bursty pressure.

**Workload partitioning (starting point):**
- **Mandatory:** response-critical checks/data required for correctness
- **Important:** only when fallback is deterministic and very cheap
- **Optional:** enrichments, diagnostics, secondary annotations

**Profile choice and rationale:**
- Start `latency_first` when optional coverage is expendable.
- Compare `efficiency` only if cheap degraded paths should still be attempted.

**Expected adaptive behavior:**
- Mandatory work remains intact.
- Optional work is proactively omitted earlier than `balanced`.
- Degradation pattern favors headroom protection over optional fidelity.

### Playbook checklist

#### Design checklist
- [ ] Identify response components that must never be delayed by optional work
- [ ] Define acceptable optional-coverage loss under pressure
- [ ] Ensure latency hints reflect realistic primary/fallback/approx path costs

#### Classification checklist
- [ ] Mandatory scope excludes polish-only work
- [ ] Important steps have deterministic fallback semantics
- [ ] Optional steps can be safely omitted without violating API contract

#### Evaluation checklist
- [ ] Run `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=agent --policies=balanced,latency_first"`
- [ ] Run `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=realism"`
- [ ] Run `./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--compare-to=mainline"` for PR evidence

#### Review checklist (what to inspect)
- [ ] `agent-eval-delta.md`: verify omission increases are mostly `expected`/`informative` and tied to `latency_first`
- [ ] Check balanced profile for accidental new omission/degradation
- [ ] Confirm headroom-oriented scenarios improve or remain stable without mandatory regression

#### Baseline refresh checklist
- [ ] Refresh baseline only when reviewer confirms omission changes are intent-aligned for real-time headroom
- [ ] Do not refresh when balanced/default scenarios regress or mandatory behavior drifts

---

## Journey C — Background enrichment workflow

**Endpoint intent:** keep pipeline throughput and valid partial outputs during queue/budget pressure.

**Workload partitioning (starting point):**
- **Mandatory:** identity integrity, commit-safe minimum output
- **Important:** primary enrichment with cached/reduced fallback
- **Optional:** secondary annotations, speculative signal enrichment

**Profile choice and rationale:**
- Start `continuity` when partial enrichment quality matters.
- Compare `efficiency` when queue-drain speed is the dominant objective.

**Expected adaptive behavior:**
- Workflow still emits valid partial records under pressure.
- Fallback usage rises before omission spikes.
- Optional layer absorbs most stress first.

### Playbook checklist

#### Design checklist
- [ ] Define minimum valid output contract for each work item
- [ ] Identify which enrichments are continuity-critical vs optional
- [ ] Set acceptable queue-pressure behavior for partial outputs

#### Classification checklist
- [ ] Important enrichments include reduced/cached fallback plans
- [ ] Optional enrichments are isolated from commit-safe core outputs
- [ ] Mandatory set protects correctness, not completeness polish

#### Evaluation checklist
- [ ] Run `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=realism"`
- [ ] Run `./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=continuity,efficiency"`
- [ ] Run `./gradlew :budgetflow-demo-fintech:runAgentEvalReport --args="--compare-to=mainline"` before review

#### Review checklist (what to inspect)
- [ ] `agent-eval-delta.md`: check continuity scenarios for fallback-before-omission behavior
- [ ] Hotspots: verify no mandatory regressions in mixed-constraint paths
- [ ] Scenario scorecards: continuity/efficiency divergence should match stated queue-throughput goal

#### Baseline refresh checklist
- [ ] Refresh baseline after merge only when reviewers accept continuity-vs-throughput tradeoff
- [ ] Keep baseline stable for unexplained scenario drift or severity escalation

---

## Reviewer packet shortcut (all journeys)

When reviewing a PR that claims intent-aligned adaptive behavior:

1. Open CI/local `agent-eval-delta.md` first (Top changes → Hotspots).
2. Verify severity and profile changes match the selected journey intent.
3. Open `agent-eval-report.md` only for full scenario context where deltas are unclear.
4. Refresh baseline only after explicit intent sign-off.

This keeps reviews focused on meaningful behavior changes instead of raw execution-count noise.
