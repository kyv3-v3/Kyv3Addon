package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.design.Kyv3DesignSystem;
import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import com.kyv3.addon.gui.motion.Kyv3Motion;
import com.kyv3.addon.gui.motion.Kyv3MotionValue;
import com.kyv3.addon.gui.render.GuiRenderUtils;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.BaseWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public interface Kyv3Widget extends BaseWidget {
    default Kyv3GuiTheme kyv3theme() {
        return (Kyv3GuiTheme) getTheme();
    }

    default double motionSpeed(double base) {
        return kyv3theme().motionSpeed(base);
    }

    default double spring01(Kyv3MotionValue value, boolean enabled, double delta, double stiffnessScale, double dampingScale) {
        Kyv3GuiTheme theme = kyv3theme();
        double stiffness = theme.springStiffness() * motionSpeed(stiffnessScale);
        double damping = theme.springDamping() * motionSpeed(dampingScale);
        return value.spring01(enabled ? 1 : 0, delta, stiffness, damping);
    }

    default double hoverLiftOffset(double progress) {
        Kyv3GuiTheme theme = kyv3theme();
        return theme.scale(2.2) * theme.hoverLiftAmount() * Kyv3Motion.clamp01(progress);
    }

    default Kyv3SurfaceStyle surfaceStyle(Kyv3VisualState state, double x, double y) {
        return Kyv3DesignSystem.surface(kyv3theme(), x, y, state);
    }

    default void renderTopGlow(GuiRenderer renderer, double x, double y, double width, Color color) {
        double glowHeight = Kyv3DesignSystem.glowHeight(kyv3theme());
        double border = Kyv3DesignSystem.borderSize(kyv3theme());
        renderer.quad(
            x + border,
            y + border,
            width - border * 2,
            glowHeight,
            color
        );
    }

    default void renderTopGlow(GuiRenderer renderer, WWidget widget, Color color) {
        renderTopGlow(renderer, widget.x, widget.y, widget.width, color);
    }

    default void renderRoundedBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
        Kyv3GuiTheme theme = kyv3theme();
        renderRoundedBackground(renderer, widget, theme.outlineColor.get(pressed, mouseOver), theme.backgroundColor.get(pressed, mouseOver));
    }

    default void renderRoundedBackground(GuiRenderer renderer, WWidget widget, Color outline, Color background) {
        renderRoundedBackground(renderer, widget.x, widget.y, widget.width, widget.height, outline, background);
    }

    default void renderRoundedBackground(GuiRenderer renderer, double x, double y, double width, double height, Color outline, Color background) {
        Kyv3GuiTheme theme = kyv3theme();
        int round = theme.roundAmount();
        double outlineSize = Math.max(1, theme.scale(1.5));
        double inset = outlineSize;

        GuiRenderUtils.quadRounded(renderer, x + inset, y + inset, width - inset * 2, height - inset * 2, background, Math.max(0, round - inset));
        GuiRenderUtils.quadOutlineRounded(renderer, x, y, width, height, outline, round, outlineSize);
    }

    default void renderRoundedBackground(GuiRenderer renderer, WWidget widget, Kyv3SurfaceStyle style, boolean focused) {
        renderRoundedBackground(renderer, widget.x, widget.y, widget.width, widget.height, style, focused);
    }

    default void renderRoundedBackground(GuiRenderer renderer, double x, double y, double width, double height, Kyv3SurfaceStyle style, boolean focused) {
        Kyv3GuiTheme theme = kyv3theme();
        int round = theme.roundAmount();
        double outlineSize = Kyv3DesignSystem.borderSize(theme);
        double inset = outlineSize;

        if (focused) {
            double ring = outlineSize + theme.scale(1);
            GuiRenderUtils.quadOutlineRounded(
                renderer,
                x - theme.scale(1),
                y - theme.scale(1),
                width + theme.scale(2),
                height + theme.scale(2),
                style.focusRing(),
                round + theme.scale(1),
                ring
            );
        }

        GuiRenderUtils.quadRounded(
            renderer,
            x + inset,
            y + inset,
            width - inset * 2,
            height - inset * 2,
            style.background(),
            Math.max(0, round - inset)
        );
        GuiRenderUtils.quadOutlineRounded(renderer, x, y, width, height, style.outline(), round, outlineSize);
    }
}
