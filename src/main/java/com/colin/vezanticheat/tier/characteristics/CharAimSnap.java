package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Pre-attack yaw snap with post-snap alignment (ported from KillAuraD). */
public final class CharAimSnap extends TierCheck {

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

        if (ctx.getCompensatedDistance() < plugin.tierCfg().checkDouble(name(), "minDistance", 1.5)) {
            decay(p, 0.35);
            return;
        }

        double postSnapAngle = CombatUtil.angularError(eye, ctx.getCompensatedLocation(), ctx.getWidth(), ctx.getHeight());

        long historyWindowMs = plugin.tierCfg().checkLong(name(), "historyWindowMs", 150L);
        List<PlayerData.PositionSample> recent = recentSamples(data, data.getLastUseEntityTime(), historyWindowMs);
        if (recent.size() < plugin.tierCfg().checkInt(name(), "minHistorySamples", 3)) {
            decay(p, 0.35);
            return;
        }

        long attackTime = data.getLastUseEntityTime();
        if (combat.isActiveCombatSpam()
                || combat.isLegitCombatMovement()
                || CombatContextAnalyzer.isKbDisplacementFlick(data, attackTime, historyWindowMs + 80L)) {
            data.setKillAuraDBuffer(Math.max(0, data.getKillAuraDBuffer() - 1));
            decay(p, 0.40);
            data.setKillAuraDLastStateMs(now);
            return;
        }

        float maxYawStep = 0.0F;
        float pairedPitchStep = 0.0F;
        long stepAge = Long.MAX_VALUE;

        PlayerData.PositionSample previous = null;
        for (PlayerData.PositionSample sample : recent) {
            if (previous != null) {
                float yawStep = CombatUtil.angleDiff(sample.getYaw(), previous.getYaw());
                float pitchStep = Math.abs(sample.getPitch() - previous.getPitch());
                if (yawStep > maxYawStep) {
                    maxYawStep = yawStep;
                    pairedPitchStep = pitchStep;
                    stepAge = data.getLastUseEntityTime() - sample.getTime();
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

        boolean blatantSnap = maxYawStep >= blatantSnapYaw
                && pairedPitchStep <= maxPitchStep
                && stepAge >= 0 && stepAge <= maxSnapToAttackMs
                && postSnapAngle <= blatantMaxPostSnapAngle;

        boolean suspicious = !blatantSnap
                && maxYawStep >= minSnapYaw
                && pairedPitchStep <= maxPitchStep
                && stepAge >= 0 && stepAge <= maxSnapToAttackMs
                && postSnapAngle <= maxPostSnapAngle
                && combat.isClean();

        int buf = data.getKillAuraDBuffer();
        long lastState = data.getKillAuraDLastStateMs();
        long bufferResetMs = plugin.tierCfg().checkLong(name(), "bufferResetMs", 2500L);
        if (lastState > 0L && (now - lastState) > bufferResetMs) buf = 0;

        if (blatantSnap || suspicious) {
            int gain = blatantSnap ? 3 : 1;
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
                blockAttack(p, data,
                        (blatantSnap ? "blatant-snap" : "snap")
                                + " yaw=" + r(maxYawStep)
                                + " angle=" + r(postSnapAngle));
                fail(p, data, blatantSnap ? 1.5 : 1.0,
                        (blatantSnap ? "blatant-snap" : "snap")
                                + " yaw=" + r(maxYawStep)
                                + " pitch=" + r(pairedPitchStep)
                                + " age=" + stepAge + "ms"
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
