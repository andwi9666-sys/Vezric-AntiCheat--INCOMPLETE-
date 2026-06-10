package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.math.AngleUtil;
import com.colin.vezanticheat.combat.math.BoundingBox;
import com.colin.vezanticheat.combat.math.RayTraceResult;
import com.colin.vezanticheat.combat.math.RayTraceUtil;
import com.colin.vezanticheat.data.PlayerCombatData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reach and aim geometry classification for one attack sample.
 * Ray traces through nested hitbox shells; only the tightest ping-allowed shell that
 * connects determines the expansion tier. High ping widens tolerance but does not
 * override impossible ray failures or extreme reach.
 */
public final class CombatHitClassifier {

    private static final int HIGH_PING_LENIENCY_THRESHOLD = 160;
    private static final int LOW_PING_SUSPICION_THRESHOLD = 50;

    private CombatHitClassifier() {}

    public static CombatHitResult classify(CombatSample sample, CombatConfig config) {
        return classify(sample, config, null, null);
    }

    public static CombatHitResult classify(CombatSample sample, CombatConfig config,
                                           PlayerCombatData attackerData, PlayerCombatData targetData) {
        if (sample == null || config == null) {
            return null;
        }

        UUID attackerUuid = sample.getAttackerUuid();
        UUID targetUuid = sample.getTargetUuid();
        if (attackerUuid == null || targetUuid == null) {
            return null;
        }

        Location eye = sample.getAttackerEye();
        Location targetBase = sample.getTargetLocation();
        if (eye == null || targetBase == null || eye.getWorld() == null || targetBase.getWorld() == null) {
            return null;
        }

        long attackTime = sample.getTimestampMs();
        long targetLeniencyWindow = config.getTargetKnockbackWindowMs();
        boolean targetKnockbackLeniency = hasRecentKnockbackChaos(targetData, attackTime, targetLeniencyWindow);

        int ping = Math.max(0, sample.getPingEstimate());
        // Low ping gets a smaller allowed expansion bucket, so borderline hits land in expansion tiers sooner.
        double allowedExpansion = config.getAllowedExpansionForPing(ping);
        if (targetKnockbackLeniency) {
            // Target velocity widens reach/expansion tolerance for classification, not attacker aim.
            allowedExpansion += config.getKnockbackTargetExpansionBonus();
        }

        float yaw = sample.getAttackerYaw();
        float pitch = sample.getAttackerPitch();
        Vector origin = eye.toVector();
        Vector direction = RayTraceUtil.getLookVector(yaw, pitch);

        double width = sample.getTargetWidth();
        double height = sample.getTargetHeight();
        double maxRayDistance = Math.max(config.getMaxBadReach() + 0.5D, 6.0D);

        BoundingBox normalBox = BoundingBox.fromFeet(targetBase, width, height);
        BoundingBox smallBox = normalBox.expand(config.getSmallHitboxExpansion());
        BoundingBox mediumBox = normalBox.expand(config.getMediumHitboxExpansion());
        BoundingBox fullBox = normalBox.expand(config.getFullHitboxExpansion());

        boolean normalHit = RayTraceUtil.rayIntersectsBox(origin, direction, normalBox, maxRayDistance);
        boolean smallHit = RayTraceUtil.rayIntersectsBox(origin, direction, smallBox, maxRayDistance);
        boolean mediumHit = RayTraceUtil.rayIntersectsBox(origin, direction, mediumBox, maxRayDistance);
        boolean fullHit = RayTraceUtil.rayIntersectsBox(origin, direction, fullBox, maxRayDistance);

        double reach = RayTraceUtil.distanceEyeToBox(origin, normalBox);

        AngleUtil.YawPitchError angularError = AngleUtil.getYawPitchError(eye, yaw, pitch, normalBox);
        double yawError = angularError.getYawError();
        double pitchError = angularError.getPitchError();

        boolean lineOfSightValid = resolveLineOfSight(sample);

        HitboxExpansionTier tier = resolveExpansionTier(
                origin, direction, normalBox, normalHit, smallHit, mediumHit, fullHit,
                allowedExpansion, maxRayDistance, config);
        boolean pingCompensated = resolvePingCompensated(tier, allowedExpansion);

        double reachBonus = targetKnockbackLeniency ? config.getKnockbackTargetReachBonus() : 0.0D;
        CombatHitClassification classification = classifyHit(reach, tier, config, reachBonus);
        double geometryScore = CombatScoreComposer.computeGeometryScore(
                tier, classification, ping, pingCompensated, lineOfSightValid, config);

        List<String> reasons = buildReasons(
                reach, classification, ping, allowedExpansion, tier, pingCompensated,
                lineOfSightValid, yawError, pitchError);
        if (targetKnockbackLeniency) {
            reasons.add("target knockback leniency applied");
        }

        Vector hitPoint = null;
        HitPointData hitPointData = null;
        if (tier != HitboxExpansionTier.MISS) {
            BoundingBox tierBox = resolveTierBox(
                    tier, normalBox, smallBox, mediumBox, fullBox, allowedExpansion);
            RayTraceResult trace = RayTraceUtil.traceRay(origin, direction, tierBox, maxRayDistance);
            if (trace.isHit()) {
                hitPoint = trace.getHitPoint();
                hitPointData = HitPointData.fromHit(
                        hitPoint, normalBox, normalHit, tier, sample.getTimestampMs());
                if (hitPointData != null) {
                    if (hitPointData.isEdgeHit()) {
                        reasons.add("hitPoint=edge");
                    }
                    if (hitPointData.isCenterLikeHit()) {
                        reasons.add("hitPoint=centerLike");
                    }
                    if (hitPointData.isExpansionShellHit()) {
                        reasons.add("hitPoint=expansionShell");
                    }
                }
            }
        }

        return CombatHitResult.builder()
                .attackerUuid(attackerUuid)
                .targetUuid(targetUuid)
                .classification(classification)
                .baseScore(geometryScore)
                .finalScore(geometryScore)
                .reachDistance(reach)
                .yawError(yawError)
                .pitchError(pitchError)
                .normalHitboxHit(normalHit)
                .smallExpansionHit(tier == HitboxExpansionTier.SMALL || tier == HitboxExpansionTier.MEDIUM)
                .fullExpansionHit(tier == HitboxExpansionTier.FULL)
                .lineOfSightValid(lineOfSightValid)
                .ping(ping)
                .allowedExpansion(allowedExpansion)
                .expansionTier(tier)
                .pingCompensated(pingCompensated)
                .hitPoint(hitPoint)
                .hitPointData(hitPointData)
                .reasons(reasons)
                .build();
    }

    private static BoundingBox resolveTierBox(HitboxExpansionTier tier, BoundingBox normalBox,
                                              BoundingBox smallBox, BoundingBox mediumBox,
                                              BoundingBox fullBox, double allowedExpansion) {
        switch (tier) {
            case SMALL:
                return smallBox;
            case MEDIUM:
                return mediumBox;
            case FULL:
                return fullBox;
            case NORMAL:
                return normalBox;
            default:
                return normalBox.expand(allowedExpansion);
        }
    }

    private static HitboxExpansionTier resolveExpansionTier(Vector origin, Vector direction,
                                                            BoundingBox normalBox,
                                                            boolean normalHit, boolean smallHit,
                                                            boolean mediumHit, boolean fullHit,
                                                            double allowedExpansion, double maxRayDistance,
                                                            CombatConfig config) {
        // Walk from tightest to widest shell; first connect wins as the gray-zone tier.
        if (normalHit) {
            return HitboxExpansionTier.NORMAL;
        }
        if (smallHit) {
            return HitboxExpansionTier.SMALL;
        }
        if (mediumHit) {
            return HitboxExpansionTier.MEDIUM;
        }
        if (fullHit) {
            return HitboxExpansionTier.FULL;
        }

        BoundingBox pingBox = normalBox.expand(allowedExpansion);
        if (RayTraceUtil.rayIntersectsBox(origin, direction, pingBox, maxRayDistance)) {
            return config.tierForAllowedExpansion(allowedExpansion);
        }
        return HitboxExpansionTier.MISS;
    }

    private static boolean resolvePingCompensated(HitboxExpansionTier tier, double allowedExpansion) {
        // Hit landed inside the ping-tolerated shell; score may be reduced but classification stands.
        if (tier == HitboxExpansionTier.NORMAL || tier == HitboxExpansionTier.MISS) {
            return false;
        }
        return tier.getExpansionAmount() <= allowedExpansion;
    }

    private static CombatHitClassification classifyHit(double reach, HitboxExpansionTier tier,
                                                       CombatConfig config, double reachBonus) {
        // Reach thresholds plus expansion tier map to gray-zone grades, not a binary legit/cheat verdict.
        double maxNormalReach = config.getMaxNormalReach() + reachBonus;
        double maxLenientReach = config.getMaxLenientReach() + reachBonus;
        double maxVeryLenientReach = config.getMaxVeryLenientReach() + reachBonus;

        if (tier == HitboxExpansionTier.NORMAL && reach <= maxNormalReach) {
            return CombatHitClassification.CLEAN;
        }
        if (tier == HitboxExpansionTier.SMALL || tier == HitboxExpansionTier.MEDIUM
                || reach <= maxLenientReach) {
            return CombatHitClassification.LENIENT;
        }
        if (tier == HitboxExpansionTier.FULL || reach <= maxVeryLenientReach) {
            return CombatHitClassification.VERY_LENIENT;
        }
        if (tier == HitboxExpansionTier.MISS && reach > config.getMaxBadReach()) {
            return CombatHitClassification.IMPOSSIBLE;
        }
        return CombatHitClassification.BAD;
    }

    private static boolean hasRecentKnockbackChaos(PlayerCombatData data, long nowMs, long windowMs) {
        if (data == null || windowMs <= 0L) {
            return false;
        }
        return data.recentlyVelocity(nowMs, windowMs) || data.recentlyDamaged(nowMs, windowMs);
    }

    private static List<String> buildReasons(double reach, CombatHitClassification classification,
                                             int ping, double allowedExpansion, HitboxExpansionTier tier,
                                             boolean pingCompensated, boolean lineOfSightValid,
                                             double yawError, double pitchError) {
        List<String> reasons = new ArrayList<String>();
        reasons.add("reach=" + round(reach));
        reasons.add("class=" + classification.name());
        reasons.add("ping=" + ping);
        reasons.add("allowedExpansion=" + round(allowedExpansion));
        reasons.add("expansionTier=" + tier.name());

        if (tier == HitboxExpansionTier.SMALL) {
            reasons.add("Hit only connected inside small +0.03 expansion");
        } else if (tier == HitboxExpansionTier.MEDIUM) {
            reasons.add("Hit only connected inside medium +0.06 expansion");
        } else if (tier == HitboxExpansionTier.FULL) {
            reasons.add("Hit only connected inside full +0.10 expansion");
        }

        if (ping <= LOW_PING_SUSPICION_THRESHOLD
                && (tier == HitboxExpansionTier.FULL || tier == HitboxExpansionTier.MEDIUM)
                && !pingCompensated) {
            reasons.add("Low ping player required expanded hitbox");
        }

        if (ping > HIGH_PING_LENIENCY_THRESHOLD && tier.isExpansionTier()) {
            reasons.add("High ping expansion leniency applied (-30% suspicion)");
        }

        if (pingCompensated) {
            reasons.add("Hit accepted within ping-allowed expansion");
        }

        if (!lineOfSightValid) {
            reasons.add("los=false");
        }
        if (yawError > 8.0D) {
            reasons.add("yaw=" + round(yawError));
        }
        if (pitchError > 8.0D) {
            reasons.add("pitch=" + round(pitchError));
        }

        return reasons;
    }

    private static boolean resolveLineOfSight(CombatSample sample) {
        Player attacker = sample.getAttacker();
        Player target = sample.getTarget();
        if (attacker != null && target != null && attacker.getWorld() != null) {
            try {
                return attacker.hasLineOfSight(target);
            } catch (Throwable ignored) {
                // Fall through when Bukkit LOS is unavailable.
            }
        }
        return true;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
