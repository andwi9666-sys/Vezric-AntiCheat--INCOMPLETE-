package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public final class NoFallUtil {
    private NoFallUtil() {}

    /**
     * Whether the server applies fall damage. NoFall checks must not expect damage when this is false.
     * Uses gamerule on supported versions; override with {@code nofall.fall-damage-enabled} in config.
     */
    public static boolean isFallDamageEnabled(VezAntiCheat plugin, World world) {
        if (plugin != null && world != null && isConfiguredNoFallDamageWorld(plugin, world.getName())) {
            return false;
        }
        if (plugin != null) {
            String mode = plugin.getConfig().getString("nofall.fall-damage-enabled", "auto");
            if ("true".equalsIgnoreCase(mode)) return true;
            if ("false".equalsIgnoreCase(mode)) return false;
        }
        if (world == null) return true;
        try {
            java.lang.reflect.Method method = world.getClass().getMethod("getGameRuleValue", String.class);
            for (String rule : new String[] {"fallDamage", "doFallDamage"}) {
                Object value = method.invoke(world, rule);
                if (value instanceof String) {
                    return !"false".equalsIgnoreCase((String) value);
                }
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static boolean shouldSkipNoFallExpectations(VezAntiCheat plugin, Player p) {
        if (p == null) return true;
        return shouldSkipNoFallExpectations(plugin, p.getWorld());
    }

    public static boolean shouldSkipNoFallExpectations(VezAntiCheat plugin, World world) {
        return !isFallDamageEnabled(plugin, world);
    }

    /**
     * Exempt fall-arc / landing prediction flags when fall damage is off for this world.
     * Does not disable speed, phase, or unrelated fly-cheat detection.
     */
    public static boolean shouldExemptFallDamagePrediction(VezAntiCheat plugin, Player p,
                                                           PlayerData data, EngineResult er,
                                                           long nowMs) {
        if (plugin == null || p == null || data == null || er == null) return false;
        if (!shouldSkipNoFallExpectations(plugin, p)) return false;
        if (er.knockbackTick || er.explosionTick || er.inWater || er.onClimbable || er.inWeb) {
            return false;
        }

        if (FallArcTracker.isLikelyLegitFallArc(plugin, data, er, nowMs)) return true;

        if (FallArcTracker.isInFallArcWindow(plugin, data, nowMs)) {
            double drop = Math.max(0.0D, data.getFallArcPeakY() - data.getFallArcMinY());
            double minDrop = plugin.getConfig().getDouble("movement-analysis.fall-arc-min-drop", 1.25D);
            if (drop >= minDrop * 0.4D) {
                Vector actual = er.actual == null ? new Vector() : er.actual;
                if (actual.getY() <= 0.55D) return true;
            }
        }

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        if (dy < -0.12D && !er.predictedOnGround && data.getEngineAirborneTicks() >= 2) {
            Context ctx = analyze(plugin, p, data);
            return ctx != null && (ctx.blockBelowAir || !ctx.onGround);
        }
        return false;
    }

    private static boolean isConfiguredNoFallDamageWorld(VezAntiCheat plugin, String worldName) {
        if (plugin == null || worldName == null || worldName.isEmpty()) return false;
        List<String> worlds = plugin.getConfig().getStringList("nofall.no-damage-worlds");
        if (worlds == null || worlds.isEmpty()) return false;
        for (String configured : worlds) {
            if (configured != null && configured.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    public static Context analyze(VezAntiCheat plugin, Player p, PlayerData data) {
        if (plugin == null || p == null || data == null) return null;

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return null;
        if (from.getWorld() == null || to.getWorld() == null) return null;
        if (!from.getWorld().equals(to.getWorld())) return null;

        double dy = to.getY() - from.getY();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distH = Math.hypot(dx, dz);

        boolean serverGround = isServerGround(to) || isServerGround(from);
        boolean clientGround = data.wasLastClientGround();
        boolean onGround = serverGround || (clientGround && Math.abs(dy) <= 0.08);

        boolean inLiquid = isLiquid(to) || isLiquid(from);
        boolean climbable = isClimbable(to) || isClimbable(from);
        boolean inWeb = isWeb(to) || isWeb(from);
        boolean onSlime = isSlime(to) || isSlime(from);
        boolean onBed = isBed(to) || isBed(from);
        boolean weirdSurface = isWeirdSurface(to) || isWeirdSurface(from);
        boolean softLanding = onSlime || onBed || inLiquid || climbable || inWeb;
        boolean blockBelowAir = isAirBelow(to);

        return new Context(from, to, dy, distH, serverGround, clientGround, onGround, inLiquid,
                climbable, inWeb, onSlime, onBed, weirdSurface, softLanding, blockBelowAir);
    }

    public static boolean shouldSkip(Player p, PlayerData data) {
        if (p == null || data == null) return true;
        if (p.isFlying() || p.getAllowFlight()) return true;
        if (p.isInsideVehicle()) return true;
        if (data.isTeleportExempt() || data.isVelocityExempt() || data.isBlockStateExempt()) return true;
        return false;
    }

    /**
     * Vanilla 1.8 fall damage from fall distance (blocks), before armor absorption.
     */
    public static double expectedFallDamage(Player p, double fallDistance) {
        if (fallDistance <= 3.0D) return 0.0D;
        double damage = fallDistance - 3.0D;
        if (p != null && p.getInventory().getBoots() != null) {
            int feather = p.getInventory().getBoots()
                    .getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION_FALL);
            if (feather > 0) {
                damage *= Math.max(0.0D, 1.0D - (feather * 0.12D));
            }
        }
        return Math.max(0.0D, damage);
    }

    public static boolean damageMatchesExpected(double expected, double actual, double tolerance) {
        if (expected <= 0.0D) return actual <= tolerance;
        if (actual <= 0.0D) return false;
        return actual >= expected - tolerance && actual <= expected + tolerance + 0.5D;
    }

    public static double serverFallDistance(PlayerData data) {
        if (data == null || !data.hasNoFallAPeakY()) return 0.0D;
        org.bukkit.Location loc = data.getLastLoc();
        double landY = loc == null ? data.getNoFallAPeakY() : loc.getY();
        return Math.max(0.0D, data.getNoFallAPeakY() - landY);
    }

    private static boolean isServerGround(Location loc) {
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

    private static boolean isAirBelow(Location loc) {
        Material below = loc.clone().subtract(0, 1.0, 0).getBlock().getType();
        return below == Material.AIR;
    }

    private static boolean isLiquid(Location loc) {
        Material t = loc.getBlock().getType();
        return t == Material.WATER || t == Material.STATIONARY_WATER
                || t == Material.LAVA || t == Material.STATIONARY_LAVA;
    }

    private static boolean isClimbable(Location loc) {
        Material t = loc.getBlock().getType();
        return t == Material.LADDER || t == Material.VINE;
    }

    private static boolean isWeb(Location loc) {
        return loc.getBlock().getType() == Material.WEB;
    }

    private static boolean isSlime(Location loc) {
        return loc.clone().subtract(0, 1, 0).getBlock().getType() == Material.SLIME_BLOCK;
    }

    private static boolean isBed(Location loc) {
        Material feet = loc.getBlock().getType();
        Material below = loc.clone().subtract(0, 1, 0).getBlock().getType();
        return feet == Material.BED_BLOCK || below == Material.BED_BLOCK;
    }

    private static boolean isWeirdSurface(Location loc) {
        Block below = loc.clone().subtract(0, 1, 0).getBlock();
        if (below == null) return false;
        Material t = below.getType();
        String name = t.name();
        return name.contains("STEP") || name.contains("SLAB") || name.contains("STAIRS");
    }

    public static final class Context {
        public final Location from;
        public final Location to;
        public final double dy;
        public final double distH;
        public final boolean serverGround;
        public final boolean clientGround;
        public final boolean onGround;
        public final boolean inLiquid;
        public final boolean climbable;
        public final boolean inWeb;
        public final boolean onSlime;
        public final boolean onBed;
        public final boolean weirdSurface;
        public final boolean softLanding;
        public final boolean blockBelowAir;

        public Context(Location from, Location to, double dy, double distH, boolean serverGround, boolean clientGround,
                       boolean onGround, boolean inLiquid, boolean climbable, boolean inWeb, boolean onSlime,
                       boolean onBed, boolean weirdSurface, boolean softLanding, boolean blockBelowAir) {
            this.from = from;
            this.to = to;
            this.dy = dy;
            this.distH = distH;
            this.serverGround = serverGround;
            this.clientGround = clientGround;
            this.onGround = onGround;
            this.inLiquid = inLiquid;
            this.climbable = climbable;
            this.inWeb = inWeb;
            this.onSlime = onSlime;
            this.onBed = onBed;
            this.weirdSurface = weirdSurface;
            this.softLanding = softLanding;
            this.blockBelowAir = blockBelowAir;
        }
    }
}
