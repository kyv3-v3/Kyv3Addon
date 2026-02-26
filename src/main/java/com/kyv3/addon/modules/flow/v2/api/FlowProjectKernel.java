package com.kyv3.addon.modules.flow.v2.api;

import com.kyv3.addon.modules.flow.v2.application.FlowRegistryService;
import com.kyv3.addon.modules.flow.v2.application.ports.FlowRegistryStore;
import com.kyv3.addon.modules.flow.v2.application.ports.FlowRuntimeGateway;
import com.kyv3.addon.modules.flow.v2.infrastructure.runtime.NoOpFlowRuntimeGateway;
import com.kyv3.addon.modules.flow.v2.infrastructure.store.InMemoryFlowRegistryStore;

import java.util.Objects;

public final class FlowProjectKernel {
    private final FlowRegistryService registryService;

    public FlowProjectKernel(FlowRegistryStore store, FlowRuntimeGateway runtimeGateway) {
        registryService = new FlowRegistryService(
            Objects.requireNonNull(store, "store"),
            Objects.requireNonNull(runtimeGateway, "runtimeGateway")
        );
    }

    public FlowRegistryService registryService() {
        return registryService;
    }

    public static FlowProjectKernel inMemory() {
        return new FlowProjectKernel(new InMemoryFlowRegistryStore(), new NoOpFlowRuntimeGateway());
    }
}
