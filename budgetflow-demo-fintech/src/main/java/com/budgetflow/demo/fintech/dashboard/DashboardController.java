package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.spring.annotation.LatencyBudget;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{id}/dashboard")
    @LatencyBudget("250ms")
    public DashboardResponse dashboard(@PathVariable("id") String accountId) {
        return dashboardService.getDashboard(accountId);
    }
}
