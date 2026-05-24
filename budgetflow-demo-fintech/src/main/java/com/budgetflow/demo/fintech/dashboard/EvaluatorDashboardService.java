package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveRequest;
import com.budgetflow.core.api.AdaptiveRequestResult;
import com.budgetflow.core.budget.DefaultExecutionBudget;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.context.BudgetContext;
import com.budgetflow.core.context.BudgetContextHolder;
import com.budgetflow.core.execution.DefaultAdaptiveExecutor;
import com.budgetflow.core.metadata.RequestExecutionDiagnostics;
import com.budgetflow.core.metadata.RequestExecutionDiagnosticsFormatter;
import com.budgetflow.core.policy.DecisionTraceEntry;
import com.budgetflow.core.policy.DefaultBudgetPolicyEngine;
import com.budgetflow.core.policy.PlannerPolicyProfile;
import com.budgetflow.demo.fintech.benchmark.DashboardBenchmarkScenario;
import com.budgetflow.demo.fintech.benchmark.DashboardBenchmarkSummary;
import com.budgetflow.demo.fintech.benchmark.DashboardComparisonHarness;
import com.budgetflow.demo.fintech.benchmark.DashboardScenarioPack;
import com.budgetflow.demo.fintech.benchmark.PressureScenarios;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class EvaluatorDashboardService {
    private static final String ACCOUNT_ID = "acc-123";
    private static final List<String> PACK_NAMES = List.of("default", "extended", "realism", "policy", "adoption");
    private static final List<PlannerPolicyProfile> DEFAULT_COMPARE_PROFILES = List.of(
        PlannerPolicyProfile.BALANCED,
        PlannerPolicyProfile.CONTINUITY,
        PlannerPolicyProfile.EFFICIENCY
    );

    public String render(
        String requestedPackName,
        String requestedScenarioName,
        String requestedProfileName,
        String requestedCompareProfiles
    ) {
        List<String> notes = new ArrayList<>();
        DashboardScenarioPack pack = resolvePack(requestedPackName, notes);
        DashboardBenchmarkScenario scenario = resolveScenario(pack, requestedScenarioName, notes);
        PlannerPolicyProfile profile = resolveProfile(requestedProfileName, "profile", notes);
        List<PlannerPolicyProfile> compareProfiles = resolveCompareProfiles(requestedCompareProfiles, profile, notes);

        List<DashboardBenchmarkSummary> comparisonSummaries;
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(noDelaySimulationSupport())) {
            comparisonSummaries = harness.run(List.of(scenario), compareProfiles);
        }
        ScenarioExecution execution = executeScenario(scenario, profile);
        return renderHtml(pack, scenario, profile, compareProfiles, comparisonSummaries, execution, notes);
    }

    private DashboardScenarioPack resolvePack(String requestedPackName, List<String> notes) {
        String candidate = requestedPackName == null || requestedPackName.isBlank() ? "default" : requestedPackName;
        try {
            return PressureScenarios.packNamed(candidate);
        } catch (IllegalArgumentException exception) {
            notes.add("Unknown pack '" + candidate + "'. Showing default.");
            return PressureScenarios.defaultPack();
        }
    }

    private DashboardBenchmarkScenario resolveScenario(
        DashboardScenarioPack pack,
        String requestedScenarioName,
        List<String> notes
    ) {
        if (requestedScenarioName == null || requestedScenarioName.isBlank()) {
            return pack.scenarios().get(0);
        }
        return pack.scenarios().stream()
            .filter(scenario -> scenario.name().equals(requestedScenarioName))
            .findFirst()
            .orElseGet(() -> {
                notes.add("Unknown scenario '" + requestedScenarioName + "' for pack '" + pack.name() + "'.");
                return pack.scenarios().get(0);
            });
    }

    private PlannerPolicyProfile resolveProfile(String requestedProfileName, String fieldName, List<String> notes) {
        try {
            return PlannerPolicyProfile.fromConfigName(requestedProfileName);
        } catch (IllegalArgumentException exception) {
            notes.add("Unknown " + fieldName + " '" + requestedProfileName + "'. Using balanced.");
            return PlannerPolicyProfile.BALANCED;
        }
    }

    private List<PlannerPolicyProfile> resolveCompareProfiles(
        String requestedCompareProfiles,
        PlannerPolicyProfile selectedProfile,
        List<String> notes
    ) {
        if (requestedCompareProfiles == null || requestedCompareProfiles.isBlank()) {
            return ensureSelectedProfile(DEFAULT_COMPARE_PROFILES, selectedProfile);
        }
        LinkedHashSet<PlannerPolicyProfile> parsed = Arrays.stream(requestedCompareProfiles.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(profileName -> parseCompareProfile(profileName, notes))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (parsed.isEmpty()) {
            notes.add("No valid compareProfiles supplied. Using balanced, continuity, efficiency.");
            return ensureSelectedProfile(DEFAULT_COMPARE_PROFILES, selectedProfile);
        }
        return ensureSelectedProfile(List.copyOf(parsed), selectedProfile);
    }

    private PlannerPolicyProfile parseCompareProfile(String profileName, List<String> notes) {
        try {
            return PlannerPolicyProfile.fromConfigName(profileName);
        } catch (IllegalArgumentException exception) {
            notes.add("Ignoring unknown compare profile '" + profileName + "'.");
            return null;
        }
    }

    private List<PlannerPolicyProfile> ensureSelectedProfile(
        List<PlannerPolicyProfile> compareProfiles,
        PlannerPolicyProfile selectedProfile
    ) {
        LinkedHashSet<PlannerPolicyProfile> resolved = new LinkedHashSet<>(compareProfiles);
        resolved.add(selectedProfile);
        return List.copyOf(resolved);
    }

    private ScenarioExecution executeScenario(
        DashboardBenchmarkScenario scenario,
        PlannerPolicyProfile profile
    ) {
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        SimulationSupport noDelay = noDelaySimulationSupport();
        try {
            BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(scenario.requestBudget())));
            AdaptiveRequest request = DashboardTaskSpecs.forAccount(
                ACCOUNT_ID,
                new BalanceClient(noDelay),
                new TransactionClient(noDelay),
                new RewardsClient(noDelay),
                new OffersClient(noDelay),
                new InsightsClient(noDelay)
            );
            AdaptiveRequestResult result = request.execute(
                new DefaultAdaptiveExecutor(
                    executorService,
                    new DefaultBudgetPolicyEngine(profile),
                    scenario::pressureSnapshot
                )
            ).toCompletableFuture().join();
            RequestExecutionDiagnostics diagnostics = result.diagnostics();
            return new ScenarioExecution(
                diagnostics,
                result.decisionTrace(),
                RequestExecutionDiagnosticsFormatter.formatSummary(diagnostics, result.decisionTrace())
            );
        } finally {
            BudgetContextHolder.clear();
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
        }
    }

    private SimulationSupport noDelaySimulationSupport() {
        return new SimulationSupport() {
            @Override
            public void delay(long millis) {
            }
        };
    }

    private String renderHtml(
        DashboardScenarioPack pack,
        DashboardBenchmarkScenario selectedScenario,
        PlannerPolicyProfile selectedProfile,
        List<PlannerPolicyProfile> compareProfiles,
        List<DashboardBenchmarkSummary> comparisonSummaries,
        ScenarioExecution execution,
        List<String> notes
    ) {
        String compareProfilesParam = compareProfiles.stream()
            .map(PlannerPolicyProfile::configName)
            .collect(Collectors.joining(","));
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><title>BudgetFlow evaluator dashboard</title>")
            .append("<style>")
            .append("body{font-family:system-ui,-apple-system,sans-serif;margin:24px;color:#111;}h1,h2,h3{margin:0 0 8px;}h1{font-size:24px;}h2{font-size:19px;}h3{font-size:16px;}")
            .append(".muted{color:#555;font-size:14px;} .row{display:flex;gap:12px;flex-wrap:wrap;} .card{border:1px solid #d8d8d8;border-radius:8px;padding:12px;min-width:210px;background:#fafafa;}")
            .append(".warn{background:#fff4e5;border:1px solid #f0c36d;padding:10px;border-radius:8px;margin:12px 0;}")
            .append("table{border-collapse:collapse;width:100%;margin-top:8px;}th,td{border:1px solid #d9d9d9;padding:6px 8px;text-align:left;vertical-align:top;font-size:13px;}th{background:#f3f3f3;}")
            .append("code{background:#f3f3f3;padding:1px 4px;border-radius:4px;} .pill{padding:2px 7px;border-radius:10px;background:#eef2ff;font-size:12px;margin-right:6px;display:inline-block;}")
            .append("ul{margin:6px 0 0 16px;padding:0;}li{margin:3px 0;}a{color:#0b57d0;text-decoration:none;}a:hover{text-decoration:underline;}")
            .append("</style></head><body>");

        html.append("<h1>BudgetFlow evaluator dashboard (prototype)</h1>")
            .append("<p class='muted'>Lightweight visual evaluation surface for scenario exploration and planner explainability. ")
            .append("Use this for demo/evaluation interpretation, not production operations.</p>");

        if (!notes.isEmpty()) {
            html.append("<div class='warn'><strong>Input notes:</strong><ul>");
            for (String note : notes) {
                html.append("<li>").append(escape(note)).append("</li>");
            }
            html.append("</ul></div>");
        }

        html.append("<div class='card'><strong>How to read this page</strong><ul>")
            .append("<li>Start with scenario intent and what to observe.</li>")
            .append("<li>Use the outcome summary to see executed vs degraded/omitted work under the selected profile.</li>")
            .append("<li>Use planner trace rows to inspect why tasks changed (<code>layer</code>, <code>fit</code>, <code>savings</code>).</li>")
            .append("<li>Use profile comparison as directional evidence only, not benchmark proof.</li>")
            .append("</ul></div>");

        html.append("<h2>Scenario packs</h2><div>");
        for (String packName : PACK_NAMES) {
            boolean active = packName.equals(pack.name());
            html.append("<span class='pill'>")
                .append(active ? "<strong>" : "")
                .append("<a href='/dashboard/evaluator?pack=").append(url(packName))
                .append("&scenario=&profile=").append(url(selectedProfile.configName()))
                .append("&compareProfiles=").append(url(compareProfilesParam)).append("'>")
                .append(escape(packName))
                .append("</a>")
                .append(active ? " (selected)</strong>" : "")
                .append("</span>");
        }
        html.append("</div><p class='muted'>").append(escape(pack.description())).append("</p>");

        html.append("<h2>Scenarios in pack: ").append(escape(pack.name())).append("</h2><ul>");
        for (DashboardBenchmarkScenario scenario : pack.scenarios()) {
            boolean active = selectedScenario.name().equals(scenario.name());
            html.append("<li>")
                .append(active ? "<strong>" : "")
                .append("<a href='/dashboard/evaluator?pack=").append(url(pack.name()))
                .append("&scenario=").append(url(scenario.name()))
                .append("&profile=").append(url(selectedProfile.configName()))
                .append("&compareProfiles=").append(url(compareProfilesParam)).append("'>")
                .append(escape(scenario.displayName()))
                .append("</a>")
                .append(active ? " (selected)</strong>" : "")
                .append(" — ").append(escape(scenario.narrative()))
                .append("</li>");
        }
        html.append("</ul>");

        html.append("<h2>Selected scenario</h2>")
            .append("<div class='row'>")
            .append(card("Scenario", escape(selectedScenario.displayName())))
            .append(card("Focus", escape(selectedScenario.evaluationFocus())))
            .append(card("Observe", escape(selectedScenario.whatToObserve())))
            .append(card("Pattern", escape(selectedScenario.realWorldPattern())))
            .append("</div>")
            .append("<div class='row'>")
            .append(card("Budget profile", escape(selectedScenario.budgetProfile())))
            .append(card("Pressure profile", escape(selectedScenario.pressureProfile())))
            .append(card("Request budget", selectedScenario.requestBudget().toMillis() + "ms"))
            .append(card("Pressure signals", escape(selectedScenario.pressureSummary())))
            .append("</div>")
            .append("<p><strong>Interpretation guidance:</strong> ")
            .append(escape(selectedScenario.interpretationGuidance()))
            .append("</p>");

        html.append("<h2>Profile selection</h2><div>");
        for (PlannerPolicyProfile profile : PlannerPolicyProfile.values()) {
            boolean active = profile == selectedProfile;
            html.append("<span class='pill'>")
                .append(active ? "<strong>" : "")
                .append("<a href='/dashboard/evaluator?pack=").append(url(pack.name()))
                .append("&scenario=").append(url(selectedScenario.name()))
                .append("&profile=").append(url(profile.configName()))
                .append("&compareProfiles=").append(url(compareProfilesParam)).append("'>")
                .append(escape(profile.configName()))
                .append("</a>")
                .append(active ? " (selected)</strong>" : "")
                .append("</span>");
        }
        html.append("</div><p class='muted'>Selected profile intent: ")
            .append(escape(selectedProfile.intent()))
            .append("</p>");

        int totalTasks = execution.decisionTrace().size();
        int executed = (int) execution.decisionTrace().stream()
            .filter(entry -> entry.selectedExecutionMode() != ExecutionMode.OMIT)
            .count();
        int degraded = execution.diagnostics().fallbackTaskNames().size()
            + execution.diagnostics().approximatedTaskNames().size()
            + execution.diagnostics().omittedTaskNames().size();

        html.append("<h2>Outcome summary (selected profile)</h2>")
            .append("<div class='row'>")
            .append(card("Tasks executed", executed + " / " + totalTasks))
            .append(card("Degraded or omitted tasks", String.valueOf(degraded)))
            .append(card("Request degraded", String.valueOf(execution.diagnostics().degraded())))
            .append(card("Remaining budget", execution.diagnostics().remainingRequestBudget().toMillis() + "ms"))
            .append("</div>")
            .append("<div class='row'>")
            .append(card("Fallback tasks", formatList(execution.diagnostics().fallbackTaskNames())))
            .append(card("Approximated tasks", formatList(execution.diagnostics().approximatedTaskNames())))
            .append(card("Omitted tasks", formatList(execution.diagnostics().omittedTaskNames())))
            .append("</div>")
            .append("<p><strong>Execution summary:</strong> <code>")
            .append(escape(execution.executionSummary()))
            .append("</code></p>");

        html.append("<h2>Planner trace / explanation</h2>")
            .append("<table><thead><tr>")
            .append("<th>Task</th><th>Importance</th><th>Selected mode</th><th>Planned latency</th>")
            .append("<th>Allocated budget</th><th>pressure</th><th>layer</th><th>fit</th><th>savings</th><th>Reason</th>")
            .append("</tr></thead><tbody>");
        for (DecisionTraceEntry entry : execution.decisionTrace()) {
            Map<String, String> reasonFields = parseReasonFields(entry.reason());
            html.append("<tr>")
                .append("<td>").append(escape(entry.taskName())).append("</td>")
                .append("<td>").append(escape(entry.taskImportance().name())).append("</td>")
                .append("<td>").append(escape(entry.selectedExecutionMode().name())).append("</td>")
                .append("<td>").append(entry.plannedExecutionLatency().toMillis()).append("ms</td>")
                .append("<td>").append(entry.allocatedBudget().toMillis()).append("ms</td>")
                .append("<td>").append(escape(reasonFields.getOrDefault("pressure", "-"))).append("</td>")
                .append("<td>").append(escape(reasonFields.getOrDefault("layer", "-"))).append("</td>")
                .append("<td>").append(escape(reasonFields.getOrDefault("fit", "-"))).append("</td>")
                .append("<td>").append(escape(reasonFields.getOrDefault("savings", "-"))).append("</td>")
                .append("<td><code>").append(escape(entry.reason())).append("</code></td>")
                .append("</tr>");
        }
        html.append("</tbody></table>");

        html.append("<h2>Profile comparison (selected scenario)</h2>")
            .append("<p class='muted'>Includes naive_parallel and selected adaptive profile set: ")
            .append(escape(compareProfilesParam))
            .append("</p>")
            .append("<table><thead><tr>")
            .append("<th>Strategy</th><th>Profile</th><th>Executed</th><th>Degraded</th>")
            .append("<th>Projected work</th><th>Omitted</th><th>Fallback</th><th>Approximate</th><th>Why</th>")
            .append("</tr></thead><tbody>");
        for (DashboardBenchmarkSummary summary : comparisonSummaries.stream()
            .sorted(Comparator.comparing(DashboardBenchmarkSummary::executionStrategy)
                .thenComparing(DashboardBenchmarkSummary::policyProfile))
            .toList()) {
            html.append("<tr>")
                .append("<td>").append(escape(summary.executionStrategy())).append("</td>")
                .append("<td>").append(escape(summary.policyProfile())).append("</td>")
                .append("<td>").append(summary.totalTasksExecuted()).append("</td>")
                .append("<td>").append(summary.degraded()).append("</td>")
                .append("<td>").append(summary.projectedWork().toMillis()).append("ms / budget ")
                .append(summary.requestBudget().toMillis()).append("ms</td>")
                .append("<td>").append(escape(formatList(summary.omittedTasks()))).append("</td>")
                .append("<td>").append(escape(formatList(summary.fallbackTasks()))).append("</td>")
                .append("<td>").append(escape(formatList(summary.approximatedTasks()))).append("</td>")
                .append("<td>").append(escape(formatList(summary.degradationReasons()))).append("</td>")
                .append("</tr>");
        }
        html.append("</tbody></table>");

        html.append("<p class='muted'>Prototype reminder: this dashboard is an evaluator surface that helps inspect policy behavior. ")
            .append("Treat it as scenario evidence, not production observability or benchmark certification.</p>");

        html.append("</body></html>");
        return html.toString();
    }

    private Map<String, String> parseReasonFields(String reason) {
        int start = reason.indexOf('[');
        int end = reason.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return Map.of();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        String rawFields = reason.substring(start + 1, end);
        for (String token : rawFields.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            if (parts.length == 2) {
                fields.put(parts[0], parts[1]);
            }
        }
        return fields;
    }

    private String formatList(List<String> values) {
        return values == null || values.isEmpty() ? "-" : String.join(", ", values);
    }

    private String card(String title, String value) {
        return "<div class='card'><strong>" + title + "</strong><div>" + value + "</div></div>";
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private record ScenarioExecution(
        RequestExecutionDiagnostics diagnostics,
        List<DecisionTraceEntry> decisionTrace,
        String executionSummary
    ) {
    }
}
