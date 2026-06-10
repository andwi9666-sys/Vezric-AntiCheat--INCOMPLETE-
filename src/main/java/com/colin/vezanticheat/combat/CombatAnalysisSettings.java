package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Runtime settings for the PacketEvents combat scoring pipeline (config.yml combat-analysis.*).
 */
public final class CombatAnalysisSettings {

    private static final String BASE = "combat-analysis.";
    private static final String DEFAULT_ALERT_FORMAT =
            "{prefix}&e{player} &7failed &cCombatAnalysis &7VL: &f{buffer} &7Reason: &f{reason}";

    private boolean enabled = true;
    private boolean cancelImpossibleHits = true;
    private boolean alertsEnabled = true;
    private long alertCooldownMs = 3000L;
    private double alertBufferSpike = 3.0D;
    private boolean debug = false;
    private String alertFormat = DEFAULT_ALERT_FORMAT;
    private long joinGraceMs = 5000L;

    public static CombatAnalysisSettings fromPlugin(VezAntiCheat plugin) {
        CombatAnalysisSettings settings = new CombatAnalysisSettings();
        if (plugin != null) {
            settings.load(plugin.getConfig());
        }
        return settings;
    }

    public void load(FileConfiguration config) {
        if (config == null) {
            return;
        }

        enabled = config.getBoolean(BASE + "enabled",
                config.getBoolean("combat-analyzer.enabled",
                        config.getBoolean(BASE + "scoring-enabled", enabled)));
        cancelImpossibleHits = config.getBoolean(BASE + "cancel-impossible-hits", cancelImpossibleHits);
        alertsEnabled = config.getBoolean(BASE + "alerts.enabled", alertsEnabled);
        alertCooldownMs = config.getLong(BASE + "alerts.cooldown-ms",
                config.getLong(BASE + "alert-cooldown-ms", alertCooldownMs));
        alertBufferSpike = config.getDouble(BASE + "alert-buffer-spike", alertBufferSpike);
        debug = config.getBoolean(BASE + "alerts.debug",
                config.getBoolean(BASE + "debug", debug));
        alertFormat = config.getString(BASE + "alert-format", DEFAULT_ALERT_FORMAT);
        if (alertFormat == null || alertFormat.isEmpty()) {
            alertFormat = DEFAULT_ALERT_FORMAT;
        }
        joinGraceMs = config.getLong(BASE + "join-grace-ms", joinGraceMs);
    }

    public void applyTo(CombatAnalyzer analyzer, FileConfiguration config) {
        if (analyzer == null) {
            return;
        }
        analyzer.getConfig().loadFrom(config);
        analyzer.setCancelImpossibleHits(cancelImpossibleHits);
    }

    public void applyTo(CombatAnalyzer analyzer) {
        applyTo(analyzer, null);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** @deprecated use {@link #isEnabled()} */
    @Deprecated
    public boolean isScoringEnabled() {
        return enabled;
    }

    public boolean isCancelImpossibleHits() {
        return cancelImpossibleHits;
    }

    public boolean isAlertsEnabled() {
        return alertsEnabled;
    }

    public long getAlertCooldownMs() {
        return alertCooldownMs;
    }

    public double getAlertBufferSpike() {
        return alertBufferSpike;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getAlertFormat() {
        return alertFormat;
    }

    public long getJoinGraceMs() {
        return joinGraceMs;
    }
}
