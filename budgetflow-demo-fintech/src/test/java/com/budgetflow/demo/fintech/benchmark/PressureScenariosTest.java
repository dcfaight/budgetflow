package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.core.policy.SystemPressureSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PressureScenariosTest {

    // -----------------------------------------------------------------------
    // Named pressure constants
    // -----------------------------------------------------------------------

    @Test
    void lowPressureConstantHasExpectedValues() {
        SystemPressureSnapshot p = PressureScenarios.LOW_PRESSURE;
        assertEquals(0.15, p.executorUtilization());
        assertEquals(0.10, p.dbPressure());
        assertEquals(0.20, p.downstreamPressure());
    }

    @Test
    void moderatePressureConstantHasExpectedValues() {
        SystemPressureSnapshot p = PressureScenarios.MODERATE_PRESSURE;
        assertEquals(0.55, p.executorUtilization());
        assertEquals(0.62, p.dbPressure());
        assertEquals(0.58, p.downstreamPressure());
    }

    @Test
    void elevatedPressureConstantHasExpectedValues() {
        SystemPressureSnapshot p = PressureScenarios.ELEVATED_PRESSURE;
        assertEquals(0.90, p.executorUtilization());
        assertEquals(0.88, p.dbPressure());
        assertEquals(0.92, p.downstreamPressure());
    }

    @Test
    void moderateDbPressureConstantHasElevatedDbAndLowOthers() {
        SystemPressureSnapshot p = PressureScenarios.MODERATE_DB_PRESSURE;
        assertEquals(0.20, p.executorUtilization());
        assertEquals(0.65, p.dbPressure());
        assertEquals(0.15, p.downstreamPressure());
    }

    @Test
    void downstreamSpikePressureConstantHasElevatedDownstreamAndLowOthers() {
        SystemPressureSnapshot p = PressureScenarios.DOWNSTREAM_SPIKE_PRESSURE;
        assertEquals(0.30, p.executorUtilization());
        assertEquals(0.22, p.dbPressure());
        assertEquals(0.84, p.downstreamPressure());
    }

    // -----------------------------------------------------------------------
    // Scenario factory methods — name, budget, pressure
    // -----------------------------------------------------------------------

    @Test
    void generousBudgetLowPressureScenarioIsConsistent() {
        DashboardBenchmarkScenario scenario = PressureScenarios.generousBudgetLowPressure();
        assertEquals("default", scenario.packName());
        assertEquals("generous_budget_low_pressure", scenario.name());
        assertEquals("Generous budget / low pressure", scenario.displayName());
        assertEquals(
            "Sanity baseline: verifies no unnecessary degradation under comfortable constraints.",
            scenario.evaluationFocus()
        );
        assertEquals(
            "Steady weekday traffic with healthy dependencies.",
            scenario.realWorldPattern()
        );
        assertEquals(Duration.ofMillis(650), scenario.requestBudget());
        assertEquals(PressureScenarios.LOW_PRESSURE, scenario.pressureSnapshot());
    }

    @Test
    void constrainedBudgetLowPressureScenarioIsConsistent() {
        DashboardBenchmarkScenario scenario = PressureScenarios.constrainedBudgetLowPressure();
        assertEquals("constrained_budget_low_pressure", scenario.name());
        assertEquals("constrained_budget", scenario.budgetProfile());
        assertEquals(Duration.ofMillis(430), scenario.requestBudget());
        assertEquals(PressureScenarios.LOW_PRESSURE, scenario.pressureSnapshot());
    }

    @Test
    void constrainedBudgetElevatedPressureScenarioIsConsistent() {
        DashboardBenchmarkScenario scenario = PressureScenarios.constrainedBudgetElevatedPressure();
        assertEquals("constrained_budget_elevated_pressure", scenario.name());
        assertEquals(Duration.ofMillis(430), scenario.requestBudget());
        assertEquals(PressureScenarios.ELEVATED_PRESSURE, scenario.pressureSnapshot());
    }

    @Test
    void generousBudgetElevatedPressureScenarioIsConsistent() {
        DashboardBenchmarkScenario scenario = PressureScenarios.generousBudgetElevatedPressure();
        assertEquals("extended", scenario.packName());
        assertEquals("generous_budget_elevated_pressure", scenario.name());
        assertEquals(Duration.ofMillis(650), scenario.requestBudget());
        assertEquals(PressureScenarios.ELEVATED_PRESSURE, scenario.pressureSnapshot());
    }

    @Test
    void tightBudgetModerateDbPressureScenarioIsConsistent() {
        DashboardBenchmarkScenario scenario = PressureScenarios.tightBudgetModerateDbPressure();
        assertEquals("tight_budget_moderate_db_pressure", scenario.name());
        assertEquals(Duration.ofMillis(300), scenario.requestBudget());
        assertEquals(PressureScenarios.MODERATE_DB_PRESSURE, scenario.pressureSnapshot());
    }

    @Test
    void moderateBudgetDownstreamSpikeScenarioIsConsistent() {
        DashboardBenchmarkScenario scenario = PressureScenarios.moderateBudgetDownstreamSpike();
        assertEquals("moderate_budget_downstream_spike", scenario.name());
        assertEquals(Duration.ofMillis(500), scenario.requestBudget());
        assertEquals(PressureScenarios.DOWNSTREAM_SPIKE_PRESSURE, scenario.pressureSnapshot());
        assertEquals("downstream_spike", scenario.pressureProfile());
        assertEquals(
            "Promotions service instability during campaign launch.",
            scenario.realWorldPattern()
        );
    }

    @Test
    void moderateBudgetElevatedPressureScenarioIsConsistent() {
        DashboardBenchmarkScenario scenario = PressureScenarios.moderateBudgetElevatedPressure();
        assertEquals("policy", scenario.packName());
        assertEquals("moderate_budget_elevated_pressure", scenario.name());
        assertEquals(
            "Use deltas for policy selection guidance only; do not treat one scenario as proof of global superiority.",
            scenario.interpretationGuidance()
        );
        assertEquals(Duration.ofMillis(520), scenario.requestBudget());
        assertEquals(PressureScenarios.ELEVATED_PRESSURE, scenario.pressureSnapshot());
    }

    // -----------------------------------------------------------------------
    // Scenario factories are deterministic
    // -----------------------------------------------------------------------

    @Test
    void scenarioFactoriesAreDeterministic() {
        assertEquals(PressureScenarios.generousBudgetLowPressure(),
            PressureScenarios.generousBudgetLowPressure());
        assertEquals(PressureScenarios.constrainedBudgetLowPressure(),
            PressureScenarios.constrainedBudgetLowPressure());
        assertEquals(PressureScenarios.constrainedBudgetElevatedPressure(),
            PressureScenarios.constrainedBudgetElevatedPressure());
        assertEquals(PressureScenarios.generousBudgetElevatedPressure(),
            PressureScenarios.generousBudgetElevatedPressure());
        assertEquals(PressureScenarios.tightBudgetModerateDbPressure(),
            PressureScenarios.tightBudgetModerateDbPressure());
        assertEquals(PressureScenarios.moderateBudgetDownstreamSpike(),
            PressureScenarios.moderateBudgetDownstreamSpike());
        assertEquals(PressureScenarios.moderateBudgetElevatedPressure(),
            PressureScenarios.moderateBudgetElevatedPressure());
    }

    // -----------------------------------------------------------------------
    // defaultScenarios() list
    // -----------------------------------------------------------------------

    @Test
    void defaultScenariosListContainsThreeEntries() {
        List<DashboardBenchmarkScenario> defaults = PressureScenarios.defaultScenarios();
        assertEquals(3, defaults.size());
    }

    @Test
    void defaultScenariosMatchIndividualFactories() {
        List<DashboardBenchmarkScenario> defaults = PressureScenarios.defaultScenarios();
        assertEquals(PressureScenarios.generousBudgetLowPressure(), defaults.get(0));
        assertEquals(PressureScenarios.constrainedBudgetLowPressure(), defaults.get(1));
        assertEquals(PressureScenarios.constrainedBudgetElevatedPressure(), defaults.get(2));
    }

    @Test
    void defaultScenariosListIsImmutable() {
        List<DashboardBenchmarkScenario> defaults = PressureScenarios.defaultScenarios();
        assertNotNull(defaults);
        // List.of() produces an immutable list; any mutation attempt throws
        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> defaults.add(PressureScenarios.generousBudgetElevatedPressure())
        );
    }

    @Test
    void scenarioPacksExposeNamedReusableCollections() {
        DashboardScenarioPack extended = PressureScenarios.extendedPack();
        DashboardScenarioPack realism = PressureScenarios.realismPack();
        DashboardScenarioPack policy = PressureScenarios.policyPack();
        DashboardScenarioPack policyFromLookup = PressureScenarios.packNamed("policy");

        assertEquals("extended", extended.name());
        assertEquals(6, extended.scenarios().size());
        assertEquals("realism", realism.name());
        assertEquals(4, realism.scenarios().size());
        assertEquals("policy", policy.name());
        assertEquals(4, policy.scenarios().size());
        assertEquals(policy, policyFromLookup);
    }
}
