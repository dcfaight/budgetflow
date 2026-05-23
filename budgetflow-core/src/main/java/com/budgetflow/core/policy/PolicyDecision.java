package com.budgetflow.core.policy;

import java.util.List;

public record PolicyDecision(
    List<TaskExecutionDirective> directives,
    boolean degraded,
    List<String> degradationReasons,
    List<DecisionTraceEntry> decisionTrace
) {
}
