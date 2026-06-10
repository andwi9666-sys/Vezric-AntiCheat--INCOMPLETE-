package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.verdict.PrismMitigationPolicy;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Block interaction line-of-sight: dig/place while not looking at target block.
 */
public final class PrismBlockSight extends TierCheck {

    public PrismBlockSight(VezAntiCheat plugin) {
        super(plugin, "PrismBlockSight", CheckTier.PRISM);
    }

    @Override
    public void onDigging(Player p, PlayerData data,
                            com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType action, Block block) {
        if (p == null || data == null || block == null || lagGated(p, data)) return;
        evaluate(p, data, block.getLocation().add(0.5, 0.5, 0.5), "dig");
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, Block against, int faceId,
                                   float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null || against == null || lagGated(p, data)) return;
        Location target = against.getLocation().clone();
        evaluate(p, data, target.add(0.5, 0.5, 0.5), "place");
    }

    private void evaluate(Player p, PlayerData data, Location blockCenter, String kind) {
        Location eye = p.getEyeLocation();
        double maxReach = plugin.tierCfg().checkDouble(name(), "maxReach", 5.5D);
        if (eye.distance(blockCenter) > maxReach) return;

        Vector dir = blockCenter.toVector().subtract(eye.toVector()).normalize();
        Vector look = eye.getDirection().normalize();
        double dot = dir.dot(look);
        double minDot = plugin.tierCfg().checkDouble(name(), "minLookDot", 0.55D);

        if (dot < minDot && incrementBuffer(p, 1)) {
            if ("dig".equals(kind)) blockDig(data, "block_sight");
            else blockPlace(data, "block_sight");
            failWithMitigation(p, data, 0.9D, kind + "_out_of_sight dot=" + round3(dot),
                    PrismMitigationPolicy.Confidence.MODERATE);
            resetBuffer(p);
        } else if (dot >= minDot) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
        }
    }
}
