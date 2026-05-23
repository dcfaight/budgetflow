package com.budgetflow.core.metadata;

import java.util.List;

public record DegradationMetadata(
    boolean degraded,
    List<String> omittedTasks,
    List<String> fallbackTasks,
    List<String> approximatedTasks
) {
}
