package com.colin.vezanticheat.tier.prism.scaffold;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.ScaffoldUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator for the silent scaffold detection system. This is the single integration point the
 * three checks call:
 *
 * <ul>
 *   <li>{@link #observePlacement} — ingests a placement (deduplicated so the three checks share one
 *       analysis per packet), runs {@link ScaffoldAnalyzer}, drives the per-check suspicion buffers
 *       with decay and the "occasional perfect placement" allowance, and evaluates the Eagle cycle.</li>
 *   <li>{@link #observeSneak} — feeds packet sneak transitions into the Eagle accumulators.</li>
 * </ul>
 *
 * <p>The engine NEVER cancels placements, applies setbacks, or raises a public violation. It only
 * decides <i>whether</i> a silent report should fire; the checks do the diagnostics/verbose emit.</p>
 *
 * <p>Check identity is fixed by registered name: A = Scaffold (legality + raytrace), B = Eagle,
 * C = LegitScaffold (consistency). The names are kept for config/profile stability while the logic
 * is a full rewrite.</p>
 */
public final class ScaffoldEngine {

    public static final String SCAFFOLD = "PrismScaffoldA"; // legality + raytrace / line-of-sight
    public static final String EAGLE = "PrismScaffoldB";    // sneak-at-edge automation
    public static final String LEGIT = "PrismScaffoldC";    // consistency analysis

    private static final ConcurrentHashMap<UUID, ScaffoldData> STATE = new ConcurrentHashMap<UUID, ScaffoldData>();
    private static final int EAGLE_WINDOW = 24;

    private ScaffoldEngine() {}

    /** Immutable per-placement verdict; each check reads its own slice. */
    public static final class ScaffoldResult {
        public final boolean scaffoldFlag;
        public final boolean legitFlag;
        public final boolean eagleFlag;
        public final String debug;

        ScaffoldResult(boolean scaffoldFlag, boolean legitFlag, boolean eagleFlag, String debug) {
            this.scaffoldFlag = scaffoldFlag;
            this.legitFlag = legitFlag;
            this.eagleFlag = eagleFlag;
            this.debug = debug == null ? "" : debug;
        }

        public boolean any() {
            return scaffoldFlag || legitFlag || eagleFlag;
        }

        static final ScaffoldResult NONE = new ScaffoldResult(false, false, false, "");
    }

    // ------------------------------------------------------------ lifecycle

    public static void clearPlayer(UUID id) {
        if (id != null) STATE.remove(id);
    }

    public static void clearAll() {
        STATE.clear();
    }

    private static ScaffoldData state(Player p) {
        return STATE.computeIfAbsent(p.getUniqueId(), new java.util.function.Function<UUID, ScaffoldData>() {
            @Override
            public ScaffoldData apply(UUID uuid) {
                return new ScaffoldData();
            }
        });
    }

    // ------------------------------------------------------------ placement

    /**
     * Ingest a block placement and return the per-check verdict. Deduplicated: when all three checks
     * call this for the same packet within one tick, the analysis runs once and the cached verdict is
     * returned to the others.
     */
    public static ScaffoldResult observePlacement(VezAntiCheat plugin, Player p, PlayerData data,
                                                  Block against, int faceId,
                                                  float cursorX, float cursorY, float cursorZ) {
        if (plugin == null || p == null || data == null || against == null) return ScaffoldResult.NONE;

        long now = System.currentTimeMillis();
        ScaffoldData sd = state(p);

        long sig = placementSignature(against, faceId);
        if (sig == sd.lastPlacementSig && (now - sd.lastPlacementMs) <= 50L && sd.cachedResult != null) {
            return sd.cachedResult; // same packet, already analyzed for the earlier-dispatched check
        }

        ScaffoldUtil.Context ctx = ScaffoldUtil.analyze(plugin, p, data);
        boolean bridging = ctx != null
                && (ctx.bridgeLike || ctx.edgeBridgeLike || ctx.speedBridgeLike || ctx.sneakingBridge);

        if (!bridging
                || (ctx != null && ScaffoldUtil.shouldExemptBridgingFlag(plugin, data, ctx, now))) {
            decayAll(sd, plugin);
            sd.clearSequence();
            sd.lastPlacementSig = sig;
            sd.cachedResult = ScaffoldResult.NONE;
            return ScaffoldResult.NONE;
        }

        ScaffoldPlacement sample = buildSample(plugin, p, data, against, faceId, cursorX, cursorY, cursorZ, ctx, sd, now);
        sd.bridgeStreak++;
        int maxSamples = plugin.tierCfg().checkInt(SCAFFOLD, "maxSamples", 24);
        sd.placements.addLast(sample);
        while (sd.placements.size() > Math.max(8, maxSamples)) sd.placements.removeFirst();

        // Record the placement against any open Eagle sneak cycle BEFORE analysis.
        recordEaglePlacement(sd, plugin, now);
        sd.lastPlacementMs = now;
        sd.lastPlacementSig = sig;

        int minBridge = plugin.tierCfg().checkInt(SCAFFOLD, "minBridgeSequence", 4);
        int minSamples = plugin.tierCfg().checkInt(LEGIT, "minSamples", 7);
        if (sd.bridgeStreak < minBridge || sd.placements.size() < Math.min(minSamples, maxSamples)) {
            decayAll(sd, plugin);
            sd.cachedResult = ScaffoldResult.NONE;
            return ScaffoldResult.NONE;
        }

        String catDebug = ScaffoldAnalyzer.analyze(plugin, data, sd, sample, ctx, SCAFFOLD, LEGIT);

        boolean scaffoldFlag = updateScaffold(plugin, sd, now);
        boolean legitFlag = updateLegit(plugin, sd, now);
        boolean eagleFlag = updateEagle(plugin, sd, now);

        ScaffoldResult result = new ScaffoldResult(scaffoldFlag, legitFlag, eagleFlag, catDebug);
        sd.cachedResult = result;
        return result;
    }

    // --------------------------------------------------------------- sneak

    /** Feed a packet ENTITY_ACTION sneak transition into the Eagle accumulators. */
    public static void observeSneak(VezAntiCheat plugin, Player p, PlayerData data, String actionName) {
        if (plugin == null || p == null || data == null || actionName == null) return;
        long now = System.currentTimeMillis();
        ScaffoldData sd = state(p);

        if ("START_SNEAKING".equals(actionName)) {
            sd.sneaking = true;
            sd.sneakStartMs = now;
            sd.sneakStartEdgeFrac = edgeFraction(p);
        } else if ("STOP_SNEAKING".equals(actionName)) {
            if (sd.sneaking && sd.lastPlacementMs > 0L) {
                long releaseDelay = now - sd.lastPlacementMs;
                long maxReleaseDelay = plugin.tierCfg().checkLong(EAGLE, "maxReleaseDelayMs", 650L);
                if (releaseDelay >= 0L && releaseDelay <= maxReleaseDelay) {
                    push(sd.eagleReleaseDelays, releaseDelay);
                }
            }
            sd.sneaking = false;
        }
    }

    // ----------------------------------------------------------- internals

    private static ScaffoldPlacement buildSample(VezAntiCheat plugin, Player p, PlayerData data,
                                                 Block against, int faceId, float cursorX, float cursorY,
                                                 float cursorZ, ScaffoldUtil.Context ctx, ScaffoldData sd, long now) {
        float yaw = data.getLastPlaceYaw();
        float pitch = data.getLastPlacePitch();

        ScaffoldPlacement prev = sd.placements.peekLast();
        float yawDelta = prev == null ? 0.0F : wrap180(yaw - prev.yaw);
        float pitchDelta = prev == null ? 0.0F : pitch - prev.pitch;
        long interval = prev == null ? 0L : Math.max(0L, now - prev.time);

        boolean sneaking = data.isLastPlaceSneaking();
        boolean onGround = data.isLastPlaceOnGround();
        double lookVsMove = lookVsMoveDeg(data, yaw);

        Location feet = p.getLocation();
        double reach = plugin.tierCfg().checkDouble(SCAFFOLD, "rayTraceReach", 4.65D);
        double step = plugin.tierCfg().checkDouble(SCAFFOLD, "rayTraceStep", 0.05D);
        Location againstLoc = against.getLocation();
        ScaffoldRaytrace.Result ray = ScaffoldRaytrace.evaluate(
                p, feet, p.getEyeHeight(), yaw, pitch, againstLoc, cursorX, cursorY, cursorZ, reach, step);

        long supportWindow = plugin.tierCfg().checkLong(SCAFFOLD, "supportHistoryMs", 1500L);
        boolean legal = !ScaffoldUtil.isInvalidSupport(data, againstLoc, supportWindow);

        Location placed = data.getLastPlacedBlockLoc();
        if (placed == null) {
            BlockFace face = ScaffoldUtil.faceFromId(faceId);
            placed = face == null ? againstLoc : ScaffoldUtil.placedLocationFromFace(againstLoc, face);
        }
        double relX = placed == null ? 0.0D : placed.getX() - feet.getX();
        double relZ = placed == null ? 0.0D : placed.getZ() - feet.getZ();

        return new ScaffoldPlacement(now, yaw, pitch, yawDelta, pitchDelta, sneaking, onGround,
                interval, lookVsMove, ray.faceHit, ray.alignAngleDeg, legal, relX, relZ);
    }

    private static boolean updateScaffold(VezAntiCheat plugin, ScaffoldData sd, long now) {
        double score = ScaffoldAnalyzer.clamp01(sd.sRaytrace * 0.7D + sd.sLegality * 0.5D);
        double decay = plugin.tierCfg().checkDouble(SCAFFOLD, "bufferDecay", 0.55D);
        if (score >= 0.5D) sd.scaffoldBuf += score;
        else sd.scaffoldBuf = Math.max(0.0D, sd.scaffoldBuf * decay);

        double need = plugin.tierCfg().checkDouble(SCAFFOLD, "bufferToFlag", 8.0D);
        long cooldown = plugin.tierCfg().checkLong(SCAFFOLD, "flagCooldownMs", 2500L);
        if (sd.scaffoldBuf >= need && (now - sd.lastScaffoldVerboseMs) >= cooldown) {
            sd.lastScaffoldVerboseMs = now;
            sd.scaffoldBuf = need * 0.5D;
            return true;
        }
        return false;
    }

    private static boolean updateLegit(VezAntiCheat plugin, ScaffoldData sd, long now) {
        // Count active consistency categories (timing/rotation/sync); require minCategoriesToFlag.
        int active = 0;
        if (sd.sTiming > 0.45D) active++;
        if (sd.sRotation > 0.45D) active++;
        if (sd.sSync > 0.45D) active++;
        int minCats = plugin.tierCfg().checkInt(LEGIT, "minCategoriesToFlag", 2);

        double combined = ScaffoldAnalyzer.clamp01((sd.sTiming + sd.sRotation + 0.6D * sd.sSync) / 2.6D);
        int allowance = plugin.tierCfg().checkInt(LEGIT, "perfectPlacementAllowance", 2);

        double decay = plugin.tierCfg().checkDouble(LEGIT, "bufferDecay", 0.55D);
        boolean enoughEvidence = active >= minCats
                && (sd.perfectStreak > allowance || combined >= 0.7D);
        if (enoughEvidence && combined >= 0.45D) sd.legitBuf += combined;
        else sd.legitBuf = Math.max(0.0D, sd.legitBuf * decay);

        double need = plugin.tierCfg().checkDouble(LEGIT, "bufferToFlag", 8.0D);
        long cooldown = plugin.tierCfg().checkLong(LEGIT, "flagCooldownMs", 2500L);
        if (sd.legitBuf >= need && (now - sd.lastLegitVerboseMs) >= cooldown) {
            sd.lastLegitVerboseMs = now;
            sd.legitBuf = need * 0.5D;
            return true;
        }
        return false;
    }

    private static boolean updateEagle(VezAntiCheat plugin, ScaffoldData sd, long now) {
        int minCycles = plugin.tierCfg().checkInt(EAGLE, "eagleMinCycles", 7);
        if (sd.eagleEdgeFracs.size() < minCycles || sd.eagleStartLeads.size() < minCycles) {
            double decay = plugin.tierCfg().checkDouble(EAGLE, "bufferDecay", 0.45D);
            sd.eagleBuf = Math.max(0.0D, sd.eagleBuf * decay);
            return false;
        }

        double[] edge = ScaffoldAnalyzer.meanStd(toDoubles(sd.eagleEdgeFracs));
        double startStd = ScaffoldAnalyzer.meanStd(toDoubles(sd.eagleStartLeads))[1];
        double releaseStd = sd.eagleReleaseDelays.size() >= minCycles
                ? ScaffoldAnalyzer.meanStd(toDoubles(sd.eagleReleaseDelays))[1] : Double.MAX_VALUE;

        double edgeMeanMax = plugin.tierCfg().checkDouble(EAGLE, "edgeMeanMax", 0.075D);
        double edgeStdMax = plugin.tierCfg().checkDouble(EAGLE, "edgeStdMax", 0.018D);
        double startStdMax = plugin.tierCfg().checkDouble(EAGLE, "startStdMaxMs", 18.0D);
        double releaseStdMax = plugin.tierCfg().checkDouble(EAGLE, "releaseStdMaxMs", 22.0D);
        int minSignals = plugin.tierCfg().checkInt(EAGLE, "minSignals", 2);

        int signals = 0;
        if (edge[0] <= edgeMeanMax && edge[1] <= edgeStdMax) signals++; // same edge distance every cycle
        if (startStd <= startStdMax) signals++;                          // robotic sneak-start timing
        if (releaseStd <= releaseStdMax) signals++;                      // robotic sneak-release timing

        double decay = plugin.tierCfg().checkDouble(EAGLE, "bufferDecay", 0.45D);
        if (signals >= minSignals) sd.eagleBuf += signals / 3.0D + 0.34D;
        else sd.eagleBuf = Math.max(0.0D, sd.eagleBuf * decay);

        double need = plugin.tierCfg().checkDouble(EAGLE, "bufferToFlag", 6.0D);
        long cooldown = plugin.tierCfg().checkLong(EAGLE, "flagCooldownMs", 3000L);
        if (sd.eagleBuf >= need && (now - sd.lastEagleVerboseMs) >= cooldown) {
            sd.lastEagleVerboseMs = now;
            sd.eagleBuf = need * 0.5D;
            return true;
        }
        return false;
    }

    private static void recordEaglePlacement(ScaffoldData sd, VezAntiCheat plugin, long now) {
        if (!sd.sneaking || sd.sneakStartMs <= 0L) return;
        long lead = now - sd.sneakStartMs;
        long maxLead = plugin.tierCfg().checkLong(EAGLE, "maxStartLeadMs", 550L);
        if (lead < 0L || lead > maxLead) return;
        push(sd.eagleStartLeads, lead);
        if (!Double.isNaN(sd.sneakStartEdgeFrac)) pushD(sd.eagleEdgeFracs, sd.sneakStartEdgeFrac);
    }

    private static void decayAll(ScaffoldData sd, VezAntiCheat plugin) {
        sd.scaffoldBuf = Math.max(0.0D, sd.scaffoldBuf * plugin.tierCfg().checkDouble(SCAFFOLD, "bufferDecay", 0.55D));
        sd.legitBuf = Math.max(0.0D, sd.legitBuf * plugin.tierCfg().checkDouble(LEGIT, "bufferDecay", 0.55D));
        sd.eagleBuf = Math.max(0.0D, sd.eagleBuf * plugin.tierCfg().checkDouble(EAGLE, "bufferDecay", 0.45D));
    }

    // --------------------------------------------------------- small math

    private static long placementSignature(Block against, int faceId) {
        long h = against.getX();
        h = h * 92821L + against.getY();
        h = h * 92821L + against.getZ();
        h = h * 92821L + faceId;
        return h;
    }

    /** Distance (0..0.5) from the player's feet to the nearest horizontal block edge. */
    private static double edgeFraction(Player p) {
        Location loc = p.getLocation();
        double fx = loc.getX() - Math.floor(loc.getX());
        double fz = loc.getZ() - Math.floor(loc.getZ());
        return Math.min(Math.min(fx, 1.0D - fx), Math.min(fz, 1.0D - fz));
    }

    /** Angle (deg) between the look heading (yaw) and the recent horizontal movement; NaN if still. */
    private static double lookVsMoveDeg(PlayerData data, float yaw) {
        List<PlayerData.PositionSample> recent = new ArrayList<PlayerData.PositionSample>();
        for (PlayerData.PositionSample s : data.getPositionHistory()) {
            if (s != null) recent.add(s);
        }
        if (recent.size() < 2) return Double.NaN;
        PlayerData.PositionSample b = recent.get(recent.size() - 1);
        PlayerData.PositionSample a = recent.get(recent.size() - 2);
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        if ((dx * dx + dz * dz) < 0.0025D) return Double.NaN; // <0.05 blocks/tick: effectively still
        double moveHeading = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = Math.abs(wrap180((float) (moveHeading - yaw)));
        return diff;
    }

    private static float wrap180(float deg) {
        float d = deg % 360.0F;
        if (d > 180.0F) d -= 360.0F;
        if (d < -180.0F) d += 360.0F;
        return d;
    }

    private static void push(java.util.Deque<Long> dq, long v) {
        dq.addLast(v);
        while (dq.size() > EAGLE_WINDOW) dq.removeFirst();
    }

    private static void pushD(java.util.Deque<Double> dq, double v) {
        dq.addLast(v);
        while (dq.size() > EAGLE_WINDOW) dq.removeFirst();
    }

    private static List<Double> toDoubles(java.util.Deque<? extends Number> dq) {
        List<Double> out = new ArrayList<Double>(dq.size());
        for (Number n : dq) out.add(n.doubleValue());
        return out;
    }
}
