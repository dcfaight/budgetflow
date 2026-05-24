package com.budgetflow.demo.fintech;

import com.budgetflow.demo.fintech.dashboard.SimulationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {
        FintechDemoApplication.class,
        DashboardStaticSignalsIntegrationTest.TestConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "budgetflow.planner.profile=continuity",
    "budgetflow.runtime-signals.enabled=true",
    "budgetflow.runtime-signals.include-default-provider=false",
    "budgetflow.runtime-signals.executor-utilization=0.35",
    "budgetflow.runtime-signals.db-pressure=0.30",
    "budgetflow.runtime-signals.downstream-pressure=0.65"
})
class DashboardStaticSignalsIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardEndpointSupportsPropertyOnlyRuntimeSignalsAndContinuityProfile() throws Exception {
        mockMvc.perform(get("/api/accounts/acc-123/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.diagnostics.degraded").value(true))
            .andExpect(jsonPath("$.diagnostics.fallbackTaskNames[0]").value("rewards"))
            .andExpect(jsonPath("$.diagnostics.fallbackTaskNames[1]").value("offers"))
            .andExpect(jsonPath("$.diagnostics.approximatedTaskNames").isEmpty())
            .andExpect(jsonPath("$.diagnostics.omittedTaskNames[0]").value("insights"))
            .andExpect(jsonPath("$.decisionTrace[2].selectedExecutionMode").value("EXECUTE_WITH_FALLBACK"))
            .andExpect(jsonPath("$.decisionTrace[3].selectedExecutionMode").value("EXECUTE_WITH_FALLBACK"))
            .andExpect(jsonPath("$.executionSummary", containsString("offers=EXECUTE_WITH_FALLBACK@12ms")));
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
    }
}
