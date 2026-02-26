package com.kyv3.addon.modules.flow;

import com.kyv3.addon.modules.flow.FlowForgeModule.CustomFlowDefinition;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowLink;
import com.kyv3.addon.modules.flow.FlowForgeModule.FlowNode;
import com.kyv3.addon.modules.flow.FlowForgeModule.NodeKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FlowGraphDiagnostics {
    private static final int MAX_MESSAGES = 24;

    private FlowGraphDiagnostics() {
    }

    public static Result analyze(CustomFlowDefinition definition) {
        if (definition == null || definition.graph == null) {
            return Result.empty();
        }

        List<FlowNode> nodes = definition.graph.nodes == null ? List.of() : definition.graph.nodes;
        List<FlowLink> links = definition.graph.links == null ? List.of() : definition.graph.links;
        return analyze(nodes, links);
    }

    public static Result analyze(List<FlowNode> nodes, List<FlowLink> links) {
        List<FlowNode> safeNodes = nodes == null ? List.of() : nodes;
        List<FlowLink> safeLinks = links == null ? List.of() : links;

        Map<Integer, Integer> incoming = new HashMap<>();
        Map<Integer, Integer> outgoing = new HashMap<>();
        Set<Integer> validNodeIds = new LinkedHashSet<>();

        int eventCount = 0;
        int actionCount = 0;

        for (FlowNode node : safeNodes) {
            if (node == null || node.id <= 0 || node.kind == null) continue;

            validNodeIds.add(node.id);
            incoming.put(node.id, 0);
            outgoing.put(node.id, 0);

            if (node.kind.isEvent()) eventCount++;
            else actionCount++;
        }

        Set<Integer> issueNodeIds = new LinkedHashSet<>();
        Set<Long> seenLinks = new LinkedHashSet<>();
        List<String> messages = new ArrayList<>();

        int warningCount = 0;
        int errorCount = 0;
        int emptyTextCount = 0;
        int unreachableCount = 0;

        if (validNodeIds.isEmpty()) {
            errorCount = appendError(messages, "Graph is empty. Add at least one event and one action node.", errorCount);
            return new Result(
                0,
                0,
                0,
                0,
                warningCount,
                errorCount,
                emptyTextCount,
                unreachableCount,
                issueNodeIds,
                messages
            );
        }

        int invalidLinkCount = 0;
        int duplicateLinkCount = 0;
        for (FlowLink link : safeLinks) {
            if (link == null || link.fromNodeId == link.toNodeId) {
                invalidLinkCount++;
                continue;
            }

            if (!validNodeIds.contains(link.fromNodeId) || !validNodeIds.contains(link.toNodeId)) {
                invalidLinkCount++;
                if (validNodeIds.contains(link.fromNodeId)) issueNodeIds.add(link.fromNodeId);
                if (validNodeIds.contains(link.toNodeId)) issueNodeIds.add(link.toNodeId);
                continue;
            }

            long linkKey = linkKey(link.fromNodeId, link.toNodeId);
            if (!seenLinks.add(linkKey)) {
                duplicateLinkCount++;
                issueNodeIds.add(link.fromNodeId);
                issueNodeIds.add(link.toNodeId);
                continue;
            }

            outgoing.put(link.fromNodeId, outgoing.getOrDefault(link.fromNodeId, 0) + 1);
            incoming.put(link.toNodeId, incoming.getOrDefault(link.toNodeId, 0) + 1);
        }

        if (invalidLinkCount > 0) {
            warningCount = appendWarning(messages, "Graph contains " + invalidLinkCount + " invalid link(s).", warningCount);
        }

        if (duplicateLinkCount > 0) {
            warningCount = appendWarning(messages, "Graph contains " + duplicateLinkCount + " duplicate link(s).", warningCount);
        }

        if (eventCount == 0) {
            errorCount = appendError(messages, "No event node present. Runtime will never trigger actions.", errorCount);
        }

        if (actionCount == 0) {
            errorCount = appendError(messages, "No action nodes present. Add executable nodes to produce behavior.", errorCount);
        }

        for (FlowNode node : safeNodes) {
            if (node == null || node.id <= 0 || node.kind == null) continue;

            int in = incoming.getOrDefault(node.id, 0);
            int out = outgoing.getOrDefault(node.id, 0);

            if (node.kind.isEvent()) {
                if (out == 0) {
                    warningCount = appendNodeWarning(issueNodeIds, messages, node.id, "Event #" + node.id + " has no outgoing path.", warningCount);
                }
            } else {
                if (in == 0) {
                    unreachableCount++;
                    warningCount = appendNodeWarning(issueNodeIds, messages, node.id, "Node #" + node.id + " is unreachable (no incoming links).", warningCount);
                }
            }

            if (requiresText(node.kind) && (node.text == null || node.text.trim().isEmpty())) {
                emptyTextCount++;
                warningCount = appendNodeWarning(issueNodeIds, messages, node.id, "Node #" + node.id + " requires text input.", warningCount);
            }
        }

        return new Result(
            validNodeIds.size(),
            safeLinks.size(),
            eventCount,
            actionCount,
            warningCount,
            errorCount,
            emptyTextCount,
            unreachableCount,
            issueNodeIds,
            messages
        );
    }

    private static int appendError(List<String> messages, String message, int counter) {
        appendMessage(messages, message);
        return counter + 1;
    }

    private static int appendWarning(List<String> messages, String message, int counter) {
        appendMessage(messages, message);
        return counter + 1;
    }

    private static int appendNodeWarning(Set<Integer> issueNodeIds, List<String> messages, int nodeId, String message, int counter) {
        issueNodeIds.add(nodeId);
        return appendWarning(messages, message, counter);
    }

    private static void appendMessage(List<String> messages, String message) {
        if (messages.size() < MAX_MESSAGES) {
            messages.add(message);
            return;
        }

        if (!messages.isEmpty()) {
            int last = messages.size() - 1;
            String overflow = "... additional diagnostics omitted ...";
            if (!overflow.equals(messages.get(last))) messages.set(last, overflow);
        }
    }

    private static long linkKey(int fromNodeId, int toNodeId) {
        return ((long) fromNodeId << 32) ^ (toNodeId & 0xFFFFFFFFL);
    }

    private static boolean requiresText(NodeKind kind) {
        return switch (kind) {
            case OnChatMatch,
                 SendMessage,
                 SendCommand,
                 Notify,
                 ToggleModule,
                 EnableModule,
                 DisableModule,
                 IfModuleActive,
                 IfHoldingItem,
                 IfDimensionContains,
                 IfTargetEntityContains -> true;
            default -> false;
        };
    }

    public enum HealthLevel {
        Healthy,
        Warning,
        Critical
    }

    public static final class Result {
        private static final Result EMPTY = new Result(0, 0, 0, 0, 0, 1, 0, 0, Set.of(), List.of("Graph is empty. Add at least one event and one action node."));

        private final int nodeCount;
        private final int linkCount;
        private final int eventCount;
        private final int actionCount;
        private final int warningCount;
        private final int errorCount;
        private final int emptyTextCount;
        private final int unreachableCount;
        private final Set<Integer> issueNodeIds;
        private final List<String> messages;

        private Result(
            int nodeCount,
            int linkCount,
            int eventCount,
            int actionCount,
            int warningCount,
            int errorCount,
            int emptyTextCount,
            int unreachableCount,
            Set<Integer> issueNodeIds,
            List<String> messages
        ) {
            this.nodeCount = Math.max(0, nodeCount);
            this.linkCount = Math.max(0, linkCount);
            this.eventCount = Math.max(0, eventCount);
            this.actionCount = Math.max(0, actionCount);
            this.warningCount = Math.max(0, warningCount);
            this.errorCount = Math.max(0, errorCount);
            this.emptyTextCount = Math.max(0, emptyTextCount);
            this.unreachableCount = Math.max(0, unreachableCount);
            this.issueNodeIds = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(issueNodeIds, "issueNodeIds")));
            this.messages = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(messages, "messages")));
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

        public int eventCount() {
            return eventCount;
        }

        public int actionCount() {
            return actionCount;
        }

        public int warningCount() {
            return warningCount;
        }

        public int errorCount() {
            return errorCount;
        }

        public int emptyTextCount() {
            return emptyTextCount;
        }

        public int unreachableCount() {
            return unreachableCount;
        }

        public int totalIssues() {
            return warningCount + errorCount;
        }

        public Set<Integer> issueNodeIds() {
            return issueNodeIds;
        }

        public List<String> messages() {
            return messages;
        }

        public int healthScore() {
            int score = 100;
            score -= errorCount * 20;
            score -= warningCount * 8;
            score -= Math.max(0, emptyTextCount - 1) * 2;
            return Math.max(0, Math.min(100, score));
        }

        public HealthLevel healthLevel() {
            if (errorCount > 0 || healthScore() < 60) return HealthLevel.Critical;
            if (warningCount > 0 || healthScore() < 85) return HealthLevel.Warning;
            return HealthLevel.Healthy;
        }

        public String healthLabel() {
            return switch (healthLevel()) {
                case Healthy -> "Healthy " + healthScore();
                case Warning -> "Warning " + healthScore();
                case Critical -> "Critical " + healthScore();
            };
        }

        public String shortGraphSummary() {
            return nodeCount + "N/" + linkCount + "L";
        }
    }
}
