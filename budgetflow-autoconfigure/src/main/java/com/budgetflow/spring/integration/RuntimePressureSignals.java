package com.budgetflow.spring.integration;

/**
 * Optional adapter interface for supplying runtime pressure signals to BudgetFlow.
 *
 * <p>Applications can provide a single bean of this type to bridge existing
 * infrastructure/runtime metrics into BudgetFlow's {@code SystemPressureProvider}
 * abstraction without coupling to a specific metrics library.
 */
public interface RuntimePressureSignals {
    double executorUtilization();

    double dbPressure();

    double downstreamPressure();
}
