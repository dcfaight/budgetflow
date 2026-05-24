package com.budgetflow.spring.autoconfigure;

import com.budgetflow.core.api.AdaptiveExecutor;
import com.budgetflow.core.api.TaskSpec;
import com.budgetflow.core.execution.ExecutionLifecycleListener;
import com.budgetflow.core.policy.BudgetPolicyEngine;
import com.budgetflow.core.policy.CompositeSystemPressureProvider;
import com.budgetflow.core.policy.DefaultSystemPressureProvider;
import com.budgetflow.core.policy.PolicyDecision;
import com.budgetflow.core.policy.PolicyEvaluationInput;
import com.budgetflow.core.policy.TaskDescriptor;
import com.budgetflow.core.policy.RuntimeSignalPressureProvider;
import com.budgetflow.core.policy.SystemPressureProvider;
import com.budgetflow.core.policy.SystemPressureSnapshot;
import com.budgetflow.core.classification.Importance;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.spring.integration.RuntimePressureSignals;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetFlowAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BudgetFlowAutoConfiguration.class));

    @Test
    void runtimeSignalAdapterIsIgnoredByDefaultUnlessEnabled() {
        contextRunner
            .withUserConfiguration(RuntimeSignalAdapterConfig.class)
            .run(context -> {
                SystemPressureProvider provider = context.getBean(SystemPressureProvider.class);
                assertInstanceOf(DefaultSystemPressureProvider.class, provider);
            });
    }

    @Test
    void runtimeSignalProviderCanBeUsedWithoutDefaultPressureProvider() {
        contextRunner
            .withUserConfiguration(RuntimeSignalAdapterConfig.class)
            .withPropertyValues(
                "budgetflow.runtime-signals.enabled=true",
                "budgetflow.runtime-signals.include-default-provider=false"
            )
            .run(context -> {
                SystemPressureProvider provider = context.getBean(SystemPressureProvider.class);
                assertInstanceOf(RuntimeSignalPressureProvider.class, provider);

                SystemPressureSnapshot snapshot = provider.currentPressure();
                assertEquals(0.55, snapshot.executorUtilization());
                assertEquals(0.40, snapshot.dbPressure());
                assertEquals(0.65, snapshot.downstreamPressure());
            });
    }

    @Test
    void runtimeSignalProviderCanBeComposedWithDefaultPressureProvider() {
        contextRunner
            .withUserConfiguration(RuntimeSignalAdapterConfig.class)
            .withPropertyValues("budgetflow.runtime-signals.enabled=true")
            .run(context -> {
                SystemPressureProvider provider = context.getBean(SystemPressureProvider.class);
                assertInstanceOf(CompositeSystemPressureProvider.class, provider);

                SystemPressureSnapshot snapshot = provider.currentPressure();
                assertTrue(snapshot.executorUtilization() >= 0.55);
                assertTrue(snapshot.dbPressure() >= 0.40);
                assertTrue(snapshot.downstreamPressure() >= 0.65);
            });
    }

    @Test
    void lifecycleListenersAreWiredIntoAdaptiveExecutor() {
        contextRunner
            .withUserConfiguration(LifecycleListenerConfig.class)
            .run(context -> {
                AdaptiveExecutor adaptiveExecutor = context.getBean(AdaptiveExecutor.class);
                TrackingLifecycleListener listener = context.getBean(TrackingLifecycleListener.class);

                adaptiveExecutor.execute(TaskSpec.optional("offers", Duration.ofMillis(80), () -> "ok"))
                    .toCompletableFuture()
                    .join();

                assertEquals(1, listener.beforePolicyCalls.get());
                assertEquals(1, listener.afterPolicyCalls.get());
                assertEquals(1, listener.afterExecutionCalls.get());
            });
    }

    @Test
    void plannerPolicyProfilePropertySelectsDeterministicVariant() {
        contextRunner
            .withPropertyValues("budgetflow.planner.policy-profile=efficiency")
            .run(context -> {
                BudgetPolicyEngine policyEngine = context.getBean(BudgetPolicyEngine.class);
                PolicyDecision decision = policyEngine.evaluate(new PolicyEvaluationInput(
                    Duration.ofMillis(260),
                    java.util.List.of(new TaskDescriptor(
                        "offers",
                        Importance.OPTIONAL,
                        Duration.ofMillis(110),
                        true,
                        true,
                        Duration.ofMillis(12),
                        Duration.ofMillis(8)
                    )),
                    new SystemPressureSnapshot(0.90, 0.20, 0.20)
                ));

                assertEquals(ExecutionMode.OMIT, decision.directives().get(0).executionMode());
                assertTrue(decision.directives().get(0).reason().contains("policy=efficiency"));
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeSignalAdapterConfig {
        @Bean
        RuntimePressureSignals runtimePressureSignals() {
            return new RuntimePressureSignals() {
                @Override
                public double executorUtilization() {
                    return 0.55;
                }

                @Override
                public double dbPressure() {
                    return 0.40;
                }

                @Override
                public double downstreamPressure() {
                    return 0.65;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LifecycleListenerConfig {
        @Bean
        TrackingLifecycleListener trackingLifecycleListener() {
            return new TrackingLifecycleListener();
        }
    }

    static final class TrackingLifecycleListener implements ExecutionLifecycleListener {
        private final AtomicInteger beforePolicyCalls = new AtomicInteger();
        private final AtomicInteger afterPolicyCalls = new AtomicInteger();
        private final AtomicInteger afterExecutionCalls = new AtomicInteger();

        @Override
        public void beforePolicyEvaluation(com.budgetflow.core.policy.PolicyEvaluationInput input) {
            beforePolicyCalls.incrementAndGet();
        }

        @Override
        public void afterPolicyEvaluation(
            com.budgetflow.core.policy.PolicyEvaluationInput input,
            com.budgetflow.core.policy.PolicyDecision decision
        ) {
            afterPolicyCalls.incrementAndGet();
        }

        @Override
        public void afterRequestExecution(com.budgetflow.core.api.RequestExecutionResult result) {
            afterExecutionCalls.incrementAndGet();
        }
    }
}
