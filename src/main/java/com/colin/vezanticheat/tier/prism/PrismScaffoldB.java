package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.ScaffoldUtil;
import org.bukkit.entity.Player;

/**
 * ScaffoldB -- Aim Mismatch and Geometric Impossibility Detection.
 *
 * <p><b>Cheat Detected:</b> Scaffold cheats that place blocks against surfaces the
 * player is not actually aiming at. Automated scaffold modules often click on block
 * faces that are invisible from the player's perspective, or aim at support blocks
 * that are geometrically behind the player relative to their movement direction.</p>
 *
 * <p><b>Detection Algorithm:</b></p>
 * <ol>
 *   <li>On each block placement (extension-like or tower-like context), four geometric
 *       impossibility checks are evaluated:
 *       <ul>
 *         <li><b>Impossible aim:</b> aimDot < 0.38 (player not looking at support) AND
 *             support is distant (>0.72 H) AND pitch is shallow (<12 degrees).</li>
 *         <li><b>Impossible support:</b> behindDot < -0.50 (support is behind player)
 *             AND placed block horizontal distance > 0.30.</li>
 *         <li><b>Hidden face:</b> The clicked face is not visible from the player's position.</li>
 *         <li><b>Face ray miss:</b> A ray from the player's eye in their look direction
 *             does not intersect the reported placement face.</li>
 *       </ul>
 *   </li>
 *   <li>If any of these are true, evidence is counted. Evidence >= 2 adds +2 to buffer;
 *       single evidence adds +1.</li>
 *   <li>At {@code bufferToFlag} (default 3), flags with VL 1.3.</li>
 * </ol>
 *
 * <p><b>Pipeline Connection:</b> Invoked via {@code onBlockPlacePacket()} from BlockPlaceEvent.
 * Uses {@link com.colin.vezanticheat.utils.ScaffoldUtil#analyze} for geometric context.
 * Reads placement pitch from {@link PlayerData#getLastPlacePitch()}.</p>
 *
 * <p><b>Buffer/Threshold System:</b> Uses {@link PlayerData#getScaffoldVerboseB()}.
 * Threshold: 3. Multi-evidence adds +2, single adds +1. Resets to 0 on flag.
 * VL decay 1.0 on clean placements.</p>
 *
 * <p><b>False Positive Protections:</b></p>
 * <ul>
 *   <li>Close-build/defense context whitelisted.</li>
 *   <li>Requires minimum streak of 4 consecutive extension/tower placements.</li>
 *   <li>Dirty samples cause buffer decay.</li>
 *   <li>All standard scaffold exemptions (fly, vehicle, teleport, velocity).</li>
 * </ul>
 */
public final class PrismScaffoldB extends TierCheck {
    public PrismScaffoldB(VezAntiCheat plugin) {
        super(plugin, "PrismScaffoldB", CheckTier.PRISM);
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId, float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null) return;
        if (PlayerData.bypass(p)) return;
        if (ScaffoldUtil.shouldSkip(p, data)) return;

        ScaffoldUtil.Context ctx = ScaffoldUtil.analyze(plugin, p, data);
        if (ctx == null) return;
        if (!ctx.clean) {
            data.setScaffoldVerboseB(Math.max(0, data.getScaffoldVerboseB() - 1));
            decay(p, 1.0);
            return;
        }

        if (ScaffoldUtil.shouldExemptBridgingFlag(plugin, data, ctx, System.currentTimeMillis())) {
            data.setScaffoldVerboseB(Math.max(0, data.getScaffoldVerboseB() - 2));
            decay(p, 1.0);
            return;
        }

        long nowMs = System.currentTimeMillis();
        ScaffoldUtil.Stats bridgeStats = ScaffoldUtil.timingStats(data.getScaffoldIntervals(), 6);
        if (ScaffoldUtil.shouldTreatBridgeAsLegit(plugin, data, ctx, bridgeStats, nowMs)) {
            data.setScaffoldVerboseB(Math.max(0, data.getScaffoldVerboseB() - 2));
            decay(p, 1.0);
            return;
        }

        if (ctx.closeBuildLike) {
            data.setScaffoldVerboseB(Math.max(0, data.getScaffoldVerboseB() - 2));
            decay(p, 1.0);
            return;
        }

        int minStreak = plugin.tierCfg().checkInt(name(), "minStreak", 4);
        if (data.getPlaceStreak() < minStreak) {
            data.setScaffoldVerboseB(Math.max(0, data.getScaffoldVerboseB() - 1));
            decay(p, 1.0);
            return;
        }

        // Accept bridge-like or tower-like. Do NOT gate on sneakingBridge.
        if (!ctx.extensionLike && !ctx.towerLike) {
            data.setScaffoldVerboseB(Math.max(0, data.getScaffoldVerboseB() - 1));
            decay(p, 1.0);
            return;
        }

        float placePitch = data.getLastPlacePitch();

        // Aim-mismatch: player's eye direction is far from the against-block,
        // AND the pitch at placement is shallow (looking nearly straight ahead).
        // Legitimate bridgers must look downward (pitch > ~12 degrees).
        boolean impossibleAim = ctx.aimDot < plugin.tierCfg().checkDouble(name(), "minAimDot", 0.38)
                && ctx.playerToAgainstH > plugin.tierCfg().checkDouble(name(), "minHorizontalSupport", 0.72)
                && ScaffoldUtil.isShallowPitch(placePitch,
                        (float) plugin.tierCfg().checkDouble(name(), "minBridgePitch", 12.0));
        boolean impossibleSupport = ctx.behindDot < plugin.tierCfg().checkDouble(name(), "maxBehindDot", -0.50)
                && ctx.playerToPlacedH > plugin.tierCfg().checkDouble(name(), "minHorizontalDistance", 0.30);
        boolean hiddenFace = ctx.hiddenFacePlace && !ctx.bridgeGeometryLenient;
        boolean faceRayMiss = !ctx.faceRayHit && !ctx.bridgeGeometryLenient;

        if (impossibleAim || impossibleSupport || hiddenFace || faceRayMiss) {
            int evidence = 0;
            if (impossibleAim) evidence++;
            if (impossibleSupport) evidence++;
            if (hiddenFace) evidence++;
            if (faceRayMiss) evidence++;
            int vb = data.getScaffoldVerboseB() + (evidence >= 2 ? 2 : 1);
            data.setScaffoldVerboseB(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 3)) {
                fail(p, data, 1.3,
                        "aimMismatch dot=" + r(ctx.aimDot)
                                + " pitch=" + r(placePitch)
                                + " hDist=" + r(ctx.playerToAgainstH)
                                + " behind=" + r(ctx.behindDot)
                                + " hiddenFace=" + hiddenFace
                                + " faceRayMiss=" + faceRayMiss
                                + " support=" + impossibleSupport
                                + " quality=" + r(ctx.quality)
                                + " " + ctx.debug);
                data.setScaffoldVerboseB(0);
            }
        } else {
            data.setScaffoldVerboseB(Math.max(0, data.getScaffoldVerboseB() - 1));
            decay(p, 1.0);
        }
    }

    private double r(double v) { return Math.round(v * 100.0) / 100.0; }
}
