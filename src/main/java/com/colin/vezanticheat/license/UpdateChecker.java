package com.colin.vezanticheat.license;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;

/**
 * Optional version check surfaced in /vez status. Does not auto-download.
 */
public final class UpdateChecker {

    private final VezAntiCheat plugin;
    private volatile String latestVersion;
    private volatile String changelogUrl;
    private volatile long lastCheckMs;

    public UpdateChecker(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void checkAsyncIfEnabled() {
        if (!plugin.getConfig().getBoolean("updates.check-enabled", false)) return;
        String endpoint = plugin.getConfig().getString("updates.manifest-url", "").trim();
        if (endpoint.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                fetchManifest(endpoint);
            }
        });
    }

    private void fetchManifest(String endpoint) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String body = sb.toString();
            latestVersion = extractJsonString(body, "latest");
            changelogUrl = extractJsonString(body, "changelog");
            lastCheckMs = System.currentTimeMillis();
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "[Updates] Manifest check failed", e);
        }
    }

    public String statusLine(String currentVersion) {
        if (latestVersion == null || latestVersion.isEmpty()) return "updates=disabled";
        if (currentVersion.equals(latestVersion)) return "latest=" + latestVersion;
        return "update available: " + latestVersion + (changelogUrl != null ? " (" + changelogUrl + ")" : "");
    }

    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }
}
