package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Entity action while dead or spectating. */
public final class PrismBadPacketsI extends PrismBadPacketCheck {

    public PrismBadPacketsI(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsI", 'I');
    }

    @Override
    public void onEntityAction(Player p, PlayerData data, String actionName) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt()) return;

        boolean invalid = p.isDead() || p.getGameMode() == GameMode.SPECTATOR;
        if (!invalid) {
            decayLetterBuffer(data, 1);
            decay(p, 0.3D);
            return;
        }

        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 2);
        int buf = incrementLetterBuffer(p, data, 2, bufferToFlag,
                "entityActionDeadSpec action=" + actionName);
        if (buf >= bufferToFlag) {
            recordBlatant(data, 2, System.currentTimeMillis());
            flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.5D),
                    "entityActionDeadSpec dead=" + p.isDead() + " spec="
                            + (p.getGameMode() == GameMode.SPECTATOR) + " action=" + actionName);
        }
    }
}
