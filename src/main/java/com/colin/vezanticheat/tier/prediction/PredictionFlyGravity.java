package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.FlyPhysicsTracker;
import org.bukkit.entity.Player;

/**
 * Gravity curve violation: sustained mismatch between observed dy chain and vanilla gravity.
 */
public final class PredictionFlyGravity extends AbstractMovementTierCheck {

    public PredictionFlyGravity(VezAntiCheat plugin) {
        super(plugin, "PredictionFlyGravity", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (!engineActive()) return;

        EngineResult er = engineResult(data);
        if (er == null) {
            cool(p, 0.4D);
            return;
        }

        if (FlyPhysicsTracker.shouldSkipMovementFlyCheck(p, data, er, nowMs, plugin)) {
            cool(p, 0.45D);
            return;
        }

        if (FlyPhysicsTracker.isMonotonicFallSession(data, er, nowMs) || data.isFallArcActive()) {
            cool(p, 0.4D);
            return;
        }

        int minAirTicks = cfgInt("gravityMinAirTicks", 10);
        if (data.getEngineAirborneTicks() < minAirTicks) {
            cool(p, 0.35D);
            return;
        }

        int sampleSize = cfgInt("gravitySampleSize",
                (int) plugin.getConfig().getLong("movement-analysis.fly-physics.gravity-sample-size", 12L));
        double errorThreshold = cfgDouble("gravityErrorThreshold", 0.06D);
        int minConsecutive = cfgInt("gravityMinConsecutive", 8);

        int streak = FlyPhysicsTracker.gravityViolationStreak(data, sampleSize, errorThreshold);
        if (streak < minConsecutive) {
            cool(p, 0.35D);
            return;
        }

        flagBuffered(p, data, 1,
                "gravity streak=" + streak + " air=" + data.getEngineAirborneTicks()
                        + " vOff=" + r(er.verticalOffset));
    }
}
