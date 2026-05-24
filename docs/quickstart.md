# BudgetFlow quickstart (Spring Boot)

BudgetFlow is an early prototype; this quickstart is optimized for trying the core flow quickly, not production hardening.

## 1) Add the dependency

In this repository’s multi-module setup, depend on the starter module:

```kotlin
dependencies {
    implementation(project(":budgetflow-spring-boot-starter"))
}
```

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
        () -> offersClient.getCachedOffers(accountId),
        () -> offersClient.getApproximateOffers(accountId))
    .build();
```

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

## Fastest way to see it running

From the repository root:

```bash
./gradlew :budgetflow-demo-fintech:bootRun
curl http://localhost:8080/api/accounts/acc-123/dashboard
```

Then run the side-by-side scenario comparison:

```bash
./gradlew :budgetflow-demo-fintech:runDashboardComparison
```
