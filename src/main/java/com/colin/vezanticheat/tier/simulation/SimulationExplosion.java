package com.colin.vezanticheat.tier.simulation;

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
 * Grim-style explosion impulse envelope sub-signal.
 *
 * <p>Evaluates ticks with {@link EngineResult#explosionTick}. Compares actual displacement
 * magnitude ratio against pending explosion vector.</p>
 */
public final class SimulationExplosion extends AbstractMovementTierCheck {

    public SimulationExplosion(VezAntiCheat plugin) {
        super(plugin, "SimulationExplosion", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!result.explosionTick) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 1);
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
        double offset = result.explosionOffset();
        double threshold = cfgDouble("threshold", 0.08D);

        if (ratio >= minRatio && offset <= threshold) {
            cool(p, 0.3D);
            return;
        }

        int gain = ratio < minRatio * 0.7D ? 2 : 1;
        flagBuffered(p, data, gain,
                "explRatio=" + r(ratio) + " off=" + r(offset) + " expected=" + r(expected) + " " + result.debug);
    }
}
