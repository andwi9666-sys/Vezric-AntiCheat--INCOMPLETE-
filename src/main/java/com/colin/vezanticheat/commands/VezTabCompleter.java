package com.colin.vezanticheat.commands;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Tab completion for /perplexion (and aliases /pe, /vez). */
public final class VezTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "status", "checks", "perf", "recommendations", "info", "movement", "trace", "tune",
            "exportdebug", "profile", "verbose", "debug", "reload", "on", "off",
            "announce", "ai", "combat");
    private static final List<String> PROFILES = Arrays.asList("lenient", "balanced", "aggressive");
    private static final List<String> TIERS = Arrays.asList(
            "CHARACTERISTICS", "PRISM", "SIMULATION", "PREDICTION");
    private static final List<String> TOGGLES = Arrays.asList("on", "off");

    private final VezAntiCheat plugin;

    public VezTabCompleter(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("profile")) return filter(PROFILES, args[1]);
            if (sub.equals("checks")) return filter(TIERS, args[1]);
            if (sub.equals("verbose") || sub.equals("debug")) return filter(TOGGLES, args[1]);
            if (sub.equals("info") || sub.equals("movement") || sub.equals("trace") || sub.equals("exportdebug")) {
                return playerNames(args[1]);
            }
            if (sub.equals("tune")) return filter(checkNames(), args[1]);
        }
        if (args.length == 3 && sub.equals("tune")) {
            // Keys vary per check; suggest the common ones every check honors.
            return filter(Arrays.asList("enabled", "shadow", "bufferToFlag", "punishVl", "decay"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> checkNames() {
        List<String> names = new ArrayList<String>();
        for (TierCheck check : plugin.tierChecks().registry().all()) {
            names.add(check.name());
        }
        return names;
    }

    private List<String> playerNames(String prefix) {
        List<String> names = new ArrayList<String>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return filter(names, prefix);
    }

    private static List<String> filter(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) return new ArrayList<String>(options);
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(option);
        }
        return out;
    }
}
