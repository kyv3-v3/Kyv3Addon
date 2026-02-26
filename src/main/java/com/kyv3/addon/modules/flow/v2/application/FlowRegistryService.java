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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FlowRegistryService {
    private final FlowRegistryStore store;
    private final FlowRuntimeGateway runtimeGateway;

    private FlowRegistry registry;
    private boolean initialized;

    public FlowRegistryService(FlowRegistryStore store, FlowRuntimeGateway runtimeGateway) {
        this.store = Objects.requireNonNull(store, "store");
        this.runtimeGateway = Objects.requireNonNull(runtimeGateway, "runtimeGateway");
    }

    public synchronized void ensureInitialized() {
        if (initialized) return;

        FlowRegistry loaded = store.load();
        registry = loaded == null ? new FlowRegistry() : loaded;
        registry.sanitize();

        syncAllRuntimeDefinitions();
        persist();
        initialized = true;
    }

    public synchronized List<FlowDefinition> getDefinitions() {
        ensureInitialized();

        List<FlowDefinition> snapshot = new ArrayList<>();
        for (FlowDefinition definition : registry.definitionsView()) {
            snapshot.add(definition.copy());
        }

        return snapshot;
    }

    public synchronized FlowDefinition getSelectedDefinition() {
        ensureInitialized();
        return registry.getSelectedDefinition().copy();
    }

    public synchronized FlowDefinition getDefinitionById(int definitionId) {
        ensureInitialized();

        FlowDefinition definition = registry.getDefinitionById(definitionId);
        return definition == null ? null : definition.copy();
    }

    public synchronized void selectDefinition(int definitionId) {
        ensureInitialized();

        registry.selectDefinition(definitionId);
        persist();
    }

    public synchronized void selectPreviousDefinition() {
        ensureInitialized();

        registry.selectPreviousDefinition();
        persist();
    }

    public synchronized void selectNextDefinition() {
        ensureInitialized();

        registry.selectNextDefinition();
        persist();
    }

    public synchronized FlowDefinition createDefinition(String preferredBaseName) {
        ensureInitialized();

        FlowDefinition definition = registry.createDefinition(preferredBaseName);
        runtimeGateway.upsertDefinition(definition.copy());
        persist();
        return definition.copy();
    }

    public synchronized FlowDefinition renameDefinition(int definitionId, String preferredName) {
        ensureInitialized();

        FlowDefinition definition = registry.getDefinitionById(definitionId);
        if (definition == null) return null;

        String before = definition.name();
        definition.setName(preferredName);
        registry.sanitize();

        FlowDefinition renamed = registry.getDefinitionById(definitionId);
        if (renamed == null) return null;
        if (!before.equals(renamed.name())) persistDefinition(renamed);

        return renamed.copy();
    }

    public synchronized boolean deleteDefinition(int definitionId) {
        ensureInitialized();

        boolean removed = registry.deleteDefinition(definitionId);
        if (!removed) return false;

        runtimeGateway.removeDefinition(definitionId);
        syncAllRuntimeDefinitions();
        persist();
        return true;
    }

    public synchronized void setDefinitionAvailable(int definitionId, boolean available) {
        ensureInitialized();

        FlowDefinition definition = registry.getDefinitionById(definitionId);
        if (definition == null) return;

        definition.setAvailable(available);
        if (!available) runtimeGateway.setDefinitionRunning(definition.id(), false);

        runtimeGateway.upsertDefinition(definition.copy());
        syncActiveFlagsFromRuntime();
        persist();
    }

    public synchronized void toggleDefinitionAvailable(int definitionId) {
        ensureInitialized();

        FlowDefinition definition = registry.getDefinitionById(definitionId);
        if (definition == null) return;

        setDefinitionAvailable(definitionId, !definition.available());
    }

    public synchronized void setDefinitionRunning(int definitionId, boolean running) {
        ensureInitialized();

        FlowDefinition definition = registry.getDefinitionById(definitionId);
        if (definition == null || !definition.available()) return;

        runtimeGateway.setDefinitionRunning(definitionId, running);
        syncActiveFlagsFromRuntime();
        persist();
    }

    public synchronized int stopAllRunningDefinitions() {
        ensureInitialized();

        int stopped = 0;
        for (FlowDefinition definition : registry.definitionsView()) {
            if (!runtimeGateway.isDefinitionRunning(definition.id())) continue;
            runtimeGateway.setDefinitionRunning(definition.id(), false);
            stopped++;
        }

        if (stopped > 0 && syncActiveFlagsFromRuntime()) {
            persist();
        }

        return stopped;
    }

    public synchronized void toggleDefinitionRunning(int definitionId) {
        ensureInitialized();
        setDefinitionRunning(definitionId, !runtimeGateway.isDefinitionRunning(definitionId));
    }

    public synchronized boolean isDefinitionRunning(int definitionId) {
        ensureInitialized();
        return runtimeGateway.isDefinitionRunning(definitionId);
    }

    public synchronized List<FlowNode> selectedNodes() {
        ensureInitialized();

        List<FlowNode> snapshot = new ArrayList<>();
        for (FlowNode node : selectedGraph().nodesView()) {
            snapshot.add(node.copy());
        }

        return snapshot;
    }

    public synchronized List<FlowLink> selectedLinks() {
        ensureInitialized();
        return new ArrayList<>(selectedGraph().linksView());
    }

    public synchronized FlowNode getSelectedNodeById(int nodeId) {
        ensureInitialized();

        FlowNode node = selectedGraph().getNodeById(nodeId);
        return node == null ? null : node.copy();
    }

    public synchronized FlowNode addNodeToSelectedGraph(FlowNodeKind kind, double x, double y) {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        FlowNode node = definition.graph().addNode(kind, x, y);
        persistDefinition(definition);
        return node.copy();
    }

    public synchronized FlowNode duplicateNodeInSelectedGraph(int nodeId, double offsetX, double offsetY) {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        FlowNode duplicate = definition.graph().duplicateNode(nodeId, offsetX, offsetY);
        if (duplicate == null) return null;

        persistDefinition(definition);
        return duplicate.copy();
    }

    public synchronized boolean moveNodeInSelectedGraph(int nodeId, double x, double y) {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        boolean moved = definition.graph().moveNode(nodeId, x, y);
        if (!moved) return false;

        persistDefinition(definition);
        return true;
    }

    public synchronized boolean setSelectedNodeText(int nodeId, String text) {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        boolean changed = definition.graph().setNodeText(nodeId, text);
        if (!changed) return false;

        persistDefinition(definition);
        return true;
    }

    public synchronized boolean setSelectedNodeNumber(int nodeId, int number) {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        boolean changed = definition.graph().setNodeNumber(nodeId, number);
        if (!changed) return false;

        persistDefinition(definition);
        return true;
    }

    public synchronized boolean removeNodeFromSelectedGraph(int nodeId) {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        boolean removed = definition.graph().removeNode(nodeId);
        if (!removed) return false;

        persistDefinition(definition);
        return true;
    }

    public synchronized boolean bringNodeToFrontInSelectedGraph(int nodeId) {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        boolean changed = definition.graph().bringNodeToFront(nodeId);
        if (!changed) return false;

        persistDefinition(definition);
        return true;
    }

    public synchronized boolean addLinkToSelectedGraph(int fromNodeId, int toNodeId) {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        boolean created = definition.graph().addLink(fromNodeId, toNodeId);
        if (!created) return false;

        persistDefinition(definition);
        return true;
    }

    public synchronized boolean removeLinkFromSelectedGraph(FlowLink link) {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        boolean removed = definition.graph().removeLink(link);
        if (!removed) return false;

        persistDefinition(definition);
        return true;
    }

    public synchronized void clearSelectedGraph() {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        definition.graph().clear();
        persistDefinition(definition);
    }

    public synchronized void markDirty() {
        ensureInitialized();

        FlowDefinition definition = selectedDefinitionMutable();
        definition.graph().sanitize();
        persistDefinition(definition);
    }

    public synchronized boolean replaceDefinitionGraph(int definitionId, FlowGraph graph) {
        ensureInitialized();

        FlowDefinition definition = registry.getDefinitionById(definitionId);
        if (definition == null) return false;

        definition.setGraph(graph == null ? new FlowGraph() : graph.copy());
        definition.sanitize();
        persistDefinition(definition);
        return true;
    }

    public synchronized void syncRuntimeStateToRegistry() {
        ensureInitialized();
        if (syncActiveFlagsFromRuntime()) persist();
    }

    public synchronized FlowRuntimeProfile runtimeProfile(int definitionId) {
        ensureInitialized();
        FlowRuntimeProfile profile = runtimeGateway.runtimeProfile(definitionId);
        return profile == null ? FlowRuntimeProfile.empty(definitionId, "") : profile;
    }

    public synchronized Map<Integer, FlowRuntimeProfile> runtimeProfiles() {
        ensureInitialized();
        Map<Integer, FlowRuntimeProfile> source = runtimeGateway.runtimeProfiles();
        if (source == null || source.isEmpty()) return Map.of();

        Map<Integer, FlowRuntimeProfile> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Integer, FlowRuntimeProfile> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null) continue;

            int definitionId = entry.getKey();
            FlowRuntimeProfile profile = entry.getValue();
            snapshot.put(definitionId, profile == null ? FlowRuntimeProfile.empty(definitionId, "") : profile);
        }

        if (snapshot.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(snapshot);
    }

    public synchronized void resetRuntimeProfile(int definitionId) {
        ensureInitialized();
        runtimeGateway.resetRuntimeProfile(definitionId);
    }

    public synchronized int resetAllRuntimeProfiles() {
        ensureInitialized();
        return runtimeGateway.resetAllRuntimeProfiles();
    }

    private FlowDefinition selectedDefinitionMutable() {
        return registry.getSelectedDefinition();
    }

    private FlowGraph selectedGraph() {
        return selectedDefinitionMutable().graph();
    }

    private void persistDefinition(FlowDefinition definition) {
        runtimeGateway.upsertDefinition(definition.copy());
        syncActiveFlagsFromRuntime();
        persist();
    }

    private void syncAllRuntimeDefinitions() {
        for (FlowDefinition definition : registry.definitionsView()) {
            runtimeGateway.upsertDefinition(definition.copy());
        }

        syncActiveFlagsFromRuntime();
    }

    private boolean syncActiveFlagsFromRuntime() {
        boolean changed = false;
        for (FlowDefinition definition : registry.definitionsView()) {
            boolean running = definition.available() && runtimeGateway.isDefinitionRunning(definition.id());
            if (definition.active() != running) {
                definition.setActive(running);
                changed = true;
            }
        }
        return changed;
    }

    private void persist() {
        store.save(registry.copy());
    }
}
