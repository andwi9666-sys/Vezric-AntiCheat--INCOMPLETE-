package com.colin.vezanticheat.banwave;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BanwaveManager {

    private final VezAntiCheat plugin;
    private final File file;
    private YamlConfiguration yml;
    private final Map<UUID, Entry> entries = new LinkedHashMap<UUID, Entry>();
    private final Random random = new Random();

    public BanwaveManager(VezAntiCheat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "banwave.yml");
        load();
    }

    private void load() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!file.exists()) file.createNewFile();
        } catch (Exception ignored) {}

        yml = YamlConfiguration.loadConfiguration(file);
        entries.clear();

        ConfigurationSection section = yml.getConfigurationSection("entries");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node == null) continue;
                entries.put(uuid, new Entry(
                        uuid,
                        node.getString("name", key),
                        node.getString("sourceCheck", "AUTO"),
                        node.getString("category", "MISC"),
                        node.getString("reason", plugin.cfg().watchdogReason("MISC")),
                        node.getLong("queuedAt", System.currentTimeMillis()),
                        node.getLong("executeAt", System.currentTimeMillis()),
                        node.getBoolean("manual", false)
                ));
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        yml.set("entries", null);
        for (Entry entry : entries.values()) {
            String base = "entries." + entry.uuid.toString();
            yml.set(base + ".name", entry.name);
            yml.set(base + ".sourceCheck", entry.sourceCheck);
            yml.set(base + ".category", entry.category);
            yml.set(base + ".reason", entry.reason);
            yml.set(base + ".queuedAt", entry.queuedAt);
            yml.set(base + ".executeAt", entry.executeAt);
            yml.set(base + ".manual", entry.manual);
        }

        try {
            yml.save(file);
        } catch (Exception ignored) {}
    }

    public void startAutoTask() {
        if (!plugin.cfg().punishEnabled()) return;
        if (!plugin.cfg().autoBanwaveEnabled()) return;

        long periodTicks = Math.max(20L, plugin.cfg().autoBanwaveIntervalSeconds() * 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                pushDue(Bukkit.getConsoleSender(), false, null, null, null);
            }
        }, periodTicks, periodTicks);
    }

    public long nextAutoDelayMs() {
        long min = plugin.cfg().autoBanwaveMinDelaySeconds() * 1000L;
        long max = plugin.cfg().autoBanwaveMaxDelaySeconds() * 1000L;
        if (max <= min) return min;
        long spread = max - min;
        return min + Math.abs(random.nextLong()) % (spread + 1L);
    }

    public synchronized boolean queueAuto(Player player, String sourceCheck, String category, String reason, long delayMs) {
        if (player == null) return false;
        return upsertEntry(new Entry(
                player.getUniqueId(),
                player.getName(),
                sourceCheck,
                category,
                reason,
                System.currentTimeMillis(),
                System.currentTimeMillis() + Math.max(0L, delayMs),
                false
        ), false);
    }

    public synchronized boolean addPlayer(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op == null || op.getUniqueId() == null) return false;

        String display = op.getName() != null ? op.getName() : name;
        return upsertEntry(new Entry(
                op.getUniqueId(),
                display,
                "MANUAL",
                "MISC",
                plugin.cfg().watchdogReason("MISC"),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                true
        ), true);
    }

    private boolean upsertEntry(Entry entry, boolean treatExistingAsNoop) {
        Entry existing = entries.get(entry.uuid);
        if (existing != null && treatExistingAsNoop) {
            return false;
        }

        if (existing != null) {
            existing.name = entry.name;
            existing.sourceCheck = entry.sourceCheck;
            existing.category = entry.category;
            existing.reason = entry.reason;
            existing.manual = existing.manual || entry.manual;
            if (entry.executeAt < existing.executeAt) {
                existing.executeAt = entry.executeAt;
            }
            save();
            return false;
        }

        entries.put(entry.uuid, entry);
        save();
        return true;
    }

    public synchronized boolean removePlayer(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op == null || op.getUniqueId() == null) return false;
        boolean removed = entries.remove(op.getUniqueId()) != null;
        if (removed) save();
        return removed;
    }

    public synchronized List<String> listNames() {
        List<String> out = new ArrayList<String>();
        for (Entry entry : entries.values()) out.add(entry.name);
        return out;
    }

    public synchronized List<UUID> listUuids() {
        return new ArrayList<UUID>(entries.keySet());
    }

    public synchronized boolean isQueued(UUID uuid) {
        return uuid != null && entries.containsKey(uuid);
    }

    public synchronized boolean removeUuid(UUID uuid) {
        if (uuid == null) return false;
        boolean removed = entries.remove(uuid) != null;
        if (removed) save();
        return removed;
    }

    public synchronized void clear() {
        entries.clear();
        save();
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized String nameOf(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry != null ? entry.name : (uuid == null ? "" : uuid.toString());
    }

    public synchronized int pushDue(CommandSender sender, boolean forceAll,
                                    String overrideType, Integer overrideTime, String overrideTimeform) {
        if (entries.isEmpty()) return 0;

        long now = System.currentTimeMillis();
        int processed = 0;
        int batchSize = forceAll ? Integer.MAX_VALUE : plugin.cfg().autoBanwaveBatchSize();
        List<UUID> order = new ArrayList<UUID>(entries.keySet());

        for (UUID uuid : order) {
            if (processed >= batchSize) break;

            Entry entry = entries.get(uuid);
            if (entry == null) continue;
            if (!forceAll && entry.executeAt > now) continue;

            boolean ok = plugin.getPunishmentManager().executeQueuedPunishment(
                    entry, overrideType, overrideTime, overrideTimeform, sender != null);
            if (ok) {
                entries.remove(uuid);
                processed++;
            }
        }

        if (processed > 0) {
            save();
        }
        return processed;
    }

    public synchronized List<Entry> snapshotEntries() {
        return Collections.unmodifiableList(new ArrayList<Entry>(entries.values()));
    }

    public static final class Entry {
        private final UUID uuid;
        private String name;
        private String sourceCheck;
        private String category;
        private String reason;
        private final long queuedAt;
        private long executeAt;
        private boolean manual;

        public Entry(UUID uuid, String name, String sourceCheck, String category,
                     String reason, long queuedAt, long executeAt, boolean manual) {
            this.uuid = uuid;
            this.name = name;
            this.sourceCheck = sourceCheck;
            this.category = category;
            this.reason = reason;
            this.queuedAt = queuedAt;
            this.executeAt = executeAt;
            this.manual = manual;
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public String getSourceCheck() { return sourceCheck; }
        public String getCategory() { return category; }
        public String getReason() { return reason; }
        public long getQueuedAt() { return queuedAt; }
        public long getExecuteAt() { return executeAt; }
        public boolean isManual() { return manual; }
    }
}
