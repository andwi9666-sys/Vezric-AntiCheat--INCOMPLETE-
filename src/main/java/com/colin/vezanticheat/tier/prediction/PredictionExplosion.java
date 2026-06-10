package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.engine.MovementPlayer;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Explosion impulse cheat: movement inconsistent with pending explosion velocity.
 *
 * <p>Evaluates ticks where {@link EngineResult#explosionTick} is true. Compares actual displacement
 * ratio against pending explosion vector and total offset.</p>
 */
public final class PredictionExplosion extends AbstractMovementTierCheck {

    public PredictionExplosion(VezAntiCheat plugin) {
        super(plugin, "PredictionExplosion", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (!engineActive()) return;

        EngineResult result = engineResult(data);
        if (result == null || !result.explosionTick) {
            cool(p, 0.35D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.35D);
            return;
        }

        MovementPlayer mp = data.getMovementPlayer();
        Vector pending = mp == null ? null : mp.pendingExplosion;
        if (pending == null) pending = data.getLastExplosionVelocity();
        if (pending == null) return;

        double expected = pending.length();
        double actual = result.actual == null ? 0.0D : result.actual.length();
        double ratio = expected <= 1.0E-4D ? 0.0D : actual / expected;
        double minRatio = cfgDouble("minExplosionRatio", 0.50D);
        double offsetThr = cfgDouble("engineOffsetThreshold", 0.10D);

        if (ratio >= minRatio && result.offset <= offsetThr) {
            cool(p, 0.35D);
            return;
        }

        int gain = ratio < minRatio * 0.7D ? 2 : 1;
        flagBuffered(p, data, gain,
                "explRatio=" + r(ratio) + " off=" + r(result.offset) + " expected=" + r(expected)
                        + " " + result.debug);
    }
}
