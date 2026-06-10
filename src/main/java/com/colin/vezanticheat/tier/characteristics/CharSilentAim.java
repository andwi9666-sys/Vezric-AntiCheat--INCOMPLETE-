package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.AimAssistUtil;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * CharSilentAim -- Polar Characteristics tier 8-signal silent aim aggregator
 *
 * <p><b>What it detects:</b> Silent KillAura that manipulates rotation packets to attack
 * targets without visually turning toward them. By combining 6 independent detection
 * signals, this check identifies aura regardless of the specific rotation bypass used
 * (server-side rotation, silent aim, snap-back, etc.).</p>
 *
 * <p><b>Algorithm (6 signals combined):</b></p>
 * <ol>
 *   <li><b>Signal 1 - Angular Error:</b> How far off the player's look direction is from
 *       the target's hitbox at attack time. High angle + low dot = not facing target.</li>
 *   <li><b>Signal 2 - Pre-Attack Snap:</b> Concentrated rotation work in the last packet
 *       before attack (ratio of last-packet improvement to total improvement). High ratio
 *       = all aiming done in one snap packet.</li>
 *   <li><b>Signal 3 - Post-Reset:</b> After a suspicious attack, the player rotates BACK
 *       toward their original look direction (confirming snap-back pattern). Tracked via
 *       onRotation() callback.</li>
 *   <li><b>Signal 4 - Movement Mismatch:</b> Player's movement direction vs. direction to
 *       target. Moving away from the target while attacking suggests automated aim.</li>
 *   <li><b>Signal 5 - Center Bias:</b> Unnaturally tight clustering of hit positions near
 *       the exact center of the target's hitbox over multiple attacks.</li>
 *   <li><b>Signal 6 - Attack-Tick Correlation:</b> Rotation changes occur ONLY on attack
 *       ticks (not between attacks). Tracked via onFlyingPacket() over a 5-second window.</li>
 * </ol>
 *
 * <p>Signals are weighted (1.5/2.0/1.5/1.0/0.8/1.5 = 8.3 total) and combined into a
 * composite score. Multiplied by 1.3-1.5x when 3-4+ signals are active simultaneously.</p>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code killAuraASwitchBuffer} -- main buffer counter.</li>
 *   <li>{@code killAuraACleanStreak} -- consecutive clean attacks (for decay).</li>
 *   <li>{@code killAuraALastHitMs} -- timestamp for decay timing.</li>
 *   <li>{@code killAuraAPostReset*} -- signal 3 tracking (active, start, base/attack yaw/pitch).</li>
 *   <li>{@code killAuraARotOnAttackTicks / rotOnNonAttackTicks / noRotOnNonAttackTicks} --
 *       signal 6 correlation counters.</li>
 *   <li>{@code killAuraACorrelationWindowStart} -- signal 6 window start.</li>
 *   <li>{@code killAuraAMismatchAngles} -- signal 4 history deque.</li>
 *   <li>{@code killAuraACenterErrors} -- signal 5 history deque.</li>
 *   <li>{@code positionHistory} -- position samples for signals 2, 4, 5.</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> Score-based tiered gain: blatant (&gt;= 0.70) = +3,
 * suspicious (&gt;= 0.45) = +2, mild (&gt;= 0.25) = +1, plus +1 bonus when 3+ signals
 * active. Flags at bufferToFlag (default 4). Clean streak of 3+ with 1.5s gap decays
 * buffer by 1. Resets to 0 on flag.</p>
 *
 * <p><b>Exemptions:</b></p>
 * <ul>
 *   <li>Velocity/teleport exempt.</li>
 *   <li>Close range (&lt; 1.5 blocks) -- angular error unreliable at point-blank.</li>
 *   <li>Low sample weight (&lt; 0.20) -- insufficient combat context reliability.</li>
 *   <li>Signal 2 skipped for high ping (&gt; 150ms).</li>
 *   <li>Signal 4 skipped at close range or low movement speed.</li>
 *   <li>Signal 5 only evaluated at 1-3 block range.</li>
 *   <li>Signal 6 skipped with &lt; 40 total ticks or &lt; 2 seconds of data.</li>
 *   <li>Vanilla expansion margin hit -- legit edge hit in the 0.1 block shell.</li>
 *   <li>High-CPS PvP spam, jump crit chains, and forward rush via shouldExemptAimHeuristics.</li>
 * </ul>
 *
 * <p><b>False positive protections:</b> Performance short-circuit skips expensive
 * signals if S1 is 0 (player IS facing target); combo/trade halves S1; recent jump
 * halves S2; signal 6 excludes naturally stationary players; composite score scaled
 * by combat sample weight; clean streak decay; close-range bypass for all geometric
 * signals; minimum active signal requirement for multiplier.</p>
 *
 * <p><b>Connections:</b> Feeds "AIM" signals to KillAuraAggregateUtil (KillAuraH).
 * Uses onRotation() for signal 3 (snap-back tracking) and onFlyingPacket() for
 * signal 6 (attack-tick correlation). Packet cancellation at cancelThreshold (0.85).</p>
 */
public final class CharSilentAim extends TierCheck {

    private static final double TOTAL_WEIGHT = 1.5 + 2.0 + 1.5 + 1.0 + 0.8 + 1.5; // 8.3

    public CharSilentAim(VezAntiCheat plugin) {
        super(plugin, "CharSilentAim", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null) return;

        long now = System.currentTimeMillis();
        if (!data.wasLastUseEntityAttack()) return;
        if (now - data.getLastUseEntityTime() > plugin.tierCfg().checkLong(name(), "attackFreshnessMs", 150L)) return;
        if (data.isVelocityExempt()) return;
        if (data.isTeleportExempt()) return;

        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (target == null) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null) return;

        if (combat.getSampleWeight() < plugin.tierCfg().checkDouble(name(), "minSampleWeight", 0.20)) {
            decay(p, 0.25);
            return;
        }

        if (CombatContextAnalyzer.shouldExemptAimHeuristics(plugin, combat, data, p, now)) {
            decay(p, 0.35);
            return;
        }

        int ping = Math.max(0, PingUtil.getPing(p));
        long rewindMs = CombatUtil.compensationWindowMs(
                ping,
                plugin.tierCfg().checkLong(name(), "rewindBaseMs", 80L),
                plugin.tierCfg().checkDouble(name(), "rewindPingFactor", 0.30),
                plugin.tierCfg().checkLong(name(), "maxRewindMs", 200L)
        );

        Location eye = data.getLastAttackEyeLocation();
        if (eye == null || eye.getWorld() == null) eye = p.getEyeLocation();

        PlayerData targetData = target instanceof Player ? plugin.data().get((Player) target) : null;
        CombatUtil.ReachContext ctx = null;
        if (plugin.getConfig().getBoolean("combat-engine.enabled", true)) {
            com.colin.vezanticheat.engine.CombatResult engine = data.getLastCombatResult();
            if (engine != null && engine.tracked) {
                ctx = com.colin.vezanticheat.engine.CombatRewind.toReachContext(engine);
            }
        }
        if (ctx == null) {
            ctx = CombatUtil.analyzeReach(eye, target, targetData, data.getLastUseEntityTime(), rewindMs);
        }
        if (ctx == null) return;

        if (ctx.isLegitExpansionMarginHit(eye)) {
            decay(p, 0.30);
            return;
        }

        double dist = ctx.getCompensatedDistance();
        if (dist < plugin.tierCfg().checkDouble(name(), "closeRangeBypass", 1.5)) {
            decay(p, 0.30);
            return;
        }

        Location compensated = ctx.getCompensatedLocation();
        double width = ctx.getWidth();
        double height = ctx.getHeight();

        // --- Signal 1: Angular error ---
        double s1 = computeAngularErrorSignal(eye, compensated, width, height, combat);

        // --- Performance short-circuit: if player IS facing target, skip expensive signals ---
        double s2 = 0.0, s4 = 0.0, s6 = 0.0;
        if (s1 > 0.0) {
            s2 = computePreAttackSnapSignal(p, data, eye, target, compensated, width, height, ping, combat);
            s4 = computeMovementMismatchSignal(p, data, target, dist, now);
            s6 = computeAttackTickCorrelationSignal(data, now);
        }

        // --- Signal 3: Post-reset bonus (always compute, accumulates over time) ---
        double s3 = getPostResetBonus(data, now);

        // --- Signal 5: Center bias (always compute, statistical) ---
        double s5 = computeCenterBiasSignal(data, eye, compensated, width, height, dist);

        // --- Combine signals ---
        double weightedSum = s1 * 1.5 + s2 * 2.0 + s3 * 1.5 + s4 * 1.0 + s5 * 0.8 + s6 * 1.5;
        double combinedScore = weightedSum / TOTAL_WEIGHT;

        int activeSignals = countAbove(0.3, s1, s2, s3, s4, s5, s6);
        if (activeSignals >= 4) combinedScore *= 1.5;
        else if (activeSignals >= 3) combinedScore *= 1.3;

        combinedScore *= combat.getSampleWeight();

        // --- Buffer logic ---
        int buf = data.getKillAuraASwitchBuffer();
        int gain = 0;

        if (combinedScore >= plugin.tierCfg().checkDouble(name(), "blatantThreshold", 0.70)) gain = 3;
        else if (combinedScore >= plugin.tierCfg().checkDouble(name(), "suspiciousThreshold", 0.45)) gain = 2;
        else if (combinedScore >= plugin.tierCfg().checkDouble(name(), "mildThreshold", 0.25)) gain = 1;

        if (activeSignals >= 3 && gain > 0) gain += 1;

        if (gain == 0) {
            int streak = data.getKillAuraACleanStreak() + 1;
            data.setKillAuraACleanStreak(streak);
            long lastEval = data.getKillAuraALastHitMs();
            if (streak >= 3 && lastEval > 0 && (now - lastEval) > 1500L) {
                buf = Math.max(0, buf - 1);
            }
            decay(p, 0.35);
        } else {
            data.setKillAuraACleanStreak(0);
            buf += gain;

            }

        data.setKillAuraASwitchBuffer(buf);
        data.setKillAuraALastHitMs(now);

        // --- Packet cancel ---
        double cancelThreshold = plugin.tierCfg().checkDouble(name(), "cancelThreshold", 0.85);
        if (combinedScore >= cancelThreshold && buf >= 3 && combat.getSampleWeight() >= 0.40) {
            blockAttack(p, data,
                    "silent-aura score=" + r(combinedScore) + " active=" + activeSignals + " buf=" + buf);
        }

        // --- Flag ---
        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 4);
        if (gain > 0 && buf < bufferToFlag) {
            verbose(p, "buf=" + buf + "/" + bufferToFlag + " score=" + r(combinedScore) + " active=" + activeSignals);
        }
        if (buf >= bufferToFlag) {
            blockAttack(p, data,
                    "silent-aura score=" + r(combinedScore) + " active=" + activeSignals + " buf=" + buf);
            fail(p, data, plugin.tierCfg().checkDouble(name(), "vl", 1.5),
                    "score=" + r(combinedScore) + " s1=" + r(s1) + " s2=" + r(s2)
                            + " s3=" + r(s3) + " s4=" + r(s4) + " s5=" + r(s5) + " s6=" + r(s6)
                            + " active=" + activeSignals + " buf=" + buf
                            + " dist=" + r(dist) + " " + combat.debugSummary());
            data.setKillAuraASwitchBuffer(0);
        }

        // --- Activate post-reset tracking (signal 3) if suspicious ---
        if (!CombatContextAnalyzer.isLikelySpacingMovement(plugin, data, p, now)
                && !CombatContextAnalyzer.isLikelyCounterstrafeSpacing(plugin, data, now)
                && (s1 > 0.3 || s2 > 0.4) && !data.isKillAuraAPostResetActive()) {
            activatePostResetTracking(data, eye, now);
        }
    }

    @Override
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {
        if (p == null || data == null) return;
        if (!data.isKillAuraAPostResetActive()) return;

        long now = System.currentTimeMillis();
        long elapsed = now - data.getKillAuraAPostResetStartMs();
        long windowMs = plugin.tierCfg().checkLong(name(), "postResetWindowMs", 150L);

        if (elapsed > windowMs || elapsed < 0) {
            data.setKillAuraAPostResetActive(false);
            return;
        }

        float baseYaw = data.getKillAuraAPostResetBaseYaw();
        float basePitch = data.getKillAuraAPostResetBasePitch();
        float attackYaw = data.getKillAuraAPostResetAttackYaw();
        float attackPitch = data.getKillAuraAPostResetAttackPitch();

        float distToBase = CombatUtil.angleDiff(yaw, baseYaw);
        float distToAttack = CombatUtil.angleDiff(yaw, attackYaw);

        double baseThreshold = plugin.tierCfg().checkDouble(name(), "postResetBaseThreshold", 8.0);
        double attackThreshold = plugin.tierCfg().checkDouble(name(), "postResetAttackThreshold", 15.0);

        if (distToBase < baseThreshold && distToAttack > attackThreshold) {
            data.setKillAuraAPostResetConfirmed(data.getKillAuraAPostResetConfirmed() + 1);
            data.setKillAuraAPostResetActive(false);
        }
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (CombatContextAnalyzer.shouldExemptAimHeuristics(plugin, null, data, p, nowMs)) return;

        // Signal 6: track rotation correlation with attack ticks
        long windowStart = data.getKillAuraACorrelationWindowStart();
        long windowMs = plugin.tierCfg().checkLong(name(), "correlationWindowMs", 5000L);
        int maxTicks = plugin.tierCfg().checkInt(name(), "correlationMaxTicks", 100);

        int totalTicks = data.getKillAuraARotOnAttackTicks()
                + data.getKillAuraARotOnNonAttackTicks()
                + data.getKillAuraANoRotOnNonAttackTicks();

        if (windowStart <= 0 || (nowMs - windowStart) > windowMs || totalTicks >= maxTicks) {
            data.setKillAuraARotOnAttackTicks(0);
            data.setKillAuraARotOnNonAttackTicks(0);
            data.setKillAuraANoRotOnNonAttackTicks(0);
            data.setKillAuraACorrelationWindowStart(nowMs);
            return;
        }

        boolean hasRotation = Math.abs(data.getLastRotationYawDelta()) > 0.1F
                || Math.abs(data.getLastRotationPitchDelta()) > 0.1F;
        boolean hasAttack = (nowMs - data.getLastUseEntityTime()) < 55L;

        if (hasRotation && hasAttack) {
            data.setKillAuraARotOnAttackTicks(data.getKillAuraARotOnAttackTicks() + 1);
        } else if (hasRotation) {
            data.setKillAuraARotOnNonAttackTicks(data.getKillAuraARotOnNonAttackTicks() + 1);
        } else if (!hasAttack) {
            data.setKillAuraANoRotOnNonAttackTicks(data.getKillAuraANoRotOnNonAttackTicks() + 1);
        }
    }

    // ========== Signal computation methods ==========

    private double computeAngularErrorSignal(Location eye, Location compensated, double width, double height,
                                             CombatContextAnalyzer.CombatContext combat) {
        double angle = CombatUtil.angularError(eye, compensated, width, height);
        double dot = CombatUtil.lookDotToHitbox(eye, compensated, width, height);

        double signal = clamp((angle - 8.0) / 55.0, 0.0, 1.0);
        if (dot < 0.55 && angle > 18.0) signal = Math.min(1.0, signal + 0.15);
        if (combat.isCombo() || combat.isTrade()) signal *= 0.5;

        return signal;
    }

    private double computePreAttackSnapSignal(Player p, PlayerData data, Location eye, Entity target,
                                              Location compensated, double width, double height,
                                              int ping, CombatContextAnalyzer.CombatContext combat) {
        if (ping > 150) return 0.0;
        long now = System.currentTimeMillis();
        if (CombatContextAnalyzer.isLikelySpacingMovement(plugin, data, p, now)
                || CombatContextAnalyzer.isLikelyCounterstrafeSpacing(plugin, data, now)) {
            return 0.0;
        }

        long attackTime = data.getLastUseEntityTime();
        long windowMs = plugin.tierCfg().checkLong(name(), "preSnapWindowMs", 250L);
        int minSamples = plugin.tierCfg().checkInt(name(), "preSnapMinSamples", 3);

        List<PlayerData.PositionSample> samples = new ArrayList<>();
        for (PlayerData.PositionSample s : data.getPositionHistory()) {
            if (s == null) continue;
            long age = attackTime - s.getTime();
            if (age >= 0 && age <= windowMs) samples.add(s);
        }

        if (samples.size() < minSamples) return 0.0;

        // Compute angular error at each sample toward compensated target
        double[] errors = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            PlayerData.PositionSample s = samples.get(i);
            Location sampleEye = new Location(eye.getWorld(), s.getX(), s.getY() + 1.62, s.getZ(), s.getYaw(), s.getPitch());
            errors[i] = CombatUtil.angularError(sampleEye, compensated, width, height);
        }

        // If first sample already well-aimed (< 10deg), player was tracking normally
        if (errors[0] < 10.0) return 0.0;

        // Compute alignment work per sample (reduction in error)
        double totalWork = 0.0;
        double lastPacketWork = 0.0;
        for (int i = 1; i < errors.length; i++) {
            double improvement = errors[i - 1] - errors[i];
            if (improvement > 0) {
                totalWork += improvement;
                if (i == errors.length - 1) lastPacketWork = improvement;
            }
        }

        if (totalWork < 3.0) return 0.0;

        double ratio = lastPacketWork / totalWork;
        double signal = clamp((ratio - 0.5) / 0.3, 0.0, 1.0);

        if (combat.isRecentJump()) signal *= 0.5;

        return signal;
    }

    private double getPostResetBonus(PlayerData data, long now) {
        int confirmed = data.getKillAuraAPostResetConfirmed();
        if (confirmed <= 0) return 0.0;

        // Decay confirmations over time
        long lastHit = data.getKillAuraALastHitMs();
        if (lastHit > 0 && (now - lastHit) > 3000L && confirmed > 0) {
            data.setKillAuraAPostResetConfirmed(confirmed - 1);
            confirmed--;
        }

        return Math.min(1.0, confirmed * 0.3);
    }

    private double computeMovementMismatchSignal(Player p, PlayerData data, Entity target, double dist, long nowMs) {
        if (dist < 1.5) return 0.0;
        if (CombatContextAnalyzer.isLikelySpacingMovement(plugin, data, p, nowMs)) return 0.0;
        if (CombatContextAnalyzer.isLikelyCounterstrafeSpacing(plugin, data, nowMs)) return 0.0;

        Deque<PlayerData.PositionSample> history = data.getPositionHistory();
        if (history.size() < 3) return 0.0;

        // Get last 2 samples for movement direction
        PlayerData.PositionSample[] recent = new PlayerData.PositionSample[2];
        int idx = 0;
        for (PlayerData.PositionSample s : history) {
            if (s == null) continue;
            recent[idx] = s;
            idx++;
            if (idx >= 2) break;
        }
        if (recent[0] == null || recent[1] == null) return 0.0;

        double dx = recent[0].getX() - recent[1].getX();
        double dz = recent[0].getZ() - recent[1].getZ();
        double speed = Math.sqrt(dx * dx + dz * dz);

        if (speed < 0.08) return 0.0;

        float moveDirection = (float) Math.toDegrees(Math.atan2(-dx, dz));

        Location playerLoc = p.getLocation();
        Location targetLoc = target.getLocation();
        double tdx = targetLoc.getX() - playerLoc.getX();
        double tdz = targetLoc.getZ() - playerLoc.getZ();
        float attackDirection = (float) Math.toDegrees(Math.atan2(-tdx, tdz));

        float mismatch = CombatUtil.angleDiff(moveDirection, attackDirection);

        // Store for averaging
        Deque<Double> mismatchHistory = data.getKillAuraAMismatchAngles();
        mismatchHistory.addLast((double) mismatch);
        while (mismatchHistory.size() > 10) mismatchHistory.removeFirst();

        // Require 5+ samples with high average
        if (mismatchHistory.size() < 5) return 0.0;
        double avg = 0.0;
        for (double m : mismatchHistory) avg += m;
        avg /= mismatchHistory.size();

        if (avg < 60.0) return 0.0;

        return clamp((mismatch - 45.0) / 90.0, 0.0, 1.0);
    }

    private double computeCenterBiasSignal(PlayerData data, Location eye, Location compensated,
                                           double width, double height, double dist) {
        if (dist < 1.0 || dist > 3.0) return 0.0;

        // Center angle: angle to exact center of hitbox
        Location center = compensated.clone().add(0, height / 2.0, 0);
        double centerAngle = CombatUtil.angularError(eye, center, 0.001, 0.001);
        double closestAngle = CombatUtil.angularError(eye, compensated, width, height);
        double centerMargin = Math.max(0.0, centerAngle - closestAngle);

        Deque<Double> errors = data.getKillAuraACenterErrors();
        errors.addLast(centerMargin);
        while (errors.size() > 10) errors.removeFirst();

        if (errors.size() < 5) return 0.0;

        double avg = 0.0;
        for (double e : errors) avg += e;
        avg /= errors.size();

        if (avg > 1.5) return 0.0;
        return clamp((1.5 - avg) / 1.0, 0.0, 1.0);
    }

    private double computeAttackTickCorrelationSignal(PlayerData data, long now) {
        int rotOnAttack = data.getKillAuraARotOnAttackTicks();
        int rotOnNonAttack = data.getKillAuraARotOnNonAttackTicks();
        int noRotNonAttack = data.getKillAuraANoRotOnNonAttackTicks();
        int totalTicks = rotOnAttack + rotOnNonAttack + noRotNonAttack;
        long windowStart = data.getKillAuraACorrelationWindowStart();

        if (totalTicks < 40 || rotOnAttack < 3) return 0.0;
        if (windowStart > 0 && (now - windowStart) < 2000L) return 0.0;

        // If player is naturally stationary (rarely rotates outside attacks), don't signal
        int totalNonAttack = rotOnNonAttack + noRotNonAttack;
        if (totalNonAttack > 0 && (double) noRotNonAttack / totalNonAttack > 0.70) return 0.0;

        int totalWithRotation = rotOnAttack + rotOnNonAttack;
        if (totalWithRotation == 0) return 0.0;

        double exclusivity = (double) rotOnAttack / totalWithRotation;
        if (exclusivity < 0.70) return 0.0;

        return clamp((exclusivity - 0.70) / 0.20, 0.0, 1.0);
    }

    private void activatePostResetTracking(PlayerData data, Location eye, long now) {
        // Find the baseline rotation (2 samples before current)
        Deque<PlayerData.PositionSample> history = data.getPositionHistory();
        PlayerData.PositionSample baseline = null;
        int count = 0;
        for (PlayerData.PositionSample s : history) {
            count++;
            if (count >= 3) { baseline = s; break; }
        }

        if (baseline == null) return;

        data.setKillAuraAPostResetActive(true);
        data.setKillAuraAPostResetStartMs(now);
        data.setKillAuraAPostResetBaseYaw(baseline.getYaw());
        data.setKillAuraAPostResetBasePitch(baseline.getPitch());
        data.setKillAuraAPostResetAttackYaw(eye.getYaw());
        data.setKillAuraAPostResetAttackPitch(eye.getPitch());
    }

    // ========== Utility ==========

    private int countAbove(double threshold, double... values) {
        int count = 0;
        for (double v : values) if (v > threshold) count++;
        return count;
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
