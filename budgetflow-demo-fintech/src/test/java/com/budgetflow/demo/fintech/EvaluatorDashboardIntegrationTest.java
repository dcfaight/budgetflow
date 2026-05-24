package com.budgetflow.demo.fintech;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EvaluatorDashboardIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void evaluatorDashboardRendersScenarioOverviewAndPlannerTrace() throws Exception {
        mockMvc.perform(get("/dashboard/evaluator"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(containsString("BudgetFlow evaluator dashboard (prototype)")))
            .andExpect(content().string(containsString("Scenarios in pack: default")))
            .andExpect(content().string(containsString("Planner trace / explanation")))
            .andExpect(content().string(containsString("Execution summary:")))
            .andExpect(content().string(containsString("Prototype reminder")));
    }

    @Test
    void evaluatorDashboardShowsProfileComparisonAndExplainabilityFields() throws Exception {
        mockMvc.perform(get("/dashboard/evaluator")
                .param("pack", "policy")
                .param("scenario", "moderate_budget_elevated_pressure")
                .param("profile", "continuity")
                .param("compareProfiles", "balanced,continuity,efficiency"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Moderate budget / elevated pressure")))
                .andExpect(content().string(containsString("Selected profile intent: Favors preserving optional signal coverage")))
            .andExpect(content().string(containsString("<th>pressure</th><th>layer</th><th>fit</th><th>savings</th>")))
            .andExpect(content().string(containsString("budgetflow_adaptive")))
            .andExpect(content().string(containsString("continuity")))
            .andExpect(content().string(containsString("efficiency")));
    }
}
