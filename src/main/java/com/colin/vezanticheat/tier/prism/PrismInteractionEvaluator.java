package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.CombatRewind;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.HitboxUtil;
import com.colin.vezanticheat.utils.LagrangeUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polar-style unified combat interaction legality: reach, line-of-sight, hitbox,
 * stale rotation, backtrack, and lag-range corroboration in one evaluator.
 * No standalone KillAura / Reach / Hitbox checks — {@link PrismInteractionLegality} only.
 */
public final class PrismInteractionEvaluator {

    private static final ConcurrentHashMap<UUID, HitboxSamples> HITBOX_STATE = new ConcurrentHashMap<UUID, HitboxSamples>();

    private static final class HitboxSamples {
        final Deque<Double> missDistances = new ArrayDeque<Double>();
        int patternViolations;
        long lastSampleMs;
    }

    private PrismInteractionEvaluator() {}

    public static InteractionResult evaluate(VezAntiCheat plugin, String checkName,
                                             Player p, PlayerData data, long now) {
        if (plugin == null || p == null || data == null || !data.wasLastUseEntityAttack()) {
            return InteractionResult.clean();
        }
        if (p.getGameMode() == GameMode.CREATIVE) return InteractionResult.clean();
        if (data.isTeleportExempt() || data.isVelocityExempt()) return InteractionResult.clean();

        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (target == null) return InteractionResult.clean();

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, checkName);
        if (combat == null || combat.isTrade()) {
            return InteractionResult.clean();
        }

        double maxReach = CombatUtil.resolveEffectiveMaxReach(plugin, checkName);

        PrismCombatSupport.ReachResult engineReach =
                PrismCombatSupport.evaluateReach(plugin, null, p, data, maxReach, false);
        PrismCombatSupport.ReachResult legacyReach =
                PrismCombatSupport.evaluateReachLegacy(plugin, checkName, p, data, maxReach, 0.0D, false);

        boolean outOfRange = engineReach.overReach || legacyReach.overReach;
        double reach = Math.max(engineReach.reach, legacyReach.reach);

        PrismCombatSupport.NoRotationResult staleRot =
                PrismCombatSupport.evaluateNoRotationA(plugin, checkName, p, data, now);
        PrismCombatSupport.NoRotationResult silentRot =
                PrismCombatSupport.evaluateNoRotationB(plugin, null, p, data, now);

        int ping = Math.max(0, PingUtil.getPing(p));
        long rewindMs = CombatUtil.compensationWindowMs(
                ping,
                plugin.tierCfg().checkLong(checkName, "rewindBaseMs", 80L),
                plugin.tierCfg().checkDouble(checkName, "rewindPingFactor", 0.35D),
                plugin.tierCfg().checkLong(checkName, "maxRewindMs", 200L)
        );

        Location eye = data.getLastAttackEyeLocation();
        if (eye == null || eye.getWorld() == null) {
            eye = HitboxUtil.buildPacketSyncedEye(p, data);
        }

        PlayerData targetData = target instanceof Player ? plugin.data().get((Player) target) : null;
        CombatUtil.ReachContext reachCtx = resolveReachContext(data, eye, target, targetData,
                data.getLastUseEntityTime(), rewindMs);

        HitboxRaySignal hitbox = evaluateHitboxRay(plugin, checkName, p, data, combat, eye, reachCtx, ping, now);

        PrismCombatSupport.RayResult packetRay = PrismCombatSupport.evaluateRotationRay(p, data, reachCtx);

        boolean hitboxMiss = hitbox.rayMiss && !outOfRange;
        boolean outOfSight = hitbox.rayMiss && packetRay.rayMiss;
        if (combat.isRecentJump() && !outOfRange
                && hitbox.missDistance <= CombatUtil.VANILLA_HITBOX_EXPANSION + 0.05D) {
            hitboxMiss = false;
            outOfSight = false;
        }
        boolean staleRotation = staleRot.suspicious || silentRot.suspicious;
        if (!hitboxMiss && !outOfRange) {
            staleRotation = false;
        }

        BacktrackSignal backtrack = evaluateBacktrack(plugin, checkName, p, data, targetData, reachCtx, ping, now);
        LagrangeSignal lagrange = target instanceof Player
                ? evaluateLagrange(plugin, checkName, p, data, (Player) target, targetData, reachCtx, combat, now)
                : LagrangeSignal.none();

        boolean hitboxPattern = hitbox.patternSuspicious;
        boolean blatantReach = outOfRange && reach > maxReach + plugin.tierCfg().checkDouble(checkName, "blatantReachOver", 0.35D);
        boolean blatant = blatantReach
                || (outOfSight && outOfRange)
                || (outOfSight && packetRay.angle > plugin.tierCfg().checkDouble(checkName, "blatantAngle", 35.0D))
                || (silentRot.suspicious && hitboxMiss);

        int signals = 0;
        if (outOfRange) signals++;
        if (outOfSight) signals++;
        if (hitboxMiss || hitboxPattern) signals++;
        if (staleRotation) signals++;
        if (backtrack.evidence) signals++;
        if (lagrange.evidence) signals++;

        boolean shouldCancel = blatant
                || outOfRange
                || (outOfSight && !HitboxUtil.isRecentHeadFlick(plugin, data, now, checkName))
                || (hitboxMiss && hitbox.missDistance > plugin.tierCfg().checkDouble(checkName, "cancelMissDistance", 0.10D));

        double confidence = blatant ? 1.0D
                : hitboxPattern ? 0.85D
                : signals >= 3 ? 0.80D
                : signals >= 2 ? 0.72D
                : signals == 1 ? 0.48D : 0.0D;

        String label = resolveLabel(outOfRange, outOfSight, hitboxMiss || hitboxPattern,
                staleRotation, backtrack.evidence, lagrange.evidence);

        String debug = label
                + " reach=" + round(reach) + "/" + round(maxReach)
                + " ray=" + packetRay.rayMiss
                + " hbMiss=" + hitboxMiss
                + " hbPat=" + hitboxPattern
                + " rot=" + staleRotation
                + " bt=" + backtrack.evidence
                + " lag=" + lagrange.evidence
                + " sig=" + signals
                + " angle=" + round(packetRay.angle);

        InteractionResult result = new InteractionResult(outOfRange, outOfSight, hitboxMiss, hitboxPattern,
                staleRotation, backtrack.evidence, lagrange.evidence, blatant, shouldCancel,
                signals, confidence, label, debug);

        if (!blatant && CombatContextAnalyzer.shouldExemptCombatInteractionFlagging(
                combat, data, data.getLastUseEntityTime())) {
            return InteractionResult.clean();
        }
        if (!blatant && !combat.isClean()) {
            return InteractionResult.clean();
        }
        return result;
    }

    public static void clearPlayer(UUID uuid) {
        HITBOX_STATE.remove(uuid);
        if (uuid != null) {
            PrismHitboxB.clearState(uuid);
        }
    }

    private static String resolveLabel(boolean outOfRange, boolean outOfSight, boolean hitbox,
                                       boolean staleRotation, boolean backtrack, boolean lagrange) {
        if (outOfRange) return "out_of_range";
        if (outOfSight) return "out_of_sight";
        if (hitbox) return "hitbox_miss";
        if (staleRotation) return "stale_rotation";
        if (backtrack || lagrange) return "latency_abuse";
        return "fighting_suspiciously";
    }

    private static HitboxRaySignal evaluateHitboxRay(VezAntiCheat plugin, String checkName,
                                                     Player p, PlayerData data,
                                                     CombatContextAnalyzer.CombatContext combat,
                                                     Location eye, CombatUtil.ReachContext ctx,
                                                     int ping, long now) {
        HitboxRaySignal empty = new HitboxRaySignal(false, false, 0.0D, false);
        if (ctx == null) return empty;

        if (ctx.getCompensatedDistance() < plugin.tierCfg().checkDouble(checkName, "minDistance", 1.2D)) {
            return empty;
        }

        if (HitboxUtil.isRecentHeadFlick(plugin, data, now, checkName)) {
            return empty;
        }

        Location compensated = ctx.getCompensatedLocation();
        double width = ctx.getWidth();
        double height = ctx.getHeight();

        double vanillaExpansion = plugin.tierCfg().checkDouble(checkName, "vanillaExpansion",
                CombatUtil.vanillaExpansion(plugin));
        double pingExpansion = Math.min(
                plugin.tierCfg().checkDouble(checkName, "maxPingExpansion", 0.12D),
                ping * plugin.tierCfg().checkDouble(checkName, "pingExpansionFactor", 0.0006D)
        );
        PlayerData targetData = null;
        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (target instanceof Player) {
            targetData = plugin.data().get((Player) target);
        }
        double kbExpansion = HitboxUtil.knockbackExpansion(plugin, data, targetData, combat, now, checkName);
        double totalExpansion = vanillaExpansion + pingExpansion + kbExpansion;

        List<Location> lookCandidates = HitboxUtil.attackLookCandidates(plugin, data, eye, now, checkName);
        CombatUtil.RayTraceResult result = HitboxUtil.bestRayTraceToHitbox(
                lookCandidates, compensated, width, height, totalExpansion, 6.0D);
        if (result == null) return empty;

        boolean rayMiss = !result.isHit();
        double missDistance = rayMiss ? result.getMissDistance() : 0.0D;
        if (rayMiss) {
            double flickGrace = HitboxUtil.flickMissGrace(plugin, data, now, ctx.getCompensatedDistance(), checkName);
            missDistance = Math.max(0.0D, missDistance - flickGrace);
            if (missDistance <= plugin.tierCfg().checkDouble(checkName, "graceThreshold", 0.02D)) {
                rayMiss = false;
                missDistance = 0.0D;
            }
        }

        UUID playerId = p.getUniqueId();
        HitboxSamples samples = HITBOX_STATE.computeIfAbsent(playerId, k -> new HitboxSamples());
        if (CombatContextAnalyzer.isActivePvpEngagement(data, now)) {
            samples.missDistances.clear();
            samples.patternViolations = 0;
            return new HitboxRaySignal(rayMiss, false, missDistance, false);
        }

        long windowMs = plugin.tierCfg().checkLong(checkName, "hitboxWindowMs", 8000L);
        if (samples.lastSampleMs > 0 && (now - samples.lastSampleMs) > windowMs) {
            samples.missDistances.clear();
            samples.patternViolations = 0;
        }

        int maxSamples = plugin.tierCfg().checkInt(checkName, "hitboxMaxSamples", 15);
        samples.missDistances.addLast(Double.valueOf(missDistance));
        while (samples.missDistances.size() > maxSamples) samples.missDistances.removeFirst();
        samples.lastSampleMs = now;

        boolean patternSuspicious = false;
        int minSamples = plugin.tierCfg().checkInt(checkName, "hitboxMinSamples", 8);
        if (samples.missDistances.size() >= minSamples) {
            double graceThreshold = plugin.tierCfg().checkDouble(checkName, "graceThreshold", 0.02D);
            int missCount = 0;
            double totalMiss = 0.0D;
            for (double d : samples.missDistances) {
                if (d > graceThreshold) {
                    missCount++;
                    totalMiss += d;
                }
            }
            double missRatio = (double) missCount / samples.missDistances.size();
            double avgMiss = missCount > 0 ? totalMiss / missCount : 0.0D;
            double missRatioThreshold = plugin.tierCfg().checkDouble(checkName, "missRatioThreshold", 0.50D);
            double avgMissThreshold = plugin.tierCfg().checkDouble(checkName, "avgMissThreshold", 0.08D);

            if (missRatio >= missRatioThreshold && avgMiss >= avgMissThreshold) {
                samples.patternViolations++;
                int violationsToFlag = plugin.tierCfg().checkInt(checkName, "hitboxViolationsToFlag", 2);
                patternSuspicious = samples.patternViolations >= violationsToFlag;
                if (patternSuspicious) {
                    samples.missDistances.clear();
                    samples.patternViolations = 0;
                }
            } else if (missRatio < 0.20D && samples.patternViolations > 0) {
                samples.patternViolations = Math.max(0, samples.patternViolations - 1);
            }
        }

        return new HitboxRaySignal(rayMiss, patternSuspicious, missDistance, patternSuspicious);
    }

    private static BacktrackSignal evaluateBacktrack(VezAntiCheat plugin, String checkName,
                                                     Player p, PlayerData data, PlayerData targetData,
                                                     CombatUtil.ReachContext ctx, int ping, long now) {
        if (ctx == null || targetData == null) return BacktrackSignal.none();

        long allowedRewindMs = CombatUtil.compensationWindowMs(
                ping,
                plugin.tierCfg().checkLong(checkName, "backtrackRewindBaseMs", 95L),
                plugin.tierCfg().checkDouble(checkName, "backtrackRewindPingFactor", 0.25D),
                plugin.tierCfg().checkLong(checkName, "backtrackMaxRewindMs", 190L)
        );

        double max = CombatUtil.resolveEffectiveMaxReach(plugin, checkName);
        double currentSlack = plugin.tierCfg().checkDouble(checkName, "backtrackCurrentSlack", 0.18D);
        double historicalSlack = plugin.tierCfg().checkDouble(checkName, "backtrackHistoricalSlack", 0.10D);
        long minExcessRewindMs = plugin.tierCfg().checkLong(checkName, "backtrackMinExcessMs", 45L);
        double minDisplacement = plugin.tierCfg().checkDouble(checkName, "backtrackMinDisplacement", 0.32D);
        double minShift = plugin.tierCfg().checkDouble(checkName, "backtrackMinShift", 0.24D);

        double shift = ctx.getCurrentDistance() - ctx.getCompensatedDistance();
        boolean stalePositionHit = ctx.getCurrentDistance() > (max + currentSlack)
                && ctx.getCompensatedDistance() <= (max + historicalSlack)
                && ctx.getCompensatedAgeMs() > (allowedRewindMs + minExcessRewindMs)
                && ctx.getCompensatedDisplacement() >= minDisplacement
                && shift >= minShift;

        long attackerGapMs = data.getLastFlyingIntervalMs();
        boolean delayPattern = attackerGapMs >= plugin.tierCfg().checkLong(checkName, "attackerPacketGapMs", 130L);
        long moveAge = data.getLastFlyingPacket() <= 0L ? Long.MAX_VALUE : (now - data.getLastFlyingPacket());
        boolean orderPattern = moveAge >= plugin.tierCfg().checkLong(checkName, "attackWithoutMoveMs", 110L);

        boolean evidence = stalePositionHit && (delayPattern || orderPattern || ctx.getCompensatedAgeMs() > allowedRewindMs + 80L);
        return new BacktrackSignal(evidence, stalePositionHit);
    }

    private static LagrangeSignal evaluateLagrange(VezAntiCheat plugin, String checkName,
                                                   Player p, PlayerData data, Player target,
                                                   PlayerData targetData, CombatUtil.ReachContext ctx,
                                                   CombatContextAnalyzer.CombatContext combat, long now) {
        if (target == null || ctx == null) return LagrangeSignal.none();
        LagrangeUtil.LagrangeSample sample = LagrangeUtil.analyzeHit(
                plugin, checkName, p, data, target, targetData, ctx, combat, now);
        if (sample.isForgiven() || sample.isSkipped()) return LagrangeSignal.none();
        double minConfidence = plugin.tierCfg().checkDouble(checkName, "lagrangeMinConfidence", 1.8D);
        boolean evidence = sample.getConfidence() >= minConfidence
                && (sample.isReachEvidence() || sample.isSpatialEvidence());
        return new LagrangeSignal(evidence, sample.getConfidence());
    }

    private static CombatUtil.ReachContext resolveReachContext(
            PlayerData data, Location eye, Entity target, PlayerData targetData,
            long attackTime, long rewindMs) {
        if (data != null && data.getLastCombatResult() != null && data.getLastCombatResult().tracked) {
            CombatUtil.ReachContext ctx = CombatRewind.toReachContext(data.getLastCombatResult());
            if (ctx != null) return ctx;
        }
        return CombatUtil.analyzeReach(eye, target, targetData, attackTime, rewindMs);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class HitboxRaySignal {
        final boolean rayMiss;
        final boolean patternSuspicious;
        final double missDistance;

        HitboxRaySignal(boolean rayMiss, boolean patternSuspicious, double missDistance, boolean ignored) {
            this.rayMiss = rayMiss;
            this.patternSuspicious = patternSuspicious;
            this.missDistance = missDistance;
        }
    }

    private static final class BacktrackSignal {
        final boolean evidence;
        final boolean staleHit;

        BacktrackSignal(boolean evidence, boolean staleHit) {
            this.evidence = evidence;
            this.staleHit = staleHit;
        }

        static BacktrackSignal none() { return new BacktrackSignal(false, false); }
    }

    private static final class LagrangeSignal {
        final boolean evidence;
        final double confidence;

        LagrangeSignal(boolean evidence, double confidence) {
            this.evidence = evidence;
            this.confidence = confidence;
        }

        static LagrangeSignal none() { return new LagrangeSignal(false, 0.0D); }
    }

    public static final class InteractionResult {
        public final boolean outOfRange;
        public final boolean outOfSight;
        public final boolean hitboxMiss;
        public final boolean hitboxPattern;
        public final boolean staleRotation;
        public final boolean backtrackEvidence;
        public final boolean lagrangeEvidence;
        public final boolean blatant;
        public final boolean shouldCancel;
        public final int signalCount;
        public final double confidence;
        public final String label;
        public final String debug;

        InteractionResult(boolean outOfRange, boolean outOfSight, boolean hitboxMiss, boolean hitboxPattern,
                          boolean staleRotation, boolean backtrackEvidence, boolean lagrangeEvidence,
                          boolean blatant, boolean shouldCancel, int signalCount, double confidence,
                          String label, String debug) {
            this.outOfRange = outOfRange;
            this.outOfSight = outOfSight;
            this.hitboxMiss = hitboxMiss;
            this.hitboxPattern = hitboxPattern;
            this.staleRotation = staleRotation;
            this.backtrackEvidence = backtrackEvidence;
            this.lagrangeEvidence = lagrangeEvidence;
            this.blatant = blatant;
            this.shouldCancel = shouldCancel;
            this.signalCount = signalCount;
            this.confidence = confidence;
            this.label = label;
            this.debug = debug;
        }

        public boolean shouldFlag(int bufferRequired) {
            if (blatant) return true;
            if (hitboxPattern) return true;
            return signalCount >= 2 && confidence >= 0.70D;
        }

        static InteractionResult clean() {
            return new InteractionResult(false, false, false, false, false, false, false,
                    false, false, 0, 0.0D, "clean", "clean");
        }
    }
}
