package com.kyv3.addon.modules.flow.v2.infrastructure.store;

import com.kyv3.addon.modules.flow.v2.application.ports.FlowRegistryStore;
import com.kyv3.addon.modules.flow.v2.domain.FlowRegistry;

public final class InMemoryFlowRegistryStore implements FlowRegistryStore {
    private FlowRegistry state;

    public InMemoryFlowRegistryStore() {
        this(new FlowRegistry());
    }

    public InMemoryFlowRegistryStore(FlowRegistry initialState) {
        state = initialState == null ? new FlowRegistry() : initialState.copy();
    }

    @Override
    public FlowRegistry load() {
        return state.copy();
    }

    @Override
    public void save(FlowRegistry registry) {
        state = registry == null ? new FlowRegistry() : registry.copy();
    }
}
