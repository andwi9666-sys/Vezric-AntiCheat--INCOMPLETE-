package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.entity.Player;

/**
 * PrismAimDuplicate — GrimAC {@code AimDuplicateLook} analogue.
 *
 * <p>A dedicated rotation packet whose yaw AND pitch are byte-identical to the previous rotation is
 * abnormal: vanilla emits a look packet only when the rotation actually changes (a perfectly still
 * player sends flying-only packets instead). Some silent-aim / no-rotation cheats emit redundant
 * identical look packets. Because a straight-line walk legitimately repeats the same rotation on
 * position+look packets, this is gated to non-position rotation packets and to recent combat, and kept
 * conservative (alerts only by default).</p>
 */
public final class PrismAimDuplicate extends TierCheck {

    public PrismAimDuplicate(VezAntiCheat plugin) {
        super(plugin, "PrismAimDuplicate", CheckTier.PRISM);
    }

    @Override
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;

        long now = System.currentTimeMillis();

        // Skip position-bearing packets: walking in a straight line legitimately repeats rotation.
        long posGuardMs = plugin.tierCfg().checkLong(name(), "positionGuardMs", 60L);
        if (data.getLastMoveMillis() > 0L && (now - data.getLastMoveMillis()) <= posGuardMs) {
            coolBuffer(p, 1);
            return;
        }

        // Only meaningful during combat — avoids idle-look false positives.
        long combatMs = plugin.tierCfg().checkLong(name(), "combatWindowMs", 1500L);
        if (data.getLastUseEntityTime() <= 0L || (now - data.getLastUseEntityTime()) > combatMs) {
            coolBuffer(p, 1);
            return;
        }

        boolean duplicate = yaw == data.getPriorYaw() && pitch == data.getPriorPitch();
        if (!duplicate) {
            coolBuffer(p, 1);
            return;
        }

        if (incrementBuffer(p, 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "vl", 1.0D),
                    "duplicate-look yaw=" + round3(yaw) + " pitch=" + round3(pitch));
            resetBuffer(p);
        }
    }
}
