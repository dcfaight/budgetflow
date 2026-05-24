package com.budgetflow.spring.autoconfigure;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.execution.DefaultAdaptiveExecutor;
import com.budgetflow.core.execution.ExecutionLifecycleListener;
import com.budgetflow.core.policy.BudgetPolicyEngine;
import com.budgetflow.core.policy.CompositeSystemPressureProvider;
import com.budgetflow.core.policy.DefaultBudgetPolicyEngine;
import com.budgetflow.core.policy.DefaultSystemPressureProvider;
import com.budgetflow.core.policy.PlannerPolicyProfile;
import com.budgetflow.core.policy.RuntimeSignalPressureProvider;
import com.budgetflow.core.policy.SystemPressureProvider;
import com.budgetflow.spring.aop.LatencyBudgetAspect;
import com.budgetflow.spring.integration.RuntimePressureSignals;
import com.budgetflow.spring.properties.BudgetFlowProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ForkJoinPool;

@AutoConfiguration
@EnableConfigurationProperties(BudgetFlowProperties.class)
public class BudgetFlowAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AdaptiveExecutor adaptiveExecutor(
        BudgetPolicyEngine budgetPolicyEngine,
        SystemPressureProvider systemPressureProvider,
        ObjectProvider<ExecutionLifecycleListener> lifecycleListeners
    ) {
        return new DefaultAdaptiveExecutor(
            ForkJoinPool.commonPool(),
            budgetPolicyEngine,
            systemPressureProvider,
            lifecycleListeners.orderedStream().toList()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public BudgetPolicyEngine budgetPolicyEngine(BudgetFlowProperties properties) {
        String configuredProfile = properties.getPlanner() == null
            ? PlannerPolicyProfile.defaultProfile().configName()
            : properties.getPlanner().resolveProfileName();
        return new DefaultBudgetPolicyEngine(PlannerPolicyProfile.fromConfigName(configuredProfile));
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemPressureProvider systemPressureProvider(
        BudgetFlowProperties properties,
        ObjectProvider<RuntimePressureSignals> runtimePressureSignals
    ) {
        SystemPressureProvider defaultProvider = new DefaultSystemPressureProvider();
        boolean runtimeSignalsEnabled = properties.getRuntimeSignals() != null
            && properties.getRuntimeSignals().isEnabled();

        if (!runtimeSignalsEnabled) {
            return defaultProvider;
        }

        SystemPressureProvider runtimeProvider = runtimeProvider(properties, runtimePressureSignals.getIfAvailable());
        if (runtimeProvider == null) {
            return defaultProvider;
        }

        if (!properties.getRuntimeSignals().isIncludeDefaultProvider()) {
            return runtimeProvider;
        }

        return CompositeSystemPressureProvider.of(defaultProvider, runtimeProvider);
    }

    private SystemPressureProvider runtimeProvider(
        BudgetFlowProperties properties,
        RuntimePressureSignals signalAdapter
    ) {
        if (signalAdapter != null) {
            return new RuntimeSignalPressureProvider(
                signalAdapter::executorUtilization,
                signalAdapter::dbPressure,
                signalAdapter::downstreamPressure
            );
        }

        BudgetFlowProperties.RuntimeSignals runtimeSignals = properties.getRuntimeSignals();
        if (runtimeSignals == null || !runtimeSignals.hasConfiguredSnapshot()) {
            return null;
        }

        return new RuntimeSignalPressureProvider(
            runtimeSignals::configuredExecutorUtilization,
            runtimeSignals::configuredDbPressure,
            runtimeSignals::configuredDownstreamPressure
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "budgetflow", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public LatencyBudgetAspect latencyBudgetAspect(BudgetFlowProperties properties) {
        return new LatencyBudgetAspect(properties);
    }
}
