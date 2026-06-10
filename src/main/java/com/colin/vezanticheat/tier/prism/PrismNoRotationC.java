package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.entity.Player;

/**
 * Attack landed outside the transaction acknowledgement window (silent desync).
 */
public final class PrismNoRotationC extends TierCheck {

    public PrismNoRotationC(VezAntiCheat plugin) {
        super(plugin, "PrismNoRotationC", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (!PrismCombatSupport.shouldProcessAttack(p, data, this, false)) return;

        long now = System.currentTimeMillis();
        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean()) {
            coolBuffer(p, 1);
            decay(p, 0.4D);
            return;
        }

        PrismCombatSupport.NoRotationResult result = PrismCombatSupport.evaluateNoRotationC(plugin, this, p, data, now);
        if (!result.suspicious) {
            coolBuffer(p, 1);
            decay(p, 0.4D);
            return;
        }
        if (incrementBuffer(p, 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "txn-window angle=" + round3(result.angle) + " rotAge=" + result.rotAgeMs + "ms");
            resetBuffer(p);
        }
    }
}
