package com.kyv3.addon.modules.flow.v2.domain;

import java.util.Objects;

public final class FlowNode {
    private int id;
    private FlowNodeKind kind;
    private double x;
    private double y;
    private String text;
    private int number;

    public FlowNode() {
        this(0, FlowNodeKind.Notify, 0, 0, "", 1);
    }

    public FlowNode(int id, FlowNodeKind kind, double x, double y, String text, int number) {
        this.id = id;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.text = text == null ? "" : text;
        this.number = Math.max(1, number);
    }

    public static FlowNode createWithDefaults(int id, FlowNodeKind kind, double x, double y) {
        FlowNode node = new FlowNode(id, kind, x, y, "", 1);

        switch (kind) {
            case OnTick -> node.number = 20;
            case OnChatMatch -> node.text = "vote";
            case Wait -> node.number = 20;
            case SendMessage -> node.text = "/spawn";
            case SendCommand -> node.text = "spawn";
            case Notify -> node.text = "Custom module executed.";
            case ToggleModule, EnableModule, DisableModule -> node.text = "auto-login";
            case Chance -> node.number = 50;
            case Repeat -> node.number = 2;
            case IfHealthBelow -> node.number = 10;
            case IfHealthAbove -> node.number = 10;
            case IfHungerBelow -> node.number = 14;
            case IfMoving -> node.number = 10;
            case IfModuleActive -> node.text = "auto-login";
            case IfHoldingItem -> node.text = "totem";
            case IfDimensionContains -> node.text = "overworld";
            case IfTargetEntityContains -> node.text = "player";
            case SelectHotbar -> node.number = 1;
            case LookYaw -> node.number = 0;
            case LookPitch -> node.number = 0;
            case AddForwardVelocity -> node.number = 30;
            case AddVerticalVelocity -> node.number = 25;
            default -> {
            }
        }

        return node;
    }

    public FlowNode copy() {
        return new FlowNode(id, kind, x, y, text, number);
    }

    public void sanitize() {
        text = text == null ? "" : text;
        number = Math.max(1, number);
    }

    public int id() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public FlowNodeKind kind() {
        return kind;
    }

    public void setKind(FlowNodeKind kind) {
        this.kind = kind;
    }

    public double x() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double y() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String text() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public int number() {
        return number;
    }

    public void setNumber(int number) {
        this.number = Math.max(1, number);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FlowNode node)) return false;

        return id == node.id && Double.compare(node.x, x) == 0 && Double.compare(node.y, y) == 0 && number == node.number && kind == node.kind && Objects.equals(text, node.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, x, y, text, number);
    }
}
