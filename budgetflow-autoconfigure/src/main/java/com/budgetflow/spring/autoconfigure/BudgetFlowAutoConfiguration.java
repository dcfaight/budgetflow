package com.budgetflow.spring.autoconfigure;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.execution.DefaultAdaptiveExecutor;
import com.budgetflow.core.policy.BudgetPolicyEngine;
import com.budgetflow.core.policy.DefaultBudgetPolicyEngine;
import com.budgetflow.core.policy.DefaultSystemPressureProvider;
import com.budgetflow.core.policy.SystemPressureProvider;
import com.budgetflow.spring.aop.LatencyBudgetAspect;
import com.budgetflow.spring.properties.BudgetFlowProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(BudgetFlowProperties.class)
public class BudgetFlowAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AdaptiveExecutor adaptiveExecutor(BudgetPolicyEngine budgetPolicyEngine, SystemPressureProvider systemPressureProvider) {
        return new DefaultAdaptiveExecutor(budgetPolicyEngine, systemPressureProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public BudgetPolicyEngine budgetPolicyEngine() {
        return new DefaultBudgetPolicyEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemPressureProvider systemPressureProvider() {
        return new DefaultSystemPressureProvider();
    }

    @Bean
    @ConditionalOnProperty(prefix = "budgetflow", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public LatencyBudgetAspect latencyBudgetAspect(BudgetFlowProperties properties) {
        return new LatencyBudgetAspect(properties);
    }
}
