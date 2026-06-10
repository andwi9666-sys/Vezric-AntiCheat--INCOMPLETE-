package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.UseItemTracker;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Attack while using item in the same slot (Prism tier). */
public final class PrismMultiActionsA extends TierCheck {

    public PrismMultiActionsA(VezAntiCheat plugin) {
        super(plugin, "PrismMultiActionsA", CheckTier.PRISM);
    }

    @Override
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, Entity target) {
        if (!attack || p == null || data == null || lagGated(p, data)) return;
        long nowMs = System.currentTimeMillis();
        UseItemTracker.reconcileMeleeAttack(p, data, nowMs);
        if (!UseItemTracker.isAttackConflictingItemUse(p, data, nowMs)) {
            coolBuffer(p, 1);
            decay(p, 0.25D);
            return;
        }

        if (incrementBuffer(p, 1)) {
            blockAttack(p, data, "PrismMultiActionsA attack-while-using");
            failWithMitigation(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "attack-while-using", com.colin.vezanticheat.verdict.PrismMitigationPolicy.Confidence.HIGH);
            resetBuffer(p);
        }
    }
}
