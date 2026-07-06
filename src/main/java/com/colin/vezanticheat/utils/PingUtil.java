package com.colin.vezanticheat.utils;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class PingUtil {
    private PingUtil() {}

    // Reflection handles resolved once — getPing is called on hot packet paths,
    // and Class.forName/getMethod per call costs more than the field read itself.
    private static final Class<?> CRAFT_PLAYER;
    private static final Method GET_HANDLE;
    private static volatile Field PING_FIELD;

    static {
        Class<?> craft = null;
        Method getHandle = null;
        try {
            craft = Class.forName("org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer");
            getHandle = craft.getMethod("getHandle");
        } catch (Throwable ignored) {
            // Non-1.8.8-R3 server: getPing returns -1 and callers fall back gracefully.
        }
        CRAFT_PLAYER = craft;
        GET_HANDLE = getHandle;
    }

    public static int getPing(Player p) {
        if (p == null || CRAFT_PLAYER == null || GET_HANDLE == null) return -1;
        try {
            if (!CRAFT_PLAYER.isInstance(p)) return -1;
            Object handle = GET_HANDLE.invoke(p);
            if (handle == null) return -1;
            Field ping = PING_FIELD;
            if (ping == null) {
                ping = handle.getClass().getField("ping");
                PING_FIELD = ping;
            }
            return ping.getInt(handle);
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
