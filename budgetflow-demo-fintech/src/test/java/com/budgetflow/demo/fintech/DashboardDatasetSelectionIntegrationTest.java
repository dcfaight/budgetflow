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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {
        FintechDemoApplication.class,
        DashboardDatasetSelectionIntegrationTest.TestConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "budgetflow.demo.dataset=scenarios/overspending-user"
})
class DashboardDatasetSelectionIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardEndpointUsesConfiguredDatasetPack() throws Exception {
        mockMvc.perform(get("/api/accounts/acc-123/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance.available").value(186.42))
            .andExpect(jsonPath("$.transactions[0].merchant").value("QuickCart"))
            .andExpect(jsonPath("$.transactions[0].amount").value(198.45));
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
