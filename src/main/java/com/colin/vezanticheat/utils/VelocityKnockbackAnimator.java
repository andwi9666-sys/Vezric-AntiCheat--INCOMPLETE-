package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.prediction.PredictionState;
import com.colin.vezanticheat.velocity.PredictedTick;
import com.colin.vezanticheat.velocity.VelocityCorrectionContext;
import com.colin.vezanticheat.velocity.VelocityPredictionEngine;
import com.colin.vezanticheat.velocity.VelocitySnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Replays knockback with miniature teleports along the predicted physics trajectory.
 */
public final class VelocityKnockbackAnimator {

    private VelocityKnockbackAnimator() {}

    public static boolean applyKnockbackAnimation(VezAntiCheat plugin, Player player, PlayerData data,
                                                  VelocityCorrectionContext ctx) {
        if (plugin == null || player == null || data == null || ctx == null) return false;
        if (PlayerData.bypass(player)) return false;
        if (!plugin.getConfig().getBoolean("prediction.setback.antikb-animation-enabled", true)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("prediction.setback.antikb-cooldown-ms", 0L);
        if (cooldownMs > 0L && data.getLastVelocityAnimationMs() > 0L
                && now - data.getLastVelocityAnimationMs() < cooldownMs) {
            return false;
        }

        Vector knockback = ctx.knockback;
        if (knockback == null || knockback.lengthSquared() < 0.001D) {
            return false;
        }

        Location start = ctx.startLoc;
        if (start == null) {
            start = SetbackUtil.resolveSetbackTarget(plugin, player, data);
        }
        if (start == null || start.getWorld() == null) {
            return false;
        }
        start = start.clone();
        start.setWorld(player.getWorld());
        start.setYaw(player.getLocation().getYaw());
        start.setPitch(player.getLocation().getPitch());

        if (!shouldAnimate(plugin, ctx, start)) {
            return hardSnap(plugin, player, data, start, knockback);
        }

        List<Location> waypoints = buildWaypoints(plugin, ctx, start);
        if (waypoints.size() < 2) {
            return hardSnap(plugin, player, data, start, knockback);
        }

        PredictionState state = data.getPredictionState();
        final int sequenceId = state.nextVelocityCorrectionSequence();
        final int tickDelay = Math.max(1, plugin.getConfig().getInt("prediction.setback.antikb-animation-tick-delay", 1));
        long exemptMs = plugin.getConfig().getLong("prediction.setback.antikb-correction-exempt-ms", 500L);
        long totalDurationMs = (long) waypoints.size() * tickDelay * 50L;
        data.markVelocityExempt(exemptMs + totalDurationMs);
        data.markTeleportExempt(exemptMs + totalDurationMs);
        data.setLastVelocityAnimationMs(now);

        state.setLastSetbackMs(now);
        state.setLastSetbackReason("antikb-" + ctx.reason);
        state.setSetbackPending(true);
        state.setActiveVelocitySession(null);
        state.setLastVelocityResult(null);
        SetbackBlocker.noteServerSetback(data, start);

        final Location anchor = start.clone();
        final Vector kbToApply = knockback.clone();
        final double expectedH = ctx.expectedHorizontal > 1.0E-4D
                ? ctx.expectedHorizontal
                : Math.hypot(kbToApply.getX(), kbToApply.getZ());
        final double minReplayRatio = plugin.getConfig()
                .getDouble("prediction.setback.antikb-min-replay-ratio", 0.85D);

        for (int step = 0; step < waypoints.size(); step++) {
            final int stepIndex = step;
            final Location waypoint = waypoints.get(step).clone();
            waypoint.setYaw(player.getLocation().getYaw());
            waypoint.setPitch(player.getLocation().getPitch());

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;
                    if (state.getVelocityCorrectionSequence() != sequenceId) return;

                    MovementEnforcement.blockCurrentMovementPacket(data, "antikb-anim");
                    MovementEnforcement.sendPositionPacket(player, waypoint);
                    try {
                        player.teleport(waypoint);
                    } catch (Throwable ignored) {
                    }

                    if (stepIndex == waypoints.size() - 1) {
                        finishAnimation(plugin, player, data, state, anchor, kbToApply,
                                expectedH, minReplayRatio, exemptMs, waypoint);
                    }
                }
            }.runTaskLater(plugin, (long) stepIndex * tickDelay);
        }

        return true;
    }

    static List<Location> buildWaypoints(VezAntiCheat plugin, VelocityCorrectionContext ctx, Location start) {
        List<Location> polyline = new ArrayList<Location>();
        polyline.add(start.clone());

        List<PredictedTick> ticks = ctx.predictedTicks;
        if (ticks == null || ticks.isEmpty()) {
            VelocitySnapshot snapshot = ctx.snapshot;
            if (snapshot == null && ctx.knockback != null) {
                snapshot = buildSnapshotFromContext(ctx, start);
            }
            if (snapshot != null) {
                ticks = VelocityPredictionEngine.simulate(plugin, snapshot);
            }
        }

        if (ticks != null) {
            for (PredictedTick tick : ticks) {
                if (tick == null) continue;
                Location point = new Location(start.getWorld(),
                        tick.centerX(), tick.centerY(), tick.centerZ());
                polyline.add(point);
            }
        }

        int steps = 24;
        if (plugin != null) {
            steps = Math.max(20, Math.min(30, plugin.getConfig()
                    .getInt("prediction.setback.antikb-animation-steps", 24)));
        }
        return resamplePolyline(polyline, steps);
    }

    static boolean shouldAnimate(VezAntiCheat plugin, VelocityCorrectionContext ctx, Location start) {
        if (plugin == null || ctx == null || start == null || start.getWorld() == null) {
            return false;
        }

        List<Location> preview = buildWaypoints(plugin, ctx, start);
        if (preview.size() < 2) {
            return false;
        }

        Location end = preview.get(preview.size() - 1);
        double horizontalDistance = Math.hypot(end.getX() - start.getX(), end.getZ() - start.getZ());
        double verticalDistance = Math.abs(end.getY() - start.getY());
        double maxHorizontalDistance = plugin.getConfig()
                .getDouble("prediction.setback.antikb-animation-max-distance", 1.70D);
        double maxVerticalDistance = plugin.getConfig()
                .getDouble("prediction.setback.antikb-animation-max-vertical-distance", 1.25D);
        return horizontalDistance <= maxHorizontalDistance && verticalDistance <= maxVerticalDistance;
    }

    private static boolean hardSnap(VezAntiCheat plugin, Player player, PlayerData data,
                                    Location start, Vector knockback) {
        MovementEnforcement.sendPositionPacket(player, start);
        try {
            player.teleport(start);
        } catch (Throwable ignored) {
            return false;
        }
        long exemptMs = plugin.getConfig().getLong("prediction.setback.antikb-correction-exempt-ms", 500L);
        final Vector kb = knockback.clone();
        applyKnockbackVelocity(player, data, kb, exemptMs);
        data.setLastVelocityAnimationMs(System.currentTimeMillis());
        return true;
    }

    private static void applyKnockbackVelocity(Player player, PlayerData data, Vector knockback, long exemptMs) {
        if (player == null || knockback == null) return;
        try {
            player.setVelocity(knockback.clone());
        } catch (Throwable ignored) {
        }
        if (data != null) {
            data.setLastVelocityTime(System.currentTimeMillis());
            data.markVelocityExempt(exemptMs);
        }
    }

    private static void finishAnimation(VezAntiCheat plugin, Player player, PlayerData data,
                                        PredictionState state, Location anchor, Vector knockback,
                                        double expectedH, double minReplayRatio, long exemptMs,
                                        Location finalWaypoint) {
        Location live = player.getLocation();
        double actualH = 0.0D;
        if (live != null && anchor != null && live.getWorld() != null && anchor.getWorld() != null
                && live.getWorld().equals(anchor.getWorld())) {
            actualH = Math.hypot(live.getX() - anchor.getX(), live.getZ() - anchor.getZ());
        }

        if (expectedH > 0.08D && actualH < expectedH * minReplayRatio) {
            applyKnockbackVelocity(player, data, knockback, exemptMs);
        } else if (knockback.lengthSquared() > 0.001D) {
            applyKnockbackVelocity(player, data, knockback, exemptMs);
        }

        double finalSnap = plugin.getConfig()
                .getDouble("prediction.setback.antikb-final-snap-distance", 0.20D);
        if (live != null && finalWaypoint != null
                && live.distanceSquared(finalWaypoint) > finalSnap * finalSnap) {
            MovementEnforcement.sendPositionPacket(player, finalWaypoint);
            try {
                player.teleport(finalWaypoint);
            } catch (Throwable ignored) {
            }
        }

        state.setSetbackPending(false);
        if (finalWaypoint != null) {
            state.setLastKnownGoodLocation(finalWaypoint, System.currentTimeMillis());
        }
        data.markVelocityExempt(exemptMs);
    }

    private static VelocitySnapshot buildSnapshotFromContext(VelocityCorrectionContext ctx, Location start) {
        Vector kb = ctx.knockback;
        double expectedH = Math.hypot(kb.getX(), kb.getZ());
        double expectedV = Math.max(0.0D, kb.getY());
        return new VelocitySnapshot(
                System.currentTimeMillis(),
                kb,
                start,
                com.colin.vezanticheat.velocity.VelocitySource.OTHER,
                expectedH,
                expectedV,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                20.0D,
                false,
                false,
                false,
                null,
                false,
                null,
                0.0D,
                0);
    }

    static List<Location> resamplePolyline(List<Location> polyline, int steps) {
        List<Location> out = new ArrayList<Location>(steps);
        if (polyline == null || polyline.isEmpty() || steps <= 0) {
            return out;
        }
        if (polyline.size() == 1) {
            Location only = polyline.get(0);
            for (int i = 0; i < steps; i++) {
                out.add(only.clone());
            }
            return out;
        }

        double[] segLengths = new double[polyline.size() - 1];
        double total = 0.0D;
        for (int i = 0; i < polyline.size() - 1; i++) {
            Location a = polyline.get(i);
            Location b = polyline.get(i + 1);
            double len = Math.sqrt(Math.pow(b.getX() - a.getX(), 2)
                    + Math.pow(b.getY() - a.getY(), 2)
                    + Math.pow(b.getZ() - a.getZ(), 2));
            segLengths[i] = len;
            total += len;
        }
        if (total <= 1.0E-6D) {
            for (int i = 0; i < steps; i++) {
                out.add(polyline.get(0).clone());
            }
            return out;
        }

        for (int s = 0; s < steps; s++) {
            double target = steps <= 1 ? 0.0D : (total * s) / (steps - 1);
            double walked = 0.0D;
            for (int i = 0; i < segLengths.length; i++) {
                if (walked + segLengths[i] >= target || i == segLengths.length - 1) {
                    Location a = polyline.get(i);
                    Location b = polyline.get(i + 1);
                    double seg = segLengths[i];
                    double t = seg <= 1.0E-6D ? 0.0D : Math.min(1.0D, (target - walked) / seg);
                    Location interp = new Location(a.getWorld(),
                            a.getX() + (b.getX() - a.getX()) * t,
                            a.getY() + (b.getY() - a.getY()) * t,
                            a.getZ() + (b.getZ() - a.getZ()) * t);
                    out.add(interp);
                    break;
                }
                walked += segLengths[i];
            }
        }
        return out;
    }
}
