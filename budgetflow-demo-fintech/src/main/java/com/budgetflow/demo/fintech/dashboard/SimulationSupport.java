package com.budgetflow.demo.fintech.dashboard;

import org.springframework.stereotype.Component;

@Component
public class SimulationSupport {
    public void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating downstream latency", interruptedException);
        }
    }
}
