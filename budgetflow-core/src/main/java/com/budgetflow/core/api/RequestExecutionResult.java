package com.budgetflow.core.api;

import com.budgetflow.core.metadata.RequestExecutionDiagnostics;
import com.budgetflow.core.policy.DecisionTraceEntry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record RequestExecutionResult(
    Map<String, TaskResult<?>> taskResults,
    List<DecisionTraceEntry> decisionTrace,
    RequestExecutionDiagnostics diagnostics
) {
    public RequestExecutionResult {
        taskResults = Map.copyOf(taskResults);
        decisionTrace = List.copyOf(decisionTrace);
    }

    public RequestExecutionResult(Map<String, TaskResult<?>> taskResults, List<DecisionTraceEntry> decisionTrace) {
        this(
            taskResults,
            decisionTrace,
            RequestExecutionDiagnostics.from(taskResults, decisionTrace, Duration.ZERO, Duration.ZERO)
        );
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
