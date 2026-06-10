package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.AimAssistUtil;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * AimAssistB -- Snap-to-Target Follow-Through Detection
 *
 * <p><b>What it detects:</b> Aim assist that produces large yaw snaps toward a target
 * and then maintains unnaturally tight tracking (low center error, low margin)
 * in the follow-up samples. Legitimate players flick but cannot sustain perfect
 * tracking after a large snap at range.</p>
 *
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>Track rotation via AimAssistUtil; require a yaw delta above the snap threshold
 *       (default 18 degrees) and below maxYawDelta (default 90).</li>
 *   <li>After the snap, collect N follow-up center-error and center-margin samples
 *       (default 5 samples).</li>
 *   <li>Compute mean error, std error, max error, and mean margin across follow samples.</li>
 *   <li>Flag if ALL of: mean error low, std error low, max error low, margin low,
 *       lookDot high, and combat context is clean/precise.</li>
 * </ol>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code aimAssistBVerbose} -- buffer counter for this check.</li>
 *   <li>{@code aimCenterErrors} -- rolling center-angle error history.</li>
 *   <li>{@code aimCenterMargins} -- rolling center-margin history.</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> Verbose counter increments by 1 on suspicious
 * samples. Must reach {@code bufferToFlag} (default 5) to flag. Decrements by 1
 * on clean rotations.</p>
 *
 * <p><b>Exemptions:</b></p>
 * <ul>
 *   <li>Combat context not clean or is a walk-through.</li>
 *   <li>Yaw delta below snap threshold (normal tracking, not a snap).</li>
 *   <li>Distance below minRange (close range makes tight tracking trivial).</li>
 *   <li>Trade state (both players hitting each other skews angles).</li>
 * </ul>
 *
 * <p><b>False positive protections:</b> Dynamic follow-std adjusted by combat sample
 * weight (lower weight = more tolerance); combo leniency on maxFollowError; requires
 * isPrecisionSample from combat context; distance gate prevents close-range FPs.</p>
 *
 * <p><b>Connections:</b> Shares AimAssistUtil sample data with AimAssistA/C.
 * Does not feed KillAuraH aggregate.</p>
 */
public final class CharAimAssistB extends TierCheck {
    public CharAimAssistB(VezAntiCheat plugin) {
        super(plugin, "CharAimAssistB", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {
        AimAssistUtil.RotationSample rotation = AimAssistUtil.trackRotation(data, yaw, pitch);
        if (rotation == null) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null) return;

        AimAssistUtil.AimContext ctx = AimAssistUtil.currentContext(plugin, p, data, name());
        if (ctx == null || !combat.isClean()
                || combat.isActiveCombatSpam() || combat.isLegitCombatMovement()
                || CombatContextAnalyzer.isKbDisplacementFlick(
                        data, data.getLastUseEntityTime(),
                        plugin.tierCfg().checkLong(name(), "kbDisplacementWindowMs", 200L))) {
            data.setAimAssistBVerbose(Math.max(0, data.getAimAssistBVerbose() - 1));
            decay(p, 0.4);
            return;
        }

        AimAssistUtil.pushSample(data, ctx.getCenterAngle(), ctx.getCenterMargin());

        double yawDelta = rotation.getYawDelta();
        if (yawDelta < plugin.tierCfg().checkDouble(name(), "snapThreshold", 18.0)
                || yawDelta > plugin.tierCfg().checkDouble(name(), "maxYawDelta", 90.0)
                || combat.isWalkThrough()) {
            data.setAimAssistBVerbose(Math.max(0, data.getAimAssistBVerbose() - 1));
            decay(p, 0.4);
            return;
        }

        int followSamples = plugin.tierCfg().checkInt(name(), "followSamples", 5);
        List<Double> errors = AimAssistUtil.tail(data.getAimCenterErrors(), followSamples);
        List<Double> margins = AimAssistUtil.tail(data.getAimCenterMargins(), followSamples);
        if (errors.size() < followSamples) return;

        double meanError = AimAssistUtil.average(errors);
        double stdError = AimAssistUtil.stdDev(errors, meanError);
        double maxError = AimAssistUtil.max(errors);
        double meanMargin = AimAssistUtil.average(margins);
        double followStd = plugin.tierCfg().checkDouble(name(), "maxFollowStd", 0.16)
                + ((1.0 - combat.getSampleWeight()) * 0.20);
        double followError = plugin.tierCfg().checkDouble(name(), "maxFollowError", 1.15)
                + (combat.isCombo() ? 0.20 : 0.0);

        boolean suspicious = ctx.getDistance() >= plugin.tierCfg().checkDouble(name(), "minRange", 2.0)
                && meanError <= plugin.tierCfg().checkDouble(name(), "maxFollowCenterAngle", 0.85)
                && stdError <= followStd
                && maxError <= followError
                && meanMargin <= plugin.tierCfg().checkDouble(name(), "maxFollowMargin", 0.12)
                && ctx.getLookDot() >= plugin.tierCfg().checkDouble(name(), "minLookDot", 0.992)
                && combat.isPrecisionSample()
                && !combat.isTrade();

        if (suspicious) {
            int vb = data.getAimAssistBVerbose() + 1;
            data.setAimAssistBVerbose(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 5)) {
                fail(p, data, 1.05,
                        "snap yaw=" + r(yawDelta)
                                + " mean=" + r(meanError)
                                + " std=" + r(stdError)
                                + " max=" + r(maxError)
                                + " dot=" + r(ctx.getLookDot())
                                + " " + combat.debugSummary());
                data.setAimAssistBVerbose(0);
            }
        } else {
            data.setAimAssistBVerbose(Math.max(0, data.getAimAssistBVerbose() - 1));
            decay(p, 0.5);
        }
    }

    private double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
