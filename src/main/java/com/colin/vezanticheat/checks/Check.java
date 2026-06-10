package com.colin.vezanticheat.checks;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.checks.prediction.SimulationSubCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CheckAliasUtil;
import com.colin.vezanticheat.utils.LagProfileUtil;
import com.colin.vezanticheat.utils.PingUtil;
import com.colin.vezanticheat.utils.SetbackUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Check — Abstract base class for all anticheat detections.
 *
 * Every check in the system extends this class. It provides:
 * - fail(): Record a violation, increment VL, broadcast to staff, execute punishment
 * - decay(): Reduce VL over time when player behaves legitimately
 * - verbose(): Send debug info to staff with alerts enabled
 * - blockCurrentAttackPacket(): Cancel the current USE_ENTITY packet (combat checks)
 * - Setback infrastructure: teleport player to last known good position
 *
 * Check Lifecycle:
 * ================
 * 1. Registered in CheckManager during onEnable()
 * 2. Receives events via override methods (onMove, onAttack, onRotation, etc.)
 * 3. Reads player state from PlayerData
 * 4. Compares against expected behavior (thresholds, simulation, timing)
 * 5. Calls fail() on violation → increments VL → PunishmentManager checks threshold
 * 6. Calls decay() on clean movement → reduces VL over time
 *
 * VL (Violation Level) System:
 * ============================
 * Each check accumulates VL independently per player. VL decays over time.
 * PunishmentManager watches total VL across all checks in a category and
 * executes actions at configured thresholds (warn, kick, ban).
 *
 * Buffer Pattern:
 * ===============
 * Most checks use a buffer before flagging:
 *   if (suspicious) buffer++;
 *   if (buffer >= bufferToFlag) { fail(); buffer = 0; }
 *   else { buffer = max(0, buffer - 1); }
 *
 * This prevents single-tick false positives from causing flags.
 * The buffer must reach threshold through CONSECUTIVE violations.
 *
 * Categories:
 * ===========
 * SPEED, FLY, VELOCITY, KILLAURA, REACH, SCAFFOLD, INVENTORY, TIMER, etc.
 * Each category has its own punishment ladder in config.yml.
 */
public abstract class Check {

    protected final VezAntiCheat plugin;
    private final String name;     // Internal name: "SpeedA", "KillAuraC", etc.
    private final String category; // Category for grouping: "SPEED", "KILLAURA", etc.

    private static final ConcurrentHashMap<String, Long> LAST_FLAG_MS = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> LAST_DECAY_MS = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<UUID, Long> LAST_SETBACK_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Long> SHARED_KILLAURA_LAST_SUPPRESS_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Integer> CLASSIC_SIM_BUFFER = new ConcurrentHashMap<UUID, Integer>();
    private static final ConcurrentHashMap<UUID, Long> CLASSIC_SIM_LAST_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Long> CLASSIC_SIM_LAST_SETBACK_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Long> CLASSIC_SIM_LAST_REARM_SUPPRESS_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Long> CLASSIC_SIM_LAST_DEDUPE_SUPPRESS_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Long> CLASSIC_SIM_LAST_FLAG_SUPPRESS_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> CLASSIC_SIM_FAMILY_MS = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>();
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>> CLASSIC_SIM_FAMILY_SCORE = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>>();
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> CLASSIC_SIM_FAMILY_DECAY_MS = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>();

    public Check(VezAntiCheat plugin, String name, String category) {
        this.plugin = plugin;
        this.name = name;
        this.category = category;
    }

    public String name() { return name; }
    public String category() { return category; }
    public String publicName() { return CheckAliasUtil.displayName(name, category); }
    public String publicCategory() { return CheckAliasUtil.displayCategory(name, category); }

    /** Purge legacy static simulation state on quit to prevent cross-session buffer bleed. */
    public static void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        LAST_SETBACK_MS.remove(uuid);
        SHARED_KILLAURA_LAST_SUPPRESS_MS.remove(uuid);
        CLASSIC_SIM_BUFFER.remove(uuid);
        CLASSIC_SIM_LAST_MS.remove(uuid);
        CLASSIC_SIM_LAST_SETBACK_MS.remove(uuid);
        CLASSIC_SIM_LAST_REARM_SUPPRESS_MS.remove(uuid);
        CLASSIC_SIM_LAST_DEDUPE_SUPPRESS_MS.remove(uuid);
        CLASSIC_SIM_LAST_FLAG_SUPPRESS_MS.remove(uuid);
        CLASSIC_SIM_FAMILY_MS.remove(uuid);
        CLASSIC_SIM_FAMILY_SCORE.remove(uuid);
        CLASSIC_SIM_FAMILY_DECAY_MS.remove(uuid);
        SimulationSubCheck.clearPlayer(uuid);
    }

    public static ClassicSimulationSnapshot classicSimulationSnapshot(UUID uuid) {
        return classicSimulationSnapshot(uuid, System.currentTimeMillis(), Long.MAX_VALUE);
    }

    public static SimulationAggregateSnapshot simulationAggregateSnapshot(UUID uuid, long nowMs, long setbackVisibilityMs) {
        ClassicSimulationSnapshot classic = classicSimulationSnapshot(uuid, nowMs, setbackVisibilityMs);
        SimulationSubCheck.PredictionSimulationSnapshot prediction =
                SimulationSubCheck.predictionSimulationSnapshot(uuid, nowMs);
        if (classic == null && prediction == null) {
            return null;
        }
        return new SimulationAggregateSnapshot(classic, prediction);
    }

    public static ClassicSimulationSnapshot classicSimulationSnapshot(UUID uuid, long nowMs, long setbackVisibilityMs) {
        if (uuid == null) return null;

        Integer total = CLASSIC_SIM_BUFFER.get(uuid);
        Long lastUpdate = CLASSIC_SIM_LAST_MS.get(uuid);
        Long lastSetback = CLASSIC_SIM_LAST_SETBACK_MS.get(uuid);
        ConcurrentHashMap<String, Integer> scores = CLASSIC_SIM_FAMILY_SCORE.get(uuid);
        ConcurrentHashMap<String, Long> familyTimes = CLASSIC_SIM_FAMILY_MS.get(uuid);

        boolean hasTotal = total != null && total.intValue() > 0;
        boolean hasScores = scores != null && !scores.isEmpty();
        boolean hasRecentSetback = lastSetback != null
                && nowMs >= lastSetback.longValue()
                && (nowMs - lastSetback.longValue()) <= Math.max(0L, setbackVisibilityMs);
        if (!hasTotal && !hasScores && !hasRecentSetback) {
            return null;
        }

        Map<String, Integer> orderedScores = new LinkedHashMap<String, Integer>();
        Map<String, Long> orderedTimes = new LinkedHashMap<String, Long>();
        List<String> families = new ArrayList<String>();
        if (scores != null) {
            families.addAll(scores.keySet());
        }
        Collections.sort(families);
        for (String family : families) {
            if (family == null) continue;
            Integer score = scores.get(family);
            if (score == null || score.intValue() <= 0) continue;
            orderedScores.put(family, score);
            if (familyTimes != null && familyTimes.containsKey(family)) {
                orderedTimes.put(family, familyTimes.get(family));
            }
        }

        int snapshotTotal = hasTotal ? total.intValue() : 0;
        if (snapshotTotal <= 0) {
            for (Integer value : orderedScores.values()) {
                if (value != null && value.intValue() > 0) {
                    snapshotTotal += value.intValue();
                }
            }
        }
        if (snapshotTotal <= 0 && orderedScores.isEmpty()) {
            return null;
        }

        return new ClassicSimulationSnapshot(snapshotTotal,
                lastUpdate == null ? 0L : lastUpdate.longValue(),
                lastSetback == null ? 0L : lastSetback.longValue(),
                orderedScores,
                orderedTimes);
    }

    public boolean enabled() {
        return plugin.cfg().checkEnabled(name);
    }

    protected boolean shadowEnabled() {
        return plugin.cfg().checkBoolean(name, "shadow", false);
    }

    private static final java.util.Set<String> ENGINE_LEGACY_MOVEMENT = new java.util.HashSet<String>(
            java.util.Arrays.asList(
                    "FlyA", "FlyB", "FlyC", "FlyD", "FlyE", "FlyF",
                    "SpeedA", "SpeedB", "SpeedC", "SpeedD",
                    "PhaseA", "PhaseB",
                    "GroundSpoofA", "GroundSpoofB",
                    "NoSlowA", "NoSlowB"));

    /**
     * When the movement engine is primary, skip legacy duplicate movement checks to avoid
     * double flags and wasted work.
     */
    protected boolean skipWhenEngineMovement() {
        if (!ENGINE_LEGACY_MOVEMENT.contains(name)) return false;
        if (plugin.engine() == null || !plugin.engine().isEnabled()) return false;
        return plugin.getConfig().getBoolean("engine.shadow-legacy-duplicates", true);
    }

    protected void shadowRecord(Player p, String stage, String detail) {
        if (p == null) return;
        String displayName = publicName();
        String aliasedDetail = aliasAwareDetail(detail);
        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(p.getUniqueId(), displayName, stage, aliasedDetail);
        }
        plugin.getLogger().info("[" + displayName + "][" + stage + "] " + p.getName() + " " + aliasedDetail);
    }

    /**
     * Send verbose debug output to staff with verbose mode enabled.
     * Call this whenever a check detects something interesting (buffer increment,
     * detection, decay, etc.) regardless of whether it flags.
     */
    protected void verbose(Player p, String reason) {
        if (p == null) return;
        plugin.tierChecks().verboseToStaff(p.getName(), name, reason);
    }

    protected void verboseFlag(Player p, long checkVl, String debug) {
        if (p == null) return;
        plugin.tierChecks().verboseFlagToStaff(p.getName(), publicName(), checkVl, debug);
    }

    /**
     * Setback without adding VL (used for small blink, etc.)
     * Teleports to the last move "from" location (stored by PlayerMoveEvent).
     */
    protected void setback(Player p, PlayerData data, String debug) {
        if (p == null || data == null) return;
        if (!plugin.cfg().enabled()) return;
        if (!enabled()) return;
        if (PlayerData.bypass(p)) return;

        if (plugin.cfg().gateByLag()) {
            double tps = plugin.tps() != null ? plugin.tps().getTps() : 20.0;
            if (tps < plugin.cfg().minTps()) return;

            int ping = PingUtil.getPing(p);
            if (ping > 0 && ping > plugin.cfg().maxPing()) return;
        }

        long now = System.currentTimeMillis();

        if (!SetbackUtil.executeSetback(plugin, p, data, debug)) return;

        String aliasedDebug = aliasAwareDetail(debug);
        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(p.getUniqueId(), publicName(), "setback", aliasedDebug);
        }
        verbose(p, "&6[SETBACK] &7" + aliasedDebug);
        LAST_SETBACK_MS.put(p.getUniqueId(), now);
    }

    protected boolean predictionSetback(Player p, PlayerData data, String reason) {
        // ENGINE AUTHORITY: route setbacks through the central MovementEnforcement (engine-aware:
        // resyncs the prediction engine, zeroes offset advantage, respects the circuit breaker)
        // rather than the legacy PredictionProcessor path. Falls back to legacy only as a
        // kill-switch when the engine is disabled.
        if (plugin.engine() != null && plugin.engine().isEnabled()) {
            return com.colin.vezanticheat.utils.MovementEnforcement.executeSetback(plugin, p, data, reason);
        }
        if (plugin.prediction() != null) {
            return plugin.prediction().trySetback(p, data, reason);
        }
        return false;
    }

    protected boolean predictionSetback(Player p, PlayerData data, Location location, String reason) {
        if (plugin.engine() != null && plugin.engine().isEnabled()) {
            return com.colin.vezanticheat.utils.MovementEnforcement.executeSetback(plugin, p, data, reason);
        }
        if (plugin.prediction() != null) {
            return plugin.prediction().trySetback(p, data, reason);
        }
        return false;
    }

    /**
     * Resolve the lag-compensated reach context for the current attack. When the Grim-style combat
     * engine is enabled and has a packet-synced (tracked) position for the target, this returns the
     * transaction-rewound distances from {@link com.colin.vezanticheat.engine.CombatResult}; otherwise
     * it falls back to the legacy {@code CombatUtil.analyzeReach} over the target's position history,
     * so combat checks behave no worse than before while entity tracking warms up.
     */
    protected com.colin.vezanticheat.utils.CombatUtil.ReachContext resolveReachContext(
            PlayerData data, Location eye, org.bukkit.entity.Entity target, PlayerData targetData,
            long attackTime, long rewindMs) {
        if (plugin.getConfig().getBoolean("combat-engine.enabled", true)) {
            com.colin.vezanticheat.engine.CombatResult engine = data == null ? null : data.getLastCombatResult();
            if (engine != null && engine.tracked) {
                com.colin.vezanticheat.utils.CombatUtil.ReachContext ctx =
                        com.colin.vezanticheat.engine.CombatRewind.toReachContext(engine);
                if (ctx != null) return ctx;
            }
        }
        return com.colin.vezanticheat.utils.CombatUtil.analyzeReach(eye, target, targetData, attackTime, rewindMs);
    }

    protected void blockCurrentAttackPacket(Player p, PlayerData data, String detail) {
        if (p == null || data == null) return;
        data.setBlockCurrentAttackPacket(true);
        data.setBlockedAttackReason(detail);
        String aliasedDetail = aliasAwareDetail(detail);
        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(p.getUniqueId(), publicName(), "packet-drop", aliasedDetail);
        }
    }

    protected void fail(Player p, PlayerData data, double addVl, String debug) {
        if (p == null || data == null) return;

        // Debug logging for scaffold checks -- helps trace fail() suppression
        boolean scaffoldDebug = isScaffoldCategory(category)
                && plugin.cfg().checkBoolean(name, "debug", false);

        if (!plugin.cfg().enabled()) {
            if (scaffoldDebug) plugin.getLogger().info("[" + name + "] fail() BLOCKED -> anticheat disabled");
            return;
        }
        if (!enabled()) {
            if (scaffoldDebug) plugin.getLogger().info("[" + name + "] fail() BLOCKED -> check disabled");
            return;
        }
        if (PlayerData.bypass(p)) {
            if (scaffoldDebug) plugin.getLogger().info("[" + name + "] fail() BLOCKED -> bypass");
            return;
        }

        // Lag gates
        if (plugin.cfg().gateByLag()) {
            double tps = plugin.tps() != null ? plugin.tps().getTps() : 20.0;
            if (tps < plugin.cfg().minTps()) {
                if (scaffoldDebug) plugin.getLogger().info("[" + name + "] fail() BLOCKED -> low TPS " + tps);
                return;
            }

            int ping = PingUtil.getPing(p);
            if (ping > 0 && ping > plugin.cfg().maxPing()) {
                if (scaffoldDebug) plugin.getLogger().info("[" + name + "] fail() BLOCKED -> high ping " + ping);
                return;
            }
        }

        // Exempt windows
        if (data.isTeleportExempt()) {
            if (scaffoldDebug) plugin.getLogger().info("[" + name + "] fail() BLOCKED -> teleport exempt");
            return;
        }
        // BlockState exemption is for movement checks (Speed/Fly) around block placement/break.
        // SCAFFOLD checks fire ON block place, FASTBREAK checks fire ON block break,
        // so they must NOT be exempted here.
        if (data.isBlockStateExempt() && !isScaffoldCategory(category) && !isFastBreakCategory(category) && !isNukerCategory(category)) return;

        double weight = plugin.cfg().checkVlWeight(name);
        long now = System.currentTimeMillis();
        double add = Math.max(0.25, addVl * weight);
        double lagScore = LagProfileUtil.activeScore(plugin, p, data, now);
        if (lagScore >= plugin.getConfig().getDouble("lag.profile.boost-start-score", 1.25D)) {
            double boostFactor = plugin.getConfig().getDouble("lag.profile.boost-factor", 0.18D);
            double maxBoost = plugin.getConfig().getDouble("lag.profile.max-boost", 0.75D);
            double lagBoost = Math.min(maxBoost, lagScore * boostFactor);
            add *= (1.0D + lagBoost);
            debug = LagProfileUtil.appendDebug(debug, plugin, p, data, now);
        }
        debug = gateClassicSimulationDebug(p, data, debug);
        if (debug == null) {
            return;
        }
        debug = aliasAwareDetail(debug);
        if (shadowEnabled()) {
            shadowRecord(p, "shadow-flag", debug);
            recordAiEvidence(p, data, add, debug, true);
            return;
        }
        if (shouldSuppressSharedKillAuraFlag(p, data, now, debug)) {
            return;
        }
        if (shouldSuppressSharedClassicSimulationFlag(p, data, now, debug)) {
            return;
        }

        String vlKey = violationVlKey();
        double checkVl = data.addCheckVl(vlKey, add);
        data.addTotalVl((int) Math.max(1, Math.round(add)));
        recordAiEvidence(p, data, checkVl, debug, false);
        String displayName = publicName();

        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(p.getUniqueId(), displayName, "flag", debug);
        }

        plugin.tierChecks().flagToStaff(p.getName(), displayName, (int) Math.round(checkVl));
        verboseFlag(p, Math.round(checkVl), debug);
        if (shouldAutoSimulationSetback()) {
            predictionSetback(p, data, "simulation");
        }

        // mark last flag time (so VL doesn't instantly decay in downtime)
        String key = makeKey(data, vlKey);
        LAST_FLAG_MS.put(key, now);
        LAST_DECAY_MS.put(key, now);

        if (plugin.cfg().punishEnabled()) {
            boolean queued = plugin.punish().handleViolation(p, data, violationPunishCheckName(), violationPunishCategory(), checkVl);
            if (queued) {
                int banVl = plugin.cfg().banVl();
                data.reduceCheckVl(vlKey, banVl * 0.5);
                data.reduceTotalVl(banVl / 2);
            }
        }
    }

    private void recordAiEvidence(Player p, PlayerData data, double checkVl, String debug, boolean shadow) {
        if (plugin.riskScore() == null) return;
        plugin.riskScore().recordFlag(p, data, name, category, checkVl, debug, shadow);
    }

    protected final String aliasAwareDetail(String detail) {
        String trimmed = detail == null ? "" : detail.trim();
        if (name.equals(publicName())) {
            return trimmed;
        }
        if (trimmed.startsWith("src=")) {
            return trimmed;
        }
        if (trimmed.isEmpty()) {
            return "src=" + name;
        }
        return "src=" + name + " " + trimmed;
    }

    private String gateClassicSimulationDebug(Player p, PlayerData data, String detail) {
        if (p == null || !usesSharedClassicSimulationEventBuffer()) {
            return detail;
        }

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long windowMs = plugin.getConfig().getLong("prediction.simulation.classic-buffer-window-ms", 1600L);
        int maxBuffer = plugin.getConfig().getInt("prediction.simulation.classic-max-buffer", 6);
        int bufferToFlag = Math.max(1, plugin.getConfig().getInt("prediction.simulation.classic-buffer-to-flag", 2));
        int soloBufferToFlag = Math.max(bufferToFlag,
                plugin.getConfig().getInt("prediction.simulation.classic-solo-buffer-to-flag", 4));
        int minFamiliesToFlag = Math.max(1,
                plugin.getConfig().getInt("prediction.simulation.classic-min-families-to-flag", 2));
        int setbackBuffer = Math.max(1,
                plugin.getConfig().getInt("prediction.simulation.classic-setback-buffer", 2));
        int setbackSoloBuffer = Math.max(setbackBuffer,
                plugin.getConfig().getInt("prediction.simulation.classic-setback-solo-buffer", 3));
        int setbackMinFamilies = Math.max(1,
                plugin.getConfig().getInt("prediction.simulation.classic-setback-min-families", 2));
        long familyCooldownMs = plugin.getConfig().getLong("prediction.simulation.classic-family-cooldown-ms", 350L);
        int gain = Math.max(1, plugin.cfg().checkInt(name(), "simulationBufferGain",
                plugin.getConfig().getInt("prediction.simulation.classic-default-gain", 1)));
        int familyMaxContribution = Math.max(1, plugin.cfg().checkInt(name(), "simulationFamilyMaxContribution",
                plugin.getConfig().getInt("prediction.simulation.classic-family-max-contribution", soloBufferToFlag)));
        String family = classicSimulationFamily();

        Long last = CLASSIC_SIM_LAST_MS.get(id);
        boolean withinWindow = last != null && (now - last.longValue()) <= windowMs;
        if (!withinWindow) {
            if (hasClassicSimulationState(id)) {
                recordClassicSimulationState(id, "expired " + describeClassicSimulationState(id, now));
            }
            clearClassicSimulationState(id);
        }

        ConcurrentHashMap<String, Long> familyTimes = CLASSIC_SIM_FAMILY_MS.computeIfAbsent(id, key -> new ConcurrentHashMap<String, Long>());
        ConcurrentHashMap<String, Integer> familyScores = CLASSIC_SIM_FAMILY_SCORE.computeIfAbsent(id, key -> new ConcurrentHashMap<String, Integer>());
        ConcurrentHashMap<String, Long> familyDecayTimes = CLASSIC_SIM_FAMILY_DECAY_MS.computeIfAbsent(id, key -> new ConcurrentHashMap<String, Long>());
        if (withinWindow) {
            applyClassicSimulationFamilyDecay(id, now, familyScores, familyTimes, familyDecayTimes);
        }
        int previous = sumValues(familyScores);
        if (previous > 0) {
            CLASSIC_SIM_BUFFER.put(id, previous);
        } else {
            CLASSIC_SIM_BUFFER.remove(id);
        }
        Long lastFamily = familyTimes.get(family);
        boolean familyCooldown = lastFamily != null && (now - lastFamily.longValue()) < familyCooldownMs;
        int familyContribution = getInt(familyScores, family);
        int remainingFamilyContribution = Math.max(0, familyMaxContribution - familyContribution);
        int appliedGain = 0;
        int buffer = previous;
        if (!familyCooldown) {
            appliedGain = Math.min(gain, remainingFamilyContribution);
            if (appliedGain > 0) {
                int previousFamilyContribution = familyContribution;
                familyContribution += appliedGain;
                familyScores.put(family, familyContribution);
                familyDecayTimes.put(family, now);
                buffer = Math.min(maxBuffer, previous + appliedGain);
                CLASSIC_SIM_BUFFER.put(id, buffer);
                CLASSIC_SIM_LAST_MS.put(id, now);
                familyTimes.put(family, now);
                if (previousFamilyContribution < familyMaxContribution && familyContribution >= familyMaxContribution) {
                    recordClassicSimulationState(id,
                            "family-cap family=" + family
                                    + " score=" + familyContribution + "/" + familyMaxContribution
                                    + " total=" + buffer
                                    + " families=" + familyTimes.size());
                }
            }
        }

        int familyCount = familyTimes.size();
        SimulationSubCheck.PredictionSimulationSnapshot predictionSnapshot =
                SimulationSubCheck.predictionSimulationSnapshot(id, now);
        int rawPredictionSupport = predictionSnapshot == null ? 0 : predictionSnapshot.getSourceScores().size();
        int predictionSupport = Math.min(rawPredictionSupport, Math.max(0,
                plugin.getConfig().getInt("prediction.simulation.cross-engine-prediction-support-cap", 1)));
        int effectiveFamilyCount = familyCount + predictionSupport;
        boolean corroborated = effectiveFamilyCount >= minFamiliesToFlag;
        boolean soloSevere = buffer >= soloBufferToFlag;
        boolean setbackCorroborated = effectiveFamilyCount >= setbackMinFamilies;
        boolean setbackSolo = buffer >= setbackSoloBuffer;
        String gatedDetail = "simBuf=" + buffer + "/" + bufferToFlag
                + " gain=" + gain
                + " applied=" + appliedGain
                + " family=" + family
                + " families=" + familyCount
                + " effFamilies=" + effectiveFamilyCount
                + " predSources=" + rawPredictionSupport
                + " predSupport=" + predictionSupport
                + " familyScore=" + familyContribution + "/" + familyMaxContribution
                + " familyCooldown=" + familyCooldown
                + " " + aliasAwareDetail(detail);
        boolean aggregateSetbackReady = data != null && !shadowEnabled()
                && buffer >= setbackBuffer
                && (setbackCorroborated || setbackSolo);
        if (aggregateSetbackReady) {
            if (predictionSetback(p, data, "simulation")) {
                CLASSIC_SIM_LAST_SETBACK_MS.put(id, now);
                recordClassicSimulationState(id, "aggregate-setback " + gatedDetail);
            }
        }
        if (plugin.diagnostics() != null
                && previous < Math.max(1, bufferToFlag - 1)
                && buffer >= Math.max(1, bufferToFlag - 1)
                && buffer < bufferToFlag) {
            plugin.diagnostics().record(p.getUniqueId(), publicName(), "candidate", gatedDetail);
        }

        if (buffer < bufferToFlag || (!corroborated && !soloSevere)) {
            return null;
        }

        clearClassicSimulationState(id);
        return gatedDetail;
    }

    private boolean shouldAutoSimulationSetback() {
        if (!"SIMULATION".equals(publicCategory())) return false;
        if (name.endsWith("Prediction")) return false;
        return plugin.cfg().checkBoolean(name, "setbackEnabled", true);
    }

    protected void decay(PlayerData data, double amount) {
        if (data == null) return;
        if (sharedKillAuraAggregateOwnsPublicFlags()) return;

        long now = System.currentTimeMillis();
        String vlKey = violationVlKey();
        String key = makeKey(data, vlKey);

        long graceMs = plugin.getConfig().getLong("vl.decay-grace-ms", 15000L);
        double basePerSecond = plugin.getConfig().getDouble("vl.decay-per-second", 0.12D);
        double globalPerSecond = plugin.getConfig().getDouble("vl.global-decay-per-second", 0.03D);
        long minIntervalMs = plugin.getConfig().getLong("vl.decay-min-interval-ms", 250L);

        Long lastFlag = LAST_FLAG_MS.get(key);
        if (lastFlag != null) {
            long sinceFlag = now - lastFlag.longValue();
            if (sinceFlag >= 0L && sinceFlag < graceMs) {
                return;
            }
        }

        Long lastDecay = LAST_DECAY_MS.get(key);
        if (lastDecay == null) {
            LAST_DECAY_MS.put(key, now);
            return;
        }

        long dtMs = now - lastDecay.longValue();
        if (dtMs < minIntervalMs) return;

        double seconds = dtMs / 1000.0D;
        double mult = clamp(amount, 0.15D, 2.5D);

        double decCheck = Math.max(0.01D, basePerSecond * seconds * mult);
        double decGlobal = Math.max(0.00D, globalPerSecond * seconds * mult);

        data.reduceCheckVl(vlKey, decCheck);

        int decGlobalInt = (int) Math.floor(decGlobal);
        if (decGlobalInt > 0) {
            data.reduceTotalVl(decGlobalInt);
        }

        LAST_DECAY_MS.put(key, now);

        if (data.getCheckVl(vlKey) <= 0.0D) {
            LAST_FLAG_MS.remove(key);
            LAST_DECAY_MS.remove(key);
        }
    }

    private String violationVlKey() {
        return usesSharedPublicVl() ? publicName() : name;
    }

    private String violationPunishCheckName() {
        return usesSharedPublicVl() ? publicName() : name;
    }

    private String violationPunishCategory() {
        return usesSharedPublicVl() ? publicCategory() : category;
    }

    private boolean usesSharedPublicVl() {
        return usesSharedSimulationVl() || usesSharedKillAuraVl() || usesSharedTimerVl();
    }

    private boolean usesSharedSimulationVl() {
        return usesSharedClassicSimulationVl() || usesSharedPredictionSimulationVl();
    }

    private boolean usesSharedClassicSimulationVl() {
        return plugin.getConfig().getBoolean("prediction.simulation.share-classic-vl", true)
                && "SIMULATION".equals(publicCategory())
                && !name.endsWith("Prediction");
    }

    private boolean usesSharedPredictionSimulationVl() {
        return plugin.getConfig().getBoolean("prediction.simulation.share-prediction-vl", true)
                && "SIMULATION".equals(publicCategory())
                && name.endsWith("Prediction");
    }

    private boolean usesSharedKillAuraVl() {
        return plugin.getConfig().getBoolean("combat.killaura.share-vl", true)
                && "KILLAURA".equals(publicCategory())
                && "KillAura".equals(publicName());
    }

    private boolean usesSharedTimerVl() {
        return plugin.getConfig().getBoolean("timer.share-vl", true)
                && "TIMER".equals(publicCategory())
                && "Timer".equals(publicName());
    }

    protected boolean sharedKillAuraAggregateOwnsPublicFlags() {
        return usesSharedKillAuraVl()
                && plugin.getConfig().getBoolean("combat.killaura.aggregate-own-public-flags", false)
                && !"KillAuraH".equals(name);
    }

    private boolean shouldSuppressSharedKillAuraFlag(Player p, PlayerData data, long now, String debug) {
        if (!usesSharedKillAuraVl() || p == null || data == null) return false;

        long rearmMs = Math.max(0L, plugin.getConfig().getLong("combat.killaura.public-flag-rearm-ms", 175L));
        if (rearmMs <= 0L) return false;

        String key = makeKey(data, violationVlKey());
        Long last = LAST_FLAG_MS.get(key);
        if (last == null) return false;

        long elapsed = now - last.longValue();
        if (elapsed < 0L || elapsed >= rearmMs) return false;

        long rearmLeft = rearmMs - elapsed;
        long stateGapMs = Math.max(0L, plugin.getConfig().getLong("combat.killaura.public-suppression-state-min-gap-ms", 125L));
        if (shouldRecordSharedKillAuraSuppression(p.getUniqueId(), now, stateGapMs)) {
            if (plugin.diagnostics() != null) {
                plugin.diagnostics().record(p.getUniqueId(), publicName(), "state",
                        "flag-suppressed reason=rearm rearmLeft=" + rearmLeft + "ms " + debug);
            }
        }
        return true;
    }

    private boolean shouldRecordSharedKillAuraSuppression(UUID id, long now, long minGapMs) {
        if (id == null) return false;
        Long last = SHARED_KILLAURA_LAST_SUPPRESS_MS.get(id);
        if (last != null && (now - last.longValue()) < Math.max(0L, minGapMs)) {
            return false;
        }
        SHARED_KILLAURA_LAST_SUPPRESS_MS.put(id, now);
        return true;
    }

    private boolean usesSharedClassicSimulationEventBuffer() {
        return plugin.getConfig().getBoolean("prediction.simulation.share-classic-buffer", true)
                && usesSharedClassicSimulationVl();
    }

    private boolean shouldSuppressSharedClassicSimulationFlag(Player p, PlayerData data, long now, String debug) {
        if (!usesSharedSimulationVl() || p == null || data == null) return false;

        long rearmMs = Math.max(0L,
                plugin.getConfig().getLong("prediction.simulation.public-flag-rearm-ms",
                        plugin.getConfig().getLong("prediction.simulation.classic-public-flag-rearm-ms", 125L)));
        if (rearmMs <= 0L) return false;

        String key = makeKey(data, violationVlKey());
        Long last = LAST_FLAG_MS.get(key);
        if (last == null) return false;

        long elapsed = now - last.longValue();
        if (elapsed < 0L || elapsed >= rearmMs) return false;

        long rearmLeft = rearmMs - elapsed;
        long stateGapMs = Math.max(0L,
                plugin.getConfig().getLong("prediction.simulation.public-flag-suppression-state-min-gap-ms",
                        plugin.getConfig().getLong("prediction.simulation.classic-public-flag-suppression-state-min-gap-ms", 90L)));
        if (shouldRecordClassicSimulationSuppression(CLASSIC_SIM_LAST_FLAG_SUPPRESS_MS, p.getUniqueId(), now, stateGapMs)) {
            recordClassicSimulationState(p.getUniqueId(),
                    "flag-suppressed reason=rearm rearmLeft=" + rearmLeft + "ms " + debug);
        }
        return true;
    }

    private String classicSimulationFamily() {
        if (category == null) return "SIMULATION";
        String normalized = category.trim().toUpperCase();
        return normalized.isEmpty() ? "SIMULATION" : normalized;
    }

    private String makeKey(PlayerData data, String vlKey) {
        return data.getUuid().toString() + ":" + vlKey;
    }

    private void clearClassicSimulationState(UUID id) {
        CLASSIC_SIM_BUFFER.remove(id);
        CLASSIC_SIM_LAST_MS.remove(id);
        CLASSIC_SIM_FAMILY_MS.remove(id);
        CLASSIC_SIM_FAMILY_SCORE.remove(id);
        CLASSIC_SIM_FAMILY_DECAY_MS.remove(id);
    }

    private boolean hadRecentClassicSimulationAggregateSetback(Player p, long now) {
        return hadRecentClassicSimulationAggregateSetback(p, now, classicSimulationSetbackDedupeMs());
    }

    private boolean hadRecentClassicSimulationAggregateSetback(Player p, long now, long windowMs) {
        if (p == null) return false;
        Long last = CLASSIC_SIM_LAST_SETBACK_MS.get(p.getUniqueId());
        return last != null && (now - last.longValue()) <= Math.max(0L, windowMs);
    }

    private long classicSimulationSetbackDedupeMs() {
        return Math.max(0L,
                plugin.getConfig().getLong("prediction.simulation.classic-setback-dedupe-ms", 100L));
    }

    private long classicSimulationSetbackRemainingMs(Player p, long now, long windowMs) {
        if (p == null) return 0L;
        Long last = CLASSIC_SIM_LAST_SETBACK_MS.get(p.getUniqueId());
        if (last == null) return 0L;
        return Math.max(0L, Math.max(0L, windowMs) - (now - last.longValue()));
    }

    private boolean shouldRecordClassicSimulationSuppression(ConcurrentHashMap<UUID, Long> tracker, UUID id, long now, long minGapMs) {
        if (tracker == null || id == null) return false;
        Long last = tracker.get(id);
        if (last != null && (now - last.longValue()) < Math.max(0L, minGapMs)) {
            return false;
        }
        tracker.put(id, now);
        return true;
    }

    private boolean hasClassicSimulationState(UUID id) {
        if (id == null) return false;
        Integer total = CLASSIC_SIM_BUFFER.get(id);
        if (total != null && total.intValue() > 0) return true;
        ConcurrentHashMap<String, Integer> scores = CLASSIC_SIM_FAMILY_SCORE.get(id);
        return scores != null && !scores.isEmpty();
    }

    private int getInt(ConcurrentHashMap<UUID, Integer> map, UUID id) {
        Integer value = map.get(id);
        return value == null ? 0 : value.intValue();
    }

    private int getInt(ConcurrentHashMap<String, Integer> map, String key) {
        Integer value = map.get(key);
        return value == null ? 0 : value.intValue();
    }

    private void applyClassicSimulationFamilyDecay(UUID id, long now,
                                                   ConcurrentHashMap<String, Integer> familyScores,
                                                   ConcurrentHashMap<String, Long> familyTimes,
                                                   ConcurrentHashMap<String, Long> familyDecayTimes) {
        if (id == null || familyScores.isEmpty()) return;

        long decayIntervalMs = Math.max(1L,
                plugin.getConfig().getLong("prediction.simulation.classic-family-decay-interval-ms", 700L));
        int decayAmount = Math.max(1,
                plugin.getConfig().getInt("prediction.simulation.classic-family-decay-amount", 1));

        for (String family : new java.util.ArrayList<String>(familyScores.keySet())) {
            if (family == null) continue;

            int score = getInt(familyScores, family);
            if (score <= 0) {
                familyScores.remove(family);
                familyTimes.remove(family);
                familyDecayTimes.remove(family);
                continue;
            }

            long decayAnchor = familyDecayTimes.containsKey(family)
                    ? familyDecayTimes.get(family).longValue()
                    : now;
            long elapsed = Math.max(0L, now - decayAnchor);
            long steps = elapsed / decayIntervalMs;
            if (steps <= 0L) {
                continue;
            }

            int nextScore = score - (int) Math.min(Integer.MAX_VALUE, steps * decayAmount);
            if (nextScore > 0) {
                familyScores.put(family, nextScore);
                familyDecayTimes.put(family, decayAnchor + (steps * decayIntervalMs));
            } else {
                int nextTotal = Math.max(0, sumValues(familyScores) - score);
                recordClassicSimulationState(id,
                        "family-decay-out family=" + family
                                + " score=" + score + "->0"
                                + " total=" + nextTotal);
                familyScores.remove(family);
                familyTimes.remove(family);
                familyDecayTimes.remove(family);
            }
        }

        if (familyScores.isEmpty()) {
            clearClassicSimulationState(id);
        }
    }

    private int sumValues(ConcurrentHashMap<String, Integer> map) {
        int total = 0;
        for (Integer value : map.values()) {
            if (value != null && value.intValue() > 0) {
                total += value.intValue();
            }
        }
        return total;
    }

    private void recordClassicSimulationState(UUID id, String detail) {
        if (id == null || plugin.diagnostics() == null) return;
        plugin.diagnostics().record(id, "Simulation", "state", detail);
    }

    private String describeClassicSimulationState(UUID id, long now) {
        ClassicSimulationSnapshot snapshot = classicSimulationSnapshot(id);
        if (snapshot == null) {
            return "buf=0";
        }
        return snapshot.describe(now);
    }

    public static final class ClassicSimulationSnapshot {
        private final int totalBuffer;
        private final long lastUpdateMs;
        private final long lastAggregateSetbackMs;
        private final Map<String, Integer> familyScores;
        private final Map<String, Long> familyLastContributionMs;

        private ClassicSimulationSnapshot(int totalBuffer, long lastUpdateMs, long lastAggregateSetbackMs,
                                          Map<String, Integer> familyScores,
                                          Map<String, Long> familyLastContributionMs) {
            this.totalBuffer = totalBuffer;
            this.lastUpdateMs = lastUpdateMs;
            this.lastAggregateSetbackMs = lastAggregateSetbackMs;
            this.familyScores = familyScores;
            this.familyLastContributionMs = familyLastContributionMs;
        }

        public int getTotalBuffer() { return totalBuffer; }
        public long getLastUpdateMs() { return lastUpdateMs; }
        public long getLastAggregateSetbackMs() { return lastAggregateSetbackMs; }
        public Map<String, Integer> getFamilyScores() { return familyScores; }

        public String describe(long nowMs) {
            return describe(nowMs, 0L, 0L);
        }

        public String describe(long nowMs, long setbackRearmMs, long setbackDedupeMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("buf=").append(totalBuffer);
            if (lastUpdateMs > 0L && nowMs >= lastUpdateMs) {
                sb.append(" age=").append(nowMs - lastUpdateMs).append("ms");
            }
            if (lastAggregateSetbackMs > 0L && nowMs >= lastAggregateSetbackMs) {
                long setbackAge = nowMs - lastAggregateSetbackMs;
                sb.append(" setbackAge=").append(setbackAge).append("ms");
                if (setbackRearmMs > 0L) {
                    sb.append(" rearmLeft=").append(Math.max(0L, setbackRearmMs - setbackAge)).append("ms");
                }
                if (setbackDedupeMs > 0L) {
                    sb.append(" dedupeLeft=").append(Math.max(0L, setbackDedupeMs - setbackAge)).append("ms");
                }
            }
            if (!familyScores.isEmpty()) {
                sb.append(" families=");
                boolean first = true;
                for (Map.Entry<String, Integer> entry : familyScores.entrySet()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append(entry.getKey()).append(":").append(entry.getValue());
                    Long familyMs = familyLastContributionMs.get(entry.getKey());
                    if (familyMs != null && nowMs >= familyMs.longValue()) {
                        sb.append("@").append(nowMs - familyMs.longValue()).append("ms");
                    }
                }
            }
            return sb.toString();
        }
    }

    public static final class SimulationAggregateSnapshot {
        private final ClassicSimulationSnapshot classic;
        private final SimulationSubCheck.PredictionSimulationSnapshot prediction;

        private SimulationAggregateSnapshot(ClassicSimulationSnapshot classic,
                                            SimulationSubCheck.PredictionSimulationSnapshot prediction) {
            this.classic = classic;
            this.prediction = prediction;
        }

        public ClassicSimulationSnapshot getClassic() { return classic; }
        public SimulationSubCheck.PredictionSimulationSnapshot getPrediction() { return prediction; }

        public int classicFamilyCount() {
            return classic == null ? 0 : classic.getFamilyScores().size();
        }

        public int predictionSourceCount() {
            return prediction == null ? 0 : prediction.getSourceScores().size();
        }

        public int engineCount() {
            int engines = 0;
            if (classicFamilyCount() > 0) engines++;
            if (predictionSourceCount() > 0) engines++;
            return engines;
        }

        public String describe(long nowMs, long setbackRearmMs, long setbackDedupeMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("engines=").append(engineCount());
            sb.append(" classicFamilies=").append(classicFamilyCount());
            sb.append(" predSources=").append(predictionSourceCount());
            if (classic != null) {
                sb.append(" classic{").append(classic.describe(nowMs, setbackRearmMs, setbackDedupeMs)).append("}");
            }
            if (prediction != null) {
                sb.append(" pred{").append(prediction.describe(nowMs)).append("}");
            }
            return sb.toString();
        }
    }

    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private boolean isSpeedCategory(String c) {
        if (c == null) return false;
        String x = c.toUpperCase();
        return x.equals("SPEED") || x.equals("STEP");
    }

    private boolean isScaffoldCategory(String c) {
        return c != null && c.equalsIgnoreCase("SCAFFOLD");
    }

    private boolean isFastBreakCategory(String c) {
        return c != null && c.equalsIgnoreCase("FASTBREAK");
    }

    private boolean isNukerCategory(String c) {
        return c != null && c.equalsIgnoreCase("NUKER");
    }

    // hooks
    public void onMove(Player p, PlayerData data) {}
    public void onAttack(Player p, PlayerData data) {}
    public void onVelocity(Player p, PlayerData data) {}
    public void onArmSwing(Player p, PlayerData data) {}
    public void onBlockPlace(Player p, PlayerData data) {}
    public void onBowShoot(Player p, PlayerData data, long pullMs) {}
    public void onConsume(Player p, PlayerData data, long useMs) {}
    public void onInventoryAction(Player p, PlayerData data) {}
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {}
    public void onBlockBreak(Player p, PlayerData data, org.bukkit.block.Block block) {}
    public void onDigStart(Player p, PlayerData data, org.bukkit.block.Block block) {}
    public void onDigging(Player p, PlayerData data, com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType action,
                          org.bukkit.block.Block block) {}
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId,
                                   float cursorX, float cursorY, float cursorZ) {}
    public void onHeldItemChange(Player p, PlayerData data, int slot) {}
    public void onEntityAction(Player p, PlayerData data, String actionName) {}
    public void onUseItem(Player p, PlayerData data) {}
    public void onWindowClick(Player p, PlayerData data, int windowId, int slot) {}
    public void onWindowConfirmation(Player p, PlayerData data, short actionId, long nowMs) {}
    public void onCloseInventory(Player p, PlayerData data) {}
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack,
                                 org.bukkit.entity.Entity target) {}
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {}

    protected void console(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}
