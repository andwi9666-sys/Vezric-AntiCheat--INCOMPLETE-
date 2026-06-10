package com.colin.vezanticheat.listeners;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.ClientMetaUtil;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

/** Tracks client brand (MC|Brand) and version for Polar-style staff metadata. */
public final class ClientBrandListener implements Listener, PluginMessageListener {

    private final VezAntiCheat plugin;

    public ClientBrandListener(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void registerChannels() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "MC|Brand", this);
        try {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "minecraft:brand", this);
        } catch (IllegalArgumentException ignored) {
            // Older API builds may not expose the namespaced channel.
        }
    }

    public void unregisterChannels() {
        try {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, "MC|Brand", this);
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, "minecraft:brand", this);
        } catch (Exception ignored) {
            // Channel may not have been registered (older API / namespaced channel absent);
            // unregister is best-effort during shutdown.
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        PlayerData data = plugin.getDataManager().get(p);
        data.setClientVersion(ClientMetaUtil.resolveClientVersion(p));
        if (PacketEvents.getAPI() != null && PacketEvents.getAPI().getPlayerManager() != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                data.setClientVersion(ClientMetaUtil.resolveClientVersion(p));
            }, 5L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // no-op; brand stays on PlayerData until remove
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (player == null || message == null || message.length == 0) return;
        if (!"MC|Brand".equals(channel) && !"minecraft:brand".equals(channel)) return;
        String brand = new String(message, StandardCharsets.UTF_8);
        if (brand.startsWith("\u0000")) brand = brand.substring(1);
        plugin.getDataManager().get(player).setClientBrand(brand);
    }
}
