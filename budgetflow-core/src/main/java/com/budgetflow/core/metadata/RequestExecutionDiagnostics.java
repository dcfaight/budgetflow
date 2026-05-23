package com.budgetflow.core.metadata;

import com.budgetflow.core.api.TaskResult;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.policy.DecisionTraceEntry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record RequestExecutionDiagnostics(
    Duration totalRequestBudget,
    Duration remainingRequestBudget,
    boolean degraded,
    List<String> omittedTaskNames,
    List<String> fallbackTaskNames,
    List<String> approximatedTaskNames
) {
    public RequestExecutionDiagnostics {
        totalRequestBudget = totalRequestBudget == null ? Duration.ZERO : totalRequestBudget;
        remainingRequestBudget = remainingRequestBudget == null ? Duration.ZERO : remainingRequestBudget;
        omittedTaskNames = List.copyOf(omittedTaskNames);
        fallbackTaskNames = List.copyOf(fallbackTaskNames);
        approximatedTaskNames = List.copyOf(approximatedTaskNames);
    }

    public static RequestExecutionDiagnostics from(
        Map<String, TaskResult<?>> taskResults,
        List<DecisionTraceEntry> decisionTrace,
        Duration totalRequestBudget,
        Duration remainingRequestBudget
    ) {
        Set<String> omitted = new LinkedHashSet<>();
        Set<String> fallback = new LinkedHashSet<>();
        Set<String> approximated = new LinkedHashSet<>();

        for (Map.Entry<String, TaskResult<?>> entry : taskResults.entrySet()) {
            String taskName = entry.getKey();
            TaskResult<?> taskResult = entry.getValue();
            if (taskResult.omitted() || taskResult.executionMode() == ExecutionMode.OMIT) {
                omitted.add(taskName);
                continue;
            }
            if (taskResult.executionMode() == ExecutionMode.EXECUTE_WITH_FALLBACK) {
                fallback.add(taskName);
                continue;
            }
            if (taskResult.executionMode() == ExecutionMode.EXECUTE_APPROXIMATE) {
                approximated.add(taskName);
            }
        }

        for (DecisionTraceEntry entry : decisionTrace) {
            if (entry.selectedExecutionMode() == ExecutionMode.OMIT) {
                omitted.add(entry.taskName());
                continue;
            }
            if (entry.selectedExecutionMode() == ExecutionMode.EXECUTE_WITH_FALLBACK) {
                fallback.add(entry.taskName());
                continue;
            }
            if (entry.selectedExecutionMode() == ExecutionMode.EXECUTE_APPROXIMATE) {
                approximated.add(entry.taskName());
            }
        }

        return new RequestExecutionDiagnostics(
            totalRequestBudget,
            remainingRequestBudget,
            !omitted.isEmpty() || !fallback.isEmpty() || !approximated.isEmpty(),
            new ArrayList<>(omitted),
            new ArrayList<>(fallback),
            new ArrayList<>(approximated)
        );
    }
}
