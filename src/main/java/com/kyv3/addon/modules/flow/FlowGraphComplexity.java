package com.kyv3.addon.modules.flow;

import com.kyv3.addon.modules.flow.FlowForgeModule.CustomFlowDefinition;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowLink;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlowGraphComplexity {
    private FlowGraphComplexity() {
    }

    public static Result analyze(CustomFlowDefinition definition) {
        if (definition == null || definition.graph == null) return Result.empty();

        List<FlowNode> nodes = definition.graph.nodes == null ? List.of() : definition.graph.nodes;
        List<FlowLink> links = definition.graph.links == null ? List.of() : definition.graph.links;
        return analyze(nodes, links);
    }

    public static Result analyze(List<FlowNode> nodes, List<FlowLink> links) {
        List<FlowNode> safeNodes = nodes == null ? List.of() : nodes;
        List<FlowLink> safeLinks = links == null ? List.of() : links;

        Map<Integer, FlowNode> nodesById = new HashMap<>();
        Map<Integer, List<Integer>> outgoing = new HashMap<>();
        Map<Integer, Integer> incoming = new HashMap<>();

        int eventNodes = 0;
        int actionNodes = 0;
        int conditionalNodes = 0;

        for (FlowNode node : safeNodes) {
            if (node == null || node.id <= 0 || node.kind == null) continue;
            if (nodesById.containsKey(node.id)) continue;

            nodesById.put(node.id, node);
            outgoing.put(node.id, new ArrayList<>());
            incoming.put(node.id, 0);

            if (node.kind.isEvent()) eventNodes++;
            else actionNodes++;

            if (node.kind.name().startsWith("If")) conditionalNodes++;
        }

        int validLinks = 0;
        Set<String> uniqueLinks = new HashSet<>();
        for (FlowLink link : safeLinks) {
            if (link == null) continue;
            if (link.fromNodeId == link.toNodeId) continue;
            if (!nodesById.containsKey(link.fromNodeId) || !nodesById.containsKey(link.toNodeId)) continue;

            String key = link.fromNodeId + ":" + link.toNodeId;
            if (!uniqueLinks.add(key)) continue;

            outgoing.get(link.fromNodeId).add(link.toNodeId);
            incoming.put(link.toNodeId, incoming.get(link.toNodeId) + 1);
            validLinks++;
        }

        int branchNodes = 0;
        int maxBranchWidth = 0;
        for (Map.Entry<Integer, List<Integer>> entry : outgoing.entrySet()) {
            int width = entry.getValue().size();
            if (width > 1) branchNodes++;
            if (width > maxBranchWidth) maxBranchWidth = width;
        }

        Set<Integer> rootEventIds = new LinkedHashSet<>();
        for (FlowNode node : nodesById.values()) {
            if (node.kind.isEvent()) rootEventIds.add(node.id);
        }

        int maxDepth = computeMaxDepth(rootEventIds, outgoing);
        int disconnectedActions = computeDisconnectedActions(nodesById, outgoing, rootEventIds);

        int rawScore =
            nodesById.size() * 2
                + validLinks
                + conditionalNodes * 3
                + branchNodes * 4
                + maxBranchWidth * 2
                + maxDepth * 2
                + disconnectedActions * 3;

        int normalizedScore = Math.max(0, Math.min(100, rawScore));

        return new Result(
            nodesById.size(),
            validLinks,
            eventNodes,
            actionNodes,
            conditionalNodes,
            branchNodes,
            maxBranchWidth,
            maxDepth,
            disconnectedActions,
            normalizedScore
        );
    }

    private static int computeMaxDepth(Set<Integer> roots, Map<Integer, List<Integer>> outgoing) {
        if (roots.isEmpty()) return 0;

        int max = 0;
        Map<Integer, Integer> memo = new HashMap<>();
        Set<Integer> visiting = new HashSet<>();

        for (int root : roots) {
            int depth = longestPathDepth(root, outgoing, visiting, memo);
            if (depth > max) max = depth;
        }

        return max;
    }

    private static int longestPathDepth(int nodeId, Map<Integer, List<Integer>> outgoing, Set<Integer> visiting, Map<Integer, Integer> memo) {
        Integer cached = memo.get(nodeId);
        if (cached != null) return cached;

        if (!visiting.add(nodeId)) return 0;

        int max = 0;
        for (int child : outgoing.getOrDefault(nodeId, List.of())) {
            int depth = 1 + longestPathDepth(child, outgoing, visiting, memo);
            if (depth > max) max = depth;
        }

        visiting.remove(nodeId);
        memo.put(nodeId, max);
        return max;
    }

    private static int computeDisconnectedActions(Map<Integer, FlowNode> nodesById, Map<Integer, List<Integer>> outgoing, Set<Integer> rootEventIds) {
        if (nodesById.isEmpty()) return 0;

        Set<Integer> reachable = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>(rootEventIds);

        while (!queue.isEmpty()) {
            int id = queue.removeFirst();
            if (!reachable.add(id)) continue;

            for (int child : outgoing.getOrDefault(id, List.of())) {
                if (!reachable.contains(child)) queue.addLast(child);
            }
        }

        int disconnectedActions = 0;
        for (FlowNode node : nodesById.values()) {
            if (node.kind.isEvent()) continue;
            if (!reachable.contains(node.id)) disconnectedActions++;
        }

        return disconnectedActions;
    }

    public enum Level {
        Simple,
        Standard,
        Advanced,
        Extreme
    }

    public static final class Result {
        private static final Result EMPTY = new Result(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        private final int nodeCount;
        private final int linkCount;
        private final int eventNodes;
        private final int actionNodes;
        private final int conditionalNodes;
        private final int branchNodes;
        private final int maxBranchWidth;
        private final int maxDepth;
        private final int disconnectedActions;
        private final int score;

        private Result(
            int nodeCount,
            int linkCount,
            int eventNodes,
            int actionNodes,
            int conditionalNodes,
            int branchNodes,
            int maxBranchWidth,
            int maxDepth,
            int disconnectedActions,
            int score
        ) {
            this.nodeCount = Math.max(0, nodeCount);
            this.linkCount = Math.max(0, linkCount);
            this.eventNodes = Math.max(0, eventNodes);
            this.actionNodes = Math.max(0, actionNodes);
            this.conditionalNodes = Math.max(0, conditionalNodes);
            this.branchNodes = Math.max(0, branchNodes);
            this.maxBranchWidth = Math.max(0, maxBranchWidth);
            this.maxDepth = Math.max(0, maxDepth);
            this.disconnectedActions = Math.max(0, disconnectedActions);
            this.score = Math.max(0, Math.min(100, score));
        }

        public static Result empty() {
            return EMPTY;
        }

        public int nodeCount() {
            return nodeCount;
        }

        public int linkCount() {
            return linkCount;
        }

        public int eventNodes() {
            return eventNodes;
        }

        public int actionNodes() {
            return actionNodes;
        }

        public int conditionalNodes() {
            return conditionalNodes;
        }

        public int branchNodes() {
            return branchNodes;
        }

        public int maxBranchWidth() {
            return maxBranchWidth;
        }

        public int maxDepth() {
            return maxDepth;
        }

        public int disconnectedActions() {
            return disconnectedActions;
        }

        public int score() {
            return score;
        }

        public Level level() {
            if (score < 25) return Level.Simple;
            if (score < 50) return Level.Standard;
            if (score < 75) return Level.Advanced;
            return Level.Extreme;
        }

        public String label() {
            return level().name() + " " + score;
        }
    }
}
