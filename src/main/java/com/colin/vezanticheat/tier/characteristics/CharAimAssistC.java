package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.AimAssistUtil;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * AimAssistC -- Smooth Tracking / Acceleration Consistency Detection
 *
 * <p><b>What it detects:</b> Aim assist that produces smooth, low-acceleration
 * tracking with minimal overshoot. Legitimate players exhibit jittery acceleration
 * profiles and occasional overshoots when tracking a moving target. Cheat modules
 * that smoothly interpolate toward the target produce unnaturally flat acceleration
 * and near-zero overshoot ratios.</p>
 *
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>Track rotation and compute center errors over a sample window (default 12).</li>
 *   <li>Compute mean center error, std error, max single error.</li>
 *   <li>Compute mean acceleration (absolute difference of consecutive yaw deltas)
 *       and acceleration standard deviation.</li>
 *   <li>Compute overshoot ratio: fraction of samples where center error exceeds
 *       a threshold angle (default 1.4 degrees).</li>
 *   <li>Flag when ALL metrics are below their thresholds simultaneously, indicating
 *       machine-like tracking precision.</li>
 * </ol>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code aimAssistCVerbose} -- buffer counter.</li>
 *   <li>{@code aimCenterErrors} -- rolling center-angle error history.</li>
 *   <li>{@code yawDeltas} -- rolling yaw delta values for acceleration computation.</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> Verbose counter increments by 1 per suspicious
 * tick. Flags at bufferToFlag (default 6). Decrements by 1 on clean ticks.</p>
 *
 * <p><b>Exemptions:</b></p>
 * <ul>
 *   <li>Combat not clean, trade, or walk-through.</li>
 *   <li>Yaw delta below minYawDelta (stationary aim).</li>
 *   <li>Distance below minRange (trivially easy tracking at close range).</li>
 * </ul>
 *
 * <p><b>False positive protections:</b> Dynamic acceleration tolerance expanded
 * by (1 - sampleWeight) factor; requires isPrecisionSample and low attack interval
 * std from combat context; overshoot ratio allows up to 8% legitimate overshoots.</p>
 *
 * <p><b>Connections:</b> Shares AimAssistUtil sample tracking with AimAssistA/B.</p>
 */
public final class CharAimAssistC extends TierCheck {
    public CharAimAssistC(VezAntiCheat plugin) {
        super(plugin, "CharAimAssistC", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {
        AimAssistUtil.RotationSample rotation = AimAssistUtil.trackRotation(data, yaw, pitch);
        if (rotation == null) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null) return;

        AimAssistUtil.AimContext ctx = AimAssistUtil.currentContext(plugin, p, data, name());
        if (ctx == null || !combat.isClean()
                || combat.isActiveCombatSpam() || combat.isLegitCombatMovement()) {
            data.setAimAssistCVerbose(Math.max(0, data.getAimAssistCVerbose() - 1));
            decay(p, 0.4);
            return;
        }

        AimAssistUtil.pushSample(data, ctx.getCenterAngle(), ctx.getCenterMargin());

        if (rotation.getYawDelta() < plugin.tierCfg().checkDouble(name(), "minYawDelta", 0.7)
                || ctx.getDistance() < plugin.tierCfg().checkDouble(name(), "minRange", 2.0)
                || combat.isTrade()
                || combat.isWalkThrough()) {
            data.setAimAssistCVerbose(Math.max(0, data.getAimAssistCVerbose() - 1));
            decay(p, 0.4);
            return;
        }

        int sampleWindow = plugin.tierCfg().checkInt(name(), "sampleWindow", 12);
        List<Double> errors = AimAssistUtil.tail(data.getAimCenterErrors(), sampleWindow);
        List<Float> yawSteps = AimAssistUtil.tailFloats(data.getYawDeltas(), sampleWindow);
        if (errors.size() < sampleWindow || yawSteps.size() < sampleWindow) return;

        double meanError = AimAssistUtil.average(errors);
        double stdError = AimAssistUtil.stdDev(errors, meanError);
        double maxError = AimAssistUtil.max(errors);
        double meanAccel = AimAssistUtil.averageAcceleration(yawSteps);
        double accelStd = accelStd(yawSteps, meanAccel);
        double overshootRatio = overshootRatio(errors, plugin.tierCfg().checkDouble(name(), "overshootAngle", 1.4));
        double accelTolerance = plugin.tierCfg().checkDouble(name(), "maxAccelerationStd", 0.08)
                + ((1.0 - combat.getSampleWeight()) * 0.10);

        boolean suspicious = meanError <= plugin.tierCfg().checkDouble(name(), "maxMeanCenterAngle", 1.0)
                && stdError <= plugin.tierCfg().checkDouble(name(), "maxCenterStd", 0.22)
                && maxError <= plugin.tierCfg().checkDouble(name(), "maxSingleError", 1.45)
                && meanAccel <= plugin.tierCfg().checkDouble(name(), "maxMeanAcceleration", 0.11)
                && accelStd <= accelTolerance
                && overshootRatio <= plugin.tierCfg().checkDouble(name(), "maxOvershootRatio", 0.08)
                && combat.isPrecisionSample()
                && combat.getAttackIntervalStdMs() <= plugin.tierCfg().checkDouble(name(), "maxAttackStdMs", 18.0);

        if (suspicious) {
            int vb = data.getAimAssistCVerbose() + 1;
            data.setAimAssistCVerbose(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 6)) {
                fail(p, data, 1.15,
                        "track mean=" + r(meanError)
                                + " std=" + r(stdError)
                                + " max=" + r(maxError)
                                + " accel=" + r(meanAccel)
                                + " astd=" + r(accelStd)
                                + " over=" + r(overshootRatio)
                                + " " + combat.debugSummary());
                data.setAimAssistCVerbose(0);
            }
        } else {
            data.setAimAssistCVerbose(Math.max(0, data.getAimAssistCVerbose() - 1));
            decay(p, 0.5);
        }
    }

    private double overshootRatio(List<Double> errors, double overshootAngle) {
        if (errors.isEmpty()) return 1.0;
        int overshoots = 0;
        for (Double error : errors) {
            if (error != null && error.doubleValue() >= overshootAngle) overshoots++;
        }
        return overshoots / (double) errors.size();
    }

    private double accelStd(List<Float> deltas, double meanAccel) {
        if (deltas.size() < 2) return 999.0;
        List<Double> accels = new ArrayList<Double>();
        for (int i = 1; i < deltas.size(); i++) {
            accels.add((double) Math.abs(deltas.get(i).floatValue() - deltas.get(i - 1).floatValue()));
        }
        return AimAssistUtil.stdDev(accels, meanAccel);
    }

    private double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
