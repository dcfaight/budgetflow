# BudgetFlow

Latency-budget-aware adaptive execution for Spring Boot.

## Idea

BudgetFlow provides a lightweight baseline for latency-aware orchestration where mandatory, important, and optional tasks can degrade gracefully under time pressure.
The core executor now supports request-scoped planning so related tasks are evaluated together under one budget with per-task decision tracing.

## Modules

- `budgetflow-core` — v1 contracts, policy model, metadata, and default executor/policy implementations.
- `budgetflow-autoconfigure` — Spring Boot auto-configuration plus `@LatencyBudget` AOP support.
- `budgetflow-spring-boot-starter` — starter dependency module.
- `budgetflow-demo-fintech` — demo dashboard application showing baseline adaptive execution flow.

## Quick start

```bash
./gradlew build
./gradlew :budgetflow-demo-fintech:bootRun
```

Then call:

```bash
curl http://localhost:8080/api/accounts/acc-123/dashboard
```
