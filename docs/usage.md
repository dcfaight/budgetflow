# BudgetFlow usage guide

This guide shows the recommended application-facing usage style for BudgetFlow's grouped request API.

BudgetFlow still supports lower-level task execution through `TaskSpec<T>` and `RequestExecutionResult`, but most application code should start with:

- `TaskKey<T>`
- `AdaptiveRequest`
- `AdaptiveRequestResult`

## Public API layering at a glance

- **Preferred application-facing path:** `TaskKey<T>`, `AdaptiveRequest`, `AdaptiveRequestResult`
- **Core/advanced contracts:** `AdaptiveExecutor`, `TaskSpec<T>`, `RequestExecutionResult`, `TaskResult<T>`
- **Demo/comparison tooling:** fintech benchmark harness/scenarios/reporting in `budgetflow-demo-fintech`

---

## When to use the ergonomic API

Use the ergonomic grouped request API when you want to:
- define several related tasks under one request budget
- execute them together
- retrieve results in a typed way
- access diagnostics and decision trace without manual string lookups

This is the preferred style for normal service-layer application code.

---

## Realistic dashboard example

This example shows a fintech dashboard service that groups five tasks — two mandatory, one important with fallback, one optional with fallback and approximate modes, and one optional that can be omitted entirely.

```java
// Define typed keys — one per task
static final TaskKey<Balance>           BALANCE      = TaskKey.of("balance");
static final TaskKey<List<Transaction>> TRANSACTIONS = TaskKey.of("transactions");
static final TaskKey<RewardsSummary>    REWARDS      = TaskKey.of("rewards");
static final TaskKey<List<Offer>>       OFFERS       = TaskKey.of("offers");
static final TaskKey<SpendingInsights>  INSIGHTS     = TaskKey.of("insights");

// Build the grouped request
AdaptiveRequest request = AdaptiveRequest.builder()
    // Mandatory: must always complete
    .mandatory(BALANCE,      Duration.ofMillis(40),  () -> balanceClient.getBalance(accountId))
    .mandatory(TRANSACTIONS, Duration.ofMillis(65),  () -> transactionClient.getTransactions(accountId))
    // Important: has a cached fallback for when the primary path is too slow
    .importantWithFallback(REWARDS,
        Duration.ofMillis(90),
        () -> rewardsClient.getRewards(accountId),
        () -> rewardsClient.getCachedRewards(accountId))
    // Optional: accepts approximate results or a cheap cached fallback
    .optionalWithFallbackAndApproximate(OFFERS,
        Duration.ofMillis(110),
        () -> offersClient.getOffers(accountId),
        () -> offersClient.getCachedOffers(accountId),
        () -> offersClient.getApproximateOffers(accountId))
    // Optional: can be dropped entirely under budget or pressure constraints
    .optional(INSIGHTS, Duration.ofMillis(140), () -> insightsClient.getInsights(accountId))
    .build();

// Execute and collect results
AdaptiveRequestResult result = request.execute(adaptiveExecutor).toCompletableFuture().join();
```

---

## Retrieving results

### `require(...)`
Use `require(...)` when a task must have produced a value:

```java
Balance             balance      = result.require(BALANCE);
List<Transaction>   transactions = result.require(TRANSACTIONS);
```

If the task was omitted or has no value, this throws an exception. Use this for mandatory tasks.

### `get(...)`
Use `get(...)` when a task may be omitted or degraded:

```java
RewardsSummary   rewards  = result.get(REWARDS).orElseGet(() -> new RewardsSummary(0));
List<Offer>      offers   = result.get(OFFERS).orElseGet(List::of);
SpendingInsights insights = result.get(INSIGHTS).orElseGet(() -> new SpendingInsights("unavailable"));
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

For common grouped-request patterns, use the named builder helpers:

```java
.importantWithFallback(REWARDS,
    Duration.ofMillis(90),
    () -> rewardsClient.getRewards(accountId),
    Duration.ofMillis(10),
    () -> rewardsClient.getCachedRewards(accountId))

.optionalWithFallbackAndApproximate(OFFERS,
    Duration.ofMillis(110),
    () -> offersClient.getOffers(accountId),
    Duration.ofMillis(12),
    () -> offersClient.getCachedOffers(accountId),
    Duration.ofMillis(8),
    () -> offersClient.getApproximateOffers(accountId))
```

If you want to stay closer to raw `TaskSpec<T>` construction, keyed factory methods are also available and still keep task names explicit:

```java
// Important task with a fallback to cached data
.task(REWARDS,
    TaskSpec.important(REWARDS, Duration.ofMillis(90), () -> rewardsClient.getRewards(accountId))
        .withFallback(() -> rewardsClient.getCachedRewards(accountId), Duration.ofMillis(10)))

// Optional task with both a fallback and a cheaper approximate path
.task(OFFERS,
    TaskSpec.optional(OFFERS, Duration.ofMillis(110), () -> offersClient.getOffers(accountId))
        .withFallback(() -> offersClient.getCachedOffers(accountId), Duration.ofMillis(12))
        .withApproximate(() -> offersClient.getApproximateOffers(accountId), Duration.ofMillis(8)))
```

The planner will choose between primary, fallback, approximate, or omit based on the remaining budget and system pressure.

When available, degraded-path latency hints are folded into planning and decision trace so the framework can reserve less budget for a 10 ms cached fallback than for a 90 ms primary call.

For optional tasks, the default policy now prefers a degraded execution path (approximate first, then fallback when available) before full omission in many stressed conditions, and reserves omission for more severe pressure/budget situations.

The helper methods reduce boilerplate, but task names, importance, and execution behavior remain explicit and inspectable through diagnostics and decision trace.

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
- planned execution latency for the selected path
- allocated budget
- remaining budget at planning time

---

## Accessing the raw result

If you need lower-level or legacy access, use:

```java
RequestExecutionResult raw = result.requestExecutionResult();
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

---

## Runtime integration hooks (optional)

BudgetFlow remains a prototype, but now includes lightweight integration points for more realistic runtime usage:

- `RuntimeSignalPressureProvider` for plugging in live pressure signal suppliers (executor, DB, downstream).
- `CompositeSystemPressureProvider` for combining multiple pressure sources conservatively (max per dimension).
- `ExecutionLifecycleListener` for optional lifecycle callbacks before/after policy evaluation and after request execution.
- named planner policy profiles (`balanced`, `continuity`, `efficiency`) for optional-task policy variation while keeping behavior deterministic.
- `OptionalTaskModeSelector` for custom optional-task policy variation when profiles are not enough.

These hooks are intentionally lightweight and optional:
- no hard dependency on any observability vendor
- no mandatory telemetry platform setup
- default planner semantics remain unchanged unless you explicitly choose a different profile or provide a custom selector

Built-in profiles:
- `balanced` (default): middle-ground behavior for most teams
- `continuity`: favors degraded optional execution paths before omission
- `efficiency`: omits optional work earlier under stress to protect latency headroom

Example policy variation wiring:

```java
OptionalTaskModeSelector preferFallback = (task, context) ->
    task.fallbackSupported() ? ExecutionMode.EXECUTE_WITH_FALLBACK : ExecutionMode.EXECUTE;

AdaptiveExecutor executor = new DefaultAdaptiveExecutor(
    new DefaultBudgetPolicyEngine(preferFallback)
);
```

For Spring Boot starter usage, enable runtime-signal adapter composition with:

```yaml
budgetflow:
  planner:
    policy-profile: balanced
  runtime-signals:
    enabled: true
    include-default-provider: true
```

Then provide one `RuntimePressureSignals` bean to bridge your app/runtime metrics into BudgetFlow.

`budgetflow.planner.policy-profile` accepts `balanced`, `continuity`, or `efficiency`.

When `ExecutionLifecycleListener` beans are present, starter auto-configuration now wires them into the default `AdaptiveExecutor`.

### Spring Boot configuration example

```yaml
budgetflow:
  enabled: true
  default-budget: 250ms
  planner:
    policy-profile: continuity
  runtime-signals:
    enabled: true
    include-default-provider: true
```

```java
@Bean
RuntimePressureSignals runtimePressureSignals(MeterRegistry meterRegistry) {
    return new RuntimePressureSignals() {
        @Override public double executorUtilization() { return readExecutorUtilization(meterRegistry); }
        @Override public double dbPressure()          { return readDbPressure(meterRegistry); }
        @Override public double downstreamPressure()  { return readDownstreamPressure(meterRegistry); }
    };
}
```

```java
@Bean
ExecutionLifecycleListener loggingLifecycleListener() {
    return new ExecutionLifecycleListener() {
        @Override
        public void afterRequestExecution(RequestExecutionResult result) {
            LOGGER.info("budgetflow diagnostics={}", result.diagnostics());
        }
    };
}
```

### Typical wiring pattern

```mermaid
flowchart LR
    A[Request budget context] --> B[DefaultSystemPressureProvider]
    C[Runtime metrics/signals] --> D[RuntimeSignalPressureProvider]
    B --> E[CompositeSystemPressureProvider]
    D --> E
    E --> F[DefaultAdaptiveExecutor]
    F --> G[ExecutionLifecycleListener (optional)]
```

Use this only when you need richer runtime realism; simple demos can continue using fixed/default providers.
