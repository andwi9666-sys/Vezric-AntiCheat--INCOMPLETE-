package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatUtil;
import org.bukkit.entity.Player;

/** Impossible-hit cancel pipeline — cancels ray-failed or over-reach hits. */
public final class PrismReachC extends TierCheck {

    public PrismReachC(VezAntiCheat plugin) {
        super(plugin, "PrismReachC", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (!data.wasLastUseEntityAttack()) return;

        PrismCombatSupport.RayResult ray = PrismCombatSupport.evaluateRotationRay(p, data);
        double maxReach = CombatUtil.resolveEffectiveMaxReach(plugin, "PrismInteractionLegality");
        PrismCombatSupport.ReachResult reach = PrismCombatSupport.evaluateReach(plugin, this, p, data, maxReach, true);

        boolean impossible = ray.rayMiss || reach.overReach;
        if (!impossible) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
            return;
        }

        if (plugin.tierCfg().checkBoolean(name(), "cancelAttack", true)) {
            blockAttack(p, data, name() + " impossible ray=" + ray.rayMiss + " reach=" + reach.overReach);
        }

        if (incrementBuffer(p, 2)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.2D),
                    "rayMiss=" + ray.rayMiss + " reach=" + round3(reach.reach) + " angle=" + round3(ray.angle));
            resetBuffer(p);
        }
    }
}
