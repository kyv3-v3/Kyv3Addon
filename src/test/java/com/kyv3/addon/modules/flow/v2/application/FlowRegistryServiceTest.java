package com.kyv3.addon.modules.flow.v2.application;

import com.kyv3.addon.modules.flow.v2.application.ports.FlowRegistryStore;
import com.kyv3.addon.modules.flow.v2.application.ports.FlowRuntimeGateway;
import com.kyv3.addon.modules.flow.v2.application.ports.FlowRuntimeProfile;
import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;
import com.kyv3.addon.modules.flow.v2.domain.FlowGraph;
import com.kyv3.addon.modules.flow.v2.domain.FlowLink;
import com.kyv3.addon.modules.flow.v2.domain.FlowNode;
import com.kyv3.addon.modules.flow.v2.domain.FlowNodeKind;
import com.kyv3.addon.modules.flow.v2.domain.FlowRegistry;
import com.kyv3.addon.modules.flow.v2.infrastructure.store.InMemoryFlowRegistryStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowRegistryServiceTest {
    @Test
    void createsAndSelectsDefinition() {
        FlowRegistryService service = new FlowRegistryService(new InMemoryFlowRegistryStore(), new FakeRuntimeGateway());

        service.ensureInitialized();
        FlowDefinition created = service.createDefinition("My Super Module");

        assertNotNull(created);
        assertEquals("my-super-module", created.name());

        service.selectDefinition(created.id());
        assertEquals(created.id(), service.getSelectedDefinition().id());
    }

    @Test
    void renamingDefinitionSanitizesAndKeepsUniqueNames() {
        FlowRegistryService service = new FlowRegistryService(new InMemoryFlowRegistryStore(), new FakeRuntimeGateway());
        service.ensureInitialized();

        FlowDefinition first = service.getSelectedDefinition();
        FlowDefinition second = service.createDefinition("alpha");
        assertEquals("alpha", second.name());

        FlowDefinition renamed = service.renameDefinition(first.id(), "Alpha");
        assertNotNull(renamed);
        assertEquals("alpha", renamed.name());
        assertEquals("alpha", service.getDefinitionById(first.id()).name());
        assertEquals("alpha-2", service.getDefinitionById(second.id()).name());
    }

    @Test
    void replaceDefinitionGraphSanitizesAndPersists() {
        FlowRegistryService service = new FlowRegistryService(new InMemoryFlowRegistryStore(), new FakeRuntimeGateway());
        service.ensureInitialized();

        FlowDefinition selected = service.getSelectedDefinition();

        List<FlowNode> nodes = new ArrayList<>();
        nodes.add(new FlowNode(1, FlowNodeKind.OnEnable, 0, 0, "", 1));
        nodes.add(new FlowNode(2, FlowNodeKind.SendMessage, 120, 0, "/spawn", 0));

        List<FlowLink> links = new ArrayList<>();
        links.add(new FlowLink(1, 2));
        links.add(new FlowLink(2, 99));

        FlowGraph graph = new FlowGraph(0, nodes, links);
        boolean replaced = service.replaceDefinitionGraph(selected.id(), graph);

        assertTrue(replaced);

        FlowDefinition refreshed = service.getDefinitionById(selected.id());
        assertNotNull(refreshed);
        assertEquals(2, refreshed.graph().nodesView().size());
        assertEquals(1, refreshed.graph().linksView().size());
        assertEquals(1, refreshed.graph().nodesView().get(1).number());
    }

    @Test
    void disablingDefinitionStopsRuntime() {
        FakeRuntimeGateway runtime = new FakeRuntimeGateway();
        FlowRegistryService service = new FlowRegistryService(new InMemoryFlowRegistryStore(), runtime);
        service.ensureInitialized();

        FlowDefinition selected = service.getSelectedDefinition();

        service.setDefinitionRunning(selected.id(), true);
        assertTrue(service.isDefinitionRunning(selected.id()));

        service.setDefinitionAvailable(selected.id(), false);
        assertFalse(service.isDefinitionRunning(selected.id()));

        FlowDefinition refreshed = service.getDefinitionById(selected.id());
        assertNotNull(refreshed);
        assertFalse(refreshed.available());
        assertFalse(refreshed.active());
    }

    @Test
    void syncRuntimeStateUpdatesActiveFlags() {
        FakeRuntimeGateway runtime = new FakeRuntimeGateway();
        FlowRegistryService service = new FlowRegistryService(new InMemoryFlowRegistryStore(), runtime);
        service.ensureInitialized();

        FlowDefinition selected = service.getSelectedDefinition();
        runtime.setDefinitionRunning(selected.id(), true);

        service.syncRuntimeStateToRegistry();
        assertTrue(service.getDefinitionById(selected.id()).active());

        runtime.setDefinitionRunning(selected.id(), false);
        service.syncRuntimeStateToRegistry();
        assertFalse(service.getDefinitionById(selected.id()).active());
    }

    @Test
    void stopAllRunningDefinitionsStopsAllAndPersistsOnce() {
        CountingStore store = new CountingStore(null);
        FakeRuntimeGateway runtime = new FakeRuntimeGateway();
        FlowRegistryService service = new FlowRegistryService(store, runtime);
        service.ensureInitialized();

        FlowDefinition first = service.getSelectedDefinition();
        FlowDefinition second = service.createDefinition("Second Module");

        service.setDefinitionRunning(first.id(), true);
        service.setDefinitionRunning(second.id(), true);
        int afterSetupSaves = store.saveCount();

        int stopped = service.stopAllRunningDefinitions();
        assertEquals(2, stopped);
        assertFalse(service.isDefinitionRunning(first.id()));
        assertFalse(service.isDefinitionRunning(second.id()));
        assertFalse(service.getDefinitionById(first.id()).active());
        assertFalse(service.getDefinitionById(second.id()).active());
        assertEquals(afterSetupSaves + 1, store.saveCount());

        int stoppedAgain = service.stopAllRunningDefinitions();
        assertEquals(0, stoppedAgain);
        assertEquals(afterSetupSaves + 1, store.saveCount());
    }

    @Test
    void runtimeProfileDelegationWorks() {
        FakeRuntimeGateway runtime = new FakeRuntimeGateway();
        FlowRegistryService service = new FlowRegistryService(new InMemoryFlowRegistryStore(), runtime);
        service.ensureInitialized();

        FlowDefinition selected = service.getSelectedDefinition();
        runtime.setRuntimeProfile(
            selected.id(),
            new FlowRuntimeProfile(selected.id(), selected.name(), 20, 120, 8_000, 6, 3_000, 9, 7_000, 14, 0, System.currentTimeMillis())
        );

        FlowRuntimeProfile profile = service.runtimeProfile(selected.id());
        assertEquals(120, profile.totalActions());
        assertEquals(9, profile.maxActionsInTick());

        service.resetRuntimeProfile(selected.id());
        assertEquals(0, service.runtimeProfile(selected.id()).totalActions());
    }

    @Test
    void resetAllRuntimeProfilesResetsEveryProfile() {
        FakeRuntimeGateway runtime = new FakeRuntimeGateway();
        FlowRegistryService service = new FlowRegistryService(new InMemoryFlowRegistryStore(), runtime);
        service.ensureInitialized();

        FlowDefinition first = service.getSelectedDefinition();
        FlowDefinition second = service.createDefinition("Second Module");

        runtime.setRuntimeProfile(first.id(), new FlowRuntimeProfile(first.id(), first.name(), 10, 50, 5_000, 5, 2_000, 7, 3_000, 9, 0, 11));
        runtime.setRuntimeProfile(second.id(), new FlowRuntimeProfile(second.id(), second.name(), 20, 100, 7_000, 8, 2_500, 12, 5_000, 15, 1, 22));

        int reset = service.resetAllRuntimeProfiles();
        assertEquals(2, reset);
        assertEquals(0, service.runtimeProfile(first.id()).totalActions());
        assertEquals(0, service.runtimeProfile(second.id()).totalActions());
    }

    @Test
    void runtimeProfileFallsBackWhenGatewayReturnsNull() {
        FakeRuntimeGateway runtime = new FakeRuntimeGateway();
        FlowRegistryService service = new FlowRegistryService(new InMemoryFlowRegistryStore(), runtime);
        service.ensureInitialized();

        FlowDefinition selected = service.getSelectedDefinition();
        runtime.setRuntimeProfile(selected.id(), null);

        FlowRuntimeProfile profile = service.runtimeProfile(selected.id());
        assertEquals(selected.id(), profile.definitionId());
        assertEquals(0, profile.totalActions());
    }

    @Test
    void runtimeProfilesAreSanitizedAndImmutable() {
        FakeRuntimeGateway runtime = new FakeRuntimeGateway();
        FlowRegistryService service = new FlowRegistryService(new InMemoryFlowRegistryStore(), runtime);
        service.ensureInitialized();

        FlowDefinition selected = service.getSelectedDefinition();
        runtime.setRuntimeProfile(selected.id(), null);

        Map<Integer, FlowRuntimeProfile> profiles = service.runtimeProfiles();
        assertTrue(profiles.containsKey(selected.id()));
        assertEquals(0, profiles.get(selected.id()).totalTicks());
        assertThrows(UnsupportedOperationException.class, () -> profiles.put(999, FlowRuntimeProfile.empty(999, "")));

        runtime.setRuntimeProfile(selected.id(), new FlowRuntimeProfile(selected.id(), selected.name(), 9, 9, 9, 1, 1, 1, 1, 1, 1, 1));
        assertEquals(0, profiles.get(selected.id()).totalTicks(), "service should return snapshot maps, not live runtime backing maps");
    }

    @Test
    void syncRuntimeStatePersistsOnlyWhenActiveFlagsChange() {
        CountingStore store = new CountingStore(null);
        FakeRuntimeGateway runtime = new FakeRuntimeGateway();
        FlowRegistryService service = new FlowRegistryService(store, runtime);
        service.ensureInitialized();

        int afterInit = store.saveCount();
        service.syncRuntimeStateToRegistry();
        assertEquals(afterInit, store.saveCount());

        FlowDefinition selected = service.getSelectedDefinition();
        runtime.setDefinitionRunning(selected.id(), true);
        service.syncRuntimeStateToRegistry();
        assertEquals(afterInit + 1, store.saveCount());

        service.syncRuntimeStateToRegistry();
        assertEquals(afterInit + 1, store.saveCount());
    }

    private static final class FakeRuntimeGateway implements FlowRuntimeGateway {
        private final Map<Integer, FlowDefinition> definitions = new HashMap<>();
        private final Set<Integer> running = new HashSet<>();
        private final Map<Integer, FlowRuntimeProfile> profiles = new HashMap<>();

        @Override
        public void upsertDefinition(FlowDefinition definition) {
            if (definition == null) return;

            FlowDefinition copy = definition.copy();
            definitions.put(copy.id(), copy);
            if (!copy.available()) running.remove(copy.id());
            profiles.putIfAbsent(copy.id(), FlowRuntimeProfile.empty(copy.id(), copy.name()));
        }

        @Override
        public void removeDefinition(int definitionId) {
            definitions.remove(definitionId);
            running.remove(definitionId);
            profiles.remove(definitionId);
        }

        @Override
        public boolean isDefinitionRunning(int definitionId) {
            return running.contains(definitionId);
        }

        @Override
        public void setDefinitionRunning(int definitionId, boolean enabled) {
            FlowDefinition definition = definitions.get(definitionId);
            if (definition == null || !definition.available()) {
                running.remove(definitionId);
                return;
            }

            if (enabled) running.add(definitionId);
            else running.remove(definitionId);
        }

        @Override
        public FlowRuntimeProfile runtimeProfile(int definitionId) {
            return profiles.getOrDefault(definitionId, FlowRuntimeProfile.empty(definitionId, ""));
        }

        @Override
        public Map<Integer, FlowRuntimeProfile> runtimeProfiles() {
            return profiles;
        }

        @Override
        public void resetRuntimeProfile(int definitionId) {
            profiles.computeIfPresent(definitionId, (id, profile) -> FlowRuntimeProfile.empty(id, profile.definitionName()));
        }

        private void setRuntimeProfile(int definitionId, FlowRuntimeProfile profile) {
            profiles.put(definitionId, profile);
        }
    }

    private static final class CountingStore implements FlowRegistryStore {
        private FlowRegistry state;
        private int saveCount;

        private CountingStore(FlowRegistry initialState) {
            state = initialState == null ? null : initialState.copy();
        }

        @Override
        public FlowRegistry load() {
            return state == null ? null : state.copy();
        }

        @Override
        public void save(FlowRegistry registry) {
            saveCount++;
            state = registry == null ? new FlowRegistry() : registry.copy();
        }

        private int saveCount() {
            return saveCount;
        }
    }
}
