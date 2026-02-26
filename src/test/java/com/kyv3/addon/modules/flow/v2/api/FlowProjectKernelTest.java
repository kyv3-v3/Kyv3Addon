package com.kyv3.addon.modules.flow.v2.api;

import com.kyv3.addon.modules.flow.v2.infrastructure.runtime.NoOpFlowRuntimeGateway;
import com.kyv3.addon.modules.flow.v2.infrastructure.store.InMemoryFlowRegistryStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowProjectKernelTest {
    @Test
    void inMemoryKernelBootstrapsRegistryService() {
        FlowProjectKernel kernel = FlowProjectKernel.inMemory();
        assertNotNull(kernel.registryService());

        kernel.registryService().ensureInitialized();
        assertNotNull(kernel.registryService().getSelectedDefinition());
        assertEquals(1, kernel.registryService().getDefinitions().size());
        assertTrue(kernel.registryService().getSelectedDefinition().available());
    }

    @Test
    void constructorRejectsNullDependencies() {
        assertThrows(NullPointerException.class, () -> new FlowProjectKernel(null, new NoOpFlowRuntimeGateway()));
        assertThrows(NullPointerException.class, () -> new FlowProjectKernel(new InMemoryFlowRegistryStore(), null));
    }
}
