package com.budgetflow.demo.fintech.dashboard;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OffersClient {
    private final SimulationSupport simulationSupport;

    public OffersClient(SimulationSupport simulationSupport) {
        this.simulationSupport = simulationSupport;
    }

    public List<Offer> getOffers(String accountId) {
        simulationSupport.delay(110);
        return List.of(
            new Offer("5% cash back on groceries"),
            new Offer("Travel bonus points weekend")
        );
    }
}
