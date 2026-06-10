package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.block.BlockFace;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScaffoldBridgeExemptTest {

    @Test
    public void bridgeGraceExemptsEdgeBridgeLikePlacement() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long nowMs = 10_000L;
        data.setLastBlockPlace(nowMs - 200L);

        ScaffoldUtil.Context ctx = bridgeContext(true, false, false, false);
        assertTrue(ScaffoldUtil.shouldExemptBridgingFlag(900L, data, ctx, nowMs));
    }

    @Test
    public void bridgeGraceExpiresAfterWindow() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long nowMs = 10_000L;
        data.setLastBlockPlace(nowMs - 1200L);

        ScaffoldUtil.Context ctx = bridgeContext(true, false, false, false);
        assertFalse(ScaffoldUtil.shouldExemptBridgingFlag(900L, data, ctx, nowMs));
    }

    @Test
    public void extensionBridgeLikeWithinGraceIsExempt() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long nowMs = 5_000L;
        data.setLastBlockPlace(nowMs - 100L);

        ScaffoldUtil.Context ctx = bridgeContext(false, false, true, true);
        assertTrue(ScaffoldUtil.shouldExemptBridgingFlag(900L, data, ctx, nowMs));
    }

    private static ScaffoldUtil.Context bridgeContext(boolean edgeBridgeLike, boolean speedBridgeLike,
                                                      boolean extensionBridgeLike, boolean bridgeGeometryLenient) {
        return new ScaffoldUtil.Context(
                BlockFace.NORTH,
                2.0D, 2.0D, 2.0D, 2.0D,
                0.45D, 0.55D,
                0.72D, 0.18D, -0.62D, -0.5D,
                true, true,
                true, false, true, false,
                speedBridgeLike, edgeBridgeLike, extensionBridgeLike,
                true, true, bridgeGeometryLenient,
                false, false, true,
                false, true, 0.85D, "bridge-test");
    }
}
