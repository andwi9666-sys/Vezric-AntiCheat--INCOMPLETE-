package com.colin.vezanticheat.license;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Minimal license gate for direct premium sales. Marketplace listings can disable via config.
 * Never throws on Netty/packet threads — validation runs async on enable.
 */
public final class LicenseManager {

    private final VezAntiCheat plugin;
    private final AtomicBoolean valid = new AtomicBoolean(true);
    private long graceExpiresMs;

    public LicenseManager(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (!plugin.getConfig().getBoolean("license.enabled", false)) {
            valid.set(true);
            return;
        }

        String key = plugin.getConfig().getString("license.key", "").trim();
        long graceHours = plugin.getConfig().getLong("license.grace-hours", 24L);
        graceExpiresMs = System.currentTimeMillis() + graceHours * 3_600_000L;

        if (key.isEmpty()) {
            plugin.getLogger().warning("[License] No license.key configured — "
                    + graceHours + "h grace before checks disable.");
            valid.set(true);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                boolean ok = validateKey(key);
                valid.set(ok);
                if (!ok) {
                    plugin.getLogger().warning("[License] Key validation failed — grace period active.");
                } else {
                    plugin.getLogger().info("[License] Key accepted.");
                }
            }
        });
    }

    public boolean checksAllowed() {
        if (!plugin.getConfig().getBoolean("license.enabled", false)) {
            return true;
        }
        if (valid.get()) return true;
        return System.currentTimeMillis() < graceExpiresMs;
    }

    public void warnStaffIfGrace() {
        if (!plugin.getConfig().getBoolean("license.enabled", false)) return;
        if (valid.get()) return;
        if (System.currentTimeMillis() >= graceExpiresMs) {
            String msg = ChatColor.RED + "[VezAC] License invalid — anticheat checks disabled.";
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("vez.admin")) p.sendMessage(msg);
            }
        }
    }

    private boolean validateKey(String key) {
        // Offline format check until validation endpoint is configured.
        String endpoint = plugin.getConfig().getString("license.validation-url", "").trim();
        if (endpoint.isEmpty()) {
            return key.length() >= 16 && key.matches("[A-Za-z0-9\\-]+");
        }
        try {
            java.net.URL url = new java.net.URL(endpoint + "?key=" + java.net.URLEncoder.encode(key, "UTF-8"));
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[License] Validation request failed", e);
            return false;
        }
    }
}
