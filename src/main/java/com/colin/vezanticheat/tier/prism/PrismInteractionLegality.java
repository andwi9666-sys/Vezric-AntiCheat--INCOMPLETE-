package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.PrismCheckLabels;
import com.colin.vezanticheat.verdict.PrismMitigationPolicy;
import org.bukkit.entity.Player;

/**
 * Polar "Fighting suspiciously" — single combat interaction check.
 * Absorbs reach, hitbox, line-of-sight, stale rotation, backtrack, and lag-range
 * without separate KillAura / Reach / Hitbox tier checks.
 */
public final class PrismInteractionLegality extends TierCheck {

    public PrismInteractionLegality(VezAntiCheat plugin) {
        super(plugin, "PrismInteractionLegality", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (!PrismCombatSupport.shouldProcessAttack(p, data, this, true)) return;

        long now = System.currentTimeMillis();
        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
            return;
        }

        PrismInteractionEvaluator.InteractionResult result =
                PrismInteractionEvaluator.evaluate(plugin, name(), p, data, now);

        if (result.signalCount <= 0 && !result.hitboxPattern) {
            coolBuffer(p, 1);
            decay(p, 0.35D);
            return;
        }

        if (!result.blatant && (combat.isTrade()
                || CombatContextAnalyzer.shouldExemptCombatInteractionFlagging(combat, data, now))) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
            return;
        }

        if (!result.blatant && !combat.isClean()) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
            return;
        }

        if (result.shouldCancel) {
            blockAttack(p, data, result.label);
        }

        if (result.blatant) {
            failWithMitigation(p, data, plugin.tierCfg().checkDouble(name(), "blatantFailVl", 1.2D),
                    PrismCheckLabels.fightingReason(result.label, result.debug),
                    PrismMitigationPolicy.Confidence.BLATANT);
            resetBuffer(p);
            return;
        }

        int gain = result.hitboxPattern ? 2 : result.signalCount >= 2 ? 2 : 1;
        if (incrementBuffer(p, gain)) {
            PrismMitigationPolicy.Confidence conf = PrismMitigationPolicy.fromInteraction(result.confidence);
            failWithMitigation(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    PrismCheckLabels.fightingReason(result.label, result.debug), conf);
            resetBuffer(p);
        }
    }
}
