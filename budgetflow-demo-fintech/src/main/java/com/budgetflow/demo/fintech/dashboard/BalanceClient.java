package com.budgetflow.demo.fintech.dashboard;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BalanceClient {
    private final SimulationSupport simulationSupport;

    public BalanceClient(SimulationSupport simulationSupport) {
        this.simulationSupport = simulationSupport;
    }

    public Balance getBalance(String accountId) {
        simulationSupport.delay(40);
        return new Balance(accountId, BigDecimal.valueOf(1024.56));
    }
}
