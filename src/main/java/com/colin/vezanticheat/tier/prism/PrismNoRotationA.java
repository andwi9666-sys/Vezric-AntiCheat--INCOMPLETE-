package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.entity.Player;

/**
 * Stale look + off-FOV attack using packet-synced eye ray context.
 */
public final class PrismNoRotationA extends TierCheck {

    public PrismNoRotationA(VezAntiCheat plugin) {
        super(plugin, "PrismNoRotationA", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (!PrismCombatSupport.shouldProcessAttack(p, data, this, true)) return;

        long now = System.currentTimeMillis();
        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean()) {
            coolBuffer(p, 1);
            decay(p, 0.4D);
            return;
        }

        PrismCombatSupport.NoRotationResult result = PrismCombatSupport.evaluateNoRotationA(plugin, this, p, data, now);
        if (!result.suspicious) {
            coolBuffer(p, 1);
            decay(p, 0.4D);
            return;
        }
        if (incrementBuffer(p, 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "silent angle=" + round3(result.angle) + " dot=" + round3(result.lookDot)
                            + " dist=" + round3(result.distance) + " rotAge=" + result.rotAgeMs + "ms");
            resetBuffer(p);
        }
    }
}
