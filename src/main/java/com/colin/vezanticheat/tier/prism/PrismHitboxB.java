package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.CombatRewind;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.HitboxUtil;
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

/** Statistical hitbox violation pattern detection (Prism tier). */
public final class PrismHitboxB extends TierCheck {

    private static final ConcurrentHashMap<UUID, HitboxSamples> STATE = new ConcurrentHashMap<UUID, HitboxSamples>();

    private static final class HitboxSamples {
        final Deque<Double> missDistances = new ArrayDeque<Double>();
        int violations;
        long lastSampleMs;
    }

    public PrismHitboxB(VezAntiCheat plugin) {
        super(plugin, "PrismHitboxB", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        long now = System.currentTimeMillis();
        if (!data.wasLastUseEntityAttack()) return;
        if (now - data.getLastUseEntityTime() > plugin.tierCfg().checkLong(name(), "attackFreshnessMs", 150L)) return;
        if (data.isVelocityExempt() || data.isTeleportExempt()) return;

        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (target == null) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean()) {
            decay(p, 0.25D);
            return;
        }

        int ping = Math.max(0, PingUtil.getPing(p));
        long rewindMs = CombatUtil.compensationWindowMs(
                ping,
                plugin.tierCfg().checkLong(name(), "rewindBaseMs", 80L),
                plugin.tierCfg().checkDouble(name(), "rewindPingFactor", 0.35D),
                plugin.tierCfg().checkLong(name(), "maxRewindMs", 200L)
        );

        Location eye = data.getLastAttackEyeLocation();
        if (eye == null || eye.getWorld() == null) {
            eye = HitboxUtil.buildPacketSyncedEye(p, data);
        }

        PlayerData targetData = target instanceof Player ? plugin.data().get((Player) target) : null;
        CombatUtil.ReachContext ctx = resolveReachContext(data, eye, target, targetData, data.getLastUseEntityTime(), rewindMs);
        if (ctx == null) return;

        if (ctx.getCompensatedDistance() < plugin.tierCfg().checkDouble(name(), "minDistance", 1.2D)) {
            decay(p, 0.25D);
            return;
        }

        Location compensated = ctx.getCompensatedLocation();
        double width = ctx.getWidth();
        double height = ctx.getHeight();

        double vanillaExpansion = plugin.tierCfg().checkDouble(name(), "vanillaExpansion",
                CombatUtil.vanillaExpansion(plugin));
        double pingExpansion = Math.min(
                plugin.tierCfg().checkDouble(name(), "maxPingExpansion", 0.12D),
                ping * plugin.tierCfg().checkDouble(name(), "pingExpansionFactor", 0.0006D)
        );
        double kbExpansion = HitboxUtil.knockbackExpansion(plugin, data, targetData, combat, now, name());
        double totalExpansion = vanillaExpansion + pingExpansion + kbExpansion;

        List<Location> lookCandidates = HitboxUtil.attackLookCandidates(plugin, data, eye, now, name());
        CombatUtil.RayTraceResult result = HitboxUtil.bestRayTraceToHitbox(
                lookCandidates, compensated, width, height, totalExpansion, 6.0D);

        if (result == null) return;

        double missDistance = result.isHit() ? 0.0D : result.getMissDistance();
        if (!result.isHit()) {
            double flickGrace = HitboxUtil.flickMissGrace(plugin, data, now, ctx.getCompensatedDistance(), name());
            missDistance = Math.max(0.0D, missDistance - flickGrace);
        }

        if (HitboxUtil.isRecentHeadFlick(plugin, data, now, name())) {
            decay(p, 0.30D);
            return;
        }

        UUID playerId = p.getUniqueId();
        HitboxSamples samples = STATE.computeIfAbsent(playerId, k -> new HitboxSamples());

        long windowMs = plugin.tierCfg().checkLong(name(), "windowMs", 8000L);
        if (samples.lastSampleMs > 0 && (now - samples.lastSampleMs) > windowMs) {
            samples.missDistances.clear();
            samples.violations = 0;
        }

        int maxSamples = plugin.tierCfg().checkInt(name(), "maxSamples", 15);
        samples.missDistances.addLast(Double.valueOf(missDistance));
        while (samples.missDistances.size() > maxSamples) samples.missDistances.removeFirst();
        samples.lastSampleMs = now;

        int minSamples = plugin.tierCfg().checkInt(name(), "minSamples", 8);
        if (samples.missDistances.size() < minSamples) {
            decay(p, 0.25D);
            return;
        }

        double graceThreshold = plugin.tierCfg().checkDouble(name(), "graceThreshold", 0.02D);
        int missCount = 0;
        double totalMissDistance = 0.0D;
        for (double d : samples.missDistances) {
            if (d > graceThreshold) {
                missCount++;
                totalMissDistance += d;
            }
        }

        double missRatio = (double) missCount / samples.missDistances.size();
        double avgMiss = missCount > 0 ? totalMissDistance / missCount : 0.0D;

        double missRatioThreshold = plugin.tierCfg().checkDouble(name(), "missRatioThreshold", 0.50D);
        double avgMissThreshold = plugin.tierCfg().checkDouble(name(), "avgMissThreshold", 0.08D);

        if (missRatio >= missRatioThreshold && avgMiss >= avgMissThreshold) {
            samples.violations++;

            int violationsToFlag = plugin.tierCfg().checkInt(name(), "violationsToFlag", 2);
            if (samples.violations < violationsToFlag) {
                verbose(p, "viol=" + samples.violations + "/" + violationsToFlag
                        + " missRatio=" + round3(missRatio) + " avgMiss=" + round3(avgMiss));
            }
            if (samples.violations >= violationsToFlag) {
                fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.2D),
                        "pattern missRatio=" + round3(missRatio)
                                + " avgMiss=" + round3(avgMiss) + " blocks"
                                + " misses=" + missCount + "/" + samples.missDistances.size()
                                + " violations=" + samples.violations
                                + " dist=" + round3(ctx.getCompensatedDistance())
                                + " expansion=" + round3(totalExpansion)
                                + " ping=" + ping
                                + " " + combat.debugSummary());
                samples.missDistances.clear();
                samples.violations = 0;
            }
        } else {
            if (missRatio < 0.20D && samples.violations > 0) {
                samples.violations = Math.max(0, samples.violations - 1);
            }
            decay(p, 0.35D);
        }
    }

    public static void clearState(UUID player) {
        STATE.remove(player);
    }

    private CombatUtil.ReachContext resolveReachContext(
            PlayerData data, Location eye, Entity target, PlayerData targetData,
            long attackTime, long rewindMs) {
        if (plugin.getConfig().getBoolean("combat-engine.enabled", true)) {
            com.colin.vezanticheat.engine.CombatResult engine = data == null ? null : data.getLastCombatResult();
            if (engine != null && engine.tracked) {
                CombatUtil.ReachContext ctx = CombatRewind.toReachContext(engine);
                if (ctx != null) return ctx;
            }
        }
        return CombatUtil.analyzeReach(eye, target, targetData, attackTime, rewindMs);
    }
}
