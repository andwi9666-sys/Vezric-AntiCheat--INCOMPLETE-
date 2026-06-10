package com.colin.vezanticheat.ai;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.utils.ConfigManager;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Lightweight rolling risk score per player from check flags.
 */
public final class RiskScoreManager {

    private final VezAntiCheat plugin;
    private final EvidenceManager evidence;
    private final Map<UUID, RiskState> states = new ConcurrentHashMap<UUID, RiskState>();

    public RiskScoreManager(VezAntiCheat plugin, EvidenceManager evidence) {
        this.plugin = plugin;
        this.evidence = evidence;
    }

    public void recordFlag(Player player, PlayerData data, String checkName, String category,
                           double checkVl, String debug, boolean shadow) {
        if (player == null || data == null || !plugin.getConfig().getBoolean("ai.enabled", true)) return;

        ConfigManager.ConfidenceProfile profile = plugin.cfg().confidenceProfileFor(checkName, category);
        double weight = Math.min(profile.maxContribution, profile.baseScore);
        if (checkVl < profile.minCheckVl) {
            weight *= 0.5D;
        }

        long now = System.currentTimeMillis();
        evictStale(now);
        RiskState state = states.computeIfAbsent(player.getUniqueId(), key -> new RiskState());
        decayState(state, now);

        state.score += weight;
        state.lastUpdateMs = now;
        state.recentFlags++;

        double offset = 0.0D;
        EngineResult engine = data.getLastEngineResult();
        if (engine != null && engine.checked) {
            offset = engine.offset;
        }

        evidence.append(player, checkName, category, checkVl, PingUtil.getPing(player), offset, debug, now);

        if (shadow || plugin.getConfig().getBoolean("ai.shadow-mode", true)) {
            plugin.getLogger().log(Level.INFO, "[AI][shadow] " + player.getName()
                    + " score=" + r(state.score) + " +" + r(weight) + " check=" + checkName
                    + " cat=" + category + " " + debug);
            return;
        }

        double markThreshold = plugin.getConfig().getDouble("ai.mark-score-threshold",
                plugin.getConfig().getDouble("punish.evidence.mark-score", 8.0D));
        if (state.score >= markThreshold) {
            plugin.getLogger().info("[AI] elevated risk " + player.getName()
                    + " score=" + r(state.score) + " check=" + checkName);
        }
    }

    public double getScore(UUID uuid) {
        RiskState state = states.get(uuid);
        if (state == null) return 0.0D;
        long now = System.currentTimeMillis();
        decayState(state, now);
        // Decay-on-read: a state that has fully decayed and gone stale is dropped so the store
        // does not retain entries for players who stopped flagging long ago.
        if (RiskWindow.shouldEvict(state.score, state.lastUpdateMs, now, windowMs())) {
            states.remove(uuid, state);
            return 0.0D;
        }
        return state.score;
    }

    /** Number of tracked player states (for diagnostics/tests). */
    public int trackedStateCount() {
        return states.size();
    }

    /**
     * Sweep the whole store and drop any state that has decayed to zero and aged past the
     * retention window. Cheap to call frequently; bounded by the number of online-ish players.
     */
    public void evictStale(long now) {
        long window = windowMs();
        for (Iterator<Map.Entry<UUID, RiskState>> it = states.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, RiskState> e = it.next();
            RiskState state = e.getValue();
            if (state == null) {
                it.remove();
                continue;
            }
            decayState(state, now);
            if (RiskWindow.shouldEvict(state.score, state.lastUpdateMs, now, window)) {
                it.remove();
            }
        }
    }

    private long windowMs() {
        return Math.max(0L, plugin.getConfig().getLong("ai.risk-window-ms", 45_000L));
    }

    public void reload() {
        states.clear();
    }

    public void remove(UUID uuid) {
        if (uuid != null) states.remove(uuid);
    }

    private void decayState(RiskState state, long now) {
        if (state == null || state.lastUpdateMs <= 0L) return;
        long elapsed = now - state.lastUpdateMs;
        if (elapsed <= 0L) return;

        double decayPerSecond = plugin.getConfig().getDouble("ai.decay-per-second", 0.35D);
        state.score = RiskWindow.decay(state.score, state.lastUpdateMs, now, decayPerSecond);
        state.lastUpdateMs = now;
    }

    private double r(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static final class RiskState {
        private double score;
        private long lastUpdateMs;
        private int recentFlags;
    }
}
