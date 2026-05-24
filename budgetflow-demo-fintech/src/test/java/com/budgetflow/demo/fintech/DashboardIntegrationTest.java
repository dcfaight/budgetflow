package com.budgetflow.demo.fintech;

import com.budgetflow.core.api.RequestExecutionResult;
import com.budgetflow.core.execution.ExecutionLifecycleListener;
import com.budgetflow.core.policy.PolicyDecision;
import com.budgetflow.core.policy.PolicyEvaluationInput;
import com.budgetflow.demo.fintech.dashboard.SimulationSupport;
import com.budgetflow.spring.integration.RuntimePressureSignals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {
        FintechDemoApplication.class,
        DashboardIntegrationTest.TestConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "budgetflow.runtime-signals.enabled=true",
    "budgetflow.runtime-signals.include-default-provider=false"
})
class DashboardIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackingLifecycleListener trackingLifecycleListener;

    @Test
    void dashboardEndpointShowsExplainableAdaptiveResponseUnderRuntimePressure() throws Exception {
        trackingLifecycleListener.reset();

        mockMvc.perform(get("/api/accounts/acc-123/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rewards.points").value(1800))
            .andExpect(jsonPath("$.offers[0].title").value("Approximate: personalized offers unavailable"))
            .andExpect(jsonPath("$.insights.summary").value("Insights omitted due to budget constraints."))
            .andExpect(jsonPath("$.diagnostics.degraded").value(true))
            .andExpect(jsonPath("$.diagnostics.omittedTaskNames[0]").value("insights"))
            .andExpect(jsonPath("$.diagnostics.fallbackTaskNames[0]").value("rewards"))
            .andExpect(jsonPath("$.diagnostics.approximatedTaskNames[0]").value("offers"))
            .andExpect(jsonPath("$.decisionTrace[2].taskName").value("rewards"))
            .andExpect(jsonPath("$.decisionTrace[2].selectedExecutionMode").value("EXECUTE_WITH_FALLBACK"))
            .andExpect(jsonPath("$.decisionTrace[2].plannedExecutionLatency").value("PT0.01S"))
            .andExpect(jsonPath("$.decisionTrace[3].taskName").value("offers"))
            .andExpect(jsonPath("$.decisionTrace[3].plannedExecutionLatency").value("PT0.008S"))
            .andExpect(jsonPath("$.executionSummary", containsString("rewards=EXECUTE_WITH_FALLBACK@10ms")))
            .andExpect(jsonPath("$.executionSummary", containsString("offers=EXECUTE_APPROXIMATE@8ms")));

        assertEquals(1, trackingLifecycleListener.beforePolicyCalls.get());
        assertEquals(1, trackingLifecycleListener.afterPolicyCalls.get());
        assertEquals(1, trackingLifecycleListener.afterExecutionCalls.get());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        @Primary
        SimulationSupport noDelaySimulationSupport() {
            return new SimulationSupport() {
                @Override
                public void delay(long millis) {
                }
            };
        }

        @Bean
        RuntimePressureSignals runtimePressureSignals() {
            return new RuntimePressureSignals() {
                @Override
                public double executorUtilization() {
                    return 0.35;
                }

                @Override
                public double dbPressure() {
                    return 0.30;
                }

                @Override
                public double downstreamPressure() {
                    return 0.92;
                }
            };
        }

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
        public void beforePolicyEvaluation(PolicyEvaluationInput input) {
            beforePolicyCalls.incrementAndGet();
        }

        @Override
        public void afterPolicyEvaluation(PolicyEvaluationInput input, PolicyDecision decision) {
            afterPolicyCalls.incrementAndGet();
        }

        @Override
        public void afterRequestExecution(RequestExecutionResult result) {
            afterExecutionCalls.incrementAndGet();
        }

        void reset() {
            beforePolicyCalls.set(0);
            afterPolicyCalls.set(0);
            afterExecutionCalls.set(0);
        }
    }
}
