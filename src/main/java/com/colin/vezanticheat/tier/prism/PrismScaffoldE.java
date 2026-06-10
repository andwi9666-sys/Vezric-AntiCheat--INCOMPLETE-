package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.ScaffoldUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * ScaffoldE -- Invalid Support Block History Detection.
 *
 * <p><b>Cheat Detected:</b> Scaffold modules that click on support ("against") blocks
 * that were not actually present at the time of placement. Some scaffold cheats use
 * recently-placed blocks as support before the server has fully acknowledged them, or
 * fabricate support block positions entirely. This check cross-references the reported
 * against-block with the placement history to detect temporal impossibilities.</p>
 *
 * <p><b>Detection Algorithm:</b></p>
 * <ol>
 *   <li>On each extension-like or tower-like placement, checks if the reported
 *       against (support) block is "invalid" by calling
 *       {@code ScaffoldUtil.isInvalidSupport()} with a history window
 *       ({@code supportHistoryMs}, default 175ms).</li>
 *   <li>If the support is invalid AND (the face is hidden, face ray misses, OR support
 *       is far [>0.8 H distance]), the placement is suspicious.</li>
 *   <li>Buffer increments. At {@code bufferToFlag} (default 2), flags with VL 1.2.</li>
 * </ol>
 *
 * <p><b>Pipeline Connection:</b> Invoked via {@code onBlockPlacePacket()} from BlockPlaceEvent.
 * Uses {@link com.colin.vezanticheat.utils.ScaffoldUtil#isInvalidSupport} which checks
 * whether the reported against-block was placed too recently to have existed when the
 * client would have needed to target it (accounting for packet travel time).</p>
 *
 * <p><b>Buffer/Threshold System:</b> Uses {@link PlayerData#getScaffoldVerboseE()}.
 * Threshold: 2. Resets on flag. VL decay 1.0 on clean placements.</p>
 *
 * <p><b>False Positive Protections:</b></p>
 * <ul>
 *   <li>Close-build context excluded.</li>
 *   <li>Requires both invalid support AND at least one geometric impossibility (hidden
 *       face, ray miss, or distant support) -- invalid support alone is insufficient.</li>
 *   <li>The 175ms support history window accounts for network round-trip time.</li>
 *   <li>All standard scaffold exemptions.</li>
 * </ul>
 */
public final class PrismScaffoldE extends TierCheck {
    public PrismScaffoldE(VezAntiCheat plugin) {
        super(plugin, "PrismScaffoldE", CheckTier.PRISM);
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId, float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null) return;
        if (PlayerData.bypass(p) || !enabled() || ScaffoldUtil.shouldSkip(p, data)) return;

        ScaffoldUtil.Context ctx = ScaffoldUtil.analyze(plugin, p, data);
        if (ctx == null || !ctx.clean) {
            data.setScaffoldVerboseE(Math.max(0, data.getScaffoldVerboseE() - 1));
            decay(p, 1.0);
            return;
        }

        if (ScaffoldUtil.shouldExemptBridgingFlag(plugin, data, ctx, System.currentTimeMillis())) {
            data.setScaffoldVerboseE(Math.max(0, data.getScaffoldVerboseE() - 2));
            decay(p, 1.0);
            return;
        }

        long nowMs = System.currentTimeMillis();
        ScaffoldUtil.Stats bridgeStats = ScaffoldUtil.timingStats(data.getScaffoldIntervals(), 6);
        if (ScaffoldUtil.shouldTreatBridgeAsLegit(plugin, data, ctx, bridgeStats, nowMs)) {
            data.setScaffoldVerboseE(Math.max(0, data.getScaffoldVerboseE() - 2));
            decay(p, 1.0);
            return;
        }

        if (ctx.closeBuildLike) {
            data.setScaffoldVerboseE(Math.max(0, data.getScaffoldVerboseE() - 2));
            decay(p, 1.0);
            return;
        }

        if (!ctx.extensionLike && !ctx.towerLike) {
            data.setScaffoldVerboseE(Math.max(0, data.getScaffoldVerboseE() - 1));
            decay(p, 1.0);
            return;
        }

        Location againstLoc = data.getLastPlaceAgainstLoc();
        boolean invalidSupport = ScaffoldUtil.isInvalidSupport(
                data,
                againstLoc,
                plugin.tierCfg().checkLong(name(), "supportHistoryMs", 350L)
        );
        boolean hiddenFace = ctx.hiddenFacePlace && !ctx.bridgeGeometryLenient;
        boolean suspicious = invalidSupport && (hiddenFace || !ctx.faceRayHit || ctx.playerToAgainstH > plugin.tierCfg().checkDouble(name(), "minSupportDistance", 0.8D));
        if (ctx.bridgeGeometryLenient) {
            suspicious = false;
        }

        if (suspicious) {
            int vb = data.getScaffoldVerboseE() + 1;
            data.setScaffoldVerboseE(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 2)) {
                fail(p, data, 1.2,
                        "invalidSupport=true hiddenFace=" + hiddenFace
                                + " faceRayMiss=" + (!ctx.faceRayHit)
                                + " supportH=" + r(ctx.playerToAgainstH)
                                + " placedH=" + r(ctx.playerToPlacedH)
                                + " quality=" + r(ctx.quality)
                                + " " + ctx.debug);
                data.setScaffoldVerboseE(0);
            }
        } else {
            data.setScaffoldVerboseE(Math.max(0, data.getScaffoldVerboseE() - 1));
            decay(p, 1.0);
        }
    }

    private double r(double v) { return Math.round(v * 100.0D) / 100.0D; }
}
