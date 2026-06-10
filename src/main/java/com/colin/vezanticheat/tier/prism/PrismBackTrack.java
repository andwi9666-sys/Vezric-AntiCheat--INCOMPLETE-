package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.CombatRewind;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.LagrangeUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Deque;

/**
 * Stale-position / backtrack reach exploit detection (Prism tier).
 * Port of legacy BackTrackA using tierCfg thresholds and TierCheck VL/buffer pipeline.
 */
public final class PrismBackTrack extends TierCheck {

    public PrismBackTrack(VezAntiCheat plugin) {
        super(plugin, "PrismBackTrack", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (PlayerData.bypass(p)) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;
        if (!shouldProcessAttack(p, data)) return;

        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (!(target instanceof Player)) return;

        Player targetPlayer = (Player) target;
        PlayerData targetData = plugin.data().get(targetPlayer);
        if (targetData == null) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean()) {
            decay(p, 0.35D);
            return;
        }

        long now = System.currentTimeMillis();
        int ping = Math.max(0, PingUtil.getPing(p));
        long allowedRewindMs = CombatUtil.compensationWindowMs(
                ping,
                plugin.tierCfg().checkLong(name(), "rewindBaseMs", 95L),
                plugin.tierCfg().checkDouble(name(), "rewindPingFactor", 0.25D),
                plugin.tierCfg().checkLong(name(), "maxRewindMs", 190L)
        );
        long analysisWindowMs = allowedRewindMs + plugin.tierCfg().checkLong(name(), "extraHistoryMs", 160L);

        Location eye = data.getLastAttackEyeLocation();
        if (eye == null || eye.getWorld() == null) {
            eye = p.getEyeLocation();
        }

        CombatUtil.ReachContext ctx = CombatRewind.broadWindowReachContext(
                plugin, p, data, eye, data.getLastTargetEntityId(), data.getLastUseEntityTime(), analysisWindowMs);
        if (ctx == null) {
            ctx = CombatUtil.analyzeReach(
                    eye,
                    targetPlayer,
                    targetData,
                    data.getLastUseEntityTime(),
                    analysisWindowMs
            );
        }
        if (ctx == null) return;

        double max = plugin.tierCfg().checkDouble(name(), "max", 3.20D);
        double currentSlack = plugin.tierCfg().checkDouble(name(), "currentSlack", 0.18D);
        double historicalSlack = plugin.tierCfg().checkDouble(name(), "historicalSlack", 0.10D);
        long minExcessRewindMs = plugin.tierCfg().checkLong(name(), "minExcessRewindMs", 45L);
        double kbScale = plugin.cfg().kbHorizontalScale();
        double minDisplacement = plugin.tierCfg().checkDouble(name(), "minDisplacement", 0.32D) * kbScale;
        double minShift = plugin.tierCfg().checkDouble(name(), "minShift", 0.24D) * kbScale;
        long staleExcessMs = Math.max(0L, ctx.getCompensatedAgeMs() - allowedRewindMs);

        long timeSinceTargetHit = now - targetData.getLastDamageTime();
        long comboWindow = plugin.tierCfg().checkLong(name(), "comboWindowMs", 800L);
        boolean inCombo = timeSinceTargetHit >= 0L && timeSinceTargetHit < comboWindow;
        double comboTolerance = 0.0D;
        if (inCombo) {
            comboTolerance = plugin.tierCfg().checkDouble(name(), "comboTolerance", 0.28D);
            double progress = timeSinceTargetHit / (double) comboWindow;
            comboTolerance *= 1.0D - (progress * progress);
        }

        double shift = ctx.getCurrentDistance() - ctx.getCompensatedDistance();
        double currentOver = Math.max(0.0D, ctx.getCurrentDistance() - max);
        boolean stalePositionHit = ctx.getCurrentDistance() > (max + currentSlack + comboTolerance)
                && ctx.getCompensatedDistance() <= (max + historicalSlack + (comboTolerance * 0.40D))
                && ctx.getCompensatedAgeMs() > (allowedRewindMs + minExcessRewindMs)
                && ctx.getCompensatedDisplacement() >= (minDisplacement + (comboTolerance * 0.55D))
                && shift >= (minShift + (comboTolerance * 0.45D));

        long attackerGapMs = data.getLastFlyingIntervalMs();
        long targetGapMs = targetData.getLastFlyingIntervalMs();
        boolean attackerDelaySpike = attackerGapMs >= plugin.tierCfg().checkLong(name(), "attackerPacketGapMs", 130L);
        boolean targetDelaySpike = targetGapMs >= plugin.tierCfg().checkLong(name(), "targetPacketGapMs", 180L);
        boolean delayPattern = attackerDelaySpike || targetDelaySpike;

        long moveAge = data.getLastFlyingPacket() <= 0L ? Long.MAX_VALUE : (now - data.getLastFlyingPacket());
        long rotationAge = data.getLastRotationPacket() <= 0L ? Long.MAX_VALUE : (now - data.getLastRotationPacket());
        boolean moveOrderSuspicious = moveAge >= plugin.tierCfg().checkLong(name(), "attackWithoutMoveMs", 110L);
        boolean rotationOrderSuspicious = rotationAge >= plugin.tierCfg().checkLong(name(), "attackWithoutRotateMs", 145L);
        boolean orderPattern = moveOrderSuspicious || rotationOrderSuspicious;

        Integer previousPing = data.hasBackTrackALastPing() ? Integer.valueOf(data.getBackTrackALastPing()) : null;
        data.setBackTrackALastPing(ping);
        data.setBackTrackAHasLastPing(true);
        int pingDelta = previousPing == null ? 0 : Math.abs(previousPing.intValue() - ping);
        boolean suddenPingShift = previousPing != null
                && pingDelta >= plugin.tierCfg().checkInt(name(), "suddenPingChange", 45);
        boolean unrealisticLatency = ping <= plugin.tierCfg().checkInt(name(), "maxLegitPingForDelay", 170)
                && ctx.getCompensatedAgeMs() >= allowedRewindMs + plugin.tierCfg().checkLong(name(), "lowPingExtraAgeMs", 55L);
        boolean pingPattern = suddenPingShift || (unrealisticLatency && delayPattern);

        pushHistory(data.getBackTrackAStaleHistory(), stalePositionHit, plugin.tierCfg().checkInt(name(), "historySize", 8));
        pushHistory(data.getBackTrackATimingHistory(), delayPattern, plugin.tierCfg().checkInt(name(), "timingHistorySize", 8));
        pushHistory(data.getBackTrackAOrderHistory(), orderPattern, plugin.tierCfg().checkInt(name(), "orderHistorySize", 8));
        pushHistory(data.getBackTrackAPingHistory(), pingPattern, plugin.tierCfg().checkInt(name(), "pingHistorySize", 6));

        if (stalePositionHit) {
            Deque<Long> ages = data.getBackTrackAStaleAgeHistory();
            ages.addLast(Long.valueOf(ctx.getCompensatedAgeMs()));
            while (ages.size() > plugin.tierCfg().checkInt(name(), "ageHistorySize", 6)) {
                ages.removeFirst();
            }
        }

        double staleRatio = ratio(data.getBackTrackAStaleHistory());
        double timingRatio = ratio(data.getBackTrackATimingHistory());
        double orderRatio = ratio(data.getBackTrackAOrderHistory());
        double pingRatio = ratio(data.getBackTrackAPingHistory());
        double meanAge = average(data.getBackTrackAStaleAgeHistory());
        LagrangeUtil.CombatTeleportSummary teleport = LagrangeUtil.summarizeCombatTeleports(
                data, now, plugin.tierCfg().checkLong(name(), "teleportWindowMs", 3200L));
        boolean teleportPattern = teleport.getCount() >= plugin.tierCfg().checkInt(name(), "minTeleportEvents", 2)
                && teleport.getSameTargetCount() >= plugin.tierCfg().checkInt(name(), "minTeleportSameTargetEvents", 1)
                && teleport.getAverageCloseDelta() >= plugin.tierCfg().checkDouble(name(), "minTeleportAverageCloseDelta", 1.45D)
                && teleport.getAverageMoveH() >= plugin.tierCfg().checkDouble(name(), "minTeleportAverageMoveH", 1.35D)
                && data.getLagrangeTeleportScore() >= plugin.tierCfg().checkDouble(name(), "minTeleportScore", 2.2D);

        boolean packetEvidence = delayPattern
                || orderPattern
                || pingPattern
                || teleportPattern
                || timingRatio >= plugin.tierCfg().checkDouble(name(), "minTimingRatio", 0.50D)
                || orderRatio >= plugin.tierCfg().checkDouble(name(), "minOrderRatio", 0.50D)
                || pingRatio >= plugin.tierCfg().checkDouble(name(), "minPingAnomalyRatio", 0.45D);

        if (inCombo && !packetEvidence) {
            stalePositionHit = false;
        }

        int weight = 0;
        if (stalePositionHit) {
            weight = 1;
            if (delayPattern) weight++;
            if (orderPattern) weight++;
            if (pingPattern) weight++;
            if (teleportPattern) weight += plugin.tierCfg().checkInt(name(), "teleportWeightBonus", 2);
            if (staleRatio >= plugin.tierCfg().checkDouble(name(), "minStaleRatio", 0.55D)) weight++;
            if (timingRatio >= plugin.tierCfg().checkDouble(name(), "minTimingRatio", 0.50D)) weight++;
            if (orderRatio >= plugin.tierCfg().checkDouble(name(), "minOrderRatio", 0.50D)) weight++;
            if (pingRatio >= plugin.tierCfg().checkDouble(name(), "minPingAnomalyRatio", 0.45D)) weight++;
            if (meanAge >= allowedRewindMs + plugin.tierCfg().checkLong(name(), "meanAgeOverMs", 35L)) weight++;

            if (!packetEvidence && staleRatio < plugin.tierCfg().checkDouble(name(), "minStaleRatio", 0.55D)) {
                weight = 0;
            }
            if (inCombo && weight < plugin.tierCfg().checkInt(name(), "minComboWeight", 4)) {
                weight = 0;
            }
        }

        int b = data.getBackTrackABuffer();
        if (weight > 0) {
            int maxBuffer = plugin.tierCfg().checkInt(name(), "maxBuffer", 9);
            b = Math.min(maxBuffer, b + weight);
            data.setBackTrackABuffer(b);

            int signalCount = 0;
            if (delayPattern) signalCount++;
            if (orderPattern) signalCount++;
            if (pingPattern) signalCount++;
            if (timingRatio >= plugin.tierCfg().checkDouble(name(), "packetCancelMinTimingRatio", 0.65D)) signalCount++;
            if (orderRatio >= plugin.tierCfg().checkDouble(name(), "packetCancelMinOrderRatio", 0.65D)) signalCount++;

            boolean cancelPacket = stalePositionHit
                    && ping <= plugin.tierCfg().checkInt(name(), "packetCancelMaxPing", 165)
                    && b >= plugin.tierCfg().checkInt(name(), "packetCancelMinBuffer", 8)
                    && weight >= plugin.tierCfg().checkInt(name(), "packetCancelMinWeight", 6)
                    && staleExcessMs >= plugin.tierCfg().checkLong(name(), "packetCancelMinExcessRewindMs", 85L)
                    && currentOver >= plugin.tierCfg().checkDouble(name(), "packetCancelMinCurrentOver", 0.45D)
                    && shift >= plugin.tierCfg().checkDouble(name(), "packetCancelMinShift", 0.42D)
                    && ctx.getCompensatedDisplacement() >= plugin.tierCfg().checkDouble(name(), "packetCancelMinDisplacement", 0.50D) * kbScale
                    && signalCount >= plugin.tierCfg().checkInt(name(), "packetCancelMinSignals", 3)
                    && (orderPattern || orderRatio >= plugin.tierCfg().checkDouble(name(), "packetCancelMinOrderRatio", 0.65D))
                    && (delayPattern || timingRatio >= plugin.tierCfg().checkDouble(name(), "packetCancelMinTimingRatio", 0.65D) || teleportPattern)
                    && (!inCombo || weight >= plugin.tierCfg().checkInt(name(), "packetCancelMinComboWeight", 8));
            if (cancelPacket) {
                blockAttack(p, data,
                        "stale-hit age=" + ctx.getCompensatedAgeMs()
                                + "ms excess=" + staleExcessMs
                                + " shift=" + round3(shift)
                                + " cur=" + round3(ctx.getCurrentDistance())
                                + " hist=" + round3(ctx.getCompensatedDistance())
                                + " disp=" + round3(ctx.getCompensatedDisplacement())
                                + " buf=" + b
                                + " weight=" + weight
                                + " signals=" + signalCount
                                + " tp=" + teleport.getCount()
                                + "/" + teleport.getSameTargetCount()
                                + " tpClose=" + round3(teleport.getAverageCloseDelta())
                                + " combo=" + inCombo);
            }

            int signalFamilies = 0;
            if (stalePositionHit) signalFamilies++;
            if (delayPattern || timingRatio >= plugin.tierCfg().checkDouble(name(), "minTimingRatio", 0.42D)) signalFamilies++;
            if (orderPattern || orderRatio >= plugin.tierCfg().checkDouble(name(), "minOrderRatio", 0.42D)) signalFamilies++;
            if (pingPattern || pingRatio >= plugin.tierCfg().checkDouble(name(), "minPingAnomalyRatio", 0.38D)) signalFamilies++;
            if (teleportPattern) signalFamilies++;

            long blatantExcessMs = plugin.tierCfg().checkLong(name(), "blatantExcessRewindMs", 120L);
            boolean blatantStale = staleExcessMs >= blatantExcessMs;
            boolean enoughSignals = signalFamilies >= 2 || blatantStale;

            int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 6);
            if (b >= bufferToFlag && enoughSignals) {
                fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.2D),
                        "age=" + ctx.getCompensatedAgeMs()
                                + "ms allowed=" + allowedRewindMs
                                + " excess=" + staleExcessMs
                                + " shift=" + round3(shift)
                                + " cur=" + round3(ctx.getCurrentDistance())
                                + " hist=" + round3(ctx.getCompensatedDistance())
                                + " disp=" + round3(ctx.getCompensatedDisplacement())
                                + " stale=" + round3(staleRatio)
                                + " timing=" + round3(timingRatio)
                                + " order=" + round3(orderRatio)
                                + " pingRatio=" + round3(pingRatio)
                                + " tp=" + teleport.getCount()
                                + "/" + teleport.getSameTargetCount()
                                + " tpClose=" + round3(teleport.getAverageCloseDelta())
                                + " tpMove=" + round3(teleport.getAverageMoveH())
                                + " gapA=" + attackerGapMs
                                + " gapT=" + targetGapMs
                                + " ping=" + ping
                                + " combo=" + inCombo);
                data.setBackTrackABuffer(b / 2);
            }
        } else {
            if (b > 0) data.setBackTrackABuffer(b - 1);
            if (!stalePositionHit) {
                decay(p, 0.35D);
                Deque<Long> ages = data.getBackTrackAStaleAgeHistory();
                if (ages != null && !delayPattern && !orderPattern) {
                    ages.clear();
                }
            }
        }
    }

    private boolean shouldProcessAttack(Player p, PlayerData data) {
        if (!data.wasLastUseEntityAttack()) return false;

        long now = System.currentTimeMillis();
        if (now - data.getLastUseEntityTime() > plugin.tierCfg().checkLong(name(), "maxPacketAgeMs", 180L)) {
            return false;
        }

        long dedupeMs = plugin.tierCfg().checkLong(name(), "dedupeMs", 25L);
        long last = data.getBackTrackALastProcessedAttackMs();
        if (last > 0L && now - last < dedupeMs) return false;
        data.setBackTrackALastProcessedAttackMs(now);
        return true;
    }

    private static void pushHistory(Deque<Boolean> history, boolean value, int maxSize) {
        history.addLast(Boolean.valueOf(value));
        while (history.size() > maxSize) history.removeFirst();
    }

    private static double ratio(Deque<Boolean> values) {
        if (values == null || values.isEmpty()) return 0.0D;
        int count = 0;
        for (Boolean value : values) {
            if (Boolean.TRUE.equals(value)) count++;
        }
        return count / (double) values.size();
    }

    private static double average(Deque<Long> values) {
        if (values == null || values.isEmpty()) return 0.0D;
        double sum = 0.0D;
        int tracked = 0;
        for (Long value : values) {
            if (value == null) continue;
            sum += value.longValue();
            tracked++;
        }
        return tracked == 0 ? 0.0D : (sum / tracked);
    }
}
