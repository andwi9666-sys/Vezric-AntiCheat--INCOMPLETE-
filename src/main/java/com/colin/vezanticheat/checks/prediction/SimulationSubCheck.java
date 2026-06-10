package com.colin.vezanticheat.checks.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.checks.Check;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared verdict path for prediction-backed movement checks that should surface as Simulation.
 */
public abstract class SimulationSubCheck extends Check {

    private static final Map<UUID, Integer> SHARED_BUFFER = new ConcurrentHashMap<UUID, Integer>();
    private static final Map<UUID, Long> SHARED_LAST_MS = new ConcurrentHashMap<UUID, Long>();
    private static final Map<UUID, ConcurrentHashMap<String, Integer>> SHARED_SOURCE_SCORES = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>>();
    private static final Map<UUID, ConcurrentHashMap<String, Long>> SHARED_SOURCE_LAST_MS = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>();

    protected SimulationSubCheck(VezAntiCheat plugin, String name, String category) {
        super(plugin, name, category);
    }

    /** True if the Grim-style movement engine is active and should drive this check. */
    protected boolean engineActive() {
        return plugin.engine() != null && plugin.engine().isEnabled();
    }

    /**
     * Returns the latest checked engine result for this player, or null when the engine is
     * disabled, the tick was exempt, or no result exists yet. Sub-checks read the decomposed
     * offset signals from this instead of running their own heuristics.
     */
    protected com.colin.vezanticheat.engine.EngineResult engineResult(PlayerData data) {
        if (!engineActive() || data == null) return null;
        com.colin.vezanticheat.engine.EngineResult result = data.getLastEngineResult();
        if (result == null || !result.checked) return null;
        return result;
    }

    protected void coolSimulation(Player p, String source) {
        if (p == null) return;
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        String normalizedSource = normalizeSource(source);
        ConcurrentHashMap<String, Integer> sourceScores = SHARED_SOURCE_SCORES.get(id);
        ConcurrentHashMap<String, Long> sourceTimes = SHARED_SOURCE_LAST_MS.get(id);
        if (sourceScores == null || sourceScores.isEmpty()) {
            SHARED_BUFFER.remove(id);
            SHARED_LAST_MS.remove(id);
            return;
        }

        int score = getSourceScore(sourceScores, normalizedSource);
        if (score <= 1) {
            sourceScores.remove(normalizedSource);
            if (sourceTimes != null) {
                sourceTimes.remove(normalizedSource);
            }
        } else {
            sourceScores.put(normalizedSource, score - 1);
            if (sourceTimes != null) {
                sourceTimes.put(normalizedSource, now);
            }
        }

        int next = sumValues(sourceScores);
        if (next <= 0) {
            clearSimulationState(id);
        } else {
            SHARED_BUFFER.put(id, next);
            SHARED_LAST_MS.put(id, now);
        }
    }

    protected void clearSimulation(Player p) {
        if (p == null) return;
        clearSimulationState(p.getUniqueId());
    }

    protected boolean handleSimulationViolation(Player p, PlayerData data, String source, int gain,
                                                int bufferToFlag, double failVl, String detail,
                                                boolean setback, String setbackReason) {
        return handleSimulationViolation(p, data, source, gain, bufferToFlag, failVl, detail,
                setback, null, setbackReason);
    }

    protected boolean handleSimulationViolation(Player p, PlayerData data, String source, int gain,
                                                int bufferToFlag, double failVl, String detail,
                                                boolean setback, Location setbackLocation, String setbackReason) {
        if (p == null || data == null) return false;

        UUID id = p.getUniqueId();
        String normalizedSource = normalizeSource(source);
        int previous = getSimulationBuffer(id);
        int sharedMaxBuffer = plugin.getConfig().getInt("prediction.simulation.shared-max-buffer", 12);
        int sourceMaxContribution = Math.max(1, plugin.cfg().checkInt(source, "sourceMaxContribution",
                plugin.getConfig().getInt("prediction.simulation.prediction-source-max-contribution",
                        Math.max(bufferToFlag, 3))));
        int minSourcesToFlag = Math.max(1, plugin.cfg().checkInt(source, "minSourcesToFlag",
                plugin.getConfig().getInt("prediction.simulation.prediction-min-sources-to-flag", 2)));
        int soloBufferToFlag = Math.max(bufferToFlag, plugin.cfg().checkInt(source, "soloBufferToFlag",
                plugin.getConfig().getInt("prediction.simulation.prediction-solo-buffer-to-flag", bufferToFlag + 1)));
        ConcurrentHashMap<String, Integer> sourceScores =
                SHARED_SOURCE_SCORES.computeIfAbsent(id, key -> new ConcurrentHashMap<String, Integer>());
        ConcurrentHashMap<String, Long> sourceTimes =
                SHARED_SOURCE_LAST_MS.computeIfAbsent(id, key -> new ConcurrentHashMap<String, Long>());
        int previousSourceScore = getSourceScore(sourceScores, normalizedSource);
        int appliedGain = Math.min(Math.max(1, gain), Math.max(0, sourceMaxContribution - previousSourceScore));
        if (appliedGain > 0) {
            sourceScores.put(normalizedSource, previousSourceScore + appliedGain);
            sourceTimes.put(normalizedSource, System.currentTimeMillis());
        }
        int sourceScore = getSourceScore(sourceScores, normalizedSource);
        int sourceCount = countSources(sourceScores);
        Check.ClassicSimulationSnapshot classicSnapshot = Check.classicSimulationSnapshot(id, System.currentTimeMillis(), 0L);
        int rawClassicSupport = classicSnapshot == null ? 0 : classicSnapshot.getFamilyScores().size();
        int classicSupport = Math.min(rawClassicSupport, Math.max(0,
                plugin.getConfig().getInt("prediction.simulation.cross-engine-classic-support-cap", 1)));
        int effectiveSourceCount = sourceCount + classicSupport;
        int buffer = Math.min(sharedMaxBuffer, sumValues(sourceScores));
        SHARED_BUFFER.put(id, buffer);
        SHARED_LAST_MS.put(id, System.currentTimeMillis());

        String debug = "source=" + source
                + " buf=" + buffer + "/" + bufferToFlag
                + " sources=" + sourceCount
                + " effSources=" + effectiveSourceCount
                + " classicFamilies=" + rawClassicSupport
                + " classicSupport=" + classicSupport
                + " sourceScore=" + sourceScore + "/" + sourceMaxContribution
                + " gain=" + gain
                + " applied=" + appliedGain
                + " " + detail;
        if (plugin.diagnostics() != null
                && previous < Math.max(1, bufferToFlag - 1)
                && buffer >= Math.max(1, bufferToFlag - 1)) {
            plugin.diagnostics().record(p.getUniqueId(), publicName(), "candidate", debug);
        }

        if (buffer < bufferToFlag || (effectiveSourceCount < minSourcesToFlag && buffer < soloBufferToFlag)) {
            return false;
        }

        fail(p, data, failVl, debug);
        if (setback) {
            if (setbackLocation != null) {
                predictionSetback(p, data, setbackLocation, setbackReason);
            } else {
                predictionSetback(p, data, setbackReason);
            }
        }
        clearSimulationState(id);
        return true;
    }

    public static PredictionSimulationSnapshot predictionSimulationSnapshot(UUID id) {
        return predictionSimulationSnapshot(id, System.currentTimeMillis());
    }

    public static PredictionSimulationSnapshot predictionSimulationSnapshot(UUID id, long nowMs) {
        if (id == null) return null;

        Integer total = SHARED_BUFFER.get(id);
        Long last = SHARED_LAST_MS.get(id);
        ConcurrentHashMap<String, Integer> sourceScores = SHARED_SOURCE_SCORES.get(id);
        ConcurrentHashMap<String, Long> sourceTimes = SHARED_SOURCE_LAST_MS.get(id);
        boolean hasTotal = total != null && total.intValue() > 0;
        boolean hasSources = sourceScores != null && !sourceScores.isEmpty();
        if (!hasTotal && !hasSources) {
            return null;
        }

        java.util.Map<String, Integer> orderedScores = new java.util.LinkedHashMap<String, Integer>();
        java.util.Map<String, Long> orderedTimes = new java.util.LinkedHashMap<String, Long>();
        java.util.List<String> sources = new java.util.ArrayList<String>();
        if (sourceScores != null) {
            sources.addAll(sourceScores.keySet());
        }
        java.util.Collections.sort(sources);
        for (String source : sources) {
            if (source == null) continue;
            int score = getStaticSourceScore(sourceScores, source);
            if (score <= 0) continue;
            orderedScores.put(source, score);
            if (sourceTimes != null && sourceTimes.containsKey(source)) {
                orderedTimes.put(source, sourceTimes.get(source));
            }
        }

        int buffer = hasTotal ? total.intValue() : sumStaticValues(sourceScores);
        if (buffer <= 0 && orderedScores.isEmpty()) {
            return null;
        }

        return new PredictionSimulationSnapshot(buffer,
                last == null ? 0L : last.longValue(),
                orderedScores,
                orderedTimes);
    }

    private int getSimulationBuffer(UUID id) {
        Integer value = SHARED_BUFFER.get(id);
        return value == null ? 0 : value.intValue();
    }

    private int getSourceScore(ConcurrentHashMap<String, Integer> sourceScores, String source) {
        if (sourceScores == null || source == null) return 0;
        Integer value = sourceScores.get(source);
        return value == null ? 0 : value.intValue();
    }

    private static int getStaticSourceScore(ConcurrentHashMap<String, Integer> sourceScores, String source) {
        if (sourceScores == null || source == null) return 0;
        Integer value = sourceScores.get(source);
        return value == null ? 0 : value.intValue();
    }

    private int countSources(ConcurrentHashMap<String, Integer> sourceScores) {
        if (sourceScores == null || sourceScores.isEmpty()) return 0;
        int total = 0;
        for (Integer value : sourceScores.values()) {
            if (value != null && value.intValue() > 0) {
                total++;
            }
        }
        return total;
    }

    private int sumValues(ConcurrentHashMap<String, Integer> sourceScores) {
        if (sourceScores == null || sourceScores.isEmpty()) return 0;
        int total = 0;
        for (Integer value : sourceScores.values()) {
            if (value != null && value.intValue() > 0) {
                total += value.intValue();
            }
        }
        return total;
    }

    private String normalizeSource(String source) {
        if (source == null) return "SIM";
        String normalized = source.trim().toUpperCase();
        return normalized.isEmpty() ? "SIM" : normalized;
    }

    private static int sumStaticValues(ConcurrentHashMap<String, Integer> sourceScores) {
        if (sourceScores == null || sourceScores.isEmpty()) return 0;
        int total = 0;
        for (Integer value : sourceScores.values()) {
            if (value != null && value.intValue() > 0) {
                total += value.intValue();
            }
        }
        return total;
    }

    private static void clearSimulationState(UUID id) {
        if (id == null) return;
        SHARED_BUFFER.remove(id);
        SHARED_LAST_MS.remove(id);
        SHARED_SOURCE_SCORES.remove(id);
        SHARED_SOURCE_LAST_MS.remove(id);
    }

    public static final class PredictionSimulationSnapshot {
        private final int buffer;
        private final long lastUpdateMs;
        private final java.util.Map<String, Integer> sourceScores;
        private final java.util.Map<String, Long> sourceLastUpdateMs;

        private PredictionSimulationSnapshot(int buffer, long lastUpdateMs,
                                             java.util.Map<String, Integer> sourceScores,
                                             java.util.Map<String, Long> sourceLastUpdateMs) {
            this.buffer = buffer;
            this.lastUpdateMs = lastUpdateMs;
            this.sourceScores = sourceScores;
            this.sourceLastUpdateMs = sourceLastUpdateMs;
        }

        public int getBuffer() { return buffer; }
        public long getLastUpdateMs() { return lastUpdateMs; }
        public java.util.Map<String, Integer> getSourceScores() { return sourceScores; }
        public java.util.Map<String, Long> getSourceLastUpdateMs() { return sourceLastUpdateMs; }

        public String describe(long nowMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("buf=").append(buffer);
            if (lastUpdateMs > 0L && nowMs >= lastUpdateMs) {
                sb.append(" age=").append(nowMs - lastUpdateMs).append("ms");
            }
            sb.append(" sources=");
            boolean first = true;
            for (java.util.Map.Entry<String, Integer> entry : sourceScores.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(entry.getKey()).append(":").append(entry.getValue());
                Long sourceMs = sourceLastUpdateMs.get(entry.getKey());
                if (sourceMs != null && nowMs >= sourceMs.longValue()) {
                    sb.append("@").append(nowMs - sourceMs.longValue()).append("ms");
                }
            }
            return sb.toString();
        }
    }
}
