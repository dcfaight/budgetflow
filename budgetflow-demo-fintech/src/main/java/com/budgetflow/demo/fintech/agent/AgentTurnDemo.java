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

import java.time.Duration;

/**
 * Minimal demonstration of {@link AgentWorkSpec} in action.
 *
 * <p>Simulates a single agent turn consisting of three steps:
 * <ol>
 *   <li>{@code retrieve-context} — mandatory; must complete before the agent can answer</li>
 *   <li>{@code verify-sources} — important; improves reliability, degrades to a heuristic check</li>
 *   <li>{@code enrich-with-examples} — optional; adds supporting examples, can be omitted</li>
 * </ol>
 *
 * <p>Running under two scenarios demonstrates how the existing adaptive orchestration model
 * plans agent-style work items under latency budget pressure — without any new planning engine:
 * <ul>
 *   <li><b>Healthy</b> (300ms budget): all steps execute normally.</li>
 *   <li><b>Constrained</b> (70ms budget): verify-sources falls back to the cheaper heuristic
 *       path, and enrich-with-examples is omitted when the remaining budget cannot fit it.</li>
 * </ul>
 *
 * <p>This is demo code, not a general-purpose agent framework. It exists to prove that
 * {@link AgentWorkSpec} fits the current orchestration model cleanly. Run it with:
 * <pre>{@code
 * ./gradlew :budgetflow-demo-fintech:runAgentTurnDemo
 * }</pre>
 */
public final class AgentTurnDemo {

    static final TaskKey<String> RETRIEVE_CONTEXT_KEY    = TaskKey.of("retrieve-context");
    static final TaskKey<String> VERIFY_SOURCES_KEY      = TaskKey.of("verify-sources");
    static final TaskKey<String> ENRICH_WITH_EXAMPLES_KEY = TaskKey.of("enrich-with-examples");

    private AgentTurnDemo() {
    }

    /**
     * Builds one agent turn as an {@link AdaptiveRequest} using {@link AgentWorkSpec}.
     * <p>
     * All three work items are described in agent-oriented vocabulary and then adapted
     * to the existing {@code TaskSpec}-based execution model via {@link AgentWorkSpec#toTaskSpec()}.
     * No second planning engine is involved.
     */
    static AdaptiveRequest buildAgentTurn() {
        AgentWorkSpec<String> retrieve = AgentWorkSpec.mandatory(
            RETRIEVE_CONTEXT_KEY, Duration.ofMillis(50),
            () -> "[context] Retrieved 3 relevant sources");

        AgentWorkSpec<String> verify = AgentWorkSpec.important(
                VERIFY_SOURCES_KEY, Duration.ofMillis(30),
                () -> "[verify] Sources cross-checked: consistent")
            .withFallback(
                () -> "[verify-heuristic] Heuristic check passed",
                Duration.ofMillis(8));

        AgentWorkSpec<String> enrich = AgentWorkSpec.optional(
            ENRICH_WITH_EXAMPLES_KEY, Duration.ofMillis(45),
            () -> "[enrich] 2 supporting examples added");

        return AdaptiveRequest.builder()
            .agentWork(RETRIEVE_CONTEXT_KEY, retrieve)
            .agentWork(VERIFY_SOURCES_KEY, verify)
            .agentWork(ENRICH_WITH_EXAMPLES_KEY, enrich)
            .build();
    }

    /**
     * Runs one agent turn scenario and returns the result.
     *
     * @param budget   the request latency budget for this scenario
     * @param pressure the fixed system pressure snapshot for this scenario
     * @return the execution result including diagnostics and decision trace
     */
    static AdaptiveRequestResult runScenario(Duration budget, FixedPressureProvider pressure) {
        BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(budget)));
        try {
            DefaultAdaptiveExecutor executor = new DefaultAdaptiveExecutor(
                new DefaultBudgetPolicyEngine(), pressure);
            return buildAgentTurn()
                .execute(executor)
                .toCompletableFuture()
                .join();
        } finally {
            BudgetContextHolder.clear();
        }
    }

    public static void main(String[] args) {
        System.out.println("AgentWorkSpec demo — agent turn under adaptive orchestration");
        System.out.println("=============================================================");
        System.out.println("Steps: retrieve-context (mandatory) → verify-sources (important, fallback available)");
        System.out.println("                                     → enrich-with-examples (optional)");
        System.out.println();

        AdaptiveRequestResult healthy = runScenario(Duration.ofMillis(300), FixedPressureProvider.zero());
        System.out.println("Scenario: healthy (300ms budget, no system pressure)");
        System.out.println(RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            healthy.diagnostics(), healthy.decisionTrace()));

        System.out.println();

        AdaptiveRequestResult constrained = runScenario(Duration.ofMillis(70), FixedPressureProvider.zero());
        System.out.println("Scenario: constrained (70ms budget, no system pressure)");
        System.out.println(RequestExecutionDiagnosticsFormatter.formatAgentSteps(
            constrained.diagnostics(), constrained.decisionTrace()));

        System.out.println();
        System.out.println("The planner used the same execution model for both scenarios.");
        System.out.println("AgentWorkSpec compiled down to TaskSpec with no second planning engine.");
    }
}
