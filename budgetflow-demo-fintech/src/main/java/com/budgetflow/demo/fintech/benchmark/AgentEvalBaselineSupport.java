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
        int regressions = 0;
        int improvements = 0;
        int expectedShifts = 0;
        int reviewChanges = 0;
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
            List<Highlight> highlights = buildHighlights(strategyDeltas, scorecardDeltas);

            if (strategyDeltas.isEmpty() && scorecardDeltas.isEmpty() && interpretationDeltas.isEmpty()) {
                continue;
            }

            for (Highlight highlight : highlights) {
                switch (highlight.kind()) {
                    case "regression" -> regressions++;
                    case "improvement" -> improvements++;
                    case "expected_shift" -> expectedShifts++;
                    default -> reviewChanges++;
                }
            }
            changedScorecards += scorecardDeltas.size();
            scenarioDeltas.add(new ScenarioDelta(
                currentScenario.name(),
                currentScenario.displayName(),
                currentScenario.taxonomy(),
                strategyDeltas,
                scorecardDeltas,
                interpretationDeltas,
                highlights
            ));
        }

        return new Comparison(
            baselineLabel,
            baseline.summary(),
            current.summary(),
            scenarioDeltas,
            new DeltaSummary(
                scenarioDeltas.size(),
                changedScorecards,
                regressions,
                improvements,
                expectedShifts,
                reviewChanges
            )
        );
    }

    static String formatDeltaMarkdown(Comparison comparison) {
        StringBuilder builder = new StringBuilder();
        builder.append("# BudgetFlow evidence delta").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Baseline: `").append(comparison.baselineLabel()).append("`").append(System.lineSeparator());
        builder.append("- Reviewer note: inspect regressions first, then expected profile-specific shifts, then neutral review changes.")
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
            .append(" | regressions: ").append(comparison.deltaSummary().regressions())
            .append(" | improvements: ").append(comparison.deltaSummary().improvements())
            .append(" | expected shifts: ").append(comparison.deltaSummary().expectedShifts())
            .append(" | review changes: ").append(comparison.deltaSummary().reviewChanges())
            .append(System.lineSeparator());

        for (ScenarioDelta scenario : comparison.scenarioDeltas()) {
            builder.append(System.lineSeparator())
                .append("## ").append(scenario.displayName())
                .append(" (`").append(scenario.name()).append("`)").append(System.lineSeparator()).append(System.lineSeparator());
            builder.append("- Taxonomy: ").append(scenario.taxonomy().summary()).append(System.lineSeparator());
            for (Highlight highlight : scenario.highlights()) {
                builder.append("- ").append(highlight.kind()).append(": ").append(highlight.message()).append(System.lineSeparator());
            }
            if (!scenario.strategyDeltas().isEmpty()) {
                builder.append(System.lineSeparator())
                    .append("| Strategy | Policy | Δexec | Δfb | Δapprox | Δomit | Degraded | Work | Kind |\n")
                    .append("|---|---|---:|---:|---:|---:|---|---|---|\n");
                for (StrategyDelta delta : scenario.strategyDeltas()) {
                    builder.append("| ").append(delta.executionStrategy())
                        .append(" | ").append(delta.policyProfile())
                        .append(" | ").append(signed(delta.executedTaskDelta()))
                        .append(" | ").append(signed(delta.fallbackDelta()))
                        .append(" | ").append(signed(delta.approximatedDelta()))
                        .append(" | ").append(signed(delta.omittedDelta()))
                        .append(" | ").append(delta.baselineDegraded()).append(" → ").append(delta.currentDegraded())
                        .append(" | ").append(delta.baselineProjectedWorkMs()).append("ms → ")
                        .append(delta.currentProjectedWorkMs()).append("ms")
                        .append(" | ").append(delta.kind())
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
                classifyChange(
                    current.executionStrategy(),
                    current.policyProfile(),
                    executedDelta,
                    fallbackDelta,
                    approximatedDelta,
                    omittedDelta,
                    baseline.degraded(),
                    current.degraded(),
                    workDelta
                )
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

    private static List<Highlight> buildHighlights(List<StrategyDelta> strategyDeltas, List<ScorecardDelta> scorecardDeltas) {
        List<Highlight> highlights = new ArrayList<>();
        for (ScorecardDelta delta : scorecardDeltas) {
            String kind = assessmentRank(delta.currentAssessment()) > assessmentRank(delta.baselineAssessment())
                ? "regression"
                : "improvement";
            highlights.add(new Highlight(
                kind,
                delta.executionStrategy() + "/" + delta.policyProfile()
                    + " assessment " + delta.baselineAssessment() + " → " + delta.currentAssessment()
            ));
        }
        for (StrategyDelta delta : strategyDeltas) {
            highlights.add(new Highlight(
                delta.kind(),
                delta.executionStrategy() + "/" + delta.policyProfile()
                    + " Δexec=" + signed(delta.executedTaskDelta())
                    + ", Δfb=" + signed(delta.fallbackDelta())
                    + ", Δapprox=" + signed(delta.approximatedDelta())
                    + ", Δomit=" + signed(delta.omittedDelta())
                    + ", work=" + delta.baselineProjectedWorkMs() + "ms → " + delta.currentProjectedWorkMs() + "ms"
                    + ", degraded=" + delta.baselineDegraded() + " → " + delta.currentDegraded()
            ));
        }
        return highlights.stream().distinct().toList();
    }

    private static String classifyChange(
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
            return "regression";
        }
        if (baselineDegraded && !currentDegraded) {
            return "improvement";
        }
        if (assessmentSensitiveRegression(executionStrategy, policyProfile, executedDelta, omittedDelta)) {
            return "regression";
        }
        if (executedDelta > 0 || omittedDelta < 0) {
            return "improvement";
        }
        if ("latency_first".equals(policyProfile) && omittedDelta > 0 && workDelta <= 0) {
            return "expected_shift";
        }
        if ("continuity".equals(policyProfile) && fallbackDelta + approximatedDelta > 0 && omittedDelta <= 0) {
            return "expected_shift";
        }
        if ("efficiency".equals(policyProfile) && (omittedDelta > 0 || workDelta < 0)) {
            return "expected_shift";
        }
        if (fallbackDelta != 0 || approximatedDelta != 0 || workDelta != 0) {
            return "review";
        }
        return "review";
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
        int regressions,
        int improvements,
        int expectedShifts,
        int reviewChanges
    ) {
    }

    record ScenarioDelta(
        String name,
        String displayName,
        DashboardBenchmarkScenario.ScenarioTaxonomy taxonomy,
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
        String kind
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
        String kind,
        String message
    ) {
    }
}
