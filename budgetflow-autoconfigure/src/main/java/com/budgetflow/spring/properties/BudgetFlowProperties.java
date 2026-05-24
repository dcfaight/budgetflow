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
        private String policyProfile;
        private String profile = "balanced";

        public String getPolicyProfile() {
            return policyProfile;
        }

        public void setPolicyProfile(String policyProfile) {
            this.policyProfile = policyProfile;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

        public String resolveProfileName() {
            if (policyProfile != null && !policyProfile.isBlank()) {
                return policyProfile;
            }
            if (profile != null && !profile.isBlank()) {
                return profile;
            }
            return "balanced";
        }
    }

    public static class RuntimeSignals {
        private boolean enabled;
        private boolean includeDefaultProvider = true;
        private Double executorUtilization;
        private Double dbPressure;
        private Double downstreamPressure;

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

        public Double getExecutorUtilization() {
            return executorUtilization;
        }

        public void setExecutorUtilization(Double executorUtilization) {
            this.executorUtilization = executorUtilization;
        }

        public Double getDbPressure() {
            return dbPressure;
        }

        public void setDbPressure(Double dbPressure) {
            this.dbPressure = dbPressure;
        }

        public Double getDownstreamPressure() {
            return downstreamPressure;
        }

        public void setDownstreamPressure(Double downstreamPressure) {
            this.downstreamPressure = downstreamPressure;
        }

        public boolean hasConfiguredSnapshot() {
            return executorUtilization != null || dbPressure != null || downstreamPressure != null;
        }

        public double configuredExecutorUtilization() {
            return executorUtilization == null ? 0.0 : executorUtilization;
        }

        public double configuredDbPressure() {
            return dbPressure == null ? 0.0 : dbPressure;
        }

        public double configuredDownstreamPressure() {
            return downstreamPressure == null ? 0.0 : downstreamPressure;
        }
    }
}
