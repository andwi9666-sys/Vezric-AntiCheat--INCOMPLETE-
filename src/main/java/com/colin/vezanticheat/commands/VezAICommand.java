package com.colin.vezanticheat.commands;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

public final class VezAICommand {

    private final VezAntiCheat plugin;

    public VezAICommand(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vez.admin")) {
            sender.sendMessage(color(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(color("&0&l[PE&7RPLEX&8ION] &8AI &7&eUsage: /perplexion ai <score|export|reload> [player]"));
            return true;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("reload")) {
            if (plugin.riskScore() != null) plugin.riskScore().reload();
            sender.sendMessage(color("&0&l[PE&7RPLEX&8ION] &8AI &7&aReloaded AI layer."));
            return true;
        }

        if (sub.equals("export")) {
            File dir = plugin.evidence() == null ? null : plugin.evidence().getEvidenceDirectory();
            sender.sendMessage(color("&0&l[PE&7RPLEX&8ION] &8AI &7&eEvidence directory: &f"
                    + (dir == null ? "unavailable" : dir.getAbsolutePath())));
            return true;
        }

        if (sub.equals("score")) {
            if (args.length < 3) {
                sender.sendMessage(color("&0&l[PE&7RPLEX&8ION] &8AI &7&eUsage: /perplexion ai score <player>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(color("&cPlayer not found."));
                return true;
            }
            double score = plugin.riskScore() == null ? 0.0D : plugin.riskScore().getScore(target.getUniqueId());
            PlayerData data = plugin.data().get(target);
            int totalVl = data == null ? 0 : data.getTotalVl();
            sender.sendMessage(color("&0&l[PE&7RPLEX&8ION] &8AI &7&e" + target.getName()
                    + " &7risk=&f" + round2(score) + " &7totalVL=&f" + totalVl));
            return true;
        }

        sender.sendMessage(color("&0&l[PE&7RPLEX&8ION] &8AI &7&eUnknown subcommand."));
        return true;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private String round2(double value) {
        return String.valueOf(Math.round(value * 100.0D) / 100.0D);
    }
}
