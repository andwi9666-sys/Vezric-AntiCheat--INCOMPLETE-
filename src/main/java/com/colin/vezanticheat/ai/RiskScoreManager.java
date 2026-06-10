package com.colin.vezanticheat.ai;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.utils.ConfigManager;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
        decayState(state, System.currentTimeMillis());
        return state.score;
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
        double decay = (elapsed / 1000.0D) * decayPerSecond;
        state.score = Math.max(0.0D, state.score - decay);
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
