package com.kyv3.addon.gui.design;

public record Kyv3VisualState(
    boolean active,
    boolean pressed,
    boolean hovered,
    boolean focused,
    boolean disabled
) {
    public static Kyv3VisualState idle() {
        return new Kyv3VisualState(false, false, false, false, false);
    }

    public Kyv3VisualState withActive(boolean value) {
        return new Kyv3VisualState(value, pressed, hovered, focused, disabled);
    }

    public Kyv3VisualState withPressed(boolean value) {
        return new Kyv3VisualState(active, value, hovered, focused, disabled);
    }

    public Kyv3VisualState withHovered(boolean value) {
        return new Kyv3VisualState(active, pressed, value, focused, disabled);
    }

    public Kyv3VisualState withFocused(boolean value) {
        return new Kyv3VisualState(active, pressed, hovered, value, disabled);
    }

    public Kyv3VisualState withDisabled(boolean value) {
        return new Kyv3VisualState(active, pressed, hovered, focused, value);
    }
}
