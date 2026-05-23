package com.budgetflow.spring.aop;

import com.budgetflow.core.budget.DefaultExecutionBudget;
import com.budgetflow.core.context.BudgetContext;
import com.budgetflow.core.context.BudgetContextHolder;
import com.budgetflow.spring.annotation.LatencyBudget;
import com.budgetflow.spring.properties.BudgetFlowProperties;
import com.budgetflow.spring.util.DurationParser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.time.Duration;

@Aspect
public class LatencyBudgetAspect {
    private final BudgetFlowProperties properties;

    public LatencyBudgetAspect(BudgetFlowProperties properties) {
        this.properties = properties;
    }

    @Around("@annotation(latencyBudget)")
    public Object applyBudget(ProceedingJoinPoint joinPoint, LatencyBudget latencyBudget) throws Throwable {
        Duration budget = parseBudget(latencyBudget.value());
        BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(budget)));

        try {
            return joinPoint.proceed();
        } finally {
            BudgetContextHolder.clear();
        }
    }

    private Duration parseBudget(String annotationValue) {
        if (annotationValue == null || annotationValue.isBlank()) {
            return properties.getDefaultBudget();
        }

        return DurationParser.parse(annotationValue);
    }
}
