package com.budgetflow.demo.fintech.benchmark;

import com.budgetflow.demo.fintech.dashboard.DashboardTaskSpecs;

import java.util.List;

/**
 * Lightweight, explainable scorecard logic derived from scenario metadata and observed planner outcomes.
 */
public final class ScenarioAssessmentScorer {
    private ScenarioAssessmentScorer() {
    }

    public static List<ScenarioScorecard> scorecards(List<DashboardBenchmarkSummary> scenarioSummaries) {
        return scenarioSummaries.stream()
            .map(summary -> score(summary, scenarioSummaries))
            .toList();
    }

    public static ScenarioScorecard score(
        DashboardBenchmarkSummary summary,
        List<DashboardBenchmarkSummary> scenarioSummaries
    ) {
        DashboardBenchmarkScenario scenario = summary.scenario();
        boolean mandatoryPreserved = mandatoryWorkPreserved(summary);
        boolean optionalAligned = optionalAlignment(summary, scenario, scenarioSummaries);
        boolean fallbackAligned = fallbackAlignment(summary, scenario, scenarioSummaries);
        boolean intentMatched = mandatoryPreserved && optionalAligned && fallbackAligned;
        AssessmentDisposition disposition = disposition(summary, scenario, mandatoryPreserved, optionalAligned, fallbackAligned);
        String rationale = rationale(summary, scenario, mandatoryPreserved, optionalAligned, fallbackAligned, disposition);
        return new ScenarioScorecard(
            summary.executionStrategy(),
            summary.policyProfile(),
            mandatoryPreserved,
            optionalAligned,
            fallbackAligned,
            intentMatched,
            disposition,
            rationale
        );
    }

    private static boolean mandatoryWorkPreserved(DashboardBenchmarkSummary summary) {
        return !summary.omittedTasks().contains(DashboardTaskSpecs.BALANCE_TASK)
            && !summary.omittedTasks().contains(DashboardTaskSpecs.TRANSACTIONS_TASK);
    }

    private static boolean optionalAlignment(
        DashboardBenchmarkSummary summary,
        DashboardBenchmarkScenario scenario,
        List<DashboardBenchmarkSummary> scenarioSummaries
    ) {
        if ("agent_coordination_healthy".equals(scenario.name())) {
            return !summary.degraded() || !summary.omittedTasks().contains(DashboardTaskSpecs.INSIGHTS_TASK);
        }
        if ("agent_coordination_degraded_cascade".equals(scenario.name())) {
            return summary.degraded() && summary.omittedTasks().contains(DashboardTaskSpecs.INSIGHTS_TASK);
        }
        if ("agent_profile_comparison".equals(scenario.name())
            && "budgetflow_adaptive".equals(summary.executionStrategy())) {
            DashboardBenchmarkSummary balanced = balancedSummary(scenarioSummaries);
            if ("latency_first".equals(summary.policyProfile())) {
                return balanced == null
                    ? !summary.omittedTasks().isEmpty()
                    : summary.omittedTasks().size() >= balanced.omittedTasks().size();
            }
            if ("efficiency".equals(summary.policyProfile())) {
                return balanced == null
                    ? !summary.omittedTasks().isEmpty() || summary.projectedWork().toMillis() <= summary.requestBudget().toMillis()
                    : summary.omittedTasks().size() >= balanced.omittedTasks().size()
                        || summary.projectedWork().compareTo(balanced.projectedWork()) <= 0;
            }
            if ("continuity".equals(summary.policyProfile())) {
                return balanced == null
                    ? !summary.fallbackTasks().isEmpty() || !summary.approximatedTasks().isEmpty()
                    : summary.omittedTasks().size() <= balanced.omittedTasks().size();
            }
            return true;
        }
        if (scenario.whatToObserve().toLowerCase().contains("optional work should be pruned first")) {
            return summary.omittedTasks().contains(DashboardTaskSpecs.INSIGHTS_TASK)
                || summary.approximatedTasks().contains(DashboardTaskSpecs.OFFERS_TASK)
                || summary.fallbackTasks().contains(DashboardTaskSpecs.OFFERS_TASK);
        }
        return true;
    }

    private static boolean fallbackAlignment(
        DashboardBenchmarkSummary summary,
        DashboardBenchmarkScenario scenario,
        List<DashboardBenchmarkSummary> scenarioSummaries
    ) {
        if ("agent_coordination_healthy".equals(scenario.name())) {
            return !summary.fallbackTasks().contains(DashboardTaskSpecs.REWARDS_TASK);
        }
        if ("agent_coordination_degraded_cascade".equals(scenario.name())) {
            return summary.fallbackTasks().contains(DashboardTaskSpecs.REWARDS_TASK);
        }
        if ("agent_profile_comparison".equals(scenario.name())
            && "budgetflow_adaptive".equals(summary.executionStrategy())) {
            DashboardBenchmarkSummary balanced = balancedSummary(scenarioSummaries);
            if ("continuity".equals(summary.policyProfile())) {
                return balanced == null
                    ? !summary.fallbackTasks().isEmpty() || !summary.approximatedTasks().isEmpty()
                    : summary.fallbackTasks().size() + summary.approximatedTasks().size()
                        >= balanced.fallbackTasks().size() + balanced.approximatedTasks().size();
            }
            if ("latency_first".equals(summary.policyProfile())) {
                return summary.fallbackTasks().stream()
                    .noneMatch(task -> DashboardTaskSpecs.OFFERS_TASK.equals(task) || DashboardTaskSpecs.INSIGHTS_TASK.equals(task));
            }
        }
        return true;
    }

    private static DashboardBenchmarkSummary balancedSummary(List<DashboardBenchmarkSummary> scenarioSummaries) {
        return scenarioSummaries.stream()
            .filter(summary -> "budgetflow_adaptive".equals(summary.executionStrategy()))
            .filter(summary -> "balanced".equals(summary.policyProfile()))
            .findFirst()
            .orElse(null);
    }

    private static AssessmentDisposition disposition(
        DashboardBenchmarkSummary summary,
        DashboardBenchmarkScenario scenario,
        boolean mandatoryPreserved,
        boolean optionalAligned,
        boolean fallbackAligned
    ) {
        if (!mandatoryPreserved) {
            return AssessmentDisposition.MISMATCHED;
        }
        if (mandatoryPreserved && optionalAligned && fallbackAligned) {
            return AssessmentDisposition.EXPECTED;
        }
        if (!"agent".equals(scenario.packName())) {
            return AssessmentDisposition.ACCEPTABLE;
        }
        if (summary.degraded()) {
            return AssessmentDisposition.CAUTIONARY;
        }
        return AssessmentDisposition.ACCEPTABLE;
    }

    private static String rationale(
        DashboardBenchmarkSummary summary,
        DashboardBenchmarkScenario scenario,
        boolean mandatoryPreserved,
        boolean optionalAligned,
        boolean fallbackAligned,
        AssessmentDisposition disposition
    ) {
        return "mandatory="
            + yesNo(mandatoryPreserved)
            + ", optional_alignment="
            + yesNo(optionalAligned)
            + ", fallback_alignment="
            + yesNo(fallbackAligned)
            + ", observed_degraded="
            + summary.degraded()
            + ", intent='"
            + scenario.whatToObserve()
            + "', assessment="
            + disposition.label();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    public enum AssessmentDisposition {
        EXPECTED("expected"),
        ACCEPTABLE("acceptable"),
        CAUTIONARY("cautionary"),
        MISMATCHED("mismatched");

        private final String label;

        AssessmentDisposition(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record ScenarioScorecard(
        String executionStrategy,
        String policyProfile,
        boolean mandatoryWorkPreserved,
        boolean optionalAlignment,
        boolean fallbackAlignment,
        boolean intentMatched,
        AssessmentDisposition disposition,
        String rationale
    ) {
    }
}
