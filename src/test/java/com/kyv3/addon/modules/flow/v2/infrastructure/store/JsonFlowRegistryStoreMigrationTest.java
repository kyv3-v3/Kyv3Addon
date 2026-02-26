package com.kyv3.addon.modules.flow.v2.infrastructure.store;

import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;
import com.kyv3.addon.modules.flow.v2.domain.FlowRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonFlowRegistryStoreMigrationTest {
    @Test
    void migratesLegacyPayloadWithoutSchemaVersion() {
        String legacyRaw = """
            {
              "nextDefinitionId": 3,
              "selectedDefinitionId": 2,
              "definitions": [
                {
                  "id": 2,
                  "name": "Legacy Module",
                  "available": true,
                  "active": false,
                  "graph": {
                    "nextNodeId": 3,
                    "nodes": [
                      { "id": 1, "kind": null, "x": 0, "y": 0, "text": null, "number": 0 },
                      { "id": 2, "kind": "Notify", "x": 120, "y": 0, "text": null, "number": 0 }
                    ],
                    "links": [
                      { "fromNodeId": 1, "toNodeId": 2 }
                    ]
                  }
                }
              ]
            }
            """;

        JsonFlowRegistryStore store = new JsonFlowRegistryStore(() -> legacyRaw, ignored -> {
        });
        FlowRegistry registry = store.load();

        assertNotNull(registry);
        assertEquals(1, registry.definitionsView().size());

        FlowDefinition definition = registry.definitionsView().getFirst();
        assertEquals("legacy-module", definition.name());
        assertEquals(2, definition.graph().nodesView().size());
        assertEquals("", definition.graph().nodesView().get(1).text());
        assertEquals(1, definition.graph().nodesView().get(1).number());
        assertFalse(definition.graph().linksView().isEmpty());
    }
}
