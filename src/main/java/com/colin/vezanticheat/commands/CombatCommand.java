package com.colin.vezanticheat.commands;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.combat.CombatEvidence;
import com.colin.vezanticheat.combat.CombatHitClassification;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Staff debug output for per-player combat evidence (/vez combat &lt;player&gt;).
 */
public final class CombatCommand {

    private final VezAntiCheat plugin;

    public CombatCommand(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("watchdog.staff")) {
            sender.sendMessage(color(plugin.getConfig().getString("messages.no-permission",
                    "{prefix}&cNo permission.").replace("{prefix}",
                    plugin.getConfig().getString("prefix", "&6[WatchDog] "))));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(color("&6[WatchDog] &eUsage: /watchdog combat <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(color("&6[WatchDog] &cPlayer not found or offline: &f" + args[0]));
            return true;
        }

        if (plugin.combat() == null) {
            sender.sendMessage(color("&6[WatchDog] &cCombat analyzer is not available."));
            return true;
        }

        CombatEvidence evidence = plugin.combat().getEvidence(target.getUniqueId());
        if (evidence == null) {
            sender.sendMessage(color("&6[WatchDog] &cNo combat evidence recorded for &f" + target.getName() + "&c."));
            return true;
        }

        sender.sendMessage(color("&6[WatchDog] &eCombat evidence for &f" + target.getName()));
        sender.sendMessage(color("&7Buffer: &f" + format(evidence.getBuffer())));
        sender.sendMessage(color("&7Classifications (&f" + evidence.getTotalSamples() + "&7): &f"
                + summarizeClassifications(evidence.getRecentClassifications())));
        sender.sendMessage(color("&7Clean: &f" + formatPercent(evidence.getCleanPercentage())
                + " &7Lenient: &f" + formatPercent(evidence.getLenientPercentage())
                + " &7Very lenient: &f" + formatPercent(evidence.getVeryLenientPercentage())));
        sender.sendMessage(color("&7Bad: &f" + formatPercent(evidence.getBadPercentage())
                + " &7Impossible: &f" + formatPercent(evidence.getImpossiblePercentage())));
        sender.sendMessage(color("&7Expansion shell: &f" + formatPercent(evidence.getExpansionShellHitRatio() * 100.0D)
                + " &7Edge: &f" + formatPercent(evidence.getEdgeHitRatio() * 100.0D)
                + " &7Center-like: &f" + formatPercent(evidence.getCenterLikeHitRatio() * 100.0D)));

        List<String> reasons = evidence.getLastSuspiciousReasons(5);
        if (reasons.isEmpty()) {
            sender.sendMessage(color("&7Recent suspicious reasons: &8(none)"));
        } else {
            sender.sendMessage(color("&7Recent suspicious reasons:"));
            for (String reason : reasons) {
                sender.sendMessage(color("&8- &f" + reason));
            }
        }
        return true;
    }

    private static String summarizeClassifications(List<CombatHitClassification> classifications) {
        if (classifications == null || classifications.isEmpty()) {
            return "(none)";
        }

        Map<CombatHitClassification, Integer> counts = new HashMap<CombatHitClassification, Integer>();
        for (CombatHitClassification classification : classifications) {
            if (classification == null) {
                continue;
            }
            Integer count = counts.get(classification);
            counts.put(classification, count == null ? 1 : count + 1);
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<CombatHitClassification, Integer> entry : counts.entrySet()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(entry.getKey().name()).append('x').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
