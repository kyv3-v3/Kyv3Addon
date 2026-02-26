package com.kyv3.addon.gui.widgets;

import com.kyv3.addon.gui.Kyv3GuiTheme;
import com.kyv3.addon.gui.design.Kyv3SurfaceStyle;
import com.kyv3.addon.gui.design.Kyv3VisualState;
import com.kyv3.addon.gui.motion.Kyv3MotionValue;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorLabel;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WKyv3TextBox extends WTextBox implements Kyv3Widget {
    private boolean cursorVisible;
    private double cursorTimer;
    private final Kyv3MotionValue cursorAnimProgress = new Kyv3MotionValue();
    private final Kyv3MotionValue liftProgress = new Kyv3MotionValue();

    public WKyv3TextBox(String text, String placeholder, CharFilter filter, Class<? extends Renderer> renderer) {
        super(text, placeholder, filter, renderer);
    }

    @Override
    protected WContainer createCompletionsRootWidget() {
        return new WVerticalList() {
            @Override
            protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                Kyv3SurfaceStyle style = WKyv3TextBox.this.surfaceStyle(
                    new Kyv3VisualState(false, false, mouseOver, false, false),
                    x,
                    y
                );
                WKyv3TextBox.this.renderRoundedBackground(renderer, this, style, false);
                WKyv3TextBox.this.renderTopGlow(renderer, this, Kyv3Colors.alpha(style.topGlow(), (int) (style.topGlow().a * 0.7)));
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T extends WWidget & ICompletionItem> T createCompletionsValueWidth(String completion, boolean selected) {
        return (T) new CompletionItem(completion, selected);
    }

    @Override
    protected void onCursorChanged() {
        cursorVisible = true;
        cursorTimer = 0;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Kyv3GuiTheme theme = kyv3theme();

        if (cursorTimer >= 1) {
            cursorVisible = !cursorVisible;
            cursorTimer = 0;
        } else {
            cursorTimer += delta * 1.75;
        }

        double interaction = spring01(liftProgress, mouseOver || focused, delta, 0.95, 0.95);
        double drawY = y - hoverLiftOffset(interaction * 0.8);

        Kyv3SurfaceStyle style = surfaceStyle(
            new Kyv3VisualState(false, false, mouseOver, focused, false),
            x,
            drawY
        );
        renderRoundedBackground(renderer, x, drawY, width, height, style, focused);
        renderTopGlow(renderer, x, drawY, width, Kyv3Colors.alpha(style.topGlow(), (int) (style.topGlow().a * (focused ? 1.0 : 0.65))));

        double pad = pad();
        double overflowWidth = getOverflowWidthForRender();
        renderer.scissorStart(x + pad, drawY + pad, width - pad * 2, height - pad * 2);

        if (!text.isEmpty()) {
            this.renderer.render(renderer, x + pad - overflowWidth, drawY + pad, text, style.text());
        } else if (placeholder != null) {
            this.renderer.render(renderer, x + pad - overflowWidth, drawY + pad, placeholder, theme.placeholderColor.get());
        }

        if (focused && (cursor != selectionStart || cursor != selectionEnd)) {
            double selStart = x + pad + getTextWidth(selectionStart) - overflowWidth;
            double selEnd = x + pad + getTextWidth(selectionEnd) - overflowWidth;
            renderer.quad(selStart, drawY + pad, selEnd - selStart, theme.textHeight(), theme.textHighlightColor.get());
        }

        double cursorAlpha = spring01(cursorAnimProgress, focused && cursorVisible, delta, 1.35, 1.1);
        if ((focused && cursorVisible) || cursorAlpha > 0) {
            renderer.setAlpha(cursorAlpha);
            renderer.quad(
                x + pad + getTextWidth(cursor) - overflowWidth,
                drawY + pad,
                theme.scale(1),
                theme.textHeight(),
                style.text()
            );
            renderer.setAlpha(1);
        }

        renderer.scissorEnd();
    }

    private static class CompletionItem extends WMeteorLabel implements ICompletionItem {
        private static final Color SELECTED_COLOR = new Color(255, 255, 255, 18);

        private boolean selected;

        public CompletionItem(String text, boolean selected) {
            super(text, false);
            this.selected = selected;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            super.onRender(renderer, mouseX, mouseY, delta);
            if (selected) renderer.quad(this, SELECTED_COLOR);
        }

        @Override
        public boolean isSelected() {
            return selected;
        }

        @Override
        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public String getCompletion() {
            return text;
        }
    }
}
