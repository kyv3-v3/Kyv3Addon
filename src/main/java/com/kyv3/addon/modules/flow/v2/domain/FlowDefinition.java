package com.kyv3.addon.modules.flow.v2.domain;

import java.util.Objects;

public final class FlowDefinition {
    private int id;
    private String name;
    private boolean available;
    private boolean active;
    private FlowGraph graph;

    public FlowDefinition() {
        this(0, "custom-module", true, false, new FlowGraph());
    }

    public FlowDefinition(int id, String name, boolean available, boolean active, FlowGraph graph) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.available = available;
        this.active = active;
        this.graph = graph == null ? new FlowGraph() : graph;
    }

    public FlowDefinition copy() {
        return new FlowDefinition(id, name, available, active, graph.copy());
    }

    public void sanitize() {
        name = FlowNameSanitizer.sanitize(name);
        if (name.isBlank()) name = "custom-module-" + Math.max(id, 1);

        if (graph == null) graph = new FlowGraph();
        graph.sanitize();

        if (!available) active = false;
    }

    public int id() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public boolean available() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
        if (!available) this.active = false;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = available && active;
    }

    public FlowGraph graph() {
        return graph;
    }

    public void setGraph(FlowGraph graph) {
        this.graph = graph == null ? new FlowGraph() : graph;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FlowDefinition that)) return false;

        return id == that.id && available == that.available && active == that.active && Objects.equals(name, that.name) && Objects.equals(graph, that.graph);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, available, active, graph);
    }
}
