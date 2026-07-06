package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.engine.MovementPlayer;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.VelocityEnforcement;
import com.colin.vezanticheat.velocity.VelocityEvaluationResult;
import com.colin.vezanticheat.velocity.VelocityProcessor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Grim-style knockback envelope sub-signal. Validates post-KB movement against pending impulse.
 *
 * <p>Uses {@link VelocityProcessor} as ratio authority when available; falls back to engine
 * displacement vs pending knockback vector.</p>
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
            data.noteKbRatioBelowThreshold(false);
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

        double ratio = resolveKnockbackRatio(plugin, p, data, result, pending);
        double minRatio = cfgDouble("minKnockbackRatio", 0.48D);
        double offset = result.velocityOffset();
        double threshold = cfgDouble("threshold", 0.08D);
        int sustainTicksRequired = cfgInt("sustainTicks", 3);

        boolean belowRatio = ratio < minRatio;
        data.noteKbRatioBelowThreshold(belowRatio && offset > threshold);

        if (ratio >= minRatio && offset <= threshold) {
            cool(p, 0.3D);
            return;
        }

        boolean sustainedPartial = belowRatio
                && offset > threshold
                && data.getKbRatioSustainTicks() >= sustainTicksRequired;

        if (!sustainedPartial && ratio >= minRatio * 0.75D && offset <= threshold * 1.5D) {
            cool(p, 0.3D);
            return;
        }

        if (!data.tryClaimKnockbackFlag(name(), nowMs, cfgLong("kbDedupeMs", 350L))) {
            cool(p, 0.2D);
            return;
        }

        int gain = ratio < minRatio * 0.75D ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "kbRatio=" + r(ratio) + " off=" + r(offset) + " sustain=" + data.getKbRatioSustainTicks()
                        + " " + result.debug)) {
            VelocityEnforcement.onVelocityFlagMinimal(plugin, p, data, pending, name());
        }
    }

    private static double resolveKnockbackRatio(VezAntiCheat plugin, Player p, PlayerData data,
                                                EngineResult result, Vector pending) {
        VelocityProcessor processor = plugin == null ? null : plugin.velocity();
        if (processor != null && p != null) {
            VelocityEvaluationResult vel = processor.getPendingResult(p.getUniqueId());
            if (vel != null && vel.expectedHorizontal > 1.0E-4D) {
                return vel.maxHorizontal / vel.expectedHorizontal;
            }
        }

        double partial = data == null ? 0.0D : data.getPartialKbRatio();
        if (partial > 0.0D && partial < 1.0D) {
            return 1.0D - partial;
        }

        double expectedH = Math.hypot(pending.getX(), pending.getZ());
        double actualH = result.actual == null ? 0.0D
                : Math.hypot(result.actual.getX(), result.actual.getZ());
        return expectedH <= 1.0E-4D ? 0.0D : actualH / expectedH;
    }
}
