package com.budgetflow.core.metadata;

import com.budgetflow.core.policy.DecisionTraceEntry;

import java.util.List;
import java.util.stream.Collectors;

public final class RequestExecutionDiagnosticsFormatter {
    private RequestExecutionDiagnosticsFormatter() {
    }

    /**
     * Formats a step-by-step agent turn summary from the decision trace.
     * <p>
     * Each line shows one work item's name, execution mode, planned latency, and importance.
     * Degraded steps are annotated with a short note so the summary is self-explanatory
     * without needing to read the full policy trace.
     * <p>
     * This is designed for display alongside agent-oriented work built with
     * {@link com.budgetflow.core.api.AgentWorkSpec}, but it works for any request trace.
     */
    public static String formatAgentSteps(RequestExecutionDiagnostics diagnostics, List<DecisionTraceEntry> decisionTrace) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Agent turn: budget=%dms, remaining=%dms, degraded=%s%n",
            diagnostics.totalRequestBudget().toMillis(),
            diagnostics.remainingRequestBudget().toMillis(),
            diagnostics.degraded()));
        for (DecisionTraceEntry entry : decisionTrace) {
            String modeLabel = switch (entry.selectedExecutionMode()) {
                case EXECUTE -> "NORMAL";
                case EXECUTE_WITH_FALLBACK -> "FALLBACK";
                case EXECUTE_APPROXIMATE -> "APPROXIMATE";
                case OMIT -> "OMIT";
            };
            String latency = entry.selectedExecutionMode() == com.budgetflow.core.classification.ExecutionMode.OMIT
                ? "--"
                : entry.plannedExecutionLatency().toMillis() + "ms";
            String note = switch (entry.selectedExecutionMode()) {
                case EXECUTE_WITH_FALLBACK -> "  ← fallback used";
                case EXECUTE_APPROXIMATE -> "  ← approximate result";
                case OMIT -> "  ← omitted";
                default -> "";
            };
            sb.append(String.format("  %-26s %-11s %-6s [%s]%s%n",
                entry.taskName(),
                modeLabel,
                latency,
                entry.taskImportance().name().toLowerCase(),
                note));
        }
        return sb.toString().stripTrailing();
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
