package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WTooltip;

public class WKyv3Tooltip extends WTooltip implements Kyv3Widget {
    public WKyv3Tooltip(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Kyv3SurfaceStyle style = surfaceStyle(
            new Kyv3VisualState(true, false, true, false, false),
            x,
            y
        );
        renderRoundedBackground(renderer, this, style, false);
        renderTopGlow(renderer, this, Kyv3Colors.alpha(style.topGlow(), (int) (style.topGlow().a * 0.8)));
    }
}
