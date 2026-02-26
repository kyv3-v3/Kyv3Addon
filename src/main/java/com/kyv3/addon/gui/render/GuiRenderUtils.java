package com.kyv3.addon.gui.render;

import com.kyv3.addon.mixin.GuiRendererAccessor;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class GuiRenderUtils {
    private GuiRenderUtils() {
    }

    public static void quadRounded(GuiRenderer renderer, double x, double y, double width, double height, Color color, double round, boolean roundTop) {
        RoundedRenderer2D.quadRounded(raw(renderer), x, y, width, height, color, round, roundTop);
    }

    public static void quadRounded(GuiRenderer renderer, double x, double y, double width, double height, Color color, double round) {
        quadRounded(renderer, x, y, width, height, color, round, true);
    }

    public static void quadRounded(GuiRenderer renderer, WWidget widget, Color color, double round) {
        quadRounded(renderer, widget.x, widget.y, widget.width, widget.height, color, round, true);
    }

    public static void quadOutlineRounded(GuiRenderer renderer, double x, double y, double width, double height, Color color, double round, double lineSize) {
        RoundedRenderer2D.quadRoundedOutline(raw(renderer), x, y, width, height, color, round, lineSize);
    }

    public static void quadOutlineRounded(GuiRenderer renderer, WWidget widget, Color color, double round, double lineSize) {
        quadOutlineRounded(renderer, widget.x, widget.y, widget.width, widget.height, color, round, lineSize);
    }

    public static void quadRoundedSide(GuiRenderer renderer, double x, double y, double width, double height, Color color, double round, boolean rightSide) {
        RoundedRenderer2D.quadRoundedSide(raw(renderer), x, y, width, height, color, round, rightSide);
    }

    public static void quadRoundedSide(GuiRenderer renderer, WWidget widget, Color color, double round, boolean rightSide) {
        quadRoundedSide(renderer, widget.x, widget.y, widget.width, widget.height, color, round, rightSide);
    }

    private static Renderer2D raw(GuiRenderer renderer) {
        return ((GuiRendererAccessor) renderer).kyv3$getRenderer2D();
    }
}
