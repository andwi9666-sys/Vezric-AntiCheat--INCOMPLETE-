package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.combat.math.RequiredRotationUtil;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.AimAssistUtil;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.PingUtil;
import com.colin.vezanticheat.utils.SilentAimAnalyzer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * CharAimSnap -- SERVER-SIDE snap-and-restore confirmation for silent aim.
 *
 * <p><b>Re-scoped.</b> The old body only fired on "one big VISIBLE flick packet in the position
 * history" -- which a true silent aura never produces, because the cheat OVERRIDES the outgoing
 * rotation so the SERVER sees the aim land on the rewound hitbox while the client camera never moves.
 * Attack-tick angular error is therefore ~0 for genuine silent aim and useless as a trigger.</p>
 *
 * <p>This rewrite confirms silent aim via the INJECTED-ROTATION FINGERPRINT reconstructed by
 * {@link SilentAimAnalyzer} over the rotation ring buffer (raw yaw/pitch + timeMs) and the position
 * history, evaluated against the SAME lag-comp hitbox the reach engine used:</p>
 * <ol>
 *   <li><b>Snap-to-target-and-restore</b> (the load-bearing tell): within a window around the attack
 *       the server yaw/pitch jumps TO a value that aims at the hitbox (error collapses from a high
 *       prior error to ~0) and then jumps BACK toward the pre-snap heading on a later packet. Read as
 *       {@code SilentAimAnalyzer.snapRestoreScore}, accumulated into a SUSTAINED window.</li>
 *   <li><b>Impossible angular velocity onto the hitbox</b> (incl. sudden 180 snaps that terminate on
 *       a valid target): the landing step's deg/sec exceeds
 *       {@link RequiredRotationUtil#snapAngularVelocityThreshold(int)} while the post-step error is
 *       near-perfect.</li>
 *   <li><b>Legacy visible large-yaw-step</b> in the position history kept ONLY as a SECONDARY
 *       corroborator -- a visible snap still counts, but is no longer the sole trigger.</li>
 * </ol>
 *
 * <p>It also publishes its freshly-computed transient values onto {@link PlayerData}
 * (snap-restore deque cap 12, last snap-restore score, last hitbox-snap velocity, center-lock jitter
 * deque cap 16) so the shared {@link CharSilentAimSignals#score} fusion can consume them this tick,
 * since this runner dispatches BEFORE CharSilentAim.</p>
 *
 * <p><b>Tiers.</b> blatant = hitbox-snap velocity above the ping threshold landing near-perfect OR
 * snap-restore &gt;= snapRestoreBlatant (default 0.70). suspicious = snap-restore &gt;=
 * snapRestoreSuspicious (default 0.45) AND combat.isClean() AND the snap-restore window average is
 * sustained (&gt;= 0.40). Tiered buffer gain, flag at bufferToFlag, blockAttack on confidence.</p>
 *
 * <p><b>FP protections (all preserved):</b> velocity/teleport exempt, lag (tps/ping) gating, close
 * range minDistance bypass, isKbDisplacementFlick / isActiveCombatSpam / isLegitCombatMovement
 * exemptions, combat.isClean() required for the suspicious tier, sustained-window requirement for
 * suspicious, and buffer decay/reset. Java 8 only.</p>
 */
public final class CharAimSnap extends TierCheck {

    /** Sustained snap-restore window average that the suspicious tier requires. */
    private static final double SUSTAINED_WINDOW_AVG = 0.40D;
    /** Enforced caps (the analyzer/PlayerData contract: this CALLER trims the deques). */
    private static final int SNAP_RESTORE_CAP = 12;
    private static final int CENTER_LOCK_CAP = 16;

    public CharAimSnap(VezAntiCheat plugin) {
        super(plugin, "CharAimSnap", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;

        long now = System.currentTimeMillis();
        if (!data.wasLastUseEntityAttack()) return;
        if (now - data.getLastUseEntityTime() > plugin.tierCfg().checkLong(name(), "attackFreshnessMs", 150L)) return;
        if (data.isVelocityExempt() || data.isTeleportExempt()) return;

        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (target == null) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null) return;

        int ping = Math.max(0, PingUtil.getPing(p));
        long rewindMs = CombatUtil.compensationWindowMs(
                ping,
                plugin.tierCfg().checkLong(name(), "rewindBaseMs", 60L),
                plugin.tierCfg().checkDouble(name(), "rewindPingFactor", 0.20),
                plugin.tierCfg().checkLong(name(), "maxRewindMs", 160L)
        );

        Location eye = data.getLastAttackEyeLocation();
        if (eye == null || eye.getWorld() == null) eye = p.getEyeLocation();

        PlayerData targetData = target instanceof Player ? plugin.data().get((Player) target) : null;
        CombatUtil.ReachContext ctx = resolveReachContext(data, eye, target, targetData, data.getLastUseEntityTime(), rewindMs);
        if (ctx == null) return;

        // FP: angular error / snap reconstruction is unreliable at point-blank.
        if (ctx.getCompensatedDistance() < plugin.tierCfg().checkDouble(name(), "minDistance", 1.5)) {
            decay(p, 0.35);
            return;
        }

        long attackTime = data.getLastUseEntityTime();
        long historyWindowMs = plugin.tierCfg().checkLong(name(), "historyWindowMs", 150L);

        // FP: combat-spam / legit combat movement / knockback-displacement flick are not silent aim.
        if (combat.isActiveCombatSpam()
                || combat.isLegitCombatMovement()
                || CombatContextAnalyzer.isKbDisplacementFlick(data, attackTime, historyWindowMs + 80L)) {
            data.setKillAuraDBuffer(Math.max(0, data.getKillAuraDBuffer() - 1));
            decay(p, 0.40);
            data.setKillAuraDLastStateMs(now);
            return;
        }

        Location hitboxFeet = ctx.getCompensatedLocation();
        double width = ctx.getWidth();
        double height = ctx.getHeight();
        double postSnapAngle = CombatUtil.angularError(eye, hitboxFeet, width, height);

        // ===================== PRIMARY: server-side snap-and-restore fingerprint =====================
        // Reconstruct the per-tick rotation trajectory through the attack moment ourselves; this runner
        // dispatches BEFORE CharSilentAim, so we cannot rely on it having populated the transient values.
        long analyzerWindowMs = plugin.tierCfg().checkLong(name(), "historyWindowMs", 150L);
        SilentAimAnalyzer.Result fp = SilentAimAnalyzer.analyze(
                data.getRotationRingBuffer(),
                data.getPositionHistory(),
                eye, hitboxFeet, width, height,
                attackTime, ping, analyzerWindowMs);

        double snapRestore = SilentAimAnalyzer.snapRestoreScore(fp);
        double hitboxSnapVel = SilentAimAnalyzer.hitboxSnapVelocity(fp);
        double centerJitter = SilentAimAnalyzer.centerLockJitter(fp);

        // Publish transient values so the shared fusion (CharSilentAimSignals.score / snapScore) sees
        // them THIS tick; we own the deque caps per the PlayerData contract.
        data.setSilentLastSnapRestoreScore(snapRestore);
        data.setSilentLastHitboxSnapVelDegPerSec(hitboxSnapVel);
        pushCapped(data.getSilentSnapRestoreScores(), snapRestore, SNAP_RESTORE_CAP);
        pushCapped(data.getSilentCenterLockJitter(), centerJitter, CENTER_LOCK_CAP);

        double windowAvg = AimAssistUtil.average(
                AimAssistUtil.tail(data.getSilentSnapRestoreScores(), SNAP_RESTORE_CAP));

        // Impossible-velocity-onto-hitbox (incl. 180 snaps): landing step deg/sec beyond the ping
        // threshold AND the landing is near-perfect (snap-TO present == lands within hitbox center band).
        double snapVelThreshold = RequiredRotationUtil.snapAngularVelocityThreshold(ping);
        boolean impossibleVelocityLanding = fp.hasSnapTo && hitboxSnapVel > snapVelThreshold;

        double snapRestoreSuspicious = plugin.tierCfg().checkDouble(name(), "snapRestoreSuspicious", 0.45);
        double snapRestoreBlatant = plugin.tierCfg().checkDouble(name(), "snapRestoreBlatant", 0.70);

        // ===================== SECONDARY corroborator: legacy visible large-yaw-step =====================
        List<PlayerData.PositionSample> recent = recentSamples(data, attackTime, historyWindowMs);
        boolean legacyBlatant = false;
        boolean legacySuspicious = false;
        float maxYawStep = 0.0F;
        float pairedPitchStep = 0.0F;
        long stepAge = Long.MAX_VALUE;
        if (recent.size() >= plugin.tierCfg().checkInt(name(), "minHistorySamples", 3)) {
            PlayerData.PositionSample previous = null;
            for (PlayerData.PositionSample sample : recent) {
                if (previous != null) {
                    float yawStep = CombatUtil.angleDiff(sample.getYaw(), previous.getYaw());
                    float pitchStep = Math.abs(sample.getPitch() - previous.getPitch());
                    if (yawStep > maxYawStep) {
                        maxYawStep = yawStep;
                        pairedPitchStep = pitchStep;
                        stepAge = attackTime - sample.getTime();
                    }
                }
                previous = sample;
            }

            double minSnapYaw = plugin.tierCfg().checkDouble(name(), "minSnapYaw", 70.0);
            double blatantSnapYaw = plugin.tierCfg().checkDouble(name(), "blatantSnapYaw", 120.0);
            double maxPitchStep = plugin.tierCfg().checkDouble(name(), "maxPitchStep", 15.0);
            long maxSnapToAttackMs = plugin.tierCfg().checkLong(name(), "maxSnapToAttackMs", 50L);
            double maxPostSnapAngle = plugin.tierCfg().checkDouble(name(), "maxPostSnapAngle", 1.5);
            double blatantMaxPostSnapAngle = plugin.tierCfg().checkDouble(name(), "blatantMaxPostSnapAngle", 0.8);

            legacyBlatant = maxYawStep >= blatantSnapYaw
                    && pairedPitchStep <= maxPitchStep
                    && stepAge >= 0 && stepAge <= maxSnapToAttackMs
                    && postSnapAngle <= blatantMaxPostSnapAngle;

            legacySuspicious = !legacyBlatant
                    && maxYawStep >= minSnapYaw
                    && pairedPitchStep <= maxPitchStep
                    && stepAge >= 0 && stepAge <= maxSnapToAttackMs
                    && postSnapAngle <= maxPostSnapAngle
                    && combat.isClean();
        }

        // ===================== fuse into the two confidence tiers =====================
        // blatant: impossible-velocity landing OR a very complete snap-and-restore OR a blatant visible snap.
        boolean blatant = impossibleVelocityLanding
                || snapRestore >= snapRestoreBlatant
                || legacyBlatant;

        // suspicious: a real snap-and-restore that is SUSTAINED over the window during clean combat,
        // or the legacy visible suspicious snap.
        boolean suspicious = !blatant
                && ((snapRestore >= snapRestoreSuspicious && combat.isClean() && windowAvg >= SUSTAINED_WINDOW_AVG)
                    || legacySuspicious);

        // ===================== buffer / decay (unchanged accounting) =====================
        int buf = data.getKillAuraDBuffer();
        long lastState = data.getKillAuraDLastStateMs();
        long bufferResetMs = plugin.tierCfg().checkLong(name(), "bufferResetMs", 2500L);
        if (lastState > 0L && (now - lastState) > bufferResetMs) buf = 0;

        if (blatant || suspicious) {
            int gain = blatant ? 3 : 1;
            if (data.getKillAuraDConsecutiveWindows() == 1) {
                gain += 1;
                data.setKillAuraDConsecutiveWindows(0);
            } else {
                data.setKillAuraDConsecutiveWindows(1);
                data.setKillAuraDLastWindowMs(now);
            }
            buf += gain;

            int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 6);
            if (buf >= bufferToFlag) {
                String tag = blatant ? "blatant-silent-snap" : "silent-snap";
                blockAttack(p, data,
                        tag + " sr=" + r(snapRestore) + " vel=" + r(hitboxSnapVel) + " yaw=" + r(maxYawStep));
                fail(p, data, blatant ? 1.5 : 1.0,
                        tag
                                + " sr=" + r(snapRestore)
                                + " win=" + r(windowAvg)
                                + " vel=" + r(hitboxSnapVel) + "(thr=" + r(snapVelThreshold) + ")"
                                + " restore=" + fp.hasRestore
                                + " jit=" + r(centerJitter)
                                + " yaw=" + r(maxYawStep)
                                + " pitch=" + r(pairedPitchStep)
                                + " age=" + (stepAge == Long.MAX_VALUE ? -1 : stepAge) + "ms"
                                + " angle=" + r(postSnapAngle)
                                + " buf=" + buf
                                + " " + combat.debugSummary());
                buf = 0;
            }
        } else {
            long decayMs = plugin.tierCfg().checkLong(name(), "decayIntervalMs", 2500L);
            if (lastState > 0L && (now - lastState) > decayMs && buf > 0) {
                buf = Math.max(0, buf - 1);
            }
            if (data.getKillAuraDConsecutiveWindows() == 1 && data.getKillAuraDLastWindowMs() > 0
                    && (now - data.getKillAuraDLastWindowMs()) > 200L) {
                data.setKillAuraDConsecutiveWindows(0);
            }
            decay(p, 0.40);
        }

        data.setKillAuraDBuffer(buf);
        data.setKillAuraDLastStateMs(now);
    }

    private List<PlayerData.PositionSample> recentSamples(PlayerData data, long now, long windowMs) {
        List<PlayerData.PositionSample> list = new ArrayList<>();
        for (PlayerData.PositionSample sample : data.getPositionHistory()) {
            if (sample == null) continue;
            long age = now - sample.getTime();
            if (age >= 0L && age <= windowMs) list.add(sample);
        }
        return list;
    }

    /** Append a value then trim to {@code cap} from the front (the PlayerData deque contract). */
    private void pushCapped(Deque<Double> deque, double value, int cap) {
        if (deque == null) return;
        deque.addLast(value);
        while (deque.size() > cap) deque.removeFirst();
    }

    private CombatUtil.ReachContext resolveReachContext(
            PlayerData data, Location eye, Entity target, PlayerData targetData,
            long attackTime, long rewindMs) {
        if (plugin.getConfig().getBoolean("combat-engine.enabled", true)) {
            com.colin.vezanticheat.engine.CombatResult engine = data.getLastCombatResult();
            if (engine != null && engine.tracked) {
                CombatUtil.ReachContext ctx = com.colin.vezanticheat.engine.CombatRewind.toReachContext(engine);
                if (ctx != null) return ctx;
            }
        }
        return CombatUtil.analyzeReach(eye, target, targetData, attackTime, rewindMs);
    }

    private double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
