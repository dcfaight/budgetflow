package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.api.AdaptiveRequest;
import com.budgetflow.core.api.AdaptiveRequestResult;
import com.budgetflow.core.budget.DefaultExecutionBudget;
import com.budgetflow.core.classification.ExecutionMode;
import com.budgetflow.core.classification.Importance;
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
import java.math.BigDecimal;
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
    private final DemoDatasetCatalog demoDatasetCatalog;

    public EvaluatorDashboardService(DemoDatasetCatalog demoDatasetCatalog) {
        this.demoDatasetCatalog = demoDatasetCatalog;
    }

    public String render(
        String requestedPackName,
        String requestedScenarioName,
        String requestedCompareScenarios,
        String requestedProfileName,
        String requestedCompareProfiles,
        String requestedDatasetId,
        String requestedCompareDatasets,
        String requestedWalkthroughStep
    ) {
        List<String> notes = new ArrayList<>();
        DashboardScenarioPack pack = resolvePack(requestedPackName, notes);
        DashboardBenchmarkScenario scenario = resolveScenario(pack, requestedScenarioName, notes);
        List<DashboardBenchmarkScenario> compareScenarios = resolveCompareScenarios(
            pack,
            requestedCompareScenarios,
            scenario,
            notes
        );
        PlannerPolicyProfile profile = resolveProfile(requestedProfileName, "profile", notes);
        List<PlannerPolicyProfile> compareProfiles = resolveCompareProfiles(requestedCompareProfiles, profile, notes);
        String selectedDatasetId = resolveDatasetId(requestedDatasetId, notes);
        List<String> compareDatasetIds = resolveCompareDatasets(requestedCompareDatasets, selectedDatasetId);
        DemoDatasetCatalog.DatasetPack selectedDataset = demoDatasetCatalog.loadDataset(selectedDatasetId);
        DatasetScenarioMetrics selectedDatasetMetrics = datasetMetrics(selectedDataset);
        List<DatasetScenarioMetrics> compareDatasetMetrics = compareDatasetIds.stream()
            .map(demoDatasetCatalog::loadDataset)
            .map(this::datasetMetrics)
            .toList();
        String walkthroughStep = resolveWalkthroughStep(requestedWalkthroughStep);

        List<DashboardBenchmarkSummary> comparisonSummaries;
        List<DashboardBenchmarkSummary> packTrendSummaries;
        List<DashboardBenchmarkSummary> storylineSummaries;
        try (DashboardComparisonHarness harness = new DashboardComparisonHarness(noDelaySimulationSupport())) {
            comparisonSummaries = harness.run(List.of(scenario), compareProfiles);
            packTrendSummaries = harness.run(
                pack.scenarios(),
                ensureSelectedProfile(List.of(PlannerPolicyProfile.BALANCED), profile)
            );
            storylineSummaries = harness.run(compareScenarios, List.of(profile));
        }
        ScenarioExecution execution = executeScenario(scenario, profile, selectedDatasetId);
        return renderHtml(
            pack,
            scenario,
            compareScenarios,
            profile,
            compareProfiles,
            selectedDataset,
            selectedDatasetMetrics,
            compareDatasetMetrics,
            comparisonSummaries,
            packTrendSummaries,
            storylineSummaries,
            execution,
            notes,
            walkthroughStep
        );
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

    private String resolveWalkthroughStep(String requestedWalkthroughStep) {
        if (requestedWalkthroughStep == null || requestedWalkthroughStep.isBlank()) {
            return "start";
        }
        String normalized = requestedWalkthroughStep.trim().toLowerCase();
        if (List.of("start", "compare", "profile", "trace").contains(normalized)) {
            return normalized;
        }
        return "start";
    }

    private List<DashboardBenchmarkScenario> resolveCompareScenarios(
        DashboardScenarioPack pack,
        String requestedCompareScenarios,
        DashboardBenchmarkScenario selectedScenario,
        List<String> notes
    ) {
        if (requestedCompareScenarios == null || requestedCompareScenarios.isBlank()) {
            return defaultCompareScenarios(pack, selectedScenario);
        }
        List<DashboardBenchmarkScenario> resolved = Arrays.stream(requestedCompareScenarios.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(scenarioName -> findScenario(pack, scenarioName, notes))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .limit(4)
            .collect(Collectors.toList());
        if (resolved.isEmpty()) {
            notes.add("No valid compareScenarios supplied. Showing compact storyline around selected scenario.");
            return defaultCompareScenarios(pack, selectedScenario);
        }
        if (!resolved.contains(selectedScenario) && resolved.size() < 4) {
            resolved = new ArrayList<>(resolved);
            resolved.add(selectedScenario);
        }
        return resolved.stream()
            .sorted(Comparator.comparingInt(pack.scenarios()::indexOf))
            .toList();
    }

    private DashboardBenchmarkScenario findScenario(
        DashboardScenarioPack pack,
        String scenarioName,
        List<String> notes
    ) {
        return pack.scenarios().stream()
            .filter(scenario -> scenario.name().equals(scenarioName))
            .findFirst()
            .orElseGet(() -> {
                notes.add("Ignoring unknown compare scenario '" + scenarioName + "'.");
                return null;
            });
    }

    private List<DashboardBenchmarkScenario> defaultCompareScenarios(
        DashboardScenarioPack pack,
        DashboardBenchmarkScenario selectedScenario
    ) {
        int selectedIndex = pack.scenarios().indexOf(selectedScenario);
        int startIndex = Math.max(0, selectedIndex - 1);
        int endIndex = Math.min(pack.scenarios().size() - 1, startIndex + 2);
        startIndex = Math.max(0, endIndex - 2);
        return pack.scenarios().subList(startIndex, endIndex + 1);
    }

    private List<PlannerPolicyProfile> ensureSelectedProfile(
        List<PlannerPolicyProfile> compareProfiles,
        PlannerPolicyProfile selectedProfile
    ) {
        LinkedHashSet<PlannerPolicyProfile> resolved = new LinkedHashSet<>(compareProfiles);
        resolved.add(selectedProfile);
        return List.copyOf(resolved);
    }

    private String resolveDatasetId(String requestedDatasetId, List<String> notes) {
        String fallback = demoDatasetCatalog.selectedDatasetId();
        if (requestedDatasetId == null || requestedDatasetId.isBlank()) {
            return fallback;
        }
        if (!demoDatasetCatalog.availableDatasetIds().contains(requestedDatasetId)) {
            notes.add("Unknown dataset '" + requestedDatasetId + "'. Showing " + fallback + ".");
            return fallback;
        }
        return requestedDatasetId;
    }

    private List<String> resolveCompareDatasets(String requestedCompareDatasets, String selectedDatasetId) {
        if (requestedCompareDatasets == null || requestedCompareDatasets.isBlank()) {
            List<String> available = demoDatasetCatalog.availableDatasetIds();
            int selectedIndex = available.indexOf(selectedDatasetId);
            if (selectedIndex > 0) {
                return List.of(available.get(selectedIndex - 1), selectedDatasetId);
            }
            if (available.size() > 1) {
                return List.of(selectedDatasetId, available.get(1));
            }
            return List.of(selectedDatasetId);
        }
        LinkedHashSet<String> compareDatasets = Arrays.stream(requestedCompareDatasets.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .filter(demoDatasetCatalog.availableDatasetIds()::contains)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        compareDatasets.add(selectedDatasetId);
        return compareDatasets.stream().limit(2).toList();
    }

    private ScenarioExecution executeScenario(
        DashboardBenchmarkScenario scenario,
        PlannerPolicyProfile profile,
        String datasetId
    ) {
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        SimulationSupport noDelay = noDelaySimulationSupport();
        try {
            BudgetContextHolder.set(new BudgetContext(new DefaultExecutionBudget(scenario.requestBudget())));
            AdaptiveRequest request = DashboardTaskSpecs.forAccount(
                ACCOUNT_ID,
                datasetId,
                new BalanceClient(noDelay, demoDatasetCatalog),
                new TransactionClient(noDelay, demoDatasetCatalog),
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
                RequestExecutionDiagnosticsFormatter.formatSummary(diagnostics, result.decisionTrace()),
                RequestExecutionDiagnosticsFormatter.formatAgentSteps(diagnostics, result.decisionTrace())
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
        List<DashboardBenchmarkScenario> compareScenarios,
        PlannerPolicyProfile selectedProfile,
        List<PlannerPolicyProfile> compareProfiles,
        DemoDatasetCatalog.DatasetPack selectedDataset,
        DatasetScenarioMetrics selectedDatasetMetrics,
        List<DatasetScenarioMetrics> compareDatasetMetrics,
        List<DashboardBenchmarkSummary> comparisonSummaries,
        List<DashboardBenchmarkSummary> packTrendSummaries,
        List<DashboardBenchmarkSummary> storylineSummaries,
        ScenarioExecution execution,
        List<String> notes,
        String walkthroughStep
    ) {
        String compareProfilesParam = compareProfiles.stream()
            .map(PlannerPolicyProfile::configName)
            .collect(Collectors.joining(","));
        String compareScenariosParam = compareScenarios.stream()
            .map(DashboardBenchmarkScenario::name)
            .collect(Collectors.joining(","));
        String compareDatasetsParam = compareDatasetMetrics.stream()
            .map(DatasetScenarioMetrics::datasetId)
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
            .append(".walkthrough-banner{background:#eef6ff;border:1px solid #bfdbfe;border-radius:12px;padding:12px;margin:14px 0;} .walkthrough-steps{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px;}")
            .append(".step-pill{padding:4px 9px;border-radius:999px;background:#dbeafe;color:#1e3a8a;font-size:12px;} .step-pill.active{background:#1d4ed8;color:#fff;font-weight:600;}")
            .append(".trend-grid{display:flex;gap:10px;flex-wrap:wrap;} .trend-card{border:1px solid #dbe2ef;background:#fff;border-radius:12px;padding:10px;min-width:260px;}")
            .append(".spark{height:8px;background:#e2e8f0;border-radius:999px;overflow:hidden;margin-top:6px;} .spark-fill{height:100%;background:linear-gradient(90deg,#34d399,#10b981);} .spark-fill.warn{background:linear-gradient(90deg,#f59e0b,#dc2626);}")
            .append(".diff-stack{display:flex;gap:4px;align-items:center;} .diff-chip{font-size:11px;padding:2px 6px;border-radius:999px;background:#e2e8f0;color:#334155;}")
            .append(".lane{border:1px solid #dbe2ef;border-radius:12px;background:#fff;padding:10px;min-width:250px;} .lane h3{margin-bottom:6px;} .mini{font-size:12px;color:#475569;}")
            .append(".trace-summary{background:#fff;border:1px solid #dbe2ef;border-radius:12px;padding:10px;margin-top:10px;}")
            .append(".storyline-grid{display:flex;gap:10px;flex-wrap:wrap;} .storyline-card{border:1px solid #dbe2ef;background:#fff;border-radius:12px;padding:10px;min-width:290px;}")
            .append(".storyline-step{padding:6px 8px;border-left:3px solid #93c5fd;background:#f8fbff;border-radius:8px;margin:6px 0;}")
            .append(".walkthrough-panel{background:#f8fbff;border:1px solid #bfdbfe;padding:12px;border-radius:12px;margin-top:10px;}")
            .append(".path-block{background:#fff;border:1px solid #dbe2ef;border-radius:10px;padding:10px;min-width:300px;}")
            .append(".mode-matrix{display:grid;grid-template-columns:repeat(2,minmax(260px,1fr));gap:10px;}")
            .append(".matrix-card{border:1px solid #dbe2ef;border-radius:12px;background:#fff;padding:10px;}")
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
            .append(card("Active dataset", escape(selectedDatasetMetrics.displayName()), "card-emphasis"))
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

        html.append("<h2>Scenario lab datasets</h2><div>");
        for (String datasetId : demoDatasetCatalog.availableDatasetIds()) {
            DatasetScenarioMetrics metrics = datasetMetrics(demoDatasetCatalog.loadDataset(datasetId));
            boolean active = datasetId.equals(selectedDataset.datasetId());
            html.append("<span class='pill ")
                .append(active ? "pill-active" : "")
                .append("'>")
                .append(active ? "<strong>" : "")
                .append("<a href='").append(dashboardLink(
                    pack.name(),
                    selectedScenario.name(),
                    selectedProfile.configName(),
                    compareScenariosParam,
                    compareProfilesParam,
                    datasetId,
                    compareDatasetsParam,
                    walkthroughStep
                )).append("'>")
                .append(escape(metrics.displayName()))
                .append("</a>")
                .append(active ? " (active)</strong>" : "")
                .append("</span>");
        }
        html.append("</div>")
            .append("<div class='row'>")
            .append(card("Scenario intent", escape(selectedDatasetMetrics.intent())))
            .append(card("Expected evaluator behavior", escape(selectedDatasetMetrics.expectedEvaluatorBehavior())))
            .append(card("Customer/profile summary", escape(selectedDatasetMetrics.profileSummary())))
            .append(card("What to look for", escape(selectedDatasetMetrics.whatToLookFor())))
            .append("</div>")
            .append("<div class='tip'><strong>Sanitized dataset notice:</strong> ")
            .append(escape(selectedDatasetMetrics.sanitizedNotice()))
            .append("</div>")
            .append("<div class='row'>")
            .append(card("Cash pressure (dataset signal)", selectedDatasetMetrics.cashPressure() + "/100"))
            .append(card("Subscription load", selectedDatasetMetrics.subscriptionLoadPercent() + "% of debit spend"))
            .append(card("Low-value card tx volume", String.valueOf(selectedDatasetMetrics.smallCardTransactionCount())))
            .append(card("Available balance", formatCurrency(selectedDatasetMetrics.availableBalance())))
            .append("</div>")
            .append("<div class='tip'><strong>Scenario playback controls:</strong> ")
            .append(playbackControls(
                selectedDataset.datasetId(),
                pack,
                selectedScenario,
                selectedProfile,
                compareScenariosParam,
                compareProfilesParam,
                compareDatasetsParam,
                walkthroughStep
            ))
            .append("</div>")
            .append("<h3>Quick compare (current vs previous scenario dataset)</h3>")
            .append(datasetCompareTable(
                compareDatasetMetrics,
                pack,
                selectedScenario,
                selectedProfile,
                compareScenariosParam,
                compareProfilesParam,
                walkthroughStep
            ));

        html.append("<div class='walkthrough-banner'>")
            .append("<strong>Scenario walkthrough mode</strong>")
            .append("<div class='mini'>Current phase: ")
            .append(escape(walkthroughLabel(walkthroughStep)))
            .append(". Follow the highlighted phase, then use recommended next comparisons.</div>")
            .append("<div class='walkthrough-steps'>")
            .append(stepPill(
                "start",
                "1) orient",
                walkthroughStep,
                pack.name(),
                selectedScenario.name(),
                selectedProfile.configName(),
                compareScenariosParam,
                compareProfilesParam,
                selectedDataset.datasetId(),
                compareDatasetsParam
            ))
            .append(stepPill(
                "compare",
                "2) compare",
                walkthroughStep,
                pack.name(),
                selectedScenario.name(),
                selectedProfile.configName(),
                compareScenariosParam,
                compareProfilesParam,
                selectedDataset.datasetId(),
                compareDatasetsParam
            ))
            .append(stepPill(
                "profile",
                "3) profile tradeoff",
                walkthroughStep,
                pack.name(),
                selectedScenario.name(),
                selectedProfile.configName(),
                compareScenariosParam,
                compareProfilesParam,
                selectedDataset.datasetId(),
                compareDatasetsParam
            ))
            .append(stepPill(
                "trace",
                "4) trace explain",
                walkthroughStep,
                pack.name(),
                selectedScenario.name(),
                selectedProfile.configName(),
                compareScenariosParam,
                compareProfilesParam,
                selectedDataset.datasetId(),
                compareDatasetsParam
            ))
            .append("</div>")
            .append("<div class='mini' style='margin-top:8px'>")
            .append("Recommended next comparison: ")
            .append(recommendedNextComparison(
                pack,
                selectedScenario,
                selectedProfile,
                compareScenariosParam,
                compareProfilesParam,
                selectedDataset.datasetId(),
                compareDatasetsParam,
                walkthroughStep
            ))
            .append("</div>")
            .append("<div class='mini' style='margin-top:6px'>")
            .append("Storyline compare set: ")
            .append(escape(compareScenarios.stream()
                .map(DashboardBenchmarkScenario::displayName)
                .collect(Collectors.joining(" → "))))
            .append("</div>")
            .append("</div>");

        html.append("<div class='walkthrough-panel'><strong>Narrative walkthrough panel</strong>")
            .append("<div class='mini'>")
            .append(walkthroughNarrative(selectedScenario, selectedProfile, walkthroughStep, pack))
            .append("</div>")
            .append("<div class='mini' style='margin-top:8px'>")
            .append("Compact storyline controls: ")
            .append(compactStorylineSelectionLinks(
                pack,
                compareScenarios,
                selectedScenario,
                selectedProfile,
                compareProfilesParam,
                selectedDataset.datasetId(),
                compareDatasetsParam,
                walkthroughStep
            ))
            .append("</div></div>");

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
                .append("<a href='").append(dashboardLink(
                    packName,
                    "",
                    selectedProfile.configName(),
                    compareScenariosParam,
                    compareProfilesParam,
                    selectedDataset.datasetId(),
                    compareDatasetsParam,
                    walkthroughStep
                )).append("'>")
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
                .append("<a href='").append(dashboardLink(
                    pack.name(),
                    scenario.name(),
                    selectedProfile.configName(),
                    compareScenariosParam,
                    compareProfilesParam,
                    selectedDataset.datasetId(),
                    compareDatasetsParam,
                    walkthroughStep
                )).append("'>")
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
            .append("</div>")
            .append("<div class='tip'><strong>Profile recommendation (prototype guidance):</strong> ")
            .append(escape(profileRecommendation(selectedScenario)))
            .append("</div>")
            .append("<div class='tip'><strong>Why this scenario matters:</strong> ")
            .append(escape(selectedScenario.evaluationFocus()))
            .append(" — ")
            .append(escape(selectedScenario.whatToObserve()))
            .append("</div>")
            .append("<div class='tip'><strong>Compare next in storyline:</strong> ")
            .append(compactStorylineSelectionLinks(
                pack,
                compareScenarios,
                selectedScenario,
                selectedProfile,
                compareProfilesParam,
                selectedDataset.datasetId(),
                compareDatasetsParam,
                walkthroughStep
            ))
            .append("</div>");

        html.append("<h2>Scenario storyline synthesis</h2>")
            .append("<p class='muted'>Compact multi-scenario view for this pack so progression is visible without opening each scenario one-by-one.</p>")
            .append("<div class='storyline-grid'>")
            .append(storylineOverviewCard(compareScenarios, storylineSummaries, selectedProfile))
            .append(storylineChangeCard(compareScenarios, storylineSummaries, selectedProfile))
            .append(storylinePackCard(pack, packTrendSummaries, selectedProfile))
            .append("</div>")
            .append(storylineTable(
                compareScenarios,
                storylineSummaries,
                selectedProfile,
                compareProfilesParam,
                selectedDataset.datasetId(),
                compareDatasetsParam,
                walkthroughStep
            ));

        html.append("<h2>Profile selection</h2><div>");
        for (PlannerPolicyProfile profile : PlannerPolicyProfile.values()) {
            boolean active = profile == selectedProfile;
            html.append("<span class='pill ")
                .append(active ? "pill-active" : "")
                .append(profile == PlannerPolicyProfile.BALANCED ? " pill-recommended" : "")
                .append("'>")
                .append(active ? "<strong>" : "")
                .append("<a href='").append(dashboardLink(
                    pack.name(),
                    selectedScenario.name(),
                    profile.configName(),
                    compareScenariosParam,
                    compareProfilesParam,
                    selectedDataset.datasetId(),
                    compareDatasetsParam,
                    walkthroughStep
                )).append("'>")
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

        DashboardBenchmarkSummary balancedAdaptive = comparisonSummaries.stream()
            .filter(summary -> STRATEGY_ADAPTIVE.equals(summary.executionStrategy()))
            .filter(summary -> PlannerPolicyProfile.BALANCED.configName().equals(summary.policyProfile()))
            .findFirst()
            .orElse(null);
        DashboardBenchmarkSummary selectedAdaptive = comparisonSummaries.stream()
            .filter(summary -> STRATEGY_ADAPTIVE.equals(summary.executionStrategy()))
            .filter(summary -> selectedProfile.configName().equals(summary.policyProfile()))
            .findFirst()
            .orElse(null);
        DashboardBenchmarkSummary naiveSummary = comparisonSummaries.stream()
            .filter(summary -> STRATEGY_NAIVE.equals(summary.executionStrategy()))
            .findFirst()
            .orElse(null);

        html.append("<h2>Compact analytics snapshot</h2>")
            .append("<p class='muted'>Quick-scan trend cards for what changed, by how much, and whether it looks meaningful in this prototype context.</p>")
            .append("<div class='trend-grid'>")
            .append(trendCard(
                "Selected adaptive budget fit",
                selectedAdaptive == null ? "-" : selectedAdaptive.projectedWork().toMillis() + "ms / "
                    + selectedAdaptive.requestBudget().toMillis() + "ms",
                selectedAdaptive == null ? 0 : safePercent(
                    selectedAdaptive.projectedWork().toMillis(),
                    selectedAdaptive.requestBudget().toMillis()
                ),
                false
            ))
            .append(trendCard(
                "Adaptive vs naive projected work",
                selectedAdaptive == null || naiveSummary == null
                    ? "-"
                    : signed(selectedAdaptive.projectedWork().toMillis() - naiveSummary.projectedWork().toMillis()) + "ms",
                selectedAdaptive == null || naiveSummary == null
                    ? 0
                    : Math.abs((int) (selectedAdaptive.projectedWork().toMillis() - naiveSummary.projectedWork().toMillis())),
                true
            ))
            .append(trendCard(
                "Adaptive degraded task count",
                selectedAdaptive == null
                    ? "-"
                    : String.valueOf(selectedAdaptive.omittedTasks().size()
                    + selectedAdaptive.fallbackTasks().size()
                    + selectedAdaptive.approximatedTasks().size()),
                selectedAdaptive == null
                    ? 0
                    : (selectedAdaptive.omittedTasks().size()
                    + selectedAdaptive.fallbackTasks().size()
                    + selectedAdaptive.approximatedTasks().size()) * 20,
                true
            ))
            .append("</div>")
            .append("<div class='trace-summary'><strong>Pack trend (")
            .append(escape(selectedProfile.configName()))
            .append(" profile):</strong> ")
            .append(escape(packTrendSummary(packTrendSummaries, selectedProfile)))
            .append("<div class='mini'>")
            .append(compactPackScenarioLinks(
                pack,
                selectedProfile,
                compareScenariosParam,
                compareProfilesParam,
                selectedDataset.datasetId(),
                compareDatasetsParam,
                walkthroughStep
            ))
            .append("</div></div>");

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

        html.append("<h2>Decision and branch path view</h2>")
            .append("<div class='row'>")
            .append("<div class='path-block'><strong>Selected path</strong><div class='decision-path'>");
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
        html.append("</div></div>")
            .append("<div class='path-block'><strong>Baseline path reference (all primary modes)</strong><div class='mini'>")
            .append(baselinePathSummary(execution.decisionTrace()))
            .append("</div><div class='mini' style='margin-top:8px'><strong>Changed branches:</strong> ")
            .append(compactChangedDecisions(execution.decisionTrace()))
            .append("</div></div></div>")
            .append("<p class='muted'>Use selected vs baseline to see where degraded/omitted branches entered and why.</p>");

        html.append("<h2>Signal-to-mode summary (explicit)</h2>")
            .append("<div class='trace-summary'>")
            .append(signalDecisionSummary(execution.decisionTrace()))
            .append("</div>")
            .append("<div class='mode-matrix'>")
            .append(signalToModeMatrix(execution.decisionTrace()))
            .append("</div>");

        html.append("<h2>Agent-step view (compact explainability)</h2>")
            .append("<p class='muted'>Same decision trace, rendered in an agent-step format to make step-level degrade/omit choices easier to scan.</p>")
            .append("<div class='trace-summary'><pre style='margin:0;white-space:pre-wrap;'>")
            .append(escape(execution.agentStepSummary()))
            .append("</pre></div>");

        html.append("<h2>Planner lanes by importance</h2>")
            .append("<div class='row'>")
            .append(importanceLane("Mandatory", execution.decisionTrace(), Importance.MANDATORY))
            .append(importanceLane("Important", execution.decisionTrace(), Importance.IMPORTANT))
            .append(importanceLane("Optional", execution.decisionTrace(), Importance.OPTIONAL))
            .append("</div>")
            .append("<div class='tip'><strong>Importance interpretation:</strong> ")
            .append("Read optional lane changes first for adaptive tradeoffs, then verify mandatory/important stability.</div>");

        html.append("<h2>Trace compression: changed decisions first</h2>")
            .append("<div class='trace-summary'>")
            .append(compactChangedDecisions(execution.decisionTrace()))
            .append("</div>");

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

        html.append("<h2>Profile comparison (selected scenario)</h2>")
            .append("<p class='muted'>Includes naive_parallel and selected adaptive profile set: ")
            .append(escape(compareProfilesParam))
            .append(". Deltas are directional and should be interpreted with scenario context.</p>")
            .append("<table><thead><tr>")
            .append("<th>Strategy</th><th>Profile</th><th>Executed</th><th>Degraded</th>")
            .append("<th>Budget fit</th><th>Delta vs balanced</th><th>Compact visual diff</th><th>Interpret</th>")
            .append("<th>Omitted</th><th>Fallback</th><th>Approximate</th><th>Why</th>")
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
                .append("<td>").append(compactDiffStack(summary, balancedAdaptive)).append("</td>")
                .append("<td>").append(escape(interpretSummary(summary, balancedAdaptive))).append("</td>")
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
            case EXECUTE_WITH_FALLBACK -> "mode-fallback";
            case EXECUTE_APPROXIMATE -> "mode-approximate";
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

    private String dashboardLink(
        String pack,
        String scenario,
        String profile,
        String compareProfiles,
        String datasetId,
        String compareDatasets,
        String walkthroughStep
    ) {
        return dashboardLink(pack, scenario, profile, "", compareProfiles, datasetId, compareDatasets, walkthroughStep);
    }

    private String dashboardLink(
        String pack,
        String scenario,
        String profile,
        String compareScenarios,
        String compareProfiles,
        String datasetId,
        String compareDatasets,
        String walkthroughStep
    ) {
        return "/dashboard/evaluator?pack=" + url(pack)
            + "&scenario=" + url(scenario == null ? "" : scenario)
            + "&profile=" + url(profile)
            + "&compareScenarios=" + url(compareScenarios == null ? "" : compareScenarios)
            + "&compareProfiles=" + url(compareProfiles)
            + "&dataset=" + url(datasetId == null ? "" : datasetId)
            + "&compareDatasets=" + url(compareDatasets == null ? "" : compareDatasets)
            + "&walkthroughStep=" + url(walkthroughStep);
    }

    private String walkthroughLabel(String step) {
        return switch (step) {
            case "compare" -> "Compare scenario outcomes";
            case "profile" -> "Profile tradeoff exploration";
            case "trace" -> "Planner trace interpretation";
            default -> "Start and orient";
        };
    }

    private String stepPill(
        String step,
        String label,
        String activeStep,
        String pack,
        String scenario,
        String profile,
        String compareScenarios,
        String compareProfiles,
        String datasetId,
        String compareDatasets
    ) {
        String classes = "step-pill" + (step.equals(activeStep) ? " active" : "");
        return "<a class='" + classes + "' href='"
            + dashboardLink(pack, scenario, profile, compareScenarios, compareProfiles, datasetId, compareDatasets, step)
            + "'>" + escape(label) + "</a>";
    }

    private String recommendedNextComparison(
        DashboardScenarioPack pack,
        DashboardBenchmarkScenario selectedScenario,
        PlannerPolicyProfile selectedProfile,
        String compareScenarios,
        String compareProfiles,
        String datasetId,
        String compareDatasets,
        String walkthroughStep
    ) {
        int scenarioIndex = pack.scenarios().indexOf(selectedScenario);
        if ("start".equals(walkthroughStep) || "compare".equals(walkthroughStep)) {
            if (scenarioIndex >= 0 && scenarioIndex < pack.scenarios().size() - 1) {
                DashboardBenchmarkScenario nextScenario = pack.scenarios().get(scenarioIndex + 1);
                return "<a href='" + dashboardLink(
                    pack.name(),
                    nextScenario.name(),
                    selectedProfile.configName(),
                    compareScenarios,
                    compareProfiles,
                    datasetId,
                    compareDatasets,
                    "compare"
                ) + "'>Compare with next scenario: " + escape(nextScenario.displayName()) + "</a>";
            }
            return "<a href='" + dashboardLink(
                "adoption".equals(pack.name()) ? "realism" : "adoption",
                "",
                selectedProfile.configName(),
                compareScenarios,
                compareProfiles,
                datasetId,
                compareDatasets,
                "compare"
            ) + "'>Move to next storyline pack for broader comparison evidence</a>";
        }
        if ("profile".equals(walkthroughStep)) {
            PlannerPolicyProfile nextProfile = selectedProfile == PlannerPolicyProfile.BALANCED
                ? PlannerPolicyProfile.CONTINUITY
                : PlannerPolicyProfile.EFFICIENCY;
            return "<a href='" + dashboardLink(
                pack.name(),
                selectedScenario.name(),
                nextProfile.configName(),
                compareScenarios,
                compareProfiles,
                datasetId,
                compareDatasets,
                "profile"
            ) + "'>Run same scenario with " + escape(nextProfile.configName()) + " for directional delta</a>";
        }
        return "<a href='" + dashboardLink(
            pack.name(),
            selectedScenario.name(),
            selectedProfile.configName(),
            compareScenarios,
            compareProfiles,
            datasetId,
            compareDatasets,
            "start"
        ) + "'>Restart walkthrough orientation for a new evaluator pass</a>";
    }

    private String profileRecommendation(DashboardBenchmarkScenario scenario) {
        String pressure = scenario.pressureProfile();
        String budget = scenario.budgetProfile();
        if (pressure.contains("high") || pressure.contains("elevated")) {
            return "Start balanced, then check continuity if preserving optional signal continuity is important under pressure.";
        }
        if (budget.contains("constrained") || budget.contains("tight")) {
            return "Start balanced, then compare efficiency to inspect latency headroom tradeoffs under tighter budgets.";
        }
        return "Balanced is still the conservative starting point; compare continuity and efficiency only for directional interpretation.";
    }

    private String trendCard(String title, String value, long percentHint, boolean warnOnHigh) {
        long clamped = Math.max(0, Math.min(100, percentHint));
        String fillClass = warnOnHigh && clamped >= 70 ? "spark-fill warn" : "spark-fill";
        return "<div class='trend-card'><strong>" + escape(title)
            + "</strong><div>" + escape(value)
            + "</div><div class='spark'><div class='" + fillClass
            + "' style='width:" + clamped + "%'></div></div></div>";
    }

    private String walkthroughNarrative(
        DashboardBenchmarkScenario selectedScenario,
        PlannerPolicyProfile selectedProfile,
        String walkthroughStep,
        DashboardScenarioPack pack
    ) {
        return switch (walkthroughStep) {
            case "compare" -> "This phase answers what changed across scenarios. Keep profile on "
                + escape(selectedProfile.configName())
                + " and compare projected work, degraded count, and changed branches in sequence for "
                + escape(pack.name()) + ".";
            case "profile" -> "This phase answers why policy profile choice matters for one scenario. Keep scenario on "
                + escape(selectedScenario.displayName())
                + " and compare balanced vs continuity vs efficiency as directional evidence.";
            case "trace" -> "This phase answers how planner decisions were made. Start from changed branches, then map pressure/layer markers to selected modes.";
            default -> "This phase orients the storyline: why this scenario matters, what to observe next, and which adjacent scenarios to compare.";
        };
    }

    private String compactStorylineSelectionLinks(
        DashboardScenarioPack pack,
        List<DashboardBenchmarkScenario> compareScenarios,
        DashboardBenchmarkScenario selectedScenario,
        PlannerPolicyProfile selectedProfile,
        String compareProfiles,
        String datasetId,
        String compareDatasets,
        String walkthroughStep
    ) {
        String activeSet = compareScenarios.stream()
            .map(DashboardBenchmarkScenario::name)
            .collect(Collectors.joining(","));
        return pack.scenarios().stream()
            .map(scenario -> {
                String scenarioSet = defaultCompareScenarios(pack, scenario).stream()
                    .map(DashboardBenchmarkScenario::name)
                    .collect(Collectors.joining(","));
                boolean active = scenario.name().equals(selectedScenario.name());
                String label = (active ? "• " : "") + scenario.displayName();
                return "<a href='" + dashboardLink(
                    pack.name(),
                    scenario.name(),
                    selectedProfile.configName(),
                    scenarioSet.isBlank() ? activeSet : scenarioSet,
                    compareProfiles,
                    datasetId,
                    compareDatasets,
                    walkthroughStep
                ) + "'>" + escape(label) + "</a>";
            })
            .collect(Collectors.joining(" · "));
    }

    private String storylineOverviewCard(
        List<DashboardBenchmarkScenario> compareScenarios,
        List<DashboardBenchmarkSummary> storylineSummaries,
        PlannerPolicyProfile selectedProfile
    ) {
        List<DashboardBenchmarkSummary> adaptive = compareScenarios.stream()
            .map(scenario -> findSummary(storylineSummaries, scenario.name(), STRATEGY_ADAPTIVE, selectedProfile.configName()))
            .filter(java.util.Objects::nonNull)
            .toList();
        long degradedScenarios = adaptive.stream().filter(DashboardBenchmarkSummary::degraded).count();
        long totalAdaptiveWork = adaptive.stream().mapToLong(summary -> summary.projectedWork().toMillis()).sum();
        return "<div class='storyline-card'><strong>Storyline overview</strong>"
            + "<div class='mini'>Scenarios compared: " + compareScenarios.size()
            + " | degraded: " + degradedScenarios + "/" + adaptive.size() + "</div>"
            + "<div class='mini' style='margin-top:6px'>Total adaptive projected work: " + totalAdaptiveWork + "ms"
            + " (" + escape(selectedProfile.configName()) + ")</div></div>";
    }

    private String storylineChangeCard(
        List<DashboardBenchmarkScenario> compareScenarios,
        List<DashboardBenchmarkSummary> storylineSummaries,
        PlannerPolicyProfile selectedProfile
    ) {
        StringBuilder html = new StringBuilder("<div class='storyline-card'><strong>What changed across storyline?</strong>");
        DashboardBenchmarkSummary previous = null;
        for (DashboardBenchmarkScenario scenario : compareScenarios) {
            DashboardBenchmarkSummary current = findSummary(
                storylineSummaries,
                scenario.name(),
                STRATEGY_ADAPTIVE,
                selectedProfile.configName()
            );
            if (current == null) {
                continue;
            }
            if (previous == null) {
                html.append("<div class='storyline-step'><strong>")
                    .append(escape(scenario.displayName()))
                    .append("</strong>: starting point, adaptive work ")
                    .append(current.projectedWork().toMillis())
                    .append("ms.</div>");
            } else {
                long workDelta = current.projectedWork().toMillis() - previous.projectedWork().toMillis();
                int degradeDelta = degradeCount(current) - degradeCount(previous);
                html.append("<div class='storyline-step'><strong>")
                    .append(escape(scenario.displayName()))
                    .append("</strong>: Δwork ")
                    .append(signed(workDelta))
                    .append("ms, Δdegrade ")
                    .append(signed(degradeDelta))
                    .append(".</div>");
            }
            previous = current;
        }
        html.append("</div>");
        return html.toString();
    }

    private String storylinePackCard(
        DashboardScenarioPack pack,
        List<DashboardBenchmarkSummary> packTrendSummaries,
        PlannerPolicyProfile selectedProfile
    ) {
        return "<div class='storyline-card'><strong>Pack-level overview</strong>"
            + "<div class='mini'>" + escape(pack.description()) + "</div>"
            + "<div class='mini' style='margin-top:6px'><strong>Pack trend:</strong> "
            + escape(packTrendSummary(packTrendSummaries, selectedProfile))
            + "</div></div>";
    }

    private String storylineTable(
        List<DashboardBenchmarkScenario> compareScenarios,
        List<DashboardBenchmarkSummary> storylineSummaries,
        PlannerPolicyProfile selectedProfile,
        String compareProfiles,
        String datasetId,
        String compareDatasets,
        String walkthroughStep
    ) {
        String compareScenariosParam = compareScenarios.stream()
            .map(DashboardBenchmarkScenario::name)
            .collect(Collectors.joining(","));
        StringBuilder html = new StringBuilder("<table><thead><tr>")
            .append("<th>Scenario</th><th>Why it matters</th><th>Adaptive fit</th>")
            .append("<th>Adaptive degraded</th><th>Adaptive vs naive work</th><th>Change vs previous</th>")
            .append("</tr></thead><tbody>");
        DashboardBenchmarkSummary previousAdaptive = null;
        for (DashboardBenchmarkScenario scenario : compareScenarios) {
            DashboardBenchmarkSummary adaptive = findSummary(
                storylineSummaries,
                scenario.name(),
                STRATEGY_ADAPTIVE,
                selectedProfile.configName()
            );
            DashboardBenchmarkSummary naive = findSummary(storylineSummaries, scenario.name(), STRATEGY_NAIVE, "-");
            if (adaptive == null) {
                continue;
            }
            long naiveDelta = naive == null ? 0 : adaptive.projectedWork().toMillis() - naive.projectedWork().toMillis();
            String progressionDelta = previousAdaptive == null
                ? "starting point"
                : "Δwork " + signed(adaptive.projectedWork().toMillis() - previousAdaptive.projectedWork().toMillis())
                + "ms, Δdegrade " + signed(degradeCount(adaptive) - degradeCount(previousAdaptive));
            html.append("<tr>")
                .append("<td><a href='")
                .append(dashboardLink(
                    scenario.packName(),
                    scenario.name(),
                    selectedProfile.configName(),
                    compareScenariosParam,
                    compareProfiles,
                    datasetId,
                    compareDatasets,
                    walkthroughStep
                ))
                .append("'>")
                .append(escape(scenario.displayName()))
                .append("</a></td>")
                .append("<td>").append(escape(scenario.evaluationFocus())).append("</td>")
                .append("<td>")
                .append(adaptive.projectedWork().toMillis()).append("ms / ")
                .append(adaptive.requestBudget().toMillis()).append("ms")
                .append("</td>")
                .append("<td>").append(degradeCount(adaptive)).append("</td>")
                .append("<td>").append(naive == null ? "-" : signed(naiveDelta) + "ms").append("</td>")
                .append("<td>").append(escape(progressionDelta)).append("</td>")
                .append("</tr>");
            previousAdaptive = adaptive;
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private DashboardBenchmarkSummary findSummary(
        List<DashboardBenchmarkSummary> summaries,
        String scenarioName,
        String strategy,
        String policyProfile
    ) {
        return summaries.stream()
            .filter(summary -> scenarioName.equals(summary.scenarioName()))
            .filter(summary -> strategy.equals(summary.executionStrategy()))
            .filter(summary -> policyProfile.equals(summary.policyProfile()))
            .findFirst()
            .orElse(null);
    }

    private int degradeCount(DashboardBenchmarkSummary summary) {
        return summary.omittedTasks().size() + summary.fallbackTasks().size() + summary.approximatedTasks().size();
    }

    private String packTrendSummary(
        List<DashboardBenchmarkSummary> packTrendSummaries,
        PlannerPolicyProfile selectedProfile
    ) {
        List<DashboardBenchmarkSummary> adaptiveSummaries = packTrendSummaries.stream()
            .filter(summary -> STRATEGY_ADAPTIVE.equals(summary.executionStrategy()))
            .filter(summary -> selectedProfile.configName().equals(summary.policyProfile()))
            .toList();
        if (adaptiveSummaries.isEmpty()) {
            return "No pack trend data available.";
        }
        long degradedScenarios = adaptiveSummaries.stream().filter(DashboardBenchmarkSummary::degraded).count();
        DashboardBenchmarkSummary heaviest = adaptiveSummaries.stream()
            .max(Comparator.comparing(summary -> summary.projectedWork().toMillis()))
            .orElse(adaptiveSummaries.get(0));
        return "degraded scenarios: " + degradedScenarios + "/" + adaptiveSummaries.size()
            + ", heaviest projected work: " + heaviest.scenario().displayName()
            + " (" + heaviest.projectedWork().toMillis() + "ms).";
    }

    private String compactPackScenarioLinks(
        DashboardScenarioPack pack,
        PlannerPolicyProfile selectedProfile,
        String compareScenarios,
        String compareProfiles,
        String datasetId,
        String compareDatasets,
        String walkthroughStep
    ) {
        return pack.scenarios().stream()
            .map(scenario -> "<a href='" + dashboardLink(
                pack.name(),
                scenario.name(),
                selectedProfile.configName(),
                compareScenarios,
                compareProfiles,
                datasetId,
                compareDatasets,
                walkthroughStep
            ) + "'>" + escape(scenario.displayName()) + "</a>")
            .collect(Collectors.joining(" · "));
    }

    private String compactDiffStack(DashboardBenchmarkSummary summary, DashboardBenchmarkSummary balancedAdaptive) {
        if (balancedAdaptive == null || !STRATEGY_ADAPTIVE.equals(summary.executionStrategy())) {
            return "<span class='diff-chip'>reference</span>";
        }
        long workDeltaMs = summary.projectedWork().toMillis() - balancedAdaptive.projectedWork().toMillis();
        int executedDelta = summary.totalTasksExecuted() - balancedAdaptive.totalTasksExecuted();
        int degradeSignalDelta = (summary.omittedTasks().size() + summary.fallbackTasks().size() + summary.approximatedTasks().size())
            - (balancedAdaptive.omittedTasks().size() + balancedAdaptive.fallbackTasks().size() + balancedAdaptive.approximatedTasks().size());
        return "<span class='diff-stack'>"
            + "<span class='diff-chip'>work " + signed(workDeltaMs) + "ms</span>"
            + "<span class='diff-chip'>exec " + signed(executedDelta) + "</span>"
            + "<span class='diff-chip'>degrade " + signed(degradeSignalDelta) + "</span>"
            + "</span>";
    }

    private String interpretSummary(DashboardBenchmarkSummary summary, DashboardBenchmarkSummary balancedAdaptive) {
        if (!STRATEGY_ADAPTIVE.equals(summary.executionStrategy())) {
            return "Naive baseline reference only.";
        }
        if (balancedAdaptive == null || PlannerPolicyProfile.BALANCED.configName().equals(summary.policyProfile())) {
            return "Balanced baseline for directional comparison.";
        }
        int degradeDelta = (summary.omittedTasks().size() + summary.fallbackTasks().size() + summary.approximatedTasks().size())
            - (balancedAdaptive.omittedTasks().size() + balancedAdaptive.fallbackTasks().size() + balancedAdaptive.approximatedTasks().size());
        if (degradeDelta > 0) {
            return "More adaptation than balanced; inspect whether added degradation is acceptable for this endpoint.";
        }
        if (degradeDelta < 0) {
            return "Less adaptation than balanced; verify budget headroom still looks safe.";
        }
        return "Similar adaptation to balanced; focus on work/coverage deltas.";
    }

    private String importanceLane(String title, List<DecisionTraceEntry> trace, Importance importance) {
        List<DecisionTraceEntry> laneEntries = trace.stream()
            .filter(entry -> entry.taskImportance() == importance)
            .toList();
        if (laneEntries.isEmpty()) {
            return "<div class='lane'><h3>" + escape(title) + "</h3><div class='mini'>No tasks in this lane.</div></div>";
        }
        long degraded = laneEntries.stream().filter(entry -> entry.selectedExecutionMode() != ExecutionMode.EXECUTE).count();
        Map<ExecutionMode, Long> modeCounts = laneEntries.stream()
            .collect(Collectors.groupingBy(DecisionTraceEntry::selectedExecutionMode, LinkedHashMap::new, Collectors.counting()));
        long expectedMs = laneEntries.stream().mapToLong(entry -> entry.expectedLatency().toMillis()).sum();
        long plannedMs = laneEntries.stream().mapToLong(entry -> entry.plannedExecutionLatency().toMillis()).sum();
        String steps = laneEntries.stream()
            .map(entry -> "<span class='diff-chip'>" + escape(entry.taskName()) + " · " + escape(entry.selectedExecutionMode().name()) + "</span>")
            .collect(Collectors.joining(" "));
        return "<div class='lane'><h3>" + escape(title) + "</h3><div class='mini'>"
            + laneEntries.size() + " tasks, " + degraded + " degraded/omitted</div>"
            + "<div class='mini'>modes: " + modeCounts.entrySet().stream()
            .map(entry -> entry.getKey().name() + "=" + entry.getValue())
            .collect(Collectors.joining(", "))
            + "</div><div class='mini'>expected/planned: " + expectedMs + "ms → " + plannedMs + "ms"
            + " (savings " + (expectedMs - plannedMs) + "ms)</div>"
            + "<div style='margin-top:6px'>" + steps + "</div></div>";
    }

    private String signalDecisionSummary(List<DecisionTraceEntry> trace) {
        Map<String, Integer> pressureCounts = new LinkedHashMap<>();
        Map<String, Integer> layerCounts = new LinkedHashMap<>();
        Map<ExecutionMode, Integer> modeCounts = new LinkedHashMap<>();
        for (DecisionTraceEntry entry : trace) {
            Map<String, String> fields = parseReasonFields(entry.reason());
            String pressure = fields.getOrDefault("pressure", "unspecified");
            String layer = fields.getOrDefault("layer", "unspecified");
            pressureCounts.put(pressure, pressureCounts.getOrDefault(pressure, 0) + 1);
            layerCounts.put(layer, layerCounts.getOrDefault(layer, 0) + 1);
            modeCounts.put(entry.selectedExecutionMode(), modeCounts.getOrDefault(entry.selectedExecutionMode(), 0) + 1);
        }
        return "pressure influence: " + formatCountMap(pressureCounts)
            + " | decision layers: " + formatCountMap(layerCounts)
            + " | selected modes: " + modeCounts.entrySet().stream()
            .map(entry -> entry.getKey().name() + "=" + entry.getValue())
            .collect(Collectors.joining(", "))
            + ". Use planner trace rows below for exact per-task reasoning.";
    }

    private String signalToModeMatrix(List<DecisionTraceEntry> trace) {
        Map<String, Map<ExecutionMode, Integer>> pressureToMode = new LinkedHashMap<>();
        for (DecisionTraceEntry entry : trace) {
            String pressure = parseReasonFields(entry.reason()).getOrDefault("pressure", "unspecified");
            pressureToMode.computeIfAbsent(pressure, ignored -> new LinkedHashMap<>());
            Map<ExecutionMode, Integer> modeCounts = pressureToMode.get(pressure);
            modeCounts.put(entry.selectedExecutionMode(), modeCounts.getOrDefault(entry.selectedExecutionMode(), 0) + 1);
        }
        return pressureToMode.entrySet().stream()
            .map(entry -> "<div class='matrix-card'><strong>" + escape(entry.getKey()) + "</strong><div class='mini'>"
                + entry.getValue().entrySet().stream()
                .map(modeEntry -> modeEntry.getKey().name() + "=" + modeEntry.getValue())
                .collect(Collectors.joining(", "))
                + "</div></div>")
            .collect(Collectors.joining(""));
    }

    private String baselinePathSummary(List<DecisionTraceEntry> trace) {
        return trace.stream()
            .map(entry -> entry.taskName() + " → EXECUTE(" + entry.expectedLatency().toMillis() + "ms)")
            .collect(Collectors.joining(" · "));
    }

    private String formatCountMap(Map<String, Integer> counts) {
        return counts.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(", "));
    }

    private String compactChangedDecisions(List<DecisionTraceEntry> trace) {
        List<DecisionTraceEntry> changed = trace.stream()
            .filter(entry -> entry.selectedExecutionMode() != ExecutionMode.EXECUTE)
            .toList();
        if (changed.isEmpty()) {
            return "No changed decisions in this run; all tasks executed on primary path.";
        }
        return changed.stream()
            .map(entry -> escape(entry.taskName()) + " → " + escape(entry.selectedExecutionMode().name())
                + " (" + escape(parseReasonFields(entry.reason()).getOrDefault("layer", "layer=unknown"))
                + ", savings=" + (entry.expectedLatency().toMillis() - entry.plannedExecutionLatency().toMillis()) + "ms)")
            .collect(Collectors.joining(" · "));
    }

    private DatasetScenarioMetrics datasetMetrics(DemoDatasetCatalog.DatasetPack datasetPack) {
        BigDecimal availableBalance = datasetPack.accounts().stream()
            .map(DemoDatasetCatalog.DemoAccount::availableBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDebit = datasetPack.transactions().stream()
            .filter(transaction -> "debit".equalsIgnoreCase(transaction.direction()))
            .map(DemoDatasetCatalog.DemoTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = datasetPack.transactions().stream()
            .filter(transaction -> "credit".equalsIgnoreCase(transaction.direction()))
            .map(DemoDatasetCatalog.DemoTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subscriptionDebit = datasetPack.transactions().stream()
            .filter(transaction -> "debit".equalsIgnoreCase(transaction.direction()))
            .filter(transaction -> transaction.category() != null && transaction.category().toLowerCase().contains("subscription"))
            .map(DemoDatasetCatalog.DemoTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long smallCardTransactions = datasetPack.transactions().stream()
            .filter(transaction -> "debit".equalsIgnoreCase(transaction.direction()))
            .filter(transaction -> "card".equalsIgnoreCase(transaction.channel()))
            .filter(transaction -> transaction.amount() != null && transaction.amount().compareTo(BigDecimal.valueOf(20)) <= 0)
            .count();
        BigDecimal budgetLimit = datasetPack.budgets().stream()
            .map(DemoDatasetCatalog.DemoBudget::monthlyLimit)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal budgetSpent = datasetPack.budgets().stream()
            .map(DemoDatasetCatalog.DemoBudget::spentToDate)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long budgetUtilizationPercent = budgetLimit.compareTo(BigDecimal.ZERO) == 0
            ? 0
            : Math.round(budgetSpent.divide(budgetLimit, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue());
        long subscriptionLoadPercent = totalDebit.compareTo(BigDecimal.ZERO) == 0
            ? 0
            : Math.round(subscriptionDebit.divide(totalDebit, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue());
        int balanceRisk = availableBalance.compareTo(BigDecimal.valueOf(250)) < 0
            ? 85
            : availableBalance.compareTo(BigDecimal.valueOf(600)) < 0
                ? 60
                : availableBalance.compareTo(BigDecimal.valueOf(1200)) < 0 ? 35 : 15;
        int budgetRisk = budgetUtilizationPercent >= 100 ? 85
            : budgetUtilizationPercent >= 85 ? 60
            : budgetUtilizationPercent >= 70 ? 40 : 20;
        int subscriptionRisk = subscriptionLoadPercent >= 40 ? 70
            : subscriptionLoadPercent >= 25 ? 45
            : subscriptionLoadPercent >= 10 ? 25 : 10;
        long cashPressure = Math.min(100, Math.round((balanceRisk * 0.5) + (budgetRisk * 0.35) + (subscriptionRisk * 0.15)));
        DemoDatasetCatalog.DemoScenarioMetadata metadata = datasetPack.scenarioMetadata();
        String primarySegment = datasetPack.customers().isEmpty()
            ? "general"
            : datasetPack.customers().stream()
                .map(DemoDatasetCatalog.DemoCustomer::segment)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse("general");
        String profileSummary = datasetPack.customers().size() + " synthetic customer(s), "
            + datasetPack.accounts().size() + " account(s), segment " + primarySegment + ".";
        if (metadata.customerProfileSummary() != null && !metadata.customerProfileSummary().isBlank()) {
            profileSummary = metadata.customerProfileSummary();
        }
        String whatToLookFor = metadata.whatToLookFor() == null || metadata.whatToLookFor().isBlank()
            ? metadata.realWorldPattern()
            : metadata.whatToLookFor();
        return new DatasetScenarioMetrics(
            datasetPack.datasetId(),
            metadata.displayName(),
            metadata.intent(),
            metadata.expectedEvaluatorBehavior(),
            profileSummary,
            whatToLookFor,
            metadata.sanitizedNotice(),
            availableBalance,
            totalDebit,
            totalCredit,
            subscriptionLoadPercent,
            budgetUtilizationPercent,
            smallCardTransactions,
            cashPressure
        );
    }

    private String playbackControls(
        String activeDatasetId,
        DashboardScenarioPack pack,
        DashboardBenchmarkScenario selectedScenario,
        PlannerPolicyProfile selectedProfile,
        String compareScenarios,
        String compareProfiles,
        String compareDatasets,
        String walkthroughStep
    ) {
        List<String> datasets = demoDatasetCatalog.availableDatasetIds();
        int currentIndex = datasets.indexOf(activeDatasetId);
        String previousDatasetId = currentIndex > 0 ? datasets.get(currentIndex - 1) : activeDatasetId;
        String nextDatasetId = currentIndex >= 0 && currentIndex < datasets.size() - 1
            ? datasets.get(currentIndex + 1)
            : activeDatasetId;
        String compareWithPrevious = previousDatasetId.equals(activeDatasetId)
            ? activeDatasetId
            : previousDatasetId + "," + activeDatasetId;
        return "<a href='" + dashboardLink(
            pack.name(),
            selectedScenario.name(),
            selectedProfile.configName(),
            compareScenarios,
            compareProfiles,
            previousDatasetId,
            compareDatasets,
            walkthroughStep
        ) + "'>◀ previous dataset</a>"
            + " · <a href='" + dashboardLink(
            pack.name(),
            selectedScenario.name(),
            selectedProfile.configName(),
            compareScenarios,
            compareProfiles,
            nextDatasetId,
            compareDatasets,
            walkthroughStep
        ) + "'>next dataset ▶</a>"
            + " · <a href='" + dashboardLink(
            pack.name(),
            selectedScenario.name(),
            selectedProfile.configName(),
            compareScenarios,
            compareProfiles,
            activeDatasetId,
            compareWithPrevious,
            walkthroughStep
        ) + "'>compare current vs previous</a>";
    }

    private String datasetCompareTable(
        List<DatasetScenarioMetrics> datasetMetrics,
        DashboardScenarioPack pack,
        DashboardBenchmarkScenario selectedScenario,
        PlannerPolicyProfile selectedProfile,
        String compareScenarios,
        String compareProfiles,
        String walkthroughStep
    ) {
        StringBuilder html = new StringBuilder("<table><thead><tr>")
            .append("<th>Dataset</th><th>Cash pressure</th><th>Subscription load</th>")
            .append("<th>Budget utilization</th><th>Small card tx</th><th>Available balance</th><th>Guidance delta</th>")
            .append("</tr></thead><tbody>");
        DatasetScenarioMetrics previous = null;
        for (DatasetScenarioMetrics metrics : datasetMetrics) {
            String compareSet = previous == null ? metrics.datasetId() : previous.datasetId() + "," + metrics.datasetId();
            html.append("<tr>")
                .append("<td><a href='")
                .append(dashboardLink(
                    pack.name(),
                    selectedScenario.name(),
                    selectedProfile.configName(),
                    compareScenarios,
                    compareProfiles,
                    metrics.datasetId(),
                    compareSet,
                    walkthroughStep
                ))
                .append("'>")
                .append(escape(metrics.displayName()))
                .append("</a><div class='mini'>").append(escape(metrics.datasetId())).append("</div></td>")
                .append("<td>").append(metrics.cashPressure()).append("/100</td>")
                .append("<td>").append(metrics.subscriptionLoadPercent()).append("%</td>")
                .append("<td>").append(metrics.budgetUtilizationPercent()).append("%</td>")
                .append("<td>").append(metrics.smallCardTransactionCount()).append("</td>")
                .append("<td>").append(escape(formatCurrency(metrics.availableBalance()))).append("</td>")
                .append("<td>");
            if (previous == null) {
                html.append("starting point");
            } else {
                html.append("Δpressure ").append(signed(metrics.cashPressure() - previous.cashPressure()))
                    .append(", Δsubs ").append(signed(metrics.subscriptionLoadPercent() - previous.subscriptionLoadPercent())).append("%")
                    .append(", Δsmall-card ").append(signed(metrics.smallCardTransactionCount() - previous.smallCardTransactionCount()));
            }
            html.append("</td></tr>");
            previous = metrics;
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String formatCurrency(BigDecimal value) {
        return "$" + value.setScale(2, java.math.RoundingMode.HALF_UP);
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
        String executionSummary,
        String agentStepSummary
    ) {
    }

    private record DatasetScenarioMetrics(
        String datasetId,
        String displayName,
        String intent,
        String expectedEvaluatorBehavior,
        String profileSummary,
        String whatToLookFor,
        String sanitizedNotice,
        BigDecimal availableBalance,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        long subscriptionLoadPercent,
        long budgetUtilizationPercent,
        long smallCardTransactionCount,
        long cashPressure
    ) {
    }
}
