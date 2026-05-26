package com.budgetflow.demo.fintech.agent;

import com.budgetflow.core.api.AdaptiveRequest;
import com.budgetflow.core.api.AdaptiveRequestResult;
import com.budgetflow.core.api.AgentWorkSpec;
import com.budgetflow.core.api.TaskKey;
import com.budgetflow.core.budget.DefaultExecutionBudget;
import com.budgetflow.core.context.BudgetContext;
import com.budgetflow.core.context.BudgetContextHolder;
import com.budgetflow.core.execution.DefaultAdaptiveExecutor;
import com.budgetflow.core.metadata.RequestExecutionDiagnosticsFormatter;
import com.budgetflow.core.policy.DefaultBudgetPolicyEngine;
import com.budgetflow.core.policy.FixedPressureProvider;
import com.budgetflow.core.policy.PlannerPolicyProfile;

import java.time.Duration;

/**
 * Demonstrates two boundary-case agent orchestration scenarios that complement
 * the baseline {@link AgentTurnDemo}:
 *
 * <h3>Scenario 1: Multi-step coordination</h3>
 * <p>
 * A flat agent turn with several parallel sub-agent work items and a consolidation step.
 * The work items represent a simple coordination pattern:
 * <ol>
 *   <li>{@code plan-coordination} — mandatory; the orchestrator decides what sub-tasks to run</li>
 *   <li>{@code fetch-source-a} — important; first sub-agent fetch, degrades to a cached path</li>
 *   <li>{@code fetch-source-b} — important; second sub-agent fetch, degrades to a cached path</li>
 *   <li>{@code consolidate-results} — important; merges both fetches, degrades to a partial summary</li>
 *   <li>{@code format-polished-response} — optional; output polish, degrades to a minimal response</li>
 * </ol>
 * Three sub-scenarios show how adaptive orchestration handles a coordination turn under
 * healthy conditions, sub-agent fallback pressure, and moderate mixed pressure.
 *
 * <h3>Scenario 2: Degraded-cascade / fallback-path scenario</h3>
 * <p>
 * Uses the same coordination work items under a severely tight budget and high system pressure
 * so that all important steps fall back simultaneously, demonstrating a full degradation cascade.
 * This boundary case verifies that the planner's cascade behavior is deterministic and traceable.
 *
 * <h3>Policy profile comparison</h3>
 * <p>
 * The coordination turn is also run under the {@code latency_first} profile to show how the same
 * work items behave differently when optional coverage is sacrificed for latency headroom.
 *
 * <p>Run with:
 * <pre>{@code
 * ./gradlew :budgetflow-demo-fintech:runAgentCoordinationDemo
 * }</pre>
 */
public final class AgentCoordinationDemo {

    // Work-item keys for the coordination turn
    static final TaskKey<String> PLAN_COORDINATION_KEY      = TaskKey.of("plan-coordination");
    static final TaskKey<String> FETCH_SOURCE_A_KEY         = TaskKey.of("fetch-source-a");
    static final TaskKey<String> FETCH_SOURCE_B_KEY         = TaskKey.of("fetch-source-b");
    static final TaskKey<String> CONSOLIDATE_RESULTS_KEY    = TaskKey.of("consolidate-results");
    static final TaskKey<String> FORMAT_POLISHED_KEY        = TaskKey.of("format-polished-response");

    private AgentCoordinationDemo() {
    }

    /**
     * Builds a coordination-style agent turn using {@link AgentWorkSpec}.
     *
     * <p>Work-item latency sketch:
     * <ul>
     *   <li>{@code plan-coordination}: 20ms mandatory</li>
     *   <li>{@code fetch-source-a}: 60ms important; fallback (cached) 12ms</li>
     *   <li>{@code fetch-source-b}: 55ms important; fallback (cached) 10ms</li>
     *   <li>{@code consolidate-results}: 45ms important; approximate (partial summary) 15ms</li>
     *   <li>{@code format-polished-response}: 35ms optional; approximate (minimal response) 8ms</li>
     * </ul>
     *
     * <p>Primary total: 215ms. Fully-degraded total: 20+12+10+15+8 = 65ms.
     */
    static AdaptiveRequest buildCoordinationTurn() {
        AgentWorkSpec<String> plan = AgentWorkSpec.mandatory(
            PLAN_COORDINATION_KEY, Duration.ofMillis(20),
            () -> "[plan] Coordination plan: fetch source-A and source-B, then consolidate");

        AgentWorkSpec<String> fetchA = AgentWorkSpec.important(
                FETCH_SOURCE_A_KEY, Duration.ofMillis(60),
                () -> "[fetch-a] Retrieved 5 documents from source A")
            .withFallback(
                () -> "[fetch-a-cached] Returned 3 cached documents from source A",
                Duration.ofMillis(12));

        AgentWorkSpec<String> fetchB = AgentWorkSpec.important(
                FETCH_SOURCE_B_KEY, Duration.ofMillis(55),
                () -> "[fetch-b] Retrieved 4 documents from source B")
            .withFallback(
                () -> "[fetch-b-cached] Returned 2 cached documents from source B",
                Duration.ofMillis(10));

        AgentWorkSpec<String> consolidate = AgentWorkSpec.important(
                CONSOLIDATE_RESULTS_KEY, Duration.ofMillis(45),
                () -> "[consolidate] Full merge of source-A and source-B results: 9 documents ranked")
            .withApproximate(
                () -> "[consolidate-partial] Partial merge: top-3 documents from each source",
                Duration.ofMillis(15));

        AgentWorkSpec<String> format = AgentWorkSpec.optional(
                FORMAT_POLISHED_KEY, Duration.ofMillis(35),
                () -> "[format] Polished narrative response with citations and follow-up suggestions")
            .withApproximate(
                () -> "[format-minimal] Minimal response with top result only",
                Duration.ofMillis(8));

        return AdaptiveRequest.builder()
            .agentWork(PLAN_COORDINATION_KEY, plan)
            .agentWork(FETCH_SOURCE_A_KEY, fetchA)
            .agentWork(FETCH_SOURCE_B_KEY, fetchB)
            .agentWork(CONSOLIDATE_RESULTS_KEY, consolidate)
            .agentWork(FORMAT_POLISHED_KEY, format)
            .build();
    }

    /**
     * Runs one scenario for the coordination turn under the given budget, pressure, and profile.
     */
    static AdaptiveRequestResult runCoordinationScenario(
        Duration budget,
        FixedPressureProvider pressure,
        PlannerPolicyProfile profile
    ) {
        BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(budget)));
        try {
            DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(
                new DefaultBudgetPolicyEngine(profile), pressure);
            return buildCoordinationTurn()
                .execute(executor)
                .toCompletableFuture()
                .join();
        } finally {
            BudgetContextHolder.clear();
        }
    }

    public static void main(String[] args) {
        System.out.println("AgentCoordinationDemo — coordination and degraded-cascade boundary cases");
        System.out.println("=========================================================================");
        System.out.println("Work items: plan-coordination (mandatory)");
        System.out.println("           → fetch-source-a (important, cached fallback)");
        System.out.println("           → fetch-source-b (important, cached fallback)");
        System.out.println("           → consolidate-results (important, partial approximate)");
        System.out.println("           → format-polished-response (optional, minimal approximate)");
        System.out.println();

        // --- Scenario 1: Multi-step coordination ---
        System.out.println("=== Scenario A: Multi-step coordination ===");
        System.out.println();

        // A1: Healthy — generous budget, no pressure; all work executes normally
        AdaptiveRequestResult healthy = runCoordinationScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero(), PlannerPolicyProfile.BALANCED);
        System.out.println("A1  healthy (300ms budget, no pressure, balanced)");
        System.out.println("    Intent: all coordination steps should execute; only optional polish may adapt");
        System.out.println("    Observe: plan+both fetches+consolidate execute at primary; polish adapts");
        System.out.println(indent(RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            healthy.diagnostics(), healthy.decisionTrace())));
        System.out.println();

        // A2: Sub-agent fallback — tight budget; fetches fall back to cached paths, consolidate approximates
        AdaptiveRequestResult subAgentFallback = runCoordinationScenario(
            Duration.ofMillis(100), FixedPressureProvider.zero(), PlannerPolicyProfile.BALANCED);
        System.out.println("A2  sub-agent fallback (100ms budget, no pressure, balanced)");
        System.out.println("    Intent: both fetch steps fall back to cheaper cached paths to fit budget");
        System.out.println("    Observe: fetch-a and fetch-b use fallback; consolidate approximates; polish omitted");
        System.out.println(indent(RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            subAgentFallback.diagnostics(), subAgentFallback.decisionTrace())));
        System.out.println();

        // A3: Mixed pressure — moderate budget, moderate pressure; fetches fall back
        FixedPressureProvider moderatePressure = FixedPressureProvider.of(0.55, 0.62, 0.58);
        AdaptiveRequestResult mixedPressure = runCoordinationScenario(
            Duration.ofMillis(180), moderatePressure, PlannerPolicyProfile.BALANCED);
        System.out.println("A3  mixed pressure (180ms budget, moderate pressure, balanced)");
        System.out.println("    Intent: pressure + partial budget tightness drives fallback on both fetches");
        System.out.println("    Observe: important steps prefer fallback paths; optional polish omitted");
        System.out.println(indent(RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            mixedPressure.diagnostics(), mixedPressure.decisionTrace())));
        System.out.println();

        // --- Scenario 2: Degraded-cascade ---
        System.out.println("=== Scenario B: Degraded-cascade / full fallback path ===");
        System.out.println();

        // B1: Severe joint constraint — tight budget + high pressure; all important steps cascade to fallback/approximate
        FixedPressureProvider highPressure = FixedPressureProvider.maximum();
        AdaptiveRequestResult degradedCascade = runCoordinationScenario(
            Duration.ofMillis(70), highPressure, PlannerPolicyProfile.BALANCED);
        System.out.println("B1  degraded-cascade (70ms budget, maximum pressure, balanced)");
        System.out.println("    Intent: severe joint constraint drives a full cascade of degradations");
        System.out.println("    Observe: all important steps fall back; optional step omitted; mandatory plan still executes");
        System.out.println(indent(RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            degradedCascade.diagnostics(), degradedCascade.decisionTrace())));
        System.out.println();

        // --- Policy profile comparison ---
        System.out.println("=== Scenario C: Profile comparison — balanced vs latency_first ===");
        System.out.println();

        // C1: Healthy scenario under balanced — optional polish adapts
        System.out.println("C1  healthy (300ms budget, no pressure) — balanced profile");
        System.out.println("    Observe: planner uses primary paths where they fit; optional step adapts gracefully");
        System.out.println(indent(RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            healthy.diagnostics(), healthy.decisionTrace())));
        System.out.println();

        // C2: Same scenario under latency_first — optional step omitted immediately
        AdaptiveRequestResult latencyFirst = runCoordinationScenario(
            Duration.ofMillis(300), FixedPressureProvider.zero(), PlannerPolicyProfile.LATENCY_FIRST);
        System.out.println("C2  healthy (300ms budget, no pressure) — latency_first profile");
        System.out.println("    Observe: latency_first omits optional polish immediately; important steps stay primary");
        System.out.println("    Profile intent: maximise remaining headroom; sacrifice optional coverage proactively");
        System.out.println(indent(RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            latencyFirst.diagnostics(), latencyFirst.decisionTrace())));
        System.out.println();

        // C3: Moderate pressure under latency_first — aggressive optional omission at lower threshold
        AdaptiveRequestResult latencyFirstPressure = runCoordinationScenario(
            Duration.ofMillis(200), moderatePressure, PlannerPolicyProfile.LATENCY_FIRST);
        System.out.println("C3  moderate pressure (200ms budget, moderate pressure) — latency_first profile");
        System.out.println("    Observe: latency_first omits optional work and prefers fallback paths earlier than balanced");
        System.out.println(indent(RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            latencyFirstPressure.diagnostics(), latencyFirstPressure.decisionTrace())));
        System.out.println();

        System.out.println("The same planning engine handled all scenarios; only the policy profile changed.");
        System.out.println("latency_first omits optional work at a lower threshold than balanced or efficiency.");
    }

    private static String indent(String text) {
        return "    " + text.replace(System.lineSeparator(), System.lineSeparator() + "    ");
    }
}
