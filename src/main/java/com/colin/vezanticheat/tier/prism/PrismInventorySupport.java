package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.MovementEnforcement;
import org.bukkit.entity.Player;

/**
 * Shared inventory-move momentum model and punishment (close + setback).
 */
public final class PrismInventorySupport {

    private PrismInventorySupport() {}

    public static long momentumExemptMs(VezAntiCheat plugin) {
        return plugin.tierCfg().checkLong("PrismInventoryA", "momentumExemptMs", 500L);
    }

    public static boolean isMomentumExempt(PlayerData data) {
        return data != null && data.isInventoryMomentumExempt();
    }

    /**
     * Max horizontal blocks/tick allowed from residual sprint momentum after inventory open.
     */
    public static double momentumThreshold(VezAntiCheat plugin, long timeSinceOpenMs) {
        if (timeSinceOpenMs <= 0L) {
            return plugin.tierCfg().checkDouble("PrismInventoryA", "openMaxSpeed", 0.30D);
        }
        double ticksSinceOpen = timeSinceOpenMs / 50.0D;
        double decayed = 0.28D * Math.pow(0.546D, ticksSinceOpen);
        double tolerance = plugin.tierCfg().checkDouble("PrismInventoryA", "momentumTolerance", 0.03D);
        double minThreshold = plugin.tierCfg().checkDouble("PrismInventoryA", "minResidualSpeed", 0.03D);
        return Math.max(minThreshold, decayed + tolerance);
    }

    public static void closeAndSetback(TierCheck check, VezAntiCheat plugin, Player p, PlayerData data,
                                       String debug) {
        if (check == null || plugin == null || p == null || data == null) return;
        p.closeInventory();
        data.setInventoryOpen(false);
        data.setInventoryCVerbose(0);
        data.setInventoryBVerbose(0);
        if (check.setbackEnabled()) {
            long delayMs = plugin.getConfig().getLong("prism.mitigation-delay-ms", 5L);
            MovementEnforcement.requestImmediateSetback(plugin, p, data, check.name() + " " + debug, delayMs);
        }
    }
}
