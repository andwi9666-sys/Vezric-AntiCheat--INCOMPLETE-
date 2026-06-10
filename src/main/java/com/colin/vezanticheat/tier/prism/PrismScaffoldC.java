package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.ScaffoldUtil;
import org.bukkit.entity.Player;

/**
 * ScaffoldC -- Far-Placement and Edge-Impossible Reach Detection.
 *
 * <p><b>Cheat Detected:</b> Scaffold hacks that place blocks at distances exceeding
 * the vanilla interaction range, or that click on support block edges that are
 * geometrically impossible to reach from the player's position. These are characteristics
 * of scaffold modules that compute optimal placement positions without regard to the
 * player's actual reach capability.</p>
 *
 * <p><b>Detection Algorithm:</b></p>
 * <ol>
 *   <li>On each extension-like or tower-like placement, five distance/geometry checks:
 *       <ul>
 *         <li><b>Far expand:</b> Eye-to-placed-block distance > maxEyeDistance (4.0) AND
 *             horizontal distance > 1.35 blocks.</li>
 *         <li><b>Edge impossible:</b> Support block distance > maxSupportDistance (1.25) AND
 *             aim dot < minAimDot (0.45) -- too far from support and not aiming at it.</li>
 *         <li><b>Hidden face:</b> Clicked face not visible from eye position.</li>
 *         <li><b>Invalid support:</b> Support block relationship is geometrically invalid.</li>
 *         <li><b>Face ray miss:</b> Look-direction ray does not hit the reported face.</li>
 *       </ul>
 *   </li>
 *   <li>Evidence >= 2 adds +2; single evidence adds +1. At {@code bufferToFlag}
 *       (default 2), flags with VL 1.2.</li>
 * </ol>
 *
 * <p><b>Pipeline Connection:</b> Invoked via {@code onBlockPlacePacket()} from BlockPlaceEvent.
 * Uses ScaffoldUtil.analyze() for all distance and geometry measurements. The
 * {@code ctx.minPlacedReach} field measures minimum possible eye-to-block distance.</p>
 *
 * <p><b>Buffer/Threshold System:</b> Uses {@link PlayerData#getScaffoldVerboseC()}.
 * Threshold: 2 (lower than A/B because reach violations are strong evidence).
 * Multi-evidence adds +2. Resets to 0 on flag. VL decay 1.0.</p>
 *
 * <p><b>False Positive Protections:</b></p>
 * <ul>
 *   <li>Close-build/defense context and dirty samples excluded.</li>
 *   <li>Requires extension-like or tower-like context (not random placements).</li>
 *   <li>The 4.0 block eye distance threshold exceeds vanilla's 4.5 reach by accounting
 *       for block-center vs block-face measurement differences.</li>
 *   <li>All standard scaffold exemptions.</li>
 * </ul>
 */
public final class PrismScaffoldC extends TierCheck {
    public PrismScaffoldC(VezAntiCheat plugin) {
        super(plugin, "PrismScaffoldC", CheckTier.PRISM);
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId, float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null) return;
        if (PlayerData.bypass(p)) return;
        if (ScaffoldUtil.shouldSkip(p, data)) return;

        ScaffoldUtil.Context ctx = ScaffoldUtil.analyze(plugin, p, data);
        if (ctx == null) return;
        if (!ctx.clean) {
            data.setScaffoldVerboseC(Math.max(0, data.getScaffoldVerboseC() - 1));
            decay(p, 1.0);
            return;
        }

        if (ScaffoldUtil.shouldExemptBridgingFlag(plugin, data, ctx, System.currentTimeMillis())) {
            data.setScaffoldVerboseC(Math.max(0, data.getScaffoldVerboseC() - 2));
            decay(p, 1.0);
            return;
        }

        long nowMs = System.currentTimeMillis();
        ScaffoldUtil.Stats bridgeStats = ScaffoldUtil.timingStats(data.getScaffoldIntervals(), 6);
        if (ScaffoldUtil.shouldTreatBridgeAsLegit(plugin, data, ctx, bridgeStats, nowMs)) {
            data.setScaffoldVerboseC(Math.max(0, data.getScaffoldVerboseC() - 2));
            decay(p, 1.0);
            return;
        }

        if (ctx.closeBuildLike) {
            data.setScaffoldVerboseC(Math.max(0, data.getScaffoldVerboseC() - 2));
            decay(p, 1.0);
            return;
        }

        // Accept bridge-like or tower-like. Do NOT gate on sneakingBridge.
        if (!ctx.extensionLike && !ctx.towerLike) {
            data.setScaffoldVerboseC(0);
            decay(p, 1.0);
            return;
        }

        // Far-placement: block placed at a distance the player could not physically reach.
        boolean farExpand = ctx.minPlacedReach > plugin.tierCfg().checkDouble(name(), "maxEyeDistance", 4.0)
                && ctx.playerToPlacedH > plugin.tierCfg().checkDouble(name(), "minHorizontalDistance", 1.35);
        boolean edgeImpossible = ctx.playerToAgainstH > plugin.tierCfg().checkDouble(name(), "maxSupportDistance", 1.25)
                && ctx.aimDot < plugin.tierCfg().checkDouble(name(), "minAimDot", 0.45);
        boolean hiddenFace = ctx.hiddenFacePlace && !ctx.bridgeGeometryLenient;
        boolean invalidSupport = ctx.invalidSupport && !ctx.bridgeGeometryLenient;
        boolean faceRayMiss = !ctx.faceRayHit && !ctx.bridgeGeometryLenient;

        if (farExpand || edgeImpossible || hiddenFace || invalidSupport || faceRayMiss) {
            int evidence = 0;
            if (farExpand) evidence++;
            if (edgeImpossible) evidence++;
            if (hiddenFace) evidence++;
            if (invalidSupport) evidence++;
            if (faceRayMiss) evidence++;
            int vb = data.getScaffoldVerboseC() + (evidence >= 2 ? 2 : 1);
            data.setScaffoldVerboseC(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 2)) {
                fail(p, data, 1.2,
                        "farPlace dist=" + r(ctx.eyeToPlaced)
                                + " h=" + r(ctx.playerToPlacedH)
                                + " support=" + r(ctx.playerToAgainstH)
                                + " dot=" + r(ctx.aimDot)
                                + " hiddenFace=" + hiddenFace
                                + " invalidSupport=" + invalidSupport
                                + " faceRayMiss=" + faceRayMiss
                                + " quality=" + r(ctx.quality)
                                + " " + ctx.debug);
                data.setScaffoldVerboseC(0);
            }
        } else {
            data.setScaffoldVerboseC(Math.max(0, data.getScaffoldVerboseC() - 1));
            decay(p, 1.0);
        }
    }

    private double r(double v) { return Math.round(v * 100.0) / 100.0; }
}
