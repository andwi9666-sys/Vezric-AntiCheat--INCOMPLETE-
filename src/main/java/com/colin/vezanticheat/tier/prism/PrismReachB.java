package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.entity.Player;

/** Lag-compensated reach corroboration with tighter margin. */
public final class PrismReachB extends TierCheck {

    public PrismReachB(VezAntiCheat plugin) {
        super(plugin, "PrismReachB", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (!data.wasLastUseEntityAttack()) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null) {
            coolBuffer(p, 1);
            return;
        }

        double maxReach = plugin.tierCfg().checkDouble(name(), "maxReach", 3.05D);
        double margin = plugin.tierCfg().checkDouble(name(), "margin", 0.08D);
        PrismCombatSupport.ReachResult result =
                PrismCombatSupport.evaluateReachLegacy(plugin, this, p, data, maxReach, margin, false);
        if (!result.overReach) {
            coolBuffer(p, 1);
            decay(p, 0.35D);
            return;
        }
        if (incrementBuffer(p, 2)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "reach=" + round3(result.reach) + " limit=" + round3(result.limit));
            resetBuffer(p);
        }
    }
}
