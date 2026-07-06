package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.BadPacketTracker;
import com.colin.vezanticheat.verdict.PrismMitigationPolicy;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Packet-stream nuker and bed-nuker coverage: fast multi-block finishes, impossible reach,
 * and repeated start/finish bursts used by instant/surroundings nukers.
 */
public final class PrismNukerA extends TierCheck {

    public PrismNukerA(VezAntiCheat plugin) {
        super(plugin, "PrismNukerA", CheckTier.PRISM);
    }

    @Override
    public void onDigging(Player p, PlayerData data, BadPacketTracker.DiggingActionType action, Block block) {
        if (p == null || data == null || block == null || lagGated(p, data)) return;
        if (action != BadPacketTracker.DiggingActionType.START
                && action != BadPacketTracker.DiggingActionType.FINISH) {
            return;
        }

        if (tooFar(p, block)) {
            int gain = isBedLike(block) ? 2 : 1;
            if (incrementBuffer(p, gain)) {
                blockDig(data, "nuker_reach");
                failWithMitigation(p, data, 1.2D,
                        "dig-reach block=" + block.getType() + " dist=" + round3(distance(p, block)),
                        PrismMitigationPolicy.Confidence.HIGH);
                resetBuffer(p);
            }
        }
    }

    @Override
    public void onBlockBreak(Player p, PlayerData data, Block block) {
        if (p == null || data == null || block == null || lagGated(p, data)) return;
        long now = System.currentTimeMillis();
        long window = plugin.tierCfg().checkLong(name(), "breakWindowMs", 650L);
        int count = 0;
        for (Long ms : data.getBreakTimestamps()) {
            if (ms != null && now - ms <= window) count++;
        }

        int maxBreaks = plugin.tierCfg().checkInt(name(), "maxBreaksPerWindow", 4);
        boolean burst = count > maxBreaks;
        boolean bedReach = isBedLike(block) && tooFar(p, block);
        if (!burst && !bedReach) {
            coolBuffer(p, 1);
            decay(p, 0.35D);
            return;
        }

        int gain = bedReach ? 2 : 1;
        if (incrementBuffer(p, gain)) {
            blockDig(data, bedReach ? "bed_nuker_reach" : "nuker_break_burst");
            failWithMitigation(p, data, bedReach ? 1.4D : 1.0D,
                    "breaks=" + count + " window=" + window + " block=" + block.getType()
                            + " dist=" + round3(distance(p, block)),
                    bedReach ? PrismMitigationPolicy.Confidence.HIGH : PrismMitigationPolicy.Confidence.MODERATE);
            resetBuffer(p);
        }
    }

    private boolean tooFar(Player p, Block block) {
        return distance(p, block) > maxReach(block);
    }

    private double maxReach(Block block) {
        return isBedLike(block)
                ? plugin.tierCfg().checkDouble(name(), "bedMaxReach", 4.6D)
                : plugin.tierCfg().checkDouble(name(), "maxReach", 5.2D);
    }

    private static double distance(Player p, Block block) {
        Location eye = p.getEyeLocation();
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        return eye.distance(center);
    }

    private static boolean isBedLike(Block block) {
        return block != null && block.getType() != null && block.getType().name().contains("BED");
    }
}
