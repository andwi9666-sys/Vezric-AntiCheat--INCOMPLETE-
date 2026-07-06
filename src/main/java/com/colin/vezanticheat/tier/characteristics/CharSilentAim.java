package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.combat.math.RequiredRotationUtil;
import com.colin.vezanticheat.utils.AimSensitivityProcessor;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.GcdLatticeAnalysis;
import com.colin.vezanticheat.utils.PingUtil;
import com.colin.vezanticheat.utils.SilentAimAnalyzer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Deque;

/**
 * CharSilentAim -- Polar Characteristics tier silent-aim FUSION CATCH-ALL.
 *
 * <p><b>What it detects:</b> Silent aim / server-side rotation. The client renders the player
 * looking wherever the user truly aims, but the cheat OVERRIDES the outgoing rotation in
 * flying/look packets so that ON THE SERVER the yaw/pitch points exactly at the target during
 * the attack tick, then restores the user's real view on a later packet. The server therefore
 * APPEARS to see perfect aim AT the attack tick.</p>
 *
 * <p><b>Why attack-time angular error fails:</b> at the attack tick the server-side angular error
 * to the target is ~0 (perfect aim), so "angular error at attack time" can NEVER catch genuine
 * silent aim while it DOES false-flag legit flicks. This rewrite does NOT depend on attack-time
 * angular error being large. Instead it reconstructs the per-tick rotation TRAJECTORY through the
 * attack moment via {@link SilentAimAnalyzer} and looks for the injected-rotation FINGERPRINT.</p>
 *
 * <p><b>Signals fused ({@link CharSilentAimSignals#fuse}):</b></p>
 * <ol>
 *   <li><b>snapRestore</b> (w 2.0) -- {@code SilentAimAnalyzer} snap-TO-target-and-restore: yaw/pitch
 *       jump TO a value aiming at the hitbox (error collapses near-0 from a high prior error) and then
 *       jump BACK toward the pre-snap heading on a later packet. The RETURN is the tell.</li>
 *   <li><b>hitboxVel</b> (w 1.8) -- impossible angular velocity onto the hitbox (incl. 180/large snaps
 *       terminating exactly on a valid target), beyond {@link RequiredRotationUtil#snapAngularVelocityThreshold(int)}.</li>
 *   <li><b>centerLock</b> (w 1.0) -- aim repeatedly lands dead-center with near-zero sub-degree jitter
 *       (humans always jitter), averaged over a window.</li>
 *   <li><b>desync</b> (w 1.0) -- horizontal movement/strafe implies a heading disagreeing with the
 *       server yaw at attack time (existing movement-mismatch, now de-gated).</li>
 *   <li><b>lattice</b> (w 1.0) -- GCD/sensitivity lattice break (residue/conformity) plus non-vanilla
 *       recovered sensitivity ({@link AimSensitivityProcessor}).</li>
 *   <li><b>exclusivity</b> (w 1.2) -- meaningful rotation happens ONLY on/just-before attack ticks
 *       (existing onFlyingPacket correlation counters).</li>
 *   <li><b>linearRamp</b> (w 0.8) -- unnaturally linear / constant-angular-velocity ramps toward the
 *       target ({@link GcdLatticeAnalysis#distributedSnapRatio(double[])}).</li>
 * </ol>
 *
 * <p><b>Multi-signal requirement (FPs near zero):</b> buffer only gains when there are &gt;=2 STRONG
 * transient signals (snapRestore/hitboxVel via {@link CharSilentAimSignals#strongSignalCount}) OR a
 * SUSTAINED snap-restore pattern (window has &gt;= snapRestoreSustainMin samples averaging &gt;=
 * snapRestoreSustainAvg). A single ambiguous hit never flags.</p>
 *
 * <p><b>False-positive protections (all preserved):</b> Bedrock/Geyser aim-exempt (via TierCheck.fail
 * -&gt; ClientCompatUtil.isAimExempt for aim characteristic checks), velocity/teleport exempt, tps/ping
 * lag gating (fail()/lagGated), combat-context exemptions (spacing, counterstrafe, kb-flick, combat-spam,
 * recent-jump via {@link CombatContextAnalyzer#shouldExemptAimHeuristics}), minSampleWeight bail +
 * sample-weight multiplier, {@code isLegitExpansionMarginHit} grace on the 0.1 shell edge, close-range
 * taper ({@link CharSilentAimSignals#closeRangeSignalScale}) instead of hard zeroing, clean-streak decay
 * + buffer reset on flag, conservative cancel guard (buf&gt;=3 AND sampleWeight&gt;=0.40).</p>
 *
 * <p><b>Hooks preserved for dependent checks:</b> onRotation still feeds
 * {@link GcdLatticeAnalysis#pushAngularSample} / peak angular velocity (other checks read
 * angularVelocitySamples) and the post-reset confirm logic (CharAimReset depends on it); onFlyingPacket
 * still maintains the attack-tick correlation counters (CharAimCorrelation depends on them).</p>
 */
public final class CharSilentAim extends TierCheck {

    /** Hard cap on the sustained snap-restore window, enforced HERE by the caller (no setter pushes). */
    private static final int SNAP_RESTORE_CAP = 12;
    /** Hard cap on the center-lock jitter window, enforced HERE by the caller (no setter pushes). */
    private static final int CENTER_LOCK_CAP = 16;

    public CharSilentAim(VezAntiCheat plugin) {
        super(plugin, "CharSilentAim", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null) return;

        long now = System.currentTimeMillis();
        // Reset per-attack transient outputs other checks may read (snapScore, peak velocity).
        data.setPeakAngularVelocityDegPerSec(0.0D);
        data.setKillAuraASnapRatio(0.0D);
        data.setSilentLastSnapRestoreScore(0.0D);
        data.setSilentLastHitboxSnapVelDegPerSec(0.0D);

        // --- Fresh-attack gate ---
        if (!data.wasLastUseEntityAttack()) return;
        if (now - data.getLastUseEntityTime() > plugin.tierCfg().checkLong(name(), "attackFreshnessMs", 150L)) return;
        if (data.isVelocityExempt()) return;
        if (data.isTeleportExempt()) return;

        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (target == null) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null) return;

        double minSampleWeight = plugin.tierCfg().checkDouble(name(), "minSampleWeight", 0.20);
        if (combat.getSampleWeight() < minSampleWeight) {
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

        // --- Resolve the SAME lag-comp hitbox the reach engine used ---
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

        // Legit 0.1-block shell edge hit -> grace.
        if (ctx.isLegitExpansionMarginHit(eye)) {
            decay(p, 0.30);
            return;
        }

        double dist = ctx.getCompensatedDistance();
        double closeBypass = plugin.tierCfg().checkDouble(name(), "closeRangeBypass", 1.5);
        double closeTaper = plugin.tierCfg().checkDouble(name(), "closeRangeTaper", 1.2);
        double closeBoost = plugin.tierCfg().checkDouble(name(), "closeRangeSignalBoost", 1.35);
        boolean closeRange = dist < closeBypass;
        // Taper (not hard-zero) the corroborating signals at point-blank where geometry is noisy.
        double closeScale = CharSilentAimSignals.closeRangeSignalScale(dist, closeTaper, closeBoost);

        Location compensated = ctx.getCompensatedLocation();
        double width = ctx.getWidth();
        double height = ctx.getHeight();

        // ===================== INJECTED-ROTATION FINGERPRINT =====================
        long windowMs = plugin.tierCfg().checkLong(name(), "analyzerWindowMs", 120L);
        SilentAimAnalyzer.Result analysis = SilentAimAnalyzer.analyze(
                data.getRotationRingBuffer(), data.getPositionHistory(),
                eye, compensated, width, height,
                data.getLastUseEntityTime(), ping, windowMs);

        // (1) snap-to-target-and-restore: store + push to the sustained window (cap enforced HERE).
        double snapRestore = CharSilentAimSignals.snapRestoreScore(
                SilentAimAnalyzer.snapRestoreScore(analysis));
        data.setSilentLastSnapRestoreScore(snapRestore);
        Deque<Double> snapWindow = data.getSilentSnapRestoreScores();
        snapWindow.addLast(snapRestore);
        while (snapWindow.size() > SNAP_RESTORE_CAP) snapWindow.removeFirst();

        // (2) impossible angular velocity ONTO the hitbox (incl. 180/large snaps).
        double hitboxSnapVelDegPerSec = SilentAimAnalyzer.hitboxSnapVelocity(analysis);
        data.setSilentLastHitboxSnapVelDegPerSec(hitboxSnapVelDegPerSec);
        double hitboxVel = CharSilentAimSignals.hitboxSnapVelocityScore(hitboxSnapVelDegPerSec, ping);

        // (3) center-lock + zero sub-degree jitter: push jitter to the window (cap enforced HERE),
        //     average jitter AND center margin, score only when BOTH are unnaturally small.
        Deque<Double> jitterWindow = data.getSilentCenterLockJitter();
        jitterWindow.addLast(SilentAimAnalyzer.centerLockJitter(analysis));
        while (jitterWindow.size() > CENTER_LOCK_CAP) jitterWindow.removeFirst();
        double centerLock = 0.0D;
        if (jitterWindow.size() >= 4) {
            double avgJitter = average(jitterWindow);
            double avgMargin = analysis.centerMarginDeg; // current-attack center margin window
            centerLock = CharSilentAimSignals.centerLockScore(avgMargin, avgJitter) * closeScale;
        }

        // (4) rotation<->movement desync (de-gated; no longer behind s1>0).
        double desync = CharSilentAimSignals.movementMismatchScore(data);

        // (5) GCD residue/conformity + non-vanilla recovered sensitivity.
        double lattice = computeLatticeSignal(data) * closeScale;

        // (6) attack-tick rotation exclusivity (existing onFlyingPacket counters).
        double exclusivity = CharSilentAimSignals.correlationScore(data) * closeScale;

        // (7) constant-velocity / linear-ramp toward the target.
        double linearRamp = analysis.linearRampRatio;

        // --- Fuse + scale by combat reliability ---
        double combinedScore = CharSilentAimSignals.fuse(
                snapRestore, hitboxVel, centerLock, desync, lattice, exclusivity, linearRamp);
        combinedScore *= combat.getSampleWeight();

        // ===================== MULTI-SIGNAL ENFORCEMENT =====================
        int strong = CharSilentAimSignals.strongSignalCount(snapRestore, hitboxVel);
        int sustainMin = plugin.tierCfg().checkInt(name(), "snapRestoreSustainMin", 3);
        double sustainAvg = plugin.tierCfg().checkDouble(name(), "snapRestoreSustainAvg", 0.45);
        boolean sustained = false;
        if (snapWindow.size() >= sustainMin) {
            sustained = average(snapWindow) >= sustainAvg;
        }
        boolean multiSignal = strong >= 2 || sustained;

        // --- Tiered buffer gain (blatant / suspicious / mild kept), gated on the multi-signal rule ---
        int buf = data.getKillAuraASwitchBuffer();
        int gain = 0;
        if (multiSignal) {
            if (combinedScore >= plugin.tierCfg().checkDouble(name(), "blatantThreshold", 0.70)) gain = 3;
            else if (combinedScore >= plugin.tierCfg().checkDouble(name(), "suspiciousThreshold", 0.45)) gain = 2;
            else if (combinedScore >= plugin.tierCfg().checkDouble(name(), "mildThreshold", 0.25)) gain = 1;
        }

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

        boolean shadow = plugin.tierCfg().checkBoolean(name(), "shadow", false);

        // --- Conservative packet cancel on high-confidence sustained pattern ---
        double cancelThreshold = plugin.tierCfg().checkDouble(name(), "cancelThreshold", 0.85);
        if (!shadow && multiSignal && combinedScore >= cancelThreshold
                && buf >= 3 && combat.getSampleWeight() >= 0.40) {
            blockAttack(p, data, "silent-aim score=" + r(combinedScore)
                    + " snap=" + r(snapRestore) + " vel=" + r(hitboxVel) + " buf=" + buf);
        }

        // --- Flag a SUSTAINED pattern over the buffer ---
        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 4);
        if (gain > 0 && buf < bufferToFlag) {
            verbose(p, "buf=" + buf + "/" + bufferToFlag + " score=" + r(combinedScore)
                    + " strong=" + strong + " sustained=" + sustained);
        }
        if (buf >= bufferToFlag) {
            if (!shadow) {
                blockAttack(p, data, "silent-aim score=" + r(combinedScore)
                        + " snap=" + r(snapRestore) + " vel=" + r(hitboxVel) + " buf=" + buf);
            }
            fail(p, data, plugin.tierCfg().checkDouble(name(), "vl", 1.5),
                    "score=" + r(combinedScore) + " snap=" + r(snapRestore) + " vel=" + r(hitboxVel)
                            + " center=" + r(centerLock) + " desync=" + r(desync) + " lattice=" + r(lattice)
                            + " excl=" + r(exclusivity) + " ramp=" + r(linearRamp)
                            + " strong=" + strong + " sustained=" + sustained + " buf=" + buf
                            + " dist=" + r(dist) + " " + combat.debugSummary());
            data.setKillAuraASwitchBuffer(0);
            snapWindow.clear();
        }

        // --- Activate post-reset tracking (CharAimReset confirm path) on a suspicious snap ---
        if (!CombatContextAnalyzer.isLikelySpacingMovement(plugin, data, p, now)
                && !CombatContextAnalyzer.isLikelyCounterstrafeSpacing(plugin, data, now)
                && (snapRestore > 0.30D || hitboxVel > 0.30D) && !data.isKillAuraAPostResetActive()) {
            activatePostResetTracking(data, eye, now);
        }
    }

    @Override
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {
        if (p == null || data == null) return;

        long now = System.currentTimeMillis();
        // KEEP: feed angularVelocitySamples + peak (read by other checks).
        GcdLatticeAnalysis.pushAngularSample(data.getAngularVelocitySamples(), yaw, pitch, now, 16);
        double peak = GcdLatticeAnalysis.peakAngularVelocityDegPerSec(data.getAngularVelocitySamples(), 50L);
        if (peak > data.getPeakAngularVelocityDegPerSec()) {
            data.setPeakAngularVelocityDegPerSec(peak);
        }

        // KEEP: post-reset confirm logic (CharAimReset depends on these counters).
        if (!data.isKillAuraAPostResetActive()) return;

        long elapsed = now - data.getKillAuraAPostResetStartMs();
        long windowMs = plugin.tierCfg().checkLong(name(), "postResetWindowMs", 150L);

        if (elapsed > windowMs || elapsed < 0) {
            data.setKillAuraAPostResetActive(false);
            return;
        }

        float baseYaw = data.getKillAuraAPostResetBaseYaw();
        float attackYaw = data.getKillAuraAPostResetAttackYaw();

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

        // KEEP: attack-tick rotation correlation counters (CharAimCorrelation depends on them).
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

    // ========== Signal helpers ==========

    /** GCD residue/conformity (max) plus non-vanilla recovered sensitivity. */
    private double computeLatticeSignal(PlayerData data) {
        if (data == null) return 0.0D;
        Deque<Float> yawDeltas = data.getYawDeltas();
        Deque<Float> pitchDeltas = data.getPitchDeltas();
        double conformity = GcdLatticeAnalysis.latticeConformitySuspicion(yawDeltas, pitchDeltas);
        double residue = GcdLatticeAnalysis.latticeResidueFraction(yawDeltas, pitchDeltas);
        double residueScore = residue >= 0.35D ? Math.min(1.0D, (residue - 0.35D) / 0.35D) : 0.0D;
        double latticeBreak = Math.max(conformity, residueScore);

        AimSensitivityProcessor.Result sens = AimSensitivityProcessor.analyze(yawDeltas, pitchDeltas);
        return Math.max(latticeBreak, sens.suspicion());
    }

    private void activatePostResetTracking(PlayerData data, Location eye, long now) {
        // Baseline = the rotation 2 samples back (the pre-snap heading the aim should restore TO).
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

    /** Mean of a deque of Doubles (null-safe, empty -> 0). */
    private double average(Deque<Double> values) {
        if (values == null || values.isEmpty()) return 0.0D;
        double sum = 0.0D;
        int n = 0;
        for (Double d : values) {
            if (d == null) continue;
            sum += d;
            n++;
        }
        return n == 0 ? 0.0D : sum / n;
    }

    private double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
