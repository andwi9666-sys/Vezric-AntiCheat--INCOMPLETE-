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

import java.util.Deque;

/** Unnatural center-of-hitbox clustering (KillAuraA signal 5). */
public final class CharAimCenter extends TierCheck {

    public CharAimCenter(VezAntiCheat plugin) {
        super(plugin, "CharAimCenter", CheckTier.CHARACTERISTICS);
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
        if (combat == null || !combat.isClean()) {
            coolBuffer(p, 1);
            decay(p, 0.35);
            return;
        }

        if (CombatContextAnalyzer.shouldExemptAimHeuristics(plugin, combat, data, p, now)) {
            coolBuffer(p, 1);
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
        CombatUtil.ReachContext ctx = CombatUtil.analyzeReach(eye, target, targetData, data.getLastUseEntityTime(), rewindMs);
        if (ctx == null) return;

        double dist = ctx.getCompensatedDistance();
        double minRange = plugin.tierCfg().checkDouble(name(), "minRange", 1.0);
        double maxRange = plugin.tierCfg().checkDouble(name(), "maxRange", 3.0);
        if (dist < minRange || dist > maxRange) {
            decay(p, 0.30);
            return;
        }

        if (ctx.isLegitExpansionMarginHit(eye)) {
            decay(p, 0.30);
            return;
        }

        double signal = recordAndScore(data, eye, ctx.getCompensatedLocation(), ctx.getWidth(), ctx.getHeight());
        double threshold = plugin.tierCfg().checkDouble(name(), "threshold", 0.45);
        if (signal < threshold) {
            coolBuffer(p, 1);
            decay(p, 0.35);
            return;
        }

        if (incrementBuffer(p, signal >= plugin.tierCfg().checkDouble(name(), "blatantThreshold", 0.75) ? 2 : 1)) {
            Deque<Double> errors = data.getKillAuraACenterErrors();
            double avg = average(errors);
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.1),
                    "centerBias signal=" + round3(signal) + " avgMargin=" + round3(avg)
                            + " samples=" + errors.size() + " dist=" + round3(dist)
                            + " " + combat.debugSummary());
            resetBuffer(p);
        }
    }

    private double recordAndScore(PlayerData data, Location eye, Location compensated, double width, double height) {
        Location center = compensated.clone().add(0, height / 2.0, 0);
        double centerAngle = CombatUtil.angularError(eye, center, 0.001, 0.001);
        double closestAngle = CombatUtil.angularError(eye, compensated, width, height);
        double centerMargin = Math.max(0.0, centerAngle - closestAngle);

        Deque<Double> errors = data.getKillAuraACenterErrors();
        errors.addLast(centerMargin);
        while (errors.size() > plugin.tierCfg().checkInt(name(), "maxSamples", 10)) errors.removeFirst();

        int minSamples = plugin.tierCfg().checkInt(name(), "minSamples", 5);
        if (errors.size() < minSamples) return 0.0;

        double avg = average(errors);
        double maxAvg = plugin.tierCfg().checkDouble(name(), "maxAvgMargin", 1.5);
        if (avg > maxAvg) return 0.0;
        return clamp((maxAvg - avg) / maxAvg, 0.0, 1.0);
    }

    private double average(Deque<Double> values) {
        if (values == null || values.isEmpty()) return 999.0;
        double sum = 0.0;
        for (Double d : values) sum += d == null ? 0.0 : d;
        return sum / values.size();
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
