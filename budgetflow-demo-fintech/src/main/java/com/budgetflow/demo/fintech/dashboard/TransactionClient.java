package com.budgetflow.demo.fintech.dashboard;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class TransactionClient {
    private final SimulationSupport simulationSupport;

    public TransactionClient(SimulationSupport simulationSupport) {
        this.simulationSupport = simulationSupport;
    }

    public List<Transaction> getTransactions(String accountId) {
        simulationSupport.delay(65);
        return List.of(
            new Transaction("txn-1001", "Coffee Shop", BigDecimal.valueOf(4.95)),
            new Transaction("txn-1002", "Grocer", BigDecimal.valueOf(54.20)),
            new Transaction("txn-1003", "Transit", BigDecimal.valueOf(2.75))
        );
    }
}
