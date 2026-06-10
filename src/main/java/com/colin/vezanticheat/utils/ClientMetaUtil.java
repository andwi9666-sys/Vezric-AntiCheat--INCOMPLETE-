package com.colin.vezanticheat.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import org.bukkit.entity.Player;

/** Resolves client version label for Polar staff metadata. */
public final class ClientMetaUtil {

    private ClientMetaUtil() {}

    public static String resolveClientVersion(Player player) {
        if (player == null) return "Unknown";
        try {
            if (PacketEvents.getAPI() != null && PacketEvents.getAPI().getPlayerManager() != null) {
                User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
                if (user != null && user.getClientVersion() != null) {
                    return formatVersion(user.getClientVersion());
                }
            }
        } catch (Throwable ignored) {
        }
        return "1.8";
    }

    private static String formatVersion(ClientVersion version) {
        if (version == null) return "Unknown";
        String name = version.name();
        if (name.startsWith("V_")) {
            name = name.substring(2).replace('_', '.');
        }
        if (name.endsWith(".0")) {
            name = name.substring(0, name.length() - 2);
        }
        return name;
    }
}
