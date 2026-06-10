package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
public final class FlyUtil {
    private FlyUtil() {}

    public static Context analyze(VezAntiCheat plugin, Player p, PlayerData data) {
        if (plugin == null || p == null || data == null) return null;

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return null;
        if (from.getWorld() == null || to.getWorld() == null) return null;
        if (!from.getWorld().equals(to.getWorld())) return null;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distH = Math.hypot(dx, dz);

        long intervalMs = data.getLastFlyingIntervalMs();
        double ticks = Math.max(1.0, Math.min(4.0, intervalMs <= 0L ? 1.0 : intervalMs / 50.0));

        boolean serverGround = isServerGround(to) || isServerGround(from);
        boolean clientGround = data.wasLastClientGround();
        boolean onGround = serverGround || (clientGround && Math.abs(dy) <= 0.08);

        boolean inLiquid = isInLiquid(to) || isInLiquid(from);
        boolean onSlime = isOnSlime(to) || isOnSlime(from);
        boolean climbable = isClimbable(to) || isClimbable(from);
        boolean inWeb = isWeb(to) || isWeb(from);
        boolean weirdSurface = isWeirdSurface(to) || isWeirdSurface(from);
        boolean lowCeiling = hasLowCeiling(to) || hasLowCeiling(from);
        boolean airBelow = isAirBelow(to, 0.7) && isAirBelow(to, 1.4);
        boolean blockNearby = hasNearbySolid(to, 0.45, 0.0) || hasNearbySolid(to, 0.45, 1.0);

        int jumpAmp = PotionUtil.jumpBoostLevel(p);
        double maxJumpRise = 0.42 + (jumpAmp * 0.10) + Math.min(0.05, (ticks - 1.0) * 0.04);

        return new Context(from, to, distH, dy, ticks, onGround, inLiquid, onSlime, climbable,
                inWeb, weirdSurface, lowCeiling, airBelow, blockNearby, maxJumpRise);
    }

    public static double expectedNextDy(double prevDy) {
        return (prevDy - 0.08) * 0.98;
    }

    /**
     * Engine-backed fly grace: jumps, vanilla step-ups, slab edges, and small drops often
     * mismatch a single predicted vector even when movement is legitimate.
     */
    public static boolean engineVerticalGrace(VezAntiCheat plugin, Player p, PlayerData data,
                                              double verticalOffset, double dy, double distH,
                                              boolean clientGround, String checkName) {
        if (plugin == null || p == null || data == null) return false;

        if (Math.abs(dy) < 0.03D && !clientGround && data.getEngineHoverTicks() >= 4) {
            return false;
        }

        double blatantOffset = CheckConfigUtil.checkDouble(plugin,checkName, "engineBlatantVerticalOffset", 0.28D);
        if (verticalOffset >= blatantOffset) {
            return false;
        }

        Context ctx = analyze(plugin, p, data);
        long now = System.currentTimeMillis();
        long jumpWindow = plugin.getConfig().getLong("movement-analysis.jump-phase-window-ms", 420L);
        boolean recentJump = now - data.getLastJumpTime() <= Math.max(0L, jumpWindow);

        if (recentJump || (ctx != null && isLikelyLegitJumpPhase(plugin, data, ctx))) {
            double minDy = CheckConfigUtil.checkDouble(plugin,checkName, "engineJumpGraceMinDy", -0.22D);
            double maxDy = CheckConfigUtil.checkDouble(plugin,checkName, "engineJumpGraceMaxDy", 0.52D);
            double maxH = CheckConfigUtil.checkDouble(plugin,checkName, "engineJumpGraceMaxHorizontal", 0.95D);
            double maxOff = CheckConfigUtil.checkDouble(plugin,checkName, "engineJumpGraceMaxOffset", 0.32D);
            if (dy >= minDy && dy <= maxDy && distH <= maxH && verticalOffset <= maxOff) {
                return true;
            }
        }

        if (MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data)) {
            double maxOff = CheckConfigUtil.checkDouble(plugin,checkName, "engineJumpGraceMaxOffset", 0.52D);
            if (verticalOffset <= maxOff && dy >= -0.78D && dy <= 0.55D) {
                return true;
            }
        }

        long arcWindow = plugin.getConfig().getLong("movement-analysis.jump-arc-window-ms", 950L);
        if (data.getLastJumpTime() > 0L && now - data.getLastJumpTime() <= arcWindow) {
            double arcMaxOff = plugin.getConfig().getDouble("movement-analysis.jump-arc-max-offset", 0.52D);
            double arcMinDy = plugin.getConfig().getDouble("movement-analysis.jump-arc-min-dy", -0.82D);
            double arcMaxDy = plugin.getConfig().getDouble("movement-analysis.jump-arc-max-dy", 0.55D);
            double arcMaxH = plugin.getConfig().getDouble("movement-analysis.jump-arc-max-horizontal", 1.08D);
            if (verticalOffset <= arcMaxOff && dy >= arcMinDy && dy <= arcMaxDy && distH <= arcMaxH) {
                return true;
            }
        }

        if (isLikelyLegitStepMotion(plugin, checkName, dy, distH, ctx, clientGround)) {
            double maxOff = CheckConfigUtil.checkDouble(plugin,checkName, "engineStepGraceMaxOffset", 0.30D);
            if (verticalOffset <= maxOff) {
                return true;
            }
        }

        if (isLikelyLegitDropMotion(plugin, checkName, dy, distH, ctx, clientGround)) {
            double maxOff = CheckConfigUtil.checkDouble(plugin,checkName, "engineDropGraceMaxOffset", 0.28D);
            if (verticalOffset <= maxOff) {
                return true;
            }
        }

        double softMax = CheckConfigUtil.checkDouble(plugin,checkName, "engineGraceMaxOffset", 0.18D);
        if (verticalOffset <= softMax && ctx != null
                && (ctx.weirdSurface || ctx.blockNearby || ctx.onSlime)
                && dy >= -0.55D && dy <= 0.55D) {
            return true;
        }

        return false;
    }

    public static boolean isLikelyLegitStepMotion(VezAntiCheat plugin, String checkName, double dy, double distH,
                                                  Context ctx, boolean clientGround) {
        if (plugin == null) return false;
        String cfg = checkName == null ? "FlyPrediction" : checkName;
        double stepMin = CheckConfigUtil.checkDouble(plugin,cfg, "engineStepMinDy", 0.12D);
        double stepMax = CheckConfigUtil.checkDouble(plugin,cfg, "engineStepMaxDy", 0.62D);
        double stepMaxH = CheckConfigUtil.checkDouble(plugin,cfg, "engineStepMaxHorizontal", 0.85D);
        if (dy >= stepMin && dy <= stepMax && distH <= stepMaxH) {
            return ctx == null || ctx.weirdSurface || ctx.blockNearby || clientGround || ctx.onGround
                    || EngineMovementGrace.hasStepSurfaceNear(ctx.to)
                    || EngineMovementGrace.hasStepSurfaceNear(ctx.from);
        }
        double slabMin = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabMinDy", 0.015D);
        double slabMax = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabMaxDy", 0.40D);
        double slabMaxH = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabMaxHorizontal", 0.85D);
        if (dy >= slabMin && dy <= slabMax && distH >= 0.01D && distH <= slabMaxH) {
            return ctx != null && (ctx.weirdSurface || ctx.blockNearby || clientGround
                    || EngineMovementGrace.hasStepSurfaceNear(ctx.to)
                    || EngineMovementGrace.hasStepSurfaceNear(ctx.from));
        }
        return false;
    }

    public static boolean isLikelyLegitDropMotion(VezAntiCheat plugin, String checkName, double dy, double distH,
                                                  Context ctx, boolean clientGround) {
        if (plugin == null) return false;
        String cfg = checkName == null ? "FlyPrediction" : checkName;
        double dropMin = CheckConfigUtil.checkDouble(plugin,cfg, "engineDropMinDy", -0.65D);
        double dropMax = CheckConfigUtil.checkDouble(plugin,cfg, "engineDropMaxDy", 0.06D);
        double dropMaxH = CheckConfigUtil.checkDouble(plugin,cfg, "engineDropMaxHorizontal", 0.85D);
        if (dy < dropMin || dy > dropMax || distH > dropMaxH) {
            return false;
        }
        return clientGround || ctx == null || ctx.weirdSurface || ctx.blockNearby
                || EngineMovementGrace.hasStepSurfaceNear(ctx == null ? null : ctx.to)
                || EngineMovementGrace.hasStepSurfaceNear(ctx == null ? null : ctx.from)
                || !ctx.airBelow;
    }

    public static boolean isLikelyLegitJumpPhase(VezAntiCheat plugin, PlayerData data, Context ctx) {
        if (plugin == null || data == null || ctx == null) return false;
        long now = System.currentTimeMillis();
        long windowMs = plugin.getConfig().getLong("movement-analysis.jump-phase-window-ms", 420L);
        if (now - data.getLastJumpTime() > Math.max(0L, windowMs)) return false;

        double minDy = plugin.getConfig().getDouble("movement-analysis.jump-phase-min-dy", -0.14D);
        double riseSlack = plugin.getConfig().getDouble("movement-analysis.jump-phase-rise-slack", 0.035D);
        double baseHorizontal = plugin.getConfig().getDouble("movement-analysis.jump-phase-max-horizontal", 0.82D);
        double extraHorizontalPerTick = plugin.getConfig().getDouble("movement-analysis.jump-phase-extra-horizontal-per-tick", 0.18D);
        double maxHorizontal = baseHorizontal + Math.max(0.0D, ctx.ticks - 1.0D) * extraHorizontalPerTick;
        return ctx.dy >= minDy
                && ctx.dy <= ctx.maxJumpRise + riseSlack
                && ctx.distH <= maxHorizontal;
    }

    public static boolean shouldSkip(Player p, PlayerData data) {
        if (p == null || data == null) return true;
        if (p.isFlying() || p.getAllowFlight()) return true;
        if (p.isInsideVehicle()) return true;
        if (data.isTeleportExempt() || data.isVelocityExempt() || data.isBlockStateExempt()) return true;
        return false;
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

    private static boolean isAirBelow(Location loc, double depth) {
        Material t = loc.clone().subtract(0, depth, 0).getBlock().getType();
        return t == Material.AIR;
    }

    private static boolean hasLowCeiling(Location loc) {
        return loc.clone().add(0, 1.8, 0).getBlock().getType().isSolid();
    }

    private static boolean hasNearbySolid(Location loc, double radius, double yOffset) {
        for (double ox = -radius; ox <= radius; ox += radius) {
            for (double oz = -radius; oz <= radius; oz += radius) {
                Block b = loc.clone().add(ox, yOffset, oz).getBlock();
                if (b != null && b.getType().isSolid()) return true;
            }
        }
        return false;
    }

    private static boolean isClimbable(Location loc) {
        Material t = loc.getBlock().getType();
        return t == Material.LADDER || t == Material.VINE;
    }

    private static boolean isWeb(Location loc) {
        return loc.getBlock().getType() == Material.WEB;
    }

    private static boolean isOnSlime(Location loc) {
        return loc.clone().subtract(0, 1, 0).getBlock().getType() == Material.SLIME_BLOCK;
    }

    private static boolean isWeirdSurface(Location loc) {
        Material t = loc.clone().subtract(0, 1, 0).getBlock().getType();
        String name = t.name();
        return name.contains("STEP") || name.contains("SLAB") || name.contains("STAIRS")
                || t == Material.SOUL_SAND;
    }

    private static boolean isInLiquid(Location loc) {
        Material t = loc.getBlock().getType();
        return t == Material.WATER || t == Material.STATIONARY_WATER
                || t == Material.LAVA || t == Material.STATIONARY_LAVA;
    }

    public static final class Context {
        public final Location from;
        public final Location to;
        public final double distH;
        public final double dy;
        public final double ticks;
        public final boolean onGround;
        public final boolean inLiquid;
        public final boolean onSlime;
        public final boolean climbable;
        public final boolean inWeb;
        public final boolean weirdSurface;
        public final boolean lowCeiling;
        public final boolean airBelow;
        public final boolean blockNearby;
        public final double maxJumpRise;

        public Context(Location from, Location to, double distH, double dy, double ticks, boolean onGround,
                       boolean inLiquid, boolean onSlime, boolean climbable, boolean inWeb,
                       boolean weirdSurface, boolean lowCeiling, boolean airBelow, boolean blockNearby,
                       double maxJumpRise) {
            this.from = from;
            this.to = to;
            this.distH = distH;
            this.dy = dy;
            this.ticks = ticks;
            this.onGround = onGround;
            this.inLiquid = inLiquid;
            this.onSlime = onSlime;
            this.climbable = climbable;
            this.inWeb = inWeb;
            this.weirdSurface = weirdSurface;
            this.lowCeiling = lowCeiling;
            this.airBelow = airBelow;
            this.blockNearby = blockNearby;
            this.maxJumpRise = maxJumpRise;
        }
    }
}
