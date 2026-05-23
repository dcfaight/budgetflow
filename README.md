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

## Current prototype capabilities

BudgetFlow currently includes:

- Gradle Kotlin DSL multi-module project structure
- Spring Boot starter and auto-configuration
- `@LatencyBudget` request budget context
- `TaskSpec<T>` / `TaskResult<T>` execution model
- request-scoped `executeRequest(...)`
- policy-driven execution mode selection
- deterministic mandatory-first planning
- per-task decision trace
- request-level execution diagnostics
- pluggable pressure provider abstraction
- fintech dashboard demo application

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

- `budgetflow-core` — core execution contracts, planning, policy, tracing, diagnostics, and pressure abstraction.
- `budgetflow-autoconfigure` — Spring Boot auto-configuration and latency budget aspect.
- `budgetflow-spring-boot-starter` — starter dependency.
- `budgetflow-demo-fintech` — demo dashboard application.

## Running the demo

```bash
./gradlew build
./gradlew :budgetflow-demo-fintech:bootRun
curl http://localhost:8080/api/accounts/acc-123/dashboard
```

## Running the comparison harness

Use the lightweight comparison harness to see naive parallel fan-out beside BudgetFlow adaptive execution across a few dashboard scenarios:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison
```

The output includes:
- scenario name
- execution strategy
- executed task count
- omitted / fallback / approximated tasks
- degraded status
- budget vs projected work
- simulated pressure snapshot

## Current status

BudgetFlow is an **early prototype / design exploration**, not a production-ready framework.

### What it is today
- a working request-aware adaptive execution prototype
- a concrete experiment in graceful degradation under latency budgets
- a framework skeleton with real planning, execution, tracing, diagnostics, and pluggable pressure inputs

### What it is not yet
- production hardened
- benchmarked rigorously
- backed by realistic pressure providers out of the box
- integrated with a full observability platform
- optimized for broad public API ergonomics

## Roadmap

Near-term priorities include:
- improving documentation and examples
- benchmarking naive vs adaptive execution
- refining public developer ergonomics
- evolving pressure providers toward more realistic runtime signals
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

That progression is deliberate: the goal is to keep the architecture understandable while the core model matures.
