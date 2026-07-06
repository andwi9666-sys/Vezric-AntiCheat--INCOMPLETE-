package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.integrations.BedrockDetector;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Client-platform/version awareness (compat.* config block). All adjustments here
 * only ever EXEMPT or LOOSEN — never tighten — so the worst case for a misdetected
 * client is a missed flag, not a false one.
 *
 * Bedrock players (Geyser/Floodgate) get aim-check exemption, scaffold buffer
 * scaling, and a reach margin. Modern Java clients behind ViaVersion can be
 * identified by protocol version for diagnostics and future leniency rules.
 */
public final class ClientCompatUtil {

    private ClientCompatUtil() {}

    // --- Bedrock ---

    /** Bedrock verdict, cached in PlayerData after the first evaluation. */
    public static boolean isBedrock(VezAntiCheat plugin, Player player, PlayerData data) {
        if (plugin == null || player == null) return false;
        if (!plugin.getConfig().getBoolean("compat.bedrock.enabled", true)) return false;
        if (data != null) {
            int cached = data.getBedrockVerdict();
            if (cached >= 0) return cached == 1;
        }
        boolean bedrock = BedrockDetector.isBedrock(player, data);
        if (data != null) data.setBedrockVerdict(bedrock ? 1 : 0);
        return bedrock;
    }

    /** True when aim-characteristic checks should skip this player entirely. */
    public static boolean isAimExempt(VezAntiCheat plugin, Player player, PlayerData data) {
        return plugin != null
                && plugin.getConfig().getBoolean("compat.bedrock.exempt-aim-checks", true)
                && isBedrock(plugin, player, data);
    }

    /** Scaffold flag buffers scale up for Bedrock (touch placement timing differs). */
    public static int scaledScaffoldBuffer(VezAntiCheat plugin, Player player, PlayerData data, int base) {
        if (!isBedrock(plugin, player, data)) return base;
        double multiplier = plugin.getConfig().getDouble("compat.bedrock.scaffold-buffer-multiplier", 1.5D);
        return (int) Math.ceil(base * Math.max(1.0D, multiplier));
    }

    /** Extra reach margin for Bedrock players (Geyser translation adds positional slack). */
    public static double bedrockReachExtra(VezAntiCheat plugin, Player player, PlayerData data) {
        if (!isBedrock(plugin, player, data)) return 0.0D;
        return Math.max(0.0D, plugin.getConfig().getDouble("compat.bedrock.reach-extra", 0.05D));
    }

    // --- ViaVersion ---

    private static volatile boolean viaResolved;
    private static volatile Object viaApi;
    private static volatile Method viaGetPlayerVersion;

    /** Client protocol version via ViaVersion, or -1 when unavailable. 1.8.x = 47. */
    public static int protocolVersion(Player player) {
        if (player == null) return -1;
        if (!viaResolved) resolveVia();
        Object api = viaApi;
        Method method = viaGetPlayerVersion;
        if (api == null || method == null) return -1;
        try {
            Object result = method.invoke(api, player.getUniqueId());
            return result instanceof Integer ? ((Integer) result).intValue() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** True when the player runs a newer-than-1.8 client through ViaVersion. */
    public static boolean isModernJavaClient(VezAntiCheat plugin, Player player) {
        if (plugin == null || !plugin.getConfig().getBoolean("compat.via.enabled", true)) return false;
        int protocol = protocolVersion(player);
        return protocol > 47;
    }

    private static synchronized void resolveVia() {
        if (viaResolved) return;
        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = viaClass.getMethod("getAPI").invoke(null);
            Method method = api.getClass().getMethod("getPlayerVersion", UUID.class);
            method.setAccessible(true);
            viaApi = api;
            viaGetPlayerVersion = method;
        } catch (Throwable ignored) {
            // ViaVersion not installed.
        } finally {
            viaResolved = true;
        }
    }
}
