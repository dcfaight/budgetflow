package com.budgetflow.core.policy;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DefaultBudgetPolicyEngine implements BudgetPolicyEngine {
    private static final double HIGH_PRESSURE = 0.85;

    @Override
    public PolicyDecision evaluate(PolicyEvaluationInput input) {
        List<TaskExecutionDirective> directives = new ArrayList<>();
        List<String> degradationReasons = new ArrayList<>();

        Duration remainingBudget = input.remainingBudget();
        int taskCount = Math.max(input.tasks().size(), 1);
        Duration perTaskBudget = remainingBudget.dividedBy(taskCount);

        for (TaskDescriptor task : input.tasks()) {
            ExecutionMode mode = chooseExecutionMode(task, input.pressureSnapshot(), remainingBudget);
            Duration allocated = minPositive(task.expectedLatency(), perTaskBudget);
            boolean degraded = mode != ExecutionMode.EXECUTE;

            if (degraded) {
                degradationReasons.add(task.taskName() + ": " + mode);
            }

            directives.add(new TaskExecutionDirective(
                task.taskName(),
                mode,
                allocated,
                false,
                degraded ? "pressure_or_budget_constraints" : "normal"
            ));
        }

        return new PolicyDecision(directives, !degradationReasons.isEmpty(), degradationReasons);
    }

    private ExecutionMode chooseExecutionMode(
        TaskDescriptor task,
        SystemPressureSnapshot snapshot,
        Duration remainingBudget
    ) {
        if (task.importance() == Importance.MANDATORY) {
            return ExecutionMode.EXECUTE;
        }

        boolean highPressure = snapshot.executorUtilization() >= HIGH_PRESSURE
            || snapshot.dbPressure() >= HIGH_PRESSURE
            || snapshot.downstreamPressure() >= HIGH_PRESSURE;

        boolean lowBudget = remainingBudget.compareTo(Duration.ofMillis(200)) < 0;

        if (task.importance() == Importance.IMPORTANT) {
            return (highPressure || lowBudget) && task.fallbackSupported()
                ? ExecutionMode.EXECUTE_WITH_FALLBACK
                : ExecutionMode.EXECUTE;
        }

        if (task.approximateSupported()) {
            return ExecutionMode.EXECUTE_APPROXIMATE;
        }

        return task.fallbackSupported() ? ExecutionMode.EXECUTE_WITH_FALLBACK : ExecutionMode.EXECUTE;
    }

    private Duration minPositive(Duration first, Duration second) {
        if (first == null || first.isZero() || first.isNegative()) {
            return second.isNegative() ? Duration.ZERO : second;
        }
        if (second == null || second.isZero() || second.isNegative()) {
            return first;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }
}
