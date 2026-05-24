package com.budgetflow.core.policy;

import java.util.Arrays;

public enum PlannerPolicyProfile {
    BALANCED("balanced"),
    CONTINUITY("continuity"),
    EFFICIENCY("efficiency");

    private final String configName;

    PlannerPolicyProfile(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }

    public static PlannerPolicyProfile fromConfigName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return BALANCED;
        }
        return Arrays.stream(values())
            .filter(profile -> profile.configName.equalsIgnoreCase(profileName.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown planner policy profile '%s'. Supported profiles: %s"
                    .formatted(profileName, PlannerPolicyProfiles.supportedProfileNames())
            ));
    }
}
