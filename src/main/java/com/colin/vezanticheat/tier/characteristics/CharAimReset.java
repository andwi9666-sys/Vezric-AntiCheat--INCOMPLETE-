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

/** Post-attack snap-back rotation pattern (KillAuraA signal 3). */
public final class CharAimReset extends TierCheck {

    public CharAimReset(VezAntiCheat plugin) {
        super(plugin, "CharAimReset", CheckTier.CHARACTERISTICS);
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
        if (combat == null || combat.getSampleWeight() < plugin.tierCfg().checkDouble(name(), "minSampleWeight", 0.20)) {
            decay(p, 0.25);
            return;
        }
        if (CombatContextAnalyzer.shouldExemptAimHeuristics(plugin, combat, data, p, now)) {
            decay(p, 0.30);
            return;
        }

        int confirmed = data.getKillAuraAPostResetConfirmed();
        long lastHit = data.getKillAuraALastHitMs();
        if (lastHit > 0 && (now - lastHit) > plugin.tierCfg().checkLong(name(), "confirmDecayMs", 3000L) && confirmed > 0) {
            data.setKillAuraAPostResetConfirmed(confirmed - 1);
            confirmed--;
        }

        int minConfirmed = plugin.tierCfg().checkInt(name(), "minConfirmed", 2);
        if (confirmed < minConfirmed) {
            maybeActivateTracking(p, data, target, now, combat);
            coolBuffer(p, 1);
            decay(p, 0.30);
            return;
        }

        if (incrementBuffer(p, confirmed >= minConfirmed + 1 ? 2 : 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.2),
                    "post-reset confirmed=" + confirmed + " " + combat.debugSummary());
            resetBuffer(p);
            data.setKillAuraAPostResetConfirmed(0);
        } else {
            verbose(p, "reset confirmed=" + confirmed);
        }

        maybeActivateTracking(p, data, target, now, combat);
    }

    private void maybeActivateTracking(Player p, PlayerData data, Entity target, long now,
                                       CombatContextAnalyzer.CombatContext combat) {
        if (data.isKillAuraAPostResetActive()) return;
        if (CombatContextAnalyzer.isLikelySpacingMovement(plugin, data, p, now)
                || CombatContextAnalyzer.isLikelyCounterstrafeSpacing(plugin, data, now)) {
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
        CombatUtil.ReachContext ctx = CombatUtil.analyzeReach(eye, target, targetData, data.getLastUseEntityTime(), rewindMs);
        if (ctx == null) return;
        if (ctx.getCompensatedDistance() < plugin.tierCfg().checkDouble(name(), "closeRangeBypass", 1.5)) return;
        if (ctx.isLegitExpansionMarginHit(eye)) return;

        double angle = CombatUtil.angularError(eye, ctx.getCompensatedLocation(), ctx.getWidth(), ctx.getHeight());
        double snapThreshold = plugin.tierCfg().checkDouble(name(), "activateAngle", 15.0);
        if (angle < snapThreshold || !combat.isClean()) return;

        PlayerData.PositionSample baseline = null;
        int count = 0;
        for (PlayerData.PositionSample s : data.getPositionHistory()) {
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
}
