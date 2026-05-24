# BudgetFlow

**Latency-budget-aware adaptive execution for Spring Boot.**

[![Java 17](https://img.shields.io/badge/java-17-437291.svg)](https://adoptium.net/)
[![Spring Boot 3.5](https://img.shields.io/badge/spring--boot-3.5.x-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Status: Prototype](https://img.shields.io/badge/status-prototype-orange.svg)](#current-status)

BudgetFlow is a prototype Java/Spring framework for request-scoped orchestration under latency pressure. It helps APIs stay inside request budgets by classifying work by importance, planning tasks together under a shared budget, and surfacing how execution degraded through decision trace and request-level diagnostics.

> **Prototype status:** ready to share, demo, and explore in realistic Spring Boot integrations; not production-hardened or benchmarked as a platform claim.

## Why this matters in the first minute

- **Request-scoped planning:** related work is planned together instead of timing out independently.
- **Explainable degradation:** decision trace shows *why* work executed, fell back, approximated, or was omitted.
- **Tryable Spring Boot story:** starter wiring, demo app, and comparison harness make the behavior easy to inspect locally.

## Quickstart first (5 minutes)

If you are new to the repository, start here:

1. **Dependency:** add `budgetflow-spring-boot-starter` to your app.
2. **Request budget:** annotate entry points with `@LatencyBudget("250ms")`.
3. **Adaptive request:** build grouped work with `TaskKey` + `AdaptiveRequest`.
4. **Runtime realism (optional):** wire `RuntimePressureSignals` and/or `ExecutionLifecycleListener`.
5. **Result:** read typed values from `AdaptiveRequestResult` plus diagnostics/trace.

See the concise guide: [docs/quickstart.md](docs/quickstart.md)

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
- typed task result access via `TaskKey<T>` and `AdaptiveRequestResult`
- policy-driven execution mode selection
- deterministic mandatory-first planning
- per-task decision trace
- request-level execution diagnostics
- pluggable pressure provider abstraction
- optional runtime pressure adapters (`RuntimeSignalPressureProvider`, `CompositeSystemPressureProvider`)
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
Strategy | Executed | Degraded | Work | Omitted | Fallback | Approx | Why
-------- | -------- | -------- | ---- | ------- | -------- | ------ | ---
budgetflow_adaptive | 4 | true | 430ms/203ms | insights | - | offers | offers=approximate_selected_by_policy[pressure=low:downstream,budget=available], insights=omitted_by_policy[pressure=low:downstream,budget=tight]
naive_parallel | 5 | true | 430ms/445ms | - | - | - | projected_work_exceeds_request_budget_by_15ms
Comparison: adaptive projected work delta=-140ms, executed_task_delta=-1, adaptive_changes=omit=insights
```

### JSON mode showcase snippet

Use `--json` when you need copy/paste-friendly output for demos:

```text
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=realism --json"
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

The latest formatter now also groups output by scenario, adds a narrative line, and emits a compact comparison summary showing projected work savings and adaptive changes side-by-side.

**What the scenarios show:**
- Under a generous budget and low pressure, naive and adaptive produce identical results — no degradation is needed.
- Under a constrained budget and low pressure, the adaptive executor omits optional `insights` while keeping `offers` on the primary path, bringing projected work down from 445 ms to 305 ms. The naive executor still attempts all five tasks over budget.
- Under elevated pressure, the adaptive executor falls back on `rewards`, approximates `offers`, and omits `insights`, projecting about 123 ms of work. The naive executor is unaware of pressure and attempts everything.

## Modules

- `budgetflow-core` — core execution contracts, planning, policy, tracing, diagnostics, pressure abstraction, and ergonomic grouped request helpers.
- `budgetflow-autoconfigure` — Spring Boot auto-configuration and latency budget aspect.
- `budgetflow-spring-boot-starter` — starter dependency.
- `budgetflow-demo-fintech` — demo dashboard application and comparison harness.

## Running the demo

```bash
./gradlew build
./gradlew :budgetflow-demo-fintech:bootRun
curl http://localhost:8080/api/accounts/acc-123/dashboard
```

The demo ships with a small, explicit config baseline in
`budgetflow-demo-fintech/src/main/resources/application.yml`.

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
  runtime-signals:
    enabled: true
    include-default-provider: true
```

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

## Running the comparison harness

BudgetFlow includes a lightweight comparison harness that runs the same dashboard workload with:

- `naive_parallel`
- `budgetflow_adaptive`

Run it with:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison
```

Optional harness arguments keep the tool lightweight while making demo output easier to compare:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=extended"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=realism --json"
```

Available scenario packs:
- `default` — the three core scenarios used in the basic comparison walkthrough
- `extended` — adds generous-budget/elevated-pressure, DB-bound, and downstream-spike scenarios
- `realism` — emphasizes richer pressure narratives while staying deterministic and explainable

### Scenario matrix

| Scenario | Pack(s) | What it demonstrates |
|----------|---------|----------------------|
| `generous_budget_low_pressure` | default, extended | Baseline convergence: adaptive and naive should be effectively equivalent when there is ample budget and low pressure. |
| `constrained_budget_low_pressure` | default, extended, realism | Budget-only stress: adaptive should omit the most expensive optional work first while preserving mandatory-first behavior. |
| `constrained_budget_elevated_pressure` | default, extended, realism | Joint budget + pressure stress: adaptive should degrade more aggressively (including fallback for important tasks). |
| `generous_budget_elevated_pressure` | extended | Pressure-only stress: even with budget headroom, elevated runtime pressure can trigger graceful degradation. |
| `tight_budget_moderate_db_pressure` | extended, realism | Dominant DB pressure path: demonstrates policy behavior when one pressure dimension (DB) is the main bottleneck. |
| `moderate_budget_downstream_spike` | extended, realism | Downstream dependency instability: shows degradation decisions when downstream pressure dominates. |

The optional JSON mode is intentionally simple and stable enough for demo automation or snapshot-style tests; it is not intended as a full benchmarking/reporting platform.

See the [Comparison harness output](#comparison-harness-output) section above for example output and interpretation guidance.

## Current status

BudgetFlow is an **early prototype / design exploration**, not a production-ready framework.

### What it is today
- a working request-aware adaptive execution prototype
- a concrete experiment in graceful degradation under latency budgets
- a framework skeleton with real planning, execution, tracing, diagnostics, pluggable pressure inputs, grouped request ergonomics, and a local comparison harness

### What it is not yet
- production hardened
- benchmarked rigorously
- backed by realistic pressure providers out of the box
- integrated with a full observability platform
- optimized for broad public API ergonomics

## Roadmap

Near-term priorities include:
- improving documentation and examples
- refining public developer ergonomics
- evolving pressure providers toward more realistic runtime signals
- expanding lightweight runtime integration hooks for pressure and observability-style callbacks
- expanding scenario realism and comparison depth
- exploring richer planning and orchestration strategies

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
