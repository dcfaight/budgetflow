package com.budgetflow.demo.fintech.benchmark;

import java.util.List;

public final class DashboardBenchmarkFormatter {
    private DashboardBenchmarkFormatter() {
    }

    public static String format(List<DashboardBenchmarkSummary> summaries) {
        StringBuilder builder = new StringBuilder();
        builder.append("Scenario | Strategy | Executed | Omitted | Fallback | Approximated | Degraded | Budget/Work | Pressure").append(System.lineSeparator());
        builder.append("-------- | -------- | -------- | ------- | -------- | ------------ | -------- | ----------- | --------").append(System.lineSeparator());
        for (DashboardBenchmarkSummary summary : summaries) {
            builder.append(summary.scenarioName()).append(" | ")
                .append(summary.executionStrategy()).append(" | ")
                .append(summary.totalTasksExecuted()).append(" | ")
                .append(formatList(summary.omittedTasks())).append(" | ")
                .append(formatList(summary.fallbackTasks())).append(" | ")
                .append(formatList(summary.approximatedTasks())).append(" | ")
                .append(summary.degraded()).append(" | ")
                .append(summary.requestBudget().toMillis()).append("ms/")
                .append(summary.projectedWork().toMillis()).append("ms | ")
                .append(formatPressure(summary))
                .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String formatList(List<String> values) {
        return values.isEmpty() ? "-" : String.join(",", values);
    }

    private static String formatPressure(DashboardBenchmarkSummary summary) {
        return String.format(
            "exec=%.2f db=%.2f down=%.2f",
            summary.pressureSnapshot().executorUtilization(),
            summary.pressureSnapshot().dbPressure(),
            summary.pressureSnapshot().downstreamPressure()
        );
    }
}
