package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Detects solid-wall spider climbing: airborne wall collision with sustained upward motion.
 */
public final class SpiderUtil {

    private SpiderUtil() {}

    public static boolean shouldExempt(VezAntiCheat plugin, Player p, PlayerData data,
                                       EngineResult er, long nowMs) {
        return shouldExempt(plugin, p, data, er, nowMs, TickSettings.from(plugin));
    }

    static boolean shouldExempt(VezAntiCheat plugin, Player p, PlayerData data,
                                EngineResult er, long nowMs, TickSettings settings) {
        if (data == null || er == null) return true;
        if (isWallSpiderMotion(er, data, p, settings)) return false;
        if (er.onClimbable || er.inWater || er.inWeb) return true;
        if (er.knockbackTick || er.explosionTick) return true;
        if (EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) return true;
        if (data.isFallArcActive() || FallArcTracker.isInFallArcWindow(plugin, data, nowMs)) return true;
        if (data.isVelocityExempt()) return true;

        long sinceJump = data.getLastJumpTime() > 0L ? nowMs - data.getLastJumpTime() : Long.MAX_VALUE;
        long postJumpGrace = settings == null ? 150L : settings.postJumpGraceMs;
        return sinceJump <= postJumpGrace;
    }

    static final class TickSettings {
        final double minAscendDy;
        final long jumpArcWindowMs;
        final long postJumpGraceMs;

        TickSettings(double minAscendDy, long jumpArcWindowMs, long postJumpGraceMs) {
            this.minAscendDy = minAscendDy;
            this.jumpArcWindowMs = jumpArcWindowMs;
            this.postJumpGraceMs = postJumpGraceMs;
        }

        static TickSettings defaults() {
            return new TickSettings(0.05D, 950L, 150L);
        }

        static TickSettings from(VezAntiCheat plugin) {
            if (plugin == null) return defaults();
            return new TickSettings(
                    plugin.getConfig().getDouble("spider-analysis.min-ascend-dy", 0.05D),
                    plugin.getConfig().getLong("movement-analysis.jump-arc-window-ms", 950L),
                    plugin.getConfig().getLong("spider-analysis.post-jump-grace-ms", 150L));
        }
    }

    public static boolean isWallSpiderMotion(EngineResult er, PlayerData data, Player p) {
        return isWallSpiderMotion(er, data, p, TickSettings.defaults());
    }

    static boolean isWallSpiderMotion(EngineResult er, PlayerData data, Player p, TickSettings settings) {
        if (er == null || !er.checked || settings == null) return false;
        if (er.onClimbable || er.inWater || er.inWeb) return false;
        if (!er.collisionX && !er.collisionZ) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        if (actual.getY() < settings.minAscendDy) return false;

        Location loc = data != null ? data.getLastLoc() : null;
        if (loc == null && p != null) loc = p.getLocation();
        return hasAdjacentSolidWall(loc);
    }

    public static boolean isSpiderAscendTick(VezAntiCheat plugin, Player p, PlayerData data,
                                             EngineResult er, long nowMs) {
        if (plugin == null || data == null || er == null || !er.checked) return false;
        return isSpiderAscendTick(plugin, p, data, er, nowMs, TickSettings.from(plugin));
    }

    static boolean isSpiderAscendTick(VezAntiCheat plugin, Player p, PlayerData data,
                                      EngineResult er, long nowMs, TickSettings settings) {
        if (data == null || er == null || !er.checked || settings == null) return false;
        if (shouldExempt(plugin, p, data, er, nowMs, settings)) return false;
        if (er.clientGround && !er.collisionX && !er.collisionZ) return false;
        if (er.predictedOnGround && !er.collisionX && !er.collisionZ) return false;
        return isWallSpiderMotion(er, data, p, settings);
    }

    public static void observe(PlayerData data, boolean spiderTick) {
        if (data == null) return;
        if (spiderTick) {
            data.setSpiderAscendStreak(data.getSpiderAscendStreak() + 1);
        } else {
            data.setSpiderAscendStreak(Math.max(0, data.getSpiderAscendStreak() - 1));
        }
    }

    public static boolean hasAdjacentSolidWall(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        if (isSolidWallBlock(loc.getWorld().getBlockAt(bx, by, bz))
                || isSolidWallBlock(loc.getWorld().getBlockAt(bx, by + 1, bz))) {
            return true;
        }

        for (int dy = 0; dy <= 1; dy++) {
            if (isSolidWallBlock(loc.getWorld().getBlockAt(bx + 1, by + dy, bz))) return true;
            if (isSolidWallBlock(loc.getWorld().getBlockAt(bx - 1, by + dy, bz))) return true;
            if (isSolidWallBlock(loc.getWorld().getBlockAt(bx, by + dy, bz + 1))) return true;
            if (isSolidWallBlock(loc.getWorld().getBlockAt(bx, by + dy, bz - 1))) return true;
        }
        return false;
    }

    private static boolean isSolidWallBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type == null || type == Material.AIR) return false;
        if (type == Material.LADDER || type == Material.VINE) return false;
        String name = type.name();
        if (name.contains("LADDER") || name.contains("VINE")) return false;
        return type.isSolid();
    }
}
