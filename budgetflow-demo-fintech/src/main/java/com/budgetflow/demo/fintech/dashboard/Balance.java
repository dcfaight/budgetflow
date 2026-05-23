package com.budgetflow.demo.fintech.dashboard;

import java.math.BigDecimal;

public record Balance(String accountId, BigDecimal available) {
}
