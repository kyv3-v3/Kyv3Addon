package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import com.kyv3.addon.gui.motion.Kyv3MotionValue;
import com.kyv3.addon.gui.render.GuiRenderUtils;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WKyv3Window extends WWindow implements Kyv3Widget {
    private final Kyv3MotionValue expandProgress = new Kyv3MotionValue();

    public WKyv3Window(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new WKyv3Header(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0) {
            Kyv3GuiTheme theme = kyv3theme();
            double expand = spring01(expandProgress, expanded, delta, 0.7, 1.0);

            Kyv3SurfaceStyle style = surfaceStyle(
                new Kyv3VisualState(expanded, false, mouseOver, false, false),
                x,
                y
            );

            double bodyY = y + header.height;
            double bodyHeight = height - header.height;

            Color top = Kyv3Colors.blend(style.background(), style.accent(), 0.14 + expand * 0.2);
            Color bottom = Kyv3Colors.blend(style.background(), style.accent(), 0.03 + expand * 0.08);
            GuiRenderUtils.quadRounded(renderer, x, bodyY - theme.scale(6), width, bodyHeight + theme.scale(6), top, theme.roundAmount(), false);
            renderer.quad(x, bodyY, width, bodyHeight, top, top, bottom, bottom);

            double s = Math.max(1, theme.scale(1.5));
            GuiRenderUtils.quadOutlineRounded(renderer, x, y, width, height, Kyv3Colors.alpha(style.outline(), 210), theme.roundAmount(), s);

            renderer.quad(
                x + s,
                bodyY,
                width - s * 2,
                Math.max(1, theme.scale(2)),
                Kyv3Colors.alpha(style.topGlow(), (int) (70 + expand * 120))
            );
        }
    }

    private class WKyv3Header extends WHeader {
        public WKyv3Header(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            Kyv3GuiTheme theme = kyv3theme();

            Kyv3SurfaceStyle style = surfaceStyle(
                new Kyv3VisualState(expanded, false, mouseOver, false, false),
                this.x,
                this.y
            );

            Color left = Kyv3Colors.alpha(Kyv3Colors.blend(style.accent(), style.activeLeft(), 0.35), 220);
            Color right = Kyv3Colors.alpha(Kyv3Colors.blend(style.accent(), style.activeRight(), 0.25), 220);
            GuiRenderUtils.quadRounded(renderer, this.x, this.y, width, height + theme.scale(8), left, theme.roundAmount(), true);
            renderer.quad(this.x, this.y, width, height, left, right, right, left);

            double underline = Math.max(1, theme.scale(2));
            renderer.quad(this.x, this.y + height - underline, width, underline, Kyv3Colors.alpha(style.focusRing(), 235));
        }
    }
}
