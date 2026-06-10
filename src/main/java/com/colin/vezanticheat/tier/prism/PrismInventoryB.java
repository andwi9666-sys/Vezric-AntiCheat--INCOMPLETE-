package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Deque;

/**
 * InventoryB — fast inventory clicks while moving beyond momentum.
 */
public final class PrismInventoryB extends TierCheck {

    public PrismInventoryB(VezAntiCheat plugin) {
        super(plugin, "PrismInventoryB", CheckTier.PRISM);
    }

    @Override
    public void onInventoryAction(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (!data.isInventoryOpen()) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;
        if (PrismInventorySupport.isMomentumExempt(data)) return;

        Deque<Long> clicks = data.getInventoryClickTimes();
        int minSamples = plugin.tierCfg().checkInt(name(), "minSamples", 5);
        if (clicks.size() < minSamples) {
            decay(p, 0.5D);
            return;
        }

        Long[] arr = clicks.toArray(new Long[0]);
        long span = arr[arr.length - 1] - arr[0];
        if (span <= 0) return;
        double cps = (arr.length * 1000.0) / span;

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return;
        double speed = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());

        long timeSinceOpen = System.currentTimeMillis() - data.getLastInventoryAction();
        double maxMomentum = PrismInventorySupport.momentumThreshold(plugin, timeSinceOpen);
        double maxCps = plugin.tierCfg().checkDouble(name(), "maxClicksPerSec", 12.0D);

        if (cps > maxCps && speed > maxMomentum) {
            int vb = data.getInventoryBVerbose() + 1;
            data.setInventoryBVerbose(vb);
            int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 2);
            if (vb >= bufferToFlag) {
                PrismInventorySupport.closeAndSetback(this, plugin, p, data,
                        "fastClickMove cps=" + r(cps) + " speed=" + r(speed)
                                + " maxMomentum=" + r(maxMomentum)
                                + " timeSinceOpen=" + timeSinceOpen + "ms");
                fail(p, data, cfgDouble("failVl", 1.5D),
                        "fastClickMove cps=" + r(cps) + " speed=" + r(speed)
                                + " maxMomentum=" + r(maxMomentum)
                                + " timeSinceOpen=" + timeSinceOpen + "ms");
                clicks.clear();
            }
        } else {
            data.setInventoryBVerbose(Math.max(0, data.getInventoryBVerbose() - 1));
            decay(p, 0.5D);
        }
    }

    private double cfgDouble(String key, double def) {
        return plugin.tierCfg().checkDouble(name(), key, def);
    }

    private double r(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
