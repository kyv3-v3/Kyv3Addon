package com.kyv3.addon.modules.flow.v2.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlowGraph {
    private int nextNodeId = 1;
    private final List<FlowNode> nodes = new ArrayList<>();
    private final List<FlowLink> links = new ArrayList<>();

    public FlowGraph() {
    }

    public FlowGraph(int nextNodeId, List<FlowNode> nodes, List<FlowLink> links) {
        this.nextNodeId = Math.max(1, nextNodeId);

        if (nodes != null) {
            for (FlowNode node : nodes) {
                if (node != null) this.nodes.add(node.copy());
            }
        }

        if (links != null) {
            this.links.addAll(links);
        }
    }

    public FlowGraph copy() {
        return new FlowGraph(nextNodeId, nodes, links);
    }

    public int nextNodeId() {
        return nextNodeId;
    }

    public void setNextNodeId(int nextNodeId) {
        this.nextNodeId = Math.max(1, nextNodeId);
    }

    public List<FlowNode> nodesView() {
        return Collections.unmodifiableList(nodes);
    }

    public List<FlowLink> linksView() {
        return Collections.unmodifiableList(links);
    }

    public FlowNode getNodeById(int nodeId) {
        for (FlowNode node : nodes) {
            if (node.id() == nodeId) return node;
        }

        return null;
    }

    public FlowNode addNode(FlowNodeKind kind, double x, double y) {
        FlowNode node = FlowNode.createWithDefaults(nextNodeId++, kind, x, y);
        nodes.add(node);
        return node;
    }

    public FlowNode duplicateNode(int sourceNodeId, double offsetX, double offsetY) {
        FlowNode source = getNodeById(sourceNodeId);
        if (source == null) return null;

        FlowNode duplicate = FlowNode.createWithDefaults(nextNodeId++, source.kind(), source.x() + offsetX, source.y() + offsetY);
        duplicate.setText(source.text());
        duplicate.setNumber(source.number());
        nodes.add(duplicate);
        return duplicate;
    }

    public boolean moveNode(int nodeId, double x, double y) {
        FlowNode node = getNodeById(nodeId);
        if (node == null) return false;

        node.setX(x);
        node.setY(y);
        return true;
    }

    public boolean setNodeText(int nodeId, String text) {
        FlowNode node = getNodeById(nodeId);
        if (node == null || !node.kind().supportsText()) return false;

        node.setText(text);
        return true;
    }

    public boolean setNodeNumber(int nodeId, int number) {
        FlowNode node = getNodeById(nodeId);
        if (node == null || !node.kind().supportsNumber()) return false;

        node.setNumber(number);
        return true;
    }

    public boolean removeNode(int nodeId) {
        boolean removed = nodes.removeIf(node -> node.id() == nodeId);
        if (!removed) return false;

        links.removeIf(link -> link.fromNodeId() == nodeId || link.toNodeId() == nodeId);
        return true;
    }

    public boolean bringNodeToFront(int nodeId) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id() != nodeId) continue;

            if (i != nodes.size() - 1) {
                FlowNode node = nodes.remove(i);
                nodes.add(node);
            }

            return true;
        }

        return false;
    }

    public boolean addLink(int fromNodeId, int toNodeId) {
        if (fromNodeId == toNodeId) return false;
        if (!containsNodeId(fromNodeId) || !containsNodeId(toNodeId)) return false;

        for (FlowLink link : links) {
            if (link.fromNodeId() == fromNodeId && link.toNodeId() == toNodeId) {
                return false;
            }
        }

        links.add(new FlowLink(fromNodeId, toNodeId));
        return true;
    }

    public boolean removeLink(FlowLink link) {
        if (link == null) return false;
        return links.remove(link);
    }

    public void clear() {
        nodes.clear();
        links.clear();
        nextNodeId = 1;
    }

    public void sanitize() {
        Map<Integer, FlowNode> validNodes = new LinkedHashMap<>();
        Map<Long, FlowLink> validLinks = new LinkedHashMap<>();
        int maxNodeId = 0;

        for (FlowNode node : nodes) {
            if (node == null || node.id() <= 0 || node.kind() == null) continue;
            if (validNodes.containsKey(node.id())) continue;

            node.sanitize();
            validNodes.put(node.id(), node);
            maxNodeId = Math.max(maxNodeId, node.id());
        }

        nodes.clear();
        nodes.addAll(validNodes.values());

        for (FlowLink link : links) {
            if (link == null) continue;
            if (link.fromNodeId() == link.toNodeId()) continue;
            if (!validNodes.containsKey(link.fromNodeId()) || !validNodes.containsKey(link.toNodeId())) continue;

            long key = linkKey(link.fromNodeId(), link.toNodeId());
            validLinks.putIfAbsent(key, link);
        }

        links.clear();
        links.addAll(validLinks.values());

        nextNodeId = Math.max(Math.max(1, nextNodeId), maxNodeId + 1);
    }

    private boolean containsNodeId(int nodeId) {
        return getNodeById(nodeId) != null;
    }

    private static long linkKey(int fromNodeId, int toNodeId) {
        return ((long) fromNodeId << 32) ^ (toNodeId & 0xFFFFFFFFL);
    }
}
