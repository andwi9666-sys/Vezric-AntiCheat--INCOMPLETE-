package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.FlyPhysicsTracker;
import org.bukkit.entity.Player;

/**
 * Bobbing fly: sustained airborne Y oscillation without monotonic fall or launch context.
 */
public final class PredictionFlyBob extends AbstractMovementTierCheck {

    public PredictionFlyBob(VezAntiCheat plugin) {
        super(plugin, "PredictionFlyBob", CheckTier.PREDICTION);
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

        FlyPhysicsTracker.BobConfig cfg = FlyPhysicsTracker.bobConfig(plugin, name());
        if (!FlyPhysicsTracker.isBobbingFlySession(data, er, nowMs, cfg)) {
            cool(p, 0.4D);
            return;
        }

        long airMs = data.getAirborneSessionStartMs() > 0L
                ? nowMs - data.getAirborneSessionStartMs() : 0L;
        double amplitude = data.getSessionMaxY() - data.getSessionMinY();
        String debug = "bob air=" + r(airMs / 1000.0D) + "s rev=" + data.getYReversalCount()
                + " amp=" + r(amplitude) + " vOff=" + r(er.verticalOffset);

        int currentBuffer = buffer(p.getUniqueId());
        int gain = currentBuffer >= 3 ? 2 : 1;
        if (flagBuffered(p, data, gain, debug)) {
            long repeatWindow = cfgLong("repeatWindowMs", 8000L);
            long lastBobHit = data.getFlyLastBobHitMs();
            if (lastBobHit > 0L && nowMs - lastBobHit <= repeatWindow) {
                requestBlatant(p, data, "bob-fly-repeat");
            }
            data.setFlyLastBobHitMs(nowMs);
            predictionSetback(p, data, "bob-fly");
        }
    }
}
