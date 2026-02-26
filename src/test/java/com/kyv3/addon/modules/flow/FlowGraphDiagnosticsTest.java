package com.kyv3.addon.modules.flow;

import com.kyv3.addon.modules.flow.FlowForgeModule.FlowLink;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowNode;
import com.kyv3.addon.modules.flow.FlowForgeModule.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowGraphDiagnosticsTest {
    @Test
    void emptyGraphIsCritical() {
        FlowGraphDiagnostics.Result result = FlowGraphDiagnostics.analyze(List.of(), List.of());

        assertEquals(0, result.nodeCount());
        assertTrue(result.errorCount() > 0);
        assertEquals(FlowGraphDiagnostics.HealthLevel.Critical, result.healthLevel());
        assertTrue(result.messages().stream().anyMatch(message -> message.toLowerCase().contains("empty")));
    }

    @Test
    void healthyGraphReturnsNoIssues() {
        FlowNode event = FlowNode.create(1, NodeKind.OnEnable, 0, 0);
        FlowNode action = FlowNode.create(2, NodeKind.SendMessage, 120, 0);

        FlowGraphDiagnostics.Result result = FlowGraphDiagnostics.analyze(
            List.of(event, action),
            List.of(new FlowLink(1, 2))
        );

        assertEquals(2, result.nodeCount());
        assertEquals(1, result.linkCount());
        assertEquals(0, result.errorCount());
        assertEquals(0, result.warningCount());
        assertEquals(FlowGraphDiagnostics.HealthLevel.Healthy, result.healthLevel());
        assertEquals(100, result.healthScore());
    }

    @Test
    void warnsForUnreachableAndMissingText() {
        FlowNode event = FlowNode.create(1, NodeKind.OnEnable, 0, 0);
        FlowNode reachable = FlowNode.create(2, NodeKind.Notify, 140, 0);
        FlowNode unreachable = FlowNode.create(3, NodeKind.SendCommand, 280, 0);
        unreachable.text = "";

        FlowGraphDiagnostics.Result result = FlowGraphDiagnostics.analyze(
            List.of(event, reachable, unreachable),
            List.of(new FlowLink(1, 2))
        );

        assertTrue(result.warningCount() >= 2);
        assertTrue(result.issueNodeIds().contains(3));
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("requires text")));
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("unreachable")));
        assertTrue(result.healthScore() < 100);
    }

    @Test
    void warnsForInvalidLinks() {
        FlowNode event = FlowNode.create(1, NodeKind.OnEnable, 0, 0);
        FlowNode action = FlowNode.create(2, NodeKind.Notify, 120, 0);

        FlowGraphDiagnostics.Result result = FlowGraphDiagnostics.analyze(
            List.of(event, action),
            List.of(
                new FlowLink(1, 2),
                new FlowLink(2, 2),
                new FlowLink(2, 99)
            )
        );

        assertTrue(result.warningCount() > 0);
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("invalid link")));
    }

    @Test
    void warnsForDuplicateLinksAndMarksNodes() {
        FlowNode event = FlowNode.create(1, NodeKind.OnEnable, 0, 0);
        FlowNode action = FlowNode.create(2, NodeKind.Notify, 120, 0);

        FlowGraphDiagnostics.Result result = FlowGraphDiagnostics.analyze(
            List.of(event, action),
            List.of(
                new FlowLink(1, 2),
                new FlowLink(1, 2),
                new FlowLink(1, 2)
            )
        );

        assertTrue(result.warningCount() >= 1);
        assertTrue(result.issueNodeIds().contains(1));
        assertTrue(result.issueNodeIds().contains(2));
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("duplicate link")));
    }

    @Test
    void capsMessagesAndReportsOverflow() {
        FlowNode event = FlowNode.create(1, NodeKind.OnEnable, 0, 0);
        FlowNode starter = FlowNode.create(2, NodeKind.Notify, 120, 0);

        List<FlowNode> nodes = new java.util.ArrayList<>();
        nodes.add(event);
        nodes.add(starter);

        for (int i = 0; i < 40; i++) {
            FlowNode unreachable = FlowNode.create(3 + i, NodeKind.SendCommand, 240 + i * 10, 0);
            unreachable.text = "";
            nodes.add(unreachable);
        }

        FlowGraphDiagnostics.Result result = FlowGraphDiagnostics.analyze(
            nodes,
            List.of(new FlowLink(1, 2))
        );

        assertEquals(24, result.messages().size());
        assertEquals("... additional diagnostics omitted ...", result.messages().getLast());
        assertTrue(result.totalIssues() > result.messages().size());
    }
}
