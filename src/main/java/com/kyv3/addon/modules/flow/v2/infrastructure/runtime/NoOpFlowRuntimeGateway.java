package com.kyv3.addon.modules.flow.v2.infrastructure.runtime;

import com.kyv3.addon.modules.flow.v2.application.ports.FlowRuntimeGateway;
import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;

public final class NoOpFlowRuntimeGateway implements FlowRuntimeGateway {
    @Override
    public void upsertDefinition(FlowDefinition definition) {
    }

    @Override
    public void removeDefinition(int definitionId) {
    }

    @Override
    public boolean isDefinitionRunning(int definitionId) {
        return false;
    }

    @Override
    public void setDefinitionRunning(int definitionId, boolean running) {
    }
}
