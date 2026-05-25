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
            .andExpect(content().string(containsString("Active dataset")))
            .andExpect(content().string(containsString("Scenario lab datasets")))
            .andExpect(content().string(containsString("Scenario playback controls")))
            .andExpect(content().string(containsString("Quick compare (current vs previous scenario dataset)")))
            .andExpect(content().string(containsString("Scenario walkthrough mode")))
            .andExpect(content().string(containsString("Narrative walkthrough panel")))
            .andExpect(content().string(containsString("Start here (first-time evaluator flow)")))
            .andExpect(content().string(containsString("Guided progression:")))
            .andExpect(content().string(containsString("Scenarios in pack: default")))
            .andExpect(content().string(containsString("Scenario storyline synthesis")))
            .andExpect(content().string(containsString("What changed across storyline?")))
            .andExpect(content().string(containsString("Compact analytics snapshot")))
            .andExpect(content().string(containsString("Decision and branch path view")))
            .andExpect(content().string(containsString("Planner lanes by importance")))
            .andExpect(content().string(containsString("Signal-to-mode summary (explicit)")))
            .andExpect(content().string(containsString("Planner trace / explanation")))
            .andExpect(content().string(containsString("Execution summary:")))
            .andExpect(content().string(containsString("Prototype reminder")));
    }

    @Test
    void evaluatorDashboardShowsProfileComparisonAndExplainabilityFields() throws Exception {
        mockMvc.perform(get("/dashboard/evaluator")
                .param("pack", "policy")
                .param("scenario", "moderate_budget_elevated_pressure")
                .param("compareScenarios", "constrained_budget_low_pressure,moderate_budget_elevated_pressure,tight_budget_moderate_db_pressure")
                .param("profile", "continuity")
                .param("compareProfiles", "balanced,continuity,efficiency")
                .param("dataset", "scenarios/high-subscription-load")
                .param("compareDatasets", "scenarios/overspending-user,scenarios/high-subscription-load")
                .param("walkthroughStep", "profile"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Moderate budget / elevated pressure")))
                .andExpect(content().string(containsString("Selected profile intent: Favors preserving optional signal coverage")))
            .andExpect(content().string(containsString("High subscription load")))
            .andExpect(content().string(containsString("What to look for")))
            .andExpect(content().string(containsString("Storyline compare set:")))
            .andExpect(content().string(containsString("Pack-level overview")))
            .andExpect(content().string(containsString("Budget fit (planned selected path)")))
            .andExpect(content().string(containsString("Profile recommendation (prototype guidance):")))
            .andExpect(content().string(containsString("Recommended next comparison:")))
            .andExpect(content().string(containsString("<th>pressure</th><th>layer</th><th>fit</th><th>savings</th>")))
            .andExpect(content().string(containsString("Delta vs balanced")))
            .andExpect(content().string(containsString("Compact visual diff")))
            .andExpect(content().string(containsString("Trace compression: changed decisions first")))
            .andExpect(content().string(containsString("budgetflow_adaptive")))
            .andExpect(content().string(containsString("continuity")))
            .andExpect(content().string(containsString("efficiency")));
    }
}
