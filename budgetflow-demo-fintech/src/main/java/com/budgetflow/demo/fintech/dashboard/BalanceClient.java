package com.budgetflow.demo.fintech.dashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BalanceClient {
    private final SimulationSupport simulationSupport;
    private final DemoDatasetCatalog demoDatasetCatalog;

    public BalanceClient(SimulationSupport simulationSupport) {
        this(simulationSupport, DemoDatasetCatalog.seedDefaultCatalog());
    }

    @Autowired
    public BalanceClient(SimulationSupport simulationSupport, DemoDatasetCatalog demoDatasetCatalog) {
        this.simulationSupport = simulationSupport;
        this.demoDatasetCatalog = demoDatasetCatalog;
    }

    public Balance getBalance(String accountId) {
        simulationSupport.delay(40);
        return demoDatasetCatalog.resolveBalance(accountId);
    }

    public Balance getBalance(String accountId, String datasetId) {
        simulationSupport.delay(40);
        return demoDatasetCatalog.resolveBalance(accountId, datasetId);
    }
}
