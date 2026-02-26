package com.kyv3.addon.modules.flow.v2.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowGraphTest {
    @Test
    void sanitizeRemovesInvalidAndDuplicateLinks() {
        List<FlowNode> nodes = new ArrayList<>();
        nodes.add(new FlowNode(1, FlowNodeKind.OnEnable, 0, 0, "", 1));
        nodes.add(new FlowNode(2, FlowNodeKind.SendMessage, 10, 10, "hi", 1));

        List<FlowLink> links = new ArrayList<>();
        links.add(new FlowLink(1, 2));
        links.add(new FlowLink(1, 2));
        links.add(new FlowLink(2, 1));
        links.add(new FlowLink(2, 2));
        links.add(new FlowLink(2, 99));
        links.add(null);

        FlowGraph graph = new FlowGraph(1, nodes, links);
        graph.sanitize();

        assertEquals(2, graph.nodesView().size());
        assertEquals(2, graph.linksView().size());
        assertEquals(3, graph.nextNodeId());
    }
}
