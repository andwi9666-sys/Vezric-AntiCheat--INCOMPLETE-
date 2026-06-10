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
            double d = boxDistance(plugin, eyeX, eyeY, eyeZ, s.x, s.y, s.z, s.width, s.height, false);
            if (d < min) {
                min = d;
                best = s;
            }
            if (d > max) {
                max = d;
            }
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
            return CombatResult.builder().timeMs(attackTime).tracked(false).debug("untracked no-target").build();
        }
        int ping = PingUtil.getPing(attacker);
        long rewindMs = CombatUtil.compensationWindowMs(ping,
                plugin.getConfig().getLong("combat-engine.fallback-rewind-base-ms", 60L),
                plugin.getConfig().getDouble("combat-engine.fallback-rewind-ping-factor", 0.5D),
                plugin.getConfig().getLong("combat-engine.max-rewind-ms", 800L));
        CombatUtil.ReachContext ctx = CombatUtil.analyzeReach(eye, bukkitTarget, targetData, attackTime, rewindMs);
        if (ctx == null) {
            return CombatResult.builder().timeMs(attackTime).tracked(false).debug("untracked no-ctx").build();
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
        if (r == null) return null;
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
