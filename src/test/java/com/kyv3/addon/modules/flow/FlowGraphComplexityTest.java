package com.kyv3.addon.modules.flow;

import com.kyv3.addon.modules.flow.FlowForgeModule.FlowLink;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowNode;
import com.kyv3.addon.modules.flow.FlowForgeModule.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowGraphComplexityTest {
    @Test
    void moreStructuredGraphsGetHigherComplexity() {
        FlowGraphComplexity.Result simple = FlowGraphComplexity.analyze(
            List.of(
                FlowNode.create(1, NodeKind.OnEnable, 0, 0),
                FlowNode.create(2, NodeKind.Notify, 120, 0)
            ),
            List.of(new FlowLink(1, 2))
        );

        FlowGraphComplexity.Result complex = FlowGraphComplexity.analyze(
            List.of(
                FlowNode.create(1, NodeKind.OnEnable, 0, 0),
                FlowNode.create(2, NodeKind.IfHealthBelow, 120, 0),
                FlowNode.create(3, NodeKind.IfModuleActive, 240, -60),
                FlowNode.create(4, NodeKind.Notify, 360, -60),
                FlowNode.create(5, NodeKind.SendCommand, 360, 30),
                FlowNode.create(6, NodeKind.Repeat, 480, -20),
                FlowNode.create(7, NodeKind.Notify, 620, -20)
            ),
            List.of(
                new FlowLink(1, 2),
                new FlowLink(2, 3),
                new FlowLink(2, 5),
                new FlowLink(3, 4),
                new FlowLink(4, 6),
                new FlowLink(5, 6),
                new FlowLink(6, 7)
            )
        );

        assertTrue(complex.score() > simple.score());
        assertTrue(complex.maxDepth() >= simple.maxDepth());
        assertTrue(complex.branchNodes() >= 1);
    }
}
