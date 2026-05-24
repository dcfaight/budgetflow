package com.budgetflow.spring.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "budgetflow")
public class BudgetFlowProperties {
    private boolean enabled = true;
    private Duration defaultBudget = Duration.ofMillis(250);
    private RuntimeSignals runtimeSignals = new RuntimeSignals();

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
