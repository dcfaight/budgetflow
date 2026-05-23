package com.budgetflow.demo.fintech.dashboard;

import org.springframework.stereotype.Component;

@Component
public class InsightsClient {
    private final SimulationSupport simulationSupport;

    public InsightsClient(SimulationSupport simulationSupport) {
        this.simulationSupport = simulationSupport;
    }

    public SpendingInsights getInsights(String accountId) {
        simulationSupport.delay(140);
        return new SpendingInsights("Spending on dining is up 8% week-over-week.");
    }
}
