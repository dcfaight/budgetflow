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
        return format(new DashboardScenarioPack("custom", "Ad hoc scenario selection.", List.of()), summaries);
    }

    public static String format(DashboardScenarioPack pack, List<DashboardBenchmarkSummary> summaries) {
        StringBuilder builder = new StringBuilder();
        builder.append("BudgetFlow dashboard comparison").append(System.lineSeparator());
        builder.append("Pack: ").append(pack.name()).append(" — ").append(pack.description()).append(System.lineSeparator());
        builder.append("Prototype comparison output only; not a rigorous benchmark suite.")
            .append(System.lineSeparator())
            .append(System.lineSeparator());

        for (Map.Entry<String, List<DashboardBenchmarkSummary>> entry : summariesByScenario(summaries).entrySet()) {
            List<DashboardBenchmarkSummary> scenarioSummaries = entry.getValue();
            DashboardBenchmarkScenario scenario = scenarioSummaries.get(0).scenario();
            builder.append("Scenario: ").append(scenario.name())
                .append(" — ").append(scenario.displayName()).append(System.lineSeparator());
            builder.append("Narrative: ").append(scenario.narrative()).append(System.lineSeparator());
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
                    .append(formatList(summary.degradationReasons()))
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
            builder.append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    public static String formatJson(DashboardScenarioPack pack, List<DashboardBenchmarkSummary> summaries) {
        StringBuilder builder = new StringBuilder();
        builder.append("{")
            .append("\"tool\":\"budgetflow_dashboard_comparison\",")
            .append("\"prototype\":true,")
            .append("\"benchmark\":false,")
            .append("\"scenarioPack\":{")
            .append("\"name\":\"").append(escape(pack.name())).append("\",")
            .append("\"description\":\"").append(escape(pack.description())).append("\"")
            .append("},")
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
}
