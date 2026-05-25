package com.budgetflow.demo.fintech.dashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionClient {
    private final SimulationSupport simulationSupport;
    private final DemoDatasetCatalog demoDatasetCatalog;

    public TransactionClient(SimulationSupport simulationSupport) {
        this(simulationSupport, DemoDatasetCatalog.seedDefaultCatalog());
    }

    @Autowired
    public TransactionClient(SimulationSupport simulationSupport, DemoDatasetCatalog demoDatasetCatalog) {
        this.simulationSupport = simulationSupport;
        this.demoDatasetCatalog = demoDatasetCatalog;
    }

    public List<Transaction> getTransactions(String accountId) {
        simulationSupport.delay(65);
        return demoDatasetCatalog.resolveTransactions(accountId);
    }
}
