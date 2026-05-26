package com.budgetflow.demo.fintech.benchmark;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DashboardBenchmarkFormatter {
    private DashboardBenchmarkFormatter() {
    }

    public static String format(List<DashboardBenchmarkSummary> summaries) {
        return format(
            new DashboardScenarioPack(
                "custom",
                "Ad hoc scenario selection.",
                "targeted local exploration with your own scenario subset",
                "./gradlew :budgetflow-demo-fintech:runDashboardComparison",
                List.of()
            ),
            summaries
        );
    }

    public static String format(DashboardScenarioPack pack, List<DashboardBenchmarkSummary> summaries) {
        StringBuilder builder = new StringBuilder();
        builder.append("BudgetFlow dashboard comparison").append(System.lineSeparator());
        builder.append("Pack: ").append(pack.name()).append(" — ").append(pack.description()).append(System.lineSeparator());
        builder.append("Best for: ").append(pack.bestFor()).append(System.lineSeparator());
        builder.append("Suggested run: ").append(pack.suggestedCommand()).append(System.lineSeparator());
        builder.append("Evaluation entry: ./gradlew :budgetflow-demo-fintech:runDashboardWalkthrough")
            .append(System.lineSeparator());
        builder.append("Prototype comparison output only; not a rigorous benchmark suite.")
            .append(System.lineSeparator())
            .append(System.lineSeparator());

        for (Map.Entry<String, List<DashboardBenchmarkSummary>> entry : summariesByScenario(summaries).entrySet()) {
            List<DashboardBenchmarkSummary> scenarioSummaries = entry.getValue();
            DashboardBenchmarkScenario scenario = scenarioSummaries.get(0).scenario();
            builder.append("Scenario: ").append(scenario.name())
                .append(" — ").append(scenario.displayName()).append(System.lineSeparator());
            builder.append("Narrative: ").append(scenario.narrative()).append(System.lineSeparator());
            builder.append("Focus: ").append(scenario.evaluationFocus()).append(System.lineSeparator());
            builder.append("Interpretation: ").append(scenario.interpretationGuidance()).append(System.lineSeparator());
            builder.append("Pattern: ").append(scenario.realWorldPattern()).append(System.lineSeparator());
            builder.append("Observe: ").append(scenario.whatToObserve()).append(System.lineSeparator());
            builder.append("Budget profile: ").append(scenario.budgetProfile())
                .append(" | Pressure profile: ").append(scenario.pressureProfile()).append(System.lineSeparator());
            builder.append("Request budget: ").append(scenario.requestBudget().toMillis()).append("ms")
                .append(" | Pressure: ").append(scenario.pressureSummary()).append(System.lineSeparator());
            builder.append("Strategy | Policy | Executed | Degraded | Work | Omitted | Fallback | Approx | Why")
                .append(System.lineSeparator());
            builder.append("-------- | ------ | -------- | -------- | ---- | ------- | -------- | ------ | ---")
                .append(System.lineSeparator());

            for (DashboardBenchmarkSummary summary : orderedStrategies(scenarioSummaries)) {
                builder.append(summary.executionStrategy()).append(" | ")
                    .append(summary.policyProfile()).append(" | ")
                    .append(summary.totalTasksExecuted()).append(" | ")
                    .append(summary.degraded()).append(" | ")
                    .append(summary.requestBudget().toMillis()).append("ms/")
                    .append(summary.projectedWork().toMillis()).append("ms | ")
                    .append(formatList(summary.omittedTasks())).append(" | ")
                    .append(formatList(summary.fallbackTasks())).append(" | ")
                    .append(formatList(summary.approximatedTasks())).append(" | ")
                    .append(formatReasons(summary.degradationReasons()))
                    .append(System.lineSeparator());
            }

            DashboardBenchmarkSummary naive = summaryForStrategy(scenarioSummaries, "naive_parallel", "-");
            DashboardBenchmarkSummary adaptive = summaryForStrategy(scenarioSummaries, "budgetflow_adaptive", "balanced");
            if (naive != null && adaptive != null) {
                long savings = naive.projectedWork().minus(adaptive.projectedWork()).toMillis();
                int executedDelta = adaptive.totalTasksExecuted() - naive.totalTasksExecuted();
                builder.append("Comparison: adaptive projected work delta=")
                    .append(savings >= 0 ? "-" : "+")
                    .append(Math.abs(savings)).append("ms")
                    .append(", executed_task_delta=").append(executedDelta)
                    .append(", adaptive_changes=")
                    .append(compactAdaptiveChanges(adaptive))
                    .append(System.lineSeparator());
                builder.append("Comparison takeaway: ")
                    .append(comparisonTakeaway(naive, adaptive))
                    .append(System.lineSeparator());
            }

            DashboardBenchmarkSummary balanced = summaryForStrategy(scenarioSummaries, "budgetflow_adaptive", "balanced");
            List<DashboardBenchmarkSummary> adaptiveVariants = scenarioSummaries.stream()
                .filter(summary -> summary.executionStrategy().equals("budgetflow_adaptive"))
                .sorted(Comparator.comparing(DashboardBenchmarkSummary::policyProfile))
                .toList();
            if (balanced != null && adaptiveVariants.size() > 1) {
                for (DashboardBenchmarkSummary variant : adaptiveVariants) {
                    if (variant.policyProfile().equals("balanced")) {
                        continue;
                    }
                    long projectedDelta = variant.projectedWork().minus(balanced.projectedWork()).toMillis();
                    int executedDelta = variant.totalTasksExecuted() - balanced.totalTasksExecuted();
                    builder.append("Policy delta vs balanced (")
                        .append(variant.policyProfile())
                        .append("): projected_work_delta=")
                        .append(projectedDelta >= 0 ? "+" : "-")
                        .append(Math.abs(projectedDelta))
                        .append("ms, executed_task_delta=")
                        .append(executedDelta)
                        .append(", adaptive_changes=")
                        .append(compactAdaptiveChanges(variant))
                        .append(System.lineSeparator());
                }
            }
            if (adaptiveVariants.size() > 1) {
                builder.append("Profile comparison summary:")
                    .append(System.lineSeparator())
                    .append(profileComparisonSummary(adaptiveVariants, scenario.requestBudget().toMillis()))
                    .append(System.lineSeparator());
            }
            builder.append("Scenario summary: ")
                .append(scenarioSummary(naive, balanced))
                .append(System.lineSeparator());
            builder.append("Profile guidance: ")
                .append(profileGuidance(adaptiveVariants))
                .append(System.lineSeparator());
            builder.append("Evaluator next step: ")
                .append(evaluatorNextStep(scenario, adaptiveVariants))
                .append(System.lineSeparator());
            builder.append(System.lineSeparator());
        }

        builder.append("Confidence summary: ")
            .append(confidenceSummaryText(summaries))
            .append(System.lineSeparator())
            .append("Prototype reminder: comparison output is for exploratory evaluation, not benchmark certification.");
        return builder.toString().trim();
    }

    public static String formatJson(DashboardScenarioPack pack, List<DashboardBenchmarkSummary> summaries) {
        ConfidenceSummary confidenceSummary = confidenceSummary(summaries);
        StringBuilder builder = new StringBuilder();
        builder.append("{")
            .append("\"tool\":\"budgetflow_dashboard_comparison\",")
            .append("\"prototype\":true,")
            .append("\"benchmark\":false,")
            .append("\"scenarioPack\":{")
            .append("\"name\":\"").append(escape(pack.name())).append("\",")
            .append("\"description\":\"").append(escape(pack.description())).append("\",")
            .append("\"bestFor\":\"").append(escape(pack.bestFor())).append("\",")
            .append("\"suggestedCommand\":\"").append(escape(pack.suggestedCommand())).append("\"")
            .append("},")
            .append("\"confidenceSummary\":")
            .append(confidenceSummaryJson(confidenceSummary))
            .append(",")
            .append("\"scenarios\":[");

        List<List<DashboardBenchmarkSummary>> grouped = List.copyOf(summariesByScenario(summaries).values());
        for (int index = 0; index < grouped.size(); index++) {
            List<DashboardBenchmarkSummary> scenarioSummaries = grouped.get(index);
            DashboardBenchmarkScenario scenario = scenarioSummaries.get(0).scenario();
            DashboardBenchmarkSummary naive = summaryForStrategy(scenarioSummaries, "naive_parallel", "-");
            DashboardBenchmarkSummary adaptive = summaryForStrategy(scenarioSummaries, "budgetflow_adaptive", "balanced");
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{")
                .append("\"name\":\"").append(escape(scenario.name())).append("\",")
                .append("\"displayName\":\"").append(escape(scenario.displayName())).append("\",")
                .append("\"narrative\":\"").append(escape(scenario.narrative())).append("\",")
                .append("\"evaluationFocus\":\"").append(escape(scenario.evaluationFocus())).append("\",")
                .append("\"interpretationGuidance\":\"").append(escape(scenario.interpretationGuidance())).append("\",")
                .append("\"realWorldPattern\":\"").append(escape(scenario.realWorldPattern())).append("\",")
                .append("\"whatToObserve\":\"").append(escape(scenario.whatToObserve())).append("\",")
                .append("\"budgetProfile\":\"").append(escape(scenario.budgetProfile())).append("\",")
                .append("\"pressureProfile\":\"").append(escape(scenario.pressureProfile())).append("\",")
                .append("\"requestBudgetMs\":").append(scenario.requestBudget().toMillis()).append(",")
                .append("\"pressure\":{")
                .append("\"executorUtilization\":").append(scenario.pressureSnapshot().executorUtilization()).append(",")
                .append("\"dbPressure\":").append(scenario.pressureSnapshot().dbPressure()).append(",")
                .append("\"downstreamPressure\":").append(scenario.pressureSnapshot().downstreamPressure())
                .append("},")
                .append("\"strategies\":")
                .append(jsonStrategies(scenarioSummaries))
                .append(",")
                .append("\"comparison\":")
                .append(comparisonJson(naive, adaptive))
                .append(",")
                .append("\"comparisonTakeaway\":\"")
                .append(escape(comparisonTakeaway(naive, adaptive)))
                .append("\",")
                .append("\"profileSummary\":")
                .append(profileSummaryJson(scenarioSummaries))
                .append(",")
                .append("\"profileComparisonSummary\":")
                .append(profileComparisonSummaryJson(scenarioSummaries, scenario.requestBudget().toMillis()))
                .append(",")
                .append("\"profileGuidance\":\"")
                .append(escape(profileGuidance(scenarioSummaries.stream()
                    .filter(summary -> summary.executionStrategy().equals("budgetflow_adaptive"))
                    .sorted(Comparator.comparing(DashboardBenchmarkSummary::policyProfile))
                    .toList())))
                .append("\",")
                .append("\"evaluatorNextStep\":\"")
                .append(escape(evaluatorNextStep(
                    scenario,
                    scenarioSummaries.stream()
                        .filter(summary -> summary.executionStrategy().equals("budgetflow_adaptive"))
                        .sorted(Comparator.comparing(DashboardBenchmarkSummary::policyProfile))
                        .toList()
                )))
                .append("\"")
                .append("}");
        }

        builder.append("]}");
        return builder.toString();
    }

    private static Map<String, List<DashboardBenchmarkSummary>> summariesByScenario(List<DashboardBenchmarkSummary> summaries) {
        return summaries.stream()
            .collect(Collectors.groupingBy(
                DashboardBenchmarkSummary::scenarioName,
                LinkedHashMap::new,
                Collectors.toList()
            ));
    }

    private static List<DashboardBenchmarkSummary> orderedStrategies(List<DashboardBenchmarkSummary> summaries) {
        return summaries.stream()
            .sorted(Comparator.comparing(DashboardBenchmarkSummary::executionStrategy)
                .thenComparing(DashboardBenchmarkSummary::policyProfile))
            .toList();
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

    private static String strategyJson(DashboardBenchmarkSummary summary) {
        return "{"
            + "\"name\":\"" + escape(summary.executionStrategy()) + "\","
            + "\"policyProfile\":\"" + escape(summary.policyProfile()) + "\","
            + "\"executedTasks\":" + summary.totalTasksExecuted() + ","
            + "\"degraded\":" + summary.degraded() + ","
            + "\"projectedWorkMs\":" + summary.projectedWork().toMillis() + ","
            + "\"omittedTasks\":" + jsonArray(summary.omittedTasks()) + ","
            + "\"fallbackTasks\":" + jsonArray(summary.fallbackTasks()) + ","
            + "\"approximatedTasks\":" + jsonArray(summary.approximatedTasks()) + ","
            + "\"degradationReasons\":" + jsonArray(summary.degradationReasons())
            + "}";
    }

    private static String compactAdaptiveChanges(DashboardBenchmarkSummary adaptive) {
        if (!adaptive.degraded()) {
            return "none";
        }
        return java.util.stream.Stream.of(
                adaptive.omittedTasks().isEmpty() ? null : "omit=" + String.join(",", adaptive.omittedTasks()),
                adaptive.fallbackTasks().isEmpty() ? null : "fallback=" + String.join(",", adaptive.fallbackTasks()),
                adaptive.approximatedTasks().isEmpty() ? null : "approx=" + String.join(",", adaptive.approximatedTasks())
            )
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.joining("; "));
    }

    private static String jsonStrategies(List<DashboardBenchmarkSummary> summaries) {
        return orderedStrategies(summaries).stream()
            .map(DashboardBenchmarkFormatter::strategyJson)
            .collect(Collectors.joining(",", "[", "]"));
    }

    private static String comparisonJson(DashboardBenchmarkSummary naive, DashboardBenchmarkSummary adaptive) {
        if (naive == null || adaptive == null) {
            return "null";
        }
        return "{"
            + "\"projectedWorkSavingsMs\":" + naive.projectedWork().minus(adaptive.projectedWork()).toMillis() + ","
            + "\"executedTaskDelta\":" + (adaptive.totalTasksExecuted() - naive.totalTasksExecuted()) + ","
            + "\"adaptiveChanges\":\"" + escape(compactAdaptiveChanges(adaptive)) + "\""
            + "}";
    }

    private static String comparisonTakeaway(DashboardBenchmarkSummary naive, DashboardBenchmarkSummary adaptive) {
        if (naive == null || adaptive == null) {
            return "Balanced adaptive summary unavailable for this scenario.";
        }
        long projectedDelta = naive.projectedWork().minus(adaptive.projectedWork()).toMillis();
        if (!adaptive.degraded() && !naive.degraded() && projectedDelta == 0) {
            return "Adaptive and naive stayed aligned here, which is the expected control-case signal.";
        }
        if (!adaptive.degraded() && projectedDelta > 0) {
            return "Adaptive reduced projected work without degrading the response shape.";
        }
        if (adaptive.degraded() && projectedDelta > 0) {
            return "Adaptive traded optional fidelity for "
                + projectedDelta
                + "ms of projected work savings while keeping the change set explicit ("
                + compactAdaptiveChanges(adaptive)
                + ").";
        }
        if (adaptive.degraded()) {
            return "Adaptive degraded response shape even though projected work did not fall below naive; inspect scenario guidance and trace reasons.";
        }
        return "Adaptive kept the response intact; inspect trace reasons if you expected a stronger divergence.";
    }

    private static String scenarioSummary(DashboardBenchmarkSummary naive, DashboardBenchmarkSummary balanced) {
        if (naive == null || balanced == null) {
            return "balanced profile summary unavailable for this scenario.";
        }
        long savings = naive.projectedWork().minus(balanced.projectedWork()).toMillis();
        return "balanced projected_work_savings="
            + savings
            + "ms_vs_naive, degraded="
            + balanced.degraded()
            + ", omitted="
            + formatList(balanced.omittedTasks())
            + ", fallback="
            + formatList(balanced.fallbackTasks())
            + ", approx="
            + formatList(balanced.approximatedTasks());
    }

    private static String profileGuidance(List<DashboardBenchmarkSummary> adaptiveVariants) {
        DashboardBenchmarkSummary balanced = adaptiveVariants.stream()
            .filter(summary -> summary.policyProfile().equals("balanced"))
            .findFirst()
            .orElse(null);
        if (balanced == null) {
            return "Use balanced as the default profile for first adoption; compare continuity and efficiency only when tradeoffs matter.";
        }

        DashboardBenchmarkSummary continuity = adaptiveVariants.stream()
            .filter(summary -> summary.policyProfile().equals("continuity"))
            .findFirst()
            .orElse(null);
        DashboardBenchmarkSummary efficiency = adaptiveVariants.stream()
            .filter(summary -> summary.policyProfile().equals("efficiency"))
            .findFirst()
            .orElse(null);
        DashboardBenchmarkSummary latencyFirst = adaptiveVariants.stream()
            .filter(summary -> summary.policyProfile().equals("latency_first"))
            .findFirst()
            .orElse(null);

        StringBuilder guidance = new StringBuilder(
            "Default to balanced for most traffic; it keeps behavior conservative and explainable."
        );
        if (continuity != null) {
            int continuityExecutedDelta = continuity.totalTasksExecuted() - balanced.totalTasksExecuted();
            guidance.append(" Choose continuity when preserving optional response coverage matters")
                .append(" (executed_task_delta_vs_balanced=")
                .append(continuityExecutedDelta)
                .append(").");
        }
        if (efficiency != null) {
            long efficiencyWorkDelta = efficiency.projectedWork().minus(balanced.projectedWork()).toMillis();
            guidance.append(" Choose efficiency when stricter latency headroom is worth dropping optional work earlier")
                .append(" (projected_work_delta_vs_balanced=")
                .append(efficiencyWorkDelta >= 0 ? "+" : "")
                .append(efficiencyWorkDelta)
                .append("ms).");
        }
        if (latencyFirst != null) {
            int latencyOmitDelta = latencyFirst.omittedTasks().size() - balanced.omittedTasks().size();
            long latencyHeadroom = latencyFirst.requestBudget().toMillis() - latencyFirst.projectedWork().toMillis();
            guidance.append(" Use latency_first for real-time or high-frequency agent turns where remaining budget headroom is the priority")
                .append("; omits optional work proactively (omit_delta_vs_balanced=")
                .append(latencyOmitDelta >= 0 ? "+" : "")
                .append(latencyOmitDelta)
                .append(", remaining_headroom=")
                .append(latencyHeadroom)
                .append("ms).")
                .append(" Note: lower optional coverage is not a failure here — it is the intended tradeoff.");
        }
        return guidance.toString();
    }

    /**
     * Compact cross-profile comparison block: one row per adaptive profile showing executed/fallback/approx/omitted
     * counts, degraded yes/no, remaining budget headroom, and a short explanation of why the profile differs from balanced.
     */
    static String profileComparisonSummary(List<DashboardBenchmarkSummary> adaptiveVariants, long requestBudgetMs) {
        DashboardBenchmarkSummary balanced = adaptiveVariants.stream()
            .filter(s -> s.policyProfile().equals("balanced"))
            .findFirst()
            .orElse(null);
        StringBuilder sb = new StringBuilder();
        sb.append("  Profile          exec  fb    approx  omit  degraded  headroom  vs-balanced")
            .append(System.lineSeparator());
        sb.append("  ---------------  ----  ----  ------  ----  --------  --------  -----------")
            .append(System.lineSeparator());
        for (DashboardBenchmarkSummary s : adaptiveVariants) {
            long headroomMs = requestBudgetMs - s.projectedWork().toMillis();
            String why = profileDiffExplanation(s, balanced);
            sb.append("  ")
                .append(padRight(s.policyProfile(), 15)).append("  ")
                .append(padLeft(String.valueOf(s.totalTasksExecuted()), 4)).append("  ")
                .append(padLeft(String.valueOf(s.fallbackTasks().size()), 4)).append("  ")
                .append(padLeft(String.valueOf(s.approximatedTasks().size()), 6)).append("  ")
                .append(padLeft(String.valueOf(s.omittedTasks().size()), 4)).append("  ")
                .append(padLeft(s.degraded() ? "yes" : "no", 8)).append("  ")
                .append(padLeft(headroomMs + "ms", 8)).append("  ")
                .append(why)
                .append(System.lineSeparator());
        }
        return sb.toString();
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

    private static String padRight(String value, int width) {
        if (value == null) {
            value = "";
        }
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    private static String padLeft(String value, int width) {
        if (value == null) {
            value = "";
        }
        if (value.length() >= width) {
            return value;
        }
        return " ".repeat(width - value.length()) + value;
    }

    private static String profileSummaryJson(List<DashboardBenchmarkSummary> scenarioSummaries) {
        List<DashboardBenchmarkSummary> adaptiveVariants = scenarioSummaries.stream()
            .filter(summary -> summary.executionStrategy().equals("budgetflow_adaptive"))
            .sorted(Comparator.comparing(DashboardBenchmarkSummary::policyProfile))
            .toList();
        return adaptiveVariants.stream()
            .map(summary -> "{"
                + "\"policyProfile\":\"" + escape(summary.policyProfile()) + "\","
                + "\"executedTasks\":" + summary.totalTasksExecuted() + ","
                + "\"projectedWorkMs\":" + summary.projectedWork().toMillis() + ","
                + "\"omittedTasks\":" + jsonArray(summary.omittedTasks()) + ","
                + "\"fallbackTasks\":" + jsonArray(summary.fallbackTasks()) + ","
                + "\"approximatedTasks\":" + jsonArray(summary.approximatedTasks()) + ","
                + "\"degraded\":" + summary.degraded()
                + "}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    private static String profileComparisonSummaryJson(
        List<DashboardBenchmarkSummary> scenarioSummaries,
        long requestBudgetMs
    ) {
        List<DashboardBenchmarkSummary> adaptiveVariants = scenarioSummaries.stream()
            .filter(summary -> summary.executionStrategy().equals("budgetflow_adaptive"))
            .sorted(Comparator.comparing(DashboardBenchmarkSummary::policyProfile))
            .toList();
        if (adaptiveVariants.isEmpty()) {
            return "[]";
        }
        DashboardBenchmarkSummary balanced = adaptiveVariants.stream()
            .filter(s -> s.policyProfile().equals("balanced"))
            .findFirst()
            .orElse(null);
        return adaptiveVariants.stream()
            .map(s -> "{"
                + "\"policyProfile\":\"" + escape(s.policyProfile()) + "\","
                + "\"executedTasks\":" + s.totalTasksExecuted() + ","
                + "\"fallbackCount\":" + s.fallbackTasks().size() + ","
                + "\"approximatedCount\":" + s.approximatedTasks().size() + ","
                + "\"omittedCount\":" + s.omittedTasks().size() + ","
                + "\"degraded\":" + s.degraded() + ","
                + "\"headroomMs\":" + (requestBudgetMs - s.projectedWork().toMillis()) + ","
                + "\"whyDiffersFromBalanced\":\"" + escape(profileDiffExplanation(s, balanced)) + "\""
                + "}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    private static String confidenceSummaryText(List<DashboardBenchmarkSummary> summaries) {
        ConfidenceSummary summary = confidenceSummary(summaries);
        return "scenarios_compared="
            + summary.scenariosCompared()
            + ", adaptive_lower_projected_work="
            + summary.adaptiveLowerProjectedWorkCount()
            + "/"
            + summary.scenariosCompared()
            + ", adaptive_degraded="
            + summary.adaptiveDegradedCount()
            + "/"
            + summary.scenariosCompared()
            + ", baseline_convergence="
            + summary.baselineConvergenceCount()
            + "/"
            + summary.scenariosCompared()
            + ", adaptive_plan_changes="
            + summary.adaptivePlanChangeCount()
            + "/"
            + summary.scenariosCompared()
            + ", profile_comparison_scenarios="
            + summary.profileComparisonScenarioCount();
    }

    private static String evaluatorNextStep(
        DashboardBenchmarkScenario scenario,
        List<DashboardBenchmarkSummary> adaptiveVariants
    ) {
        DashboardBenchmarkSummary balanced = adaptiveVariants.stream()
            .filter(summary -> summary.policyProfile().equals("balanced"))
            .findFirst()
            .orElse(null);
        if (balanced == null) {
            return "Run the policy pack to compare balanced, continuity, and efficiency before changing defaults.";
        }
        DashboardBenchmarkSummary continuity = adaptiveVariants.stream()
            .filter(summary -> summary.policyProfile().equals("continuity"))
            .findFirst()
            .orElse(null);
        DashboardBenchmarkSummary efficiency = adaptiveVariants.stream()
            .filter(summary -> summary.policyProfile().equals("efficiency"))
            .findFirst()
            .orElse(null);
        if (continuity == null || efficiency == null) {
            return "Keep balanced for now and run --pack=policy when you need profile-selection evidence.";
        }
        int continuityCoverageDelta = continuity.totalTasksExecuted() - balanced.totalTasksExecuted();
        long efficiencyHeadroomDelta = balanced.projectedWork().minus(efficiency.projectedWork()).toMillis();
        return "For "
            + scenario.name()
            + ", keep balanced unless your endpoint requires continuity (+"
            + continuityCoverageDelta
            + " executed tasks vs balanced) or stricter headroom (efficiency saves "
            + efficiencyHeadroomDelta
            + "ms projected work).";
    }

    private static String formatReasons(List<String> reasons) {
        if (reasons.isEmpty()) {
            return "-";
        }
        return reasons.stream()
            .map(DashboardBenchmarkFormatter::compactReason)
            .collect(Collectors.joining(", "));
    }

    private static String compactReason(String reason) {
        int bracketStart = reason.indexOf('[');
        int bracketEnd = reason.lastIndexOf(']');
        if (bracketStart < 0 || bracketEnd <= bracketStart) {
            return reason;
        }
        String taskPrefix = "";
        int separator = reason.indexOf('=');
        if (separator > 0 && separator < bracketStart) {
            taskPrefix = reason.substring(0, separator + 1);
            reason = reason.substring(separator + 1);
            bracketStart = reason.indexOf('[');
            bracketEnd = reason.lastIndexOf(']');
        }
        String mode = reason.substring(0, bracketStart);
        Map<String, String> fields = java.util.Arrays.stream(reason.substring(bracketStart + 1, bracketEnd).split(","))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .map(token -> token.split("=", 2))
            .filter(parts -> parts.length == 2)
            .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (first, second) -> first, LinkedHashMap::new));
        String pressure = fields.getOrDefault("pressure", "unknown");
        String layer = fields.getOrDefault("layer", "unknown");
        String fit = fields.getOrDefault("fit", "unknown");
        String savings = fields.getOrDefault("savings", "unknown");
        return "%s%s[%s|%s|fit=%s|savings=%s]"
            .formatted(taskPrefix, mode, pressure, layer, fit, savings);
    }

    private static String confidenceSummaryJson(ConfidenceSummary summary) {
        return "{"
            + "\"scenariosCompared\":" + summary.scenariosCompared() + ","
            + "\"adaptiveLowerProjectedWorkCount\":" + summary.adaptiveLowerProjectedWorkCount() + ","
            + "\"adaptiveDegradedCount\":" + summary.adaptiveDegradedCount() + ","
            + "\"baselineConvergenceCount\":" + summary.baselineConvergenceCount() + ","
            + "\"adaptivePlanChangeCount\":" + summary.adaptivePlanChangeCount() + ","
            + "\"profileComparisonScenarioCount\":" + summary.profileComparisonScenarioCount()
            + "}";
    }

    private static ConfidenceSummary confidenceSummary(List<DashboardBenchmarkSummary> summaries) {
        int scenariosCompared = 0;
        int adaptiveLowerProjectedWorkCount = 0;
        int adaptiveDegradedCount = 0;
        int baselineConvergenceCount = 0;
        int adaptivePlanChangeCount = 0;
        int profileComparisonScenarioCount = 0;
        for (List<DashboardBenchmarkSummary> scenarioSummaries : summariesByScenario(summaries).values()) {
            DashboardBenchmarkSummary naive = summaryForStrategy(scenarioSummaries, "naive_parallel", "-");
            DashboardBenchmarkSummary adaptive = summaryForStrategy(scenarioSummaries, "budgetflow_adaptive", "balanced");
            long adaptiveVariantCount = scenarioSummaries.stream()
                .filter(summary -> summary.executionStrategy().equals("budgetflow_adaptive"))
                .count();
            if (adaptiveVariantCount > 1) {
                profileComparisonScenarioCount++;
            }
            if (naive == null || adaptive == null) {
                continue;
            }
            scenariosCompared++;
            if (adaptive.projectedWork().compareTo(naive.projectedWork()) < 0) {
                adaptiveLowerProjectedWorkCount++;
            }
            if (adaptive.degraded()) {
                adaptiveDegradedCount++;
            }
            if (!adaptive.degraded()
                && !naive.degraded()
                && adaptive.projectedWork().equals(naive.projectedWork())
                && adaptive.totalTasksExecuted() == naive.totalTasksExecuted()) {
                baselineConvergenceCount++;
            }
            if (adaptive.degraded()
                || adaptive.projectedWork().compareTo(naive.projectedWork()) != 0
                || adaptive.totalTasksExecuted() != naive.totalTasksExecuted()) {
                adaptivePlanChangeCount++;
            }
        }
        return new ConfidenceSummary(
            scenariosCompared,
            adaptiveLowerProjectedWorkCount,
            adaptiveDegradedCount,
            baselineConvergenceCount,
            adaptivePlanChangeCount,
            profileComparisonScenarioCount
        );
    }

    private static String formatList(List<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }

    private static String jsonArray(List<String> values) {
        return values.stream()
            .map(value -> "\"" + escape(value) + "\"")
            .collect(Collectors.joining(",", "[", "]"));
    }

    private static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private record ConfidenceSummary(
        int scenariosCompared,
        int adaptiveLowerProjectedWorkCount,
        int adaptiveDegradedCount,
        int baselineConvergenceCount,
        int adaptivePlanChangeCount,
        int profileComparisonScenarioCount
    ) {
    }
}
