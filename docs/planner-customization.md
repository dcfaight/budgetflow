# BudgetFlow planner customization

BudgetFlow keeps planner customization intentionally small:

- **Default path:** stay on `budgetflow.planner.profile=balanced`
- **First advanced step:** compare `continuity` and `efficiency`
- **Last step:** provide a custom `OptionalTaskModeSelector` only when the built-in profiles are still not enough

This is a prototype framework, so the goal is explainable customization, not a heavyweight planner plugin system.

## 1) Choose the lightest path that fits

| Need | Recommended path |
|---|---|
| First-time adoption or unclear tradeoffs | `balanced` |
| Preserve more optional response coverage | `continuity` |
| Protect stricter latency headroom | `efficiency` |
| Real-time or high-frequency agent turns; maximise remaining budget headroom | `latency_first` |
| Endpoint-specific optional-task policy not covered by the built-ins | custom `OptionalTaskModeSelector` |

Recommended Spring Boot property:

```yaml
budgetflow:
  planner:
    profile: balanced
```

Supported built-in values:
- `balanced` / `default`
- `continuity`
- `efficiency`
- `latency_first` / `latency` / `fast`

### Profile summary

| Profile | Optional behavior | Degraded-path usage | Best for |
|---|---|---|---|
| `balanced` | Degrade then omit under stress | Full degraded-path exploration | General-purpose default |
| `continuity` | Strongly prefers degraded paths before omission | Maximises degraded path use | Preserving response coverage |
| `efficiency` | Omit earlier, minimal degraded-path use | Lean degraded-path use | Protecting latency headroom |
| `latency_first` | Omit at a low ratio threshold; never use degraded paths for optional work | None for optional steps | Real-time agent turns; strict latency budgets |

## 2) Understand what is customizable

BudgetFlow separates planner responsibilities on purpose:

- request-budget fit, pressure analysis, and degraded-path cost signals are computed first
- `OptionalTaskModeSelector` decides **optional-task mode only**
- orchestration order, diagnostics, and decision trace formatting stay in the default planner
- reason strings expose key planner signals (`mixed`, `degrade_pref`, `fit`, `savings`) so profile or custom-selector behavior is still auditable

That means custom selectors can change optional-task behavior without losing centralized trace semantics.

## 3) Concrete custom-selector example

Use a custom selector when you want a clear, endpoint-specific policy shape.

```java
final class UserVisibleCoverageSelector implements OptionalTaskModeSelector {
    @Override
    public ExecutionMode chooseMode(TaskDescriptor task, OptionalTaskPlanningContext context) {
        if (!context.highPressure() && context.primaryFitsBudget()) {
            return ExecutionMode.EXECUTE;
        }
        if (task.fallbackSupported() && !context.veryLowBudget()) {
            return ExecutionMode.EXECUTE_WITH_FALLBACK;
        }
        return context.suggestedDegradedMode(task);
    }
}

AdaptiveExecutor executor = new DefaultAdaptiveExecutor(
    new DefaultBudgetPolicyEngine(new UserVisibleCoverageSelector(), "user_visible_coverage")
);
```

What this demonstrates:
- keep the planner deterministic
- use precomputed context instead of recomputing pressure/budget logic
- keep reason formatting, diagnostics, and decision trace centralized in `DefaultBudgetPolicyEngine`

## 4) Review custom behavior responsibly

When comparing a custom selector to the built-in profiles:

1. run the sample endpoint flow first
2. run `--pack=default`
3. run `--pack=policy --policies=balanced,continuity,efficiency`
4. run `--pack=agent --policies=balanced,latency_first` to see coordination and degraded-cascade boundary cases
5. only then compare your custom selector against the same scenarios

Inspect:
- `diagnostics.degraded`
- omitted/fallback/approximated task lists
- `decisionTrace[*].reason`
- `decisionTrace[*].plannedExecutionLatency`
- comparison `comparisonTakeaway` and `confidenceSummary`

## 5) Keep customization maintainable

- Prefer one clear selector per endpoint family over many subtle variants.
- Use built-in profiles for broad defaults and custom selectors for explicit exceptions.
- Treat harness and scenario output as evaluation support, not benchmark proof.
