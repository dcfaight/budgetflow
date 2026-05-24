package com.budgetflow.core.policy;

import com.budgetflow.core.classification.Importance;

import java.time.Duration;

public record TaskDescriptor(
    String taskName,
    Importance importance,
    Duration expectedLatency,
    boolean fallbackSupported,
    boolean approximateSupported,
    Duration fallbackExpectedLatency,
    Duration approximateExpectedLatency
) {
}
