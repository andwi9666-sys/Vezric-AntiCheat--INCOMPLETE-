package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.FlyPhysicsTracker;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Sudden vertical rise without velocity, explosion, jump, or piston context.
 */
public final class PredictionFlyBurst extends AbstractMovementTierCheck {

    public PredictionFlyBurst(VezAntiCheat plugin) {
        super(plugin, "PredictionFlyBurst", CheckTier.PREDICTION);
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

        long velocityWindow = cfgLong("burstVelocityWindowMs",
                plugin.getConfig().getLong("movement-analysis.fly-physics.burst-velocity-window-ms", 700L));
        if (FlyPhysicsTracker.isBurstExempt(plugin, data, er, nowMs, velocityWindow)) {
            cool(p, 0.4D);
            return;
        }

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double lastDy = data.getFlySessionLastDy();
        double twoTickRise = dy + Math.max(0.0D, lastDy);

        double burstDy = cfgDouble("burstDy", 0.38D);
        double twoTickBurst = cfgDouble("twoTickBurstDy", 0.55D);
        double blatantBurstDy = cfgDouble("blatantBurstDy", 0.55D);

        boolean singleBurst = dy >= burstDy;
        boolean twoTickBurstHit = twoTickRise >= twoTickBurst && data.getEngineAirborneTicks() >= 2;
        if (!singleBurst && !twoTickBurstHit) {
            cool(p, 0.35D);
            return;
        }

        int gain = singleBurst && dy >= blatantBurstDy ? 2 : 1;
        String debug = "burst dy=" + r(dy) + " two=" + r(twoTickRise) + " air=" + data.getEngineAirborneTicks();
        if (flagBuffered(p, data, gain, debug)) {
            if (singleBurst && dy >= blatantBurstDy) {
                requestBlatant(p, data, "y-burst");
            }
            predictionSetback(p, data, "y-burst");
        }
    }
}
