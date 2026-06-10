package com.colin.vezanticheat.commands;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.banwave.BanwaveManager;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;

public class BanwaveCommand implements CommandExecutor {

    private final VezAntiCheat plugin;

    public BanwaveCommand(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String pref() {
        return c(plugin.getConfig().getString("prefix", "&6[Vez] &r"));
    }

    private void usage(CommandSender s) {
        s.sendMessage(pref() + c("&e/banwave add <player>"));
        s.sendMessage(pref() + c("&e/banwave remove <player>"));
        s.sendMessage(pref() + c("&e/banwave list"));
        s.sendMessage(pref() + c("&e/banwave push <perm|temp> [time] [timeform]"));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {

        if (!s.hasPermission("vez.admin")) {
            s.sendMessage(pref() + c("&cNo permission."));
            return true;
        }

        if (args.length == 0) {
            usage(s);
            return true;
        }

        BanwaveManager bw = plugin.getBanwaveManager();

        String sub = args[0].toLowerCase();

        if (sub.equals("add")) {
            if (args.length < 2) {
                usage(s);
                return true;
            }
            String name = args[1];
            boolean added = bw.addPlayer(name);
            s.sendMessage(pref() + c(added ? "&aAdded &f" + name + " &ato the banwave." : "&e" + name + " &7was already in the banwave."));
            return true;
        }

        if (sub.equals("remove")) {
            if (args.length < 2) {
                usage(s);
                return true;
            }
            String name = args[1];
            boolean removed = bw.removePlayer(name);
            s.sendMessage(pref() + c(removed ? "&aRemoved &f" + name + " &afrom the banwave." : "&c" + name + " &7was not in the banwave."));
            return true;
        }

        if (sub.equals("list")) {
            List<String> names = bw.listNames();
            s.sendMessage(pref() + c("&eBanwave players &7(" + names.size() + "):"));
            if (names.isEmpty()) {
                s.sendMessage(pref() + c("&7- &f(none)"));
                return true;
            }
            for (String n : names) {
                s.sendMessage(pref() + c("&7- &f" + n));
            }
            return true;
        }

        if (sub.equals("push")) {
            if (args.length < 2) {
                usage(s);
                return true;
            }

            String mode = args[1].toLowerCase(); // perm / temp
            if (!mode.equals("perm") && !mode.equals("temp")) {
                usage(s);
                return true;
            }

            Integer time = null;
            String timeform = null;

            if (mode.equals("temp")) {
                if (args.length < 4) {
                    s.sendMessage(pref() + c("&cFor temp banwave you must do: &e/banwave push temp <time> <timeform>"));
                    return true;
                }
                try {
                    time = Integer.parseInt(args[2]);
                } catch (Exception ex) {
                    s.sendMessage(pref() + c("&cTime must be a number."));
                    return true;
                }
                timeform = args[3].toLowerCase();
                if (!isValidTimeform(timeform)) {
                    s.sendMessage(pref() + c("&cInvalid timeform. Use: second, minute, hour, day, week, month, year"));
                    return true;
                }
            }

            List<UUID> targets = bw.listUuids();
            if (targets.isEmpty()) {
                s.sendMessage(pref() + c("&cBanwave is empty."));
                return true;
            }

            int success = plugin.getPunishmentManager().pushBanwave(s, mode, time, timeform);

            s.sendMessage(pref() + c("&aPushed banwave. &7Processed: &f" + targets.size() + " &7| Banned: &f" + success));

            // clear after push
            bw.clear();
            s.sendMessage(pref() + c("&7Banwave list cleared."));
            return true;
        }

        usage(s);
        return true;
    }

    private boolean isValidTimeform(String s) {
        return s.equals("second") || s.equals("minute") || s.equals("hour")
                || s.equals("day") || s.equals("week") || s.equals("month") || s.equals("year");
    }
}
