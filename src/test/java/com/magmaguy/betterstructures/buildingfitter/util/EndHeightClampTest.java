package com.magmaguy.betterstructures.buildingfitter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EndHeightClampTest {
    @Test
    void clampsToHighestEndBoundWhenExceedingCeiling() {
        int clamped = EndHeightClamp.clamp(400, 320, 0, 60, 12);
        assertTrue(clamped <= 320);
    }
}
