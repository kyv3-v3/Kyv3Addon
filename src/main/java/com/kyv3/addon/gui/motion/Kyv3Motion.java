package com.kyv3.addon.gui.motion;

public final class Kyv3Motion {
    private Kyv3Motion() {
    }

    public static double approach(double current, double target, double delta, double speed) {
        if (delta <= 0) return current;
        if (speed <= 0) return target;

        double alpha = 1 - Math.exp(-delta * speed * 10.0);
        return current + (target - current) * clamp01(alpha);
    }

    public static double toggle01(double current, boolean enabled, double delta, double speed) {
        return clamp01(approach(current, enabled ? 1 : 0, delta, speed));
    }

    public static SpringState spring(double value, double velocity, double target, double delta, double stiffness, double damping) {
        if (delta <= 0) return new SpringState(value, velocity);

        double displacement = target - value;
        double springForce = displacement * Math.max(0, stiffness);
        double dampingForce = velocity * Math.max(0, damping);

        double acceleration = springForce - dampingForce;
        double nextVelocity = velocity + acceleration * delta;
        double nextValue = value + nextVelocity * delta;

        return new SpringState(nextValue, nextVelocity);
    }

    public static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record SpringState(double value, double velocity) {
    }
}
