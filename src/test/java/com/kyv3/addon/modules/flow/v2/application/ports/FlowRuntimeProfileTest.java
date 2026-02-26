package com.kyv3.addon.modules.flow.v2.application.ports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowRuntimeProfileTest {
    @Test
    void canonicalConstructorNormalizesInvalidValues() {
        FlowRuntimeProfile profile = new FlowRuntimeProfile(
            -7,
            null,
            -1,
            -2,
            -3,
            -4,
            -5,
            -6,
            -7,
            -8,
            -9,
            -10
        );

        assertEquals(0, profile.definitionId());
        assertEquals("", profile.definitionName());
        assertEquals(0, profile.totalTicks());
        assertEquals(0, profile.totalActions());
        assertEquals(0, profile.totalActionNanos());
        assertEquals(0, profile.lastTickActions());
        assertEquals(0, profile.lastTickNanos());
        assertEquals(0, profile.maxActionsInTick());
        assertEquals(0, profile.maxTickNanos());
        assertEquals(0, profile.queuePeak());
        assertEquals(0, profile.watchdogTrips());
        assertEquals(0, profile.lastActionTimestamp());
    }

    @Test
    void averageHelpersReturnExpectedValues() {
        FlowRuntimeProfile profile = new FlowRuntimeProfile(
            12,
            "test",
            5,
            20,
            10_000,
            4,
            2_500,
            9,
            3_000,
            15,
            1,
            1234
        );

        assertEquals(4.0, profile.averageActionsPerTick());
        assertEquals(0.5, profile.averageActionMicros());
        assertEquals(2.5, profile.lastTickMicros());
    }
}
