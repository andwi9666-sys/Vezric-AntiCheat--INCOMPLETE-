package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.ScaffoldUtil;
import com.colin.vezanticheat.utils.UseItemTracker;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Tick/input/interact ordering check (PLACE_WITHOUT_ROTATE). */
public final class PrismPacketOrderD extends TierCheck {

    public PrismPacketOrderD(VezAntiCheat plugin) {
        super(plugin, "PrismPacketOrderD", CheckTier.PRISM);
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId,
                                   float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        evaluate(p, data, System.currentTimeMillis());
    }

    private void evaluate(Player p, PlayerData data, long nowMs) {
        if (UseItemTracker.isLegitSwordBlockSession(p, data, nowMs)) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
            return;
        }
        double suspicion = PrismPacketOrderSupport.score(plugin, this, data, nowMs,
                PrismPacketOrderSupport.OrderKind.PLACE_WITHOUT_ROTATE);
        double threshold = plugin.tierCfg().checkDouble(name(), "threshold", 0.55D);
        if (suspicion < threshold) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
            return;
        }

        // PLACE_WITHOUT_ROTATE cannot separate legit bridging/building from scaffold using packet timing
        // alone, and naive geometry false-flags it:
        //   - legit BACKWARD speed-bridging looks forward-down (a fixed aim, no LOOK packets) and places
        //     against the block BEHIND the player, so the crosshair is NOT on the against block; and
        //   - legit steady-aim building sends no LOOK packets while aiming straight AT the block.
        // So only flag the narrow case that is neither: the player is stationary (not bridging) AND the
        // placement eye-ray clearly misses the against block. Real scaffold's robotic timing/rotation and
        // line-of-sight are handled by the dedicated silent scaffold system (PrismScaffold/LegitScaffold).
        if (isBridgingMovement(data) || looksAtAgainstBlock(p, data)) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
            return;
        }

        if (incrementBuffer(p, suspicion >= threshold * 1.35D ? 2 : 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "order suspicion=" + round3(suspicion) + " kind=PLACE_WITHOUT_ROTATE stationary-blind-place");
            resetBuffer(p);
        }
    }

    /** True when the player has meaningful horizontal movement this tick (bridging / walking), not static. */
    private boolean isBridgingMovement(PlayerData data) {
        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return false;
        }
        double moveH = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        return moveH >= plugin.tierCfg().checkDouble(name(), "minBridgeMoveH", 0.05D);
    }

    /**
     * True when the player's placement rotation actually points at the block they placed against
     * (legit placement, even with a perfectly steady aim). Returns true when geometry is unknown so an
     * ambiguous case never false-flags.
     */
    private boolean looksAtAgainstBlock(Player p, PlayerData data) {
        Location against = data.getLastPlaceAgainstLoc();
        if (against == null || against.getWorld() == null) return true;
        Location feet = data.getLastLoc();
        if (feet == null || feet.getWorld() == null) feet = p.getLocation();
        if (feet == null || feet.getWorld() == null) return true;
        double reach = plugin.tierCfg().checkDouble(name(), "lookReach", 5.0D);
        return ScaffoldUtil.canRayHitPlacedAgainstFace(
                feet, p.getEyeHeight(), data.getLastPlaceYaw(), data.getLastPlacePitch(), against, reach, 0.1D);
    }
}
