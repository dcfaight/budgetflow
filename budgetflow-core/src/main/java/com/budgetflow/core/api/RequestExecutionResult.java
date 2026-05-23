package com.budgetflow.core.api;

import com.budgetflow.core.policy.DecisionTraceEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RequestExecutionResult(
    Map<String, TaskResult<?>> taskResults,
    List<DecisionTraceEntry> decisionTrace
) {
    public RequestExecutionResult {
        taskResults = Map.copyOf(taskResults);
        decisionTrace = List.copyOf(decisionTrace);
    }

    static RequestExecutionResult fromTaskSpecs(List<TaskSpec<?>> taskSpecs, List<TaskResult<?>> results) {
        Map<String, TaskResult<?>> collected = new LinkedHashMap<>();
        for (int index = 0; index < taskSpecs.size(); index++) {
            collected.put(taskSpecs.get(index).taskName(), results.get(index));
        }
        return new RequestExecutionResult(collected, List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> TaskResult<T> taskResult(String taskName) {
        TaskResult<?> result = taskResults.get(taskName);
        if (result == null) {
            throw new IllegalArgumentException("No task result for task: " + taskName);
        }
        return (TaskResult<T>) result;
    }
}
