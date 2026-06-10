package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.VelocityEnforcement;
import com.colin.vezanticheat.velocity.VelocityEvaluationResult;
import com.colin.vezanticheat.velocity.VelocityProcessor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Knockback resistance pattern via velocity prediction engine. */
public final class CharVelocityPattern extends TierCheck {

    public CharVelocityPattern(VezAntiCheat plugin) {
        super(plugin, "CharVelocityPattern", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (PlayerData.bypass(p)) return;

        VelocityProcessor processor = plugin.velocity();
        if (processor == null) return;
        processor.onTick(p, data, nowMs);

        VelocityEvaluationResult result = processor.getPendingResult(p.getUniqueId());
        if (result == null) return;

        boolean deferClear = false;
        try {
            if (data.isVelocityExempt()
                    || EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) {
                deferClear = true;
                coolBuffer(p, 1);
                decay(p, 0.8);
                return;
            }

            updatePartialKbRatio(data, result);

            if (result.isExempt()) {
                coolBuffer(p, 1);
                decay(p, 0.8);
                return;
            }

            boolean blatant = result.impossiblePosition && result.setbackConfidence >= 0.85;
            boolean multiSignal = (result.zeroVertical && result.reducedHorizontal)
                    || (result.reverseKnockback && result.reducedHorizontal);

            if (!blatant && !multiSignal) {
                coolBuffer(p, 1);
                decay(p, 0.8);
                return;
            }

            int violations = countViolations(result);
            if (violations == 0) {
                coolBuffer(p, 1);
                decay(p, 0.8);
                return;
            }

            if (blatant) {
                VelocityEnforcement.onVelocityFlag(plugin, p, data, result, name());
                fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0),
                        buildDebug(result, violations));
                resetBuffer(p);
                return;
            }

            int gain = 1;
            if (result.reverseKnockback && result.reducedHorizontal) gain += 1;

            if (incrementBuffer(p, gain)) {
                fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0),
                        buildDebug(result, violations));
                VelocityEnforcement.onVelocityFlag(plugin, p, data, result, name());
                resetBuffer(p);
            } else {
                verbose(p, "velocity violations=" + violations + " zeroV=" + result.zeroVertical);
            }
        } finally {
            if (!deferClear) {
                processor.clearPendingResult(p.getUniqueId());
            }
        }
    }

    private void updatePartialKbRatio(PlayerData data, VelocityEvaluationResult result) {
        if (data == null || result == null || result.expectedHorizontal <= 1.0E-4D) {
            if (data != null) data.setPartialKbRatio(0.0D);
            return;
        }
        double ratio = result.maxHorizontal / result.expectedHorizontal;
        if (result.reducedHorizontal || result.zeroVertical) {
            data.setPartialKbRatio(Math.min(1.0D, 1.0D - ratio));
        } else {
            data.setPartialKbRatio(Math.max(0.0D, data.getPartialKbRatio() * 0.85D));
        }
    }

    private int countViolations(VelocityEvaluationResult result) {
        int count = 0;
        if (result.zeroVertical) count++;
        if (result.reducedHorizontal) count++;
        if (result.reverseKnockback) count++;
        if (result.impossiblePosition) count++;
        return count;
    }

    private String buildDebug(VelocityEvaluationResult result, int violations) {
        return "pattern violations=" + violations
                + " zeroV=" + result.zeroVertical
                + " reducedH=" + result.reducedHorizontal
                + " reverse=" + result.reverseKnockback
                + " vGain=" + round3(result.verticalGain)
                + " maxH=" + round3(result.maxHorizontal)
                + " dot=" + round3(result.directionDot)
                + " " + result.debugSummary;
    }
}
