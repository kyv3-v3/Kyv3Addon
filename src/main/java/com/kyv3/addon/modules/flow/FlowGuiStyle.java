package com.kyv3.addon.modules.flow;

import net.minecraft.util.math.MathHelper;

final class FlowGuiStyle {
    static final int ROOT_BG_TOP = 0xD00E1218;
    static final int ROOT_BG_BOTTOM = 0xD0070A10;
    static final int ROOT_VIGNETTE_TOP = 0x42223A54;
    static final int ROOT_VIGNETTE_BOTTOM = 0x00223A54;

    static final int PANEL_TOP = 0xC5162537;
    static final int PANEL_BOTTOM = 0xBB0E1621;
    static final int PANEL_BORDER = 0xFF324A63;

    static final int SURFACE_SOFT = 0x8B121B27;
    static final int SURFACE_ALT = 0x8B101722;
    static final int SURFACE_HEADER_TOP = 0xC2283D55;
    static final int SURFACE_HEADER_BOTTOM = 0x8C1A2A3C;

    static final int INPUT_IDLE = 0xDA142131;
    static final int INPUT_HOVER = 0xE61C2E42;
    static final int INPUT_FOCUS = 0xF0243C54;
    static final int INPUT_BORDER_IDLE = 0xFF314A61;
    static final int INPUT_BORDER_HOVER = 0xFF4F7396;
    static final int INPUT_BORDER_FOCUS = 0xFF81C7FF;

    static final int TEXT_PRIMARY = 0xFFEAF3FF;
    static final int TEXT_SECONDARY = 0xFFBCD0E4;
    static final int TEXT_MUTED = 0xFF8FA5BD;

    private FlowGuiStyle() {
    }

    static int blend(int base, int target, float factor) {
        float f = MathHelper.clamp(factor, 0f, 1f);

        int a1 = (base >>> 24) & 0xFF;
        int r1 = (base >>> 16) & 0xFF;
        int g1 = (base >>> 8) & 0xFF;
        int b1 = base & 0xFF;

        int a2 = (target >>> 24) & 0xFF;
        int r2 = (target >>> 16) & 0xFF;
        int g2 = (target >>> 8) & 0xFF;
        int b2 = target & 0xFF;

        int a = (int) (a1 + (a2 - a1) * f);
        int r = (int) (r1 + (r2 - r1) * f);
        int g = (int) (g1 + (g2 - g1) * f);
        int b = (int) (b1 + (b2 - b1) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int brighten(int color, float amount) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;

        float f = MathHelper.clamp(amount, 0f, 1f);
        r = Math.min(255, (int) (r + (255 - r) * f));
        g = Math.min(255, (int) (g + (255 - g) * f));
        b = Math.min(255, (int) (b + (255 - b) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int alpha(int rgb, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    static float animate(float current, boolean active, float delta, float responsiveness) {
        return animate(current, active ? 1f : 0f, delta, responsiveness);
    }

    static float animate(float current, float target, float delta, float responsiveness) {
        float clampedDelta = Math.max(0f, delta);
        float response = Math.max(0.1f, responsiveness);
        float step = 1f - (float) Math.exp(-clampedDelta * response);
        step = MathHelper.clamp(step, 0.05f, 0.5f);
        return MathHelper.clamp(current + (target - current) * step, 0f, 1f);
    }
}
