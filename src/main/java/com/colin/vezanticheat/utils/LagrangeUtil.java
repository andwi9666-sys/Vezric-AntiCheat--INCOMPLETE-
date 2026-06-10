package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Per-hit signal scoring for Lag Range (fake lag + stale-position reach).
 */
public final class LagrangeUtil {
    private LagrangeUtil() {}

    public static void observeCombatMovement(VezAntiCheat plugin, Player player, PlayerData data, long now) {
        if (plugin == null || player == null || data == null) return;

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) return;
        if (!from.getWorld().equals(to.getWorld())) return;
        if (data.isTeleportExempt() || data.isVelocityExempt() || data.isPotionExempt() || data.isBlockStateExempt()) return;

        long focusWindowMs = plugin.getConfig().getLong("lag.profile.combat-teleport.focus-window-ms", 2200L);
        UUID targetUuid = resolveCombatFocusTarget(data, now, focusWindowMs);
        Player target = targetUuid == null ? null : Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline() || target.equals(player)) {
            long intentWindowMs = plugin.getConfig().getLong("lag.profile.combat-teleport.intent-window-ms", 900L);
            double fallbackRange = plugin.getConfig().getDouble("lag.profile.combat-teleport.fallback-range", 6.5D);
            boolean combatIntent = now - data.getLastCombatInteractTime() <= intentWindowMs
                    || now - data.getLastDamageTime() <= intentWindowMs;
            if (!combatIntent) return;
            target = nearestNearbyPlayer(player, fallbackRange);
            if (target == null || target.equals(player)) return;
            targetUuid = target.getUniqueId();
            data.noteCombatTarget(targetUuid, now);
        }
        if (target.getGameMode() == null || target.getGameMode().name().equals("SPECTATOR")) return;

        PlayerData targetData = plugin.data().get(target);
        if (targetData == null) return;

        Location targetLoc = targetData.getLastLoc();
        if (targetLoc == null || targetLoc.getWorld() == null || !targetLoc.getWorld().equals(to.getWorld())) {
            targetLoc = target.getLocation();
        }
        if (targetLoc == null || targetLoc.getWorld() == null || !targetLoc.getWorld().equals(to.getWorld())) return;

        int minPingSamples = plugin.getConfig().getInt("lag.profile.combat-teleport.min-ping-samples", 8);
        if (data.getRecentPingSamples().size() < minPingSamples) return;

        double meanPing = LagProfileUtil.meanPing(data);
        double jitter = LagProfileUtil.pingJitter(data);
        int trustedPingMax = plugin.getConfig().getInt("lag.profile.combat-teleport.trusted-ping-max",
                plugin.getConfig().getInt("lag.profile.trusted-ping-max", 190));
        double trustedJitterMax = plugin.getConfig().getDouble("lag.profile.combat-teleport.trusted-jitter-max",
                plugin.getConfig().getDouble("lag.profile.trusted-jitter-max", 22.0D));
        if (meanPing <= 0.0D || meanPing > trustedPingMax || jitter > trustedJitterMax) return;

        long gapMs = data.getLastFlyingIntervalMs();
        long minGapMs = plugin.getConfig().getLong("lag.profile.combat-teleport.min-gap-ms", 170L);
        long gapExcessMs = plugin.getConfig().getLong("lag.profile.combat-teleport.gap-excess-ms", 25L);
        long allowance = Math.max(minGapMs, LagProfileUtil.dynamicAllowance(plugin, data) + gapExcessMs);
        if (gapMs < allowance) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double moveH = Math.hypot(dx, dz);
        double distBefore = horizontalDistance(from, targetLoc);
        double distAfter = horizontalDistance(to, targetLoc);
        double closeDelta = distBefore - distAfter;

        double minStartDistance = plugin.getConfig().getDouble("lag.profile.combat-teleport.min-start-distance", 4.6D);
        double maxEndDistance = plugin.getConfig().getDouble("lag.profile.combat-teleport.max-end-distance", 3.8D);
        if (distBefore < minStartDistance || distAfter > maxEndDistance) return;

        double ticks = Math.max(1.0D, Math.min(6.0D, gapMs / 50.0D));
        SpeedUtil.Context speed = SpeedUtil.analyze(plugin, player, data);
        double legitPerTick = speed == null
                ? plugin.getConfig().getDouble("lag.profile.combat-teleport.default-legit-per-tick", 0.36D)
                : Math.max(speed.expectedGround, speed.expectedAir);
        double minMoveH = plugin.getConfig().getDouble("lag.profile.combat-teleport.min-move-h", 1.15D);
        double minMoveOver = plugin.getConfig().getDouble("lag.profile.combat-teleport.min-move-over-legit", 0.40D);
        double allowedMove = (legitPerTick * ticks) + minMoveOver;
        if (moveH < Math.max(minMoveH, allowedMove)) return;

        double minCloseDelta = plugin.getConfig().getDouble("lag.profile.combat-teleport.min-close-delta", 1.30D);
        double minClosureRatio = plugin.getConfig().getDouble("lag.profile.combat-teleport.min-closure-ratio", 0.72D);
        if (closeDelta < minCloseDelta || closeDelta < (moveH * minClosureRatio)) return;

        int maxSamples = plugin.getConfig().getInt("lag.profile.combat-teleport.max-samples", 16);
        long windowMs = plugin.getConfig().getLong("lag.profile.combat-teleport.window-ms", 9000L);
        data.recordLagrangeTeleportSample(now, targetUuid, gapMs, moveH, closeDelta, distAfter, meanPing, jitter, maxSamples, windowMs);

        double gain = plugin.getConfig().getDouble("lag.profile.combat-teleport.score-gain", 1.15D);
        double closeBonus = Math.min(
                plugin.getConfig().getDouble("lag.profile.combat-teleport.max-close-bonus", 0.75D),
                Math.max(0.0D, closeDelta - minCloseDelta) * plugin.getConfig().getDouble("lag.profile.combat-teleport.close-bonus-factor", 0.22D)
        );
        data.setLagrangeTeleportScore(Math.min(
                plugin.getConfig().getDouble("lag.profile.combat-teleport.max-score", 10.0D),
                data.getLagrangeTeleportScore() + gain + closeBonus
        ));

        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(player.getUniqueId(), "LagrangeB", "teleport",
                    "gap=" + gapMs
                            + " moveH=" + round(moveH)
                            + " close=" + round(closeDelta)
                            + " end=" + round(distAfter)
                            + " target=" + target.getName()
                            + " score=" + round(data.getLagrangeTeleportScore()));
        }
    }

    public static CombatTeleportSummary summarizeCombatTeleports(PlayerData data, long now, long windowMs) {
        CombatTeleportSummary summary = new CombatTeleportSummary();
        if (data == null) return summary;
        Deque<PlayerData.CombatTeleportSample> samples = data.getLagrangeTeleportSamples();
        if (samples.isEmpty()) return summary;

        UUID firstTarget = null;
        Iterator<PlayerData.CombatTeleportSample> it = samples.descendingIterator();
        while (it.hasNext()) {
            PlayerData.CombatTeleportSample sample = it.next();
            if (now - sample.getTime() > windowMs) {
                break;
            }
            summary.count++;
            summary.totalCloseDelta += sample.getCloseDelta();
            summary.totalMoveH += sample.getMoveH();
            summary.totalGapMs += sample.getGapMs();
            summary.bestCloseDelta = Math.max(summary.bestCloseDelta, sample.getCloseDelta());
            summary.bestMoveH = Math.max(summary.bestMoveH, sample.getMoveH());
            summary.bestGapMs = Math.max(summary.bestGapMs, sample.getGapMs());
            if (firstTarget == null) {
                firstTarget = sample.getTargetUuid();
            }
            if (firstTarget != null && firstTarget.equals(sample.getTargetUuid())) {
                summary.sameTargetCount++;
            }
            summary.lastTargetUuid = sample.getTargetUuid();
            summary.lastEndDistance = sample.getEndDistance();
        }
        return summary;
    }

    public static LagrangeSample analyzeHit(
            VezAntiCheat plugin,
            String checkName,
            Player attacker,
            PlayerData attackerData,
            Player target,
            PlayerData targetData,
            CombatUtil.ReachContext ctx,
            CombatContextAnalyzer.CombatContext combat,
            long now) {

        if (plugin == null || attacker == null || attackerData == null || target == null
                || ctx == null || combat == null) {
            return LagrangeSample.skipped("null-input");
        }

        double maxReach = resolveLagrangeMaxReach(plugin, checkName);
        int ping = Math.max(0, PingUtil.getPing(attacker));
        long allowedRewindMs = CombatUtil.compensationWindowMs(
                ping,
                plugin.cfg().checkLong(checkName, "rewindBaseMs", 90L),
                plugin.cfg().checkDouble(checkName, "rewindPingFactor", 0.30),
                plugin.cfg().checkLong(checkName, "maxRewindMs", 200L)
        );

        double meanPing = LagProfileUtil.meanPing(attackerData);
        double jitter = LagProfileUtil.pingJitter(attackerData);
        int trustedPingMax = plugin.cfg().checkInt(checkName, "trustedPingMax", 175);
        double trustedJitterMax = plugin.cfg().checkDouble(checkName, "trustedJitterMax", 20.0D);

        if (shouldForgive(plugin, checkName, attackerData, targetData, combat, now, meanPing, jitter,
                trustedPingMax, trustedJitterMax)) {
            return LagrangeSample.forgiven("exempt");
        }

        Location eye = attackerData.getLastAttackEyeLocation();
        if (eye == null || eye.getWorld() == null) {
            eye = attacker.getEyeLocation();
        }

        double reachShift = ctx.getCurrentDistance() - ctx.getCompensatedDistance();
        long staleExcessMs = ctx.getCompensatedAgeMs() - allowedRewindMs;
        double minExpansion = Math.max(0.0, ctx.getCompensatedDistance() - maxReach);
        double minReachShift = plugin.cfg().checkDouble(checkName, "minReachShift", 0.14);
        long minStaleExcessMs = plugin.cfg().checkLong(checkName, "minStaleExcessMs", 35L);
        double minExpansionThreshold = plugin.cfg().checkDouble(checkName, "minExpansion", 0.04);
        double edgeReachMin = plugin.cfg().checkDouble(checkName, "edgeReachMin", maxReach);
        double edgeReachMax = plugin.cfg().checkDouble(checkName, "edgeReachMax", maxReach + 0.15D);

        boolean staleExcess = staleExcessMs >= minStaleExcessMs
                && ctx.getCompensatedDisplacement() >= plugin.cfg().checkDouble(checkName, "minDisplacement", 0.20);
        boolean reachShiftHit = reachShift >= minReachShift
                && ctx.getCompensatedDistance() <= maxReach + plugin.cfg().checkDouble(checkName, "historicalSlack", 0.12);
        boolean edgeReach = ctx.getCompensatedDistance() >= edgeReachMin
                && ctx.getCompensatedDistance() <= edgeReachMax;
        boolean expansionHit = minExpansion >= minExpansionThreshold;

        long attackWindowMs = plugin.getConfig().getLong("lag.profile.attack-window-ms", 900L);
        boolean gapActive = now - attackerData.getLastLagSpikeTime() <= attackWindowMs;
        boolean burstActive = now - attackerData.getLastLagBurstTime() <= attackWindowMs;
        boolean burstAttack = (gapActive || burstActive)
                && meanPing <= trustedPingMax
                && jitter <= trustedJitterMax;
        if (!burstAttack) {
            burstAttack = LagProfileUtil.isCombatCoverActive(plugin, attacker, attackerData, now);
        }
        boolean selectiveLag = LagProfileUtil.isSelectiveLagActive(plugin, attacker, attackerData, now);

        boolean stablePing = meanPing > 0.0 && meanPing <= trustedPingMax && jitter <= trustedJitterMax;

        double lookDotThreshold = plugin.cfg().checkDouble(checkName, "lookDotMin", 0.72);
        double angularMax = plugin.cfg().checkDouble(checkName, "angularErrorMax", 28.0);
        Location compensated = ctx.getCompensatedLocation();
        double lookDot = compensated != null
                ? CombatUtil.lookDotToHitbox(eye, compensated, ctx.getWidth(), ctx.getHeight())
                : -1.0;
        double angular = compensated != null
                ? CombatUtil.angularError(eye, compensated, ctx.getWidth(), ctx.getHeight())
                : 180.0;
        boolean lookInvalid = lookDot >= 0.0 && lookDot < lookDotThreshold && angular > angularMax;

        boolean losInvalid = false;
        int blockedPoints = 0;
        double walkThroughDistance = plugin.cfg().checkDouble(checkName, "walkThroughDistance", 0.75);
        if (!isWalkThroughRange(attacker, target, walkThroughDistance)) {
            OcclusionContext occlusion = analyzeOcclusion(target);
            int minOcclusion = plugin.cfg().checkInt(checkName, "minOcclusionNeighbors", 5);
            if (occlusion.totalBlockingNeighbors >= minOcclusion) {
                double step = plugin.cfg().checkDouble(checkName, "losStep", 0.20);
                double hitboxExpand = plugin.cfg().checkDouble(checkName, "hitboxExpand", 0.12);
                double endMargin = plugin.cfg().checkDouble(checkName, "endMargin", 0.40);
                int minBlockedPoints = plugin.cfg().checkInt(checkName, "minBlockedPoints", 6);
                VisiblePathStats pathStats = visiblePathStats(attacker, target, step, hitboxExpand, endMargin);
                blockedPoints = pathStats.blockedPoints;
                losInvalid = pathStats.visiblePoints == 0 && blockedPoints >= minBlockedPoints;
            }
        }

        double wStale = weight(plugin, checkName, "staleExcess", 1.25);
        double wShift = weight(plugin, checkName, "reachShift", 1.0);
        double wEdge = weight(plugin, checkName, "edgeReach", 0.75);
        double wExpand = weight(plugin, checkName, "minExpansion", 0.85);
        double wBurst = weight(plugin, checkName, "burstAttack", 1.1);
        double wLos = weight(plugin, checkName, "losInvalid", 1.2);
        double wLook = weight(plugin, checkName, "lookInvalid", 0.6);
        double stableMult = weight(plugin, checkName, "stablePing", 0.9);
        double selectiveLagWeight = weight(plugin, checkName, "selectiveLag", 0.35);
        double noSelectiveLagMultiplier = plugin.cfg().checkDouble(checkName, "noSelectiveLagMultiplier", 0.58);

        double confidence = 0.0;
        int timingFamily = 0;
        int reachFamily = 0;
        int spatialFamily = 0;
        StringBuilder tags = new StringBuilder();

        if (staleExcess) {
            confidence += wStale;
            timingFamily = 1;
            tags.append("stale ");
        }
        if (burstAttack) {
            confidence += wBurst;
            timingFamily = 1;
            tags.append("burst ");
        }
        if (selectiveLag) {
            confidence += selectiveLagWeight;
            tags.append("sel ");
        }
        if (reachShiftHit) {
            confidence += wShift;
            reachFamily = 1;
            tags.append("shift ");
        }
        if (edgeReach && expansionHit) {
            confidence += wEdge;
            reachFamily = 1;
            tags.append("edge ");
        }
        if (expansionHit && !edgeReach) {
            confidence += wExpand * 0.65;
            reachFamily = 1;
            tags.append("expand ");
        }
        if (losInvalid) {
            confidence += wLos;
            spatialFamily = 1;
            tags.append("los ");
        }
        if (lookInvalid && (staleExcess || reachShiftHit)) {
            confidence += wLook;
            spatialFamily = 1;
            tags.append("look ");
        }

        boolean timingEvidence = staleExcess || burstAttack;
        boolean reachEvidence = reachShiftHit || (edgeReach && expansionHit);
        boolean spatialEvidence = losInvalid || (lookInvalid && (staleExcess || reachShiftHit));
        long severeStaleExcessMs = plugin.cfg().checkLong(checkName, "severeStaleExcessMs", 85L);
        long emergencyStaleExcessMs = plugin.cfg().checkLong(checkName, "emergencyMinStaleExcessMs", 70L);
        double emergencyMinExpansion = plugin.cfg().checkDouble(checkName, "emergencyMinExpansion", 0.09D);
        boolean severeStale = staleExcessMs >= severeStaleExcessMs;
        boolean emergencyWithoutSelective = !selectiveLag
                && staleExcessMs >= emergencyStaleExcessMs
                && reachShiftHit
                && minExpansion >= emergencyMinExpansion
                && spatialEvidence;

        int families = timingFamily + reachFamily + spatialFamily;
        int minFamilies = plugin.cfg().checkInt(checkName, "minSignalFamilies", 2);
        if (families < minFamilies) {
            return LagrangeSample.clean("families=" + families);
        }

        if (!stablePing) {
            confidence *= 0.55;
            tags.append("unstable-ping ");
        } else {
            confidence *= stableMult;
        }

        if (reachShiftHit && !burstAttack && !staleExcess) {
            confidence *= 0.45;
            tags.append("shift-only ");
        }

        if (!selectiveLag) {
            if (!emergencyWithoutSelective || !timingEvidence || !reachEvidence) {
                return LagrangeSample.clean("no-selective-lag");
            }
            confidence *= noSelectiveLagMultiplier;
            tags.append("no-sel ");
        }

        return new LagrangeSample(
                confidence,
                reachShift,
                staleExcessMs,
                minExpansion,
                selectiveLag,
                burstAttack,
                staleExcess,
                reachEvidence,
                spatialEvidence,
                severeStale,
                losInvalid,
                lookInvalid,
                stablePing,
                families,
                blockedPoints,
                meanPing,
                jitter,
                tags.toString().trim()
        );
    }

    private static boolean shouldForgive(
            VezAntiCheat plugin,
            String checkName,
            PlayerData attackerData,
            PlayerData targetData,
            CombatContextAnalyzer.CombatContext combat,
            long now,
            double meanPing,
            double jitter,
            int trustedPingMax,
            double trustedJitterMax) {

        if (combat.isTrade() && combat.getSampleWeight() < plugin.cfg().checkDouble(checkName, "minTradeWeight", 0.55)) {
            return true;
        }
        if (combat.getSampleWeight() < plugin.cfg().checkDouble(checkName, "minSampleWeight", 0.40)) {
            return true;
        }
        if (combat.getHitSelectType() != CombatContextAnalyzer.HitSelectType.NONE) {
            return true;
        }
        if (attackerData.isTeleportExempt() || attackerData.isVelocityExempt()) {
            return true;
        }

        double tps = plugin.tps() != null ? plugin.tps().getTps() : 20.0;
        if (tps < plugin.cfg().minTps()) {
            return true;
        }

        if (meanPing > trustedPingMax || jitter > trustedJitterMax) {
            return true;
        }

        if (targetData != null) {
            long comboWindow = plugin.cfg().checkLong(checkName, "comboWindowMs", 800L);
            long timeSinceHit = now - targetData.getLastDamageTime();
            if (timeSinceHit >= 0L && timeSinceHit < comboWindow) {
                double comboTol = plugin.cfg().checkDouble(checkName, "comboTolerance", 0.22);
                long progress = timeSinceHit;
                double scale = 1.0 - ((progress / (double) comboWindow) * (progress / (double) comboWindow));
                if (scale * comboTol > 0.12) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double resolveLagrangeMaxReach(VezAntiCheat plugin, String checkName) {
        if ("PrismInteractionLegality".equals(checkName)) {
            return CombatUtil.resolveEffectiveMaxReach(plugin, checkName);
        }
        return plugin.cfg().checkDouble(checkName, "max", 3.10D);
    }

    private static double weight(VezAntiCheat plugin, String checkName, String key, double def) {
        ConfigurationSection section = plugin.cfg().checkSection(checkName);
        if (section == null) return def;
        ConfigurationSection weights = section.getConfigurationSection("weights");
        if (weights == null) return def;
        return weights.getDouble(key, def);
    }

    private static UUID resolveCombatFocusTarget(PlayerData data, long now, long focusWindowMs) {
        if (data == null) return null;
        if (data.getLagCombatFocusTargetUuid() != null && now - data.getLagCombatFocusTime() <= focusWindowMs) {
            return data.getLagCombatFocusTargetUuid();
        }
        if (data.getLagCombatDamagerUuid() != null && now - data.getLagCombatDamagerTime() <= focusWindowMs) {
            return data.getLagCombatDamagerUuid();
        }
        return null;
    }

    private static double horizontalDistance(Location a, Location b) {
        if (a == null || b == null) return 0.0D;
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private static Player nearestNearbyPlayer(Player player, double range) {
        if (player == null || range <= 0.0D) return null;
        Player best = null;
        double bestSq = range * range;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof Player)) continue;
            Player other = (Player) entity;
            if (!other.isOnline() || other.equals(player)) continue;
            if (other.getGameMode() != null && other.getGameMode().name().equals("SPECTATOR")) continue;
            if (other.getWorld() == null || !other.getWorld().equals(player.getWorld())) continue;
            double dx = other.getLocation().getX() - player.getLocation().getX();
            double dz = other.getLocation().getZ() - player.getLocation().getZ();
            double distSq = (dx * dx) + (dz * dz);
            if (distSq <= bestSq) {
                bestSq = distSq;
                best = other;
            }
        }
        return best;
    }

    private static double round(double v) {
        return Math.round(v * 100.0D) / 100.0D;
    }

    private static boolean isWalkThroughRange(Player attacker, Player target, double maxHorizontalDistance) {
        Location a = attacker.getLocation();
        Location b = target.getLocation();
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()) <= maxHorizontalDistance;
    }

    private static VisiblePathStats visiblePathStats(Player attacker, Player target,
                                                       double step, double hitboxExpand, double endMargin) {
        int visible = 0;
        int blocked = 0;
        if (attacker == null || target == null) return new VisiblePathStats(0, 0);
        if (attacker.hasLineOfSight(target)) return new VisiblePathStats(1, 0);
        Location from = attacker.getEyeLocation();
        for (Location point : targetSamplePoints(target)) {
            if (!CombatUtil.isRayBlockedBySolid(from, point, step, hitboxExpand, endMargin)) {
                visible++;
            } else {
                blocked++;
            }
        }
        return new VisiblePathStats(visible, blocked);
    }

    private static List<Location> targetSamplePoints(Player target) {
        Location base = target.getLocation();
        List<Location> points = new ArrayList<Location>(12);
        double feetY = base.getY() + 0.10;
        double midY = base.getY() + Math.min(0.95, target.getEyeHeight() * 0.55);
        double headY = base.getY() + Math.max(1.45, target.getEyeHeight() - 0.12);

        points.add(new Location(base.getWorld(), base.getX(), feetY, base.getZ()));
        points.add(new Location(base.getWorld(), base.getX() + 0.22, feetY, base.getZ() + 0.22));
        points.add(new Location(base.getWorld(), base.getX() - 0.22, feetY, base.getZ() - 0.22));
        points.add(new Location(base.getWorld(), base.getX(), midY, base.getZ()));
        points.add(new Location(base.getWorld(), base.getX() + 0.26, midY, base.getZ()));
        points.add(new Location(base.getWorld(), base.getX() - 0.26, midY, base.getZ()));
        points.add(new Location(base.getWorld(), base.getX(), midY, base.getZ() + 0.26));
        points.add(new Location(base.getWorld(), base.getX(), midY, base.getZ() - 0.26));
        points.add(new Location(base.getWorld(), base.getX(), headY, base.getZ()));
        points.add(new Location(base.getWorld(), base.getX() + 0.18, headY, base.getZ() - 0.18));
        points.add(new Location(base.getWorld(), base.getX() - 0.18, headY, base.getZ() + 0.18));
        return points;
    }

    private static OcclusionContext analyzeOcclusion(Player target) {
        Location base = target.getLocation();
        Block feet = base.getBlock();
        Block body = base.clone().add(0.0, 1.0, 0.0).getBlock();
        Block head = base.clone().add(0.0, 2.0, 0.0).getBlock();
        int feetNeighbors = countBlockingNeighbors(feet, false);
        int bodyNeighbors = countBlockingNeighbors(body, true);
        int headNeighbors = countBlockingNeighbors(head, true);
        return new OcclusionContext(feetNeighbors + bodyNeighbors + headNeighbors, bodyNeighbors);
    }

    private static int countBlockingNeighbors(Block block, boolean includeDown) {
        if (block == null) return 0;
        int count = 0;
        BlockFace[] faces = new BlockFace[] {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        };
        for (BlockFace face : faces) {
            if (isBlockingNeighbor(block.getRelative(face))) count++;
        }
        if (isBlockingNeighbor(block.getRelative(BlockFace.UP))) count++;
        if (includeDown && isBlockingNeighbor(block.getRelative(BlockFace.DOWN))) count++;
        return count;
    }

    private static boolean isBlockingNeighbor(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type == null) return false;
        if (type == Material.AIR) return false;
        if (type == Material.WATER || type == Material.STATIONARY_WATER) return false;
        if (type == Material.LAVA || type == Material.STATIONARY_LAVA) return false;
        return type.isSolid();
    }

    public static final class LagrangeSample {
        private final double confidence;
        private final double reachShift;
        private final long staleExcessMs;
        private final double minExpansion;
        private final boolean selectiveLag;
        private final boolean burstAttack;
        private final boolean staleExcess;
        private final boolean reachEvidence;
        private final boolean spatialEvidence;
        private final boolean severeStale;
        private final boolean losInvalid;
        private final boolean lookInvalid;
        private final boolean stablePing;
        private final int signalFamilies;
        private final int blockedPoints;
        private final double meanPing;
        private final double jitter;
        private final String tags;
        private final boolean forgiven;
        private final boolean skipped;

        private LagrangeSample(double confidence, double reachShift, long staleExcessMs, double minExpansion,
                               boolean selectiveLag, boolean burstAttack, boolean staleExcess,
                               boolean reachEvidence, boolean spatialEvidence, boolean severeStale,
                               boolean losInvalid, boolean lookInvalid, boolean stablePing,
                               int signalFamilies, int blockedPoints, double meanPing, double jitter, String tags) {
            this.confidence = confidence;
            this.reachShift = reachShift;
            this.staleExcessMs = staleExcessMs;
            this.minExpansion = minExpansion;
            this.selectiveLag = selectiveLag;
            this.burstAttack = burstAttack;
            this.staleExcess = staleExcess;
            this.reachEvidence = reachEvidence;
            this.spatialEvidence = spatialEvidence;
            this.severeStale = severeStale;
            this.losInvalid = losInvalid;
            this.lookInvalid = lookInvalid;
            this.stablePing = stablePing;
            this.signalFamilies = signalFamilies;
            this.blockedPoints = blockedPoints;
            this.meanPing = meanPing;
            this.jitter = jitter;
            this.tags = tags == null ? "" : tags;
            this.forgiven = false;
            this.skipped = false;
        }

        private LagrangeSample(boolean forgiven, boolean skipped, String tags) {
            this.confidence = 0.0;
            this.reachShift = 0.0;
            this.staleExcessMs = 0L;
            this.minExpansion = 0.0;
            this.selectiveLag = false;
            this.burstAttack = false;
            this.staleExcess = false;
            this.reachEvidence = false;
            this.spatialEvidence = false;
            this.severeStale = false;
            this.losInvalid = false;
            this.lookInvalid = false;
            this.stablePing = false;
            this.signalFamilies = 0;
            this.blockedPoints = 0;
            this.meanPing = 0.0;
            this.jitter = 0.0;
            this.tags = tags == null ? "" : tags;
            this.forgiven = forgiven;
            this.skipped = skipped;
        }

        public static LagrangeSample forgiven(String reason) {
            return new LagrangeSample(true, false, reason);
        }

        public static LagrangeSample skipped(String reason) {
            return new LagrangeSample(false, true, reason);
        }

        public static LagrangeSample clean(String reason) {
            return new LagrangeSample(false, true, reason);
        }

        public double getConfidence() { return confidence; }
        public double getReachShift() { return reachShift; }
        public long getStaleExcessMs() { return staleExcessMs; }
        public double getMinExpansion() { return minExpansion; }
        public boolean isSelectiveLag() { return selectiveLag; }
        public boolean isBurstAttack() { return burstAttack; }
        public boolean isStaleExcess() { return staleExcess; }
        public boolean isReachEvidence() { return reachEvidence; }
        public boolean isSpatialEvidence() { return spatialEvidence; }
        public boolean isSevereStale() { return severeStale; }
        public boolean isLosInvalid() { return losInvalid; }
        public boolean isLookInvalid() { return lookInvalid; }
        public boolean isStablePing() { return stablePing; }
        public int getSignalFamilies() { return signalFamilies; }
        public int getBlockedPoints() { return blockedPoints; }
        public double getMeanPing() { return meanPing; }
        public double getJitter() { return jitter; }
        public String getTags() { return tags; }
        public boolean isForgiven() { return forgiven; }
        public boolean isSkipped() { return skipped; }

        public String debugLine() {
            if (forgiven || skipped) return tags;
            return "conf=" + round(confidence)
                    + " shift=" + round(reachShift)
                    + " stale+" + staleExcessMs
                    + " expand=" + round(minExpansion)
                    + " fam=" + signalFamilies
                    + " sel=" + selectiveLag
                    + " ping=" + round(meanPing)
                    + " jitter=" + round(jitter)
                    + " blocked=" + blockedPoints
                    + " [" + tags + "]";
        }

        private static double round(double v) {
            return Math.round(v * 100.0) / 100.0;
        }
    }

    private static final class VisiblePathStats {
        private final int visiblePoints;
        private final int blockedPoints;

        private VisiblePathStats(int visiblePoints, int blockedPoints) {
            this.visiblePoints = visiblePoints;
            this.blockedPoints = blockedPoints;
        }
    }

    private static final class OcclusionContext {
        private final int totalBlockingNeighbors;

        private OcclusionContext(int totalBlockingNeighbors, int bodyBlockingNeighbors) {
            this.totalBlockingNeighbors = totalBlockingNeighbors;
        }
    }

    public static final class CombatTeleportSummary {
        private int count;
        private int sameTargetCount;
        private double totalCloseDelta;
        private double totalMoveH;
        private double totalGapMs;
        private double bestCloseDelta;
        private double bestMoveH;
        private double bestGapMs;
        private double lastEndDistance;
        private UUID lastTargetUuid;

        public int getCount() { return count; }
        public int getSameTargetCount() { return sameTargetCount; }
        public double getAverageCloseDelta() { return count <= 0 ? 0.0D : totalCloseDelta / count; }
        public double getAverageMoveH() { return count <= 0 ? 0.0D : totalMoveH / count; }
        public double getAverageGapMs() { return count <= 0 ? 0.0D : totalGapMs / count; }
        public double getBestCloseDelta() { return bestCloseDelta; }
        public double getBestMoveH() { return bestMoveH; }
        public double getBestGapMs() { return bestGapMs; }
        public double getLastEndDistance() { return lastEndDistance; }
        public UUID getLastTargetUuid() { return lastTargetUuid; }
        public boolean hasAny() { return count > 0; }
    }
}
