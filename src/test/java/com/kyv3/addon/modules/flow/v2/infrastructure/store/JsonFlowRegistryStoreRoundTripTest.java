package com.kyv3.addon.modules.flow.v2.infrastructure.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;
import com.kyv3.addon.modules.flow.v2.domain.FlowNode;
import com.kyv3.addon.modules.flow.v2.domain.FlowNodeKind;
import com.kyv3.addon.modules.flow.v2.domain.FlowRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFlowRegistryStoreRoundTripTest {
    @Test
    void saveAndLoadRoundTripPreservesGraphAndSelection() {
        AtomicReference<String> raw = new AtomicReference<>();
        JsonFlowRegistryStore store = new JsonFlowRegistryStore(raw::get, raw::set);

        FlowRegistry registry = new FlowRegistry();
        FlowDefinition first = registry.createDefinition("Alpha Module");
        FlowDefinition second = registry.createDefinition("Beta Module");
        registry.selectDefinition(first.id());

        FlowNode event = first.graph().addNode(FlowNodeKind.OnEnable, 0, 0);
        FlowNode action = first.graph().addNode(FlowNodeKind.SendMessage, 120, 0);
        action.setText("/spawn");
        first.graph().addLink(event.id(), action.id());
        second.setAvailable(false);

        store.save(registry);

        String payload = raw.get();
        assertNotNull(payload);

        JsonObject saved = JsonParser.parseString(payload).getAsJsonObject();
        assertEquals(FlowRegistryMigrationAssistant.CURRENT_SCHEMA_VERSION, saved.get("schemaVersion").getAsInt());

        FlowRegistry loaded = store.load();
        assertEquals(2, loaded.definitionsView().size());
        assertEquals(first.id(), loaded.selectedDefinitionId());

        FlowDefinition loadedFirst = loaded.getDefinitionById(first.id());
        assertNotNull(loadedFirst);
        assertEquals(2, loadedFirst.graph().nodesView().size());
        assertEquals(1, loadedFirst.graph().linksView().size());

        FlowNode loadedAction = loadedFirst.graph().nodesView().stream()
            .filter(node -> node.kind() == FlowNodeKind.SendMessage)
            .findFirst()
            .orElse(null);
        assertNotNull(loadedAction);
        assertEquals("/spawn", loadedAction.text());

        FlowDefinition loadedSecond = loaded.getDefinitionById(second.id());
        assertNotNull(loadedSecond);
        assertTrue(!loadedSecond.available());
    }

    @Test
    void saveNullWritesBlankPayload() {
        AtomicReference<String> raw = new AtomicReference<>("seed");
        JsonFlowRegistryStore store = new JsonFlowRegistryStore(raw::get, raw::set);

        store.save(null);
        assertEquals("", raw.get());
    }
}
