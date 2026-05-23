package com.budgetflow.spring.util;

import java.time.Duration;

public final class DurationParser {
    private DurationParser() {
    }

    public static Duration parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Duration value must not be blank");
        }

        String normalized = value.trim().toLowerCase();

        if (normalized.startsWith("pt")) {
            return Duration.parse(normalized.toUpperCase());
        }

        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(parseLong(normalized.substring(0, normalized.length() - 2), value));
        }

        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(parseLong(normalized.substring(0, normalized.length() - 1), value));
        }

        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(parseLong(normalized.substring(0, normalized.length() - 1), value));
        }

        if (normalized.endsWith("h")) {
            return Duration.ofHours(parseLong(normalized.substring(0, normalized.length() - 1), value));
        }

        throw new IllegalArgumentException("Unsupported duration format: " + value);
    }

    private static long parseLong(String rawNumber, String originalValue) {
        try {
            return Long.parseLong(rawNumber.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid duration value: " + originalValue, ex);
        }
    }
}
