package com.colin.vezanticheat.commands;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AlertsCommand implements CommandExecutor {

    private final VezAntiCheat plugin;

    public AlertsCommand(VezAntiCheat plugin) {
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

        PlayerData data = plugin.getDataManager().get(p);

        if (args.length == 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("on")) {
                data.setFlagsEnabled(true);
                p.sendMessage(c("&0&l[PE&7RPLEX&8ION] &aAlerts enabled."));
                return true;
            }
            if (sub.equals("off")) {
                data.setFlagsEnabled(false);
                p.sendMessage(c("&0&l[PE&7RPLEX&8ION] &cAlerts disabled."));
                return true;
            }
        }

        p.sendMessage(c("&0&l[PE&7RPLEX&8ION] &eUsage: /alerts <on|off>"));
        return true;
    }
}
