package com.kyv3.addon.modules.flow;

import com.kyv3.addon.modules.flow.FlowForgeModule.FlowLink;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowNode;
import com.kyv3.addon.modules.flow.FlowForgeModule.GraphSnapshot;
import com.kyv3.addon.modules.flow.FlowForgeModule.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowGraphCleanupTest {
    @Test
    void removesInvalidAndDuplicateStructures() {
        GraphSnapshot graph = new GraphSnapshot();

        FlowNode a = FlowNode.create(1, NodeKind.OnEnable, 0, 0);
        FlowNode b = FlowNode.create(2, NodeKind.Notify, 100, 0);
        FlowNode dup = FlowNode.create(2, NodeKind.Notify, 220, 0);
        b.number = 0;
        b.text = null;

        graph.nodes.add(a);
        graph.nodes.add(b);
        graph.nodes.add(dup);

        graph.links.add(new FlowLink(1, 2));
        graph.links.add(new FlowLink(1, 2));
        graph.links.add(new FlowLink(2, 2));
        graph.links.add(new FlowLink(2, 99));

        FlowGraphCleanup.Report report = FlowGraphCleanup.cleanupInPlace(graph, false);

        assertTrue(report.changed());
        assertEquals(2, graph.nodes.size());
        assertEquals(1, graph.links.size());
        assertEquals(1, report.removedDuplicateNodes());
        assertEquals(1, report.removedDuplicateLinks());
        assertTrue(report.removedInvalidLinks() >= 2);
        assertEquals(1, graph.links.getFirst().fromNodeId);
        assertEquals(2, graph.links.getFirst().toNodeId);
        assertEquals(1, graph.nodes.get(1).number);
        assertEquals("", graph.nodes.get(1).text);
    }

    @Test
    void prunesUnreachableActionNodesWhenRequested() {
        GraphSnapshot graph = new GraphSnapshot();

        FlowNode event = FlowNode.create(1, NodeKind.OnEnable, 0, 0);
        FlowNode reachable = FlowNode.create(2, NodeKind.Notify, 120, 0);
        FlowNode unreachable = FlowNode.create(3, NodeKind.Notify, 240, 0);

        graph.nodes.add(event);
        graph.nodes.add(reachable);
        graph.nodes.add(unreachable);
        graph.links.add(new FlowLink(1, 2));

        FlowGraphCleanup.Report report = FlowGraphCleanup.cleanupInPlace(graph, true);

        assertTrue(report.changed());
        assertEquals(1, report.removedUnreachableNodes());
        assertEquals(2, graph.nodes.size());
        assertEquals(1, graph.links.size());
    }
}
