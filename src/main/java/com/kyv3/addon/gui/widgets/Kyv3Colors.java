package com.kyv3.addon.gui.widgets;

import meteordevelopment.meteorclient.utils.render.color.Color;

public final class Kyv3Colors {
    private Kyv3Colors() {
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.r, color.g, color.b, clamp(alpha));
    }

    public static Color blend(Color a, Color b, double t) {
        double clamped = clamp01(t);
        return new Color(
            mix(a.r, b.r, clamped),
            mix(a.g, b.g, clamped),
            mix(a.b, b.b, clamped),
            mix(a.a, b.a, clamped)
        );
    }

    public static Color brighten(Color color, double amount) {
        double clamped = clamp01(amount);
        return new Color(
            mix(color.r, 255, clamped),
            mix(color.g, 255, clamped),
            mix(color.b, 255, clamped),
            color.a
        );
    }

    public static Color rainbow(double offset, int alpha) {
        double hue = (System.currentTimeMillis() / 12.0 + offset) % 360.0;
        Color rainbow = Color.fromHsv(hue, 0.85, 1.0);
        rainbow.a = clamp(alpha);
        return rainbow;
    }

    private static int mix(int a, int b, double t) {
        return clamp((int) Math.round(a + (b - a) * t));
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
