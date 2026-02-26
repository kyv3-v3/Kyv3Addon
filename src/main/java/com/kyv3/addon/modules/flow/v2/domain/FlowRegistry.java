package com.kyv3.addon.modules.flow.v2.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlowRegistry {
    private int nextDefinitionId = 1;
    private int selectedDefinitionId = -1;
    private final List<FlowDefinition> definitions = new ArrayList<>();

    public FlowRegistry() {
    }

    public FlowRegistry(int nextDefinitionId, int selectedDefinitionId, List<FlowDefinition> definitions) {
        this.nextDefinitionId = Math.max(1, nextDefinitionId);
        this.selectedDefinitionId = selectedDefinitionId;

        if (definitions != null) {
            for (FlowDefinition definition : definitions) {
                if (definition != null) this.definitions.add(definition.copy());
            }
        }

        sanitize();
    }

    public FlowRegistry copy() {
        return new FlowRegistry(nextDefinitionId, selectedDefinitionId, definitions);
    }

    public int nextDefinitionId() {
        return nextDefinitionId;
    }

    public void setNextDefinitionId(int nextDefinitionId) {
        this.nextDefinitionId = Math.max(1, nextDefinitionId);
    }

    public int selectedDefinitionId() {
        return selectedDefinitionId;
    }

    public void setSelectedDefinitionId(int selectedDefinitionId) {
        this.selectedDefinitionId = selectedDefinitionId;
    }

    public List<FlowDefinition> definitionsView() {
        return Collections.unmodifiableList(definitions);
    }

    public FlowDefinition getDefinitionById(int definitionId) {
        for (FlowDefinition definition : definitions) {
            if (definition.id() == definitionId) return definition;
        }

        return null;
    }

    public FlowDefinition getSelectedDefinition() {
        FlowDefinition selected = getDefinitionById(selectedDefinitionId);
        if (selected != null) return selected;

        if (definitions.isEmpty()) {
            return createDefinition("custom-module");
        }

        selectedDefinitionId = definitions.getFirst().id();
        return definitions.getFirst();
    }

    public void ensureDefaultDefinition(String preferredBaseName) {
        if (!definitions.isEmpty()) return;
        createDefinition(preferredBaseName);
    }

    public FlowDefinition createDefinition(String preferredBaseName) {
        List<String> names = new ArrayList<>();
        for (FlowDefinition definition : definitions) {
            names.add(definition.name());
        }

        String name = FlowNameSanitizer.nextUniqueName(preferredBaseName, names);
        FlowDefinition definition = new FlowDefinition(nextDefinitionId++, name, true, false, new FlowGraph());
        definitions.add(definition);
        selectedDefinitionId = definition.id();
        return definition;
    }

    public boolean deleteDefinition(int definitionId) {
        FlowDefinition definition = getDefinitionById(definitionId);
        if (definition == null) return false;

        definitions.remove(definition);

        if (definitions.isEmpty()) {
            FlowDefinition fallback = createDefinition("custom-module");
            selectedDefinitionId = fallback.id();
        } else if (selectedDefinitionId == definitionId) {
            selectedDefinitionId = definitions.getFirst().id();
        }

        return true;
    }

    public void selectDefinition(int definitionId) {
        if (getDefinitionById(definitionId) == null) return;
        selectedDefinitionId = definitionId;
    }

    public void selectPreviousDefinition() {
        if (definitions.size() < 2) return;

        int idx = indexOfSelected();
        idx = (idx - 1 + definitions.size()) % definitions.size();
        selectedDefinitionId = definitions.get(idx).id();
    }

    public void selectNextDefinition() {
        if (definitions.size() < 2) return;

        int idx = indexOfSelected();
        idx = (idx + 1) % definitions.size();
        selectedDefinitionId = definitions.get(idx).id();
    }

    public void sanitize() {
        Map<Integer, FlowDefinition> validDefinitions = new LinkedHashMap<>();
        int maxDefinitionId = 0;

        for (FlowDefinition definition : definitions) {
            if (definition == null || definition.id() <= 0) continue;
            if (validDefinitions.containsKey(definition.id())) continue;

            definition.sanitize();
            validDefinitions.put(definition.id(), definition);
            maxDefinitionId = Math.max(maxDefinitionId, definition.id());
        }

        definitions.clear();
        definitions.addAll(validDefinitions.values());

        ensureUniqueNames();

        nextDefinitionId = Math.max(Math.max(1, nextDefinitionId), maxDefinitionId + 1);

        if (definitions.isEmpty()) {
            FlowDefinition fallback = createDefinition("custom-module");
            selectedDefinitionId = fallback.id();
            return;
        }

        if (getDefinitionById(selectedDefinitionId) == null) {
            selectedDefinitionId = definitions.getFirst().id();
        }
    }

    private void ensureUniqueNames() {
        Set<String> used = new HashSet<>();

        for (FlowDefinition definition : definitions) {
            String base = FlowNameSanitizer.sanitize(definition.name());
            if (base.isBlank()) base = "custom-module";

            if (!used.contains(base)) {
                definition.setName(base);
                used.add(base);
                continue;
            }

            int index = 2;
            while (used.contains(base + "-" + index)) index++;

            String unique = base + "-" + index;
            definition.setName(unique);
            used.add(unique);
        }
    }

    private int indexOfSelected() {
        for (int i = 0; i < definitions.size(); i++) {
            if (definitions.get(i).id() == selectedDefinitionId) return i;
        }

        return 0;
    }
}
