package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.AttackRayContext;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.CombatResult;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.HitboxUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Shared geometry helpers for Prism-tier silent aim and reach checks.
 */
public final class PrismCombatSupport {

    private PrismCombatSupport() {}

    public static boolean shouldProcessAttack(Player p, PlayerData data, TierCheck check, boolean dedupe) {
        if (p == null || data == null || !data.wasLastUseEntityAttack()) return false;
        long now = System.currentTimeMillis();
        long attackWindowMs = check.pluginRef().tierCfg().checkLong(check.name(), "attackWindowMs", 220L);
        if (now - data.getLastUseEntityTime() > attackWindowMs) return false;
        if (dedupe) {
            long last = data.getNoRotationALastProcessedAttackMs();
            long dedupeMs = check.pluginRef().tierCfg().checkLong(check.name(), "dedupeMs", 25L);
            if (last > 0L && now - last < dedupeMs) return false;
            data.setNoRotationALastProcessedAttackMs(now);
        }
        return true;
    }

    public static NoRotationResult evaluateNoRotationA(VezAntiCheat plugin, TierCheck check,
                                                     Player p, PlayerData data, long now) {
        String cfg = check == null ? "PrismInteractionLegality" : check.name();
        return evaluateNoRotationA(plugin, cfg, p, data, now);
    }

    public static NoRotationResult evaluateNoRotationA(VezAntiCheat plugin, String checkName,
                                                     Player p, PlayerData data, long now) {
        long rotAge = now - data.getLastRotationPacket();
        long maxRotationAgeMs = plugin.tierCfg().checkLong(checkName, "maxRotationAgeMs", 165L);
        AttackContext ctx = buildAttackContext(plugin, checkName, p, data);
        if (ctx == null) return NoRotationResult.clean();

        double maxAttackAngle = plugin.tierCfg().checkDouble(checkName, "maxAttackAngle", 32.0D);
        double maxLookDot = plugin.tierCfg().checkDouble(checkName, "maxLookDot", 0.84D);
        boolean suspicious = rotAge > maxRotationAgeMs && ctx.angle > maxAttackAngle && ctx.lookDot < maxLookDot;
        return new NoRotationResult(suspicious, ctx.angle, ctx.lookDot, ctx.distance, rotAge);
    }

    public static NoRotationResult evaluateNoRotationB(VezAntiCheat plugin, TierCheck check,
                                                       Player p, PlayerData data, long now) {
        String cfg = check == null ? "PrismInteractionLegality" : check.name();
        NoRotationResult base = evaluateNoRotationA(plugin, cfg, p, data, now);
        if (!base.suspicious) return base;
        AttackRayContext ray = data.getAttackRayContext();
        if (ray == null) return base;
        float cameraDelta = CombatUtil.angleDiff(ray.packetYaw, ray.cameraYaw);
        boolean impossible = cameraDelta > plugin.tierCfg().checkDouble(cfg, "maxCameraDelta", 12.0D)
                && ray.angleDegrees > plugin.tierCfg().checkDouble(cfg, "minPacketAngle", 28.0D);
        return new NoRotationResult(impossible, ray.angleDegrees, ray.lookDot, ray.distance, base.rotAgeMs);
    }

    public static NoRotationResult evaluateNoRotationC(VezAntiCheat plugin, TierCheck check,
                                                       Player p, PlayerData data, long now) {
        if (data.getTransactionState().hasRecentAck(now)) {
            return NoRotationResult.clean();
        }
        return evaluateNoRotationA(plugin, check, p, data, now);
    }

    public static RayResult evaluateRotationRay(Player p, PlayerData data) {
        return evaluateRotationRay(p, data, null);
    }

    public static RayResult evaluateRotationRay(Player p, PlayerData data, CombatUtil.ReachContext reachCtx) {
        AttackRayContext ctx = data.getAttackRayContext();
        Entity target = data.getLastTargetEntity();
        if (target == null) {
            target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        }
        if (target == null) return RayResult.clean();

        Location eye = HitboxUtil.buildPacketSyncedEye(p, data);
        Location base = reachCtx != null && reachCtx.getCompensatedLocation() != null
                ? reachCtx.getCompensatedLocation()
                : target.getLocation();
        double width = reachCtx != null ? reachCtx.getWidth() : CombatUtil.entityWidth(target);
        double height = reachCtx != null ? reachCtx.getHeight() : CombatUtil.entityHeight(target);

        CombatUtil.RayTraceResult trace = CombatUtil.rayTraceToHitbox(
                eye, base, width, height, CombatUtil.VANILLA_HITBOX_EXPANSION, 6.0D);
        boolean miss = trace == null || !trace.isHit();

        double angle = ctx != null ? ctx.angleDegrees : 0.0D;
        double lookDot = ctx != null ? ctx.lookDot : 1.0D;
        double distance = ctx != null ? ctx.distance : CombatUtil.distanceToHitbox(eye, target);
        if (ctx == null) {
            Location packetEye = eye.clone();
            packetEye.setYaw(data.getPacketYaw());
            packetEye.setPitch(data.getPacketPitch());
            angle = CombatUtil.angularError(packetEye, base, width, height);
            lookDot = CombatUtil.lookDotToHitbox(packetEye, base, width, height);
        }
        return new RayResult(miss, angle, lookDot, distance);
    }

    public static ReachResult evaluateReach(VezAntiCheat plugin, TierCheck check, Player p, PlayerData data,
                                            double maxReach, boolean cancelOnFail) {
        if (p.getGameMode() == GameMode.CREATIVE) return ReachResult.clean();
        CombatResult engine = data.getLastCombatResult();
        if (engine == null || !engine.tracked) return ReachResult.clean();
        double reach = engine.reachDistance();
        boolean over = reach > maxReach;
        return new ReachResult(over, reach, maxReach, cancelOnFail && over);
    }

    public static ReachResult evaluateReachLegacy(VezAntiCheat plugin, TierCheck check, Player p, PlayerData data,
                                                  double maxReach, double margin, boolean cancelOnFail) {
        String cfg = check == null ? "PrismInteractionLegality" : check.name();
        return evaluateReachLegacy(plugin, cfg, p, data, maxReach, margin, cancelOnFail);
    }

    public static ReachResult evaluateReachLegacy(VezAntiCheat plugin, String checkName, Player p, PlayerData data,
                                                  double maxReach, double margin, boolean cancelOnFail) {
        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, checkName);
        if (combat == null) return ReachResult.clean();
        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (target == null) return ReachResult.clean();
        int ping = Math.max(0, PingUtil.getPing(p));
        long rewindMs = CombatUtil.compensationWindowMs(ping, 80L, 0.30D, 220L);
        Location eye = data.getLastAttackEyeLocation();
        if (eye == null) eye = p.getEyeLocation();
        PlayerData targetData = target instanceof Player ? plugin.data().get((Player) target) : null;
        CombatUtil.ReachContext ctx = CombatUtil.analyzeReach(eye, target, targetData, data.getLastUseEntityTime(), rewindMs);
        if (ctx == null) return ReachResult.clean();
        double limit = maxReach + margin;
        boolean over = ctx.getCompensatedDistance() > limit;
        return new ReachResult(over, ctx.getCompensatedDistance(), limit, cancelOnFail && over);
    }

    private static AttackContext buildAttackContext(VezAntiCheat plugin, TierCheck check, Player p, PlayerData data) {
        return buildAttackContext(plugin, check == null ? "PrismInteractionLegality" : check.name(), p, data);
    }

    private static AttackContext buildAttackContext(VezAntiCheat plugin, String checkName, Player p, PlayerData data) {
        AttackRayContext ray = data.getAttackRayContext();
        if (ray != null) {
            double minRange = plugin.tierCfg().checkDouble(checkName, "minRange", 1.25D);
            double maxRange = plugin.tierCfg().checkDouble(checkName, "maxRange", 4.2D);
            if (ray.distance < minRange || ray.distance > maxRange) return null;
            return new AttackContext(ray.distance, ray.angleDegrees, ray.lookDot);
        }
        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (target == null) return null;
        Location eye = HitboxUtil.buildPacketSyncedEye(p, data);
        double width = CombatUtil.entityWidth(target);
        double height = CombatUtil.entityHeight(target);
        double distance = CombatUtil.distanceToHitbox(eye, target.getLocation(), width, height);
        double minRange = plugin.tierCfg().checkDouble(checkName, "minRange", 1.25D);
        double maxRange = plugin.tierCfg().checkDouble(checkName, "maxRange", 4.2D);
        if (distance < minRange || distance > maxRange) return null;
        double angle = CombatUtil.angleToEntity(eye, target, data.getPacketYaw(), data.getPacketPitch());
        double lookDot = CombatUtil.lookDotToHitbox(eye, target.getLocation(), width, height);
        return new AttackContext(distance, angle, lookDot);
    }

    public static final class NoRotationResult {
        public final boolean suspicious;
        public final double angle;
        public final double lookDot;
        public final double distance;
        public final long rotAgeMs;

        NoRotationResult(boolean suspicious, double angle, double lookDot, double distance, long rotAgeMs) {
            this.suspicious = suspicious;
            this.angle = angle;
            this.lookDot = lookDot;
            this.distance = distance;
            this.rotAgeMs = rotAgeMs;
        }

        static NoRotationResult clean() {
            return new NoRotationResult(false, 0.0D, 1.0D, 0.0D, 0L);
        }
    }

    public static final class RayResult {
        public final boolean rayMiss;
        public final double angle;
        public final double lookDot;
        public final double distance;

        RayResult(boolean rayMiss, double angle, double lookDot, double distance) {
            this.rayMiss = rayMiss;
            this.angle = angle;
            this.lookDot = lookDot;
            this.distance = distance;
        }

        static RayResult clean() { return new RayResult(false, 0.0D, 1.0D, 0.0D); }
    }

    public static final class ReachResult {
        public final boolean overReach;
        public final double reach;
        public final double limit;
        public final boolean cancel;

        ReachResult(boolean overReach, double reach, double limit, boolean cancel) {
            this.overReach = overReach;
            this.reach = reach;
            this.limit = limit;
            this.cancel = cancel;
        }

        static ReachResult clean() { return new ReachResult(false, 0.0D, 3.0D, false); }
    }

    private static final class AttackContext {
        final double distance;
        final double angle;
        final double lookDot;

        AttackContext(double distance, double angle, double lookDot) {
            this.distance = distance;
            this.angle = angle;
            this.lookDot = lookDot;
        }
    }
}
