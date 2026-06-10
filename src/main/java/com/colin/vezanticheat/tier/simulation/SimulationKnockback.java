package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.engine.MovementPlayer;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.VelocityEnforcement;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Grim-style knockback envelope sub-signal. Validates post-KB movement against pending impulse.
 *
 * <p>Only evaluates ticks with {@link EngineResult#knockbackTick}. Compares actual horizontal
 * displacement ratio to pending knockback vector and engine offset.</p>
 */
public final class SimulationKnockback extends AbstractMovementTierCheck {

    public SimulationKnockback(VezAntiCheat plugin) {
        super(plugin, "SimulationKnockback", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!result.knockbackTick) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 1);
            return;
        }

        MovementPlayer mp = data.getMovementPlayer();
        Vector pending = mp == null ? null : mp.pendingKnockback;
        if (pending == null) pending = data.getLastVelocity();
        if (pending == null) return;

        double expectedH = Math.hypot(pending.getX(), pending.getZ());
        double actualH = result.actual == null ? 0.0D
                : Math.hypot(result.actual.getX(), result.actual.getZ());
        double ratio = expectedH <= 1.0E-4D ? 0.0D : actualH / expectedH;
        double minRatio = cfgDouble("minKnockbackRatio", 0.55D);
        double offset = result.velocityOffset();
        double threshold = cfgDouble("threshold", 0.08D);

        if (ratio >= minRatio && offset <= threshold) {
            cool(p, 0.3D);
            return;
        }

        int gain = ratio < minRatio * 0.75D ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "kbRatio=" + r(ratio) + " off=" + r(offset) + " expectedH=" + r(expectedH) + " " + result.debug)) {
            VelocityEnforcement.onVelocityFlagMinimal(plugin, p, data, pending, name());
        }
    }
}
