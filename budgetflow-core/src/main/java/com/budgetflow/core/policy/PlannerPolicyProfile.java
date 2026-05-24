package com.budgetflow.core.policy;

import java.util.Arrays;
import java.util.List;

public enum PlannerPolicyProfile {
    BALANCED(
        "balanced",
        "Balanced (default)",
        "Recommended default profile for most services.",
        List.of("default")
    ),
    CONTINUITY(
        "continuity",
        "Continuity",
        "Favors preserving optional signal coverage through degraded paths before omission.",
        List.of()
    ),
    EFFICIENCY(
        "efficiency",
        "Efficiency",
        "Favors earlier optional omission to preserve latency headroom under stress.",
        List.of()
    );

    private final String configName;
    private final String displayName;
    private final String intent;
    private final List<String> aliases;

    PlannerPolicyProfile(
        String configName,
        String displayName,
        String intent,
        List<String> aliases
    ) {
        this.configName = configName;
        this.displayName = displayName;
        this.intent = intent;
        this.aliases = List.copyOf(aliases);
    }

    public String configName() {
        return configName;
    }

    public String displayName() {
        return displayName;
    }

    public String intent() {
        return intent;
    }

    public List<String> aliases() {
        return aliases;
    }

    public boolean matches(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return this == BALANCED;
        }
        String normalized = profileName.trim();
        return configName.equalsIgnoreCase(normalized)
            || aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(normalized));
    }

    public static PlannerPolicyProfile defaultProfile() {
        return BALANCED;
    }

    public static PlannerPolicyProfile fromConfigName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return defaultProfile();
        }
        return Arrays.stream(values())
            .filter(profile -> profile.matches(profileName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown planner policy profile '%s'. Supported profiles: %s"
                    .formatted(profileName, PlannerPolicyProfiles.supportedConfigNames())
            ));
    }
}
