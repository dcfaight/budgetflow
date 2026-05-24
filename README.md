# BudgetFlow

**Latency-budget-aware adaptive execution for Spring Boot.**

[![Java 17](https://img.shields.io/badge/java-17-437291.svg)](https://adoptium.net/)
[![Spring Boot 3.5](https://img.shields.io/badge/spring--boot-3.5.x-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Status: Prototype](https://img.shields.io/badge/status-prototype-orange.svg)](#current-status)
[![Version: 0.x](https://img.shields.io/badge/version-0.x-lightgrey.svg)](#current-status)

BudgetFlow is a polished Spring Boot prototype framework for latency-budget-aware adaptive execution. It explores request-scoped orchestration where mandatory, important, and optional work is planned together, degraded deterministically under budget/runtime pressure, and explained through decision trace plus request-level diagnostics.

> **Prototype status:** polished and evaluator-ready for realistic Spring Boot demos; not production-hardened, benchmark-certified, or API-stable as a platform claim.

> **Milestone narrative (May 2026):** see [docs/milestone-public-prototype.md](docs/milestone-public-prototype.md) for what this milestone represents and why it matters.
>
> **Next-step roadmap:** see [docs/status-roadmap.md](docs/status-roadmap.md) for realistic near-term exploration priorities.

## Start here in order

If you are exploring the repository for the first time, use this sequence:

1. **Framework quickstart:** [docs/quickstart.md](docs/quickstart.md)
2. **Guided local walkthrough:** `./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough`
3. **Evaluation runbook:** [docs/evaluate.md](docs/evaluate.md)
4. **Planner defaults vs customization:** [docs/planner-customization.md](docs/planner-customization.md)
5. **Architecture + extension boundaries:** [docs/architecture.md](docs/architecture.md)

## Why this matters in the first minute

- **Request-scoped planning:** related work is planned together instead of timing out independently.
- **Explainable degradation:** decision trace shows *why* work executed, fell back, approximated, or was omitted.
- **Planner maturity with explicit boundaries:** configurable profiles, path-aware costs, mixed-constraint semantics, and lightweight customization boundaries keep behavior deliberate and inspectable.
- **Tryable Spring Boot story:** starter wiring, demo app, and comparison harness make the behavior easy to inspect locally.

## Quickstart first (5 minutes)

If you are new to the repository, start here:

1. **Dependency:** add `budgetflow-spring-boot-starter` to your app.
2. **Request budget:** annotate entry points with `@LatencyBudget("250ms")`.
3. **Adaptive request:** build grouped work with `TaskKey` + `AdaptiveRequest`.
4. **Default planner path:** stay on `budgetflow.planner.profile=balanced` unless you have a clear reason to compare variants.
5. **Runtime realism (optional):** wire `RuntimePressureSignals` and/or `ExecutionLifecycleListener`.
6. **Result:** read typed values from `AdaptiveRequestResult` plus diagnostics/trace.

See the concise guide: [docs/quickstart.md](docs/quickstart.md)

If you want the shortest guided local tour, run:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough
```

If you are evaluating BudgetFlow for potential adoption, continue with:
[docs/evaluate.md](docs/evaluate.md)

If you are evaluating planner variants or extension boundaries, continue with:
[docs/planner-customization.md](docs/planner-customization.md)

## Package consumption at a glance

BudgetFlow is published as a multi-module prototype with a deliberately simple default entry point:

- **Default app dependency:** `budgetflow-spring-boot-starter`
- **Transitively included at compile/runtime:** `budgetflow-core` (public contracts + grouped request API)
- **Transitively included at compile/runtime:** `budgetflow-autoconfigure` (Spring Boot wiring + `@LatencyBudget`)
- **Optional for local evaluation only:** `budgetflow-demo-fintech`

For most Spring Boot services, depending only on the starter is enough.

### Minimal adoption flow

```text
@LatencyBudget -> AdaptiveRequest.builder(...) -> execute(adaptiveExecutor)
              -> AdaptiveRequestResult (values + diagnostics + decision trace)
```

## Why

Most APIs treat all downstream work as equally important.

Under latency pressure, the usual outcomes are:
- hard timeouts,
- cascading failures,
- or hidden, ad hoc degradation.

In practice, many responses contain a mix of:
- **mandatory** work that must complete,
- **important** work that can fall back,
- **optional** work that can be approximated or omitted.

BudgetFlow explores what happens when that distinction becomes a first-class execution model.

## Core ideas

### Request latency budgets
A request can carry a latency budget that acts as an orchestration constraint instead of just a timeout boundary.

### Task importance
Work is classified by importance:
- `MANDATORY`
- `IMPORTANT`
- `OPTIONAL`

### Execution modes
A task may be planned to:
- execute normally
- execute with fallback
- execute approximately
- be omitted

### Request-scoped planning
Related tasks are planned together under one shared request budget.

### Path-aware planning (what changed)
Path-aware planning means each task can expose cheaper execution paths (fallback/approximate) with explicit latency hints, and the planner uses those path costs while budgeting the rest of the request.

| Planning model | Budget view |
|---|---|
| Pre path-aware | Budgeting assumed primary-path latency only, so degraded paths did not reduce projected planner cost. |
| Path-aware | Budgeting uses selected-path latency (`plannedExecutionLatency`), so fallback/approximate choices can preserve budget for later tasks. |

### Decision trace
The planner records why each task received its selected execution mode.

### Request-level diagnostics
Execution surfaces request-level signals such as:
- total budget
- remaining budget
- degraded status
- omitted tasks
- fallback tasks
- approximated tasks

## Ergonomic grouped request API

BudgetFlow includes a higher-level grouped request API for application code:

- `TaskKey<T>` — typed handle for a named task
- `AdaptiveRequest` — groups multiple adaptive tasks into one request
- `AdaptiveRequestResult` — provides typed access to task results, diagnostics, and decision trace

This reduces manual string-based result lookup while preserving the underlying request-scoped execution model.

### Start here: public API layers

For most application/service code, start with:

- `TaskKey<T>`
- `AdaptiveRequest`
- `AdaptiveRequestResult`

Use lower-level contracts when you need explicit framework/infrastructure control:

- `AdaptiveExecutor`
- `TaskSpec<T>`
- `RequestExecutionResult`

Demo/comparison helpers (for example, the fintech benchmark harness and scenario packs) are sample tooling, not the preferred framework entry path.

### Planner extension boundaries

BudgetFlow keeps planner customization intentionally lightweight:

- **Scoring/signals:** the core planner derives request-budget fit, pressure, and degraded-path signals before policy selection.
- **Policy selection:** `OptionalTaskModeSelector` chooses the execution mode for optional tasks from those computed signals.
- **Orchestration + trace:** `DefaultBudgetPolicyEngine` still owns planning order, budget allocation, diagnostics, and decision trace output.

That keeps extension points explicit without turning the planner into a heavyweight plugin system.

**Recommended path:** start with `balanced`, compare `continuity`/`efficiency` only when a real endpoint tradeoff exists, and reach for a custom `OptionalTaskModeSelector` only when the built-in profiles still do not fit.

### Dashboard example

A fintech dashboard assembles five pieces of data with different importance levels:

```java
static final TaskKey<Balance>            BALANCE      = TaskKey.of("balance");
static final TaskKey<List<Transaction>>  TRANSACTIONS = TaskKey.of("transactions");
static final TaskKey<RewardsSummary>     REWARDS      = TaskKey.of("rewards");
static final TaskKey<List<Offer>>        OFFERS       = TaskKey.of("offers");
static final TaskKey<SpendingInsights>   INSIGHTS     = TaskKey.of("insights");

AdaptiveRequest request = AdaptiveRequest.builder()
    // Must always complete
    .mandatory(BALANCE,      Duration.ofMillis(40),  () -> balanceClient.getBalance(accountId))
    .mandatory(TRANSACTIONS, Duration.ofMillis(65),  () -> transactionClient.getTransactions(accountId))
    // Important — has a cheaper cached fallback
    .importantWithFallback(REWARDS, Duration.ofMillis(90),
        () -> rewardsClient.getRewards(accountId),
        Duration.ofMillis(10),
        () -> rewardsClient.getCachedRewards(accountId))
    // Optional — accepts approximate results or a cached fallback
    .optionalWithFallbackAndApproximate(OFFERS, Duration.ofMillis(110),
        () -> offersClient.getOffers(accountId),
        Duration.ofMillis(12),
        () -> offersClient.getCachedOffers(accountId),
        Duration.ofMillis(8),
        () -> offersClient.getApproximateOffers(accountId))
    // Optional — can be dropped entirely under pressure
    .optional(INSIGHTS, Duration.ofMillis(140), () -> insightsClient.getInsights(accountId))
    .build();

AdaptiveRequestResult result = request.execute(adaptiveExecutor).toCompletableFuture().join();

// Mandatory results — throws if not present
Balance             balance      = result.require(BALANCE);
List<Transaction>   transactions = result.require(TRANSACTIONS);

// Optional results — safe even when omitted or degraded
RewardsSummary      rewards  = result.get(REWARDS).orElseGet(() -> new RewardsSummary(0));
List<Offer>         offers   = result.get(OFFERS).orElseGet(List::of);
SpendingInsights    insights = result.get(INSIGHTS).orElseGet(() -> new SpendingInsights("unavailable"));

// Inspect execution metadata
RequestExecutionDiagnostics diagnostics = result.diagnostics();
List<DecisionTraceEntry>    trace       = result.decisionTrace();
```

The lower-level `TaskSpec<T>` / `RequestExecutionResult` model remains available for advanced or custom usage.

## Current prototype capabilities

BudgetFlow currently includes:

- Gradle Kotlin DSL multi-module project structure
- Spring Boot starter and auto-configuration
- `@LatencyBudget` request budget context
- `TaskSpec<T>` / `TaskResult<T>` execution model
- request-scoped `executeRequest(...)`
- higher-level grouped request composition via `AdaptiveRequest`
- named grouped-request helpers such as `importantWithFallback(...)` and `optionalWithFallbackAndApproximate(...)`
- optional degraded-path latency hints so fallback/approximate execution can participate in planning more realistically
- lightweight optional-task strategy extension via `OptionalTaskModeSelector` (default behavior remains deterministic)
- mixed-constraint optional-path preference signals (`degrade_pref`) so moderate stress can preserve response fidelity while severe stress still prioritizes cheaper paths
- named planner policy profiles (`balanced`/`default`, `continuity`, `efficiency`) with deterministic semantics and balanced default behavior
- Spring Boot planner profile selection via `budgetflow.planner.profile` (legacy alias: `budgetflow.planner.policy-profile`)
- typed task result access via `TaskKey<T>` and `AdaptiveRequestResult`
- policy-driven execution mode selection
- deterministic mandatory-first planning
- per-task decision trace
- request-level execution diagnostics
- pluggable pressure provider abstraction
- optional runtime pressure adapters (`RuntimeSignalPressureProvider`, `CompositeSystemPressureProvider`)
- optional property-only runtime signal inputs (`budgetflow.runtime-signals.*`) for quick starter/demo setup without custom beans
- optional execution lifecycle hooks (`ExecutionLifecycleListener`)
- fintech dashboard demo application
- naive-vs-adaptive comparison harness with scenario packs, grouped reporting, and optional JSON output

## Comparison harness output

Running the harness across the three default scenarios produces output like:

```text
BudgetFlow dashboard comparison
Pack: default — Core scenarios for first-time comparison runs.
Prototype comparison output only; not a rigorous benchmark suite.

Scenario: constrained_budget_low_pressure — Constrained budget / low pressure
Narrative: Budget is the binding constraint while infrastructure remains healthy.
Budget profile: constrained_budget | Pressure profile: low_pressure
Request budget: 430ms | Pressure: exec=0.15 db=0.10 down=0.20
Strategy | Policy | Executed | Degraded | Work | Omitted | Fallback | Approx | Why
-------- | ------ | -------- | -------- | ---- | ------- | -------- | ------ | ---
budgetflow_adaptive | balanced | 4 | true | 430ms/203ms | insights | - | offers | offers=approximate_selected_by_policy[policy=balanced,pressure=low:downstream,active_signals=0,budget=available], insights=omitted_by_policy[policy=balanced,pressure=low:downstream,active_signals=0,budget=tight]
naive_parallel | - | 5 | true | 430ms/445ms | - | - | - | projected_work_exceeds_request_budget_by_15ms
Comparison: adaptive projected work delta=-140ms, executed_task_delta=-1, adaptive_changes=omit=insights
```

### JSON mode showcase snippet

Use `--json` when you need copy/paste-friendly output for demos:

```text
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=realism --json"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=balanced,continuity,efficiency"
```

Example (trimmed):

```json
{
  "scenario": "constrained_budget_elevated_pressure",
  "adaptive": {
    "degraded": true,
    "omitted": ["insights"],
    "fallback": ["rewards"],
    "approximate": ["offers"]
  },
  "comparison": {
    "projectedWorkDeltaMs": -322
  }
}
```

### How to read the output

| Column | Meaning |
|--------|---------|
| **Degraded** | `true` when any task was omitted, fell back, or ran approximately |
| **Omitted** | Tasks skipped entirely — their data is absent from the response |
| **Fallback** | Tasks that ran a cheaper secondary path instead of the primary |
| **Approximated** | Tasks that returned a lower-fidelity result (e.g., cached or estimated) |
| **Work** | Request latency budget vs projected work under the chosen execution modes |

The latest formatter now also groups output by scenario, adds a narrative line, emits compact comparison deltas, includes a `comparisonTakeaway`, and finishes with a richer `confidenceSummary` so profile and scenario tradeoffs are easier to interpret quickly.
Degradation reasons are also compacted with pressure/layer/fit/savings markers so the planner decision path is easier to scan quickly.

The constrained-budget scenarios are the clearest before/after showcase:
- `naive_parallel` still attempts all work using primary-path assumptions.
- `budgetflow_adaptive` applies path-aware planning and can project lower work by selecting fallback/approximate paths before omission where policy allows.

**What the scenarios show:**
- Under a generous budget and low pressure, naive and adaptive produce identical results — no degradation is needed.
- Under a constrained budget and low pressure, the adaptive executor omits optional `insights` while keeping `offers` on the primary path, bringing projected work down from 445 ms to 305 ms. The naive executor still attempts all five tasks over budget.
- Under elevated pressure, the adaptive executor falls back on `rewards`, approximates `offers`, and omits `insights`, projecting about 123 ms of work. The naive executor is unaware of pressure and attempts everything.

**Profile recommendation guidance (prototype):**
- Use `default`/`balanced` first unless you have a clear reason to optimize for a specific tradeoff.
- Prefer `continuity` when preserving optional response coverage matters more than strict headroom.
- Prefer `efficiency` when minimizing projected work and protecting latency headroom is the priority.
- Treat harness guidance as scenario evidence, not a rigorous benchmark claim.

## Modules

- `budgetflow-core` — core execution contracts, planning, policy, tracing, diagnostics, pressure abstraction, and ergonomic grouped request helpers.
- `budgetflow-autoconfigure` — Spring Boot auto-configuration and latency budget aspect (runtime wiring module).
- `budgetflow-spring-boot-starter` — default consumer dependency; exposes core API plus Spring integration annotations/autoconfiguration.
- `budgetflow-demo-fintech` — demo dashboard application and comparison harness (evaluation tooling, not framework runtime dependency).

## Running the demo

```bash
./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough
./gradlew :budgetflow-demo-fintech:bootRun
curl http://localhost:8080/api/accounts/acc-123/dashboard
```

The demo ships with a small, explicit config baseline in
`budgetflow-demo-fintech/src/main/resources/application.yml`.

The walkthrough task prints the preferred “what to run / what to observe / how to interpret it” sequence before you move into deeper harness comparisons.

Example response (trimmed):

```json
{
  "rewards": { "points": 2450 },
  "offers": [
    { "title": "Approximate: personalized offers unavailable" }
  ],
  "insights": {
    "summary": "Insights omitted due to budget constraints."
  },
  "diagnostics": {
    "degraded": true,
    "omittedTaskNames": ["insights"],
    "fallbackTaskNames": [],
    "approximatedTaskNames": ["offers"]
  },
  "decisionTrace": [
    {
      "taskName": "offers",
      "selectedExecutionMode": "EXECUTE_APPROXIMATE",
      "expectedLatency": "PT0.11S",
      "plannedExecutionLatency": "PT0.008S"
    },
    {
      "taskName": "insights",
      "selectedExecutionMode": "OMIT",
      "expectedLatency": "PT0.14S",
      "plannedExecutionLatency": "PT0S"
    }
  ]
}
```

### Optional runtime pressure + lifecycle wiring (Spring Boot)

Enable runtime signal adapter support in configuration:

```yaml
budgetflow:
  planner:
    profile: default
  runtime-signals:
    enabled: true
    include-default-provider: true
```

`budgetflow.planner.profile` accepts `default`/`balanced`, `continuity`, or `efficiency`.
`budgetflow.planner.policy-profile` remains supported as a legacy alias.

Provide a `RuntimePressureSignals` bean (for example from Micrometer or custom gauges):

```java
@Bean
RuntimePressureSignals runtimePressureSignals(
    MeterRegistry meterRegistry
) {
    return new RuntimePressureSignals() {
        @Override public double executorUtilization() { return readExecutorUtilization(meterRegistry); }
        @Override public double dbPressure()          { return readDbPressure(meterRegistry); }
        @Override public double downstreamPressure()  { return readDownstreamPressure(meterRegistry); }
    };
}
```

`ExecutionLifecycleListener` beans are now auto-detected by the starter and attached to the adaptive executor automatically.

For quick demos without wiring a bean, you can also provide static runtime-signal values directly:

```yaml
budgetflow:
  runtime-signals:
    enabled: true
    include-default-provider: false
    executor-utilization: 0.72
    db-pressure: 0.64
    downstream-pressure: 0.48
```

## Running the comparison harness

BudgetFlow includes a lightweight comparison harness that runs the same dashboard workload with:

- `naive_parallel`
- `budgetflow_adaptive`

Run it with:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison
```

For the preferred first-time evaluator flow, start with:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=default"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=adoption"
```

Optional harness arguments keep the tool lightweight while making demo output easier to compare:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=extended"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=adoption"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=realism --json"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=balanced,continuity,efficiency"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=default --json --out=/tmp/budgetflow-report.json"
```

`--policies=` also accepts `default` as an alias for `balanced`.

Available scenario packs:
- `default` — the three core scenarios used in the basic comparison walkthrough; best for first-time repo evaluation
- `extended` — adds tight-budget/path-aware, generous-budget/elevated-pressure, DB-bound, and downstream-spike scenarios; best for broader local exploration
- `adoption` — compact, reusable end-to-end evaluator storyline (control -> commuter mixed spike -> DB-bound bottleneck); best for realistic first-pass adoption confidence checks
- `realism` — emphasizes richer pressure narratives while staying deterministic and explainable, including a clean budget-only path-aware scenario; best for recognizable scenario sharing and JSON export
- `policy` — profile-comparison scenarios that make planner policy differences easier to inspect; best for deliberate planner-profile selection

Available adaptive policy profiles:
- `balanced` — default middle-ground policy (recommended starting point)
- `continuity` — prefers degraded execution paths over omission when possible
- `efficiency` — omits optional work sooner under stress to preserve latency headroom

### Scenario matrix

| Scenario | Pack(s) | What it demonstrates |
|----------|---------|----------------------|
| `generous_budget_low_pressure` | default, extended | Baseline convergence: adaptive and naive should be effectively equivalent when there is ample budget and low pressure. |
| `constrained_budget_low_pressure` | default, extended, realism, policy | Budget-only stress: adaptive should omit the most expensive optional work first while preserving mandatory-first behavior. |
| `constrained_budget_elevated_pressure` | default, extended, realism, policy | Joint budget + pressure stress: adaptive should degrade more aggressively (including fallback for important tasks). |
| `tight_budget_low_pressure` | extended, realism | Path-aware budget rescue: even with calm runtime signals, degraded-path latency hints should let adaptive preserve more useful work than primary-path-only reasoning. |
| `generous_budget_elevated_pressure` | extended | Pressure-only stress: even with budget headroom, elevated runtime pressure can trigger graceful degradation. |
| `tight_budget_moderate_db_pressure` | extended, realism, policy | Dominant DB pressure path: demonstrates policy behavior when one pressure dimension (DB) is the main bottleneck. |
| `moderate_budget_downstream_spike` | extended, realism | Downstream dependency instability: shows degradation decisions when downstream pressure dominates. |
| `moderate_budget_elevated_pressure` | policy | Policy-profile comparison focus: same pressure + budget setup used to contrast balanced, continuity, and efficiency behavior. |
| `commuter_spike_mixed_pressure` | adoption | Recognizable multi-signal traffic burst: validates deterministic mixed-constraint behavior where degraded paths should be preferred before omission when they still fit budget. |

The optional JSON mode is intentionally simple and stable enough for demo automation or snapshot-style tests; it is not intended as a full benchmarking/reporting platform.

Recent formatter output also includes a small confidence summary at the end of text output (and as `confidenceSummary` in JSON) so readers can quickly see how often adaptive planning reduced projected work in the selected scenario pack.

### Evaluation interpretation checklist

Use harness output as scenario evidence, not benchmark certification:

- Start with `default` pack to validate baseline + constrained behavior.
- Use `adoption` pack for a compact control -> realistic stress -> bottleneck story.
- Use `extended` or `realism` to inspect dominant-signal and mixed-constraint cases.
- Use `policy` pack when choosing between `balanced`, `continuity`, and `efficiency`.
- Prefer reading **decision trace reasons + scenario narratives** before drawing conclusions from single deltas.
- Use `comparisonTakeaway` and `confidenceSummary` as orientation aids, not benchmark claims.
- Export reports with `--out=` for review/share, then compare across the same pack/profile inputs only.

See the [Comparison harness output](#comparison-harness-output) section above for example output and interpretation guidance.

For a complete runbook (what to run, what to observe, and how to interpret profile tradeoffs), see:
[docs/evaluate.md](docs/evaluate.md)

## Current status

BudgetFlow is a **polished prototype milestone**, not a production-ready framework.

Versioning is currently pre-1.0 (`0.x`) and should be treated as exploratory framework evolution, not API stability guarantees.

### What it is today
- a working request-aware adaptive execution prototype
- a concrete experiment in graceful degradation under latency budgets
- a coherent framework prototype with request-scoped planning, deterministic degradation semantics, diagnostics/explainability, planner profile controls, and practical evaluation tooling

### What it is not yet
- production hardened
- benchmarked rigorously
- backed by realistic pressure providers out of the box
- integrated with a full observability platform
- optimized for broad public API ergonomics

## Roadmap

Near-term priorities are organized into three themes:
- deeper planner/runtime sophistication with deterministic explainability intact
- stronger adoption/packaging story around starter-first integration and ergonomics
- richer evaluation realism and evidence without overclaiming benchmark rigor

See [docs/status-roadmap.md](docs/status-roadmap.md) for current maturity focus areas and conservative next steps.

## Release notes (prototype)

Recent maturity updates are tracked in:
[CHANGELOG.md](CHANGELOG.md)

## Project motivation

BudgetFlow is exploring a simple question:

> What if latency budgets were treated as a first-class orchestration input instead of an after-the-fact timeout?

## Notes

This repository is intentionally evolving incrementally:
1. scaffold the framework shape
2. make adaptive behavior real
3. move planning to request scope
4. harden planner semantics
5. surface diagnostics and explainability
6. separate pressure from budget via pluggable providers
7. add a naive-vs-adaptive comparison harness
8. improve pressure realism and scenario support
9. add higher-level grouped request ergonomics

That progression is deliberate: the goal is to keep the architecture understandable while the core model matures.
