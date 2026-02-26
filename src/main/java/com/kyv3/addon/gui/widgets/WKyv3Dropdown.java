package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import com.kyv3.addon.gui.motion.Kyv3MotionValue;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;

public class WKyv3Dropdown<T> extends WDropdown<T> implements Kyv3Widget {
    private final Kyv3MotionValue hoverProgress = new Kyv3MotionValue();
    private final Kyv3MotionValue pressProgress = new Kyv3MotionValue();

    public WKyv3Dropdown(T[] values, T value) {
        super(values, value);
    }

    @Override
    protected WDropdownRoot createRootWidget() {
        return new WRoot();
    }

    @Override
    protected WDropdownValue createValueWidget() {
        return new WValue();
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Kyv3GuiTheme theme = kyv3theme();
        double pad = pad();
        double triangleSize = theme.textHeight();
        double hover = spring01(hoverProgress, mouseOver, delta, 0.95, 0.95);
        double press = spring01(pressProgress, pressed, delta, 1.2, 1.0);
        double lift = hoverLiftOffset(Math.max(hover, press * 0.75));
        double drawY = y - lift;

        Kyv3SurfaceStyle style = surfaceStyle(
            new Kyv3VisualState(false, pressed, mouseOver, false, false),
            x,
            drawY
        );
        renderRoundedBackground(renderer, x, drawY, width, height, style, false);
        renderTopGlow(renderer, x, drawY, width, Kyv3Colors.alpha(style.topGlow(), (int) (style.topGlow().a * 0.85)));

        String text = get().toString();
        double textWidth = theme.textWidth(text);
        renderer.text(text, x + pad + maxValueWidth / 2 - textWidth / 2, drawY + pad, style.text(), false);
        renderer.rotatedQuad(
            x + pad + maxValueWidth + pad,
            drawY + pad,
            triangleSize,
            triangleSize,
            0,
            GuiRenderer.TRIANGLE,
            Kyv3Colors.blend(style.text(), style.accent(), 0.25)
        );
    }

    private static class WRoot extends WDropdownRoot implements Kyv3Widget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            Kyv3SurfaceStyle style = surfaceStyle(
                new Kyv3VisualState(false, false, mouseOver, false, false),
                x,
                y
            );
            renderRoundedBackground(renderer, this, style, false);
            renderTopGlow(renderer, this, Kyv3Colors.alpha(style.topGlow(), (int) (style.topGlow().a * 0.7)));
        }
    }

    private class WValue extends WDropdownValue implements Kyv3Widget {
        @Override
        protected void onCalculateSize() {
            double pad = pad();
            width = pad + theme.textWidth(value.toString()) + pad;
            height = pad + theme.textHeight() + pad;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            Kyv3SurfaceStyle style = surfaceStyle(
                new Kyv3VisualState(false, pressed, mouseOver, false, false),
                x,
                y
            );
            renderRoundedBackground(renderer, this, style, false);

            String label = value.toString();
            renderer.text(label, x + width / 2 - theme.textWidth(label) / 2, y + pad(), style.text(), false);
        }
    }
}
