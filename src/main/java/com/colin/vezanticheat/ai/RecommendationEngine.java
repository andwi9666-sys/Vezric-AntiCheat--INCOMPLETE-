package com.colin.vezanticheat.ai;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.punishment.PunishmentMode;
import com.colin.vezanticheat.utils.FlagStatsTracker;
import com.colin.vezanticheat.utils.PerfSampler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rule-based configuration advisor behind /perplexion recommendations. Inspects the
 * last hour of flag statistics plus live server health and produces human-readable
 * suggestions ("ReachA is flagging many high-ping players — raise the ping gate or
 * switch to the lenient profile"). Advice only — it never changes config itself.
 */
public final class RecommendationEngine {

    private final VezAntiCheat plugin;

    public RecommendationEngine(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public List<String> generate() {
        List<String> out = new ArrayList<String>();
        FlagStatsTracker stats = plugin.flagStats();
        if (stats == null) {
            out.add("Flag statistics are not available yet.");
            return out;
        }

        List<FlagStatsTracker.CheckSummary> summaries = stats.summarize();
        int maxPing = plugin.getConfig().getInt("lag.max-ping", 250);
        double minTps = plugin.getConfig().getDouble("lag.min-tps", 18.5D);

        for (FlagStatsTracker.CheckSummary s : summaries) {
            // A check hitting many distinct players at volume is the classic
            // false-positive signature — cheaters are rare, config problems are not.
            if (s.flags >= 20 && s.distinctPlayers >= 4) {
                out.add(s.check + " flagged " + s.flags + " times across " + s.distinctPlayers
                        + " players in the last hour. If these are not confirmed cheaters, raise its"
                        + " bufferToFlag (/perplexion tune " + s.check + " bufferToFlag <n>) or set it"
                        + " to shadow mode while you investigate.");
            }

            if (s.avgPing > 0 && s.avgPing >= 0.8D * maxPing && s.flags >= 8) {
                out.add(s.check + " mostly flags high-ping players (avg "
                        + Math.round(s.avgPing) + "ms vs " + maxPing + "ms gate). Consider raising"
                        + " exempt.ping-scaling.cap-ms, lowering lag.max-ping, or switching to the"
                        + " lenient profile.");
            }

            if (s.avgTps > 0 && s.avgTps < 19.0D && s.flags >= 8) {
                out.add(s.check + " flags coincide with low TPS (avg "
                        + String.format(Locale.ROOT, "%.1f", s.avgTps) + "). Raise lag.min-tps (currently "
                        + minTps + ") or address the server lag source before trusting these flags.");
            }

            if (s.topPlayer != null && s.topPlayerFlags >= 10
                    && s.topPlayerFlags >= (int) (s.flags * 0.7D)) {
                String name = resolveName(s.topPlayer);
                out.add(name + " accounts for " + s.topPlayerFlags + "/" + s.flags + " of "
                        + s.check + "'s flags this hour — likely a real cheater. Review with"
                        + " /perplexion trace " + name + " and /perplexion exportdebug " + name + ".");
            }
        }

        PerfSampler perf = plugin.perf();
        if (perf != null && perf.isEnabled()) {
            PerfSampler.Snapshot snap = perf.snapshot();
            if (snap.packetAvgMs > 0.5D && snap.packetSamples > 1000) {
                out.add("Packet processing averages "
                        + String.format(Locale.ROOT, "%.2f", snap.packetAvgMs)
                        + "ms — higher than expected. Review /perplexion perf and disable any"
                        + " per-check debug options.");
            }
        }

        PunishmentMode mode = plugin.cfg().punishSafetyMode();
        int totalFlags = stats.totalFlagsLastHour();
        if (mode == PunishmentMode.INSTANT) {
            out.add("Punishment mode is INSTANT. Unless this server has completed the staging"
                    + " checklist, switch to banwave: set punish.safety-mode: banwave.");
        }
        if (mode == PunishmentMode.SILENT && totalFlags > 0) {
            out.add("Punishment mode is SILENT — " + totalFlags + " flags this hour were recorded"
                    + " but staff saw none. Switch to alerts-only or mitigation when ready.");
        }

        if (totalFlags == 0) {
            boolean playersOnline = !Bukkit.getOnlinePlayers().isEmpty();
            if (playersOnline) {
                out.add("No flags recorded in the last hour with players online. That can be"
                        + " healthy — but verify packet hooks are active via /perplexion status.");
            } else {
                out.add("No flag data yet (no players online recently). Recommendations improve"
                        + " as real traffic accumulates.");
            }
        }

        if (out.isEmpty()) {
            out.add("Flag volume, ping distribution, and TPS all look healthy. Current profile"
                    + " and thresholds appear well-matched to this server.");
        }
        return out;
    }

    private String resolveName(java.util.UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline != null && offline.getName() != null ? offline.getName() : uuid.toString();
    }
}
