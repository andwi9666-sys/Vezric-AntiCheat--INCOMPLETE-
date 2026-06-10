package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * InventoryA — clicking inventory slots while moving beyond residual momentum.
 */
public final class PrismInventoryA extends TierCheck {

    public PrismInventoryA(VezAntiCheat plugin) {
        super(plugin, "PrismInventoryA", CheckTier.PRISM);
    }

    @Override
    public void onInventoryAction(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (!data.isInventoryOpen()) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;
        if (PrismInventorySupport.isMomentumExempt(data)) return;

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return;

        double horizontalSpeed = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        long now = System.currentTimeMillis();
        long timeSinceOpen = now - data.getLastInventoryAction();
        double maxMomentumSpeed = PrismInventorySupport.momentumThreshold(plugin, timeSinceOpen);

        if (horizontalSpeed <= maxMomentumSpeed) {
            decay(p, 0.4D);
            return;
        }

        PrismInventorySupport.closeAndSetback(this, plugin, p, data,
                "invMove speed=" + r(horizontalSpeed) + " maxMomentum=" + r(maxMomentumSpeed)
                        + " timeSinceOpen=" + timeSinceOpen + "ms");
        fail(p, data, cfgDouble("failVl", 1.0D),
                "invMove speed=" + r(horizontalSpeed) + " maxMomentum=" + r(maxMomentumSpeed)
                        + " timeSinceOpen=" + timeSinceOpen + "ms");
    }

    private double cfgDouble(String key, double def) {
        return plugin.tierCfg().checkDouble(name(), key, def);
    }

    private double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
