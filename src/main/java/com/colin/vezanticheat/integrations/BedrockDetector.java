package com.colin.vezanticheat.integrations;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

/**
 * Detects Bedrock players (joining through Geyser/Floodgate). Bedrock clients have
 * fundamentally different input (touch/controller aim, different rotation cadence,
 * server-authoritative reach quirks), so several checks exempt or loosen for them.
 *
 * Detection order, most to least reliable:
 *   1. Floodgate API via reflection (no compile-time dependency)
 *   2. Floodgate UUID convention (most significant bits == 0)
 *   3. Client brand containing "geyser"
 */
public final class BedrockDetector {

    private BedrockDetector() {}

    private static volatile boolean floodgateResolved;
    private static volatile Object floodgateApi;
    private static volatile Method isFloodgatePlayer;

    public static boolean isBedrock(Player player, PlayerData data) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        Boolean floodgate = queryFloodgate(uuid);
        if (floodgate != null) return floodgate.booleanValue();

        if (uuid != null && uuid.getMostSignificantBits() == 0L) return true;

        String brand = data != null ? data.getClientBrand() : null;
        return brand != null && brand.toLowerCase(Locale.ROOT).contains("geyser");
    }

    private static Boolean queryFloodgate(UUID uuid) {
        if (uuid == null) return null;
        if (!floodgateResolved) resolveFloodgate();
        Object api = floodgateApi;
        Method method = isFloodgatePlayer;
        if (api == null || method == null) return null;
        try {
            Object result = method.invoke(api, uuid);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized void resolveFloodgate() {
        if (floodgateResolved) return;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            floodgateApi = api;
            isFloodgatePlayer = method;
        } catch (Throwable ignored) {
            // Floodgate not installed — UUID/brand fallbacks still apply.
        } finally {
            floodgateResolved = true;
        }
    }
}
