package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.design.Kyv3DesignSystem;
import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import com.kyv3.addon.gui.motion.Kyv3MotionValue;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.systems.modules.Module;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class WKyv3Module extends WPressable implements Kyv3Widget {
    private final Module module;
    private final String title;

    private double titleWidth;
    private final Kyv3MotionValue activeProgress = new Kyv3MotionValue();
    private final Kyv3MotionValue hoverProgress = new Kyv3MotionValue();

    public WKyv3Module(Module module, String title) {
        this.module = module;
        this.title = title;
        this.tooltip = module.description;

        if (module.isActive()) {
            activeProgress.set(1);
            hoverProgress.set(1);
        }
    }

    @Override
    public double pad() {
        return kyv3theme().scale(4);
    }

    @Override
    protected void onCalculateSize() {
        double pad = pad();
        if (titleWidth == 0) titleWidth = kyv3theme().textWidth(title);

        width = pad + titleWidth + pad;
        height = pad + kyv3theme().textHeight() + pad;
    }

    @Override
    protected void onPressed(int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) module.toggle();
        else if (button == GLFW_MOUSE_BUTTON_RIGHT) mc.setScreen(kyv3theme().moduleScreen(module));
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Kyv3GuiTheme theme = kyv3theme();

        double active = spring01(activeProgress, module.isActive(), delta, 0.9, 1.0);
        double hover = spring01(hoverProgress, mouseOver, delta, 0.95, 0.95);
        double lift = hoverLiftOffset(Math.max(hover, active * 0.35));
        double drawY = y - lift;

        Kyv3SurfaceStyle style = surfaceStyle(
            new Kyv3VisualState(module.isActive(), pressed, mouseOver, false, false),
            x,
            drawY
        );

        renderRoundedBackground(renderer, x, drawY, width, height, style, false);
        renderTopGlow(renderer, x, drawY, width, Kyv3Colors.alpha(style.topGlow(), (int) (style.topGlow().a * (0.4 + hover * 0.6))));

        if (active > 0) {
            renderer.quad(
                x + theme.scale(1.5),
                drawY + theme.scale(1.5),
                (width - theme.scale(3)) * active,
                height - theme.scale(3),
                Kyv3Colors.alpha(style.activeLeft(), (int) (90 + 100 * active)),
                Kyv3Colors.alpha(style.activeRight(), 35)
            );
        }

        double barWidth = Kyv3DesignSystem.indicatorWidth(theme);
        double barHeight = height * (0.22 + 0.78 * Math.max(active, hover));
        renderer.quad(x, drawY + height - barHeight, barWidth, barHeight, Kyv3Colors.alpha(style.accent(), 225));

        double pad = pad();
        double textX = x + pad;
        double textSpace = width - pad * 2;
        if (theme.moduleAlignment.get() == AlignmentX.Center) textX += textSpace / 2 - titleWidth / 2;
        else if (theme.moduleAlignment.get() == AlignmentX.Right) textX += textSpace - titleWidth;

        renderer.text(
            title,
            textX,
            drawY + pad,
            Kyv3Colors.blend(style.text(), Kyv3Colors.brighten(style.accent(), 0.45), active * 0.2 + hover * 0.2),
            false
        );
    }
}
