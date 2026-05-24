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
    private static final String STRATEGY_ADAPTIVE = "budgetflow_adaptive";
    private static final String STRATEGY_NAIVE = "naive_parallel";
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
        Duration projectedWork = execution.decisionTrace().stream()
            .map(DecisionTraceEntry::plannedExecutionLatency)
            .reduce(Duration.ZERO, Duration::plus);
        long requestBudgetMs = selectedScenario.requestBudget().toMillis();
        long projectedWorkMs = projectedWork.toMillis();
        long remainingBudgetMs = execution.diagnostics().remainingRequestBudget().toMillis();

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><title>BudgetFlow evaluator dashboard</title>")
            .append("<style>")
            .append("body{font-family:Inter,system-ui,-apple-system,sans-serif;margin:20px auto;padding:0 18px;max-width:1240px;color:#131826;background:#f8fafc;}")
            .append("h1,h2,h3{margin:0 0 10px;color:#0f172a;}h1{font-size:29px;line-height:1.1;}h2{font-size:20px;margin-top:26px;}h3{font-size:16px;}")
            .append("p{line-height:1.45;}code{background:#eef2ff;padding:1px 5px;border-radius:4px;}")
            .append(".muted{color:#475569;font-size:14px;} .row{display:flex;gap:12px;flex-wrap:wrap;align-items:stretch;}")
            .append(".hero{background:#fff;border:1px solid #dbe2ef;border-radius:14px;padding:18px;box-shadow:0 1px 3px rgba(15,23,42,.06);} ")
            .append(".card{border:1px solid #dbe2ef;border-radius:12px;padding:12px;min-width:220px;background:#fff;box-shadow:0 1px 2px rgba(15,23,42,.04);}")
            .append(".card-emphasis{background:#f8fbff;border-color:#bfdbfe;} .card-tip{background:#f8fafc;border-color:#cbd5e1;}")
            .append(".warn{background:#fff7ed;border:1px solid #fdba74;padding:10px;border-radius:10px;margin:12px 0;color:#9a3412;}")
            .append(".tip{background:#f8fafc;border:1px solid #dbe2ef;padding:10px;border-radius:10px;margin:10px 0;}")
            .append("table{border-collapse:separate;border-spacing:0;width:100%;margin-top:10px;background:#fff;border:1px solid #dbe2ef;border-radius:12px;overflow:hidden;}")
            .append("th,td{border-bottom:1px solid #e2e8f0;padding:8px 9px;text-align:left;vertical-align:top;font-size:13px;}th{background:#f1f5f9;font-weight:600;color:#1e293b;}")
            .append("tbody tr:nth-child(even){background:#f8fafc;}tbody tr:last-child td{border-bottom:none;}")
            .append(".selected-row td{background:#eff6ff;} .trace-degraded td{background:#fff7ed;}")
            .append(".pill{padding:3px 8px;border-radius:999px;background:#e2e8f0;font-size:12px;margin-right:6px;display:inline-block;color:#334155;}")
            .append(".pill-active{background:#dbeafe;color:#1e3a8a;font-weight:600;} .pill-recommended{background:#dcfce7;color:#166534;font-weight:600;}")
            .append(".badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;font-weight:600;}")
            .append(".badge-ok{background:#dcfce7;color:#166534;} .badge-mid{background:#fef3c7;color:#92400e;} .badge-high{background:#fee2e2;color:#991b1b;}")
            .append(".mode{font-weight:600;} .mode-execute{color:#166534;} .mode-fallback{color:#92400e;} .mode-approximate{color:#9a3412;} .mode-omit{color:#991b1b;}")
            .append(".metric{min-width:280px;background:#fff;border:1px solid #dbe2ef;border-radius:12px;padding:10px;} .metric-label{font-size:13px;font-weight:600;}")
            .append(".bar{margin-top:8px;height:10px;background:#e2e8f0;border-radius:999px;overflow:hidden;} .bar-fill{height:100%;background:linear-gradient(90deg,#60a5fa,#2563eb);} .bar-fill.over{background:linear-gradient(90deg,#f59e0b,#dc2626);} ")
            .append(".decision-path{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px;} .decision-step{background:#fff;border:1px solid #dbe2ef;border-radius:10px;padding:7px 9px;font-size:12px;} .arrow{color:#94a3b8;font-size:12px;align-self:center;}")
            .append(".delta{font-weight:600;} .delta-up{color:#14532d;} .delta-down{color:#9a3412;} .delta-neutral{color:#475569;}")
            .append("ul,ol{margin:6px 0 0 18px;padding:0;}li{margin:4px 0;}a{color:#1d4ed8;text-decoration:none;}a:hover{text-decoration:underline;}")
            .append("</style></head><body>");

        html.append("<div class='hero'>")
            .append("<h1>BudgetFlow evaluator dashboard (prototype)</h1>")
            .append("<p class='muted'>Lightweight visual evaluation surface for scenario exploration and planner explainability. ")
            .append("Use this for demo/evaluation interpretation, not production operations.</p>")
            .append("<div class='row'>")
            .append(card("Current scenario", escape(selectedScenario.displayName()), "card-emphasis"))
            .append(card("Current profile", escape(selectedProfile.configName()), "card-emphasis"))
            .append(card("Request budget", requestBudgetMs + "ms", "card-emphasis"))
            .append(card("Planned work (selected profile)", projectedWorkMs + "ms", "card-emphasis"))
            .append("</div>")
            .append("</div>");

        if (!notes.isEmpty()) {
            html.append("<div class='warn'><strong>Input notes:</strong><ul>");
            for (String note : notes) {
                html.append("<li>").append(escape(note)).append("</li>");
            }
            html.append("</ul></div>");
        }

        html.append("<h2>Start here (first-time evaluator flow)</h2>")
            .append("<div class='row'>")
            .append("<div class='card card-tip'><strong>1) Choose a starting point</strong><ul>")
            .append("<li>Recommended first pack: <code>default</code> for control + constrained cases.</li>")
            .append("<li>If evaluating adoption storyline quickly: <code>adoption</code>.</li>")
            .append("<li>Use <code>policy</code> when profile tradeoff selection is your primary question.</li>")
            .append("</ul></div>")
            .append("<div class='card card-tip'><strong>2) Run one profile first</strong><ul>")
            .append("<li>Start with <code>balanced</code> unless you already know continuity/headroom priorities.</li>")
            .append("<li>Then compare <code>continuity</code> and <code>efficiency</code> for directional deltas.</li>")
            .append("</ul></div>")
            .append("<div class='card card-tip'><strong>3) Interpret conservatively</strong><ul>")
            .append("<li>Read budget-fit and degradation cues before drawing conclusions.</li>")
            .append("<li>Treat profile and strategy deltas as scenario evidence, not benchmark certification.</li>")
            .append("</ul></div>")
            .append("</div>")
            .append("<div class='tip'><strong>Guided progression:</strong> default → adoption → realism → policy. ")
            .append("Use <code>extended</code> when you need broader pressure variety in one pass.</div>");

        html.append("<h2>Scenario packs</h2><div>");
        for (String packName : PACK_NAMES) {
            boolean active = packName.equals(pack.name());
            html.append("<span class='pill ")
                .append(active ? "pill-active" : "")
                .append(" ")
                .append("default".equals(packName) ? "pill-recommended" : "")
                .append("'>")
                .append(active ? "<strong>" : "")
                .append("<a href='/dashboard/evaluator?pack=").append(url(packName))
                .append("&scenario=&profile=").append(url(selectedProfile.configName()))
                .append("&compareProfiles=").append(url(compareProfilesParam)).append("'>")
                .append(escape(packName))
                .append("</a>")
                .append(active ? " (selected)</strong>" : "")
                .append("default".equals(packName) && !active ? " (recommended first)" : "")
                .append("</span>");
        }
        html.append("</div><div class='row'>")
            .append(card("Pack description", escape(pack.description())))
            .append(card("Best for", escape(pack.bestFor())))
            .append(card("Suggested CLI run", "<code>" + escape(pack.suggestedCommand()) + "</code>"))
            .append("</div>");

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
                .append(pack.scenarios().indexOf(scenario) == 0 && !active ? " (recommended first in pack)" : "")
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
            .append("<div class='tip'><strong>Interpretation guidance:</strong> ")
            .append(escape(selectedScenario.interpretationGuidance()))
            .append("</div>");

        html.append("<h2>Profile selection</h2><div>");
        for (PlannerPolicyProfile profile : PlannerPolicyProfile.values()) {
            boolean active = profile == selectedProfile;
            html.append("<span class='pill ")
                .append(active ? "pill-active" : "")
                .append(profile == PlannerPolicyProfile.BALANCED ? " pill-recommended" : "")
                .append("'>")
                .append(active ? "<strong>" : "")
                .append("<a href='/dashboard/evaluator?pack=").append(url(pack.name()))
                .append("&scenario=").append(url(selectedScenario.name()))
                .append("&profile=").append(url(profile.configName()))
                .append("&compareProfiles=").append(url(compareProfilesParam)).append("'>")
                .append(escape(profile.configName()))
                .append("</a>")
                .append(active ? " (selected)</strong>" : "")
                .append(profile == PlannerPolicyProfile.BALANCED && !active ? " (recommended first)" : "")
                .append("</span>");
        }
        html.append("</div><div class='row'>");
        for (PlannerPolicyProfile profile : PlannerPolicyProfile.values()) {
            html.append(card(profile.displayName(), escape(profile.intent())));
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
            .append(card("Request degraded", degradationStateBadge(execution.diagnostics())))
            .append(card("Remaining budget", remainingBudgetMs + "ms"))
            .append("</div>")
            .append("<div class='row'>")
            .append(budgetMetric("Budget fit (planned selected path)", projectedWorkMs, requestBudgetMs))
            .append(budgetMetric("Remaining headroom after execution", Math.max(0, remainingBudgetMs), requestBudgetMs))
            .append("</div>")
            .append("<div class='row'>")
            .append(card("Fallback tasks", formatList(execution.diagnostics().fallbackTaskNames())))
            .append(card("Approximated tasks", formatList(execution.diagnostics().approximatedTaskNames())))
            .append(card("Omitted tasks", formatList(execution.diagnostics().omittedTaskNames())))
            .append("</div>")
            .append("<p><strong>Execution summary:</strong> <code>")
            .append(escape(execution.executionSummary()))
            .append("</code></p>");

        html.append("<h2>Decision path (lightweight visual summary)</h2>")
            .append("<div class='decision-path'>");
        for (int index = 0; index < execution.decisionTrace().size(); index++) {
            DecisionTraceEntry entry = execution.decisionTrace().get(index);
            html.append("<div class='decision-step'><strong>")
                .append(escape(entry.taskName()))
                .append("</strong><br>")
                .append("<span class='mode ")
                .append(modeClass(entry.selectedExecutionMode()))
                .append("'>")
                .append(escape(entry.selectedExecutionMode().name()))
                .append("</span> · ")
                .append(entry.plannedExecutionLatency().toMillis()).append("ms")
                .append("</div>");
            if (index < execution.decisionTrace().size() - 1) {
                html.append("<div class='arrow'>→</div>");
            }
        }
        html.append("</div>")
            .append("<p class='muted'>Use this as a quick path overview, then verify details in the planner trace table.</p>");

        html.append("<h2>Planner trace / explanation</h2>")
            .append("<table><thead><tr>")
            .append("<th>Task</th><th>Importance</th><th>Selected mode</th><th>Planned latency</th>")
            .append("<th>Allocated budget</th><th>pressure</th><th>layer</th><th>fit</th><th>savings</th><th>Reason</th>")
            .append("</tr></thead><tbody>");
        for (DecisionTraceEntry entry : execution.decisionTrace()) {
            Map<String, String> reasonFields = parseReasonFields(entry.reason());
            boolean traceDegraded = entry.selectedExecutionMode() != ExecutionMode.EXECUTE;
            html.append("<tr class='")
                .append(traceDegraded ? "trace-degraded" : "")
                .append("'>")
                .append("<td>").append(escape(entry.taskName())).append("</td>")
                .append("<td>").append(escape(entry.taskImportance().name())).append("</td>")
                .append("<td><span class='mode ")
                .append(modeClass(entry.selectedExecutionMode()))
                .append("'>")
                .append(escape(entry.selectedExecutionMode().name()))
                .append("</span></td>")
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

        DashboardBenchmarkSummary balancedAdaptive = comparisonSummaries.stream()
            .filter(summary -> STRATEGY_ADAPTIVE.equals(summary.executionStrategy()))
            .filter(summary -> PlannerPolicyProfile.BALANCED.configName().equals(summary.policyProfile()))
            .findFirst()
            .orElse(null);

        html.append("<h2>Profile comparison (selected scenario)</h2>")
            .append("<p class='muted'>Includes naive_parallel and selected adaptive profile set: ")
            .append(escape(compareProfilesParam))
            .append(". Deltas are directional and should be interpreted with scenario context.</p>")
            .append("<table><thead><tr>")
            .append("<th>Strategy</th><th>Profile</th><th>Executed</th><th>Degraded</th>")
            .append("<th>Budget fit</th><th>Delta vs balanced</th><th>Omitted</th><th>Fallback</th><th>Approximate</th><th>Why</th>")
            .append("</tr></thead><tbody>");
        for (DashboardBenchmarkSummary summary : comparisonSummaries.stream()
            .sorted(Comparator.comparing(DashboardBenchmarkSummary::executionStrategy)
                .thenComparing(DashboardBenchmarkSummary::policyProfile))
            .toList()) {
            boolean selectedRow = STRATEGY_ADAPTIVE.equals(summary.executionStrategy())
                && selectedProfile.configName().equals(summary.policyProfile());
            long fitPercent = safePercent(summary.projectedWork().toMillis(), summary.requestBudget().toMillis());
            String deltaVsBalanced = formatDeltaVsBalanced(summary, balancedAdaptive);
            html.append("<tr class='")
                .append(selectedRow ? "selected-row" : "")
                .append("'>")
                .append("<td>").append(escape(summary.executionStrategy())).append("</td>")
                .append("<td>").append(escape(summary.policyProfile())).append("</td>")
                .append("<td>").append(summary.totalTasksExecuted()).append("</td>")
                .append("<td>").append(summary.degraded()).append("</td>")
                .append("<td>").append(summary.projectedWork().toMillis()).append("ms / ")
                .append(summary.requestBudget().toMillis()).append("ms (").append(fitPercent).append("%)</td>")
                .append("<td>").append(deltaVsBalanced).append("</td>")
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
        return card(title, value, "");
    }

    private String card(String title, String value, String className) {
        String classes = className == null || className.isBlank() ? "card" : "card " + className;
        return "<div class='" + classes + "'><strong>" + title + "</strong><div>" + value + "</div></div>";
    }

    private String budgetMetric(String label, long usedMs, long budgetMs) {
        long clampedBudget = Math.max(1, budgetMs);
        long fillPercent = safePercent(usedMs, clampedBudget);
        String fillClass = usedMs > budgetMs ? "bar-fill over" : "bar-fill";
        return "<div class='metric'><div class='metric-label'>"
            + escape(label)
            + "</div><div class='muted'>"
            + usedMs + "ms / " + budgetMs + "ms (" + fillPercent + "%)"
            + "</div><div class='bar'><div class='"
            + fillClass
            + "' style='width:"
            + Math.min(fillPercent, 100)
            + "%'></div></div></div>";
    }

    private String degradationStateBadge(RequestExecutionDiagnostics diagnostics) {
        int degradedSignals = diagnostics.fallbackTaskNames().size()
            + diagnostics.approximatedTaskNames().size()
            + diagnostics.omittedTaskNames().size();
        if (!diagnostics.omittedTaskNames().isEmpty()) {
            return "<span class='badge badge-high'>high degradation</span> (" + degradedSignals + " changes)";
        }
        if (degradedSignals > 0) {
            return "<span class='badge badge-mid'>degraded</span> (" + degradedSignals + " changes)";
        }
        return "<span class='badge badge-ok'>stable</span>";
    }

    private String modeClass(ExecutionMode mode) {
        return switch (mode) {
            case EXECUTE -> "mode-execute";
            case FALLBACK -> "mode-fallback";
            case APPROXIMATE -> "mode-approximate";
            case OMIT -> "mode-omit";
        };
    }

    private long safePercent(long numerator, long denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return 0;
        }
        return Math.round((numerator * 100.0) / denominator);
    }

    private String formatDeltaVsBalanced(DashboardBenchmarkSummary summary, DashboardBenchmarkSummary balancedAdaptive) {
        if (balancedAdaptive == null || !STRATEGY_ADAPTIVE.equals(summary.executionStrategy())) {
            if (STRATEGY_NAIVE.equals(summary.executionStrategy())) {
                return "reference only";
            }
            return "-";
        }
        if (PlannerPolicyProfile.BALANCED.configName().equals(summary.policyProfile())) {
            return "baseline";
        }
        long workDeltaMs = summary.projectedWork().toMillis() - balancedAdaptive.projectedWork().toMillis();
        int executedDelta = summary.totalTasksExecuted() - balancedAdaptive.totalTasksExecuted();
        int degradeSignalDelta = (summary.omittedTasks().size() + summary.fallbackTasks().size() + summary.approximatedTasks().size())
            - (balancedAdaptive.omittedTasks().size() + balancedAdaptive.fallbackTasks().size() + balancedAdaptive.approximatedTasks().size());
        return "<span class='delta " + deltaClass(workDeltaMs) + "'>Δwork " + signed(workDeltaMs) + "ms</span>"
            + " · "
            + "<span class='delta " + deltaClass(executedDelta) + "'>Δexec " + signed(executedDelta) + "</span>"
            + " · "
            + "<span class='delta " + deltaClass(degradeSignalDelta) + "'>Δdegrade " + signed(degradeSignalDelta) + "</span>";
    }

    private String deltaClass(long delta) {
        if (delta > 0) {
            return "delta-down";
        }
        if (delta < 0) {
            return "delta-up";
        }
        return "delta-neutral";
    }

    private String signed(long value) {
        if (value > 0) {
            return "+" + value;
        }
        return String.valueOf(value);
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
