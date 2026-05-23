package com.budgetflow.core.metadata;

import com.budgetflow.core.policy.DecisionTraceEntry;

import java.util.List;
import java.util.stream.Collectors;

public final class RequestExecutionDiagnosticsFormatter {
    private RequestExecutionDiagnosticsFormatter() {
    }

    public static String formatSummary(RequestExecutionDiagnostics diagnostics, List<DecisionTraceEntry> decisionTrace) {
        String traceSummary = decisionTrace.stream()
            .map(entry -> entry.taskName() + "=" + entry.selectedExecutionMode() + "(" + entry.reason() + ")")
            .collect(Collectors.joining(", "));
        return String.format(
            "budget[totalMs=%d,remainingMs=%d] degraded=%s omitted=%s fallback=%s approximated=%s trace=[%s]",
            diagnostics.totalRequestBudget().toMillis(),
            diagnostics.remainingRequestBudget().toMillis(),
            diagnostics.degraded(),
            diagnostics.omittedTaskNames(),
            diagnostics.fallbackTaskNames(),
            diagnostics.approximatedTaskNames(),
            traceSummary
        );
    }
}
