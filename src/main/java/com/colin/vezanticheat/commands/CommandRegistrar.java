package com.colin.vezanticheat.commands;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;

import java.util.logging.Level;

/**
 * Binds plugin.yml commands to executors and clears usage text so Bukkit/Paper does not
 * echo raw plugin.yml templates when dispatch fails.
 */
public final class CommandRegistrar {

    private CommandRegistrar() {}

    public static boolean bind(VezAntiCheat plugin, VezCommand vez, AlertsCommand alerts,
                            FlagsCommand flags, BanwaveCommand banwave) {
        boolean ok = true;
        ok &= bindOne(plugin, "vez", vez);
        ok &= bindOne(plugin, "alerts", alerts);
        ok &= bindOne(plugin, "flags", flags);
        ok &= bindOne(plugin, "banwave", banwave);
        if (ok) {
            plugin.getLogger().info("All Vez commands bound successfully.");
        } else {
            plugin.getLogger().warning("One or more Vez commands failed to bind — CommandFallbackListener will still dispatch.");
        }
        return ok;
    }

    private static boolean bindOne(VezAntiCheat plugin, String name, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().severe("Command /" + name + " missing from plugin.yml — command will not work.");
            return false;
        }
        command.setExecutor(safe(plugin, name, executor));
        command.setUsage("");
        command.setPermissionMessage(null);
        plugin.getLogger().info("Bound /" + name + " executor.");
        return true;
    }

    private static CommandExecutor safe(final VezAntiCheat plugin, final String name,
                                        final CommandExecutor delegate) {
        return new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                try {
                    return delegate.onCommand(sender, cmd, label, args);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Command /" + name + " failed", t);
                    sender.sendMessage(ChatColor.RED + "[Vez] Command failed. See console for details.");
                    return true;
                }
            }
        };
    }
}
