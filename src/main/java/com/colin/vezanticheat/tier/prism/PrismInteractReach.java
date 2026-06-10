package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.entity.Player;

/** Structural interact distance validation. */
public final class PrismInteractReach extends TierCheck {

    public PrismInteractReach(VezAntiCheat plugin) {
        super(plugin, "PrismInteractReach", CheckTier.PRISM);
    }

    @Override
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, org.bukkit.entity.Entity target) {
        if (p == null || data == null || attack) return;
        if (lagGated(p, data)) return;
        if (data.isTeleportExempt()) return;

        double maxDist = plugin.tierCfg().checkDouble(name(), "maxInteractDistance", 4.5D);
        double dist = data.getLastUseEntityDistance();
        if (dist <= maxDist) {
            coolBuffer(p, 1);
            decay(p, 0.35D);
            return;
        }

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat != null && !combat.isClean()) {
            coolBuffer(p, 1);
            decay(p, 0.35D);
            return;
        }

        if (incrementBuffer(p, dist > maxDist + 0.5D ? 2 : 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "interact-dist=" + round3(dist) + " max=" + round3(maxDist));
            resetBuffer(p);
        }
    }
}
