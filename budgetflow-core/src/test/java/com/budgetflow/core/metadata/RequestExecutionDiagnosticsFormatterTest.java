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
                "fallback_selected_by_policy[policy=balanced,layer=important_runtime_relief,pressure=high:downstream,budget=tight,degrade_pref=fallback,fit=degraded,savings=high]",
                Duration.ofMillis(90),
                Duration.ofMillis(10),
                Duration.ofMillis(60),
                Duration.ofMillis(210)
            ),
            new DecisionTraceEntry(
                "offers",
                Importance.OPTIONAL,
                ExecutionMode.EXECUTE_APPROXIMATE,
                "approximate_selected_by_policy[policy=balanced,layer=optional_mixed_degrade,pressure=high:downstream,budget=tight,degrade_pref=approximate,fit=degraded,savings=high]",
                Duration.ofMillis(110),
                Duration.ofMillis(8),
                Duration.ofMillis(40),
                Duration.ofMillis(150)
            ),
            new DecisionTraceEntry(
                "insights",
                Importance.OPTIONAL,
                ExecutionMode.OMIT,
                "omitted_by_policy[policy=balanced,layer=optional_mixed_omit,pressure=high:downstream,budget=tight,degrade_pref=approximate,fit=none,savings=high]",
                Duration.ofMillis(140),
                Duration.ZERO,
                Duration.ZERO,
                Duration.ofMillis(60)
            )
        );

        String formatted = RequestExecutionDiagnosticsFormatter.formatSummary(diagnostics, trace);
        assertEquals(
            "budget[totalMs=430,remainingMs=60] degraded=true omitted=[insights] fallback=[rewards] approximated=[offers] "
                + "trace=[rewards=EXECUTE_WITH_FALLBACK@10ms{fallback_selected_by_policy(policy=balanced,layer=important_runtime_relief,fit=degraded,savings=high,pressure=high:downstream,budget=tight,degrade_pref=fallback)}, "
                + "offers=EXECUTE_APPROXIMATE@8ms{approximate_selected_by_policy(policy=balanced,layer=optional_mixed_degrade,fit=degraded,savings=high,pressure=high:downstream,budget=tight,degrade_pref=approximate)}, "
                + "insights=OMIT@0ms{omitted_by_policy(policy=balanced,layer=optional_mixed_omit,fit=none,savings=high,pressure=high:downstream,budget=tight,degrade_pref=approximate)}]",
            formatted
        );
    }
}
