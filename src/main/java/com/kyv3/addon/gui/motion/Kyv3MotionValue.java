package com.kyv3.addon.gui.motion;

public final class Kyv3MotionValue {
    private double value;
    private double velocity;

    public Kyv3MotionValue() {
        this(0);
    }

    public Kyv3MotionValue(double value) {
        this.value = value;
    }

    public double value() {
        return value;
    }

    public double velocity() {
        return velocity;
    }

    public void set(double value) {
        this.value = value;
        this.velocity = 0;
    }

    public double approach(double target, double delta, double speed) {
        value = Kyv3Motion.approach(value, target, delta, speed);
        return value;
    }

    public double toggle01(boolean enabled, double delta, double speed) {
        value = Kyv3Motion.toggle01(value, enabled, delta, speed);
        return value;
    }

    public double spring(double target, double delta, double stiffness, double damping) {
        Kyv3Motion.SpringState state = Kyv3Motion.spring(value, velocity, target, delta, stiffness, damping);
        value = state.value();
        velocity = state.velocity();
        return value;
    }

    public double spring01(double target, double delta, double stiffness, double damping) {
        spring(target, delta, stiffness, damping);
        if (value < 0) {
            value = 0;
            velocity = 0;
        } else if (value > 1) {
            value = 1;
            velocity = 0;
        }

        return value;
    }
}
