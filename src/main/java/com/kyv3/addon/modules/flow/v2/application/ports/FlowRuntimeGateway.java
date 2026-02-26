package com.kyv3.addon.modules.flow.v2.application.ports;

import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;

import java.util.Map;

public interface FlowRuntimeGateway {
    void upsertDefinition(FlowDefinition definition);

    void removeDefinition(int definitionId);

    boolean isDefinitionRunning(int definitionId);

    void setDefinitionRunning(int definitionId, boolean running);

    default FlowRuntimeProfile runtimeProfile(int definitionId) {
        return FlowRuntimeProfile.empty(definitionId, "");
    }

    default Map<Integer, FlowRuntimeProfile> runtimeProfiles() {
        return Map.of();
    }

    default void resetRuntimeProfile(int definitionId) {
    }

    default int resetAllRuntimeProfiles() {
        Map<Integer, FlowRuntimeProfile> profiles = runtimeProfiles();
        if (profiles == null || profiles.isEmpty()) return 0;

        int reset = 0;
        for (Integer definitionId : profiles.keySet()) {
            if (definitionId == null) continue;
            resetRuntimeProfile(definitionId);
            reset++;
        }

        return reset;
    }
}
