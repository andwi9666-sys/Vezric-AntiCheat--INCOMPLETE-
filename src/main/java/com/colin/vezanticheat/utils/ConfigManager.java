package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConfigManager {
    private final VezAntiCheat plugin;
    private File checksFile;
    private FileConfiguration checksConfig;

    private static final Set<String> RESERVED_CHECK_KEYS = Collections.unmodifiableSet(
            java.util.Arrays.stream(new String[]{"defaults", "checks-version"})
                    .collect(java.util.stream.Collectors.toSet()));

    public ConfigManager(VezAntiCheat plugin) {
        this.plugin = plugin;
        loadChecksConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        loadChecksConfig();
    }

    private void loadChecksConfig() {
        File legacyFile = new File(plugin.getDataFolder(), "checks.yml.legacy");
        File oldFile = new File(plugin.getDataFolder(), "checks.yml");
        if (!legacyFile.exists()) {
            if (oldFile.exists()) {
                checksFile = oldFile;
            } else {
                plugin.saveResource("checks.yml.legacy", false);
                checksFile = legacyFile;
            }
        } else {
            checksFile = legacyFile;
        }
        if (!checksFile.exists()) {
            plugin.saveResource("checks.yml.legacy", false);
            checksFile = legacyFile;
        }
        checksConfig = YamlConfiguration.loadConfiguration(checksFile);
    }

    public FileConfiguration getChecksConfig() {
        return checksConfig;
    }

    public void saveChecksConfig() {
        if (checksConfig == null || checksFile == null) return;
        try {
            checksConfig.save(checksFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save checks.yml: " + e.getMessage());
        }
    }

    public List<String> listCheckNames() {
        if (checksConfig == null) return Collections.emptyList();
        List<String> names = new ArrayList<String>();
        for (String key : checksConfig.getKeys(false)) {
            if (!RESERVED_CHECK_KEYS.contains(key)) {
                names.add(key);
            }
        }
        Collections.sort(names);
        return names;
    }

    public ConfigurationSection checkSection(String checkName) {
        if (checksConfig == null || checkName == null) return null;
        return checksConfig.getConfigurationSection(checkName);
    }

    private ConfigurationSection checkDefaults() {
        return checksConfig == null ? null : checksConfig.getConfigurationSection("defaults");
    }

    public boolean hasCheckKey(String checkName, String key) {
        ConfigurationSection check = checkSection(checkName);
        if (check != null && check.contains(key)) return true;
        ConfigurationSection defaults = checkDefaults();
        return defaults != null && defaults.contains(key);
    }

    public Object getCheckValue(String checkName, String key) {
        ConfigurationSection check = checkSection(checkName);
        if (check != null && check.contains(key)) {
            return check.get(key);
        }
        ConfigurationSection defaults = checkDefaults();
        if (defaults != null && defaults.contains(key)) {
            return defaults.get(key);
        }
        return null;
    }

    public void setCheckValue(String checkName, String key, Object value) {
        if (checksConfig == null || checkName == null || key == null) return;
        ConfigurationSection check = checksConfig.getConfigurationSection(checkName);
        if (check == null) {
            check = checksConfig.createSection(checkName);
        }
        if (value == null) {
            check.set(key, null);
        } else {
            check.set(key, value);
        }
    }

    public void removeCheckKey(String checkName, String key) {
        ConfigurationSection check = checkSection(checkName);
        if (check != null) {
            check.set(key, null);
        }
    }

    public boolean checkEnabled(String checkName) {
        return checkBoolean(checkName, "enabled", true);
    }

    public boolean checkBoolean(String checkName, String key, boolean def) {
        Object value = getCheckValue(checkName, key);
        if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        if (value instanceof String) {
            String s = ((String) value).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(s) || "on".equals(s) || "yes".equals(s)) return true;
            if ("false".equals(s) || "off".equals(s) || "no".equals(s)) return false;
        }
        return def;
    }

    public String checkString(String checkName, String key, String def) {
        Object value = getCheckValue(checkName, key);
        return value == null ? def : String.valueOf(value);
    }

    public double checkVlWeight(String checkName) {
        return checkDouble(checkName, "vl", 1.0);
    }

    public double checkDouble(String checkName, String key, double def) {
        Object value = getCheckValue(checkName, key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public int checkInt(String checkName, String key, int def) {
        Object value = getCheckValue(checkName, key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public long checkLong(String checkName, String key, long def) {
        Object value = getCheckValue(checkName, key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public boolean enabled() {
        if (!plugin.getConfig().getBoolean("anticheat.enabled", true)) return false;
        return plugin.license() == null || plugin.license().checksAllowed();
    }

    public String prefix() {
        return color(plugin.getConfig().getString("prefix", "&0&l[PE&7RPLEX&8ION] &r"));
    }

    public String msg(String key) {
        return color(plugin.getConfig().getString("messages." + key, ""));
    }

    public String flagFormat() {
        return color(plugin.getConfig().getString("flags.format",
                "{prefix}&e{player} &7failed &c{check} &7(&fVL {vl}&7)"));
    }

    public boolean staffDefaultFlagsOn() {
        return plugin.getConfig().getBoolean("flags.staff-default-on", true);
    }

    public boolean punishEnabled() {
        return plugin.getConfig().getBoolean("punish.enabled", true);
    }

    /**
     * Buyer-facing safety mode gate. When punish.safety-mode is missing (pre-1.2.0
     * configs), derive from legacy keys so existing setups keep their exact behavior:
     * punish.enabled=false → ALERTS_ONLY, execution.type=IMMEDIATE → INSTANT, else BANWAVE.
     */
    public com.colin.vezanticheat.punishment.PunishmentMode punishSafetyMode() {
        String raw = plugin.getConfig().getString("punish.safety-mode", null);
        if (raw != null && !raw.trim().isEmpty()) {
            return com.colin.vezanticheat.punishment.PunishmentMode.parse(
                    raw, com.colin.vezanticheat.punishment.PunishmentMode.BANWAVE);
        }
        if (!punishEnabled()) {
            return com.colin.vezanticheat.punishment.PunishmentMode.ALERTS_ONLY;
        }
        if ("IMMEDIATE".equalsIgnoreCase(punishExecutionType())) {
            return com.colin.vezanticheat.punishment.PunishmentMode.INSTANT;
        }
        return com.colin.vezanticheat.punishment.PunishmentMode.BANWAVE;
    }

    /** Punishment-time TPS gate (punish.lag-gate): defer executions during server lag. */
    public boolean punishLagGateEnabled() {
        return plugin.getConfig().getBoolean("punish.lag-gate.enabled", true);
    }

    public long punishLagGateRetryMs() {
        return plugin.getConfig().getLong("punish.lag-gate.retry-delay-ms", 120000L);
    }

    /**
     * Extra exemption-window milliseconds for a player's current ping
     * (exempt.ping-scaling): min(ping * factor, cap-ms). High-ping players get
     * longer teleport/velocity/blockstate/potion grace so late packets do not flag.
     */
    public long exemptPingBonusMs(org.bukkit.entity.Player player) {
        if (player == null) return 0L;
        if (!plugin.getConfig().getBoolean("exempt.ping-scaling.enabled", true)) return 0L;
        int ping = PingUtil.getPing(player);
        if (ping <= 0) return 0L;
        double factor = plugin.getConfig().getDouble("exempt.ping-scaling.factor", 0.5D);
        long cap = plugin.getConfig().getLong("exempt.ping-scaling.cap-ms", 150L);
        return Math.min((long) (ping * factor), Math.max(0L, cap));
    }

    public int banVl() {
        return plugin.getConfig().getInt("punish.ban-vl", 25);
    }

    public double kbHorizontal() {
        LegacyKbBridge.KnockbackProfile profile = LegacyKbBridge.readProfile();
        return profile != null ? profile.horizontal : plugin.getConfig().getDouble("knockback-profile.horizontal", 0.40D);
    }

    public double kbVertical() {
        LegacyKbBridge.KnockbackProfile profile = LegacyKbBridge.readProfile();
        return profile != null ? profile.vertical : plugin.getConfig().getDouble("knockback-profile.vertical", 0.389D);
    }

    public double kbVerticalLimit() {
        LegacyKbBridge.KnockbackProfile profile = LegacyKbBridge.readProfile();
        return profile != null ? profile.verticalLimit : plugin.getConfig().getDouble("knockback-profile.vertical-limit", 0.40D);
    }

    public double kbExtraHorizontal() {
        LegacyKbBridge.KnockbackProfile profile = LegacyKbBridge.readProfile();
        return profile != null ? profile.extraHorizontal : plugin.getConfig().getDouble("knockback-profile.extra-horizontal", 0.50D);
    }

    public double kbExtraVertical() {
        LegacyKbBridge.KnockbackProfile profile = LegacyKbBridge.readProfile();
        return profile != null ? profile.extraVertical : plugin.getConfig().getDouble("knockback-profile.extra-vertical", 0.10D);
    }

    public double kbHorizontalScale() {
        double baseline = 0.40D;
        double profile = kbHorizontal();
        if (profile <= 1.0E-6D) return 1.0D;
        return profile / baseline;
    }

    public double kbVerticalScale() {
        double baseline = 0.40D;
        double profile = Math.min(kbVerticalLimit(), kbVertical() + kbExtraVertical());
        if (profile <= 1.0E-6D) return 1.0D;
        return profile / baseline;
    }

    public String punishMode() {
        return plugin.getConfig().getString("punish.mode", "CHECK").toUpperCase();
    }

    public String punishExecutionType() {
        return plugin.getConfig().getString("punish.execution.type", "BANWAVE").toUpperCase();
    }

    public boolean autoBanwaveEnabled() {
        return plugin.getConfig().getBoolean("punish.execution.auto-banwave.enabled", true);
    }

    public long autoBanwaveIntervalSeconds() {
        return plugin.getConfig().getLong("punish.execution.auto-banwave.interval-seconds", 30L);
    }

    public long autoBanwaveMinDelaySeconds() {
        return plugin.getConfig().getLong("punish.execution.auto-banwave.min-delay-seconds", 900L);
    }

    public long autoBanwaveMaxDelaySeconds() {
        return plugin.getConfig().getLong("punish.execution.auto-banwave.max-delay-seconds", 7200L);
    }

    public int autoBanwaveBatchSize() {
        return plugin.getConfig().getInt("punish.execution.auto-banwave.batch-size", 8);
    }

    public boolean hybridImmediateEnabled() {
        return plugin.getConfig().getBoolean("punish.execution.hybrid.enabled", true);
    }

    public int hybridImmediateVl() {
        return plugin.getConfig().getInt("punish.execution.hybrid.blatant-vl", 45);
    }

    public long hybridImmediateWindowSeconds() {
        return plugin.getConfig().getLong("punish.execution.hybrid.window-seconds", 240L);
    }

    public int hybridImmediateMinFlags() {
        return plugin.getConfig().getInt("punish.execution.hybrid.min-flags", 8);
    }

    public int hybridImmediateMinHotChecks() {
        return plugin.getConfig().getInt("punish.execution.hybrid.min-hot-checks", 2);
    }

    public double hybridImmediateCheckVl() {
        return plugin.getConfig().getDouble("punish.execution.hybrid.min-check-vl", 15.0D);
    }

    public boolean blatantBedNukerEnabled() {
        return plugin.getConfig().getBoolean("punish.execution.hybrid.blatant-bed-nuker.enabled", true);
    }

    public long blatantBedNukerWindowSeconds() {
        return plugin.getConfig().getLong("punish.execution.hybrid.blatant-bed-nuker.window-seconds", 120L);
    }

    public int blatantBedNukerIncidents() {
        return plugin.getConfig().getInt("punish.execution.hybrid.blatant-bed-nuker.incidents", 2);
    }

    public long blatantBedNukerDedupeMs() {
        return plugin.getConfig().getLong("punish.execution.hybrid.blatant-bed-nuker.dedupe-ms", 3000L);
    }

    public double punishMarkScore() {
        return plugin.getConfig().getDouble("punish.evidence.mark-score", 8.0);
    }

    public double punishQueueScore() {
        return plugin.getConfig().getDouble("punish.evidence.queue-score", 12.0);
    }

    public long punishRecentWindowSeconds() {
        return plugin.getConfig().getLong("punish.evidence.recent-window-seconds", 600L);
    }

    public int punishFastFlagCount() {
        return plugin.getConfig().getInt("punish.evidence.fast-flags.count", 6);
    }

    public long punishFastFlagWindowSeconds() {
        return plugin.getConfig().getLong("punish.evidence.fast-flags.window-seconds", 90L);
    }

    public double punishFastFlagBonus() {
        return plugin.getConfig().getDouble("punish.evidence.fast-flags.bonus", 2.5);
    }

    public double punishMultiCheckBonus() {
        return plugin.getConfig().getDouble("punish.evidence.multi-check-bonus", 1.75);
    }

    public double punishTripleCheckBonus() {
        return plugin.getConfig().getDouble("punish.evidence.triple-check-bonus", 2.5);
    }

    public int punishGlobalEvidenceVl() {
        return plugin.getConfig().getInt("punish.evidence.global-vl", 35);
    }

    public double punishGlobalEvidenceBonus() {
        return plugin.getConfig().getDouble("punish.evidence.global-bonus", 1.5);
    }

    public double punishMarkedBonus() {
        return plugin.getConfig().getDouble("punish.evidence.marked-bonus", 1.75);
    }

    public String punishReason(String category) {
        return plugin.getConfig().getString("punish.reasons." + category, "Cheating");
    }

    // Ban announcement settings. Reads punish.announcements.*; falls back to the
    // legacy punish.watchdog.* keys so pre-1.2.0 configs keep working.
    private String announcementString(String key, String def) {
        String legacy = plugin.getConfig().getString("punish.watchdog." + key, def);
        return plugin.getConfig().getString("punish.announcements." + key, legacy);
    }

    public String watchdogReason(String category) {
        String fallback = announcementString("reason",
                "Cheating through the use of unfair game advantages.");
        if (category == null) return fallback;
        return announcementString("reasons." + category, fallback);
    }

    public String watchdogAppealUrl() {
        return announcementString("appeal-url", "https://your-server.example/appeal");
    }

    public String watchdogBroadcast() {
        return announcementString("broadcast", "&4Perplexion &7has removed &c{player} &7for cheating.");
    }

    public String watchdogRemoveMessage() {
        return announcementString("remove-message", "&7A player has been removed from your game.");
    }

    public String punishCmdTemp() {
        return plugin.getConfig().getString("punish.commands.tempban", "tempban {player} {time} {timeform} {reason}");
    }

    public String punishCmdPerm() {
        return plugin.getConfig().getString("punish.commands.permban", "ban {player} {reason}");
    }

    public String ladderType(int n) {
        return plugin.getConfig().getString("punish.ladder." + n + ".type", "TEMP");
    }

    public String ladderDuration(int n) {
        return plugin.getConfig().getString("punish.ladder." + n + ".duration", "7d");
    }

    public int punishMaxStage() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("punish.ladder");
        return section == null ? 4 : Math.max(1, section.getKeys(false).size());
    }

    public ConfidenceProfile confidenceProfileFor(String checkName, String category) {
        String tier = plugin.getConfig().getString("punish.evidence.overrides." + checkName, null);
        if (tier == null && category != null) {
            tier = plugin.getConfig().getString("punish.evidence.categories." + category.toUpperCase(), null);
        }
        if (tier == null) {
            tier = plugin.getConfig().getString("punish.evidence.default", "MEDIUM");
        }
        return confidenceProfile(tier);
    }

    private ConfidenceProfile confidenceProfile(String tierName) {
        String tier = tierName == null ? "MEDIUM" : tierName.toUpperCase();
        String base = "punish.evidence.confidence." + tier + ".";
        double minCheckVl = plugin.getConfig().getDouble(base + "min-check-vl", 8.0);
        double baseScore = plugin.getConfig().getDouble(base + "score", 3.0);
        double maxContribution = plugin.getConfig().getDouble(base + "max-contribution", baseScore * 2.0);
        return new ConfidenceProfile(minCheckVl, baseScore, maxContribution);
    }

    public static final class ConfidenceProfile {
        public final double minCheckVl;
        public final double baseScore;
        public final double maxContribution;

        public ConfidenceProfile(double minCheckVl, double baseScore, double maxContribution) {
            this.minCheckVl = minCheckVl;
            this.baseScore = baseScore;
            this.maxContribution = maxContribution;
        }
    }

    public long teleportExemptMs() {
        return plugin.getConfig().getLong("exempt.teleport-ms", 900L);
    }

    public long velocityExemptMs() {
        return plugin.getConfig().getLong("exempt.velocity-ms", 450L);
    }

    public long blockStateExemptMs() {
        return plugin.getConfig().getLong("exempt.blockstate-ms", 350L);
    }

    public long potionExemptMs() {
        return plugin.getConfig().getLong("exempt.potion-ms", 800L);
    }

    public int maxPing() {
        return plugin.getConfig().getInt("lag.max-ping", 250);
    }

    public double minTps() {
        return plugin.getConfig().getDouble("lag.min-tps", 18.5);
    }

    public boolean gateByLag() {
        return plugin.getConfig().getBoolean("lag.enable-gates", true);
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
