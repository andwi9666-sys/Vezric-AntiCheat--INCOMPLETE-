package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.MovementEnforcement;
import com.colin.vezanticheat.utils.SetbackBlocker;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Detects ignored server teleports / setback accept failures (Prism tier). */
public final class PrismSetbackAccept extends TierCheck {

    public PrismSetbackAccept(VezAntiCheat plugin) {
        super(plugin, "PrismSetbackAccept", CheckTier.PRISM);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (!data.isPendingSetback()) return;

        long graceMs = plugin.tierCfg().checkLong(name(), "graceMs", 350L);
        long sinceSetback = nowMs - data.getPendingSetbackSinceMs();
        if (sinceSetback < graceMs) return;

        Location target = data.getPendingSetbackTarget();
        Location current = data.getLastLoc();
        if (target == null || current == null || target.getWorld() == null || current.getWorld() == null) return;
        if (!target.getWorld().equals(current.getWorld())) return;

        double dist = target.distance(current);
        double acceptDist = plugin.tierCfg().checkDouble(name(), "acceptDistance", 1.0D);
        if (dist <= acceptDist) {
            SetbackBlocker.onAcceptedPosition(p, data, current);
            coolBuffer(p, 1);
            decay(p, 0.35D);
            return;
        }

        double rejectDist = plugin.tierCfg().checkDouble(name(), "rejectDistance", 2.5D);
        if (dist < rejectDist) {
            MovementEnforcement.blockCurrentMovementPacket(data, "pending-setback dist=" + round3(dist));
            coolBuffer(p, 1);
            decay(p, 0.25D);
            return;
        }

        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 4);
        if (incrementBuffer(p, 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "ignored-setback dist=" + round3(dist) + " target=" + fmt(target));
            MovementEnforcement.requestBlatantEnforcement(plugin, p, data,
                    "PrismSetbackAccept dist=" + round3(dist));
            data.clearPendingSetback();
            resetBuffer(p);
        } else {
            verbose(p, "buf=" + buffer(p.getUniqueId()) + "/" + bufferToFlag + " setback-dist=" + round3(dist));
            MovementEnforcement.blockCurrentMovementPacket(data, "pending-setback dist=" + round3(dist));
        }
    }

    private static String fmt(Location loc) {
        return String.format("%.2f,%.2f,%.2f", loc.getX(), loc.getY(), loc.getZ());
    }
}
