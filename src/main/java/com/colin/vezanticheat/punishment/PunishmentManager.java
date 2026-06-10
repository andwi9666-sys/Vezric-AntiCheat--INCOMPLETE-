package com.colin.vezanticheat.punishment;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.banwave.BanwaveManager;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CheckAliasUtil;
import com.colin.vezanticheat.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PunishmentManager {

    private final VezAntiCheat plugin;
    private final File file;
    private YamlConfiguration yml;
    private final Map<UUID, EvidenceState> evidenceStates = new HashMap<UUID, EvidenceState>();
    private final Map<UUID, BlatantIncidentState> blatantBedNukerStates = new HashMap<UUID, BlatantIncidentState>();
    private final Deque<Long> recentPunishmentTimes = new ArrayDeque<Long>();
    private BukkitTask announcementTask;

    public PunishmentManager(VezAntiCheat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "punishments.yml");
        load();
    }

    private void load() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!file.exists()) file.createNewFile();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "PunishmentManager: could not create punishments.yml: " + ex.getMessage(), ex);
        }
        yml = YamlConfiguration.loadConfiguration(file);
        recentPunishmentTimes.clear();
        List<Long> stored = yml.getLongList("recent-punishments");
        long cutoff = System.currentTimeMillis() - (24L * 60L * 60L * 1000L);
        for (Long time : stored) {
            if (time == null) continue;
            if (time.longValue() >= cutoff) {
                recentPunishmentTimes.addLast(time.longValue());
            }
        }
    }

    private void save() {
        trimRecentPunishments(System.currentTimeMillis());
        yml.set("recent-punishments", new ArrayList<Long>(recentPunishmentTimes));
        try {
            yml.save(file);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "PunishmentManager: failed to save punishments.yml: " + ex.getMessage(), ex);
        }
    }

    public void startAnnouncementTask() {
        if (announcementTask != null) {
            announcementTask.cancel();
        }
        long periodTicks = 60L * 60L * 20L;
        announcementTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                broadcastWatchdogAnnouncement();
            }
        }, periodTicks, periodTicks);
    }

    public void broadcastWatchdogAnnouncement() {
        long now = System.currentTimeMillis();
        trimRecentPunishments(now);
        int count = recentPunishmentTimes.size();
        String message = ChatColor.DARK_RED + "[WatchDogAnnouncement] "
                + ChatColor.WHITE + "WatchDog AntiCheat has banned over "
                + count
                + " within the past 24 hours! Please refrain from "
                + ChatColor.DARK_RED + "cheating"
                + ChatColor.WHITE + " to keep our enviornment safe, secure, and happy. If you notice somebody cheating, please report them using /wdr, /report, or /watchdogreport.";
        Bukkit.broadcastMessage(message);
    }

    public int getRecentPunishmentCount24Hours() {
        trimRecentPunishments(System.currentTimeMillis());
        return recentPunishmentTimes.size();
    }

    public int getBanCount(UUID uuid) {
        if (uuid == null) return 0;
        return yml.getInt("bans." + uuid.toString(), 0);
    }

    public void setBanCount(UUID uuid, int count) {
        if (uuid == null) return;
        yml.set("bans." + uuid.toString(), count);
        save();
    }

    public int addBanCount(UUID uuid) {
        int next = getBanCount(uuid) + 1;
        setBanCount(uuid, next);
        return next;
    }

    public long getMarkTime(UUID uuid) {
        if (uuid == null) return 0L;
        return yml.getLong("marks." + uuid.toString(), 0L);
    }

    public void setMarkTime(UUID uuid, long when) {
        if (uuid == null) return;
        yml.set("marks." + uuid.toString(), when);
        save();
    }

    public void clearMark(UUID uuid) {
        if (uuid == null) return;
        yml.set("marks." + uuid.toString(), null);
        save();
    }

    public boolean handleViolation(Player player, PlayerData data, String checkName, String category, double checkVl) {
        if (player == null || data == null) return false;
        if (!plugin.cfg().punishEnabled()) return false;
        if (PlayerData.bypass(player) || player.isOp()) return false;

        String publicCheckName = CheckAliasUtil.displayName(checkName, category);
        String publicCategory = CheckAliasUtil.displayCategory(checkName, category);

        UUID uuid = player.getUniqueId();
        BanwaveManager banwave = plugin.getBanwaveManager();

        long now = System.currentTimeMillis();
        EvidenceState state = evidenceStates.get(uuid);
        if (state == null) {
            state = new EvidenceState();
            evidenceStates.put(uuid, state);
        }
        state.record(now, checkName);

        EvidenceSnapshot snapshot = buildEvidenceSnapshot(data, publicCategory, checkName, checkVl, state, now);
        if (snapshot.markScore >= plugin.cfg().punishMarkScore() && getMarkTime(uuid) <= 0L) {
            setMarkTime(uuid, now);
        }

        if (shouldExecuteHybridImmediate(player, data, publicCheckName, publicCategory, checkVl, snapshot, state, now)) {
            if (banwave != null) {
                banwave.removeUuid(uuid);
            }
            return executeImmediate(player, publicCategory, null, null, null, publicCheckName);
        }

        if (banwave != null && banwave.isQueued(uuid)) return false;

        boolean marked = getMarkTime(uuid) > 0L;
        if (!marked) return false;
        if (snapshot.queueScore < plugin.cfg().punishQueueScore()) return false;

        if ("IMMEDIATE".equals(plugin.cfg().punishExecutionType())) {
            return executeImmediate(player, publicCategory, null, null, null, publicCheckName);
        }

        if (banwave == null) return false;

        long delayMs = banwave.nextAutoDelayMs();
        String reason = plugin.cfg().watchdogReason(publicCategory);
        boolean queued = banwave.queueAuto(player, publicCheckName, publicCategory, reason, delayMs);
        if (queued) {
            state.lastQueueMs = now;
        }
        return queued;
    }

    private boolean shouldExecuteHybridImmediate(Player player, PlayerData data, String checkName, String category,
                                                 double checkVl, EvidenceSnapshot snapshot, EvidenceState state, long now) {
        if (player == null || data == null || state == null || snapshot == null) return false;
        if (!plugin.cfg().hybridImmediateEnabled()) return false;

        long windowMs = plugin.cfg().hybridImmediateWindowSeconds() * 1000L;
        int recentFlags = state.countRecent(now, windowMs);
        if (recentFlags < plugin.cfg().hybridImmediateMinFlags()) return false;
        if (data.getTotalVl() < plugin.cfg().hybridImmediateVl()) return false;

        ConfigManager.ConfidenceProfile profile = plugin.cfg().confidenceProfileFor(checkName, category);
        boolean highConfidence = profile.baseScore >= 4.0D;
        boolean severeCheckVl = checkVl >= plugin.cfg().hybridImmediateCheckVl();
        boolean severeQueueScore = snapshot.queueScore >= plugin.cfg().punishQueueScore() + 3.0D;
        boolean extremeBurst = data.getTotalVl() >= (plugin.cfg().hybridImmediateVl() * 2)
                && recentFlags >= Math.max(plugin.cfg().hybridImmediateMinFlags(), 6);
        boolean enoughHotChecks = snapshot.hotChecks >= plugin.cfg().hybridImmediateMinHotChecks();

        return severeQueueScore
                || extremeBurst
                || (highConfidence && severeCheckVl && enoughHotChecks);
    }

    public boolean executeQueuedPunishment(BanwaveManager.Entry entry, String overrideType,
                                           Integer overrideTime, String overrideTimeform, boolean announceToSender) {
        if (entry == null || entry.getUuid() == null) return false;
        if (!plugin.cfg().punishEnabled()) return false;

        OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getUuid());
        String playerName = entry.getName();
        if ((playerName == null || playerName.trim().isEmpty()) && offline != null && offline.getName() != null) {
            playerName = offline.getName();
        }
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }

        int stage = Math.min(getBanCount(entry.getUuid()) + 1, plugin.cfg().punishMaxStage());
        String ladderType = plugin.cfg().ladderType(stage).toUpperCase(Locale.ROOT);
        String ladderDuration = plugin.cfg().ladderDuration(stage);
        String reason = entry.getReason() != null && !entry.getReason().trim().isEmpty()
                ? entry.getReason()
                : plugin.cfg().watchdogReason(entry.getCategory());

        String effectiveType = ladderType;
        Integer effectiveTime = null;
        String effectiveTimeform = null;
        if (overrideType != null && !overrideType.trim().isEmpty()) {
            effectiveType = overrideType.toUpperCase(Locale.ROOT);
        }
        if (overrideTime != null && overrideTimeform != null) {
            effectiveTime = overrideTime;
            effectiveTimeform = overrideTimeform.toLowerCase(Locale.ROOT);
        }

        if (!"PERM".equals(ladderType) && effectiveTime == null) {
            TimeSpec ts = parseDuration(ladderDuration);
            if (ts == null) ts = new TimeSpec(30, "day");
            effectiveTime = ts.time;
            effectiveTimeform = ts.timeform;
        }

        Player online = Bukkit.getPlayer(entry.getUuid());
        if (online != null && (PlayerData.bypass(online) || online.isOp())) {
            return false;
        }

        if (online != null) {
            online.kickPlayer(renderBanScreen("PERM".equals(effectiveType), effectiveTime, effectiveTimeform, reason));
        }

        dispatchPunishCommand(playerName, reason, effectiveType, effectiveTime, effectiveTimeform, ladderDuration);
        recordPunishment(System.currentTimeMillis());
        addBanCount(entry.getUuid());
        clearMark(entry.getUuid());
        evidenceStates.remove(entry.getUuid());
        broadcastWatchdogPunishment(playerName);
        return true;
    }

    public boolean executeImmediate(Player player, String category, String overrideType,
                                    Integer overrideTime, String overrideTimeform, String sourceCheck) {
        if (player == null) return false;

        BanwaveManager.Entry entry = new BanwaveManager.Entry(
                player.getUniqueId(),
                player.getName(),
                sourceCheck == null ? "AUTO" : sourceCheck,
                category,
                plugin.cfg().watchdogReason(category),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false
        );
        return executeQueuedPunishment(entry, overrideType, overrideTime, overrideTimeform, false);
    }

    public boolean handleBlatantBedNukerIncident(Player player, PlayerData data, String sourceCheck, String debug) {
        if (player == null || data == null) return false;
        if (!plugin.cfg().punishEnabled()) return false;
        if (!plugin.cfg().blatantBedNukerEnabled()) return false;
        if (PlayerData.bypass(player) || player.isOp()) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        BlatantIncidentState state = blatantBedNukerStates.get(uuid);
        if (state == null) {
            state = new BlatantIncidentState();
            blatantBedNukerStates.put(uuid, state);
        }

        long dedupeMs = plugin.cfg().blatantBedNukerDedupeMs();
        if (state.lastIncidentMs > 0L && (now - state.lastIncidentMs) < dedupeMs) {
            return false;
        }

        long windowMs = plugin.cfg().blatantBedNukerWindowSeconds() * 1000L;
        state.lastIncidentMs = now;
        state.timestamps.addLast(now);
        while (!state.timestamps.isEmpty()) {
            Long first = state.timestamps.peekFirst();
            if (first == null || (now - first.longValue()) <= windowMs) break;
            state.timestamps.removeFirst();
        }

        setMarkTime(uuid, now);

        if (state.timestamps.size() >= plugin.cfg().blatantBedNukerIncidents()) {
            BanwaveManager banwave = plugin.getBanwaveManager();
            if (banwave != null) {
                banwave.removeUuid(uuid);
            }
            blatantBedNukerStates.remove(uuid);
            return executeImmediate(player, "NUKER", null, null, null, sourceCheck);
        }
        return false;
    }

    private void dispatchPunishCommand(String playerName, String reason, String effectiveType,
                                       Integer effectiveTime, String effectiveTimeform, String ladderDuration) {
        if ("PERM".equals(effectiveType)) {
            String cmd = plugin.cfg().punishCmdPerm()
                    .replace("{player}", playerName)
                    .replace("{reason}", reason);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }

        String cmd = plugin.cfg().punishCmdTemp()
                .replace("{player}", playerName)
                .replace("{time}", String.valueOf(effectiveTime == null ? 30 : effectiveTime))
                .replace("{timeform}", effectiveTimeform == null ? "day" : effectiveTimeform)
                .replace("{duration}", ladderDuration == null ? "30d" : ladderDuration)
                .replace("{reason}", reason);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void broadcastWatchdogPunishment(String playerName) {
        String broadcast = plugin.cfg().watchdogBroadcast().replace("{player}", playerName);
        if (broadcast != null && !broadcast.trim().isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcast));
        }

        String remove = plugin.cfg().watchdogRemoveMessage();
        if (remove != null && !remove.trim().isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', remove));
        }
    }

    private String renderBanScreen(boolean permanent, Integer time, String timeform, String reason) {
        String line1;
        if (permanent) {
            line1 = "&cYou are permanently banned from this server!";
        } else {
            line1 = "&cYou are temporarily banned for " + (time == null ? 30 : time) + " "
                    + normalizeTimeForm(timeform, time) + " from this server!";
        }

        return ChatColor.translateAlternateColorCodes('&',
                line1
                        + "\n&7Reason: &f" + reason
                        + "\n&7Find out more: &b" + plugin.cfg().watchdogAppealUrl());
    }

    private String normalizeTimeForm(String timeform, Integer time) {
        String unit = (timeform == null || timeform.trim().isEmpty()) ? "day" : timeform.toLowerCase(Locale.ROOT);
        if (time != null && time == 1) return unit;
        if (unit.endsWith("s")) return unit;
        return unit + "s";
    }

    private void recordPunishment(long now) {
        recentPunishmentTimes.addLast(now);
        trimRecentPunishments(now);
        save();
    }

    private void trimRecentPunishments(long now) {
        long cutoff = now - (24L * 60L * 60L * 1000L);
        while (!recentPunishmentTimes.isEmpty()) {
            Long first = recentPunishmentTimes.peekFirst();
            if (first == null || first.longValue() >= cutoff) break;
            recentPunishmentTimes.removeFirst();
        }
    }

    public boolean punishBanwave(String playerName, String overrideType, Integer overrideTime, String overrideTimeform) {
        if (playerName == null || playerName.trim().isEmpty()) return false;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offline != null ? offline.getUniqueId() : null;
        if (uuid == null) return false;

        BanwaveManager.Entry entry = new BanwaveManager.Entry(
                uuid,
                offline != null && offline.getName() != null ? offline.getName() : playerName,
                "MANUAL",
                "MISC",
                plugin.cfg().watchdogReason("MISC"),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                true
        );
        return executeQueuedPunishment(entry, overrideType, overrideTime, overrideTimeform, true);
    }

    public int pushBanwave(CommandSender sender, String overrideType, Integer overrideTime, String overrideTimeform) {
        BanwaveManager banwave = plugin.getBanwaveManager();
        if (banwave == null) return 0;
        return banwave.pushDue(sender, true, overrideType, overrideTime, overrideTimeform);
    }

    public static final class TimeSpec {
        public final int time;
        public final String timeform;

        public TimeSpec(int time, String timeform) {
            this.time = time;
            this.timeform = timeform;
        }
    }

    public TimeSpec parseDuration(String d) {
        if (d == null) return null;
        String s = d.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty()) return null;

        int idx = 0;
        while (idx < s.length() && Character.isDigit(s.charAt(idx))) idx++;
        if (idx == 0) return null;

        int num;
        try {
            num = Integer.parseInt(s.substring(0, idx));
        } catch (Exception ex) {
            return null;
        }

        String unit = s.substring(idx);
        if (unit.equals("s") || unit.equals("sec") || unit.equals("second") || unit.equals("seconds")) return new TimeSpec(num, "second");
        if (unit.equals("m") || unit.equals("min") || unit.equals("minute") || unit.equals("minutes")) return new TimeSpec(num, "minute");
        if (unit.equals("h") || unit.equals("hr") || unit.equals("hour") || unit.equals("hours")) return new TimeSpec(num, "hour");
        if (unit.equals("d") || unit.equals("day") || unit.equals("days")) return new TimeSpec(num, "day");
        if (unit.equals("w") || unit.equals("wk") || unit.equals("week") || unit.equals("weeks")) return new TimeSpec(num, "week");
        if (unit.equals("mo") || unit.equals("mon") || unit.equals("month") || unit.equals("months")) return new TimeSpec(num, "month");
        if (unit.equals("y") || unit.equals("yr") || unit.equals("year") || unit.equals("years")) return new TimeSpec(num, "year");
        return null;
    }

    private EvidenceSnapshot buildEvidenceSnapshot(PlayerData data, String category, String checkName,
                                                   double checkVl, EvidenceState state, long now) {
        Map<String, Double> snapshot = data.snapshotCheckVl();
        snapshot.put(checkName, Math.max(checkVl, data.getCheckVl(checkName)));

        double score = 0.0;
        int hotChecks = 0;
        for (Map.Entry<String, Double> entry : snapshot.entrySet()) {
            String trackedCheck = entry.getKey();
            double vl = entry.getValue() == null ? 0.0 : entry.getValue().doubleValue();
            String trackedCategory = CheckAliasUtil.displayCategory(trackedCheck, rawCategoryOf(trackedCheck, category));
            ConfigManager.ConfidenceProfile profile = plugin.cfg().confidenceProfileFor(trackedCheck, trackedCategory);
            if (vl < profile.minCheckVl) continue;

            hotChecks++;
            double scaled = Math.min(profile.maxContribution, profile.baseScore * (vl / profile.minCheckVl));
            score += scaled;
        }

        int recentFlags = state.countRecent(now, plugin.cfg().punishRecentWindowSeconds() * 1000L);
        if (recentFlags >= plugin.cfg().punishFastFlagCount()
                && state.withinFastWindow(now, plugin.cfg().punishFastFlagWindowSeconds() * 1000L)) {
            score += plugin.cfg().punishFastFlagBonus();
        }

        if (hotChecks >= 2) {
            score += plugin.cfg().punishMultiCheckBonus();
        }
        if (hotChecks >= 3) {
            score += plugin.cfg().punishTripleCheckBonus();
        }
        if (data.getTotalVl() >= plugin.cfg().punishGlobalEvidenceVl()) {
            score += plugin.cfg().punishGlobalEvidenceBonus();
        }

        double queueScore = score;
        if (getMarkTime(data.getUuid()) > 0L) {
            queueScore += plugin.cfg().punishMarkedBonus();
        }

        return new EvidenceSnapshot(score, queueScore, hotChecks, recentFlags);
    }

    private String rawCategoryOf(String checkName, String fallback) {
        if (checkName == null) return fallback == null ? "MISC" : fallback;
        if (checkName.startsWith("Reach")) return "REACH";
        if (checkName.startsWith("BackTrack")) return "BACKTRACK";
        if (checkName.startsWith("Velocity")) return "VELOCITY";
        if (checkName.startsWith("KillAura")) return "KILLAURA";
        if (checkName.startsWith("Scaffold")) return "SCAFFOLD";
        if (checkName.startsWith("AimAssist")) return "AIMASSIST";
        if (checkName.startsWith("AutoClick")) return "AUTOCLICK";
        if (checkName.startsWith("Fly")) return "FLY";
        if (checkName.startsWith("GroundSpoof") || checkName.startsWith("PredictionGroundSpoof")) {
            return "GROUNDSPOOF";
        }
        if (checkName.startsWith("NoFall") || checkName.startsWith("PredictionNoFall")) {
            return "NOFALL";
        }
        if (checkName.startsWith("Phase")) return "PHASE";
        if (checkName.startsWith("NoSlow")) return "NOSLOW";
        if (checkName.startsWith("Speed")) return "SPEED";
        if (checkName.startsWith("Step")) return "STEP";
        if (checkName.startsWith("Nuker")) return "NUKER";
        if (checkName.startsWith("Timer")) return "TIMER";
        return fallback == null ? "MISC" : fallback;
    }

    private static final class EvidenceState {
        private final Deque<Long> timestamps = new ArrayDeque<Long>();
        private long lastQueueMs;

        void record(long now, String checkName) {
            timestamps.addLast(now);
            while (timestamps.size() > 40) timestamps.removeFirst();
        }

        int countRecent(long now, long windowMs) {
            trim(now, windowMs);
            return timestamps.size();
        }

        boolean withinFastWindow(long now, long windowMs) {
            trim(now, windowMs);
            if (timestamps.isEmpty()) return false;
            Long first = timestamps.peekFirst();
            Long last = timestamps.peekLast();
            return first != null && last != null && (last.longValue() - first.longValue()) <= windowMs;
        }

        private void trim(long now, long windowMs) {
            while (!timestamps.isEmpty()) {
                Long first = timestamps.peekFirst();
                if (first == null || now - first.longValue() <= windowMs) break;
                timestamps.removeFirst();
            }
        }
    }

    private static final class EvidenceSnapshot {
        private final double markScore;
        private final double queueScore;
        private final int hotChecks;
        private final int recentFlags;

        private EvidenceSnapshot(double markScore, double queueScore, int hotChecks, int recentFlags) {
            this.markScore = markScore;
            this.queueScore = queueScore;
            this.hotChecks = hotChecks;
            this.recentFlags = recentFlags;
        }
    }

    private static final class BlatantIncidentState {
        private final Deque<Long> timestamps = new ArrayDeque<Long>();
        private long lastIncidentMs;
    }
}
