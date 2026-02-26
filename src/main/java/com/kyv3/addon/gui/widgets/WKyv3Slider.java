package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import com.kyv3.addon.gui.motion.Kyv3MotionValue;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WKyv3Slider extends WSlider implements Kyv3Widget {
    private final Kyv3MotionValue handlePulse = new Kyv3MotionValue();

    public WKyv3Slider(double value, double min, double max) {
        super(value, min, max);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Kyv3GuiTheme theme = kyv3theme();
        double pulse = spring01(handlePulse, dragging || handleMouseOver, delta, 1.1, 1.0);
        double lift = hoverLiftOffset(pulse);
        double drawY = y - lift;

        Kyv3SurfaceStyle style = surfaceStyle(
            new Kyv3VisualState(false, dragging, mouseOver || handleMouseOver, false, false),
            x,
            drawY
        );
        renderRoundedBackground(renderer, x, drawY, width, height, style, false);
        renderTopGlow(renderer, x, drawY, width, Kyv3Colors.alpha(style.topGlow(), (int) (style.topGlow().a * (0.55 + pulse * 0.45))));

        double valueWidth = valueWidth();
        double handleSize = handleSize();
        double barHeight = Math.max(2, theme.scale(3));
        double barX = x + handleSize / 2;
        double barY = drawY + height / 2 - barHeight / 2;

        Color left = Kyv3Colors.blend(theme.sliderLeft.get(), style.accent(), theme.accentStrength.get() + pulse * 0.12);
        Color right = Kyv3Colors.blend(theme.sliderRight.get(), style.background(), 0.18 + pulse * 0.1);
        renderer.quad(barX, barY, valueWidth, barHeight, left);
        renderer.quad(barX + valueWidth, barY, width - valueWidth - handleSize, barHeight, right);

        double handleExpand = theme.scale(1.4) * pulse;
        double hx = x + valueWidth - handleExpand / 2;
        double hy = drawY - handleExpand / 2;
        double hs = handleSize + handleExpand;
        Color handle = Kyv3Colors.blend(theme.sliderHandle.get(dragging, handleMouseOver), style.accent(), theme.accentStrength.get() * 0.9 + pulse * 0.16);
        renderer.quad(hx, hy, hs, hs, GuiRenderer.CIRCLE, handle);
    }
}
