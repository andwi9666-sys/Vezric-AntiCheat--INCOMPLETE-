package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.AimAssistUtil;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.LagProfileUtil;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * AimAssistA -- Yaw Consistency / Grid-Quantized Rotation Detection
 *
 * <p><b>What it detects:</b> Aim assist modules that produce unnaturally consistent
 * yaw rotation steps. Cheat clients using smooth aim or aim correction produce
 * rotation deltas that cluster around fixed step sizes, yielding very low standard
 * deviation and high "dominant step ratio" / "grid score" -- patterns impossible
 * for human mouse movement.</p>
 *
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>Track rotation samples via {@code AimAssistUtil.trackRotation} (yaw/pitch deltas).</li>
 *   <li>Compute combat context (CombatContextAnalyzer) to filter out unreliable data
 *       (trades, walk-throughs, low sample weight).</li>
 *   <li>Compute aim context (centerAngle, centerMargin) toward the current target.</li>
 *   <li>Over a configurable sample window (default 10 yaw deltas), calculate:
 *       mean yaw, stdDev, dominant step ratio (how many deltas share the same quantized step),
 *       and grid score (how well all deltas align to a quantized GCD grid).</li>
 *   <li>Dynamic thresholds adjust based on combat sample weight, combo state,
 *       and recent block-hits.</li>
 *   <li>A secondary "lag-smoothed aim" path catches aim assist that is masked by
 *       slight network jitter -- uses relaxed thresholds with LagProfileUtil context.</li>
 * </ol>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code aimAssistAVerbose} -- buffer counter incremented on suspicious ticks,
 *       decayed on clean ticks.</li>
 *   <li>{@code yawDeltas} -- rolling list of yaw delta magnitudes.</li>
 *   <li>{@code aimCenterErrors / aimCenterMargins} -- center-tracking accuracy history.</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> The verbose counter must reach {@code bufferToFlag}
 * (default 7, reduced by 1 under lag cover) before a flag fires. On each clean tick the
 * counter decrements by 1 (never below 0). On flag, counter resets to 0.</p>
 *
 * <p><b>Exemptions:</b></p>
 * <ul>
 *   <li>Combat context not clean (lag, trade, walk-through) -- decays and returns.</li>
 *   <li>Yaw delta outside min/max range (too small = normal tracking, too large = flick).</li>
 *   <li>centerAngle too large (player not aiming near target, likely irrelevant rotation).</li>
 * </ul>
 *
 * <p><b>False positive protections:</b> Dynamic threshold scaling based on combat
 * sample weight, combo leniency, block-hit leniency, lag cover tolerance expansion,
 * and a minimum buffer requirement (minimum 3 even if bufferToFlag is lowered).</p>
 *
 * <p><b>Connections to other checks:</b> Shares AimAssistUtil sample tracking
 * (pushSample) with AimAssistB and AimAssistC. Does not feed KillAuraH aggregate.</p>
 */
public final class CharAimAssistA extends TierCheck {
    public CharAimAssistA(VezAntiCheat plugin) {
        super(plugin, "CharAimAssistA", CheckTier.CHARACTERISTICS);
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
            data.setAimAssistAVerbose(Math.max(0, data.getAimAssistAVerbose() - 1));
            decay(p, 0.4);
            return;
        }

        AimAssistUtil.pushSample(data, ctx.getCenterAngle(), ctx.getCenterMargin());

        double yawDelta = rotation.getYawDelta();
        if (yawDelta < plugin.tierCfg().checkDouble(name(), "minYawDelta", 0.75)
                || yawDelta > plugin.tierCfg().checkDouble(name(), "maxYawDelta", 18.0)
                || ctx.getCenterAngle() > plugin.tierCfg().checkDouble(name(), "maxCenterAngle", 8.5)
                || combat.isTrade()
                || combat.isWalkThrough()) {
            data.setAimAssistAVerbose(Math.max(0, data.getAimAssistAVerbose() - 1));
            decay(p, 0.4);
            return;
        }

        int sampleWindow = plugin.tierCfg().checkInt(name(), "sampleWindow", 10);
        List<Float> yawSteps = AimAssistUtil.tailFloats(data.getYawDeltas(), sampleWindow);
        if (yawSteps.size() < sampleWindow) return;

        double meanYaw = AimAssistUtil.average(yawSteps);
        double stdYaw = AimAssistUtil.stdDev(yawSteps, meanYaw);
        double dominantStepRatio = AimAssistUtil.dominantStepRatio(yawSteps,
                plugin.tierCfg().checkDouble(name(), "stepQuantum", 0.01));
        double gridScore = AimAssistUtil.quantizedGridScore(yawSteps,
                plugin.tierCfg().checkDouble(name(), "gcdQuantum", 0.005));
        double dynamicStd = plugin.tierCfg().checkDouble(name(), "maxStdYaw", 0.12)
                + ((1.0 - combat.getSampleWeight()) * 0.18);
        double dynamicDominant = plugin.tierCfg().checkDouble(name(), "minDominantStepRatio", 0.72)
                + (combat.isCombo() ? 0.04 : 0.0);
        double dynamicGrid = plugin.tierCfg().checkDouble(name(), "minGridScore", 0.86)
                + (combat.isRecentBlockhit() ? 0.03 : 0.0);

        boolean suspicious = meanYaw >= plugin.tierCfg().checkDouble(name(), "minMeanYaw", 1.0)
                && stdYaw <= dynamicStd
                && dominantStepRatio >= dynamicDominant
                && gridScore >= dynamicGrid
                && ctx.getCenterMargin() <= plugin.tierCfg().checkDouble(name(), "maxCenterMargin", 0.22)
                && combat.isPrecisionSample()
                && combat.getClickStdMs() <= plugin.tierCfg().checkDouble(name(), "maxClickStdMs", 8.5);
        long now = System.currentTimeMillis();
        boolean lagCoverActive = LagProfileUtil.isCombatCoverActive(plugin, p, data, now);
        boolean lagSmoothedAim = lagCoverActive
                && meanYaw >= plugin.tierCfg().checkDouble(name(), "lagCoverMinMeanYaw", 0.85D)
                && stdYaw <= dynamicStd + plugin.tierCfg().checkDouble(name(), "lagCoverExtraStdYaw", 0.08D)
                && dominantStepRatio >= plugin.tierCfg().checkDouble(name(), "lagCoverMinDominantStepRatio", 0.68D)
                && gridScore >= plugin.tierCfg().checkDouble(name(), "lagCoverMinGridScore", 0.80D)
                && ctx.getCenterMargin() <= plugin.tierCfg().checkDouble(name(), "lagCoverMaxCenterMargin", 0.30D)
                && combat.isPrecisionSample()
                && combat.getClickStdMs() <= plugin.tierCfg().checkDouble(name(), "lagCoverMaxClickStdMs", 12.0D);

        if (suspicious || lagSmoothedAim) {
            int vb = data.getAimAssistAVerbose() + 1 + (lagSmoothedAim ? LagProfileUtil.bufferBonus(plugin, p, data, now) : 0);
            data.setAimAssistAVerbose(vb);
            int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 7) - (lagSmoothedAim ? 1 : 0);
            if (vb >= Math.max(3, bufferToFlag)) {
                fail(p, data, 1.0,
                        (lagSmoothedAim ? "lag-smooth" : "consistency")
                                + " mean=" + r(meanYaw)
                                + " std=" + r(stdYaw)
                                + " dominant=" + r(dominantStepRatio)
                                + " grid=" + r(gridScore)
                                + " center=" + r(ctx.getCenterAngle())
                                + " " + combat.debugSummary());
                data.setAimAssistAVerbose(0);
            }
        } else {
            data.setAimAssistAVerbose(Math.max(0, data.getAimAssistAVerbose() - 1));
            decay(p, 0.5);
        }
    }

    protected double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
