package com.kyv3.addon.gui.design;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.widgets.Kyv3Colors;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class Kyv3DesignSystem {
    private Kyv3DesignSystem() {
    }

    public static Kyv3SurfaceStyle surface(Kyv3GuiTheme theme, double x, double y, Kyv3VisualState state) {
        Color accent = theme.accentAt(x, y);

        double accentStrength = theme.accentStrength.get();
        double contrast = theme.surfaceContrast.get();
        double glowStrength = theme.glowStrength.get();

        double interaction = 0;
        if (state.hovered()) interaction += 0.11;
        if (state.pressed()) interaction += 0.15;
        if (state.active()) interaction += 0.22;
        if (state.focused()) interaction += 0.14;

        Color outlineBase = theme.outlineColor.get(state.pressed(), state.hovered(), state.active());
        Color backgroundBase = theme.backgroundColor.get(state.pressed(), state.hovered(), state.active());
        Color textBase = state.disabled() ? theme.textSecondaryColor.get() : theme.textColor.get();

        Color outline = Kyv3Colors.blend(outlineBase, accent, accentStrength + contrast * 0.1 + interaction * 0.32);
        Color background = Kyv3Colors.blend(backgroundBase, accent, accentStrength * (0.65 + contrast * 0.45) + interaction * 0.22);
        Color text = Kyv3Colors.blend(textBase, Kyv3Colors.brighten(accent, 0.5), (state.active() ? 0.3 : 0.05) + interaction * 0.45);

        Color topGlow = Kyv3Colors.alpha(
            Kyv3Colors.brighten(background, 0.34),
            (int) Math.round(40 + glowStrength * 130 + interaction * 95)
        );

        Color activeLeft = Kyv3Colors.alpha(Kyv3Colors.brighten(accent, 0.33), (int) Math.round(85 + interaction * 110));
        Color activeRight = Kyv3Colors.alpha(accent, (int) Math.round(25 + interaction * 60));

        Color focusRing = Kyv3Colors.alpha(
            Kyv3Colors.brighten(accent, 0.35),
            (int) Math.round(70 + theme.focusRingStrength.get() * 160 + interaction * 45)
        );

        if (state.disabled()) {
            outline = scaleAlpha(outline, 0.55);
            background = scaleAlpha(background, 0.45);
            text = scaleAlpha(text, 0.6);
            topGlow = scaleAlpha(topGlow, 0.3);
            activeLeft = scaleAlpha(activeLeft, 0.25);
            activeRight = scaleAlpha(activeRight, 0.2);
            focusRing = scaleAlpha(focusRing, 0.3);
        }

        return new Kyv3SurfaceStyle(accent, outline, background, text, topGlow, activeLeft, activeRight, focusRing);
    }

    public static double borderSize(Kyv3GuiTheme theme) {
        return Math.max(1, theme.scale(1.5));
    }

    public static double glowHeight(Kyv3GuiTheme theme) {
        return Math.max(1, theme.scale(2));
    }

    public static double indicatorWidth(Kyv3GuiTheme theme) {
        return Math.max(2, theme.scale(3));
    }

    private static Color scaleAlpha(Color color, double factor) {
        return Kyv3Colors.alpha(color, (int) Math.round(color.a * clamp01(factor)));
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
