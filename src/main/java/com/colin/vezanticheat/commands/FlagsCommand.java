package com.colin.vezanticheat.commands;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.staff.FlagsGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FlagsCommand implements CommandExecutor {

    private final VezAntiCheat plugin;

    public FlagsCommand(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;
        if (!p.hasPermission("vez.staff")) {
            p.sendMessage(c("&cYou do not have permission."));
            return true;
        }

        if (args.length == 1) {
            String sub = args[0].toLowerCase();
            PlayerData data = plugin.getDataManager().get(p);
            if (sub.equals("on")) {
                data.setFlagsEnabled(true);
                p.sendMessage(c(plugin.getConfig().getString("messages.flags-on",
                        "{prefix}&aAlerts enabled.").replace("{prefix}", plugin.cfg().prefix())));
                return true;
            }
            if (sub.equals("off")) {
                data.setFlagsEnabled(false);
                p.sendMessage(c(plugin.getConfig().getString("messages.flags-off",
                        "{prefix}&cAlerts disabled.").replace("{prefix}", plugin.cfg().prefix())));
                return true;
            }
        }

        FlagsGui.open(plugin, p);
        return true;
    }
}
