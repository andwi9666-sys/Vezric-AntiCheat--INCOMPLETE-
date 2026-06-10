package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.entity.Player;

/**
 * Eye-ray vs attack vector at packet yaw — cancels impossible hits.
 */
public final class PrismRotationRay extends TierCheck {

    public PrismRotationRay(VezAntiCheat plugin) {
        super(plugin, "PrismRotationRay", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (!data.wasLastUseEntityAttack()) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean()) {
            coolBuffer(p, 1);
            decay(p, 0.35D);
            return;
        }

        PrismCombatSupport.RayResult result = PrismCombatSupport.evaluateRotationRay(p, data);
        if (!result.rayMiss) {
            coolBuffer(p, 1);
            decay(p, 0.35D);
            return;
        }

        if (incrementBuffer(p, 2)) {
            blockAttack(p, data, name() + " rayMiss angle=" + round3(result.angle));
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "rayMiss angle=" + round3(result.angle) + " dot=" + round3(result.lookDot)
                            + " dist=" + round3(result.distance));
            resetBuffer(p);
        }
    }
}
