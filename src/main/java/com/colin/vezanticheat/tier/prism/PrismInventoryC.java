package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * InventoryC — sustained movement while inventory is open (after momentum grace).
 */
public final class PrismInventoryC extends TierCheck {

    public PrismInventoryC(VezAntiCheat plugin) {
        super(plugin, "PrismInventoryC", CheckTier.PRISM);
    }

    @Override
    public void onMove(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (!data.isInventoryOpen()) {
            data.setInventoryCVerbose(0);
            return;
        }
        if (data.isTeleportExempt() || data.isVelocityExempt()) {
            data.setInventoryCVerbose(0);
            return;
        }
        if (PrismInventorySupport.isMomentumExempt(data)) {
            return;
        }

        long now = System.currentTimeMillis();
        long timeSinceOpen = now - data.getLastInventoryAction();
        long gracePeriodMs = plugin.tierCfg().checkLong(name(), "gracePeriodMs",
                PrismInventorySupport.momentumExemptMs(plugin));
        if (timeSinceOpen < gracePeriodMs) {
            return;
        }

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return;

        double speed = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        double maxResidual = PrismInventorySupport.momentumThreshold(plugin, timeSinceOpen);

        if (speed > maxResidual) {
            int vb = data.getInventoryCVerbose() + 1;
            data.setInventoryCVerbose(vb);
            int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 3);
            if (vb >= bufferToFlag) {
                PrismInventorySupport.closeAndSetback(this, plugin, p, data,
                        "sustainedInvMove speed=" + r(speed)
                                + " timeSinceOpen=" + timeSinceOpen + "ms"
                                + " maxResidual=" + r(maxResidual));
                fail(p, data, cfgDouble("failVl", 1.2D),
                        "sustainedInvMove speed=" + r(speed)
                                + " timeSinceOpen=" + timeSinceOpen + "ms"
                                + " maxResidual=" + r(maxResidual));
            }
        } else {
            data.setInventoryCVerbose(Math.max(0, data.getInventoryCVerbose() - 1));
            decay(p, 0.4D);
        }
    }

    private double cfgDouble(String key, double def) {
        return plugin.tierCfg().checkDouble(name(), key, def);
    }

    private double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
