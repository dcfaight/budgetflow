package com.budgetflow.demo.fintech.dashboard;

import java.math.BigDecimal;

public record Transaction(String id, String merchant, BigDecimal amount) {
}
