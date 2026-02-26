package com.kyv3.addon.modules.flow;

import com.kyv3.addon.modules.flow.FlowForgeModule.FlowLink;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlowGraphCleanup {
    private FlowGraphCleanup() {
    }

    public static Report cleanupInPlace(FlowForgeModule.GraphSnapshot graph, boolean pruneUnreachableActions) {
        if (graph == null) return Report.empty();

        if (graph.nodes == null) graph.nodes = new ArrayList<>();
        if (graph.links == null) graph.links = new ArrayList<>();

        int beforeNodes = graph.nodes.size();
        int beforeLinks = graph.links.size();

        int removedInvalidNodes = 0;
        int removedDuplicateNodes = 0;
        int normalizedNodeFields = 0;

        Map<Integer, FlowNode> uniqueNodes = new LinkedHashMap<>();
        for (FlowNode node : graph.nodes) {
            if (node == null || node.id <= 0 || node.kind == null) {
                removedInvalidNodes++;
                continue;
            }

            if (uniqueNodes.containsKey(node.id)) {
                removedDuplicateNodes++;
                continue;
            }

            int normalized = normalizeNode(node);
            normalizedNodeFields += normalized;
            uniqueNodes.put(node.id, node);
        }

        graph.nodes.clear();
        graph.nodes.addAll(uniqueNodes.values());

        int removedInvalidLinks = 0;
        int removedDuplicateLinks = 0;

        Set<String> uniqueLinks = new HashSet<>();
        List<FlowLink> cleanedLinks = new ArrayList<>();
        for (FlowLink link : graph.links) {
            if (link == null || link.fromNodeId == link.toNodeId) {
                removedInvalidLinks++;
                continue;
            }

            if (!uniqueNodes.containsKey(link.fromNodeId) || !uniqueNodes.containsKey(link.toNodeId)) {
                removedInvalidLinks++;
                continue;
            }

            String key = link.fromNodeId + ":" + link.toNodeId;
            if (!uniqueLinks.add(key)) {
                removedDuplicateLinks++;
                continue;
            }

            cleanedLinks.add(link);
        }

        graph.links.clear();
        graph.links.addAll(cleanedLinks);

        int removedUnreachableNodes = 0;
        int removedOrphanLinks = 0;

        if (pruneUnreachableActions && !graph.nodes.isEmpty()) {
            Set<Integer> reachable = computeReachableNodeIds(graph.nodes, graph.links);
            Set<Integer> validAfterPrune = new HashSet<>();
            List<FlowNode> prunedNodes = new ArrayList<>(graph.nodes.size());

            for (FlowNode node : graph.nodes) {
                if (node.kind.isEvent() || reachable.contains(node.id)) {
                    prunedNodes.add(node);
                    validAfterPrune.add(node.id);
                } else {
                    removedUnreachableNodes++;
                }
            }

            if (removedUnreachableNodes > 0) {
                graph.nodes.clear();
                graph.nodes.addAll(prunedNodes);

                List<FlowLink> prunedLinks = new ArrayList<>(graph.links.size());
                for (FlowLink link : graph.links) {
                    if (!validAfterPrune.contains(link.fromNodeId) || !validAfterPrune.contains(link.toNodeId)) {
                        removedOrphanLinks++;
                        continue;
                    }
                    prunedLinks.add(link);
                }

                graph.links.clear();
                graph.links.addAll(prunedLinks);
            }
        }

        int maxNodeId = 0;
        for (FlowNode node : graph.nodes) {
            if (node.id > maxNodeId) maxNodeId = node.id;
        }

        int expectedNextNodeId = Math.max(1, maxNodeId + 1);
        boolean nextNodeIdRebased = graph.nextNodeId != expectedNextNodeId;
        graph.nextNodeId = Math.max(expectedNextNodeId, graph.nextNodeId);

        int afterNodes = graph.nodes.size();
        int afterLinks = graph.links.size();

        boolean changed =
            removedInvalidNodes > 0
                || removedDuplicateNodes > 0
                || removedInvalidLinks > 0
                || removedDuplicateLinks > 0
                || removedUnreachableNodes > 0
                || removedOrphanLinks > 0
                || normalizedNodeFields > 0
                || nextNodeIdRebased;

        return new Report(
            beforeNodes,
            afterNodes,
            beforeLinks,
            afterLinks,
            removedInvalidNodes,
            removedDuplicateNodes,
            removedInvalidLinks,
            removedDuplicateLinks,
            removedUnreachableNodes,
            removedOrphanLinks,
            normalizedNodeFields,
            nextNodeIdRebased,
            changed
        );
    }

    private static int normalizeNode(FlowNode node) {
        int changes = 0;

        if (node.text == null) {
            node.text = "";
            changes++;
        }

        if (node.number < 1) {
            node.number = 1;
            changes++;
        }

        return changes;
    }

    private static Set<Integer> computeReachableNodeIds(List<FlowNode> nodes, List<FlowLink> links) {
        Map<Integer, List<Integer>> outgoing = new HashMap<>();
        for (FlowNode node : nodes) {
            outgoing.put(node.id, new ArrayList<>());
        }

        for (FlowLink link : links) {
            List<Integer> children = outgoing.get(link.fromNodeId);
            if (children != null) children.add(link.toNodeId);
        }

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (FlowNode node : nodes) {
            if (node.kind.isEvent()) queue.addLast(node.id);
        }

        Set<Integer> reachable = new HashSet<>();
        while (!queue.isEmpty()) {
            int id = queue.removeFirst();
            if (!reachable.add(id)) continue;

            for (int child : outgoing.getOrDefault(id, List.of())) {
                if (!reachable.contains(child)) queue.addLast(child);
            }
        }

        return reachable;
    }

    public static final class Report {
        private static final Report EMPTY = new Report(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false);

        private final int beforeNodes;
        private final int afterNodes;
        private final int beforeLinks;
        private final int afterLinks;
        private final int removedInvalidNodes;
        private final int removedDuplicateNodes;
        private final int removedInvalidLinks;
        private final int removedDuplicateLinks;
        private final int removedUnreachableNodes;
        private final int removedOrphanLinks;
        private final int normalizedNodeFields;
        private final boolean nextNodeIdRebased;
        private final boolean changed;

        private Report(
            int beforeNodes,
            int afterNodes,
            int beforeLinks,
            int afterLinks,
            int removedInvalidNodes,
            int removedDuplicateNodes,
            int removedInvalidLinks,
            int removedDuplicateLinks,
            int removedUnreachableNodes,
            int removedOrphanLinks,
            int normalizedNodeFields,
            boolean nextNodeIdRebased,
            boolean changed
        ) {
            this.beforeNodes = Math.max(0, beforeNodes);
            this.afterNodes = Math.max(0, afterNodes);
            this.beforeLinks = Math.max(0, beforeLinks);
            this.afterLinks = Math.max(0, afterLinks);
            this.removedInvalidNodes = Math.max(0, removedInvalidNodes);
            this.removedDuplicateNodes = Math.max(0, removedDuplicateNodes);
            this.removedInvalidLinks = Math.max(0, removedInvalidLinks);
            this.removedDuplicateLinks = Math.max(0, removedDuplicateLinks);
            this.removedUnreachableNodes = Math.max(0, removedUnreachableNodes);
            this.removedOrphanLinks = Math.max(0, removedOrphanLinks);
            this.normalizedNodeFields = Math.max(0, normalizedNodeFields);
            this.nextNodeIdRebased = nextNodeIdRebased;
            this.changed = changed;
        }

        public static Report empty() {
            return EMPTY;
        }

        public int beforeNodes() {
            return beforeNodes;
        }

        public int afterNodes() {
            return afterNodes;
        }

        public int beforeLinks() {
            return beforeLinks;
        }

        public int afterLinks() {
            return afterLinks;
        }

        public int removedInvalidNodes() {
            return removedInvalidNodes;
        }

        public int removedDuplicateNodes() {
            return removedDuplicateNodes;
        }

        public int removedInvalidLinks() {
            return removedInvalidLinks;
        }

        public int removedDuplicateLinks() {
            return removedDuplicateLinks;
        }

        public int removedUnreachableNodes() {
            return removedUnreachableNodes;
        }

        public int removedOrphanLinks() {
            return removedOrphanLinks;
        }

        public int normalizedNodeFields() {
            return normalizedNodeFields;
        }

        public boolean nextNodeIdRebased() {
            return nextNodeIdRebased;
        }

        public boolean changed() {
            return changed;
        }

        public String summary() {
            return "nodes " + beforeNodes + "->" + afterNodes
                + ", links " + beforeLinks + "->" + afterLinks
                + ", removed=" + (removedInvalidNodes + removedDuplicateNodes + removedInvalidLinks + removedDuplicateLinks + removedUnreachableNodes + removedOrphanLinks);
        }
    }
}
