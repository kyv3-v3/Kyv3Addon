package com.kyv3.addon.modules.flow.v2.application.ports;

import com.kyv3.addon.modules.flow.v2.domain.FlowRegistry;

public interface FlowRegistryStore {
    FlowRegistry load();

    void save(FlowRegistry registry);
}
