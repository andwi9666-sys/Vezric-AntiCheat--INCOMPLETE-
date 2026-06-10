package com.colin.vezanticheat.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BadPacketValidationUtilTest {

    @Test
    public void rejectsNanRotation() {
        assertTrue(BadPacketValidationUtil.isInvalidRotation(Float.NaN, 0.0F, 90.1));
    }

    @Test
    public void rejectsExcessivePitch() {
        assertTrue(BadPacketValidationUtil.isInvalidRotation(0.0F, 91.0F, 90.1));
    }

    @Test
    public void acceptsNormalRotation() {
        assertFalse(BadPacketValidationUtil.isInvalidRotation(45.0F, 10.0F, 90.1));
    }

    @Test
    public void acceptsUnwrappedYaw() {
        assertFalse(BadPacketValidationUtil.isInvalidRotation(720.0F, 45.0F, 90.1));
        assertFalse(BadPacketValidationUtil.isInvalidRotation(-540.0F, -30.0F, 90.1));
    }

    @Test
    public void rejectsInvalidHotbarSlot() {
        assertTrue(BadPacketValidationUtil.isInvalidHotbarSlot(-1));
        assertTrue(BadPacketValidationUtil.isInvalidHotbarSlot(9));
        assertFalse(BadPacketValidationUtil.isInvalidHotbarSlot(4));
    }

    @Test
    public void rejectsInvalidCursor() {
        assertTrue(BadPacketValidationUtil.isInvalidCursor(1.5F, 0.5F, 0.5F));
        assertFalse(BadPacketValidationUtil.isInvalidCursor(0.5F, 0.5F, 0.5F));
    }
}
