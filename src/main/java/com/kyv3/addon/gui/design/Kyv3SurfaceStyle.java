package com.kyv3.addon.gui.design;

import meteordevelopment.meteorclient.utils.render.color.Color;

public record Kyv3SurfaceStyle(
    Color accent,
    Color outline,
    Color background,
    Color text,
    Color topGlow,
    Color activeLeft,
    Color activeRight,
    Color focusRing
) {
}
