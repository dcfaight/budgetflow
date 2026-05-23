package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.policy.SystemPressureSnapshot;

import java.time.Duration;
import java.util.List;

/**
 * Factory for reusable {@link DashboardBenchmarkScenario} instances used in the comparison harness.
 *
 * <p>Centralises scenario and pressure-snapshot definitions so they are easy to compose,
 * extend, and reason about without duplicating magic numbers across the codebase.
 *
 * <p>Named pressure snapshots can be used directly when constructing custom scenarios:
 * <pre>{@code
 * new DashboardBenchmarkScenario("my_scenario", Duration.ofMillis(500), PressureScenarios.MODERATE_PRESSURE);
 * }</pre>
 */
public final class PressureScenarios {

    /** Low pressure: all dimensions below 25%. */
    public static final SystemPressureSnapshot LOW_PRESSURE =
        new SystemPressureSnapshot(0.15, 0.10, 0.20);

    /** Moderate pressure: all dimensions around 55–62%. */
    public static final SystemPressureSnapshot MODERATE_PRESSURE =
        new SystemPressureSnapshot(0.55, 0.62, 0.58);

    /** Elevated pressure: all dimensions above 85%. */
    public static final SystemPressureSnapshot ELEVATED_PRESSURE =
        new SystemPressureSnapshot(0.90, 0.88, 0.92);

    /**
     * Moderate database pressure: executor and downstream are low, but DB is elevated.
     * Models a scenario where a slow database query queue is the primary bottleneck.
     */
    public static final SystemPressureSnapshot MODERATE_DB_PRESSURE =
        new SystemPressureSnapshot(0.20, 0.65, 0.15);

    private PressureScenarios() {
    }

    /**
     * Generous budget (650 ms) under low pressure — the system has headroom on both
     * dimensions. Adaptive and naïve results should be identical.
     */
    public static DashboardBenchmarkScenario generousBudgetLowPressure() {
        return new DashboardBenchmarkScenario(
            "generous_budget_low_pressure",
            Duration.ofMillis(650),
            LOW_PRESSURE
        );
    }

    /**
     * Constrained budget (430 ms) under low pressure — budget is the binding constraint;
     * the adaptive executor should shed optional work the naïve approach cannot.
     */
    public static DashboardBenchmarkScenario constrainedBudgetLowPressure() {
        return new DashboardBenchmarkScenario(
            "constrained_budget_low_pressure",
            Duration.ofMillis(430),
            LOW_PRESSURE
        );
    }

    /**
     * Constrained budget (430 ms) with elevated pressure — both budget and system load
     * are high; the adaptive executor should shed more aggressively than under low pressure.
     */
    public static DashboardBenchmarkScenario constrainedBudgetElevatedPressure() {
        return new DashboardBenchmarkScenario(
            "constrained_budget_elevated_pressure",
            Duration.ofMillis(430),
            ELEVATED_PRESSURE
        );
    }

    /**
     * Generous budget (650 ms) with elevated pressure — budget is not the constraint but
     * high system load should still cause the adaptive executor to degrade non-essential work.
     */
    public static DashboardBenchmarkScenario generousBudgetElevatedPressure() {
        return new DashboardBenchmarkScenario(
            "generous_budget_elevated_pressure",
            Duration.ofMillis(650),
            ELEVATED_PRESSURE
        );
    }

    /**
     * Tight budget (300 ms) with moderate database pressure — DB load is above the moderate
     * threshold while executor and downstream are low; models a DB-bound degradation scenario.
     */
    public static DashboardBenchmarkScenario tightBudgetModerateDbPressure() {
        return new DashboardBenchmarkScenario(
            "tight_budget_moderate_db_pressure",
            Duration.ofMillis(300),
            MODERATE_DB_PRESSURE
        );
    }

    /**
     * Returns all default scenarios used by {@link DashboardComparisonHarness#runDefaultScenarios()}.
     */
    public static List<DashboardBenchmarkScenario> defaultScenarios() {
        return List.of(
            generousBudgetLowPressure(),
            constrainedBudgetLowPressure(),
            constrainedBudgetElevatedPressure()
        );
    }
}
