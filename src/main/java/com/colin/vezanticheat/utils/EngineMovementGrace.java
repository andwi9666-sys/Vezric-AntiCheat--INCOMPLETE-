package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Shared exemptions for engine-backed prediction sub-checks (Offset, Fly, Speed, etc.).
 * Covers knockback/combat impulses, jumps, step-ups, and slab/stair descents where the
 * prediction envelope legitimately diverges from actual movement.
 */
public final class EngineMovementGrace {

    private EngineMovementGrace() {}

    /** Skip engine offset / fly simulation flags entirely for this tick. */
    public static boolean shouldExemptEngineFlag(VezAntiCheat plugin, Player p, PlayerData data,
                                               EngineResult er, long nowMs, String checkName) {
        if (plugin == null || p == null || data == null || er == null) return true;

        if (er.knockbackTick || er.explosionTick) return true;
        if (data.isVelocityExempt() || data.isTeleportExempt() || data.isPotionExempt()) return true;
        if (data.isBlockStateExempt()) return true;
        if (data.isEatMovementGrace() || ItemUseMovementUtil.suppressesMovementFlags(data, System.currentTimeMillis())) return true;

        if (isKnockbackOrCombatGrace(plugin, data, nowMs)) return true;
        if (isLikelyLegitBridgeMovement(plugin, p, data, er, nowMs)) return true;

        if (FallArcTracker.isLikelyLegitFallArc(plugin, data, er, nowMs)) return true;
        if (NoFallUtil.shouldExemptFallDamagePrediction(plugin, p, data, er, nowMs)) return true;

        if (MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data)) return true;
        if (isLikelyLegitJumpArc(plugin, p, data, er, nowMs)) return true;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());

        if (FlyUtil.engineVerticalGrace(plugin, p, data, er.verticalOffset, dy, distH, er.clientGround, checkName)) {
            return true;
        }

        if (isPostKnockbackStrafeJump(plugin, data, er, dy, distH, nowMs)) {
            return true;
        }

        if (isLikelyLegitSpeedLocomotion(plugin, p, er, checkName)) {
            return true;
        }

        return isLegitSlabOrStairMotion(plugin, data, dy, distH, er.offset, checkName);
    }

    /**
     * Phase through-wall: require collision but exempt KB/combat/slab/corner-push ticks.
     */
    public static boolean shouldExemptPhaseFlag(VezAntiCheat plugin, Player p, PlayerData data,
                                              EngineResult er, long nowMs, String checkName) {
        if (plugin == null || p == null || data == null || er == null) return true;

        if (er.knockbackTick || er.explosionTick) return true;
        if (data.isVelocityExempt() || data.isTeleportExempt() || data.isPotionExempt()) return true;
        if (isKnockbackOrCombatGrace(plugin, data, nowMs)) return true;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());

        if (isLegitSlabOrStairMotion(plugin, data, dy, distH, er.offset, checkName)) return true;

        // Corner pushes: collision without meaningful offset is normal wall friction.
        String cfg = checkName == null ? "PhasePrediction" : checkName;
        double thr = CheckConfigUtil.checkDouble(plugin, cfg, "enginePhaseOffset", 0.10D);
        if ((er.collisionX || er.collisionZ) && er.horizontalOffset <= thr * 1.25D) {
            return true;
        }

        return false;
    }

    /**
     * Ladder / liquid checks: only skip on impulse or teleport — not jump arcs or slab grace.
     */
    public static boolean shouldExemptLiquidLocomotionFlag(VezAntiCheat plugin, PlayerData data,
                                                           EngineResult er, long nowMs) {
        if (plugin == null || data == null || er == null) return true;
        if (er.knockbackTick || er.explosionTick) return true;
        if (data.isVelocityExempt() || data.isTeleportExempt()) return true;
        return isKnockbackOrCombatGrace(plugin, data, nowMs);
    }

    /** Post-KB combo strafe: upward dy with elevated horizontal offset is legit. */
    private static boolean isPostKnockbackStrafeJump(VezAntiCheat plugin, PlayerData data,
                                                     EngineResult er, double dy, double distH, long nowMs) {
        if (plugin == null || data == null || er == null) return false;
        if (!isKnockbackOrCombatGrace(plugin, data, nowMs)) return false;
        if (dy <= 0.0D) return false;

        double maxH = CheckConfigUtil.checkDouble(plugin, "PredictionFly", "engineGraceMaxOffset", 0.20D);
        double maxV = CheckConfigUtil.checkDouble(plugin, "PredictionFly", "jumpGraceMaxOffset", 0.30D);
        return er.horizontalOffset <= maxH && er.verticalOffset <= maxV && distH <= 1.10D;
    }

    public static boolean isKnockbackOrCombatGrace(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (data == null) return false;
        if (isKnockbackOrCombatGrace(data, nowMs, GraceSettings.from(plugin))) return true;
        return plugin != null && SpeedUtil.isRecentExplosion(plugin, data, nowMs);
    }

    /** Snapshot of knockback/combat grace windows used by tests in this package. */
    static final class GraceSettings {
        final long kbGraceMs;
        final long combatGraceMs;

        GraceSettings(long kbGraceMs, long combatGraceMs) {
            this.kbGraceMs = kbGraceMs;
            this.combatGraceMs = combatGraceMs;
        }

        static GraceSettings defaults() {
            return new GraceSettings(500L, 450L);
        }

        static GraceSettings from(VezAntiCheat plugin) {
            if (plugin == null) return defaults();
            long kbGraceMs = plugin.getConfig().getLong("engine.knockback-grace-ms",
                    Math.max(plugin.cfg().velocityExemptMs(), 500L));
            long combatMs = plugin.getConfig().getLong("engine.combat-movement-grace-ms",
                    plugin.getConfig().getLong("movement-analysis.combat-window-ms", 450L));
            return new GraceSettings(kbGraceMs, combatMs);
        }
    }

    static boolean isKnockbackOrCombatGrace(PlayerData data, long nowMs, GraceSettings settings) {
        if (data == null || settings == null) return false;

        if (data.getLastVelocityTime() > 0L && (nowMs - data.getLastVelocityTime()) <= settings.kbGraceMs) {
            return true;
        }
        if (data.getLastUseEntityTime() > 0L && (nowMs - data.getLastUseEntityTime()) <= settings.combatGraceMs) {
            return true;
        }
        return data.getLastDamageTime() > 0L && (nowMs - data.getLastDamageTime()) <= settings.combatGraceMs;
    }

    /**
     * Horizontal speed exemption: knockback flings players sideways; the engine often
     * under-predicts horizontal displacement for several ticks after a velocity packet.
     */
    public static boolean shouldExemptHorizontalSpeedFlag(VezAntiCheat plugin, Player p, PlayerData data,
                                                          EngineResult er, long nowMs, String checkName) {
        if (plugin == null || p == null || data == null || er == null) return true;

        if (SpeedPatternUtil.shouldForceSpeedDetection(plugin, p, data, er, nowMs)) return false;

        if (er.knockbackTick || er.explosionTick) return true;
        if (data.isVelocityExempt() || data.isTeleportExempt() || data.isPotionExempt()) return true;
        if (data.isBlockStateExempt()) return true;
        if (data.isEatMovementGrace() || ItemUseMovementUtil.suppressesMovementFlags(data, System.currentTimeMillis())) return true;
        if (isKnockbackOrCombatGrace(plugin, data, nowMs)) return true;
        if (isLikelyLegitBridgeMovement(plugin, p, data, er, nowMs)) return true;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());

        String cfg = checkName == null ? "SpeedPrediction" : checkName;
        double blatantH = CheckConfigUtil.checkDouble(plugin,cfg, "engineBlatantHorizontalOffset", 0.55D);
        if (er.horizontalOffset >= blatantH) return false;

        if (MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data)) {
            double maxH = CheckConfigUtil.checkDouble(plugin,cfg, "jumpGraceMaxOffset", 1.10D);
            double ratio = SpeedPatternUtil.horizontalRatio(er);
            if (er.horizontalOffset <= maxH && ratio < 1.10D) return true;
        }

        if (isLikelyLegitJumpArc(plugin, p, data, er, nowMs)) {
            if (SpeedPatternUtil.isYPortSlam(data, er)) return false;
            if (SpeedPatternUtil.horizontalRatio(er) >= 1.12D) return false;
            return true;
        }

        if (isLegitSlabOrStairMotion(plugin, data, dy, distH, er.offset, cfg)) return true;

        if (isLikelyWallStop(er)) return true;

        double speedThr = CheckConfigUtil.checkDouble(plugin, cfg, "engineHorizontalOffset", 0.032D);
        if (PotionUtil.hasSpeedBoost(p) && er.horizontalOffset <= speedThr + PotionUtil.combinedSpeedOffsetAllowance(p)) {
            return true;
        }

        if (PotionUtil.hasSpeedBoost(p) && p.isSprinting() && isLikelyLegitGroundLocomotion(er)) {
            double sprintSpeedCap = speedThr + PotionUtil.combinedSpeedOffsetAllowance(p) + 0.055D;
            if (er.horizontalOffset <= sprintSpeedCap) return true;
            double ratio = SpeedPatternUtil.horizontalRatio(er);
            double ratioCap = 1.12D + (PotionUtil.speedLevel(p) * 0.04D)
                    + Math.max(0.0D, (p.getWalkSpeed() / 0.2F - 1.0F) * 0.08D);
            if (ratio < ratioCap) return true;
        }

        if (er.onIce || er.onSlime) {
            double iceMaxH = CheckConfigUtil.checkDouble(plugin,cfg, "engineIceMaxHorizontalOffset", 0.62D);
            if (er.horizontalOffset <= iceMaxH) return true;
        }

        double kbMaxH = CheckConfigUtil.checkDouble(plugin,cfg, "engineKnockbackHorizontalMaxOffset", 0.58D);
        if (data.getLastVelocityTime() > 0L
                && (nowMs - data.getLastVelocityTime()) <= plugin.getConfig().getLong("engine.knockback-grace-ms", 550L)
                && er.horizontalOffset <= kbMaxH) {
            return true;
        }

        return false;
    }

    /**
     * Legit horizontal+vertical mismatch on slabs/stairs (step up, walk down, edge snaps).
     */
    public static boolean isLegitSlabOrStairMotion(VezAntiCheat plugin, PlayerData data,
                                                   double dy, double distH, double reducedOffset,
                                                   String checkName) {
        if (plugin == null || data == null) return false;

        String cfg = checkName == null ? "OffsetPrediction" : checkName;
        // TIGHTENED (movement 2A): the old 0.28 slab/stair grace was wide enough to mask a real
        // sub-threshold offset on every step surface. Replaced with a tighter buffered value that
        // stays inside the engine compensation leniency budget cap (engine.compensation
        // .leniency-budget-cap, default 0.12) so step-surface grace cannot be farmed past the cap.
        double maxOffset = CheckConfigUtil.checkDouble(plugin, cfg, "engineSlabOffsetGrace", 0.12D);
        if (reducedOffset > maxOffset) return false;

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        boolean stepSurface = hasStepSurfaceNear(from) || hasStepSurfaceNear(to);
        if (!stepSurface) return false;

        double maxH = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabMaxHorizontal", 0.85D);
        if (distH > maxH) return false;

        double riseMin = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabRiseMinDy", 0.015D);
        double riseMax = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabRiseMaxDy", 0.62D);
        if (dy >= riseMin && dy <= riseMax) return true;

        double fallMin = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabFallMinDy", -0.65D);
        double fallMax = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabFallMaxDy", 0.06D);
        return dy >= fallMin && dy <= fallMax;
    }

    /**
     * Client {@code onGround=true} while the engine predicts airborne is normal during
     * vanilla step-ups onto slabs/stairs (1.8 sends ground through the step).
     */
    public static boolean shouldExemptGroundSpoofFlag(VezAntiCheat plugin, PlayerData data,
                                                      EngineResult er, long nowMs, String checkName) {
        if (plugin == null || data == null || er == null) return true;

        if (er.knockbackTick || er.explosionTick) return true;
        if (data.isVelocityExempt() || data.isTeleportExempt() || data.isPotionExempt()) return true;
        if (isKnockbackOrCombatGrace(plugin, data, nowMs)) return true;

        if (!er.clientGround) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());

        String cfg = checkName == null ? "GroundSpoofPrediction" : checkName;
        double minDy = CheckConfigUtil.checkDouble(plugin,cfg, "minDy",
                plugin.getConfig().getDouble("prediction.ground-spoof.min-dy", 0.05D));
        double minDescentDy = CheckConfigUtil.checkDouble(plugin, cfg, "minDescentDy",
                plugin.getConfig().getDouble("nofall.ground-spoof.min-descent-dy", -0.04D));

        if (Math.abs(dy) <= minDy) return true;

        if (dy < minDescentDy && FallArcTracker.isInFallArcWindow(plugin, data, nowMs)) {
            org.bukkit.World world = resolveWorld(data);
            if (world != null && NoFallUtil.isFallDamageEnabled(plugin, world)) {
                return false;
            }
        } else if (FallArcTracker.isInFallArcWindow(plugin, data, nowMs)) {
            return true;
        }

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        boolean stepSurface = hasStepSurfaceNear(from) || hasStepSurfaceNear(to);
        if (!stepSurface) return false;

        double riseMin = CheckConfigUtil.checkDouble(plugin,cfg, "engineStepMinDy", 0.015D);
        double riseMax = CheckConfigUtil.checkDouble(plugin,cfg, "engineStepMaxDy", 0.62D);
        double maxH = CheckConfigUtil.checkDouble(plugin,cfg, "engineStepMaxHorizontal", 0.85D);
        if (dy >= riseMin && dy <= riseMax && distH <= maxH) return true;

        FlyUtil.Context flyCtx = FlyUtil.analyze(plugin, null, data);
        if (flyCtx != null && FlyUtil.isLikelyLegitStepMotion(plugin, cfg, dy, distH, flyCtx, true)) {
            return true;
        }

        double fallMin = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabFallMinDy", -0.65D);
        double fallMax = CheckConfigUtil.checkDouble(plugin,cfg, "engineSlabFallMaxDy", 0.06D);
        return dy >= fallMin && dy <= fallMax && distH <= maxH;
    }

    /**
     * Speed potion or plugin-boosted walk speed on flat ground — covers lobby Speed I sprint FPs
     * on {@link com.colin.vezanticheat.tier.CheckTier#SIMULATION} corroboration checks.
     */
    public static boolean isLikelyLegitSpeedLocomotion(VezAntiCheat plugin, Player p, EngineResult er,
                                                       String checkName) {
        if (p == null || er == null || !PotionUtil.hasSpeedBoost(p)) return false;

        String cfg = checkName == null ? "PredictionSpeed" : checkName;
        double speedThr = CheckConfigUtil.checkDouble(plugin, cfg, "engineHorizontalOffset", 0.032D);
        double allowance = PotionUtil.combinedSpeedOffsetAllowance(p);

        if (er.horizontalOffset <= speedThr + allowance) return true;

        if (!p.isSprinting() || !isLikelyLegitGroundLocomotion(er)) return false;

        double sprintCap = speedThr + allowance + 0.06D;
        if (er.horizontalOffset <= sprintCap) return true;

        double ratio = SpeedPatternUtil.horizontalRatio(er);
        double ratioCap = 1.12D + (PotionUtil.speedLevel(p) * 0.04D)
                + Math.max(0.0D, (p.getWalkSpeed() / 0.2F - 1.0F) * 0.08D);
        return ratio < ratioCap;
    }

    /**
     * Normal grounded walk/sprint where the engine slightly under-predicts should not trip
     * simulation corroboration sub-checks.
     */
    public static boolean isLikelyLegitGroundLocomotion(EngineResult er) {
        if (er == null || !er.clientGround) return false;
        if (er.knockbackTick || er.explosionTick || er.couldSkipTick) return false;
        if (er.inWater || er.onClimbable || er.inWeb) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());
        if (distH < 0.03D || distH > 0.52D) return false;
        if (Math.abs(dy) > 0.12D) return false;
        return er.predictedOnGround || er.clientGround;
    }

    /**
     * Multi-tick sprint jump / ledge-drop arcs: rise, carry, fall, and landing spread over many
     * ticks with higher per-tick horizontal and vertical deltas than flat-ground prediction allows.
     */
    public static boolean isLikelyLegitJumpArc(VezAntiCheat plugin, Player p, PlayerData data,
                                               EngineResult er, long nowMs) {
        if (plugin == null || p == null || data == null || er == null) return false;
        if (er.knockbackTick || er.explosionTick || er.inWater || er.onClimbable || er.inWeb) return false;
        if (data.getLastJumpTime() <= 0L) return false;

        long arcWindow = plugin.getConfig().getLong("movement-analysis.jump-arc-window-ms", 950L);
        if (nowMs - data.getLastJumpTime() > arcWindow) return false;
        if (data.getEngineAirborneTicks() > 22 && nowMs - data.getLastJumpTime() > 650L) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());

        double maxDistH = plugin.getConfig().getDouble("movement-analysis.jump-arc-max-horizontal", 1.08D);
        if (p != null && PotionUtil.hasSpeed(p)) {
            maxDistH += 0.12D * PotionUtil.speedLevel(p);
        }
        if (p != null && p.isSprinting()) {
            maxDistH += 0.14D;
        }
        double minDy = plugin.getConfig().getDouble("movement-analysis.jump-arc-min-dy", -0.82D);
        double maxDy = plugin.getConfig().getDouble("movement-analysis.jump-arc-max-dy", 0.55D);
        double maxOffset = plugin.getConfig().getDouble("movement-analysis.jump-arc-max-offset", 0.52D);
        maxOffset += PotionUtil.jumpArcOffsetAllowance(p);
        if (p != null && p.isSprinting()) {
            maxOffset += 0.18D;
        }
        if (data.getLastEatStart() > 0L && nowMs - data.getLastEatStart() <= 2500L) {
            maxOffset += 0.28D;
            maxDistH += 0.22D;
        }
        if (data.isEatMovementGrace()) {
            maxOffset += 0.22D;
            maxDistH += 0.16D;
        }

        if (dy < minDy || dy > maxDy || distH > maxDistH || er.offset > maxOffset) return false;
        if (Math.abs(dy) < 0.03D && !er.clientGround && !er.predictedOnGround) return false;

        FlyUtil.Context ctx = FlyUtil.analyze(plugin, p, data);
        boolean airborne = ctx != null && (ctx.airBelow || !ctx.onGround);
        boolean falling = dy < -0.02D;
        boolean rising = dy > 0.06D;
        boolean landing = er.clientGround && dy <= 0.18D && dy >= minDy;
        boolean movementLike = p.isSprinting() || distH >= 0.08D || data.isFallArcActive()
                || data.getFallArcStartMs() > 0L
                || (data.getFallArcLandMs() > 0L && nowMs - data.getFallArcLandMs() <= 750L);

        return movementLike && (airborne || falling || rising || landing || !er.predictedOnGround);
    }

    /**
     * Sneak bridging and edge-standing produce client {@code onGround=false} ticks where vanilla
     * movement prediction under-shoots horizontal carry and vertical snap.
     */
    public static boolean isLikelyLegitBridgeMovement(VezAntiCheat plugin, Player p, PlayerData data,
                                                      EngineResult er, long nowMs) {
        if (plugin == null || p == null || data == null || er == null) return false;

        long placeMs = Math.max(data.getLastBlockPlace(), data.getLastBlockPlacePacketTime());
        long bridgeGraceMs = plugin.getConfig().getLong("scaffold-analysis.bridge-movement-grace-ms", 900L);
        if (placeMs <= 0L || (nowMs - placeMs) > bridgeGraceMs) return false;

        Location feet = data.getLastLoc();
        if (feet == null) feet = p.getLocation();
        if (!ScaffoldUtil.hasBridgingSupportBelow(plugin, p, feet)
                && !ScaffoldUtil.isBlockEdgeStand(plugin, p, feet)) {
            return false;
        }

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double distH = Math.hypot(actual.getX(), actual.getZ());
        double maxH = plugin.getConfig().getDouble("scaffold-analysis.bridge-max-horizontal-offset", 0.40D);
        double maxOffset = plugin.getConfig().getDouble("scaffold-analysis.bridge-max-offset", 0.45D);
        double maxDistH = plugin.getConfig().getDouble("scaffold-analysis.bridge-max-horizontal", 0.58D);
        return distH <= maxDistH && er.horizontalOffset <= maxH && er.offset <= maxOffset;
    }

    public static boolean isLikelyWallStop(EngineResult er) {
        if (er == null || (!er.collisionX && !er.collisionZ)) return false;
        Vector actual = er.actual == null ? new Vector() : er.actual;
        double distH = Math.hypot(actual.getX(), actual.getZ());
        if (distH <= 0.08D) return true;
        return distH <= 0.20D && er.horizontalOffset <= 0.40D;
    }

    public static boolean hasStepSurfaceNear(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (double ox = -0.3; ox <= 0.3; ox += 0.3) {
            for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
                for (double yOff = -0.2; yOff <= 0.5; yOff += 0.5) {
                    Material type = loc.clone().add(ox, yOff, oz).getBlock().getType();
                    if (isStepLikeMaterial(type)) return true;
                }
            }
        }
        return false;
    }

    public static boolean isStepLikeMaterial(Material material) {
        if (material == null || material == Material.AIR) return false;
        String name = material.name();
        return name.contains("STEP")
                || name.contains("SLAB")
                || name.contains("STAIRS")
                || name.contains("FENCE")
                || name.contains("WALL")
                || material == Material.SOUL_SAND
                || material == Material.SNOW
                || material == Material.CARPET;
    }

    private static org.bukkit.World resolveWorld(PlayerData data) {
        if (data == null) return null;
        org.bukkit.Location loc = data.getLastLoc();
        if (loc != null && loc.getWorld() != null) return loc.getWorld();
        loc = data.getLastMoveFrom();
        return loc == null ? null : loc.getWorld();
    }
}
