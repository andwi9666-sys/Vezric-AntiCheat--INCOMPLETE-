package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Applies bundled lenient/balanced/aggressive profiles from jar resources to the live config.yml.
 */
public final class ConfigProfileManager {

    private static final Set<String> PROFILES = new HashSet<String>(
            Arrays.asList("lenient", "balanced", "aggressive"));

    private final VezAntiCheat plugin;

    public ConfigProfileManager(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public static boolean isValidProfile(String name) {
        return name != null && PROFILES.contains(name.toLowerCase(Locale.ROOT));
    }

    public String applyProfile(String profileName) throws IOException {
        String normalized = profileName.toLowerCase(Locale.ROOT);
        if (!PROFILES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown profile: " + profileName);
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Could not create plugin data folder");
        }

        File configFile = new File(dataFolder, "config.yml");
        if (configFile.exists()) {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File backup = new File(dataFolder, "config.yml.bak-" + stamp);
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        String resourcePath = "config-profiles/" + normalized + ".yml";
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) {
            throw new IOException("Bundled profile missing: " + resourcePath);
        }

        FileConfiguration bundled = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        in.close();
        bundled.save(configFile);
        return normalized;
    }
}
