package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Reduces combat-analysis false positives for laggy, desynced, or messy fight conditions.
 * High ping and recent teleports dampen scores or defer impossible cancel; they do not grant blanket immunity.
 */
public final class CombatFalsePositiveGuard {

    private static final double MIN_MULTIPLIER = 0.05D;

    private CombatFalsePositiveGuard() {}

    public static boolean shouldSkipCombatAnalysis(VezAntiCheat plugin, Player attacker, Player target,
                                                   long nowMs) {
        if (plugin == null || attacker == null || target == null) {
            return false;
        }
        CombatConfig config = resolveConfig(plugin);
        PlayerData attackerData = plugin.data().get(attacker);
        PlayerData targetData = plugin.data().get(target);
        long typicalTeleportExemptMs = plugin.cfg() != null ? plugin.cfg().teleportExemptMs() : 900L;
        return shouldSkipCombatAnalysis(config, attackerData, targetData, nowMs, typicalTeleportExemptMs);
    }

    static boolean shouldSkipCombatAnalysis(CombatConfig config, PlayerData attackerData, PlayerData targetData,
                                            long nowMs, long typicalTeleportExemptMs) {
        if (config == null) {
            return false;
        }
        if (attackerData != null
                && attackerData.isWithinCombatJoinGrace(nowMs, config.getFpJoinSkipMs())) {
            return true;
        }
        if (targetData != null
                && targetData.isWithinCombatJoinGrace(nowMs, config.getFpJoinSkipMs())) {
            return true;
        }
        if (attackerData != null
                && isWithinRecentTeleport(attackerData, nowMs, config.getFpTeleportSkipMs(), typicalTeleportExemptMs)) {
            return true;
        }
        if (targetData != null
                && isWithinRecentTeleport(targetData, nowMs, config.getFpTeleportSkipMs(), typicalTeleportExemptMs)) {
            return true;
        }
        return false;
    }

    public static double getFalsePositiveMultiplier(VezAntiCheat plugin, CombatSample sample) {
        if (plugin == null || sample == null) {
            return 1.0D;
        }
        CombatConfig config = resolveConfig(plugin);
        PlayerData attackerData = sample.getAttacker() != null ? plugin.data().get(sample.getAttacker()) : null;
        PlayerData targetData = sample.getTarget() != null ? plugin.data().get(sample.getTarget()) : null;
        long typicalTeleportExemptMs = plugin.cfg() != null ? plugin.cfg().teleportExemptMs() : 900L;
        return getGlobalMultiplier(config, sample, attackerData, targetData, nowMs(sample),
                typicalTeleportExemptMs, getTps(plugin));
    }

    public static double getBehaviorMultiplier(VezAntiCheat plugin, CombatSample sample) {
        if (sample == null) {
            return 1.0D;
        }
        CombatConfig config = plugin != null ? resolveConfig(plugin) : CombatConfig.defaults();
        double multiplier = 1.0D;
        if (sample.getPingEstimate() > config.getFpHighAttackerPing()) {
            multiplier *= config.getFpAttackerPingAimMultiplier();
        }
        // Sub-1.2 block fights produce noisy aim data; dampen behavior scores, not geometry.
        if (sample.getAttackDistance() > 0.0D
                && sample.getAttackDistance() < config.getFpCloseRangeBlocks()) {
            multiplier *= config.getFpCloseRangeAimMultiplier();
        }
        if (hasLargeVerticalOffset(sample, config.getFpVerticalOffsetBlocks())) {
            multiplier *= config.getFpVerticalOffsetMultiplier();
        }
        return clampMultiplier(multiplier);
    }

    public static double getGeometryMultiplier(VezAntiCheat plugin, CombatSample sample) {
        if (sample == null) {
            return 1.0D;
        }
        CombatConfig config = plugin != null ? resolveConfig(plugin) : CombatConfig.defaults();
        // High target ping widens reach tolerance in geometry scoring, not behavior.
        if (sample.getTargetPingEstimate() > config.getFpHighTargetPing()) {
            return clampMultiplier(config.getFpTargetPingReachMultiplier());
        }
        return 1.0D;
    }

    public static boolean shouldSuppressAlerts(VezAntiCheat plugin) {
        return shouldSuppressAlerts(getTps(plugin), resolveConfig(plugin));
    }

    static boolean shouldSuppressAlerts(double tps, CombatConfig config) {
        if (config == null) {
            return false;
        }
        return tps < config.getFpTpsAlertSuppressThreshold();
    }

    public static boolean shouldLenientImpossibleRaytrace(VezAntiCheat plugin, Player attacker, Player target,
                                                            long nowMs) {
        if (plugin == null || !resolveConfig(plugin).isFpTeleportLenientImpossible()) {
            return false;
        }
        // After teleport, defer impossible-ray cancel; extreme reach still classifies IMPOSSIBLE.
        long typicalTeleportExemptMs = plugin.cfg() != null ? plugin.cfg().teleportExemptMs() : 900L;
        CombatConfig config = resolveConfig(plugin);
        PlayerData attackerData = attacker != null ? plugin.data().get(attacker) : null;
        PlayerData targetData = target != null ? plugin.data().get(target) : null;
        return (attackerData != null && isWithinRecentTeleport(
                attackerData, nowMs, config.getFpTeleportSkipMs(), typicalTeleportExemptMs))
                || (targetData != null && isWithinRecentTeleport(
                targetData, nowMs, config.getFpTeleportSkipMs(), typicalTeleportExemptMs));
    }

    static double getGlobalMultiplier(CombatConfig config, CombatSample sample,
                                      PlayerData attackerData, PlayerData targetData, long nowMs,
                                      long typicalTeleportExemptMs, double tps) {
        if (config == null || sample == null) {
            return 1.0D;
        }
        double multiplier = 1.0D;

        if (tps < config.getFpTpsReduceThreshold()) {
            multiplier *= config.getFpTpsSuspicionMultiplier();
        }

        if (attackerData != null && attackerData.isVelocityExempt()) {
            multiplier *= config.getFpVelocityMultiplier();
        }
        if (targetData != null && targetData.isVelocityExempt()) {
            multiplier *= config.getFpVelocityMultiplier();
        }

        Player attacker = sample.getAttacker();
        Player target = sample.getTarget();
        if (attacker != null && CombatContextAnalyzer.isTightEnvironment(attacker)) {
            multiplier *= config.getFpTightSpaceMultiplier();
        }
        if (target != null && CombatContextAnalyzer.isTightEnvironment(target)) {
            multiplier *= config.getFpTightSpaceMultiplier();
        }
        if (attacker != null && isNearSolidBlock(attacker)) {
            multiplier *= config.getFpNearBlockMultiplier();
        }
        if (target != null && isNearSolidBlock(target)) {
            multiplier *= config.getFpNearBlockMultiplier();
        }

        if ((attackerData != null && isPartialTeleportLeniency(
                attackerData, nowMs, config.getFpTeleportSkipMs(), typicalTeleportExemptMs))
                || (targetData != null && isPartialTeleportLeniency(
                targetData, nowMs, config.getFpTeleportSkipMs(), typicalTeleportExemptMs))) {
            multiplier *= config.getFpRecentTeleportMultiplier();
        }

        return clampMultiplier(multiplier);
    }

    static boolean isWithinRecentTeleport(PlayerData data, long nowMs, long recentWindowMs,
                                          long typicalExemptMs) {
        if (data == null || nowMs >= data.getTeleportExemptUntilMs()) {
            return false;
        }
        long remaining = data.getTeleportExemptUntilMs() - nowMs;
        return remaining > Math.max(0L, typicalExemptMs - recentWindowMs);
    }

    static boolean isPartialTeleportLeniency(PlayerData data, long nowMs, long recentWindowMs,
                                             long typicalExemptMs) {
        if (data == null || nowMs >= data.getTeleportExemptUntilMs()) {
            return false;
        }
        long remaining = data.getTeleportExemptUntilMs() - nowMs;
        long skipThreshold = Math.max(0L, typicalExemptMs - recentWindowMs);
        return remaining <= skipThreshold;
    }

    static boolean isNearSolidBlock(Player player) {
        if (player == null || player.getLocation() == null) {
            return false;
        }
        Location base = player.getLocation();
        return isSolid(base.getBlock())
                || isSolid(base.clone().add(0.0, 1.0, 0.0).getBlock())
                || isSolid(base.clone().add(0.32, 0.0, 0.0).getBlock())
                || isSolid(base.clone().add(-0.32, 0.0, 0.0).getBlock())
                || isSolid(base.clone().add(0.0, 0.0, 0.32).getBlock())
                || isSolid(base.clone().add(0.0, 0.0, -0.32).getBlock());
    }

    static boolean hasLargeVerticalOffset(CombatSample sample, double thresholdBlocks) {
        Location attackerLoc = sample.getAttackerLocation();
        Location targetLoc = sample.getTargetLocation();
        if (attackerLoc == null || targetLoc == null) {
            return false;
        }
        return Math.abs(targetLoc.getY() - attackerLoc.getY()) >= thresholdBlocks;
    }

    private static boolean isSolid(Block block) {
        return block != null && block.getType() != Material.AIR && block.getType().isSolid();
    }

    private static long nowMs(CombatSample sample) {
        return sample.getTimestampMs() > 0L ? sample.getTimestampMs() : System.currentTimeMillis();
    }

    private static double getTps(VezAntiCheat plugin) {
        return plugin != null && plugin.tps() != null ? plugin.tps().getTps() : 20.0D;
    }

    private static CombatConfig resolveConfig(VezAntiCheat plugin) {
        if (plugin != null && plugin.combat() != null) {
            return plugin.combat().getConfig();
        }
        return CombatConfig.defaults();
    }

    static double clampMultiplier(double multiplier) {
        return Math.max(MIN_MULTIPLIER, Math.min(1.0D, multiplier));
    }
}
