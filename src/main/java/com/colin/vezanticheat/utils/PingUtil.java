package com.colin.vezanticheat.utils;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class PingUtil {
    private PingUtil() {}

    public static int getPing(Player p) {
        if (p == null) return -1;

        // 1.8.8 CraftPlayer handle ping (reflection so it compiles cleanly)
        try {
            Class<?> craft = Class.forName("org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer");
            if (!craft.isInstance(p)) return -1;

            Method getHandle = craft.getMethod("getHandle");
            Object handle = getHandle.invoke(p);
            if (handle == null) return -1;

            Field ping = handle.getClass().getField("ping");
            return ping.getInt(handle);
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
