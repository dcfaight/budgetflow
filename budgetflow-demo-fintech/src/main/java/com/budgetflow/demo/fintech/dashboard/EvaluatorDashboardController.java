package com.budgetflow.demo.fintech.dashboard;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvaluatorDashboardController {
    private final EvaluatorDashboardService evaluatorDashboardService;

    public EvaluatorDashboardController(EvaluatorDashboardService evaluatorDashboardService) {
        this.evaluatorDashboardService = evaluatorDashboardService;
    }

    @GetMapping(value = "/dashboard/evaluator", produces = MediaType.TEXT_HTML_VALUE)
    public String evaluatorDashboard(
        @RequestParam(name = "pack", defaultValue = "default") String packName,
        @RequestParam(name = "scenario", required = false) String scenarioName,
        @RequestParam(name = "profile", defaultValue = "balanced") String profileName,
        @RequestParam(name = "compareProfiles", defaultValue = "balanced,continuity,efficiency") String compareProfiles
    ) {
        return evaluatorDashboardService.render(packName, scenarioName, profileName, compareProfiles);
    }
}
