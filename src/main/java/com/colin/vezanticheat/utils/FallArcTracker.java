package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Tracks multi-block falls (walk-off, jump-off ledge, sprint drop) so prediction checks can
 * exempt legitimate landing arcs where the engine under-predicts vertical motion.
 */
public final class FallArcTracker {

    private FallArcTracker() {}

    /** Snapshot of movement-analysis fall settings used by tests in this package. */
    static final class Settings {
        final double minDrop;
        final long landGraceMs;
        final long fallWindowMs;
        final long fallDamageGraceMs;

        Settings(double minDrop, long landGraceMs, long fallWindowMs, long fallDamageGraceMs) {
            this.minDrop = minDrop;
            this.landGraceMs = landGraceMs;
            this.fallWindowMs = fallWindowMs;
            this.fallDamageGraceMs = fallDamageGraceMs;
        }

        static Settings defaults() {
            return new Settings(0.75D, 750L, 2800L, 800L);
        }

        static Settings from(VezAntiCheat plugin) {
            if (plugin == null) return defaults();
            long damageGraceMs = plugin.getConfig().getLong("movement-analysis.fall-damage-setback-grace-ms", 800L);
            long flyDamageGraceMs = plugin.tierCfg().checkLong("PredictionFly", "damageCooldownMs", 500L);
            return new Settings(
                    plugin.getConfig().getDouble("movement-analysis.fall-arc-min-drop", 0.75D),
                    plugin.getConfig().getLong("movement-analysis.fall-land-grace-ms", 750L),
                    plugin.getConfig().getLong("movement-analysis.fall-arc-window-ms", 2800L),
                    Math.max(damageGraceMs, flyDamageGraceMs));
        }
    }

    public static void observe(VezAntiCheat plugin, Player p, PlayerData data,
                               Location from, Location to, boolean clientGround, long nowMs) {
        if (plugin == null || data == null || from == null || to == null) return;
        observe(data, from, to, clientGround, nowMs, Settings.from(plugin));
    }

    static void observe(PlayerData data, Location from, Location to, boolean clientGround,
                        long nowMs, Settings settings) {
        if (data == null || from == null || to == null || settings == null) return;
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;

        boolean fromGround = isGrounded(from, data, true);
        boolean toGround = isGrounded(to, data, clientGround);
        boolean airBelow = isAirBelow(to);

        boolean serverFromGround = isServerGround(from);
        boolean serverToGround = isServerGround(to);
        boolean walkingOffLedge = serverFromGround && airBelow && !serverToGround
                && (to.getY() < from.getY() - 0.01D || !toGround);

        if (walkingOffLedge || (fromGround && airBelow && !serverToGround && !toGround)) {
            data.setFallArcPeakY(from.getY());
            data.setFallArcMinY(to.getY());
            data.setFallArcStartMs(nowMs);
            data.setFallArcLandMs(0L);
            data.setFallArcActive(true);
        } else if (data.isFallArcActive() || data.getFallArcStartMs() > 0L) {
            data.setFallArcPeakY(Math.max(data.getFallArcPeakY(), Math.max(from.getY(), to.getY())));
            data.setFallArcMinY(Math.min(data.getFallArcMinY(), Math.min(from.getY(), to.getY())));

            double minDrop = settings.minDrop;
            boolean stillHighDescent = airBelow && to.getY() < data.getFallArcPeakY() - minDrop * 0.4D;
            double dy = to.getY() - from.getY();

            if (serverToGround) {
                data.setFallArcLandMs(nowMs);
                data.setFallArcActive(false);
            } else if (toGround && !stillHighDescent && Math.abs(dy) <= 0.08D) {
                data.setFallArcLandMs(nowMs);
                data.setFallArcActive(false);
            }
        }

        if (data.getFallArcLandMs() > 0L && nowMs - data.getFallArcLandMs() > settings.landGraceMs) {
            data.clearFallArc();
        }

        if (data.getFallArcStartMs() > 0L && !data.isFallArcActive()
                && data.getFallArcLandMs() <= 0L
                && nowMs - data.getFallArcStartMs() > settings.fallWindowMs) {
            data.clearFallArc();
        }
    }

    public static boolean isInFallArcWindow(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (plugin == null || data == null) return false;
        return isInFallArcWindow(data, nowMs, Settings.from(plugin));
    }

    static boolean isInFallArcWindow(PlayerData data, long nowMs, Settings settings) {
        if (data == null || settings == null || data.getFallArcStartMs() <= 0L) return false;

        if (data.isFallArcActive()) return true;
        if (nowMs - data.getFallArcStartMs() <= settings.fallWindowMs) return true;
        if (data.getFallArcLandMs() > 0L && nowMs - data.getFallArcLandMs() <= settings.landGraceMs) return true;
        return isStillInDescentFallArc(data, settings);
    }

    /**
     * Suppress movement setbacks after legit falls. NoFall cheats avoid fall damage; legit players take it.
     * Also covers the landing grace window before damage is applied.
     */
    public static boolean shouldSuppressLegitFallSetback(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (plugin == null || data == null) return false;
        return shouldSuppressLegitFallSetback(data, nowMs, Settings.from(plugin));
    }

    static boolean shouldSuppressLegitFallSetback(PlayerData data, long nowMs, Settings settings) {
        if (data == null || settings == null) return false;

        if (data.getLastFallDamageTime() > 0L
                && (nowMs - data.getLastFallDamageTime()) <= settings.fallDamageGraceMs) {
            return true;
        }

        if (data.getFallArcLandMs() > 0L) {
            if (nowMs - data.getFallArcLandMs() <= settings.landGraceMs) {
                double drop = Math.max(0.0D, data.getFallArcPeakY() - data.getFallArcMinY());
                return drop >= settings.minDrop;
            }
        }

        return isActiveLegitFallDescent(data, settings);
    }

    /**
     * Broader prediction-tier grace for normal falls. This runs before checks build buffers,
     * while {@link #shouldSuppressLegitFallSetback} is the final enforcement veto.
     */
    public static boolean shouldSuppressLegitFallMovement(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (plugin == null || data == null) return false;
        return shouldSuppressLegitFallMovement(data, nowMs, Settings.from(plugin));
    }

    static boolean shouldSuppressLegitFallMovement(PlayerData data, long nowMs, Settings settings) {
        if (data == null || settings == null) return false;
        if (shouldSuppressLegitFallSetback(data, nowMs, settings)) return true;

        long landAt = data.getNoFallALandAtMs();
        if (landAt > 0L && nowMs - landAt <= settings.landGraceMs) {
            double fall = data.getNoFallALandFall();
            if (fall >= settings.minDrop) return true;
        }

        long expectedDamage = data.getNoFallAExpectedDamageAfterMs();
        if (expectedDamage > 0L
                && nowMs >= landAt
                && nowMs <= expectedDamage + settings.fallDamageGraceMs) {
            double fall = data.getNoFallALandFall();
            if (fall >= settings.minDrop) return true;
        }

        return isInFallArcWindow(data, nowMs, settings);
    }

    /** True while a tracked fall arc is still descending with meaningful drop. */
    private static boolean isActiveLegitFallDescent(PlayerData data, Settings settings) {
        if (data == null || settings == null || data.getFallArcStartMs() <= 0L) return false;

        double drop = Math.max(0.0D, data.getFallArcPeakY() - data.getFallArcMinY());
        if (drop < settings.minDrop * 0.4D) return false;

        if (data.isFallArcActive()) return true;
        return isStillInDescentFallArc(data, settings);
    }

    /** Extended fall window for long descents still above the landing zone. */
    private static boolean isStillInDescentFallArc(PlayerData data, Settings settings) {
        if (data == null || settings == null || data.getFallArcStartMs() <= 0L) return false;

        double drop = Math.max(0.0D, data.getFallArcPeakY() - data.getFallArcMinY());
        if (drop < settings.minDrop * 0.4D) return false;

        Location loc = data.getLastLoc();
        if (loc == null || loc.getWorld() == null) return false;
        if (!isAirBelow(loc)) return false;
        return loc.getY() < data.getFallArcPeakY() - settings.minDrop * 0.4D;
    }

    private static boolean isInFallArcLandingGrace(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (plugin == null || data == null || data.getFallArcLandMs() <= 0L) return false;
        long landGrace = plugin.getConfig().getLong("movement-analysis.fall-land-grace-ms", 750L);
        return nowMs - data.getFallArcLandMs() <= landGrace;
    }

    public static boolean isLikelyLegitFallArc(VezAntiCheat plugin, PlayerData data,
                                              EngineResult er, long nowMs) {
        if (plugin == null || data == null || er == null) return false;
        if (er.knockbackTick || er.explosionTick || er.inWater || er.onClimbable || er.inWeb) return false;
        if (data.getFallArcStartMs() <= 0L) return false;

        if (!isInFallArcWindow(plugin, data, nowMs)) return false;

        double drop = Math.max(0.0D, data.getFallArcPeakY() - data.getFallArcMinY());
        double minDrop = plugin.getConfig().getDouble("movement-analysis.fall-arc-min-drop", 0.75D);
        World world = resolveWorld(data);
        boolean noFallDamage = world != null && !NoFallUtil.isFallDamageEnabled(plugin, world);
        if (drop < minDrop * (noFallDamage ? 0.4D : 1.0D)) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());

        if (noFallDamage) {
            double maxH = plugin.getConfig().getDouble("movement-analysis.fall-arc-max-horizontal", 1.12D);
            if (dy <= 0.55D && (dy < -0.01D || er.clientGround || data.getFallArcLandMs() > 0L)) {
                return distH <= maxH * 1.5D;
            }
            return false;
        }

        boolean landingGrace = isInFallArcLandingGrace(plugin, data, nowMs);
        double maxOffset = landingGrace
                ? plugin.getConfig().getDouble("movement-analysis.fall-arc-landing-max-offset", 0.85D)
                : plugin.getConfig().getDouble("movement-analysis.fall-arc-max-offset", 0.62D);
        if (er.offset > maxOffset) return false;

        if (landingGrace && er.clientGround && dy <= 0.18D) {
            double maxH = plugin.getConfig().getDouble("movement-analysis.fall-arc-max-horizontal", 1.12D);
            return distH <= maxH * 1.25D;
        }

        if (Math.abs(dy) < 0.03D && !er.clientGround && !er.predictedOnGround && !landingGrace) return false;

        double maxH = plugin.getConfig().getDouble("movement-analysis.fall-arc-max-horizontal", 1.12D);
        if (landingGrace) {
            maxH *= 1.25D;
        }
        double minDy = plugin.getConfig().getDouble("movement-analysis.fall-arc-min-dy", -1.05D);
        double maxDy = plugin.getConfig().getDouble("movement-analysis.fall-arc-max-dy", 0.42D);

        return dy >= minDy && dy <= maxDy && distH <= maxH;
    }

    private static World resolveWorld(PlayerData data) {
        Location loc = data == null ? null : data.getLastLoc();
        return loc == null ? null : loc.getWorld();
    }

    private static boolean isGrounded(Location loc, PlayerData data, boolean clientGround) {
        if (isServerGround(loc)) return true;
        return clientGround && data != null && data.wasLastClientGround();
    }

    private static boolean isServerGround(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World world = loc.getWorld();
        for (double ox = -0.3; ox <= 0.3; ox += 0.3) {
            for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
                Location probe = loc.clone().add(ox, -0.1, oz);
                org.bukkit.Material below = world.getBlockAt(
                        probe.getBlockX(), probe.getBlockY(), probe.getBlockZ()).getType();
                if (below.isSolid() || below.name().contains("FENCE") || below.name().contains("WALL")
                        || below.name().contains("STEP") || below.name().contains("STAIRS")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAirBelow(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Location below = loc.clone().subtract(0.0, 1.0, 0.0);
        org.bukkit.Material type = loc.getWorld().getBlockAt(
                below.getBlockX(), below.getBlockY(), below.getBlockZ()).getType();
        return type == org.bukkit.Material.AIR;
    }
}
