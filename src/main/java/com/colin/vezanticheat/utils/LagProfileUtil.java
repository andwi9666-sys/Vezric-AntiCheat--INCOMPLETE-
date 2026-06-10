package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Deque;

public final class LagProfileUtil {
    private LagProfileUtil() {}

    public static void handleFlyingInterval(Plugin plugin, Player player, PlayerData data, long now, long interval) {
        if (plugin == null || player == null || data == null) return;
        recordPingSample(player, data);
        applyPassiveDecay(plugin, data, now);

        if (interval <= 0L) return;

        long suspiciousGapMs = plugin.getConfig().getLong("lag.profile.suspicious-gap-ms", 160L);
        long burstThresholdMs = plugin.getConfig().getLong("lag.profile.burst-threshold-ms", 18L);
        long burstWindowMs = plugin.getConfig().getLong("lag.profile.burst-window-ms", 700L);
        long velocityWindowMs = plugin.getConfig().getLong("lag.profile.velocity-window-ms", 650L);
        int minBurstPackets = plugin.getConfig().getInt("lag.profile.min-burst-packets", 4);
        int trustedPingMax = plugin.getConfig().getInt("lag.profile.trusted-ping-max", 190);
        double trustedJitterMax = plugin.getConfig().getDouble("lag.profile.trusted-jitter-max", 22.0D);

        double meanPing = meanPing(data);
        double jitter = pingJitter(data);
        long allowance = dynamicAllowance(plugin, meanPing, jitter);

        if (interval >= suspiciousGapMs && interval > allowance && meanPing <= trustedPingMax && jitter <= trustedJitterMax) {
            double severity = 1.0D + Math.min(1.4D, (interval - allowance) / 140.0D);
            if (now - data.getLastVelocityTime() <= velocityWindowMs) {
                severity += 0.55D;
            }

            data.setLagProfileScore(Math.min(8.0D, data.getLagProfileScore() + severity));
            data.setLastLagSpikeTime(now);
            data.setLastLagEvidenceTime(now);
            data.setLagBurstPackets(0);

            Deque<Long> gaps = data.getRecentLagGapIntervals();
            gaps.addLast(interval);
            while (gaps.size() > 10) gaps.removeFirst();
            return;
        }

        if (interval <= burstThresholdMs && now - data.getLastLagSpikeTime() <= burstWindowMs) {
            int burstPackets = data.getLagBurstPackets() + 1;
            data.setLagBurstPackets(burstPackets);
            if (burstPackets >= minBurstPackets) {
                data.setLastLagBurstTime(now);
                data.setLastLagEvidenceTime(now);
                data.setSuspiciousLagBursts(data.getSuspiciousLagBursts() + 1);
                data.setLagProfileScore(Math.min(8.0D, data.getLagProfileScore() + 0.22D));
            }
            return;
        }

        if (interval > burstThresholdMs && now - data.getLastLagSpikeTime() > burstWindowMs) {
            data.setLagBurstPackets(0);
        }
    }

    public static void handleAttack(Plugin plugin, Player player, PlayerData data, long now) {
        if (plugin == null || player == null || data == null) return;
        applyPassiveDecay(plugin, data, now);

        long attackWindowMs = plugin.getConfig().getLong("lag.profile.attack-window-ms", 900L);
        int trustedPingMax = plugin.getConfig().getInt("lag.profile.trusted-ping-max", 190);
        double trustedJitterMax = plugin.getConfig().getDouble("lag.profile.trusted-jitter-max", 22.0D);

        double meanPing = meanPing(data);
        double jitter = pingJitter(data);
        boolean gapActive = now - data.getLastLagSpikeTime() <= attackWindowMs;
        boolean burstActive = now - data.getLastLagBurstTime() <= attackWindowMs;
        if (!gapActive && !burstActive) return;
        if (meanPing > trustedPingMax || jitter > trustedJitterMax) return;

        double bonus = burstActive ? 0.90D : 0.45D;
        if (data.getLastUseEntityDistance() > 3.05D) {
            bonus += 0.20D;
        }

        data.setSuspiciousLagAttacks(data.getSuspiciousLagAttacks() + 1);
        data.setLastLagAttackTime(now);
        data.setLastLagEvidenceTime(now);
        data.setLagProfileScore(Math.min(8.0D, data.getLagProfileScore() + bonus));
    }

    public static void handleVelocity(Plugin plugin, Player player, PlayerData data, long now) {
        if (plugin == null || player == null || data == null) return;
        applyPassiveDecay(plugin, data, now);
        if (now - data.getLastLagSpikeTime() <= plugin.getConfig().getLong("lag.profile.velocity-window-ms", 650L)) {
            data.setLastLagEvidenceTime(now);
            data.setLagProfileScore(Math.min(8.0D, data.getLagProfileScore() + 0.25D));
        }
    }

    public static double activeScore(Plugin plugin, Player player, PlayerData data, long now) {
        if (plugin == null || player == null || data == null) return 0.0D;
        applyPassiveDecay(plugin, data, now);

        long activeWindowMs = plugin.getConfig().getLong("lag.profile.active-window-ms", 4500L);
        if (now - data.getLastLagEvidenceTime() > activeWindowMs) {
            return 0.0D;
        }

        double score = data.getLagProfileScore();
        if (data.getSuspiciousLagAttacks() >= 2 && now - data.getLastLagAttackTime() <= activeWindowMs) {
            score += 0.6D;
        }
        if (data.getSuspiciousLagBursts() >= 2 && now - data.getLastLagBurstTime() <= activeWindowMs) {
            score += 0.4D;
        }
        if (now - data.getLastLagrangeTeleportTime() <= activeWindowMs) {
            LagrangeUtil.CombatTeleportSummary teleport = LagrangeUtil.summarizeCombatTeleports(data, now, activeWindowMs);
            int minEvents = plugin.getConfig().getInt("lag.profile.combat-teleport.active-min-events", 2);
            if (teleport.getCount() >= minEvents) {
                double scoreFactor = plugin.getConfig().getDouble("lag.profile.combat-teleport.active-score-factor", 0.45D);
                double maxBonus = plugin.getConfig().getDouble("lag.profile.combat-teleport.active-max-bonus", 1.65D);
                score += Math.min(maxBonus, data.getLagrangeTeleportScore() * scoreFactor);
            }
        }
        return Math.max(0.0D, Math.min(8.0D, score));
    }

    public static boolean isSelectiveLagActive(Plugin plugin, Player player, PlayerData data, long now) {
        if (plugin == null || player == null || data == null) return false;
        double score = activeScore(plugin, player, data, now);
        if (score < plugin.getConfig().getDouble("lag.profile.selective-start-score", 1.35D)) {
            return false;
        }
        long activeWindow = plugin.getConfig().getLong("lag.profile.active-window-ms", 4500L);
        if (now - data.getLastLagrangeTeleportTime() <= activeWindow) {
            LagrangeUtil.CombatTeleportSummary teleport = LagrangeUtil.summarizeCombatTeleports(data, now, activeWindow);
            if (teleport.getCount() >= plugin.getConfig().getInt("lag.profile.combat-teleport.selective-min-events", 2)) {
                return true;
            }
        }
        return (now - data.getLastLagAttackTime() <= activeWindow && data.getSuspiciousLagAttacks() > 0)
                || (now - data.getLastLagBurstTime() <= activeWindow && data.getSuspiciousLagBursts() > 0);
    }

    public static boolean isCombatCoverActive(Plugin plugin, Player player, PlayerData data, long now) {
        if (!isSelectiveLagActive(plugin, player, data, now)) return false;
        long window = plugin.getConfig().getLong("lag.profile.combat-cover-window-ms", 1400L);
        return now - data.getLastLagAttackTime() <= window
                || now - data.getLastLagSpikeTime() <= window
                || now - data.getLastLagrangeTeleportTime() <= window;
    }

    public static boolean isVelocityCoverActive(Plugin plugin, Player player, PlayerData data, long now) {
        if (plugin == null || player == null || data == null) return false;
        long window = plugin.getConfig().getLong("lag.profile.velocity-cover-window-ms", 1200L);
        return activeScore(plugin, player, data, now) >= plugin.getConfig().getDouble("lag.profile.velocity-cover-score", 1.15D)
                && (now - data.getLastLagSpikeTime() <= window || now - data.getLastLagBurstTime() <= window);
    }

    public static int bufferBonus(Plugin plugin, Player player, PlayerData data, long now) {
        double score = activeScore(plugin, player, data, now);
        if (score >= plugin.getConfig().getDouble("lag.profile.buffer-bonus-high-score", 2.8D)) return 2;
        if (score >= plugin.getConfig().getDouble("lag.profile.buffer-bonus-low-score", 1.4D)) return 1;
        return 0;
    }

    public static boolean isTrustedConnection(Plugin plugin, PlayerData data) {
        if (plugin == null || data == null) return false;
        return meanPing(data) <= plugin.getConfig().getInt("lag.profile.trusted-ping-max", 190)
                && pingJitter(data) <= plugin.getConfig().getDouble("lag.profile.trusted-jitter-max", 22.0D);
    }

    public static long dynamicAllowance(Plugin plugin, PlayerData data) {
        if (plugin == null || data == null) return 0L;
        return dynamicAllowance(plugin, meanPing(data), pingJitter(data));
    }

    public static String appendDebug(String debug, Plugin plugin, Player player, PlayerData data, long now) {
        String lag = debugSummary(plugin, player, data, now);
        if (lag.isEmpty()) return debug;
        if (debug == null || debug.trim().isEmpty()) return lag;
        return debug + " " + lag;
    }

    public static String debugSummary(Plugin plugin, Player player, PlayerData data, long now) {
        double score = activeScore(plugin, player, data, now);
        if (score <= 0.0D) return "";
        long lastGap = 0L;
        Deque<Long> gaps = data.getRecentLagGapIntervals();
        if (!gaps.isEmpty()) {
            Long value = gaps.peekLast();
            lastGap = value == null ? 0L : value.longValue();
        }
        return "lag(score=" + round(score)
                + ",gap=" + lastGap
                + ",burst=" + data.getLagBurstPackets()
                + ",atk=" + data.getSuspiciousLagAttacks()
                + ",tp=" + combatTeleportCount(data, now,
                plugin.getConfig().getLong("lag.profile.active-window-ms", 4500L))
                + ",tpScore=" + round(data.getLagrangeTeleportScore())
                + ",ping=" + round(meanPing(data))
                + ",jitter=" + round(pingJitter(data))
                + ")";
    }

    private static void recordPingSample(Player player, PlayerData data) {
        int ping = Math.max(0, PingUtil.getPing(player));
        Deque<Integer> samples = data.getRecentPingSamples();
        samples.addLast(ping);
        while (samples.size() > 24) samples.removeFirst();
    }

    private static void applyPassiveDecay(Plugin plugin, PlayerData data, long now) {
        long lastEvidence = data.getLastLagEvidenceTime();
        if (lastEvidence <= 0L) return;

        double decayPerSecond = plugin.getConfig().getDouble("lag.profile.decay-per-second", 0.28D);
        long dt = Math.max(0L, now - lastEvidence);
        if (dt < 1000L) return;

        double decay = (dt / 1000.0D) * decayPerSecond;
        if (decay <= 0.0D) return;

        data.setLagProfileScore(Math.max(0.0D, data.getLagProfileScore() - decay));
        data.setLastLagEvidenceTime(now);

        long staleWindowMs = plugin.getConfig().getLong("lag.profile.counter-reset-ms", 12000L);
        if (now - data.getLastLagAttackTime() > staleWindowMs) {
            data.setSuspiciousLagAttacks(0);
        }
        if (now - data.getLastLagBurstTime() > staleWindowMs) {
            data.setSuspiciousLagBursts(0);
            data.setLagBurstPackets(0);
        }
        if (now - data.getLastLagrangeTeleportTime() > staleWindowMs) {
            data.setLagrangeTeleportScore(0.0D);
        }
    }

    private static int combatTeleportCount(PlayerData data, long now, long windowMs) {
        return LagrangeUtil.summarizeCombatTeleports(data, now, windowMs).getCount();
    }

    private static long dynamicAllowance(Plugin plugin, double meanPing, double jitter) {
        long base = plugin.getConfig().getLong("lag.profile.base-gap-allowance-ms", 115L);
        double pingFactor = plugin.getConfig().getDouble("lag.profile.ping-factor", 0.55D);
        double jitterFactor = plugin.getConfig().getDouble("lag.profile.jitter-factor", 1.8D);
        return Math.round(base + (meanPing * pingFactor) + (jitter * jitterFactor));
    }

    public static double meanPing(PlayerData data) {
        Deque<Integer> samples = data.getRecentPingSamples();
        if (samples.isEmpty()) return 0.0D;
        double sum = 0.0D;
        for (Integer sample : samples) {
            if (sample != null) sum += sample.intValue();
        }
        return sum / samples.size();
    }

    public static double pingJitter(PlayerData data) {
        Deque<Integer> samples = data.getRecentPingSamples();
        if (samples.size() < 2) return 0.0D;
        Integer previous = null;
        double totalDelta = 0.0D;
        int deltas = 0;
        for (Integer sample : samples) {
            if (sample == null) continue;
            if (previous != null) {
                totalDelta += Math.abs(sample.intValue() - previous.intValue());
                deltas++;
            }
            previous = sample;
        }
        return deltas == 0 ? 0.0D : (totalDelta / deltas);
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
