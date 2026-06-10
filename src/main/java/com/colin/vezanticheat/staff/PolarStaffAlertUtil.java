package com.colin.vezanticheat.staff;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/** Polar-style staff alert formatting (Metadata / Player Info / Server Info). */
public final class PolarStaffAlertUtil {

    private PolarStaffAlertUtil() {}

    public static String happenedAgo(long timestampMs, long nowMs) {
        long seconds = Math.max(0L, (nowMs - timestampMs) / 1000L);
        if (seconds < 60L) return seconds + "s ago";
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + "m ago";
        return (minutes / 60L) + "h ago";
    }

    public static List<String> tooltipLore(PolarFlagRecord record, long nowMs, boolean includeDebug) {
        List<String> lore = new ArrayList<String>();
        lore.add(color("&6&lMetadata"));
        lore.add(color("&7Check: &f" + record.polarCheck));
        lore.add(color("&7Happened: &f" + happenedAgo(record.timestampMs, nowMs)));
        lore.add("");
        lore.add(color("&b&lPlayer Info"));
        lore.add(color("&7Data: &f- " + record.dataTier));
        lore.add(color("&7Ping: &f" + record.ping));
        lore.add(color("&7Brand: &f" + record.clientBrand));
        lore.add(color("&7Client: &f" + record.clientVersion));
        lore.add("");
        lore.add(color("&a&lServer Info"));
        lore.add(color("&7TPS: &f" + String.format("%.2f", record.tps)));
        lore.add(color("&7Server: &f" + record.serverName));
        if (includeDebug && record.debug != null && !record.debug.isEmpty()) {
            lore.add("");
            lore.add(color("&8" + record.debug));
        }
        return lore;
    }

    public static String[] verboseChatLines(PolarFlagRecord record, long nowMs, boolean includeDebug) {
        String[] lines = new String[includeDebug && record.debug != null && !record.debug.isEmpty() ? 4 : 3];
        lines[0] = color("&8  &6Metadata &7| &fCheck: &e" + record.polarCheck
                + " &7| &fHappened: &e" + happenedAgo(record.timestampMs, nowMs));
        lines[1] = color("&8  &bPlayer &7| &fData: &e" + record.dataTier
                + " &7| &fPing: &e" + record.ping
                + " &7| &fBrand: &e" + record.clientBrand
                + " &7| &fClient: &e" + record.clientVersion);
        lines[2] = color("&8  &aServer &7| &fTPS: &e" + String.format("%.2f", record.tps)
                + " &7| &fServer: &e" + record.serverName
                + " &7| &fVL: &e" + record.vl);
        if (lines.length > 3) {
            lines[3] = color("&8  &7" + record.debug);
        }
        return lines;
    }

    private static String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
