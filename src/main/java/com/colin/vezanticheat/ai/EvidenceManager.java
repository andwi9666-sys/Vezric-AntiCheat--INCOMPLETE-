package com.colin.vezanticheat.ai;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Append-only CSV evidence export for flagged events.
 */
public final class EvidenceManager {

    private final VezAntiCheat plugin;
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public EvidenceManager(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public synchronized void append(Player player, String check, String category, double vl,
                                    int ping, double offset, String debug, long timestampMs) {
        if (player == null || !plugin.getConfig().getBoolean("ai.evidence.enabled", true)) return;

        File dir = new File(plugin.getDataFolder(), "evidence");
        if (!dir.exists() && !dir.mkdirs()) return;

        String day = dayFormat.format(new Date(timestampMs));
        File file = new File(dir, day + ".csv");
        boolean writeHeader = !file.exists();

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file, true));
            if (writeHeader) {
                writer.write("uuid,check,category,vl,ping,offset,debug,timestamp");
                writer.newLine();
            }
            writer.write(csv(player.getUniqueId()) + ","
                    + csv(check) + ","
                    + csv(category) + ","
                    + r(vl) + ","
                    + ping + ","
                    + r(offset) + ","
                    + csv(debug) + ","
                    + csv(timeFormat.format(new Date(timestampMs))));
            writer.newLine();
        } catch (IOException ignored) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public File getEvidenceDirectory() {
        return new File(plugin.getDataFolder(), "evidence");
    }

    private String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String csv(UUID uuid) {
        return uuid == null ? "" : uuid.toString();
    }

    private String r(double value) {
        return String.valueOf(Math.round(value * 1000.0D) / 1000.0D);
    }
}
