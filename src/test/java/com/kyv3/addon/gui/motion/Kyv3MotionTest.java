package com.kyv3.addon.gui.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Kyv3MotionTest {
    @Test
    void approachConvergesTowardTarget() {
        double value = 0.0;
        for (int i = 0; i < 120; i++) {
            value = Kyv3Motion.approach(value, 1.0, 1.0 / 60.0, 1.0);
        }

        assertTrue(value > 0.98, "value should get very close to target");
    }

    @Test
    void approachWithZeroDeltaKeepsCurrentValue() {
        double value = Kyv3Motion.approach(0.35, 1.0, 0.0, 1.0);
        assertEquals(0.35, value, 1e-9);
    }

    @Test
    void toggleIsClampedToZeroOne() {
        double value = Kyv3Motion.toggle01(0.95, true, 1.0, 5.0);
        assertTrue(value <= 1.0 && value >= 0.0);
    }

    @Test
    void springWithZeroDeltaKeepsState() {
        Kyv3Motion.SpringState state = Kyv3Motion.spring(0.2, 0.5, 1.0, 0.0, 20.0, 8.0);
        assertEquals(0.2, state.value(), 1e-9);
        assertEquals(0.5, state.velocity(), 1e-9);
    }
}
