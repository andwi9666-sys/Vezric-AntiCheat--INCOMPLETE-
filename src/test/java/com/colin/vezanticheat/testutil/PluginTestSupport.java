package com.colin.vezanticheat.testutil;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.plugin.java.JavaPlugin;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;

/**
 * JavaPlugin.getDataFolder() is final in Spigot 1.8.8 and this project uses
 * mockito-core (no inline mock maker), so the data folder cannot be stubbed.
 * Instead the private field behind the final getter is set reflectively on the
 * mock instance, which the real getter then serves.
 */
public final class PluginTestSupport {

    private PluginTestSupport() {}

    public static VezAntiCheat pluginWithDataFolder(File dataFolder) {
        VezAntiCheat plugin = Mockito.mock(VezAntiCheat.class);
        try {
            Field field = JavaPlugin.class.getDeclaredField("dataFolder");
            field.setAccessible(true);
            field.set(plugin, dataFolder);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not inject dataFolder", ex);
        }
        return plugin;
    }
}
