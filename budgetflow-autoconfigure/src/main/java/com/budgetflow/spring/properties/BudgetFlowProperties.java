package com.budgetflow.spring.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "budgetflow")
public class BudgetFlowProperties {
    private boolean enabled = true;
    private Duration defaultBudget = Duration.ofMillis(250);
    private RuntimeSignals runtimeSignals = new RuntimeSignals();
    private Planner planner = new Planner();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDefaultBudget() {
        return defaultBudget;
    }

    public void setDefaultBudget(Duration defaultBudget) {
        this.defaultBudget = defaultBudget;
    }

    public RuntimeSignals getRuntimeSignals() {
        return runtimeSignals;
    }

    public void setRuntimeSignals(RuntimeSignals runtimeSignals) {
        this.runtimeSignals = runtimeSignals;
    }

    public Planner getPlanner() {
        return planner;
    }

    public void setPlanner(Planner planner) {
        this.planner = planner;
    }

    public static class Planner {
        private String policyProfile = "balanced";

        public String getPolicyProfile() {
            return policyProfile;
        }

        public void setPolicyProfile(String policyProfile) {
            this.policyProfile = policyProfile;
        }
    }

    public static class RuntimeSignals {
        private boolean enabled;
        private boolean includeDefaultProvider = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeDefaultProvider() {
            return includeDefaultProvider;
        }

        public void setIncludeDefaultProvider(boolean includeDefaultProvider) {
            this.includeDefaultProvider = includeDefaultProvider;
        }
    }
}
