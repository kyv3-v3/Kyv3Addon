package com.kyv3.addon.modules.flow.v2.infrastructure.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.kyv3.addon.modules.flow.v2.application.ports.FlowRegistryStore;
import com.kyv3.addon.modules.flow.v2.domain.FlowDefinition;
import com.kyv3.addon.modules.flow.v2.domain.FlowGraph;
import com.kyv3.addon.modules.flow.v2.domain.FlowLink;
import com.kyv3.addon.modules.flow.v2.domain.FlowNode;
import com.kyv3.addon.modules.flow.v2.domain.FlowNodeKind;
import com.kyv3.addon.modules.flow.v2.domain.FlowRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JsonFlowRegistryStore implements FlowRegistryStore {
    private final Supplier<String> rawReader;
    private final Consumer<String> rawWriter;
    private final Gson gson;

    public JsonFlowRegistryStore(Supplier<String> rawReader, Consumer<String> rawWriter) {
        this(rawReader, rawWriter, new GsonBuilder().disableHtmlEscaping().create());
    }

    public JsonFlowRegistryStore(Supplier<String> rawReader, Consumer<String> rawWriter, Gson gson) {
        this.rawReader = rawReader;
        this.rawWriter = rawWriter;
        this.gson = gson;
    }

    @Override
    public FlowRegistry load() {
        String raw = rawReader.get();
        if (raw == null || raw.isBlank()) return new FlowRegistry();

        try {
            JsonObject json = gson.fromJson(raw, JsonObject.class);
            JsonObject migrated = FlowRegistryMigrationAssistant.migrate(json);
            RegistryDto dto = gson.fromJson(migrated, RegistryDto.class);
            return fromDto(dto);
        } catch (Exception ignored) {
            return new FlowRegistry();
        }
    }

    @Override
    public void save(FlowRegistry registry) {
        if (registry == null) {
            rawWriter.accept("");
            return;
        }

        RegistryDto dto = toDto(registry);
        rawWriter.accept(gson.toJson(dto));
    }

    private static RegistryDto toDto(FlowRegistry registry) {
        RegistryDto dto = new RegistryDto();
        dto.schemaVersion = FlowRegistryMigrationAssistant.CURRENT_SCHEMA_VERSION;
        dto.nextDefinitionId = registry.nextDefinitionId();
        dto.selectedDefinitionId = registry.selectedDefinitionId();

        dto.definitions = new ArrayList<>();
        for (FlowDefinition definition : registry.definitionsView()) {
            DefinitionDto definitionDto = new DefinitionDto();
            definitionDto.id = definition.id();
            definitionDto.name = definition.name();
            definitionDto.available = definition.available();
            definitionDto.active = definition.active();

            GraphDto graphDto = new GraphDto();
            graphDto.nextNodeId = definition.graph().nextNodeId();

            graphDto.nodes = new ArrayList<>();
            for (FlowNode node : definition.graph().nodesView()) {
                NodeDto nodeDto = new NodeDto();
                nodeDto.id = node.id();
                nodeDto.kind = node.kind() == null ? FlowNodeKind.Notify.name() : node.kind().name();
                nodeDto.x = node.x();
                nodeDto.y = node.y();
                nodeDto.text = node.text();
                nodeDto.number = node.number();
                graphDto.nodes.add(nodeDto);
            }

            graphDto.links = new ArrayList<>();
            for (FlowLink link : definition.graph().linksView()) {
                LinkDto linkDto = new LinkDto();
                linkDto.fromNodeId = link.fromNodeId();
                linkDto.toNodeId = link.toNodeId();
                graphDto.links.add(linkDto);
            }

            definitionDto.graph = graphDto;
            dto.definitions.add(definitionDto);
        }

        return dto;
    }

    private static FlowRegistry fromDto(RegistryDto dto) {
        if (dto == null) return new FlowRegistry();

        List<FlowDefinition> definitions = new ArrayList<>();
        if (dto.definitions != null) {
            for (DefinitionDto definitionDto : dto.definitions) {
                if (definitionDto == null) continue;

                List<FlowNode> nodes = new ArrayList<>();
                List<FlowLink> links = new ArrayList<>();
                int nextNodeId = 1;

                if (definitionDto.graph != null) {
                    nextNodeId = Math.max(1, definitionDto.graph.nextNodeId);

                    if (definitionDto.graph.nodes != null) {
                        for (NodeDto nodeDto : definitionDto.graph.nodes) {
                            if (nodeDto == null) continue;

                            FlowNodeKind kind;
                            try {
                                kind = FlowNodeKind.valueOf(nodeDto.kind == null ? FlowNodeKind.Notify.name() : nodeDto.kind);
                            } catch (IllegalArgumentException ignored) {
                                kind = FlowNodeKind.Notify;
                            }

                            nodes.add(new FlowNode(nodeDto.id, kind, nodeDto.x, nodeDto.y, nodeDto.text, nodeDto.number));
                        }
                    }

                    if (definitionDto.graph.links != null) {
                        for (LinkDto linkDto : definitionDto.graph.links) {
                            if (linkDto == null) continue;
                            links.add(new FlowLink(linkDto.fromNodeId, linkDto.toNodeId));
                        }
                    }
                }

                FlowGraph graph = new FlowGraph(nextNodeId, nodes, links);
                FlowDefinition definition = new FlowDefinition(definitionDto.id, definitionDto.name, definitionDto.available, definitionDto.active, graph);
                definitions.add(definition);
            }
        }

        return new FlowRegistry(dto.nextDefinitionId, dto.selectedDefinitionId, definitions);
    }

    private static final class RegistryDto {
        private int schemaVersion = FlowRegistryMigrationAssistant.CURRENT_SCHEMA_VERSION;
        private int nextDefinitionId = 1;
        private int selectedDefinitionId = -1;
        private List<DefinitionDto> definitions;
    }

    private static final class DefinitionDto {
        private int id;
        private String name;
        private boolean available;
        private boolean active;
        private GraphDto graph;
    }

    private static final class GraphDto {
        private int nextNodeId = 1;
        private List<NodeDto> nodes;
        private List<LinkDto> links;
    }

    private static final class NodeDto {
        private int id;
        private String kind;
        private double x;
        private double y;
        private String text;
        private int number;
    }

    private static final class LinkDto {
        private int fromNodeId;
        private int toNodeId;
    }
}
