package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.NoFallUtil;
import org.bukkit.entity.Player;

/**
 * Detects client fallDistance resets while server-tracked fall distance continues (NoFallC revival).
 */
public final class PredictionNoFallReset extends AbstractMovementTierCheck {

    public PredictionNoFallReset(VezAntiCheat plugin) {
        super(plugin, "PredictionNoFallReset", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (NoFallUtil.shouldSkip(p, data)) return;

        double minTrackedFall = cfgDouble("minTrackedFall", 3.2D);
        double hardResetReported = cfgDouble("hardResetReported", 0.45D);
        int resetCountToFlag = cfgInt("resetCountToFlag", 2);
        float clientFall = p.getFallDistance();
        double serverFall = NoFallUtil.serverFallDistance(data);
        double discrepancy = data.calculateCumulativeDiscrepancy();

        EngineResult er = engineResult(data);
        boolean midAir = er != null && !er.clientGround && !er.predictedOnGround;

        boolean resetHit = serverFall >= minTrackedFall && clientFall <= hardResetReported
                && data.getNoFallCMaxReportedFall() >= minTrackedFall;
        boolean midAirReset = midAir && clientFall <= hardResetReported
                && serverFall >= minTrackedFall * 0.75D;

        if (!resetHit && !midAirReset && data.getNoFallCResetCount() < resetCountToFlag) {
            cool(p, 0.3D);
            return;
        }

        if (data.getNoFallCResetCount() < resetCountToFlag && !resetHit) {
            cool(p, 0.3D);
            return;
        }

        int gain = discrepancy >= 1.5D ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "fall-reset count=" + data.getNoFallCResetCount()
                        + " server=" + r(serverFall) + " client=" + r(clientFall)
                        + " disc=" + r(discrepancy))) {
            data.setNoFallCResetCount(0);
            data.setNoFallCMaxReportedFall(0.0F);
        }
    }
}
