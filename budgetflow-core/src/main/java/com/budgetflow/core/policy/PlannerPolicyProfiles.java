package com.budgetflow.core.policy;

import java.util.Arrays;
import java.util.List;

public final class PlannerPolicyProfiles {
    private PlannerPolicyProfiles() {
    }

    public static OptionalTaskModeSelector optionalTaskSelector(PlannerPolicyProfile profile) {
        return switch (profile) {
            case BALANCED -> new DefaultOptionalTaskModeSelector();
            case CONTINUITY -> new ContinuityOptionalTaskModeSelector();
            case EFFICIENCY -> new EfficiencyOptionalTaskModeSelector();
        };
    }

    public static OptionalTaskModeSelector optionalTaskSelector(String profileName) {
        return optionalTaskSelector(PlannerPolicyProfile.fromConfigName(profileName));
    }

    public static List<String> supportedProfileNames() {
        return Arrays.stream(PlannerPolicyProfile.values())
            .map(PlannerPolicyProfile::configName)
            .toList();
    }
}
