package com.colin.vezanticheat.integrations;

import com.colin.vezanticheat.VezAntiCheat;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Coexistence with Vulcan when both use the shared PacketEvents server plugin.
 * Vulcan also injects transaction pings and heavy packet listeners — we defer hooks and
 * disable our outbound transaction injection to avoid channel / confirmation clashes.
 */
public final class VulcanCompat {

    private static final String[] VULCAN_PLUGIN_NAMES = {"Vulcan", "vulcan"};

    private VulcanCompat() {}

    public static boolean isVulcanPresent() {
        for (String name : VULCAN_PLUGIN_NAMES) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null) {
                return true;
            }
        }
        return false;
    }

    public static Settings resolve(VezAntiCheat plugin) {
        String mode = plugin.getConfig().getString("integrations.vulcan.mode", "auto").trim().toLowerCase();
        boolean enabled;
        if ("true".equals(mode) || "on".equals(mode) || "compat".equals(mode)) {
            enabled = true;
        } else if ("false".equals(mode) || "off".equals(mode)) {
            enabled = false;
        } else {
            enabled = isVulcanPresent();
        }

        if (!enabled) {
            return Settings.disabled();
        }

        long hookDelayTicks = Math.max(0L, plugin.getConfig().getLong("integrations.vulcan.hook-delay-ticks", 40L));
        boolean disableTransactions = plugin.getConfig().getBoolean("integrations.vulcan.disable-transactions", true);
        boolean skipTransactionListener = plugin.getConfig().getBoolean("integrations.vulcan.skip-transaction-listener", true);
        PacketListenerPriority receivePriority = parsePriority(
                plugin.getConfig().getString("integrations.vulcan.receive-priority", "LOWEST"));

        return new Settings(true, hookDelayTicks, disableTransactions, skipTransactionListener, receivePriority);
    }

    private static PacketListenerPriority parsePriority(String raw) {
        if (raw == null) return PacketListenerPriority.LOWEST;
        try {
            return PacketListenerPriority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PacketListenerPriority.LOWEST;
        }
    }

    public static final class Settings {
        public final boolean active;
        public final long hookDelayTicks;
        public final boolean disableTransactions;
        public final boolean skipTransactionListener;
        public final PacketListenerPriority receivePriority;

        private Settings(boolean active, long hookDelayTicks, boolean disableTransactions,
                         boolean skipTransactionListener, PacketListenerPriority receivePriority) {
            this.active = active;
            this.hookDelayTicks = hookDelayTicks;
            this.disableTransactions = disableTransactions;
            this.skipTransactionListener = skipTransactionListener;
            this.receivePriority = receivePriority;
        }

        static Settings disabled() {
            return new Settings(false, 0L, false, false, PacketListenerPriority.NORMAL);
        }
    }
}
