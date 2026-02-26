package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import com.kyv3.addon.gui.motion.Kyv3MotionValue;
import com.kyv3.addon.gui.render.GuiRenderUtils;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.screen.Screen;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPos;

public class WKyv3TopBar extends WTopBar implements Kyv3Widget {
    @Override
    public void init() {
        for (Tab tab : Tabs.get()) add(new WKyv3TopBarButton(tab));
    }

    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        Kyv3SurfaceStyle style = surfaceStyle(
            new Kyv3VisualState(false, pressed, hovered, false, false),
            x,
            y
        );
        return style.background();
    }

    @Override
    protected Color getNameColor() {
        Kyv3SurfaceStyle style = surfaceStyle(Kyv3VisualState.idle(), x, y);
        return style.text();
    }

    private class WKyv3TopBarButton extends WPressable implements Kyv3Widget {
        private final Tab tab;
        private final Kyv3MotionValue activeProgress = new Kyv3MotionValue();
        private final Kyv3MotionValue hoverProgress = new Kyv3MotionValue();

        private WKyv3TopBarButton(Tab tab) {
            this.tab = tab;
        }

        @Override
        protected void onCalculateSize() {
            double pad = pad();
            width = pad + theme.textWidth(tab.name) + pad;
            height = pad + theme.textHeight() + pad;
        }

        @Override
        protected void onPressed(int button) {
            Screen screen = mc.currentScreen;
            if (!(screen instanceof TabScreen tabScreen) || tabScreen.tab != tab) {
                double mouseX = mc.mouse.getX();
                double mouseY = mc.mouse.getY();
                tab.openScreen(theme);
                glfwSetCursorPos(mc.getWindow().getHandle(), mouseX, mouseY);
            }
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            Kyv3GuiTheme theme = kyv3theme();
            boolean selected = mc.currentScreen instanceof TabScreen tabScreen && tabScreen.tab == tab;

            double active = spring01(activeProgress, selected, delta, 0.9, 1.0);
            double hover = spring01(hoverProgress, mouseOver, delta, 1.0, 0.95);
            double lift = hoverLiftOffset(Math.max(hover, active * 0.35));
            double drawY = y - lift;

            Kyv3SurfaceStyle style = surfaceStyle(
                new Kyv3VisualState(selected, selected || pressed, mouseOver, false, false),
                x + tab.name.length() * 10.0,
                drawY
            );

            int side = state(this);
            if (side == 1) GuiRenderUtils.quadRoundedSide(renderer, x, drawY, width, height, style.background(), theme.roundAmount(), false);
            else if (side == 2) GuiRenderUtils.quadRoundedSide(renderer, x, drawY, width, height, style.background(), theme.roundAmount(), true);
            else if (side == 3) GuiRenderUtils.quadRounded(renderer, x, drawY, width, height, style.background(), theme.roundAmount());
            else renderer.quad(x, drawY, width, height, style.background());

            renderer.quad(
                x + theme.scale(1.5),
                drawY + theme.scale(1.5),
                width - theme.scale(3),
                Math.max(1, theme.scale(2)),
                Kyv3Colors.alpha(style.topGlow(), (int) (60 + hover * 90))
            );

            if (selected || active > 0) {
                double line = Math.max(1, theme.scale(2));
                renderer.quad(
                    x + theme.scale(2),
                    drawY + height - line,
                    width - theme.scale(4),
                    line,
                    Kyv3Colors.alpha(style.focusRing(), (int) (120 + active * 120))
                );
            }

            double pad = pad();
            renderer.text(
                tab.name,
                x + pad,
                drawY + pad,
                Kyv3Colors.blend(style.text(), Kyv3Colors.brighten(style.accent(), 0.45), active * 0.35 + hover * 0.2),
                false
            );
        }
    }

    private int state(WKyv3TopBarButton button) {
        if (cells.isEmpty()) return 0;

        int result = 0;
        if (button.equals(cells.get(0).widget())) result |= 1;
        if (button.equals(cells.get(cells.size() - 1).widget())) result |= 2;
        return result;
    }
}
