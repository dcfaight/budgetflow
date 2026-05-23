package com.budgetflow.demo.fintech.dashboard;

import org.springframework.stereotype.Component;

@Component
public class RewardsClient {
    private final SimulationSupport simulationSupport;

    public RewardsClient(SimulationSupport simulationSupport) {
        this.simulationSupport = simulationSupport;
    }

    public RewardsSummary getRewards(String accountId) {
        simulationSupport.delay(90);
        return new RewardsSummary(2450);
    }
}
