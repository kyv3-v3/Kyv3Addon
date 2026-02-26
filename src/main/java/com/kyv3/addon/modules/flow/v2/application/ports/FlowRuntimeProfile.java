package com.kyv3.addon.modules.flow.v2.application.ports;

public record FlowRuntimeProfile(
    int definitionId,
    String definitionName,
    long totalTicks,
    long totalActions,
    long totalActionNanos,
    int lastTickActions,
    long lastTickNanos,
    int maxActionsInTick,
    long maxTickNanos,
    int queuePeak,
    int watchdogTrips,
    long lastActionTimestamp
) {
    public FlowRuntimeProfile {
        definitionId = Math.max(0, definitionId);
        definitionName = definitionName == null ? "" : definitionName;

        totalTicks = Math.max(0L, totalTicks);
        totalActions = Math.max(0L, totalActions);
        totalActionNanos = Math.max(0L, totalActionNanos);
        lastTickActions = Math.max(0, lastTickActions);
        lastTickNanos = Math.max(0L, lastTickNanos);
        maxActionsInTick = Math.max(0, maxActionsInTick);
        maxTickNanos = Math.max(0L, maxTickNanos);
        queuePeak = Math.max(0, queuePeak);
        watchdogTrips = Math.max(0, watchdogTrips);
        lastActionTimestamp = Math.max(0L, lastActionTimestamp);
    }

    public static FlowRuntimeProfile empty(int definitionId, String definitionName) {
        return new FlowRuntimeProfile(
            Math.max(0, definitionId),
            definitionName == null ? "" : definitionName,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
        );
    }

    public double averageActionsPerTick() {
        if (totalTicks <= 0) return 0;
        return totalActions / (double) totalTicks;
    }

    public double averageActionMicros() {
        if (totalActions <= 0) return 0;
        return (totalActionNanos / 1_000.0) / totalActions;
    }

    public double lastTickMicros() {
        return lastTickNanos / 1_000.0;
    }
}
