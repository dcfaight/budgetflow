# BudgetFlow usage guide

This guide shows the recommended application-facing usage style for BudgetFlow's grouped request API.

BudgetFlow still supports lower-level task execution through `TaskSpec<T>` and `RequestExecutionResult`, but most application code should start with:

- `TaskKey<T>`
- `AdaptiveRequest`
- `AdaptiveRequestResult`

---

## When to use the ergonomic API

Use the ergonomic grouped request API when you want to:
- define several related tasks under one request budget
- execute them together
- retrieve results in a typed way
- access diagnostics and decision trace without manual string lookups

This is the preferred style for normal service-layer application code.

---

## Basic example

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

---

## Required vs optional values

### `require(...)`
Use `require(...)` when a task must have produced a value:

```java
Balance balance = result.require(BALANCE);
```

If the task was omitted or has no value, this throws an exception.

### `get(...)`
Use `get(...)` when a task may be omitted or degraded:

```java
Optional<RewardsSummary> rewards = result.get(REWARDS);
```

This returns `Optional.empty()` when the task was omitted or produced no value.

### `taskResult(...)`
Use `taskResult(...)` when you need the full result metadata:

```java
TaskResult<RewardsSummary> rewardsResult = result.taskResult(REWARDS);
ExecutionMode mode = rewardsResult.executionMode();
```

This is useful when you want to inspect:
- execution mode
- omission
- value presence
- reason

---

## Using fallback and approximate execution

For tasks that need fallback or approximate behavior, build the `TaskSpec<T>` explicitly and add it with `.task(...)`:

```java
TaskKey<RewardsSummary> REWARDS = TaskKey.of("rewards");

AdaptiveRequest request = AdaptiveRequest.builder()
    .task(
        REWARDS,
        TaskSpec.important("rewards", Duration.ofMillis(90), () -> rewardsClient.getRewards(accountId))
            .withFallback(() -> rewardsClient.getCachedRewards(accountId))
    )
    .build();
```

This keeps the grouped request API small while still allowing full task customization.

---

## Accessing diagnostics and decision trace

`AdaptiveRequestResult` exposes request-level metadata directly:

```java
RequestExecutionDiagnostics diagnostics = result.diagnostics();
List<DecisionTraceEntry> trace = result.decisionTrace();
```

Use diagnostics to inspect:
- degraded status
- omitted tasks
- fallback tasks
- approximated tasks
- request budget totals and remaining budget

Use decision trace to inspect:
- per-task execution mode
- planning reason
- allocated budget
- remaining budget at planning time

---

## Accessing the raw result

If you need lower-level or legacy access, use:

```java
RequestExecutionResult raw = result.raw();
```

This can be useful when:
- integrating with older code
- debugging
- using APIs that still expect string-based task lookup

---

## When to use lower-level `TaskSpec<T>` directly

Drop down to the lower-level API when you need:
- more explicit control over task configuration
- direct access to raw `RequestExecutionResult`
- infrastructure or tooling code that works with task lists directly
- interoperability with existing lower-level framework code

The grouped request API is additive — it does not replace the core model.

---

## Recommended rule of thumb

For normal application code:
- start with `TaskKey<T>`
- build grouped requests with `AdaptiveRequest`
- consume results through `AdaptiveRequestResult`

For advanced/custom framework usage:
- use `TaskSpec<T>`
- use raw `RequestExecutionResult`
- work directly with `AdaptiveExecutor`

---

## Notes

The grouped request API improves ergonomics, but the underlying execution model is still request-scoped adaptive planning using named tasks under a shared budget.

That means:
- task names still matter
- execution remains explicit
- diagnostics and decision trace remain first-class
