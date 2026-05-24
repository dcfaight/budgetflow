package com.budgetflow.core.metadata;

import com.budgetflow.core.policy.DecisionTraceEntry;

import java.util.List;
import java.util.stream.Collectors;

public final class RequestExecutionDiagnosticsFormatter {
    private RequestExecutionDiagnosticsFormatter() {
    }

    public static String formatSummary(RequestExecutionDiagnostics diagnostics, List<DecisionTraceEntry> decisionTrace) {
        String traceSummary = decisionTrace.stream()
            .map(entry -> entry.taskName()
                + "=" + entry.selectedExecutionMode()
                + "@"
                + entry.plannedExecutionLatency().toMillis()
                + "ms{"
                + compactReason(entry.reason())
                + "}")
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

    private static String compactReason(String reason) {
        int start = reason.indexOf('[');
        int end = reason.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return reason;
        }
        String action = reason.substring(0, start);
        java.util.Map<String, String> fields = java.util.Arrays.stream(reason.substring(start + 1, end).split(","))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .map(token -> token.split("=", 2))
            .filter(parts -> parts.length == 2)
            .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (first, second) -> first, java.util.LinkedHashMap::new));
        return "%s(policy=%s,layer=%s,fit=%s,savings=%s,pressure=%s,budget=%s,degrade_pref=%s)"
            .formatted(
                action,
                fields.getOrDefault("policy", "unknown"),
                fields.getOrDefault("layer", "unknown"),
                fields.getOrDefault("fit", "unknown"),
                fields.getOrDefault("savings", "unknown"),
                fields.getOrDefault("pressure", "unknown"),
                fields.getOrDefault("budget", "unknown"),
                fields.getOrDefault("degrade_pref", "unknown")
            );
    }
}
