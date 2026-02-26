package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import com.kyv3.addon.gui.motion.Kyv3MotionValue;
import com.kyv3.addon.gui.render.GuiRenderUtils;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;

public class WKyv3Checkbox extends WCheckbox implements Kyv3Widget {
    private final Kyv3MotionValue checkProgress = new Kyv3MotionValue();
    private final Kyv3MotionValue hoverProgress = new Kyv3MotionValue();

    public WKyv3Checkbox(boolean checked) {
        super(checked);
        checkProgress.set(checked ? 1 : 0);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Kyv3GuiTheme theme = kyv3theme();
        double checkedProgress = spring01(checkProgress, checked, delta, 1.2, 1.0);
        double hover = spring01(hoverProgress, mouseOver, delta, 0.95, 0.95);
        double lift = hoverLiftOffset(Math.max(hover, checkedProgress * 0.3));
        double drawY = y - lift;

        Kyv3SurfaceStyle style = surfaceStyle(
            new Kyv3VisualState(checked, pressed, mouseOver, false, false),
            x,
            drawY
        );
        renderRoundedBackground(renderer, x, drawY, width, height, style, false);
        renderTopGlow(renderer, x, drawY, width, Kyv3Colors.alpha(style.topGlow(), (int) (style.topGlow().a * (0.45 + checkedProgress * 0.45 + hover * 0.1))));

        if (checkedProgress > 0) {
            double size = (width - theme.scale(4)) * checkedProgress;
            double px = x + (width - size) / 2;
            double py = drawY + (height - size) / 2;
            GuiRenderUtils.quadRounded(
                renderer,
                px,
                py,
                size,
                size,
                Kyv3Colors.blend(style.activeLeft(), style.accent(), 0.35),
                Math.max(1, theme.roundAmount() - 2)
            );
        }
    }
}
