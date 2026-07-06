package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.prediction.PredictionState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Unified setback anchor: the last on-ground position from a fully valid movement tick.
 * All setbacks teleport here — the position before invalid movement began.
 */
public final class SetbackUtil {

    private SetbackUtil() {}

    public static void seedValidGroundAnchor(PlayerData data, Location location, long nowMs) {
        if (data == null || location == null || location.getWorld() == null) return;
        if (!isServerGroundAt(location)) return;
        data.getPredictionState().setLastValidGroundSetbackLocation(location.clone(), nowMs);
    }

    /**
     * Call once per position packet after engine + legacy prediction have run.
     */
    public static void recordMovementSample(VezAntiCheat plugin, PlayerData data, Location to, long nowMs) {
        if (plugin == null || data == null || to == null || to.getWorld() == null) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;

        EngineResult engine = data.getLastEngineResult();
        if (engine != null && !engine.checked) {
            return;
        }

        boolean grounded = isServerGroundAt(to);
        boolean valid = isMovementValid(plugin, data, engine);
        if (engine != null && engine.checked) {
            grounded = grounded || engine.predictedOnGround;
        } else {
            PredictionResult prediction = data.getPredictionState().getLastResult();
            if (prediction != null) {
                grounded = grounded || prediction.effectiveServerGround;
            }
        }

        if (!valid || !grounded) {
            return;
        }

        data.getPredictionState().setLastValidGroundSetbackLocation(to.clone(), nowMs);
        // GrimAC afterTickFriction: remember the post-tick (friction-applied) velocity at this valid anchor
        // so a setback can re-apply the player's momentum instead of zeroing it.
        com.colin.vezanticheat.movement.SimulationResult sim = data.getLastSimulationResult();
        data.getPredictionState().setLastKnownGoodVelocity(sim == null ? null : sim.nextMotion);
    }

    /** Schedules a main-thread teleport to the resolved setback anchor. */
    public static boolean executeSetback(VezAntiCheat plugin, Player player, PlayerData data, String reason) {
        return MovementEnforcement.executeSetback(plugin, player, data, reason);
    }

    public static Location resolveSetbackTarget(VezAntiCheat plugin, Player player, PlayerData data) {
        if (plugin == null || player == null || data == null) return null;

        Location current = player.getLocation();
        if (current == null || current.getWorld() == null) return null;

        long now = System.currentTimeMillis();
        long maxAgeMs = Math.max(250L, plugin.getConfig().getLong("prediction.setback.max-valid-age-ms", 2500L));
        PredictionState state = data.getPredictionState();

        Location anchor = state.getLastValidGroundSetbackLocation();
        long anchorTime = state.getLastValidGroundSetbackTimeMs();
        if (isUsableTarget(current, anchor)
                && anchorTime > 0L
                && now >= anchorTime
                && (now - anchorTime) <= maxAgeMs
                && isTargetSafe(anchor)) {
            return withCurrentLook(anchor, current);
        }

        // Fallback: last recorded move-from position. Apply the SAME max-age + same-world checks
        // the primary anchor has (lastMoveMillis is stamped alongside lastMoveFrom) so we never
        // teleport to a stale or cross-world position.
        Location previousMove = data.getLastMoveFrom();
        long previousMoveTime = data.getLastMoveMillis();
        if (isUsableTarget(current, previousMove)
                && previousMoveTime > 0L
                && now >= previousMoveTime
                && (now - previousMoveTime) <= maxAgeMs
                && isServerGroundAt(previousMove)
                && isTargetSafe(previousMove)) {
            return withCurrentLook(previousMove, current);
        }

        return null;
    }

    /**
     * Execution-time safety: the target chunk must be loaded and the block below must be solid.
     * Prevents teleporting a player into unloaded terrain or into the air.
     */
    private static boolean isTargetSafe(Location target) {
        if (target == null || target.getWorld() == null) return false;
        int cx = (int) Math.floor(target.getX()) >> 4;
        int cz = (int) Math.floor(target.getZ()) >> 4;
        if (!target.getWorld().isChunkLoaded(cx, cz)) return false;
        return isServerGroundAt(target);
    }

    private static boolean isMovementValid(VezAntiCheat plugin, PlayerData data, EngineResult engine) {
        if (plugin.engine() != null && plugin.engine().isEnabled() && engine != null && engine.checked) {
            double threshold = plugin.getConfig().getDouble("prediction.setback.valid-engine-offset", 0.12D);
            return engine.offset <= threshold;
        }

        PredictionResult prediction = data.getPredictionState().getLastResult();
        if (prediction == null) {
            return false;
        }
        return !prediction.horizontalViolation
                && !prediction.verticalViolation
                && !prediction.phaseViolation;
    }

    private static Location withCurrentLook(Location target, Location current) {
        Location resolved = target.clone();
        resolved.setYaw(current.getYaw());
        resolved.setPitch(current.getPitch());
        return resolved;
    }

    private static boolean isUsableTarget(Location current, Location target) {
        if (current == null || target == null) return false;
        if (current.getWorld() == null || target.getWorld() == null) return false;
        return current.getWorld().equals(target.getWorld());
    }

    public static boolean isServerGroundAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (double ox = -0.3; ox <= 0.3; ox += 0.3) {
            for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
                Material below = loc.clone().add(ox, -0.1, oz).getBlock().getType();
                if (below.isSolid() || below.name().contains("FENCE") || below.name().contains("WALL")
                        || below.name().contains("STEP") || below.name().contains("STAIRS")) {
                    return true;
                }
            }
        }
        return false;
    }
}
