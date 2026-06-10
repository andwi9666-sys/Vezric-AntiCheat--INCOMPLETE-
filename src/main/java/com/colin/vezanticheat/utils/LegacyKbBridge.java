package com.colin.vezanticheat.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;

public final class LegacyKbBridge {
    private static final String PLUGIN_NAME = "LegacyKB";
    private static final String MAIN_CLASS = "org.abyssmc.legacykb.LegacyKB";

    private static Plugin cachedPlugin;
    private static Field horizontalField;
    private static Field verticalField;
    private static Field verticalLimitField;
    private static Field extraHorizontalField;
    private static Field extraVerticalField;

    private LegacyKbBridge() {}

    public static boolean isAvailable() {
        return resolvePlugin() != null;
    }

    public static KnockbackProfile readProfile() {
        Plugin plugin = resolvePlugin();
        if (plugin == null) return null;

        Double horizontal = readDouble(plugin, horizontalField);
        Double vertical = readDouble(plugin, verticalField);
        Double verticalLimit = readDouble(plugin, verticalLimitField);
        Double extraHorizontal = readDouble(plugin, extraHorizontalField);
        Double extraVertical = readDouble(plugin, extraVerticalField);

        if (horizontal == null || vertical == null || verticalLimit == null
                || extraHorizontal == null || extraVertical == null) {
            return null;
        }

        return new KnockbackProfile(
                horizontal.doubleValue(),
                vertical.doubleValue(),
                verticalLimit.doubleValue(),
                extraHorizontal.doubleValue(),
                extraVertical.doubleValue()
        );
    }

    private static Plugin resolvePlugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) {
            clear();
            return null;
        }
        if (!(plugin.getClass().getName().equals(MAIN_CLASS))) {
            clear();
            return null;
        }

        if (plugin != cachedPlugin || horizontalField == null) {
            cachedPlugin = plugin;
            try {
                Class<?> type = plugin.getClass();
                horizontalField = open(type, "knockbackHorizontal");
                verticalField = open(type, "knockbackVertical");
                verticalLimitField = open(type, "knockbackVerticalLimit");
                extraHorizontalField = open(type, "knockbackExtraHorizontal");
                extraVerticalField = open(type, "knockbackExtraVertical");
            } catch (Throwable ignored) {
                clear();
                return null;
            }
        }

        return cachedPlugin;
    }

    private static Field open(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Double readDouble(Plugin plugin, Field field) {
        if (plugin == null || field == null) return null;
        try {
            Object value = field.get(plugin);
            return value instanceof Number ? Double.valueOf(((Number) value).doubleValue()) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void clear() {
        cachedPlugin = null;
        horizontalField = null;
        verticalField = null;
        verticalLimitField = null;
        extraHorizontalField = null;
        extraVerticalField = null;
    }

    public static final class KnockbackProfile {
        public final double horizontal;
        public final double vertical;
        public final double verticalLimit;
        public final double extraHorizontal;
        public final double extraVertical;

        public KnockbackProfile(double horizontal, double vertical, double verticalLimit,
                                double extraHorizontal, double extraVertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.verticalLimit = verticalLimit;
            this.extraHorizontal = extraHorizontal;
            this.extraVertical = extraVertical;
        }
    }
}
