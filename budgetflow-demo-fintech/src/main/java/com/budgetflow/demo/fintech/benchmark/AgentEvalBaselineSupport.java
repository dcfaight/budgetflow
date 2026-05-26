package com.budgetflow.demo.fintech.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class AgentEvalBaselineSupport {
    static final String SNAPSHOT_FILE_NAME = "agent-eval-snapshot.json";
    static final String DELTA_JSON_FILE_NAME = "agent-eval-delta.json";
    static final String DELTA_MD_FILE_NAME = "agent-eval-delta.md";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Map<String, Integer> ASSESSMENT_RANK = Map.of(
        "expected", 0,
        "acceptable", 1,
        "cautionary", 2,
        "mismatched", 3
    );
    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
        "expected", 0,
        "informative", 1,
        "cautionary", 2,
        "regression-risk", 3
    );

    private AgentEvalBaselineSupport() {
    }

    static Snapshot snapshot(DashboardScenarioPack pack, List<DashboardBenchmarkSummary> summaries) {
        Map<String, List<DashboardBenchmarkSummary>> grouped = summaries.stream()
            .collect(Collectors.groupingBy(
                DashboardBenchmarkSummary::scenarioName,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        List<ScenarioSnapshot> scenarios = new ArrayList<>();
        int scenariosCompared = 0;
        int adaptiveLowerProjectedWorkCount = 0;
        int adaptiveDegradedCount = 0;
        int baselineConvergenceCount = 0;
        int adaptivePlanChangeCount = 0;
        int profileComparisonScenarioCount = 0;
        Map<String, Integer> dispositionCounts = new LinkedHashMap<>();

        for (List<DashboardBenchmarkSummary> scenarioSummaries : grouped.values()) {
            DashboardBenchmarkScenario scenario = scenarioSummaries.get(0).scenario();
            List<StrategySnapshot> strategies = orderedStrategies(scenarioSummaries).stream()
                .map(summary -> new StrategySnapshot(
                    summary.executionStrategy(),
                    summary.policyProfile(),
                    summary.totalTasksExecuted(),
                    summary.degraded(),
                    summary.projectedWork().toMillis(),
                    List.copyOf(summary.omittedTasks()),
                    List.copyOf(summary.fallbackTasks()),
                    List.copyOf(summary.approximatedTasks()),
                    List.copyOf(summary.degradationReasons())
                ))
                .toList();

            List<ScenarioAssessmentScorer.ScenarioScorecard> scorecards =
                ScenarioAssessmentScorer.scorecards(scenarioSummaries);
            List<ScorecardSnapshot> snapshotScorecards = scorecards.stream()
                .map(scorecard -> {
                    dispositionCounts.merge(scorecard.disposition().label(), 1, Integer::sum);
                    return new ScorecardSnapshot(
                        scorecard.executionStrategy(),
                        scorecard.policyProfile(),
                        scorecard.mandatoryWorkPreserved(),
                        scorecard.optionalAlignment(),
                        scorecard.fallbackAlignment(),
                        scorecard.intentMatched(),
                        scorecard.disposition().label(),
                        scorecard.rationale()
                    );
                })
                .toList();

            List<DashboardBenchmarkSummary> adaptiveVariants = scenarioSummaries.stream()
                .filter(summary -> "budgetflow_adaptive".equals(summary.executionStrategy()))
                .sorted(Comparator.comparing(DashboardBenchmarkSummary::policyProfile))
                .toList();
            DashboardBenchmarkSummary balanced = summaryForStrategy(scenarioSummaries, "budgetflow_adaptive", "balanced");
            List<ProfileComparisonSnapshot> profileComparisons = adaptiveVariants.stream()
                .map(summary -> new ProfileComparisonSnapshot(
                    summary.policyProfile(),
                    summary.totalTasksExecuted(),
                    summary.fallbackTasks().size(),
                    summary.approximatedTasks().size(),
                    summary.omittedTasks().size(),
                    summary.degraded(),
                    summary.requestBudget().toMillis() - summary.projectedWork().toMillis(),
                    profileDiffExplanation(summary, balanced)
                ))
                .toList();

            if (adaptiveVariants.size() > 1) {
                profileComparisonScenarioCount++;
            }

            DashboardBenchmarkSummary naive = summaryForStrategy(scenarioSummaries, "naive_parallel", "-");
            if (naive != null && balanced != null) {
                scenariosCompared++;
                if (balanced.projectedWork().compareTo(naive.projectedWork()) < 0) {
                    adaptiveLowerProjectedWorkCount++;
                }
                if (balanced.degraded()) {
                    adaptiveDegradedCount++;
                }
                if (!balanced.degraded()
                    && !naive.degraded()
                    && balanced.projectedWork().equals(naive.projectedWork())
                    && balanced.totalTasksExecuted() == naive.totalTasksExecuted()) {
                    baselineConvergenceCount++;
                }
                if (balanced.degraded()
                    || balanced.projectedWork().compareTo(naive.projectedWork()) != 0
                    || balanced.totalTasksExecuted() != naive.totalTasksExecuted()) {
                    adaptivePlanChangeCount++;
                }
            }

            scenarios.add(new ScenarioSnapshot(
                scenario.name(),
                scenario.displayName(),
                scenario.taxonomy(),
                strategies,
                snapshotScorecards,
                profileComparisons
            ));
        }

        return new Snapshot(
            pack.name(),
            pack.description(),
            new SummarySnapshot(
                scenariosCompared,
                adaptiveLowerProjectedWorkCount,
                adaptiveDegradedCount,
                baselineConvergenceCount,
                adaptivePlanChangeCount,
                profileComparisonScenarioCount,
                dispositionCounts
            ),
            scenarios
        );
    }

    static void writeSnapshot(Path path, Snapshot snapshot) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), snapshot);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to write snapshot: " + path, ioException);
        }
    }

    static Snapshot readSnapshot(Path path) {
        try {
            return OBJECT_MAPPER.readValue(path.toFile(), Snapshot.class);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to read snapshot: " + path, ioException);
        }
    }

    static Path saveBaseline(
        Path outDir,
        String baselineName,
        String reportJson,
        String reportMarkdown,
        Snapshot snapshot
    ) {
        Path baselineDir = outDir.resolve("baselines").resolve(sanitizeName(baselineName));
        writeString(baselineDir.resolve("agent-eval-report.json"), reportJson);
        writeString(baselineDir.resolve("agent-eval-report.md"), reportMarkdown);
        writeSnapshot(baselineDir.resolve(SNAPSHOT_FILE_NAME), snapshot);
        return baselineDir;
    }

    static Path resolveSnapshotPath(Path outDir, String baselineRef) {
        Path explicit = Path.of(baselineRef);
        if (Files.exists(explicit)) {
            return Files.isDirectory(explicit) ? explicit.resolve(SNAPSHOT_FILE_NAME) : explicit;
        }
        return outDir.resolve("baselines").resolve(sanitizeName(baselineRef)).resolve(SNAPSHOT_FILE_NAME);
    }

    static Comparison compare(String baselineLabel, Snapshot baseline, Snapshot current) {
        List<ScenarioDelta> scenarioDeltas = new ArrayList<>();
        int regressionRiskCount = 0;
        int cautionaryCount = 0;
        int informativeCount = 0;
        int expectedCount = 0;
        int changedScorecards = 0;

        Map<String, ScenarioSnapshot> baselineScenarios = baseline.scenarios().stream()
            .collect(Collectors.toMap(ScenarioSnapshot::name, scenario -> scenario, (first, second) -> first, LinkedHashMap::new));

        for (ScenarioSnapshot currentScenario : current.scenarios()) {
            ScenarioSnapshot baselineScenario = baselineScenarios.get(currentScenario.name());
            if (baselineScenario == null) {
                continue;
            }

            List<StrategyDelta> strategyDeltas = compareStrategies(baselineScenario, currentScenario);
            List<ScorecardDelta> scorecardDeltas = compareScorecards(baselineScenario, currentScenario);
            List<ProfileInterpretationDelta> interpretationDeltas = compareInterpretations(baselineScenario, currentScenario);
            List<Highlight> highlights = buildHighlights(currentScenario, strategyDeltas, scorecardDeltas, interpretationDeltas);

            if (strategyDeltas.isEmpty() && scorecardDeltas.isEmpty() && interpretationDeltas.isEmpty()) {
                continue;
            }

            for (Highlight highlight : highlights) {
                switch (highlight.severity()) {
                    case "regression-risk" -> regressionRiskCount++;
                    case "cautionary" -> cautionaryCount++;
                    case "informative" -> informativeCount++;
                    default -> expectedCount++;
                }
            }
            changedScorecards += scorecardDeltas.size();
            String severity = scenarioSeverity(highlights);
            scenarioDeltas.add(new ScenarioDelta(
                currentScenario.name(),
                currentScenario.displayName(),
                currentScenario.taxonomy(),
                severity,
                scenarioPrioritySummary(currentScenario, severity, highlights),
                scenarioReviewHotspots(currentScenario, highlights),
                strategyDeltas,
                scorecardDeltas,
                interpretationDeltas,
                highlights
            ));
        }

        List<ScenarioDelta> sorted = scenarioDeltas.stream()
            .sorted(Comparator
                .comparingInt((ScenarioDelta delta) -> -severityRank(delta.severity()))
                .thenComparingInt(delta -> -delta.highlights().size())
                .thenComparing(ScenarioDelta::name))
            .toList();

        return new Comparison(
            baselineLabel,
            baseline.summary(),
            current.summary(),
            sorted,
            new DeltaSummary(
                sorted.size(),
                changedScorecards,
                regressionRiskCount,
                cautionaryCount,
                informativeCount,
                expectedCount
            )
        );
    }

    static String formatDeltaMarkdown(Comparison comparison) {
        StringBuilder builder = new StringBuilder();
        builder.append("# BudgetFlow evidence delta").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Baseline: `").append(comparison.baselineLabel()).append("`").append(System.lineSeparator());
        builder.append("- Reviewer note: inspect `regression-risk` first, then `cautionary`, then `informative`; keep `expected` as validation evidence.")
            .append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("## Summary delta").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Adaptive degraded runs: ")
            .append(deltaText(
                comparison.baselineSummary().adaptiveDegradedCount(),
                comparison.currentSummary().adaptiveDegradedCount()
            ))
            .append(System.lineSeparator());
        builder.append("- Baseline convergence: ")
            .append(deltaText(
                comparison.baselineSummary().baselineConvergenceCount(),
                comparison.currentSummary().baselineConvergenceCount()
            ))
            .append(System.lineSeparator());
        builder.append("- Adaptive plan changes: ")
            .append(deltaText(
                comparison.baselineSummary().adaptivePlanChangeCount(),
                comparison.currentSummary().adaptivePlanChangeCount()
            ))
            .append(System.lineSeparator());
        builder.append("- Scorecard dispositions: ")
            .append(dispositionDeltaText(comparison.baselineSummary().scorecardDispositions(),
                comparison.currentSummary().scorecardDispositions()))
            .append(System.lineSeparator());
        builder.append("- Changed scenarios: ").append(comparison.deltaSummary().changedScenarioCount())
            .append(" | changed scorecards: ").append(comparison.deltaSummary().changedScorecardCount())
            .append(" | regression-risk: ").append(comparison.deltaSummary().regressionRiskCount())
            .append(" | cautionary: ").append(comparison.deltaSummary().cautionaryCount())
            .append(" | informative: ").append(comparison.deltaSummary().informativeCount())
            .append(" | expected: ").append(comparison.deltaSummary().expectedCount())
            .append(System.lineSeparator());
        appendTopChanges(builder, comparison);
        appendHotspots(builder, comparison);

        for (ScenarioDelta scenario : comparison.scenarioDeltas()) {
            builder.append(System.lineSeparator())
                .append("## ").append(scenario.displayName())
                .append(" (`").append(scenario.name()).append("`)").append(System.lineSeparator()).append(System.lineSeparator());
            builder.append("- Priority: `").append(scenario.severity()).append("` — ")
                .append(scenario.prioritySummary()).append(System.lineSeparator());
            builder.append("- Taxonomy: ").append(scenario.taxonomy().summary()).append(System.lineSeparator());
            builder.append("- Hotspots: ").append(String.join("; ", scenario.reviewHotspots())).append(System.lineSeparator());
            for (Highlight highlight : orderedHighlights(scenario.highlights())) {
                builder.append("- [").append(highlight.severity()).append("] ")
                    .append(highlight.category()).append(" (")
                    .append(highlight.executionStrategy()).append("/")
                    .append(highlight.policyProfile()).append("): ")
                    .append(highlight.message()).append(" — ")
                    .append(highlight.reviewFocus()).append(System.lineSeparator());
            }
            if (!scenario.strategyDeltas().isEmpty()) {
                builder.append(System.lineSeparator())
                    .append("| Strategy | Policy | Severity | Category | Δexec | Δfb | Δapprox | Δomit | Degraded | Work | Review focus |\n")
                    .append("|---|---|---|---|---:|---:|---:|---:|---|---|---|\n");
                for (StrategyDelta delta : orderedStrategyDeltas(scenario.strategyDeltas())) {
                    builder.append("| ").append(delta.executionStrategy())
                        .append(" | ").append(delta.policyProfile())
                        .append(" | ").append(delta.severity())
                        .append(" | ").append(delta.triageCategory())
                        .append(" | ").append(signed(delta.executedTaskDelta()))
                        .append(" | ").append(signed(delta.fallbackDelta()))
                        .append(" | ").append(signed(delta.approximatedDelta()))
                        .append(" | ").append(signed(delta.omittedDelta()))
                        .append(" | ").append(delta.baselineDegraded()).append(" → ").append(delta.currentDegraded())
                        .append(" | ").append(delta.baselineProjectedWorkMs()).append("ms → ")
                        .append(delta.currentProjectedWorkMs()).append("ms")
                        .append(" | ").append(delta.reviewFocus())
                        .append(" |").append(System.lineSeparator());
                }
            }
            if (!scenario.scorecardDeltas().isEmpty()) {
                builder.append(System.lineSeparator())
                    .append("| Strategy | Policy | Assessment | Note |\n")
                    .append("|---|---|---|---|\n");
                for (ScorecardDelta delta : scenario.scorecardDeltas()) {
                    builder.append("| ").append(delta.executionStrategy())
                        .append(" | ").append(delta.policyProfile())
                        .append(" | ").append(delta.baselineAssessment()).append(" → ").append(delta.currentAssessment())
                        .append(" | ").append(delta.note())
                        .append(" |").append(System.lineSeparator());
                }
            }
            if (!scenario.profileInterpretationDeltas().isEmpty()) {
                builder.append(System.lineSeparator())
                    .append("| Policy | Baseline | Current |\n")
                    .append("|---|---|---|\n");
                for (ProfileInterpretationDelta delta : scenario.profileInterpretationDeltas()) {
                    builder.append("| ").append(delta.policyProfile())
                        .append(" | ").append(delta.baselineInterpretation())
                        .append(" | ").append(delta.currentInterpretation())
                        .append(" |").append(System.lineSeparator());
                }
            }
        }
        return builder.toString();
    }

    static String formatDeltaJson(Comparison comparison) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(comparison);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to format comparison delta JSON", ioException);
        }
    }

    private static List<StrategyDelta> compareStrategies(ScenarioSnapshot baselineScenario, ScenarioSnapshot currentScenario) {
        Map<String, StrategySnapshot> baselineStrategies = baselineScenario.strategies().stream()
            .collect(Collectors.toMap(
                strategy -> key(strategy.executionStrategy(), strategy.policyProfile()),
                strategy -> strategy,
                (first, second) -> first,
                LinkedHashMap::new
            ));
        List<StrategyDelta> deltas = new ArrayList<>();
        for (StrategySnapshot current : currentScenario.strategies()) {
            StrategySnapshot baseline = baselineStrategies.get(key(current.executionStrategy(), current.policyProfile()));
            if (baseline == null) {
                continue;
            }
            int executedDelta = current.executedTasks() - baseline.executedTasks();
            int fallbackDelta = current.fallbackTasks().size() - baseline.fallbackTasks().size();
            int approximatedDelta = current.approximatedTasks().size() - baseline.approximatedTasks().size();
            int omittedDelta = current.omittedTasks().size() - baseline.omittedTasks().size();
            long workDelta = current.projectedWorkMs() - baseline.projectedWorkMs();
            if (executedDelta == 0
                && fallbackDelta == 0
                && approximatedDelta == 0
                && omittedDelta == 0
                && workDelta == 0
                && baseline.degraded() == current.degraded()) {
                continue;
            }
            DeltaTriage triage = classifyChange(
                current.executionStrategy(),
                current.policyProfile(),
                executedDelta,
                fallbackDelta,
                approximatedDelta,
                omittedDelta,
                baseline.degraded(),
                current.degraded(),
                workDelta
            );
            deltas.add(new StrategyDelta(
                current.executionStrategy(),
                current.policyProfile(),
                executedDelta,
                fallbackDelta,
                approximatedDelta,
                omittedDelta,
                baseline.degraded(),
                current.degraded(),
                baseline.projectedWorkMs(),
                current.projectedWorkMs(),
                triage.severity(),
                triage.category(),
                triage.likelyExplanation(),
                triage.reviewFocus()
            ));
        }
        return deltas;
    }

    private static List<ScorecardDelta> compareScorecards(ScenarioSnapshot baselineScenario, ScenarioSnapshot currentScenario) {
        Map<String, ScorecardSnapshot> baselineScorecards = baselineScenario.scorecards().stream()
            .collect(Collectors.toMap(
                scorecard -> key(scorecard.executionStrategy(), scorecard.policyProfile()),
                scorecard -> scorecard,
                (first, second) -> first,
                LinkedHashMap::new
            ));
        List<ScorecardDelta> deltas = new ArrayList<>();
        for (ScorecardSnapshot current : currentScenario.scorecards()) {
            ScorecardSnapshot baseline = baselineScorecards.get(key(current.executionStrategy(), current.policyProfile()));
            if (baseline == null || baseline.assessment().equals(current.assessment())) {
                continue;
            }
            String note = assessmentRank(current.assessment()) > assessmentRank(baseline.assessment())
                ? "assessment regressed"
                : "assessment improved";
            deltas.add(new ScorecardDelta(
                current.executionStrategy(),
                current.policyProfile(),
                baseline.assessment(),
                current.assessment(),
                note
            ));
        }
        return deltas;
    }

    private static List<ProfileInterpretationDelta> compareInterpretations(
        ScenarioSnapshot baselineScenario,
        ScenarioSnapshot currentScenario
    ) {
        Map<String, ProfileComparisonSnapshot> baselineComparisons = baselineScenario.profileComparisons().stream()
            .collect(Collectors.toMap(
                ProfileComparisonSnapshot::policyProfile,
                comparison -> comparison,
                (first, second) -> first,
                LinkedHashMap::new
            ));
        List<ProfileInterpretationDelta> deltas = new ArrayList<>();
        for (ProfileComparisonSnapshot current : currentScenario.profileComparisons()) {
            ProfileComparisonSnapshot baseline = baselineComparisons.get(current.policyProfile());
            if (baseline == null || baseline.interpretation().equals(current.interpretation())) {
                continue;
            }
            deltas.add(new ProfileInterpretationDelta(
                current.policyProfile(),
                baseline.interpretation(),
                current.interpretation()
            ));
        }
        return deltas;
    }

    private static List<Highlight> buildHighlights(
        ScenarioSnapshot scenario,
        List<StrategyDelta> strategyDeltas,
        List<ScorecardDelta> scorecardDeltas,
        List<ProfileInterpretationDelta> interpretationDeltas
    ) {
        List<Highlight> highlights = new ArrayList<>();
        for (ScorecardDelta delta : scorecardDeltas) {
            boolean worsened = assessmentRank(delta.currentAssessment()) > assessmentRank(delta.baselineAssessment());
            String severity = worsened
                ? ("cautionary".equals(delta.currentAssessment()) || "mismatched".equals(delta.currentAssessment())
                    ? "regression-risk"
                    : "cautionary")
                : "informative";
            highlights.add(new Highlight(
                severity,
                worsened ? "likely_regression" : "notable_recovery",
                delta.executionStrategy(),
                delta.policyProfile(),
                worsened
                    ? "Scorecard disposition worsened versus baseline."
                    : "Scorecard disposition improved versus baseline.",
                "Inspect scorecard rationale and planner trace for this strategy/profile.",
                delta.executionStrategy() + "/" + delta.policyProfile()
                    + " assessment " + delta.baselineAssessment() + " → " + delta.currentAssessment()
            ));
        }
        for (StrategyDelta delta : strategyDeltas) {
            highlights.add(new Highlight(
                delta.severity(),
                delta.triageCategory(),
                delta.executionStrategy(),
                delta.policyProfile(),
                delta.likelyExplanation(),
                delta.reviewFocus(),
                delta.executionStrategy() + "/" + delta.policyProfile()
                    + " Δexec=" + signed(delta.executedTaskDelta())
                    + ", Δfb=" + signed(delta.fallbackDelta())
                    + ", Δapprox=" + signed(delta.approximatedDelta())
                    + ", Δomit=" + signed(delta.omittedDelta())
                    + ", work=" + delta.baselineProjectedWorkMs() + "ms → " + delta.currentProjectedWorkMs() + "ms"
                    + ", degraded=" + delta.baselineDegraded() + " → " + delta.currentDegraded()
            ));
        }
        for (ProfileInterpretationDelta delta : interpretationDeltas) {
            String severity = delta.currentInterpretation().contains("inspect trace for cause")
                ? "cautionary"
                : "informative";
            highlights.add(new Highlight(
                severity,
                "profile-intent difference",
                "budgetflow_adaptive",
                delta.policyProfile(),
                "Profile-vs-balanced interpretation changed versus baseline.",
                "Confirm this interpretation shift still matches endpoint intent.",
                "interpretation " + delta.policyProfile() + ": " + delta.baselineInterpretation()
                    + " → " + delta.currentInterpretation()
            ));
        }
        return highlights.stream()
            .distinct()
            .sorted(Comparator
                .comparingInt((Highlight highlight) -> -severityRank(highlight.severity()))
                .thenComparing(Highlight::executionStrategy)
                .thenComparing(Highlight::policyProfile)
                .thenComparing(Highlight::message))
            .toList();
    }

    private static DeltaTriage classifyChange(
        String executionStrategy,
        String policyProfile,
        int executedDelta,
        int fallbackDelta,
        int approximatedDelta,
        int omittedDelta,
        boolean baselineDegraded,
        boolean currentDegraded,
        long workDelta
    ) {
        if (!baselineDegraded && currentDegraded) {
            return new DeltaTriage(
                "regression-risk",
                "likely_regression",
                "Scenario now degrades where baseline did not.",
                "Check why degradation was introduced and whether mandatory/important work shifted."
            );
        }
        if (baselineDegraded && !currentDegraded) {
            return new DeltaTriage(
                "informative",
                "notable_improvement",
                "Scenario no longer degrades versus baseline.",
                "Validate this is a real quality gain, not hidden scenario drift."
            );
        }
        if (assessmentSensitiveRegression(executionStrategy, policyProfile, executedDelta, omittedDelta)) {
            return new DeltaTriage(
                "regression-risk",
                "likely_regression",
                "Balanced/default expectations now execute less work or omit more.",
                "Inspect planner reasons and verify mandatory/important coverage remains intact."
            );
        }
        if (executedDelta > 0 || omittedDelta < 0) {
            return new DeltaTriage(
                "informative",
                "coverage_change",
                "Current run keeps more work than baseline.",
                "Confirm added work aligns with endpoint intent and budget headroom."
            );
        }
        if ("latency_first".equals(policyProfile) && omittedDelta > 0 && workDelta <= 0) {
            return new DeltaTriage(
                "expected",
                "profile-intent difference",
                "latency_first omitted more optional work while protecting headroom.",
                "Usually expected; only inspect if mandatory/important behavior changed."
            );
        }
        if ("continuity".equals(policyProfile) && fallbackDelta + approximatedDelta > 0 && omittedDelta <= 0) {
            return new DeltaTriage(
                "expected",
                "profile-intent difference",
                "continuity favored degraded optional paths over omission.",
                "Validate that this preserved response continuity as intended."
            );
        }
        if ("efficiency".equals(policyProfile) && (omittedDelta > 0 || workDelta < 0)) {
            return new DeltaTriage(
                "expected",
                "profile-intent difference",
                "efficiency shifted toward lower optional work and/or leaner projected cost.",
                "Confirm this tradeoff matches endpoint latency/headroom goals."
            );
        }
        if (omittedDelta > 0) {
            return new DeltaTriage(
                "cautionary",
                "optional coverage drop",
                "Optional work dropped versus baseline outside strong profile-intent evidence.",
                "Check if this is acceptable scenario drift or an early regression signal."
            );
        }
        if (fallbackDelta != 0 || approximatedDelta != 0 || workDelta != 0) {
            String severity = Math.abs(workDelta) >= 80 ? "cautionary" : "informative";
            return new DeltaTriage(
                severity,
                "scenario drift",
                "Fallback/approximation/work shape changed without explicit profile-intent signal.",
                "Review trace and taxonomy context to separate benign drift from risk."
            );
        }
        return new DeltaTriage(
            "informative",
            "notable variation",
            "Planner behavior changed in a small but reviewable way.",
            "Quickly confirm no intent mismatch and move on."
        );
    }

    private static boolean assessmentSensitiveRegression(
        String executionStrategy,
        String policyProfile,
        int executedDelta,
        int omittedDelta
    ) {
        if (executedDelta < 0) {
            return true;
        }
        if (omittedDelta <= 0) {
            return false;
        }
        return "balanced".equals(policyProfile)
            || "-".equals(policyProfile)
            || "naive_parallel".equals(executionStrategy);
    }

    private static int assessmentRank(String assessment) {
        return ASSESSMENT_RANK.getOrDefault(assessment, 100);
    }

    private static int severityRank(String severity) {
        return SEVERITY_RANK.getOrDefault(severity, 0);
    }

    private static String scenarioSeverity(List<Highlight> highlights) {
        return highlights.stream()
            .map(Highlight::severity)
            .max(Comparator.comparingInt(AgentEvalBaselineSupport::severityRank))
            .orElse("expected");
    }

    private static String scenarioPrioritySummary(
        ScenarioSnapshot scenario,
        String severity,
        List<Highlight> highlights
    ) {
        long profileIntentDifferences = highlights.stream()
            .filter(highlight -> "profile-intent difference".equals(highlight.category()))
            .count();
        if ("regression-risk".equals(severity)) {
            return "Baseline-vs-current changes include likely regressions; inspect this scenario first.";
        }
        if ("cautionary".equals(severity)) {
            return "Notable drift detected; verify intent match before merging.";
        }
        if (profileIntentDifferences > 0) {
            return "Mostly profile-intent differences; review for endpoint alignment.";
        }
        return "Primarily informative deltas; quick validation is usually sufficient.";
    }

    private static List<String> scenarioReviewHotspots(ScenarioSnapshot scenario, List<Highlight> highlights) {
        List<String> hotspots = new ArrayList<>();
        hotspots.add("endpoint=" + scenario.taxonomy().endpointIntent());
        hotspots.add("pressure_mode=" + scenario.taxonomy().pressureMode());
        if (highlights.stream().anyMatch(highlight -> "balanced".equals(highlight.policyProfile()))) {
            hotspots.add("balanced profile changes");
        }
        List<String> changedProfiles = highlights.stream()
            .map(Highlight::policyProfile)
            .filter(profile -> !"-".equals(profile))
            .distinct()
            .toList();
        if (changedProfiles.size() > 1) {
            hotspots.add("multi-profile divergence");
        }
        if (highlights.stream().anyMatch(highlight -> "scenario drift".equals(highlight.category()))) {
            hotspots.add("scenario drift vs baseline");
        }
        return hotspots;
    }

    private static List<Highlight> orderedHighlights(List<Highlight> highlights) {
        return highlights.stream()
            .sorted(Comparator
                .comparingInt((Highlight highlight) -> -severityRank(highlight.severity()))
                .thenComparing(Highlight::category)
                .thenComparing(Highlight::executionStrategy)
                .thenComparing(Highlight::policyProfile))
            .toList();
    }

    private static List<StrategyDelta> orderedStrategyDeltas(List<StrategyDelta> deltas) {
        return deltas.stream()
            .sorted(Comparator
                .comparingInt((StrategyDelta delta) -> -severityRank(delta.severity()))
                .thenComparing(StrategyDelta::executionStrategy)
                .thenComparing(StrategyDelta::policyProfile))
            .toList();
    }

    private static void appendTopChanges(StringBuilder builder, Comparison comparison) {
        List<TopChange> topChanges = comparison.scenarioDeltas().stream()
            .flatMap(scenario -> orderedHighlights(scenario.highlights()).stream()
                .map(highlight -> new TopChange(
                    scenario.name(),
                    scenario.displayName(),
                    scenario.taxonomy(),
                    highlight
                )))
            .sorted(Comparator
                .comparingInt((TopChange change) -> -severityRank(change.highlight().severity()))
                .thenComparing(change -> change.scenarioName())
                .thenComparing(change -> change.highlight().executionStrategy())
                .thenComparing(change -> change.highlight().policyProfile()))
            .limit(8)
            .toList();
        if (topChanges.isEmpty()) {
            return;
        }
        builder.append(System.lineSeparator())
            .append("## Top changes (inspect first)").append(System.lineSeparator()).append(System.lineSeparator());
        for (TopChange topChange : topChanges) {
            builder.append("- [").append(topChange.highlight().severity()).append("] ")
                .append(topChange.scenarioDisplayName()).append(" (`").append(topChange.scenarioName()).append("`)")
                .append(" — ").append(topChange.highlight().executionStrategy()).append("/")
                .append(topChange.highlight().policyProfile()).append(": ")
                .append(topChange.highlight().message())
                .append(" (focus: ").append(topChange.highlight().reviewFocus()).append(")")
                .append(System.lineSeparator());
        }
    }

    private static void appendHotspots(StringBuilder builder, Comparison comparison) {
        List<ScenarioDelta> hotspots = comparison.scenarioDeltas().stream()
            .filter(scenario -> severityRank(scenario.severity()) >= severityRank("cautionary"))
            .limit(5)
            .toList();
        if (hotspots.isEmpty()) {
            return;
        }
        builder.append(System.lineSeparator())
            .append("## Hotspots").append(System.lineSeparator()).append(System.lineSeparator());
        for (ScenarioDelta hotspot : hotspots) {
            builder.append("- ").append(hotspot.displayName()).append(" (`").append(hotspot.name()).append("`)")
                .append(" — ").append(hotspot.severity())
                .append(" | ").append(String.join("; ", hotspot.reviewHotspots()))
                .append(System.lineSeparator());
        }
    }

    private static DashboardBenchmarkSummary summaryForStrategy(
        List<DashboardBenchmarkSummary> summaries,
        String strategy,
        String policyProfile
    ) {
        return summaries.stream()
            .filter(summary -> summary.executionStrategy().equals(strategy))
            .filter(summary -> summary.policyProfile().equals(policyProfile))
            .findFirst()
            .orElse(null);
    }

    private static List<DashboardBenchmarkSummary> orderedStrategies(List<DashboardBenchmarkSummary> summaries) {
        return summaries.stream()
            .sorted(Comparator.comparing(DashboardBenchmarkSummary::executionStrategy)
                .thenComparing(DashboardBenchmarkSummary::policyProfile))
            .toList();
    }

    private static String profileDiffExplanation(DashboardBenchmarkSummary variant, DashboardBenchmarkSummary balanced) {
        if (balanced == null || variant.policyProfile().equals("balanced")) {
            return "baseline";
        }
        long workDelta = variant.projectedWork().toMillis() - balanced.projectedWork().toMillis();
        int omitDelta = variant.omittedTasks().size() - balanced.omittedTasks().size();
        int fbDelta = variant.fallbackTasks().size() - balanced.fallbackTasks().size();
        int approxDelta = variant.approximatedTasks().size() - balanced.approximatedTasks().size();
        String profile = variant.policyProfile();
        if ("latency_first".equals(profile)) {
            if (omitDelta > 0) {
                return "omits optional work proactively to protect headroom (+" + omitDelta + " omit vs balanced)";
            }
            return "omits optional work at low ratio threshold; headroom preserved for mandatory steps";
        }
        if ("continuity".equals(profile)) {
            if (fbDelta > 0 || approxDelta > 0) {
                return "prefers degraded path over omission (+" + (fbDelta + approxDelta) + " fb/approx vs balanced)";
            }
            if (omitDelta < 0) {
                return "fewer omissions than balanced; fallback paths taken where balanced omits";
            }
            return "similar coverage to balanced; continuity preference active but not triggered here";
        }
        if ("efficiency".equals(profile)) {
            if (omitDelta > 0) {
                return "omits earlier to protect headroom (+" + omitDelta + " omit vs balanced)";
            }
            if (workDelta < 0) {
                return "leaner projected work (" + workDelta + "ms vs balanced) via earlier omission";
            }
            return "similar plan to balanced; efficiency threshold not reached in this scenario";
        }
        if (workDelta > 0) {
            return "more work than balanced (+" + workDelta + "ms); inspect trace for cause";
        }
        if (workDelta < 0) {
            return "less projected work (" + workDelta + "ms vs balanced); likely earlier omission";
        }
        return "equivalent plan to balanced in this scenario";
    }

    private static String deltaText(int baselineValue, int currentValue) {
        return baselineValue + " → " + currentValue + " (" + signed(currentValue - baselineValue) + ")";
    }

    private static String dispositionDeltaText(Map<String, Integer> baseline, Map<String, Integer> current) {
        List<String> keys = new ArrayList<>();
        keys.addAll(baseline.keySet());
        current.keySet().stream().filter(key -> !keys.contains(key)).forEach(keys::add);
        return keys.stream()
            .map(key -> key + "=" + baseline.getOrDefault(key, 0) + " → " + current.getOrDefault(key, 0)
                + " (" + signed(current.getOrDefault(key, 0) - baseline.getOrDefault(key, 0)) + ")")
            .collect(Collectors.joining(", "));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static void writeString(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to write artifact: " + path, ioException);
        }
    }

    private static String sanitizeName(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9._-]+", "-");
    }

    private static String key(String executionStrategy, String policyProfile) {
        return executionStrategy + "|" + policyProfile;
    }

    record Snapshot(
        String packName,
        String packDescription,
        SummarySnapshot summary,
        List<ScenarioSnapshot> scenarios
    ) {
    }

    record SummarySnapshot(
        int scenariosCompared,
        int adaptiveLowerProjectedWorkCount,
        int adaptiveDegradedCount,
        int baselineConvergenceCount,
        int adaptivePlanChangeCount,
        int profileComparisonScenarioCount,
        Map<String, Integer> scorecardDispositions
    ) {
    }

    record ScenarioSnapshot(
        String name,
        String displayName,
        DashboardBenchmarkScenario.ScenarioTaxonomy taxonomy,
        List<StrategySnapshot> strategies,
        List<ScorecardSnapshot> scorecards,
        List<ProfileComparisonSnapshot> profileComparisons
    ) {
    }

    record StrategySnapshot(
        String executionStrategy,
        String policyProfile,
        int executedTasks,
        boolean degraded,
        long projectedWorkMs,
        List<String> omittedTasks,
        List<String> fallbackTasks,
        List<String> approximatedTasks,
        List<String> degradationReasons
    ) {
    }

    record ScorecardSnapshot(
        String executionStrategy,
        String policyProfile,
        boolean mandatoryWorkPreserved,
        boolean optionalAlignment,
        boolean fallbackAlignment,
        boolean intentMatched,
        String assessment,
        String rationale
    ) {
    }

    record ProfileComparisonSnapshot(
        String policyProfile,
        int executedTasks,
        int fallbackCount,
        int approximatedCount,
        int omittedCount,
        boolean degraded,
        long headroomMs,
        String interpretation
    ) {
    }

    record Comparison(
        String baselineLabel,
        SummarySnapshot baselineSummary,
        SummarySnapshot currentSummary,
        List<ScenarioDelta> scenarioDeltas,
        DeltaSummary deltaSummary
    ) {
    }

    record DeltaSummary(
        int changedScenarioCount,
        int changedScorecardCount,
        int regressionRiskCount,
        int cautionaryCount,
        int informativeCount,
        int expectedCount
    ) {
    }

    record ScenarioDelta(
        String name,
        String displayName,
        DashboardBenchmarkScenario.ScenarioTaxonomy taxonomy,
        String severity,
        String prioritySummary,
        List<String> reviewHotspots,
        List<StrategyDelta> strategyDeltas,
        List<ScorecardDelta> scorecardDeltas,
        List<ProfileInterpretationDelta> profileInterpretationDeltas,
        List<Highlight> highlights
    ) {
    }

    record StrategyDelta(
        String executionStrategy,
        String policyProfile,
        int executedTaskDelta,
        int fallbackDelta,
        int approximatedDelta,
        int omittedDelta,
        boolean baselineDegraded,
        boolean currentDegraded,
        long baselineProjectedWorkMs,
        long currentProjectedWorkMs,
        String severity,
        String triageCategory,
        String likelyExplanation,
        String reviewFocus
    ) {
    }

    record ScorecardDelta(
        String executionStrategy,
        String policyProfile,
        String baselineAssessment,
        String currentAssessment,
        String note
    ) {
    }

    record ProfileInterpretationDelta(
        String policyProfile,
        String baselineInterpretation,
        String currentInterpretation
    ) {
    }

    record Highlight(
        String severity,
        String category,
        String executionStrategy,
        String policyProfile,
        String likelyExplanation,
        String reviewFocus,
        String message
    ) {
    }

    private record DeltaTriage(
        String severity,
        String category,
        String likelyExplanation,
        String reviewFocus
    ) {
    }

    private record TopChange(
        String scenarioName,
        String scenarioDisplayName,
        DashboardBenchmarkScenario.ScenarioTaxonomy taxonomy,
        Highlight highlight
    ) {
    }
}
