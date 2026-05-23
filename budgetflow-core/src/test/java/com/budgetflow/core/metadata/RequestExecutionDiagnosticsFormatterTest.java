package com.budgetflow.core.metadata;

import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
import com.budgetflow.core.policy.DecisionTraceEntry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestExecutionDiagnosticsFormatterTest {

    @Test
    void formatSummaryRemainsStableForDiagnosticsContracts() {
        RequestExecutionDiagnostics diagnostics = new RequestExecutionDiagnostics(
            Duration.ofMillis(430),
            Duration.ofMillis(60),
            true,
            List.of("insights"),
            List.of("rewards"),
            List.of("offers")
        );
        List<DecisionTraceEntry> trace = List.of(
            new DecisionTraceEntry(
                "rewards",
                Importance.IMPORTANT,
                ExecutionMode.EXECUTE_WITH_FALLBACK,
                "fallback_selected_by_policy[pressure=high:downstream,budget=tight]",
                Duration.ofMillis(90),
                Duration.ofMillis(60),
                Duration.ofMillis(210)
            ),
            new DecisionTraceEntry(
                "offers",
                Importance.OPTIONAL,
                ExecutionMode.EXECUTE_APPROXIMATE,
                "approximate_selected_by_policy[pressure=high:downstream,budget=tight]",
                Duration.ofMillis(110),
                Duration.ofMillis(40),
                Duration.ofMillis(150)
            ),
            new DecisionTraceEntry(
                "insights",
                Importance.OPTIONAL,
                ExecutionMode.OMIT,
                "omitted_by_policy[pressure=high:downstream,budget=tight]",
                Duration.ofMillis(140),
                Duration.ZERO,
                Duration.ofMillis(60)
            )
        );

        String formatted = RequestExecutionDiagnosticsFormatter.formatSummary(diagnostics, trace);
        assertEquals(
            "budget[totalMs=430,remainingMs=60] degraded=true omitted=[insights] fallback=[rewards] approximated=[offers] "
                + "trace=[rewards=EXECUTE_WITH_FALLBACK(fallback_selected_by_policy[pressure=high:downstream,budget=tight]), "
                + "offers=EXECUTE_APPROXIMATE(approximate_selected_by_policy[pressure=high:downstream,budget=tight]), "
                + "insights=OMIT(omitted_by_policy[pressure=high:downstream,budget=tight])]",
            formatted
        );
    }
}
