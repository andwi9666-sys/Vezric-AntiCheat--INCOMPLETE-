package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.VelocityEnforcement;
import com.colin.vezanticheat.velocity.VelocityEvaluationResult;
import org.bukkit.entity.Player;

/**
 * Anti-knockback / velocity cheat detection from the velocity evaluation pipeline.
 */
public final class PredictionVelocity extends AbstractMovementTierCheck {

    public PredictionVelocity(VezAntiCheat plugin) {
        super(plugin, "PredictionVelocity", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (plugin.prediction() == null) return;

        VelocityEvaluationResult result = plugin.prediction().getLastVelocityResult(data);
        if (result == null) return;
        data.getPredictionState().setLastVelocityResult(null);

        if (result.isExempt()) {
            cool(p, 0.7D);
            return;
        }

        boolean blatant = result.impossiblePosition && result.setbackConfidence >= 0.85;
        boolean multiSignal = (result.zeroVertical && result.reducedHorizontal)
                || (result.reverseKnockback && result.reducedHorizontal);

        if (!blatant && !multiSignal) {
            cool(p, 0.7D);
            return;
        }

        int gain = blatant ? 2 : 1;
        String debug = "zeroV=" + result.zeroVertical
                + " redH=" + result.reducedHorizontal
                + " weakFwd=" + result.weakForwardResponse
                + " rev=" + result.reverseKnockback
                + " imp=" + result.impossiblePosition
                + " conf=" + Math.round(result.setbackConfidence * 100) + "%"
                + " " + result.debugSummary;

        if (blatant) {
            if (!data.tryClaimKnockbackFlag(name(), nowMs, cfgLong("kbDedupeMs", 350L))) {
                cool(p, 0.7D);
                return;
            }
            VelocityEnforcement.onVelocityFlag(plugin, p, data, result, name());
            fail(p, data, cfgDouble("failVl", 1.0D), debug);
            resetBuffer(p);
            return;
        }

        if (flagBuffered(p, data, gain, debug)) {
            if (!data.tryClaimKnockbackFlag(name(), nowMs, cfgLong("kbDedupeMs", 350L))) {
                resetBuffer(p);
                return;
            }
            VelocityEnforcement.onVelocityFlag(plugin, p, data, result, name());
        }
    }
}
