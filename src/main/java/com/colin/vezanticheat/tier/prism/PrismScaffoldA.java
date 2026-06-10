package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.PrismPatternSupport;
import com.colin.vezanticheat.utils.ScaffoldUtil;
import com.colin.vezanticheat.verdict.PrismMitigationPolicy;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.logging.Logger;

/**
 * ScaffoldA -- Robotic Timing and Pitch-Lock Detection.
 *
 * <p><b>Cheat Detected:</b> Scaffold/Bridge hacks that place blocks beneath the player
 * while walking or towering with inhuman precision. Automated scaffold modules produce
 * telltale patterns: unnaturally consistent placement intervals (low coefficient of
 * variation), locked pitch angles, behind-player placements, hidden-face clicks, and
 * invalid support block relationships.</p>
 *
 * <p><b>Detection Algorithm:</b> Evaluates multiple evidence signals simultaneously:</p>
 * <ol>
 *   <li><b>Robotic timing:</b> Computes statistics (avg, CV, spread) over the last N
 *       placement intervals. Flags if avg <= maxAverageMs (200), CV <= maxCv (0.12),
 *       AND spread <= maxSpreadMs (90) -- indicating machine-like consistency.</li>
 *   <li><b>Pitch-lock:</b> Measures pitch range (max - min degrees) across recent
 *       placements. If range <= maxPitchCv (6.0 degrees), the player's aim is
 *       suspiciously stable.</li>
 *   <li><b>Behind-place:</b> The placed block is in the opposite direction of movement
 *       (behindDot < -0.56).</li>
 *   <li><b>Hidden-face / invalid-support / face-ray-miss:</b> Geometric impossibilities
 *       from ScaffoldUtil context analysis.</li>
 *   <li><b>Impossible aim:</b> Aim dot-product mismatch with shallow pitch.</li>
 *   <li><b>Far placement:</b> Block placed beyond max eye distance (4.05 blocks).</li>
 *   <li>Flags when evidence >= 2 OR any single strong indicator (impossibleAim, behindPlace,
 *       farPlace, hiddenFace, invalidSupport, faceRayMiss) is true.</li>
 * </ol>
 *
 * <p><b>Pipeline Connection:</b> Invoked via {@code onBlockPlacePacket()} from the Bukkit
 * BlockPlaceEvent. Uses {@link com.colin.vezanticheat.utils.ScaffoldUtil#analyze} for
 * geometric context analysis and {@link com.colin.vezanticheat.utils.ScaffoldUtil#timingStats}
 * for interval statistics. Pitch history is stored in
 * {@link PlayerData#getScaffoldPitchHistory()} deque (max 20 entries).</p>
 *
 * <p><b>Buffer/Threshold System:</b> Uses {@link PlayerData#getScaffoldVerboseA()}.
 * Evidence >= 3 adds +2; otherwise +1. Default threshold: 2.
 * On flag, half the interval samples are trimmed (not cleared) to allow rapid re-detection.
 * VL decay 1.0 on clean placements.</p>
 *
 * <p><b>False Positive Protections:</b></p>
 * <ul>
 *   <li>Close-build/defense context (pillar building, nearby block placing) is explicitly
 *       whitelisted and causes buffer decay.</li>
 *   <li>"Legit bridge-like" placements (sneaking + close support + good aim + downward
 *       pitch) are whitelisted.</li>
 *   <li>Dirty ScaffoldUtil samples (lag, velocity, weird surfaces) cause buffer decay.</li>
 *   <li>Requires minimum placement streak (default 5) before evaluation.</li>
 *   <li>Requires extension-like or tower-like context (not random block placing).</li>
 *   <li>Fly/AllowFlight/Vehicle players are skipped via ScaffoldUtil.shouldSkip.</li>
 *   <li>Optional debug logging (config flag) for tuning.</li>
 * </ul>
 */
public final class PrismScaffoldA extends TierCheck {
    public PrismScaffoldA(VezAntiCheat plugin) {
        super(plugin, "PrismScaffoldA", CheckTier.PRISM);
    }

    private boolean dbg() {
        return plugin.tierCfg().checkBoolean(name(), "debug", false);
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId, float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null) return;

        Logger log = dbg() ? plugin.getLogger() : null;

        if (PlayerData.bypass(p)) {
            if (log != null) log.info("[ScaffoldA] SKIP " + p.getName() + " -> bypass");
            return;
        }
        if (ScaffoldUtil.shouldSkip(p, data)) {
            if (log != null) log.info("[ScaffoldA] SKIP " + p.getName()
                    + " -> shouldSkip (fly=" + p.isFlying() + " allowFlight=" + p.getAllowFlight()
                    + " vehicle=" + p.isInsideVehicle()
                    + " tpExempt=" + data.isTeleportExempt()
                    + " velExempt=" + data.isVelocityExempt() + ")");
            return;
        }

        ScaffoldUtil.Context ctx = ScaffoldUtil.analyze(plugin, p, data);
        if (ctx == null) {
            if (log != null) log.info("[ScaffoldA] SKIP " + p.getName() + " -> analyze returned null"
                    + " (placed=" + data.getLastPlacedBlockLoc()
                    + " against=" + data.getLastPlaceAgainstLoc()
                    + " lastLoc=" + data.getLastLoc() + ")");
            return;
        }
        if (!ctx.clean) {
            if (log != null) log.info("[ScaffoldA] SKIP " + p.getName() + " -> dirty sample " + ctx.debug);
            data.setScaffoldVerboseA(Math.max(0, data.getScaffoldVerboseA() - 1));
            decay(p, 1.0);
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (ScaffoldUtil.shouldExemptBridgingFlag(plugin, data, ctx, nowMs)) {
            if (log != null) log.info("[ScaffoldA] SKIP " + p.getName() + " -> bridge grace");
            data.setScaffoldVerboseA(Math.max(0, data.getScaffoldVerboseA() - 2));
            decay(p, 1.0);
            return;
        }

        // Record pitch for variance tracking regardless of bridging context
        Deque<Float> pitchHistory = data.getScaffoldPitchHistory();
        pitchHistory.addLast(data.getLastPlacePitch());
        if (pitchHistory.size() > 20) pitchHistory.removeFirst();

        // Accept both bridge-like AND tower-like placements.
        // Do NOT gate on sneakingBridge -- many scaffold cheats sneak.
        if (ctx.closeBuildLike) {
            if (log != null) log.info("[ScaffoldA] SKIP " + p.getName()
                    + " -> close-build/defense context"
                    + " moveH=" + r(ctx.moveH)
                    + " placedH=" + r(ctx.playerToPlacedH)
                    + " supportH=" + r(ctx.playerToAgainstH)
                    + " aimDot=" + r(ctx.aimDot));
            data.setScaffoldVerboseA(Math.max(0, data.getScaffoldVerboseA() - 2));
            decay(p, 1.0);
            return;
        }
        if (!ctx.extensionLike && !ctx.towerLike) {
            if (log != null) log.info("[ScaffoldA] SKIP " + p.getName()
                    + " -> not extension/tower (placedBelow=" + r(ctx.placedBelow)
                    + " hDist=" + r(ctx.playerToPlacedH) + ")");
            data.setScaffoldVerboseA(0);
            decay(p, 1.0);
            return;
        }

        int sampleSize = plugin.tierCfg().checkInt(name(), "sampleSize", 6);
        ScaffoldUtil.Stats stats = ScaffoldUtil.timingStats(data.getScaffoldIntervals(), sampleSize);

        if (ScaffoldUtil.shouldTreatBridgeAsLegit(plugin, data, ctx, stats, nowMs)) {
            if (log != null) log.info("[ScaffoldA] SKIP " + p.getName() + " -> legit bridge imperfections");
            data.setScaffoldVerboseA(Math.max(0, data.getScaffoldVerboseA() - 2));
            data.setPrismPerfectPlacementStreak(0);
            decay(p, 1.0);
            return;
        }

        int pitchSampleSize = plugin.tierCfg().checkInt(name(), "pitchSampleSize", 6);
        double maxPitchRange = plugin.tierCfg().checkDouble(name(), "maxPitchCv", 6.0);
        double pitchRange = ScaffoldUtil.pitchCv(data.getScaffoldPitchHistory(), pitchSampleSize);
        double maxTimingCv = plugin.tierCfg().checkDouble(name(), "maxTimingCv", 0.08D);
        double maxSpread = plugin.tierCfg().checkDouble(name(), "maxSpreadMs", 75.0D);
        double maxAverageMs = plugin.tierCfg().checkDouble(name(), "maxAverageMs", 175.0);

        if (PrismPatternSupport.hasBridgeHumanImperfection(stats, pitchRange, maxTimingCv, maxPitchRange, maxSpread)) {
            data.setScaffoldVerboseA(Math.max(0, data.getScaffoldVerboseA() - 1));
            data.setPrismPerfectPlacementStreak(0);
            decay(p, 1.0);
            return;
        }

        // Machine pace only: sustained identical interval + pitch lock.
        double maxCv = plugin.tierCfg().checkDouble(name(), "maxCv", 0.06);
        boolean roboticTiming = stats != null
                && stats.avg <= maxAverageMs
                && stats.cv <= maxCv
                && (stats.max - stats.min) <= maxSpread;
        boolean pitchLocked = pitchRange <= maxPitchRange;
        int minStreak = plugin.tierCfg().checkInt(name(), "minStreak", 5);

        if (log != null) {
            log.info("[ScaffoldA] " + p.getName()
                    + " streak=" + data.getPlaceStreak() + "/" + minStreak
                    + " stats=" + (stats != null ? "avg=" + r(stats.avg) + " cv=" + r(stats.cv)
                            + " spread=" + (stats.max - stats.min) : "null")
                    + " pitchRange=" + r(pitchRange)
                    + " robotic=" + roboticTiming + " pitchLocked=" + pitchLocked
                    + " movement={" + ctx.debug + "}");
        }

        if (data.getPlaceStreak() < minStreak || !roboticTiming || !pitchLocked) {
            data.setScaffoldVerboseA(Math.max(0, data.getScaffoldVerboseA() - 1));
            decay(p, 1.0);
            return;
        }

        PrismPatternSupport.ScaffoldPatternScore pattern =
                PrismPatternSupport.analyzeScaffoldPattern(plugin, name(), data, ctx, stats);
        if (!pattern.suspicious) {
            data.setScaffoldVerboseA(Math.max(0, data.getScaffoldVerboseA() - 1));
            decay(p, 1.0);
            return;
        }

        int vb = data.getScaffoldVerboseA() + 1;
        data.setScaffoldVerboseA(vb);
        if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 4)) {
            if (plugin.tierCfg().checkBoolean(name(), "cancelPlaceOnFlag", false)) {
                blockPlace(data, "bridging_suspiciously");
            }
            failWithMitigation(p, data, 1.0,
                    "bridging_suspiciously " + pattern.debug + " " + ctx.debug,
                    PrismMitigationPolicy.fromPattern(pattern.confidence));
            while (data.getScaffoldIntervals().size() > sampleSize / 2) {
                data.getScaffoldIntervals().removeFirst();
            }
            data.setScaffoldVerboseA(0);
        }
    }

    private double r(double v) { return Math.round(v * 100.0) / 100.0; }
}
