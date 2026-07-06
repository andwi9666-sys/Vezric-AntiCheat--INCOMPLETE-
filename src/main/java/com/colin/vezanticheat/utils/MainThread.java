package com.colin.vezanticheat.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Marshals work onto the main server thread. Detection code runs on per-connection
 * Netty threads; anything touching Bukkit API that is not documented thread-safe
 * (kick, command dispatch, broadcast, inventory/GUI, player iteration) must go
 * through here. Runs inline when already on the primary thread so main-thread
 * callers keep their existing synchronous behavior.
 */
public final class MainThread {

    private MainThread() {}

    public static void run(Plugin plugin, Runnable action) {
        if (action == null) return;
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, action);
        } catch (IllegalStateException ex) {
            // Plugin is disabling; scheduler refuses new tasks. Dropping a chat/GUI
            // update during shutdown is harmless — log at fine level only.
            plugin.getLogger().fine("MainThread: dropped task during shutdown: " + ex.getMessage());
        } catch (org.bukkit.plugin.IllegalPluginAccessException ex) {
            plugin.getLogger().fine("MainThread: dropped task during shutdown: " + ex.getMessage());
        }
    }
}
