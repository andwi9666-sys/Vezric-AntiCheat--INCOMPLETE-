package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.entity.Player;

/** Close inventory while moving beyond simulation offset (Prism tier). */
public final class PrismMultiActionsD extends TierCheck {

    public PrismMultiActionsD(VezAntiCheat plugin) {
        super(plugin, "PrismMultiActionsD", CheckTier.PRISM);
    }

    @Override
    public void onCloseInventory(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;

        EngineResult result = data.getLastEngineResult();
        if (result == null || !result.checked) return;

        double threshold = plugin.tierCfg().checkDouble(name(), "moveOffsetThreshold", 0.03D);
        if (result.offset <= threshold) {
            coolBuffer(p, 1);
            decay(p, 0.30D);
            return;
        }

        if (incrementBuffer(p, 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 0.8D),
                    "close-inv-moving off=" + round3(result.offset));
            resetBuffer(p);
        }
    }
}
