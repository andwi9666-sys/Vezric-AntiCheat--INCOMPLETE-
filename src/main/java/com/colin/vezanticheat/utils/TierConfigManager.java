package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads tier YAML configs (characteristics, prism, simulation, prediction).
 */
public final class TierConfigManager {

    private static final String[] TIER_FILES = {
            "characteristics.yml",
            "prism.yml",
            "simulation.yml",
            "prediction.yml"
    };

    private final VezAntiCheat plugin;
    private final Map<CheckTier, FileConfiguration> tierConfigs = new EnumMap<CheckTier, FileConfiguration>(CheckTier.class);
    private final Map<CheckTier, File> tierFiles = new EnumMap<CheckTier, File>(CheckTier.class);

    public TierConfigManager(VezAntiCheat plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void reload() {
        loadAll();
    }

    private void loadAll() {
        File tiersDir = new File(plugin.getDataFolder(), "tiers");
        if (!tiersDir.exists()) tiersDir.mkdirs();

        tierConfigs.put(CheckTier.CHARACTERISTICS, loadTierFile(tiersDir, "characteristics.yml", CheckTier.CHARACTERISTICS));
        tierConfigs.put(CheckTier.PRISM, loadTierFile(tiersDir, "prism.yml", CheckTier.PRISM));
        tierConfigs.put(CheckTier.SIMULATION, loadTierFile(tiersDir, "simulation.yml", CheckTier.SIMULATION));
        tierConfigs.put(CheckTier.PREDICTION, loadTierFile(tiersDir, "prediction.yml", CheckTier.PREDICTION));
    }

    private FileConfiguration loadTierFile(File dir, String fileName, CheckTier tier) {
        File file = new File(dir, fileName);
        tierFiles.put(tier, file);
        if (!file.exists()) {
            plugin.saveResource("tiers/" + fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration tierConfig(CheckTier tier) {
        return tierConfigs.get(tier);
    }

    public ConfigurationSection checkSection(String checkName) {
        if (checkName == null) return null;
        for (FileConfiguration cfg : tierConfigs.values()) {
            ConfigurationSection section = cfg.getConfigurationSection(checkName);
            if (section != null) return section;
        }
        return null;
    }

    public CheckTier tierForCheck(String checkName) {
        ConfigurationSection section = checkSection(checkName);
        if (section == null) return null;
        String raw = section.getString("tier");
        if (raw == null) return null;
        try {
            return CheckTier.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public List<String> listCheckNames() {
        List<String> names = new ArrayList<String>();
        for (FileConfiguration cfg : tierConfigs.values()) {
            for (String key : cfg.getKeys(false)) {
                if ("defaults".equals(key) || key.endsWith("-version")) continue;
                if (!names.contains(key)) names.add(key);
            }
        }
        Collections.sort(names);
        return names;
    }

    private ConfigurationSection defaults(CheckTier tier) {
        FileConfiguration cfg = tierConfigs.get(tier);
        return cfg == null ? null : cfg.getConfigurationSection("defaults");
    }

    private Object getValue(CheckTier tier, String checkName, String key) {
        FileConfiguration cfg = tierConfigs.get(tier);
        if (cfg == null) return null;
        ConfigurationSection check = cfg.getConfigurationSection(checkName);
        if (check != null && check.contains(key)) return check.get(key);
        ConfigurationSection def = defaults(tier);
        if (def != null && def.contains(key)) return def.get(key);
        return null;
    }

    private CheckTier resolveTier(String checkName) {
        CheckTier explicit = tierForCheck(checkName);
        if (explicit != null) return explicit;
        for (CheckTier tier : CheckTier.values()) {
            FileConfiguration cfg = tierConfigs.get(tier);
            if (cfg != null && cfg.isConfigurationSection(checkName)) return tier;
        }
        return CheckTier.CHARACTERISTICS;
    }

    public boolean checkEnabled(String checkName) {
        return checkBoolean(checkName, "enabled", true);
    }

    public boolean checkBoolean(String checkName, String key, boolean def) {
        CheckTier tier = resolveTier(checkName);
        Object value = getValue(tier, checkName, key);
        if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        if (value instanceof String) {
            String s = ((String) value).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(s) || "on".equals(s) || "yes".equals(s)) return true;
            if ("false".equals(s) || "off".equals(s) || "no".equals(s)) return false;
        }
        return def;
    }

    public double checkDouble(String checkName, String key, double def) {
        CheckTier tier = resolveTier(checkName);
        Object value = getValue(tier, checkName, key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble(((String) value).trim()); }
            catch (NumberFormatException ignored) { return def; }
        }
        return def;
    }

    public int checkInt(String checkName, String key, int def) {
        CheckTier tier = resolveTier(checkName);
        Object value = getValue(tier, checkName, key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt(((String) value).trim()); }
            catch (NumberFormatException ignored) { return def; }
        }
        return def;
    }

    public long checkLong(String checkName, String key, long def) {
        CheckTier tier = resolveTier(checkName);
        Object value = getValue(tier, checkName, key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong(((String) value).trim()); }
            catch (NumberFormatException ignored) { return def; }
        }
        return def;
    }

    public String checkString(String checkName, String key, String def) {
        CheckTier tier = resolveTier(checkName);
        Object value = getValue(tier, checkName, key);
        if (value != null) return String.valueOf(value);
        return def;
    }

    public void setCheckValue(String checkName, String key, Object value) {
        CheckTier tier = resolveTier(checkName);
        FileConfiguration cfg = tierConfigs.get(tier);
        if (cfg == null) return;
        ConfigurationSection section = cfg.getConfigurationSection(checkName);
        if (section == null) section = cfg.createSection(checkName);
        section.set(key, value);
        saveTier(tier);
    }

    public void saveTier(CheckTier tier) {
        File file = tierFiles.get(tier);
        FileConfiguration cfg = tierConfigs.get(tier);
        if (file == null || cfg == null) return;
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save tier config " + file.getName() + ": " + e.getMessage());
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("anticheat.enabled", true);
    }

    public boolean gateByLag() {
        return plugin.getConfig().getBoolean("lag.enable-gates", true);
    }

    public double minTps() {
        return plugin.getConfig().getDouble("lag.min-tps", 18.5D);
    }

    public int maxPing() {
        return plugin.getConfig().getInt("lag.max-ping", 250);
    }
}
