package com.budgetflow.core.api;

import com.budgetflow.core.classification.Importance;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorkSpecTest {

    private static final TaskKey<String> TOOL_KEY = TaskKey.of("tool-call");

    @Test
    void mapsToUnderlyingTaskSpecWithoutChangingSemantics() {
        AgentWorkSpec<String> workSpec = AgentWorkSpec.optional(TOOL_KEY, Duration.ofMillis(90), () -> "primary")
            .withFallback(() -> "fallback", Duration.ofMillis(12))
            .withApproximate(() -> "approximate", Duration.ofMillis(7));

        TaskSpec<String> taskSpec = workSpec.toTaskSpec();

        assertEquals("tool-call", taskSpec.taskName());
        assertEquals(Importance.OPTIONAL, taskSpec.importance());
        assertTrue(taskSpec.fallbackSupplier().isPresent());
        assertTrue(taskSpec.approximateSupplier().isPresent());
        assertEquals(Duration.ofMillis(12), taskSpec.fallbackExpectedLatency().orElseThrow());
        assertEquals(Duration.ofMillis(7), taskSpec.approximateExpectedLatency().orElseThrow());
    }

    @Test
    void adaptiveRequestBuilderAcceptsAgentWorkSpec() {
        AgentWorkSpec<String> workSpec = AgentWorkSpec.important(TOOL_KEY, Duration.ofMillis(45), () -> "ok");

        AdaptiveRequest request = AdaptiveRequest.builder()
            .agentWork(TOOL_KEY, workSpec)
            .build();

        assertEquals(1, request.taskSpecs().size());
        assertEquals("tool-call", request.taskSpecs().get(0).taskName());
        assertEquals(Importance.IMPORTANT, request.taskSpecs().get(0).importance());
    }

    @Test
    void builderRejectsMismatchedAgentWorkName() {
        AgentWorkSpec<String> workSpec = AgentWorkSpec.mandatory("other-work", Duration.ofMillis(20), () -> "v");

        assertThrows(IllegalArgumentException.class,
            () -> AdaptiveRequest.builder().agentWork(TOOL_KEY, workSpec).build());
    }
}
