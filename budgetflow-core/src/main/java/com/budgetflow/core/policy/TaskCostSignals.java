package com.budgetflow.core.policy;

record TaskCostSignals(
    double primaryLatencyRatio,
    double fallbackLatencyRatio,
    double approximateLatencyRatio,
    double cheapestDegradedLatencyRatio,
    long degradedSavingsMillis,
    double degradedSavingsRatio,
    long primaryHeadroomMillis,
    boolean degradedPathAvailable,
    boolean primaryFitsBudget,
    boolean fallbackFitsBudget,
    boolean approximateFitsBudget,
    boolean cheapestDegradedFitsBudget,
    long primaryOverrunMillis,
    long fallbackOverrunMillis,
    long approximateOverrunMillis,
    long cheapestDegradedOverrunMillis
) {
}
