package com.budgetflow.spring.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "budgetflow")
public class BudgetFlowProperties {
    private boolean enabled = true;
    private Duration defaultBudget = Duration.ofMillis(250);

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
}
