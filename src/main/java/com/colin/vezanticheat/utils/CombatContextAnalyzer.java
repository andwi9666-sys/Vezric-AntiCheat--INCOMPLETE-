package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class CombatContextAnalyzer {
    private CombatContextAnalyzer() {}

    public static CombatContext analyze(VezAntiCheat plugin, Player attacker, PlayerData attackerData, String checkName) {
        if (plugin == null || attacker == null || attackerData == null) return null;

        long now = System.currentTimeMillis();
        long attackWindowMs = plugin.cfg().checkLong(checkName, "attackWindowMs",
                plugin.getConfig().getLong("combat-analysis.attack-window-ms", 325L));
        if (!attackerData.wasLastUseEntityAttack() || now - attackerData.getLastUseEntityTime() > attackWindowMs) {
            return null;
        }

        Entity target = CombatUtil.resolveTarget(attacker, attackerData.getLastTargetUuid());
        if (target == null) return null;

        PlayerData targetData = target instanceof Player ? plugin.data().get((Player) target) : null;
        int attackerPing = Math.max(0, PingUtil.getPing(attacker));
        int targetPing = target instanceof Player ? Math.max(0, PingUtil.getPing((Player) target)) : 0;
        double attackerJitter = jitter(attackerData.getFlyingIntervals(), attackerData.getLastFlyingIntervalMs());
        double targetJitter = targetData == null ? 0.0 : jitter(targetData.getFlyingIntervals(), targetData.getLastFlyingIntervalMs());

        long rewindMs = CombatUtil.compensationWindowMs(
                attackerPing,
                plugin.cfg().checkLong(checkName, "rewindBaseMs",
                        plugin.getConfig().getLong("combat-analysis.rewind-base-ms", 70L)),
                plugin.cfg().checkDouble(checkName, "rewindPingFactor",
                        plugin.getConfig().getDouble("combat-analysis.rewind-ping-factor", 0.25D)),
                plugin.cfg().checkLong(checkName, "maxRewindMs",
                        plugin.getConfig().getLong("combat-analysis.max-rewind-ms", 180L))
        );

        Location eye = attackerData.getLastAttackEyeLocation();
        if (eye == null || eye.getWorld() == null) {
            eye = attacker.getEyeLocation();
        }

        CombatUtil.ReachContext reach = CombatUtil.analyzeReach(eye, target, targetData,
                attackerData.getLastUseEntityTime(), rewindMs);
        if (reach == null) return null;

        long tradeWindowMs = plugin.getConfig().getLong("combat-analysis.trade-window-ms", 450L);
        long comboWindowMs = plugin.getConfig().getLong("combat-analysis.combo-window-ms", 800L);
        long jumpResetMs = plugin.getConfig().getLong("combat-analysis.jump-reset-window-ms", 220L);
        long blockhitMs = plugin.getConfig().getLong("combat-analysis.blockhit-window-ms", 250L);
        double walkThroughDistance = plugin.getConfig().getDouble("combat-analysis.walk-through-distance", 1.10D);
        long parryMinMs = plugin.getConfig().getLong("combat-analysis.hit-select.parry-min-ms", 100L);
        long parryMaxMs = plugin.getConfig().getLong("combat-analysis.hit-select.parry-max-ms", 250L);
        long quarterMinMs = plugin.getConfig().getLong("combat-analysis.hit-select.quarter-min-ms", 500L);
        long quarterMaxMs = plugin.getConfig().getLong("combat-analysis.hit-select.quarter-max-ms", 1000L);
        long deepMinMs = plugin.getConfig().getLong("combat-analysis.hit-select.deep-min-ms", 1000L);

        boolean trade = now - attackerData.getLastDamageTime() <= tradeWindowMs
                || (targetData != null && now - targetData.getLastDamageTime() <= tradeWindowMs);
        boolean combo = targetData != null && now - targetData.getLastDamageTime() <= comboWindowMs;
        boolean recentJump = now - attackerData.getLastJumpTime() <= jumpResetMs;
        boolean recentBlockhit = now - attackerData.getLastCombatInteractTime() <= blockhitMs;
        boolean airborne = !attacker.isOnGround();
        boolean targetAirborne = target instanceof Player && !((Player) target).isOnGround();
        boolean nearWalkThrough = reach.getCurrentDistance() <= walkThroughDistance;
        boolean tightEnvironment = isTightEnvironment(attacker) || (target instanceof Player && isTightEnvironment((Player) target));

        long moveAgeMs = now - attackerData.getLastMoveMillis();
        long rotationAgeMs = now - attackerData.getLastRotationPacket();
        long swingAgeMs = attackerData.getLastAttackSwingDeltaMs();
        long attackInterval = last(attackerData.getAttackIntervals());
        double clickStd = std(attackerData.getClickIntervals());
        double attackStd = std(attackerData.getAttackIntervals());
        long selfDamageAgeMs = attackerData.getLastDamageTime() <= 0L ? Long.MAX_VALUE : now - attackerData.getLastDamageTime();
        HitSelectType hitSelectType = classifyHitSelect(selfDamageAgeMs, parryMinMs, parryMaxMs, quarterMinMs, quarterMaxMs, deepMinMs);
        long combatSpamWindowMs = plugin.getConfig().getLong("combat-analysis.combat-spam-window-ms", 900L);
        int combatSpamMinAttacks = plugin.getConfig().getInt("combat-analysis.combat-spam-min-attacks", 4);
        int recentAttacks = recentAttackCount(attackerData.getAttackTimestamps(), now, combatSpamWindowMs);
        boolean activeCombatSpam = recentAttacks >= combatSpamMinAttacks;
        boolean legitCombatMovement = isLikelyLegitCombatMovement(
                plugin, attacker, attackerData, now, recentAttacks, jumpCombatWindowMs(plugin));

        int tradeDepth = recentAttackCount(attackerData.getAttackTimestamps(), now,
                plugin.getConfig().getLong("combat-analysis.trade-depth-window-ms", 1200L));
        boolean spacingManipulation = nearWalkThrough
                || (combo && reach.getCompensatedDisplacement() >= plugin.getConfig().getDouble("combat-analysis.spacing-shift-threshold", 0.28D));
        boolean tripleHitLikely = isTripleHitLikely(attackerData.getAttackIntervals(),
                plugin.getConfig().getLong("combat-analysis.triple-hit-fast-ms", 350L),
                plugin.getConfig().getLong("combat-analysis.triple-hit-delay-ms", 525L));
        boolean defensiveTrade = trade && recentJump;
        boolean packetBurst = attackerData.getLastFlyingIntervalMs() >= plugin.getConfig().getLong("combat-analysis.packet-burst-ms", 110L)
                || attackerJitter >= plugin.getConfig().getDouble("combat-analysis.packet-burst-jitter-ms", 18.0D);

        double quality = 1.0D;
        List<String> notes = new ArrayList<String>();

        if (attackerJitter >= plugin.getConfig().getDouble("combat-analysis.high-jitter-ms", 12.0D)) {
            quality -= 0.20D;
            notes.add("attacker-jitter");
        }
        if (targetJitter >= plugin.getConfig().getDouble("combat-analysis.high-jitter-ms", 12.0D)) {
            quality -= 0.12D;
            notes.add("target-jitter");
        }
        if (packetBurst) {
            quality -= 0.18D;
            notes.add("packet-burst");
        }
        if (attackerData.isTeleportExempt()) {
            quality -= 0.45D;
            notes.add("teleport");
        }
        if (attackerData.isVelocityExempt()) {
            quality -= 0.20D;
            notes.add("recent-velocity");
        }
        if (trade) {
            quality -= 0.12D;
            notes.add("trade");
        }
        if (combo) {
            quality -= 0.08D;
            notes.add("combo");
        }
        if (hitSelectType != HitSelectType.NONE) {
            quality -= hitSelectType == HitSelectType.PARRY ? 0.10D : 0.14D;
            notes.add("hit-select-" + hitSelectType.name().toLowerCase());
        }
        if (tripleHitLikely) {
            quality -= 0.08D;
            notes.add("triple-hit");
        }
        if (recentJump) {
            quality -= 0.18D;
            notes.add("recent-jump");
        }
        if (recentBlockhit) {
            quality -= 0.10D;
            notes.add("recent-blockhit");
        }
        if (defensiveTrade) {
            quality -= 0.08D;
            notes.add("defensive-trade");
        }
        if (airborne || targetAirborne) {
            quality -= 0.12D;
            notes.add("airborne");
        }
        if (tightEnvironment) {
            quality -= 0.20D;
            notes.add("collision");
        }
        if (nearWalkThrough) {
            quality -= 0.10D;
            notes.add("walk-through");
        }
        if (spacingManipulation) {
            quality -= 0.08D;
            notes.add("spacing");
        }
        if (moveAgeMs > plugin.getConfig().getLong("combat-analysis.stale-move-ms", 150L)) {
            quality -= 0.10D;
            notes.add("stale-move");
        }
        if (rotationAgeMs > plugin.getConfig().getLong("combat-analysis.stale-rotation-ms", 140L)) {
            quality -= 0.12D;
            notes.add("stale-rotation");
        }
        if (swingAgeMs != Long.MAX_VALUE && swingAgeMs > plugin.getConfig().getLong("combat-analysis.stale-swing-ms", 180L)) {
            quality -= 0.12D;
            notes.add("stale-swing");
        }

        if (activeCombatSpam) {
            quality -= 0.10D;
            notes.add("combat-spam");
        }
        if (legitCombatMovement) {
            quality -= 0.14D;
            notes.add("legit-combat-move");
        }

        quality = clamp(quality, 0.0D, 1.0D);
        double packetConfidence = clamp(1.0D - ((attackerJitter + targetJitter) / 40.0D), 0.0D, 1.0D);
        double sampleWeight = clamp((quality * 0.75D) + (packetConfidence * 0.25D), 0.0D, 1.0D);

        return new CombatContext(
                now,
                attacker,
                target,
                attackerData,
                targetData,
                attackerPing,
                targetPing,
                attackerJitter,
                targetJitter,
                moveAgeMs,
                rotationAgeMs,
                swingAgeMs,
                attackInterval,
                clickStd,
                attackStd,
                selfDamageAgeMs,
                airborne,
                targetAirborne,
                trade,
                combo,
                hitSelectType,
                tradeDepth,
                tripleHitLikely,
                spacingManipulation,
                defensiveTrade,
                recentJump,
                recentBlockhit,
                nearWalkThrough,
                tightEnvironment,
                packetBurst,
                activeCombatSpam,
                legitCombatMovement,
                quality,
                packetConfidence,
                sampleWeight,
                reach,
                notes
        );
    }

    public static boolean isTightEnvironment(Player player) {
        if (player == null || player.getLocation() == null) return false;
        Location base = player.getLocation();
        return isSolid(base.clone().add(0.0, 2.0, 0.0).getBlock())
                || isSolid(base.clone().add(0.32, 0.0, 0.0).getBlock())
                || isSolid(base.clone().add(-0.32, 0.0, 0.0).getBlock())
                || isSolid(base.clone().add(0.0, 0.0, 0.32).getBlock())
                || isSolid(base.clone().add(0.0, 0.0, -0.32).getBlock())
                || isSolid(base.clone().add(0.32, 1.0, 0.0).getBlock())
                || isSolid(base.clone().add(-0.32, 1.0, 0.0).getBlock())
                || isSolid(base.clone().add(0.0, 1.0, 0.32).getBlock())
                || isSolid(base.clone().add(0.0, 1.0, -0.32).getBlock());
    }

    private static boolean isSolid(Block block) {
        return block != null && block.getType() != Material.AIR && block.getType().isSolid();
    }

    private static double jitter(Deque<Long> values, long lastInterval) {
        if (values == null || values.isEmpty()) {
            return lastInterval <= 0L ? 0.0D : Math.abs(lastInterval - 50.0D);
        }
        return std(values);
    }

    private static double std(Deque<Long> values) {
        if (values == null || values.size() < 3) return 0.0D;
        double mean = 0.0D;
        int count = 0;
        for (Long value : values) {
            if (value == null) continue;
            mean += value.longValue();
            count++;
        }
        if (count == 0) return 0.0D;
        mean /= count;

        double variance = 0.0D;
        for (Long value : values) {
            if (value == null) continue;
            double delta = value.longValue() - mean;
            variance += delta * delta;
        }
        return Math.sqrt(variance / count);
    }

    private static long last(Deque<Long> values) {
        Long last = values == null ? null : values.peekLast();
        return last == null ? 0L : last.longValue();
    }

    /** High-frequency left-click combat (standing still, S-tap chains, etc.). */
    public static boolean isActiveCombatSpam(PlayerData data, long nowMs) {
        if (data == null) return false;
        long windowMs = 900L;
        return recentAttackCount(data.getAttackTimestamps(), nowMs, windowMs) >= 4;
    }

    /**
     * Active PvP engagement: rapid successful hits or swing+attack bursts.
     * Used to suppress soft interaction signals (stale rotation, hitbox pattern) during legit spam clicking.
     */
    public static boolean isActivePvpEngagement(PlayerData data, long nowMs) {
        if (data == null) return false;
        if (isActiveCombatSpam(data, nowMs) || isLikelyLegitCombatMovement(data, nowMs)) {
            return true;
        }
        if (recentAttackCount(data.getAttackTimestamps(), nowMs, 650L) >= 3) {
            return true;
        }
        long swingAge = data.getLastArmSwingPacket() > 0L
                ? nowMs - data.getLastArmSwingPacket()
                : Long.MAX_VALUE;
        return swingAge <= 140L
                && recentAttackCount(data.getAttackTimestamps(), nowMs, 450L) >= 2;
    }

    /** Soft combat interaction flags (not blatant reach) during legit PvP spam / movement. */
    public static boolean shouldExemptCombatInteractionFlagging(CombatContext combat, PlayerData data,
                                                                  long anchorMs) {
        long now = anchorMs > 0L ? anchorMs : System.currentTimeMillis();
        if (isActivePvpEngagement(data, now)) {
            return true;
        }
        if (combat != null && (combat.isActiveCombatSpam() || combat.isLegitCombatMovement())) {
            return true;
        }
        return isKbDisplacementFlick(data, anchorMs, 220L);
    }

    /**
     * Suppress aim/heuristic checks during legit high-CPS PvP (spam click, jump crit chains,
     * W-tap / forward rush).
     */
    public static boolean shouldExemptAimHeuristics(CombatContext combat, PlayerData data, Player player,
                                                    long nowMs) {
        return shouldExemptAimHeuristics(null, combat, data, player, nowMs);
    }

    public static boolean shouldExemptAimHeuristics(VezAntiCheat plugin, CombatContext combat, PlayerData data,
                                                    Player player, long nowMs) {
        if (data == null) return false;
        if (isActivePvpEngagement(data, nowMs)) return true;
        if (isLikelySpacingMovement(plugin, data, player, nowMs)) return true;
        if (isLikelyCounterstrafeSpacing(plugin, data, nowMs)) return true;
        if (combat != null && (combat.isActiveCombatSpam() || combat.isLegitCombatMovement())) return true;
        return isJumpCombatEngagement(combat, data, player, nowMs, jumpCombatWindowMs(plugin));
    }

    public static long spacingAttackWindowMs(VezAntiCheat plugin) {
        if (plugin == null) return 650L;
        return plugin.getConfig().getLong("combat-analysis.spacing-attack-window-ms", 650L);
    }

    public static long counterstrafeWindowMs(VezAntiCheat plugin) {
        if (plugin == null) return 450L;
        return plugin.getConfig().getLong("combat-analysis.counterstrafe-window-ms", 450L);
    }

    public static double spacingBackwardMinAngle(VezAntiCheat plugin) {
        if (plugin == null) return 110.0D;
        return plugin.getConfig().getDouble("combat-analysis.spacing-backward-min-angle", 110.0D);
    }

    public static double spacingMinSpeed(VezAntiCheat plugin) {
        if (plugin == null) return 0.04D;
        return plugin.getConfig().getDouble("combat-analysis.spacing-min-speed", 0.04D);
    }

    /** Backward strafe / S-tap spacing while attacking. */
    public static boolean isLikelySpacingMovement(PlayerData data, Player player, long nowMs) {
        return isLikelySpacingMovement(null, data, player, nowMs);
    }

    public static boolean isLikelySpacingMovement(VezAntiCheat plugin, PlayerData data, Player player, long nowMs) {
        if (data == null) return false;
        long attackWindow = spacingAttackWindowMs(plugin);
        if (data.getLastUseEntityTime() <= 0L || nowMs - data.getLastUseEntityTime() > attackWindow) {
            return false;
        }

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) return false;
        if (!from.getWorld().equals(to.getWorld())) return false;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double speed = Math.hypot(dx, dz);
        if (speed < spacingMinSpeed(plugin)) return false;

        float moveDir = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yaw = to.getYaw();
        if (player != null && player.getLocation() != null) {
            yaw = player.getLocation().getYaw();
        }

        float backwardness = CombatUtil.angleDiff(moveDir, yaw);
        double minAngle = spacingBackwardMinAngle(plugin);
        return backwardness >= minAngle && backwardness <= 180.0F;
    }

    /** S→W counterstrafe or brief backward-then-forward rush into a hit. */
    public static boolean isLikelyCounterstrafeSpacing(PlayerData data, long nowMs) {
        return isLikelyCounterstrafeSpacing(null, data, nowMs);
    }

    public static boolean isLikelyCounterstrafeSpacing(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (data == null) return false;
        long attackWindow = spacingAttackWindowMs(plugin);
        if (data.getLastUseEntityTime() <= 0L || nowMs - data.getLastUseEntityTime() > attackWindow) {
            return false;
        }
        long window = counterstrafeWindowMs(plugin);
        if (hasRecentHorizontalReversal(data, nowMs, window)) return true;
        return hasBackwardThenForwardPattern(plugin, data, nowMs, window);
    }

    public static long jumpCombatWindowMs(VezAntiCheat plugin) {
        if (plugin == null) return 950L;
        long combatWindow = plugin.getConfig().getLong("combat-analysis.jump-combat-window-ms", 950L);
        if (combatWindow > 0L) return combatWindow;
        return plugin.getConfig().getLong("movement-analysis.jump-arc-window-ms", 950L);
    }

    /**
     * Legit KB displacement: rapid out-and-back yaw oscillation before an attack.
     * Differs from aim-assist snaps, which are single large turns that stay on target.
     */
    public static boolean isKbDisplacementFlick(PlayerData data, long anchorMs, long windowMs) {
        if (data == null || anchorMs <= 0L || windowMs <= 0L) return false;

        float minStep = 28.0F;
        int signChanges = 0;
        int largeSteps = 0;
        Float prevSigned = null;
        PlayerData.PositionSample previous = null;

        for (PlayerData.PositionSample sample : data.getPositionHistory()) {
            if (sample == null) continue;
            long age = anchorMs - sample.getTime();
            if (age < 0L || age > windowMs) continue;

            if (previous != null) {
                float signed = signedYawDelta(previous.getYaw(), sample.getYaw());
                if (Math.abs(signed) >= minStep) {
                    largeSteps++;
                    if (prevSigned != null
                            && Math.signum(signed) != Math.signum(prevSigned)
                            && Math.abs(prevSigned) >= minStep) {
                        signChanges++;
                    }
                    prevSigned = signed;
                }
            }
            previous = sample;
        }
        return signChanges >= 1 && largeSteps >= 2;
    }

    private static float signedYawDelta(float from, float to) {
        float diff = to - from;
        while (diff > 180.0F) diff -= 360.0F;
        while (diff < -180.0F) diff += 360.0F;
        return diff;
    }

    /** W-tap / S-tap / counterstrafe, standing still, or sprint-jump forward rush while trading hits. */
    public static boolean isLikelyLegitCombatMovement(PlayerData data, long nowMs) {
        if (data == null) return false;
        return isLikelyLegitCombatMovement(null, null, data, nowMs,
                recentAttackCount(data.getAttackTimestamps(), nowMs, 900L), 950L);
    }

    private static boolean isLikelyLegitCombatMovement(VezAntiCheat plugin, Player player, PlayerData data,
                                                       long nowMs, int recentAttacks, long jumpCombatWindowMs) {
        if (data == null) return false;
        long attackWindow = spacingAttackWindowMs(plugin);
        if (nowMs - data.getLastUseEntityTime() > attackWindow) return false;

        if (recentAttacks < 2) {
            return isLikelySpacingMovement(plugin, data, player, nowMs)
                    || isLikelyCounterstrafeSpacing(plugin, data, nowMs);
        }

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from != null && to != null && from.getWorld() != null && to.getWorld() != null
                && from.getWorld().equals(to.getWorld())) {
            double distH = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
            if (distH <= 0.08D) return true;
            if (isForwardJumpRush(data, nowMs, recentAttacks, jumpCombatWindowMs, distH)) return true;
        } else if (nowMs - data.getLastMoveMillis() > 120L && recentAttacks >= 2) {
            return true;
        }

        return hasRecentHorizontalReversal(data, nowMs, counterstrafeWindowMs(plugin))
                || isLikelySpacingMovement(plugin, data, player, nowMs)
                || isLikelyCounterstrafeSpacing(plugin, data, nowMs);
    }

    private static boolean isForwardJumpRush(PlayerData data, long nowMs, int recentAttacks,
                                             long jumpCombatWindowMs, double distH) {
        if (recentAttacks < 2 || jumpCombatWindowMs <= 0L) return false;
        if (data.getLastJumpTime() <= 0L || nowMs - data.getLastJumpTime() > jumpCombatWindowMs) return false;
        return distH >= 0.06D && distH <= 0.65D;
    }

    private static boolean isJumpCombatEngagement(CombatContext combat, PlayerData data, Player player,
                                                  long nowMs, long jumpCombatWindowMs) {
        if (data.getLastJumpTime() <= 0L || nowMs - data.getLastJumpTime() > jumpCombatWindowMs) return false;
        if (recentAttackCount(data.getAttackTimestamps(), nowMs, 900L) < 2) return false;
        if (combat != null && (combat.isAirborne() || combat.isRecentJump())) return true;
        return player != null && !player.isOnGround();
    }

    private static boolean hasRecentHorizontalReversal(PlayerData data, long nowMs, long windowMs) {
        PlayerData.PositionSample previous = null;
        PlayerData.PositionSample beforePrevious = null;
        for (PlayerData.PositionSample sample : data.getPositionHistory()) {
            if (sample == null) continue;
            long age = nowMs - sample.getTime();
            if (age < 0L || age > windowMs) continue;
            beforePrevious = previous;
            previous = sample;
        }
        if (previous == null || beforePrevious == null) return false;

        double dx1 = previous.getX() - beforePrevious.getX();
        double dz1 = previous.getZ() - beforePrevious.getZ();
        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return false;
        double dx2 = to.getX() - from.getX();
        double dz2 = to.getZ() - from.getZ();
        double m1 = Math.hypot(dx1, dz1);
        double m2 = Math.hypot(dx2, dz2);
        if (m1 < 0.04D || m2 < 0.04D) return false;
        double dot = (dx1 * dx2) + (dz1 * dz2);
        return dot < -(0.22D * m1 * m2);
    }

    private static boolean hasBackwardThenForwardPattern(VezAntiCheat plugin, PlayerData data,
                                                         long nowMs, long windowMs) {
        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return false;

        double dx2 = to.getX() - from.getX();
        double dz2 = to.getZ() - from.getZ();
        double forwardSpeed = Math.hypot(dx2, dz2);
        if (forwardSpeed < spacingMinSpeed(plugin)) return false;

        float forwardDir = (float) Math.toDegrees(Math.atan2(-dx2, dz2));
        if (CombatUtil.angleDiff(forwardDir, to.getYaw()) > 70.0F) return false;

        PlayerData.PositionSample previous = null;
        for (PlayerData.PositionSample sample : data.getPositionHistory()) {
            if (sample == null) continue;
            long age = nowMs - sample.getTime();
            if (age < 0L || age > windowMs) continue;
            if (previous != null) {
                double dx1 = sample.getX() - previous.getX();
                double dz1 = sample.getZ() - previous.getZ();
                double backSpeed = Math.hypot(dx1, dz1);
                if (backSpeed >= spacingMinSpeed(plugin)) {
                    float backDir = (float) Math.toDegrees(Math.atan2(-dx1, dz1));
                    if (CombatUtil.angleDiff(backDir, sample.getYaw()) >= spacingBackwardMinAngle(plugin)) {
                        return true;
                    }
                }
            }
            previous = sample;
        }
        return false;
    }

    private static int recentAttackCount(Deque<Long> values, long now, long windowMs) {
        if (values == null || values.isEmpty()) return 0;
        int count = 0;
        for (Long value : values) {
            if (value != null && now - value.longValue() <= windowMs) {
                count++;
            }
        }
        return count;
    }

    private static HitSelectType classifyHitSelect(long ageMs, long parryMinMs, long parryMaxMs,
                                                   long quarterMinMs, long quarterMaxMs, long deepMinMs) {
        if (ageMs < 0L || ageMs == Long.MAX_VALUE) return HitSelectType.NONE;
        if (ageMs >= parryMinMs && ageMs <= parryMaxMs) return HitSelectType.PARRY;
        if (ageMs >= quarterMinMs && ageMs <= quarterMaxMs) return HitSelectType.QUARTER;
        if (ageMs >= deepMinMs) return HitSelectType.DEEP;
        return HitSelectType.NONE;
    }

    private static boolean isTripleHitLikely(Deque<Long> intervals, long fastMs, long delayMs) {
        if (intervals == null || intervals.size() < 3) return false;
        Long[] arr = intervals.toArray(new Long[0]);
        long a = arr[arr.length - 3] == null ? 0L : arr[arr.length - 3].longValue();
        long b = arr[arr.length - 2] == null ? 0L : arr[arr.length - 2].longValue();
        long c = arr[arr.length - 1] == null ? 0L : arr[arr.length - 1].longValue();
        if (a <= 0L || b <= 0L || c <= 0L) return false;
        return a <= fastMs && b <= fastMs && c >= delayMs;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static final class CombatContext {
        private final long now;
        private final Player attacker;
        private final Entity target;
        private final PlayerData attackerData;
        private final PlayerData targetData;
        private final int attackerPing;
        private final int targetPing;
        private final double attackerJitter;
        private final double targetJitter;
        private final long moveAgeMs;
        private final long rotationAgeMs;
        private final long swingAgeMs;
        private final long attackIntervalMs;
        private final double clickStdMs;
        private final double attackIntervalStdMs;
        private final long selfDamageAgeMs;
        private final boolean airborne;
        private final boolean targetAirborne;
        private final boolean trade;
        private final boolean combo;
        private final HitSelectType hitSelectType;
        private final int tradeDepth;
        private final boolean tripleHitLikely;
        private final boolean spacingManipulation;
        private final boolean defensiveTrade;
        private final boolean recentJump;
        private final boolean recentBlockhit;
        private final boolean walkThrough;
        private final boolean tightEnvironment;
        private final boolean packetBurst;
        private final boolean activeCombatSpam;
        private final boolean legitCombatMovement;
        private final double sampleCleanliness;
        private final double packetConfidence;
        private final double sampleWeight;
        private final CombatUtil.ReachContext reach;
        private final List<String> notes;

        public CombatContext(long now, Player attacker, Entity target, PlayerData attackerData, PlayerData targetData,
                             int attackerPing, int targetPing, double attackerJitter, double targetJitter,
                             long moveAgeMs, long rotationAgeMs, long swingAgeMs, long attackIntervalMs,
                             double clickStdMs, double attackIntervalStdMs, long selfDamageAgeMs,
                             boolean airborne, boolean targetAirborne, boolean trade, boolean combo,
                             HitSelectType hitSelectType, int tradeDepth, boolean tripleHitLikely,
                             boolean spacingManipulation, boolean defensiveTrade, boolean recentJump, boolean recentBlockhit,
                             boolean walkThrough, boolean tightEnvironment, boolean packetBurst,
                             boolean activeCombatSpam, boolean legitCombatMovement,
                             double sampleCleanliness, double packetConfidence, double sampleWeight,
                             CombatUtil.ReachContext reach, List<String> notes) {
            this.now = now;
            this.attacker = attacker;
            this.target = target;
            this.attackerData = attackerData;
            this.targetData = targetData;
            this.attackerPing = attackerPing;
            this.targetPing = targetPing;
            this.attackerJitter = attackerJitter;
            this.targetJitter = targetJitter;
            this.moveAgeMs = moveAgeMs;
            this.rotationAgeMs = rotationAgeMs;
            this.swingAgeMs = swingAgeMs;
            this.attackIntervalMs = attackIntervalMs;
            this.clickStdMs = clickStdMs;
            this.attackIntervalStdMs = attackIntervalStdMs;
            this.selfDamageAgeMs = selfDamageAgeMs;
            this.airborne = airborne;
            this.targetAirborne = targetAirborne;
            this.trade = trade;
            this.combo = combo;
            this.hitSelectType = hitSelectType;
            this.tradeDepth = tradeDepth;
            this.tripleHitLikely = tripleHitLikely;
            this.spacingManipulation = spacingManipulation;
            this.defensiveTrade = defensiveTrade;
            this.recentJump = recentJump;
            this.recentBlockhit = recentBlockhit;
            this.walkThrough = walkThrough;
            this.tightEnvironment = tightEnvironment;
            this.packetBurst = packetBurst;
            this.activeCombatSpam = activeCombatSpam;
            this.legitCombatMovement = legitCombatMovement;
            this.sampleCleanliness = sampleCleanliness;
            this.packetConfidence = packetConfidence;
            this.sampleWeight = sampleWeight;
            this.reach = reach;
            this.notes = notes == null ? new ArrayList<String>() : notes;
        }

        public long getNow() { return now; }
        public Player getAttacker() { return attacker; }
        public Entity getTarget() { return target; }
        public PlayerData getAttackerData() { return attackerData; }
        public PlayerData getTargetData() { return targetData; }
        public int getAttackerPing() { return attackerPing; }
        public int getTargetPing() { return targetPing; }
        public double getAttackerJitter() { return attackerJitter; }
        public double getTargetJitter() { return targetJitter; }
        public long getMoveAgeMs() { return moveAgeMs; }
        public long getRotationAgeMs() { return rotationAgeMs; }
        public long getSwingAgeMs() { return swingAgeMs; }
        public long getAttackIntervalMs() { return attackIntervalMs; }
        public double getClickStdMs() { return clickStdMs; }
        public double getAttackIntervalStdMs() { return attackIntervalStdMs; }
        public long getSelfDamageAgeMs() { return selfDamageAgeMs; }
        public boolean isAirborne() { return airborne; }
        public boolean isTargetAirborne() { return targetAirborne; }
        public boolean isTrade() { return trade; }
        public boolean isCombo() { return combo; }
        public HitSelectType getHitSelectType() { return hitSelectType; }
        public int getTradeDepth() { return tradeDepth; }
        public boolean isTripleHitLikely() { return tripleHitLikely; }
        public boolean isSpacingManipulation() { return spacingManipulation; }
        public boolean isDefensiveTrade() { return defensiveTrade; }
        public boolean isRecentJump() { return recentJump; }
        public boolean isRecentBlockhit() { return recentBlockhit; }
        public boolean isWalkThrough() { return walkThrough; }
        public boolean isTightEnvironment() { return tightEnvironment; }
        public boolean isPacketBurst() { return packetBurst; }
        public boolean isActiveCombatSpam() { return activeCombatSpam; }
        public boolean isLegitCombatMovement() { return legitCombatMovement; }
        public double getSampleCleanliness() { return sampleCleanliness; }
        public double getPacketConfidence() { return packetConfidence; }
        public double getSampleWeight() { return sampleWeight; }
        public CombatUtil.ReachContext getReach() { return reach; }
        public boolean isClean() { return sampleCleanliness >= 0.55D && packetConfidence >= 0.35D; }
        public boolean isPrecisionSample() {
            if (activeCombatSpam || legitCombatMovement) return false;
            return sampleCleanliness >= 0.62D
                    && !tightEnvironment
                    && !packetBurst
                    && (!trade || sampleCleanliness >= 0.72D)
                    && (!recentJump || sampleCleanliness >= 0.74D);
        }

        public String debugSummary() {
            return "clean=" + round(sampleCleanliness)
                    + " weight=" + round(sampleWeight)
                    + " pc=" + round(packetConfidence)
                    + " ping=" + attackerPing + "/" + targetPing
                    + " jitter=" + round(attackerJitter) + "/" + round(targetJitter)
                    + " moveAge=" + moveAgeMs
                    + " rotAge=" + rotationAgeMs
                    + " swingAge=" + swingAgeMs
                    + " trade=" + trade
                    + " combo=" + combo
                    + " select=" + hitSelectType.name().toLowerCase()
                    + " depth=" + tradeDepth
                    + " triple=" + tripleHitLikely
                    + " spacing=" + spacingManipulation
                    + " jump=" + recentJump
                    + " blockhit=" + recentBlockhit
                    + " walkThrough=" + walkThrough
                    + " env=" + tightEnvironment
                    + (notes.isEmpty() ? "" : " notes=" + notes.toString());
        }

        private double round(double value) {
            return Math.round(value * 100.0D) / 100.0D;
        }
    }

    public enum HitSelectType {
        NONE,
        PARRY,
        QUARTER,
        DEEP
    }
}
