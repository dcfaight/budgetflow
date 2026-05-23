# BudgetFlow architecture

BudgetFlow is a prototype framework for **latency-budget-aware adaptive execution** in Spring Boot applications.

Its purpose is to help a request stay within a target latency budget by:
- planning related work together under a shared budget,
- classifying work by importance,
- selecting execution modes per task,
- and surfacing how the response degraded.

---

## High-level model

A request enters the system with a latency budget.

BudgetFlow then:
1. captures that budget in request context,
2. builds a request-scoped task plan,
3. evaluates the plan with a policy engine,
4. executes tasks according to selected execution modes,
5. records decision trace and diagnostics,
6. returns both business data and execution metadata.

---

## Core concepts

### Execution budget
An `ExecutionBudget` represents the request time budget.

It exposes:
- request start time
- total budget
- elapsed time
- remaining time
- expiration state

A default implementation (`DefaultExecutionBudget`) tracks these values using wall-clock time.

---

### Budget context
`BudgetContext` and `BudgetContextHolder` provide request-scoped access to the active execution budget.

In Spring Boot applications, the budget is established via the `@LatencyBudget` annotation and aspect-based auto-configuration.

---

### Task importance
Each unit of work is modeled with an importance level:

- `MANDATORY`
- `IMPORTANT`
- `OPTIONAL`

This classification drives how aggressively BudgetFlow is allowed to degrade a task.

---

### Task specification
A task is described with `TaskSpec<T>`.

A task spec includes:
- task name
- importance
- expected latency
- primary supplier
- optional fallback supplier
- optional approximate supplier

This is the main input into adaptive request planning.

---

### Execution modes
The policy engine may choose one of these modes for a task:

- `EXECUTE`
- `EXECUTE_WITH_FALLBACK`
- `EXECUTE_APPROXIMATE`
- `OMIT`

These modes are recorded both in execution results and in the decision trace.

---

## Request-scoped planning

### Why request scope matters
BudgetFlow does not evaluate tasks purely in isolation.

Instead, related tasks are planned together under one request budget so the framework can reason about:
- mandatory work first,
- discretionary work afterward,
- and total response quality under time pressure.

### Planner behavior
The default policy engine currently uses a deterministic planning model:

1. classify tasks by importance
2. reserve budget for `MANDATORY` tasks first
3. plan `IMPORTANT` tasks from discretionary remainder
4. plan `OPTIONAL` tasks last
5. preserve original order within each importance class

This gives the planner a stable and explainable behavior model.

---

## Policy evaluation

### Inputs
`PolicyEvaluationInput` currently includes:
- remaining request budget
- request task descriptors
- a `SystemPressureSnapshot`

### Pressure source
Pressure is provided through a pluggable `SystemPressureProvider`.

This separates:
- **request budget** from
- **system/runtime pressure**

A default provider exists, but the abstraction is designed so future implementations can use richer runtime signals.

### Output
The policy engine returns `PolicyDecision`, which contains:
- task directives
- degraded flag
- degradation reasons
- decision trace entries

---

## Execution

`AdaptiveExecutor` is the core execution interface.

The main request-scoped path is:

- `executeRequest(List<TaskSpec<?>>)`

Execution produces a `RequestExecutionResult`, which contains:
- per-task results
- decision trace
- request diagnostics

Each task result captures the selected execution mode and whether the task was omitted.

---

## Decision trace

The decision trace is intended to explain planning behavior at request scope.

Each `DecisionTraceEntry` includes:
- task name
- task importance
- selected execution mode
- reason
- expected latency
- allocated budget
- remaining budget at planning time

This makes the planner behavior inspectable and easier to debug.

---

## Request diagnostics

`RequestExecutionDiagnostics` summarizes the request outcome in a compact form.

It includes:
- total request budget
- remaining request budget
- degraded status
- omitted task names
- fallback task names
- approximated task names

This is the main request-level observability summary.

---

## Spring Boot integration

BudgetFlow’s Spring integration currently includes:

- `@LatencyBudget`
- request budget aspect
- auto-configuration
- starter dependency

This allows applications to establish a request budget declaratively and use the adaptive executor inside normal Spring services.

---

## Demo architecture

The demo application models a fintech dashboard request with these tasks:

### Mandatory
- balance
- transactions

### Important
- rewards

### Optional
- offers
- insights

The request is planned as a group, executed under the active budget, and returned with:
- business payload
- decision trace
- diagnostics
- concise execution summary

---

## Current limitations

BudgetFlow is still an early prototype.

Current limitations include:
- heuristic planner thresholds
- limited runtime pressure realism
- no benchmark harness yet
- no production-grade observability integration
- low-level developer ergonomics in some APIs
- no advanced scheduling/concurrency orchestration yet

---

## Near-term evolution

The most important next steps are likely:
- benchmark naive vs adaptive execution
- improve pressure-provider realism
- refine public developer ergonomics
- add richer architectural documentation and examples

---

## Comparison harness

The fintech demo includes a lightweight comparison harness that runs the same dashboard workload with two strategies:

- `naive_parallel`
- `budgetflow_adaptive`

The harness exists to make the framework’s behavior easier to inspect locally. It is not intended to be a statistically rigorous benchmark system.

Its purpose is to show:
- how request-scoped planning changes task execution
- how constrained budgets affect response composition
- how pressure and task importance influence omission, fallback, and approximation
- how diagnostics and decision trace make degraded execution explainable

The comparison harness reuses the same `DashboardTaskSpecs` model as the main demo service so that comparison scenarios remain aligned with the actual example workload.

---

## Summary

BudgetFlow’s architecture is centered on a simple idea:

> treat latency budgets as a planning input, not just a timeout

Everything else in the system follows from that:
- task classification
- request-scoped planning
- adaptive execution modes
- decision trace
- execution diagnostics
