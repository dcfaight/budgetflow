package com.budgetflow.core.policy;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public final class PlannerPolicyProfiles {
    private PlannerPolicyProfiles() {
    }

    public static OptionalTaskModeSelector optionalTaskSelector(PlannerPolicyProfile profile) {
        return switch (profile) {
            case BALANCED -> new DefaultOptionalTaskModeSelector();
            case CONTINUITY -> new ContinuityOptionalTaskModeSelector();
            case EFFICIENCY -> new EfficiencyOptionalTaskModeSelector();
            case LATENCY_FIRST -> new LatencyFirstOptionalTaskModeSelector();
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

    public static List<String> supportedConfigNames() {
        return Arrays.stream(PlannerPolicyProfile.values())
            .flatMap(profile -> Stream.concat(Stream.of(profile.configName()), profile.aliases().stream()))
            .distinct()
            .toList();
    }
}
