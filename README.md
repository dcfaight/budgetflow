# BudgetFlow

**Latency-budget-aware adaptive execution for Spring Boot.**

BudgetFlow is a prototype Java/Spring framework for request-scoped orchestration under latency pressure. It helps APIs stay inside request budgets by classifying work by importance, planning tasks together under a shared budget, and surfacing how execution degraded through decision trace and request-level diagnostics.

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

Example:

```java
TaskKey<Balance> BALANCE = TaskKey.of("balance");
TaskKey<List<Transaction>> TRANSACTIONS = TaskKey.of("transactions");

AdaptiveRequest request = AdaptiveRequest.builder()
    .mandatory(BALANCE, Duration.ofMillis(40), () -> balanceClient.getBalance(accountId))
    .mandatory(TRANSACTIONS, Duration.ofMillis(65), () -> transactionClient.getTransactions(accountId))
    .build();

AdaptiveRequestResult result = request.execute(adaptiveExecutor).toCompletableFuture().join();

Balance balance = result.require(BALANCE);
List<Transaction> transactions = result.require(TRANSACTIONS);
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
- typed task result access via `TaskKey<T>` and `AdaptiveRequestResult`
- policy-driven execution mode selection
- deterministic mandatory-first planning
- per-task decision trace
- request-level execution diagnostics
- pluggable pressure provider abstraction
- fintech dashboard demo application
- naive-vs-adaptive comparison harness for local scenario testing

## Example use case

A fintech dashboard endpoint may need to assemble:

### Mandatory
- account balance
- recent transactions

### Important
- rewards summary, with a fallback path

### Optional
- offers, with approximate or cached fallback options
- spending insights, which can be omitted under pressure

BudgetFlow plans these tasks together under one request budget and surfaces what happened to the response.

## Example response signals

The demo can surface:
- `decisionTrace`
- `diagnostics`
- `executionSummary`

These help explain:
- which tasks were omitted
- which tasks used fallback
- which tasks were approximated
- whether the request was degraded
- how much request budget remained

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

## Running the comparison harness

BudgetFlow includes a lightweight comparison harness that runs the same dashboard workload with:

- `naive_parallel`
- `budgetflow_adaptive`

This makes it easy to inspect how adaptive execution changes the request outcome under different latency budgets and pressure conditions.

Run it with:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison
```

Built-in scenarios currently include:
- generous budget / low pressure
- constrained budget / low pressure
- constrained budget / elevated pressure

The harness prints compact comparison output including:
- scenario name
- execution strategy
- executed task count
- omitted tasks
- fallback tasks
- approximated tasks
- degraded status
- request budget vs projected work
- simulated pressure snapshot

### What the comparison is for

This is a **local comparison harness**, not a rigorous performance benchmark suite.

It is intended to help demonstrate:
- how BudgetFlow changes execution under pressure
- how adaptive planning affects response completeness
- how degradation becomes visible through diagnostics

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
