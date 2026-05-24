package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerPolicyProfilesTest {
    @Test
    void profileNamesAreStableAndSupported() {
        assertEquals(List.of("balanced", "continuity", "efficiency"), PlannerPolicyProfiles.supportedProfileNames());
    }

    @Test
    void unknownProfileNameThrowsHelpfulError() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PlannerPolicyProfile.fromConfigName("unknown")
        );
        assertTrue(exception.getMessage().contains("balanced"));
        assertTrue(exception.getMessage().contains("default"));
        assertTrue(exception.getMessage().contains("continuity"));
        assertTrue(exception.getMessage().contains("efficiency"));
    }

    @Test
    void defaultAliasResolvesToBalancedProfile() {
        assertEquals(PlannerPolicyProfile.BALANCED, PlannerPolicyProfile.fromConfigName("default"));
    }

    @Test
    void profileSelectorsRemainDeterministicForEquivalentInputs() {
        TaskDescriptor task = new TaskDescriptor(
            "offers",
            com.budgetflow.core.classification.Importance.OPTIONAL,
            Duration.ofMillis(120),
            true,
            true,
            Duration.ofMillis(16),
            Duration.ofMillis(9)
        );
        OptionalTaskPlanningContext context = new OptionalTaskPlanningContext(
            new SystemPressureSnapshot(0.90, 0.20, 0.20),
            Duration.ofMillis(260),
            1,
            false,
            0.433,
            0.76,
            0.46,
            0.07,
            0.93,
            111,
            true,
            true,
            true,
            true,
            true,
            0,
            0,
            0,
            0,
            false,
            false,
            true,
            "moderate",
            ExecutionMode.EXECUTE_APPROXIMATE,
            0.50,
            0.74
        );

        OptionalTaskModeSelector selector = PlannerPolicyProfiles.optionalTaskSelector(PlannerPolicyProfile.EFFICIENCY);
        ExecutionMode first = selector.chooseMode(task, context);
        ExecutionMode second = selector.chooseMode(task, context);

        assertEquals(first, second);
    }
}
