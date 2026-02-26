package com.kyv3.addon.gui.render;

import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class RoundedRenderer2D {
    private static final double CIRCLE_NONE = 0;
    private static final double CIRCLE_QUARTER = Math.PI / 2;
    private static final double CIRCLE_HALF = CIRCLE_QUARTER * 2;
    private static final double CIRCLE_THREE_QUARTER = CIRCLE_QUARTER * 3;

    private RoundedRenderer2D() {
    }

    public static void quadRoundedOutline(Renderer2D renderer, double x, double y, double width, double height, Color color, double radius, double lineSize) {
        if (width <= 0 || height <= 0 || lineSize <= 0) return;
        radius = fitRadius(radius, width, height);

        if (radius <= 0) {
            quad(renderer, x, y, width, lineSize, color);
            quad(renderer, x, y + height - lineSize, width, lineSize, color);
            quad(renderer, x, y + lineSize, lineSize, height - lineSize * 2, color);
            quad(renderer, x + width - lineSize, y + lineSize, lineSize, height - lineSize * 2, color);
            return;
        }

        circlePartOutline(renderer, x + radius, y + radius, radius, CIRCLE_THREE_QUARTER, CIRCLE_QUARTER, color, lineSize);
        quad(renderer, x + radius, y, width - radius * 2, lineSize, color);
        circlePartOutline(renderer, x + width - radius, y + radius, radius, CIRCLE_NONE, CIRCLE_QUARTER, color, lineSize);

        quad(renderer, x, y + radius, lineSize, height - radius * 2, color);
        quad(renderer, x + width - lineSize, y + radius, lineSize, height - radius * 2, color);

        circlePartOutline(renderer, x + width - radius, y + height - radius, radius, CIRCLE_QUARTER, CIRCLE_QUARTER, color, lineSize);
        quad(renderer, x + radius, y + height - lineSize, width - radius * 2, lineSize, color);
        circlePartOutline(renderer, x + radius, y + height - radius, radius, CIRCLE_HALF, CIRCLE_QUARTER, color, lineSize);
    }

    public static void quadRounded(Renderer2D renderer, double x, double y, double width, double height, Color color, double radius, boolean roundTop) {
        if (width <= 0 || height <= 0) return;
        radius = fitRadius(radius, width, height);

        if (radius <= 0) {
            quad(renderer, x, y, width, height, color);
            return;
        }

        if (roundTop) {
            circlePart(renderer, x + radius, y + radius, radius, CIRCLE_THREE_QUARTER, CIRCLE_QUARTER, color);
            quad(renderer, x + radius, y, width - 2 * radius, radius, color);
            circlePart(renderer, x + width - radius, y + radius, radius, CIRCLE_NONE, CIRCLE_QUARTER, color);
            quad(renderer, x, y + radius, width, height - 2 * radius, color);
        } else {
            quad(renderer, x, y, width, height - radius, color);
        }

        circlePart(renderer, x + width - radius, y + height - radius, radius, CIRCLE_QUARTER, CIRCLE_QUARTER, color);
        quad(renderer, x + radius, y + height - radius, width - 2 * radius, radius, color);
        circlePart(renderer, x + radius, y + height - radius, radius, CIRCLE_HALF, CIRCLE_QUARTER, color);
    }

    public static void quadRoundedSide(Renderer2D renderer, double x, double y, double width, double height, Color color, double radius, boolean rightSide) {
        if (width <= 0 || height <= 0) return;
        radius = fitRadius(radius, width, height);

        if (radius <= 0) {
            quad(renderer, x, y, width, height, color);
            return;
        }

        if (rightSide) {
            circlePart(renderer, x + width - radius, y + radius, radius, CIRCLE_NONE, CIRCLE_QUARTER, color);
            circlePart(renderer, x + width - radius, y + height - radius, radius, CIRCLE_QUARTER, CIRCLE_QUARTER, color);
            quad(renderer, x, y, width - radius, height, color);
            quad(renderer, x + width - radius, y + radius, radius, height - radius * 2, color);
        } else {
            circlePart(renderer, x + radius, y + radius, radius, CIRCLE_THREE_QUARTER, CIRCLE_QUARTER, color);
            circlePart(renderer, x + radius, y + height - radius, radius, CIRCLE_HALF, CIRCLE_QUARTER, color);
            quad(renderer, x + radius, y, width - radius, height, color);
            quad(renderer, x, y + radius, radius, height - radius * 2, color);
        }
    }

    public static void circlePart(Renderer2D renderer, double x, double y, double radius, double startAngle, double angle, Color color) {
        if (radius <= 0 || angle <= 0) return;
        int depth = getCircleDepth(radius, angle);
        renderer.triangles.ensureCapacity(depth + 2, depth * 6);
        double step = angle / depth;

        int center = renderer.triangles.vec2(x, y).color(color).next();
        int previous = vecOnCircle(renderer, x, y, radius, startAngle, color);

        for (int i = 1; i < depth + 1; i++) {
            int next = vecOnCircle(renderer, x, y, radius, startAngle + step * i, color);
            renderer.triangles.quad(previous, center, next, next);
            previous = next;
        }
    }

    public static void circlePartOutline(Renderer2D renderer, double x, double y, double radius, double startAngle, double angle, Color color, double outlineWidth) {
        if (radius <= 0 || angle <= 0 || outlineWidth <= 0) return;
        if (outlineWidth >= radius) {
            circlePart(renderer, x, y, radius, startAngle, angle, color);
            return;
        }

        int depth = getCircleDepth(radius, angle);
        renderer.triangles.ensureCapacity((depth + 1) * 2, depth * 6);
        double step = angle / depth;

        int innerPrevious = vecOnCircle(renderer, x, y, radius - outlineWidth, startAngle, color);
        int outerPrevious = vecOnCircle(renderer, x, y, radius, startAngle, color);

        for (int i = 1; i < depth + 1; i++) {
            int inner = vecOnCircle(renderer, x, y, radius - outlineWidth, startAngle + step * i, color);
            int outer = vecOnCircle(renderer, x, y, radius, startAngle + step * i, color);
            renderer.triangles.quad(inner, innerPrevious, outerPrevious, outer);
            innerPrevious = inner;
            outerPrevious = outer;
        }
    }

    private static double fitRadius(double radius, double width, double height) {
        if (radius * 2 > height) radius = height / 2;
        if (radius * 2 > width) radius = width / 2;
        return radius;
    }

    private static int getCircleDepth(double radius, double angle) {
        return Math.max(1, (int) (angle * radius / CIRCLE_QUARTER));
    }

    private static int vecOnCircle(Renderer2D renderer, double x, double y, double radius, double angle, Color color) {
        return renderer.triangles.vec2(x + Math.sin(angle) * radius, y - Math.cos(angle) * radius).color(color).next();
    }

    private static void quad(Renderer2D renderer, double x, double y, double width, double height, Color color) {
        if (width <= 0 || height <= 0) return;
        renderer.triangles.ensureQuadCapacity();
        renderer.quad(x, y, width, height, color);
    }
}
