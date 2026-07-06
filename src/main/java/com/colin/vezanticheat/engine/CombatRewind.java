package com.colin.vezanticheat.engine;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.List;

/**
 * CombatRewind — reconstructs the lag-compensated reach for one attack.
 *
 * Preferred path (engine, transaction-anchored): gather the target's position snapshots whose
 * sequence sits in the bracket around the client's last acknowledged transaction (plus a small
 * window for interpolation and 1-tick send latency), and report the minimum eye->hitbox distance
 * across them (lenient, false-positive-safe) along with the maximum (for BackTrack).
 *
 * Fallback (untracked target or engine off): delegate to {@link CombatUtil#analyzeReach} against
 * the target player's client position history, exactly as the legacy checks did, so behaviour is
 * never worse than before while tracking warms up.
 */
public final class CombatRewind {

    private static final double POINT_THREE_EXPANSION = 0.03D;

    private CombatRewind() {}

    public static CombatResult compute(VezAntiCheat plugin, org.bukkit.entity.Player attacker, PlayerData attackerData,
                                       Location eye, int targetEntityId, Entity bukkitTarget,
                                       PlayerData targetData, long attackTime) {
        if (plugin == null) {
            return CombatResult.invalid(attackTime, "no-plugin");
        }
        boolean engineOn = plugin.getConfig().getBoolean("combat-engine.enabled", true);
        TrackedEntity tracked = engineOn && attackerData != null
                ? attackerData.getCompensatedEntities().get(targetEntityId)
                : null;

        if (tracked != null && eye != null && eye.getWorld() != null) {
            CombatResult result = fromTracked(plugin, attacker, attackerData, eye, tracked, attackTime);
            if (result != null) return result;
        }
        return fromLegacy(plugin, attacker, eye, bukkitTarget, targetData, attackTime);
    }

    private static CombatResult fromTracked(VezAntiCheat plugin, org.bukkit.entity.Player attacker, PlayerData attackerData,
                                            Location eye, TrackedEntity tracked, long attackTime) {
        List<TrackedEntity.PositionSnapshot> snapshots = tracked.snapshotsCopy();
        if (snapshots.isEmpty()) {
            return null;
        }

        long lastAcked = attackerData.getTransactionState().getLastAckedSequence();
        int back = Math.max(0, plugin.getConfig().getInt("combat-engine.rewind-bracket-back", 2));
        int forward = Math.max(0, plugin.getConfig().getInt("combat-engine.rewind-bracket-forward", 1));
        long maxRewindMs = Math.max(50L, plugin.getConfig().getLong("combat-engine.max-rewind-ms", 800L));

        double eyeX = eye.getX();
        double eyeY = eye.getY();
        double eyeZ = eye.getZ();

        double min = Double.MAX_VALUE;
        double max = 0.0D;
        TrackedEntity.PositionSnapshot best = null;
        int considered = 0;
        java.util.List<TrackedEntity.PositionSnapshot> bracketSnaps = new java.util.ArrayList<TrackedEntity.PositionSnapshot>();

        boolean haveAck = lastAcked >= 0L;
        for (TrackedEntity.PositionSnapshot s : snapshots) {
            boolean inBracket;
            if (haveAck) {
                inBracket = s.sequence >= (lastAcked - back) && s.sequence <= (lastAcked + forward);
            } else {
                long age = attackTime - s.timeMs;
                inBracket = age >= -50L && age <= maxRewindMs;
            }
            if (!inBracket) continue;

            considered++;
            bracketSnaps.add(s);
            double d = boxDistance(plugin, eyeX, eyeY, eyeZ, s.x, s.y, s.z, s.width, s.height, false);
            if (d < min) {
                min = d;
                best = s;
            }
            if (d > max) {
                max = d;
            }
        }

        // GrimAC ReachInterpolationData analogue: the true on-screen position can fall BETWEEN two
        // discrete per-tick snapshots, so also test interpolated sub-positions across each consecutive
        // in-bracket pair (living entities interpolate over ~3 steps). This only ever lowers the rewound
        // distance — it adds leniency for fast-moving / high-ping targets, never a new flag.
        if (best != null && bracketSnaps.size() >= 2
                && plugin.getConfig().getBoolean("combat-engine.interpolation-enabled", true)) {
            int steps = Math.max(1, plugin.getConfig().getInt("combat-engine.interpolation-steps", 3));
            double interp = interpolatedMinDistance(plugin, eyeX, eyeY, eyeZ, bracketSnaps, steps);
            if (interp < min) min = interp;
        }

        if (best == null) {
            // Bracket empty (e.g., just spawned) — use the latest known position.
            TrackedEntity.PositionSnapshot latest = tracked.latest();
            double d = boxDistance(plugin, eyeX, eyeY, eyeZ, latest.x, latest.y, latest.z, latest.width, latest.height, false);
            min = d;
            max = d;
            best = latest;
            considered = 1;
        }

        TrackedEntity.PositionSnapshot latest = tracked.latest();
        double current = boxDistance(plugin, eyeX, eyeY, eyeZ, latest.x, latest.y, latest.z, latest.width, latest.height, false);

        World world = eye.getWorld();
        Location chosen = new Location(world, best.x, best.y, best.z);
        long pingMs = resolvePing(attacker, attackerData);
        long bracketAge = Math.max(0L, attackTime - best.timeMs);
        double displacement = Math.sqrt(
                (best.x - latest.x) * (best.x - latest.x)
                        + (best.y - latest.y) * (best.y - latest.y)
                        + (best.z - latest.z) * (best.z - latest.z));

        String debug = "tracked rew=" + r(min) + " cur=" + r(current) + " max=" + r(max)
                + " ack=" + lastAcked + " snaps=" + considered
                + " ping=" + pingMs + " age=" + bracketAge + "ms";

        return CombatResult.builder()
                .timeMs(attackTime)
                .tracked(true)
                .rewoundDistance(min)
                .currentDistance(current)
                .minDistance(min)
                .maxDistance(max)
                .displacement(displacement)
                .width(best.width)
                .height(best.height)
                .pingMs(pingMs)
                .bracketAgeMs(bracketAge)
                .snapshotsConsidered(considered)
                .chosenLocation(chosen)
                .debug(debug)
                .build();
    }

    private static CombatResult fromLegacy(VezAntiCheat plugin, org.bukkit.entity.Player attacker, Location eye,
                                           Entity bukkitTarget, PlayerData targetData, long attackTime) {
        if (eye == null || bukkitTarget == null) {
            return CombatResult.invalid(attackTime, "untracked no-target");
        }
        int ping = PingUtil.getPing(attacker);
        long rewindMs = CombatUtil.compensationWindowMs(ping,
                plugin.getConfig().getLong("combat-engine.fallback-rewind-base-ms", 60L),
                plugin.getConfig().getDouble("combat-engine.fallback-rewind-ping-factor", 0.5D),
                plugin.getConfig().getLong("combat-engine.max-rewind-ms", 800L));
        CombatUtil.ReachContext ctx = CombatUtil.analyzeReach(eye, bukkitTarget, targetData, attackTime, rewindMs);
        if (ctx == null) {
            return CombatResult.invalid(attackTime, "untracked no-ctx");
        }
        String debug = "legacy rew=" + r(ctx.getCompensatedDistance()) + " cur=" + r(ctx.getCurrentDistance())
                + " ping=" + ping + " age=" + ctx.getCompensatedAgeMs() + "ms";
        return CombatResult.builder()
                .timeMs(attackTime)
                .tracked(false)
                .rewoundDistance(ctx.getCompensatedDistance())
                .currentDistance(ctx.getCurrentDistance())
                .minDistance(ctx.getCompensatedDistance())
                .maxDistance(ctx.getCurrentDistance())
                .displacement(ctx.getCompensatedDisplacement())
                .width(ctx.getWidth())
                .height(ctx.getHeight())
                .pingMs(ping)
                .bracketAgeMs(ctx.getCompensatedAgeMs())
                .snapshotsConsidered(0)
                .chosenLocation(ctx.getCompensatedLocation())
                .debug(debug)
                .build();
    }

    /**
     * Broad-window reach context over the packet-synced entity positions, for the stale-position
     * checks (BackTrack / Lagrange) that must look BEYOND the legitimate transaction bracket to
     * detect hits landed on an out-of-window position. Returns the closest position within
     * {@code windowMs} along with its age, which is exactly what those checks compare against the
     * allowed rewind. Returns null when the target is not tracked, so the caller falls back to the
     * legacy position-history {@code analyzeReach}.
     */
    public static CombatUtil.ReachContext broadWindowReachContext(VezAntiCheat plugin, org.bukkit.entity.Player attacker,
                                                                  PlayerData attackerData, Location eye,
                                                                  int targetEntityId, long attackTime, long windowMs) {
        if (!plugin.getConfig().getBoolean("combat-engine.enabled", true)) return null;
        if (attackerData == null || eye == null || eye.getWorld() == null) return null;
        TrackedEntity tracked = attackerData.getCompensatedEntities().get(targetEntityId);
        if (tracked == null) return null;
        List<TrackedEntity.PositionSnapshot> snaps = tracked.snapshotsCopy();
        if (snaps.isEmpty()) return null;

        double eyeX = eye.getX();
        double eyeY = eye.getY();
        double eyeZ = eye.getZ();
        TrackedEntity.PositionSnapshot latest = tracked.latest();
        double current = boxDistance(plugin, eyeX, eyeY, eyeZ, latest.x, latest.y, latest.z, latest.width, latest.height, false);

        double best = current;
        long bestAge = 0L;
        double bestDisp = 0.0D;
        TrackedEntity.PositionSnapshot bestSnap = latest;
        for (TrackedEntity.PositionSnapshot s : snaps) {
            long age = attackTime - s.timeMs;
            if (age < -50L || age > windowMs) continue;
            double d = boxDistance(plugin, eyeX, eyeY, eyeZ, s.x, s.y, s.z, s.width, s.height, false);
            if (d + 1.0E-4D < best) {
                best = d;
                bestAge = age;
                bestSnap = s;
                bestDisp = Math.sqrt((s.x - latest.x) * (s.x - latest.x)
                        + (s.y - latest.y) * (s.y - latest.y)
                        + (s.z - latest.z) * (s.z - latest.z));
            }
        }
        Location chosen = new Location(eye.getWorld(), bestSnap.x, bestSnap.y, bestSnap.z);
        return new CombatUtil.ReachContext(current, best, bestAge, bestDisp, bestSnap.width, bestSnap.height, chosen);
    }

    /**
     * Adapt an engine {@link CombatResult} to the legacy {@link CombatUtil.ReachContext} shape so
     * existing combat checks consume the engine's transaction-rewound distances without changing
     * any of their tolerance/buffer logic.
     */
    public static CombatUtil.ReachContext toReachContext(CombatResult r) {
        if (r == null || !r.valid) return null;
        return new CombatUtil.ReachContext(
                r.currentDistance,
                r.rewoundDistance,
                r.bracketAgeMs,
                r.displacement,
                r.width,
                r.height,
                r.getChosenLocation());
    }

    private static long resolvePing(org.bukkit.entity.Player attacker, PlayerData attackerData) {
        if (attackerData != null) {
            long txPing = attackerData.getTransactionState().getTransactionPingMs();
            if (txPing >= 0L) return txPing;
        }
        return Math.max(0, PingUtil.getPing(attacker));
    }

    /**
     * Minimum distance from the eye point to the entity's expanded AABB. Mirrors
     * {@link CombatUtil#distanceToHitbox} with symmetric vanilla combat expansion.
     */
    /**
     * Minimum eye-&gt;hitbox distance across positions interpolated BETWEEN consecutive in-bracket
     * snapshots (GrimAC ReachInterpolationData analogue). Only the interior fractions are sampled —
     * the snapshot endpoints are already scanned discretely — so this can only lower the rewound
     * distance, adding leniency for targets moving between server ticks.
     */
    private static double interpolatedMinDistance(VezAntiCheat plugin, double eyeX, double eyeY, double eyeZ,
                                                  java.util.List<TrackedEntity.PositionSnapshot> snaps, int steps) {
        snaps.sort(java.util.Comparator.comparingLong(s -> s.sequence));
        double min = Double.MAX_VALUE;
        for (int i = 0; i + 1 < snaps.size(); i++) {
            TrackedEntity.PositionSnapshot a = snaps.get(i);
            TrackedEntity.PositionSnapshot b = snaps.get(i + 1);
            for (int k = 1; k < steps; k++) {
                double t = k / (double) steps;
                double x = a.x + (b.x - a.x) * t;
                double y = a.y + (b.y - a.y) * t;
                double z = a.z + (b.z - a.z) * t;
                double w = a.width + (b.width - a.width) * t;
                double h = a.height + (b.height - a.height) * t;
                double d = boxDistance(plugin, eyeX, eyeY, eyeZ, x, y, z, w, h, false);
                if (d < min) min = d;
            }
        }
        return min;
    }

    private static double boxDistance(VezAntiCheat plugin,
                                      double eyeX, double eyeY, double eyeZ,
                                      double x, double y, double z, double width, double height,
                                      boolean pointThreePossible) {
        double expansion = CombatUtil.vanillaExpansion(plugin)
                + (pointThreePossible ? POINT_THREE_EXPANSION : 0.0D);
        return CombatUtil.buildCombatAabb(x, y, z, width, height, expansion)
                .distanceTo(new org.bukkit.util.Vector(eyeX, eyeY, eyeZ));
    }

    private static double r(double v) {
        return Math.round(v * 1000.0D) / 1000.0D;
    }
}
