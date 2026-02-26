package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import com.kyv3.addon.gui.motion.Kyv3MotionValue;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

public class WKyv3Button extends WButton implements Kyv3Widget {
    private final Kyv3MotionValue hoverProgress = new Kyv3MotionValue();
    private final Kyv3MotionValue pressProgress = new Kyv3MotionValue();

    public WKyv3Button(String text, GuiTexture texture) {
        super(text, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Kyv3GuiTheme theme = kyv3theme();
        double hover = spring01(hoverProgress, mouseOver, delta, 1.0, 1.0);
        double press = spring01(pressProgress, pressed, delta, 1.25, 1.05);
        double interaction = Math.max(hover, press);
        double lift = hoverLiftOffset(Math.max(hover, press * 0.75));
        double drawY = y - lift;

        Kyv3SurfaceStyle style = surfaceStyle(
            new Kyv3VisualState(false, pressed, mouseOver, false, false),
            x,
            drawY
        );
        renderRoundedBackground(renderer, x, drawY, width, height, style, false);
        renderTopGlow(renderer, x, drawY, width, Kyv3Colors.alpha(style.topGlow(), (int) (style.topGlow().a * (0.65 + hover * 0.35))));

        double bottomLine = Math.max(1, theme.scale(2));
        double activeLineWidth = width * (0.28 + 0.72 * interaction);
        renderer.quad(
            x + (width - activeLineWidth) / 2,
            drawY + height - bottomLine,
            activeLineWidth,
            bottomLine,
            Kyv3Colors.alpha(Kyv3Colors.blend(style.accent(), style.outline(), 0.25), (int) (75 + 145 * interaction))
        );

        double pad = pad();
        if (text != null) {
            renderer.text(text, x + width / 2 - textWidth / 2, drawY + pad, style.text(), false);
        } else {
            double ts = theme.textHeight();
            renderer.quad(x + width / 2 - ts / 2, drawY + pad, ts, ts, texture, style.text());
        }
    }
}
