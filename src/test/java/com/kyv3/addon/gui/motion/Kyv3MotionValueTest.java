package com.kyv3.addon.gui.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Kyv3MotionValueTest {
    @Test
    void spring01ClampsUpperBound() {
        Kyv3MotionValue value = new Kyv3MotionValue(0.9);
        double next = value.spring01(2.0, 1.0, 100.0, 0.0);
        assertEquals(1.0, next, 1e-9);
        assertEquals(0.0, value.velocity(), 1e-9);
    }

    @Test
    void spring01ClampsLowerBound() {
        Kyv3MotionValue value = new Kyv3MotionValue(0.1);
        double next = value.spring01(-2.0, 1.0, 100.0, 0.0);
        assertEquals(0.0, next, 1e-9);
        assertEquals(0.0, value.velocity(), 1e-9);
    }

    @Test
    void setResetsVelocity() {
        Kyv3MotionValue value = new Kyv3MotionValue(0.0);
        value.spring(1.0, 0.2, 15.0, 1.0);
        value.set(0.4);
        assertEquals(0.4, value.value(), 1e-9);
        assertEquals(0.0, value.velocity(), 1e-9);
    }
}
