package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.ScaffoldUtil;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * ScaffoldD -- Behind-Placement and Tower Aim Mismatch Detection.
 *
 * <p><b>Cheat Detected:</b> Scaffold modules that place blocks in the opposite direction
 * of the player's movement (behind them) while bridging, or that tower upward with an
 * aim direction that does not point at the support block. Legitimate bridging requires
 * the player to look backward (or at least toward the edge), but scaffold cheats place
 * blocks behind without the corresponding head rotation.</p>
 *
 * <p><b>Detection Algorithm:</b></p>
 * <ol>
 *   <li>Requires extension-like or tower-like context with minimum movement speed
 *       ({@code minMoveH}, default 0.08) for bridge detection.</li>
 *   <li>Evaluates "impossible support" if any of these are true after minStreak (4) placements:
 *       <ul>
 *         <li>behindDot < maxBehindDot (-0.55) AND placed block H distance > 0.25
 *             (placing behind relative to movement).</li>
 *         <li>Tower-like with aimDot < towerMinAimDot (0.40) AND support distance > 0.70
 *             (towering without looking at the support face).</li>
 *         <li>Hidden face, invalid support, or face ray miss from ScaffoldUtil.</li>
 *       </ul>
 *   </li>
 *   <li>If impossible support is detected, buffer increments. At {@code bufferToFlag}
 *       (default 2), flags with VL 1.5.</li>
 * </ol>
 *
 * <p><b>Pipeline Connection:</b> Invoked via {@code onBlockPlacePacket()} from BlockPlaceEvent.
 * Uses ScaffoldUtil.analyze() for all geometric context. The behind-dot is computed as
 * the dot product between the player's movement vector and the vector toward the placed
 * block (negative = behind).</p>
 *
 * <p><b>Buffer/Threshold System:</b> Uses {@link PlayerData#getScaffoldVerboseD()}.
 * Threshold: 2 (low because behind-placement is a strong signal). Resets on flag.
 * VL decay 1.0 on clean placements.</p>
 *
 * <p><b>False Positive Protections:</b></p>
 * <ul>
 *   <li>Close-build/defense context whitelisted with accelerated decay.</li>
 *   <li>Minimum movement speed threshold prevents flagging stationary placements.</li>
 *   <li>Requires placement streak >= 4 before evaluation.</li>
 *   <li>Optional debug logging for tuning.</li>
 *   <li>All standard scaffold exemptions.</li>
 * </ul>
 */
public final class PrismScaffoldD extends TierCheck {
    public PrismScaffoldD(VezAntiCheat plugin) {
        super(plugin, "PrismScaffoldD", CheckTier.PRISM);
    }

    private boolean dbg() {
        return plugin.tierCfg().checkBoolean(name(), "debug", false);
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId, float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null) return;

        Logger log = dbg() ? plugin.getLogger() : null;

        if (PlayerData.bypass(p)) { if (log != null) log.info("[ScaffoldD] SKIP " + p.getName() + " -> bypass"); return; }
        if (ScaffoldUtil.shouldSkip(p, data)) {
            if (log != null) log.info("[ScaffoldD] SKIP " + p.getName()
                    + " -> shouldSkip (fly=" + p.isFlying() + " allowFlight=" + p.getAllowFlight()
                    + " tpExempt=" + data.isTeleportExempt()
                    + " velExempt=" + data.isVelocityExempt() + ")");
            return;
        }

        ScaffoldUtil.Context ctx = ScaffoldUtil.analyze(plugin, p, data);
        if (ctx == null) {
            if (log != null) log.info("[ScaffoldD] SKIP " + p.getName() + " -> analyze null");
            return;
        }
        if (!ctx.clean) {
            if (log != null) log.info("[ScaffoldD] SKIP " + p.getName() + " -> dirty sample " + ctx.debug);
            data.setScaffoldVerboseD(Math.max(0, data.getScaffoldVerboseD() - 1));
            decay(p, 1.0);
            return;
        }

        if (ScaffoldUtil.shouldExemptBridgingFlag(plugin, data, ctx, System.currentTimeMillis())) {
            if (log != null) log.info("[ScaffoldD] SKIP " + p.getName() + " -> bridge grace");
            data.setScaffoldVerboseD(Math.max(0, data.getScaffoldVerboseD() - 2));
            decay(p, 1.0);
            return;
        }

        long nowMs = System.currentTimeMillis();
        ScaffoldUtil.Stats bridgeStats = ScaffoldUtil.timingStats(data.getScaffoldIntervals(), 6);
        if (ScaffoldUtil.shouldTreatBridgeAsLegit(plugin, data, ctx, bridgeStats, nowMs)) {
            if (log != null) log.info("[ScaffoldD] SKIP " + p.getName() + " -> legit bridge imperfections");
            data.setScaffoldVerboseD(Math.max(0, data.getScaffoldVerboseD() - 2));
            decay(p, 1.0);
            return;
        }

        if (ctx.closeBuildLike) {
            if (log != null) log.info("[ScaffoldD] SKIP " + p.getName()
                    + " -> close-build/defense context"
                    + " moveH=" + r(ctx.moveH)
                    + " placedH=" + r(ctx.playerToPlacedH)
                    + " supportH=" + r(ctx.playerToAgainstH)
                    + " aimDot=" + r(ctx.aimDot));
            data.setScaffoldVerboseD(Math.max(0, data.getScaffoldVerboseD() - 2));
            decay(p, 1.0);
            return;
        }

        double minMoveH = plugin.tierCfg().checkDouble(name(), "minMoveH", 0.08);
        // Accept bridge or tower support, but still require some movement for bridge detection.
        if ((!ctx.extensionLike && !ctx.towerLike) || (ctx.extensionLike && ctx.moveH < minMoveH)) {
            if (log != null) log.info("[ScaffoldD] SKIP " + p.getName()
                    + " -> not extension/tower/moving (extension=" + ctx.extensionLike
                    + " tower=" + ctx.towerLike
                    + " moveH=" + r(ctx.moveH) + "/" + r(minMoveH)
                    + " placedBelow=" + r(ctx.placedBelow) + ")");
            data.setScaffoldVerboseD(Math.max(0, data.getScaffoldVerboseD() - 1));
            decay(p, 1.0);
            return;
        }

        // Behind-placement: player places a block opposite their movement direction.
        double maxBehindDot = plugin.tierCfg().checkDouble(name(), "maxBehindDot", -0.55);
        double minHDist = plugin.tierCfg().checkDouble(name(), "minHorizontalDistance", 0.25);
        int minStreak = plugin.tierCfg().checkInt(name(), "minStreak", 4);

        boolean hiddenFace = ctx.hiddenFacePlace && !ctx.bridgeGeometryLenient;
        boolean invalidSupport = ctx.invalidSupport && !ctx.bridgeGeometryLenient;
        boolean faceRayMiss = !ctx.faceRayHit && !ctx.bridgeGeometryLenient;
        boolean impossibleSupport = data.getPlaceStreak() >= minStreak
                && ((ctx.behindDot < maxBehindDot && ctx.playerToPlacedH > minHDist && !ctx.bridgeGeometryLenient)
                || (ctx.towerLike && ctx.aimDot < plugin.tierCfg().checkDouble(name(), "towerMinAimDot", 0.40)
                && ctx.playerToAgainstH > plugin.tierCfg().checkDouble(name(), "towerMinSupportDistance", 0.70))
                || hiddenFace
                || invalidSupport
                || faceRayMiss);

        if (log != null) {
            log.info("[ScaffoldD] " + p.getName()
                    + " behindDot=" + r(ctx.behindDot) + "/" + r(maxBehindDot)
                    + " hDist=" + r(ctx.playerToPlacedH) + "/" + r(minHDist)
                    + " streak=" + data.getPlaceStreak() + "/" + minStreak
                    + " moveH=" + r(ctx.moveH)
                    + " hiddenFace=" + hiddenFace
                    + " invalidSupport=" + invalidSupport
                    + " faceRayMiss=" + faceRayMiss
                    + " quality=" + r(ctx.quality)
                    + " impossible=" + impossibleSupport
                    + " vb=" + data.getScaffoldVerboseD()
                    + " movement={" + ctx.debug + "}"
                    + " blockExempt=" + data.isBlockStateExempt());
        }

        if (impossibleSupport) {
            int vb = data.getScaffoldVerboseD() + 1;
            data.setScaffoldVerboseD(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 2)) {
                fail(p, data, 1.5,
                        "behindPlace dot=" + r(ctx.behindDot)
                                + " moved=" + r(ctx.moveH)
                                + " hDist=" + r(ctx.playerToPlacedH)
                                + " hiddenFace=" + hiddenFace
                                + " invalidSupport=" + invalidSupport
                                + " faceRayMiss=" + faceRayMiss
                                + " streak=" + data.getPlaceStreak()
                                + " quality=" + r(ctx.quality)
                        + " " + ctx.debug);
                data.setScaffoldVerboseD(0);
            }
        } else {
            data.setScaffoldVerboseD(Math.max(0, data.getScaffoldVerboseD() - 1));
            decay(p, 1.0);
        }
    }

    private double r(double v) { return Math.round(v * 100.0) / 100.0; }
}
