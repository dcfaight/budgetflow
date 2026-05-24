# BudgetFlow quickstart (Spring Boot)

BudgetFlow is an early prototype; this quickstart is optimized for trying the core flow quickly, not production hardening.

If you want the repository’s preferred guided local tour instead of just the API path, run:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough
```

## 1) Add the dependency

In this repository’s multi-module setup, depend on the starter module:

```kotlin
dependencies {
    implementation(project(":budgetflow-spring-boot-starter"))
}
```

Consumption boundary:
- `budgetflow-spring-boot-starter` is the default application dependency
- it exposes core API types (`TaskKey`, `AdaptiveRequest`, `AdaptiveRequestResult`) for compile-time usage
- it also carries Spring integration annotations/autoconfiguration (including `@LatencyBudget`) so app code does not need to depend on `budgetflow-autoconfigure` directly

## 2) Establish a request budget

Annotate your endpoint/service entry point with `@LatencyBudget`:

```java
@GetMapping("/dashboard/{id}")
@LatencyBudget("250ms")
public DashboardResponse dashboard(@PathVariable String id) {
    return dashboardService.getDashboard(id);
}
```

## 3) Build an adaptive grouped request

Use the preferred public API path (`TaskKey`, `AdaptiveRequest`, `AdaptiveRequestResult`):

```java
static final TaskKey<Balance> BALANCE = TaskKey.of("balance");
static final TaskKey<List<Offer>> OFFERS = TaskKey.of("offers");

AdaptiveRequest request = AdaptiveRequest.builder()
    .mandatory(BALANCE, Duration.ofMillis(40), () -> balanceClient.getBalance(accountId))
    .optionalWithFallbackAndApproximate(
        OFFERS,
        Duration.ofMillis(110),
        () -> offersClient.getOffers(accountId),
        Duration.ofMillis(12),
        () -> offersClient.getCachedOffers(accountId),
        Duration.ofMillis(8),
        () -> offersClient.getApproximateOffers(accountId))
    .build();
```

When you know the cheaper fallback/approximate path cost, pass that latency hint too. It keeps request-scoped planning explainable while letting the planner reserve less budget for degraded work.

## 4) Execute and read results

```java
AdaptiveRequestResult result = request.execute(adaptiveExecutor).toCompletableFuture().join();

Balance balance = result.require(BALANCE);
List<Offer> offers = result.get(OFFERS).orElseGet(List::of);

RequestExecutionDiagnostics diagnostics = result.diagnostics();
List<DecisionTraceEntry> trace = result.decisionTrace();
```

What you get back:
- task values (`require`/`get`)
- degradation summary (`RequestExecutionDiagnostics`)
- per-task planning reasons and modes (`DecisionTraceEntry`)

## 5) Optional: wire runtime pressure + lifecycle hooks

BudgetFlow stays usable without extra wiring, but starter integration now supports a lightweight runtime adapter flow:

```yaml
budgetflow:
  planner:
    profile: default
  runtime-signals:
    enabled: true
    include-default-provider: true
```

```java
@Bean
RuntimePressureSignals runtimePressureSignals() {
    return new RuntimePressureSignals() {
        @Override public double executorUtilization() { return 0.55; }
        @Override public double dbPressure()          { return 0.40; }
        @Override public double downstreamPressure()  { return 0.65; }
    };
}
```

If present, `ExecutionLifecycleListener` beans are auto-wired into `AdaptiveExecutor`.

For quick local evaluation, runtime signals can also be provided via properties only (no adapter bean):

```yaml
budgetflow:
  runtime-signals:
    enabled: true
    include-default-provider: false
    executor-utilization: 0.72
    db-pressure: 0.64
    downstream-pressure: 0.48
```

You can also make the starter defaults visible in `application.yml`:

```yaml
budgetflow:
  enabled: true
  default-budget: 250ms
  planner:
    profile: default
  runtime-signals:
    enabled: false
    include-default-provider: true
```

## Fastest way to see it running

From the repository root:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough
./gradlew :budgetflow-demo-fintech:bootRun
curl http://localhost:8080/api/accounts/acc-123/dashboard
```

Then run the side-by-side scenario comparison:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=policy --policies=balanced,continuity,efficiency"
./gradlew :budgetflow-demo-fintech:runDashboardComparison --args="--pack=default --json --out=/tmp/budgetflow-report.json"
```

`budgetflow.planner.profile` is the recommended property name. `budgetflow.planner.policy-profile` remains supported for compatibility.

For a complete evaluator workflow (including what to observe and how to interpret profile tradeoffs), continue with:
`docs/evaluate.md`.
