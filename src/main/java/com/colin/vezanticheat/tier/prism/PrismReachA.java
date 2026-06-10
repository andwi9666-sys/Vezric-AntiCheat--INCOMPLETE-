package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import org.bukkit.entity.Player;

/** Transaction-rewound reach with 1.8 +0.1 margin. */
public final class PrismReachA extends TierCheck {

    public PrismReachA(VezAntiCheat plugin) {
        super(plugin, "PrismReachA", CheckTier.PRISM);
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

        double maxReach = CombatUtil.resolveEffectiveMaxReach(plugin, "PrismInteractionLegality");
        PrismCombatSupport.ReachResult result = PrismCombatSupport.evaluateReach(plugin, this, p, data, maxReach, false);
        if (!result.overReach) {
            coolBuffer(p, 1);
            decay(p, 0.35D);
            return;
        }
        if (incrementBuffer(p, result.reach > maxReach + 0.25D ? 2 : 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "reach=" + round3(result.reach) + " max=" + round3(result.limit));
            resetBuffer(p);
        }
    }
}
