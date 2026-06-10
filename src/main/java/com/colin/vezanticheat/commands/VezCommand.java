package com.colin.vezanticheat.commands;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.ConfigProfileManager;
import com.colin.vezanticheat.utils.DiagnosticsTracker;
import com.colin.vezanticheat.utils.PerfSampler;
import com.colin.vezanticheat.utils.KillAuraAggregateUtil;
import com.colin.vezanticheat.utils.LagProfileUtil;
import com.colin.vezanticheat.utils.LagrangeUtil;
import com.colin.vezanticheat.utils.PingUtil;
import com.colin.vezanticheat.velocity.VelocityEvaluationResult;
import com.colin.vezanticheat.velocity.VelocitySession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class VezCommand implements CommandExecutor {

    private final VezAntiCheat plugin;
    private final VezAICommand aiCommand;
    private final CombatCommand combatCommand;

    public VezCommand(VezAntiCheat plugin) {
        this.plugin = plugin;
        this.aiCommand = new VezAICommand(plugin);
        this.combatCommand = new CombatCommand(plugin);
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String pref() {
        return c(plugin.getConfig().getString("prefix", "&6[Vez] &r"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(c("&6[VezAC] &eUsage: /vez <on|off|status|reload|profile|info|trace|tune|verbose|debug|announce|ai|combat>"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("ai")) {
            return aiCommand.handle(sender, args);
        }

        if (sub.equals("combat")) {
            String[] combatArgs = new String[args.length - 1];
            if (args.length > 1) {
                System.arraycopy(args, 1, combatArgs, 0, args.length - 1);
            }
            return combatCommand.handle(sender, combatArgs);
        }

        if (sub.equals("status")) {
            boolean enabled = plugin.cfg().enabled();
            sender.sendMessage(c("&6[VezAC] &eEnabled: &f" + enabled));
            sender.sendMessage(c("&6[VezAC] &ePlugin loaded: &f" + plugin.isEnabled()));
            sender.sendMessage(c("&6[VezAC] &ePacket hooks: &f" + plugin.packetHooksRegistered()));
            sender.sendMessage(c("&6[VezAC] &ePacketEvents: &f" + (com.github.retrooper.packetevents.PacketEvents.getAPI() != null)));
            sender.sendMessage(c("&6[VezAC] &eTier checks loaded: &f" + plugin.tierChecks().count()));
            sender.sendMessage(c("&6[VezAC] &eArchitecture: &fPolar tiers (CHAR/PRISM/SIM/PRED)"));
            sender.sendMessage(c("&6[VezAC] &eTPS: &f" + (plugin.tps() != null ? round2(plugin.tps().getTps()) : "20.0")));
            sender.sendMessage(c("&6[VezAC] &eOnline: &f" + Bukkit.getOnlinePlayers().size()));
            if (plugin.license() != null && plugin.getConfig().getBoolean("license.enabled", false)) {
                sender.sendMessage(c("&6[VezAC] &eLicense: &f"
                        + (plugin.license().checksAllowed() ? "active/grace" : "expired")));
            }
            if (plugin.updates() != null) {
                sender.sendMessage(c("&6[VezAC] &e" + plugin.updates().statusLine(plugin.getDescription().getVersion())));
            }
            PerfSampler perf = plugin.perf();
            if (perf != null) {
                PerfSampler.Snapshot snap = perf.snapshot();
                sender.sendMessage(c("&6[VezAC] &ePerf: &fmovement " + round2(snap.movementAvgMs) + "ms ("
                        + snap.movementSamples + " samples), packets " + round2(snap.packetAvgMs) + "ms ("
                        + snap.packetSamples + " samples)"));
            }
            if (!plugin.isEnabled() || !plugin.packetHooksRegistered()) {
                sender.sendMessage(c("&6[VezAC] &cChecks will not run until PacketEvents is ready and hooks register."));
                sender.sendMessage(c("&6[VezAC] &7Use &f/vez reload &7after fixing PacketEvents — avoid &f/reload&7."));
            } else if (!enabled) {
                sender.sendMessage(c("&6[VezAC] &cAnticheat is disabled. Use &f/vez on &cto re-enable."));
            }
            return true;
        }

        if (sub.equals("profile")) {
            if (!sender.hasPermission("vez.admin")) {
                sender.sendMessage(pref() + c("&cNo permission."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(pref() + c("&eUsage: /vez profile <lenient|balanced|aggressive>"));
                sender.sendMessage(pref() + c("&7Copies a bundled profile to config.yml and reloads."));
                return true;
            }
            String profileName = args[1].toLowerCase();
            if (!ConfigProfileManager.isValidProfile(profileName)) {
                sender.sendMessage(pref() + c("&cUnknown profile. Use lenient, balanced, or aggressive."));
                return true;
            }
            try {
                plugin.profiles().applyProfile(profileName);
                plugin.reloadConfig();
                plugin.cfg().reload();
                plugin.tierCfg().reload();
                if (plugin.tierChecks() != null) plugin.tierChecks().clearBuffers();
                if (plugin.riskScore() != null) plugin.riskScore().reload();
                plugin.reloadCombatSettings();
                if (plugin.perf() != null) {
                    plugin.perf().setEnabled(plugin.getConfig().getBoolean("diagnostics.perf-sampling-enabled", false));
                }
                plugin.startDecayTask();
                sender.sendMessage(pref() + c("&aApplied profile &f" + profileName + "&a and reloaded."));
            } catch (Exception e) {
                sender.sendMessage(pref() + c("&cProfile apply failed: " + e.getMessage()));
            }
            return true;
        }

        if (sub.equals("verbose")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(pref() + c("&cOnly players can use this command."));
                return true;
            }
            if (!sender.hasPermission("vez.staff")) {
                sender.sendMessage(pref() + c("&cNo permission."));
                return true;
            }
            Player staffPlayer = (Player) sender;
            PlayerData staffData = plugin.data().get(staffPlayer);
            boolean current = staffData.isVerboseMode();
            staffData.setVerboseMode(!current);
            if (!current) {
                sender.sendMessage(c("&6[Vez] &aVerbose mode &2enabled&a. You will see verbose flag notifications."));
            } else {
                sender.sendMessage(c("&6[Vez] &cVerbose mode &4disabled&c."));
            }
            return true;
        }

        if (sub.equals("debug")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(pref() + c("&cOnly players can use this command."));
                return true;
            }
            if (!sender.hasPermission("vez.staff")) {
                sender.sendMessage(pref() + c("&cNo permission."));
                return true;
            }
            Player staffPlayer = (Player) sender;
            PlayerData staffData = plugin.data().get(staffPlayer);
            boolean current = staffData.isDebugMode();
            staffData.setDebugMode(!current);
            if (!current) {
                sender.sendMessage(c("&6[Vez] &aDebug mode &2enabled&a. Verbose flags will include check debug details."));
            } else {
                sender.sendMessage(c("&6[Vez] &cDebug mode &4disabled&c."));
            }
            return true;
        }

        if (sub.equals("info")) {
            if (!sender.hasPermission("vez.staff")) {
                sender.sendMessage(pref() + c("&cNo permission."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(pref() + c("&eUsage: /vez info <player>"));
                return true;
            }
            String name = args[1];
            Player online = Bukkit.getPlayerExact(name);
            PlayerData data = (online != null ? plugin.data().get(online) : null);

            sender.sendMessage(pref() + c("&e--- Vez Info: &f" + name + " &e---"));
            double tps = (plugin.tps() != null ? plugin.tps().getTps() : 20.0);
            sender.sendMessage(pref() + c("&7TPS: &f" + round2(tps)));

            if (online != null) {
                int ping = PingUtil.getPing(online);
                sender.sendMessage(pref() + c("&7Ping: &f" + ping));
                if (data != null) {
                    sender.sendMessage(pref() + c("&7Total VL: &f" + data.getTotalVl()));
                    sender.sendMessage(pref() + c("&7Exempt: &f"
                            + "tp=" + (data.isTeleportExempt() ? "&aY" : "&cN")
                            + " &7kb=" + (data.isVelocityExempt() ? "&aY" : "&cN")
                            + " &7block=" + (data.isBlockStateExempt() ? "&aY" : "&cN")
                            + " &7potion=" + (data.isPotionExempt() ? "&aY" : "&cN")
                            + " &7invMomentum=" + (data.isInventoryMomentumExempt() ? "&aY" : "&cN")
                    ));
                    Map<String, Double> vl = data.snapshotCheckVl();
                    sender.sendMessage(pref() + c("&7Checks tracked: &f" + vl.size()));
                    long now = System.currentTimeMillis();
                    sender.sendMessage(pref() + c("&7Lag profile: &f"
                            + "score=" + round2(data.getLagProfileScore())
                            + " &7sel=" + (LagProfileUtil.isSelectiveLagActive(plugin, online, data, now) ? "&aY" : "&cN")
                            + " &7bursts=" + data.getSuspiciousLagBursts()
                            + " &7atk=" + data.getSuspiciousLagAttacks()));
                    LagrangeUtil.CombatTeleportSummary lagTeleport = LagrangeUtil.summarizeCombatTeleports(
                            data, now, plugin.getConfig().getLong("lag.profile.active-window-ms", 4500L));
                    if (lagTeleport.hasAny() || data.getLagrangeTeleportScore() > 0.0D) {
                        sender.sendMessage(pref() + c("&7Lag teleport: &f"
                                + "events=" + lagTeleport.getCount()
                                + " &7same=" + lagTeleport.getSameTargetCount()
                                + " &7avgClose=" + round2(lagTeleport.getAverageCloseDelta())
                                + " &7avgMove=" + round2(lagTeleport.getAverageMoveH())
                                + " &7bestGap=" + round2(lagTeleport.getBestGapMs())
                                + " &7score=" + round2(data.getLagrangeTeleportScore())));
                    }
                    formatTierVlSummary(sender, online, data);
                    com.colin.vezanticheat.data.PlayerData engineData = plugin.data().get(online);
                    if (engineData != null && engineData.getLastEngineResult() != null) {
                        sender.sendMessage(pref() + c("&7Engine: &f" + engineData.getLastEngineResult().debug));
                    }
                    if (engineData != null && engineData.getLastCombatResult() != null) {
                        com.colin.vezanticheat.engine.CombatResult cr = engineData.getLastCombatResult();
                        long txPing = engineData.getTransactionState().getTransactionPingMs();
                        sender.sendMessage(pref() + c("&7Combat: &f" + cr.debug
                                + " entities=" + engineData.getCompensatedEntities().size()
                                + " txPing=" + (txPing < 0 ? "n/a" : (txPing + "ms"))));
                    }
                    KillAuraAggregateUtil.Snapshot killAura = KillAuraAggregateUtil.snapshot(plugin, online.getUniqueId(), now);
                    if (killAura != null) {
                        sender.sendMessage(pref() + c("&7Shared aura: &f" + killAura.describe(plugin, now)));
                    }

                    if (plugin.prediction() != null) {
                        PredictionResult prediction = plugin.prediction().getLastResult(data);
                        VelocitySession session = plugin.prediction().getActiveVelocitySession(data);
                        VelocityEvaluationResult velocity = plugin.prediction().getLastVelocityResult(data);
                        if (prediction != null) {
                            sender.sendMessage(pref() + c("&7Prediction: &f"
                                    + " h=" + round2(prediction.horizontalDistance) + "/" + round2(prediction.expectedHorizontal)
                                    + " dy=" + round2(prediction.dy)
                                    + " cg=" + prediction.clientGround
                                    + " sg=" + prediction.serverGround
                                    + " phase=" + prediction.phaseViolation
                                    + " timerDebt=" + round2(prediction.timerDebtMs)));
                        }
                        if (session != null) {
                            sender.sendMessage(pref() + c("&7KB session: &aactive"
                                    + " &7stack=" + session.stackCount
                                    + " &7maxH=" + round2(session.maxHorizontal)
                                    + " &7gainY=" + round2(session.verticalGain())));
                        } else if (velocity != null) {
                            sender.sendMessage(pref() + c("&7KB result: &ecomplete"
                                    + " &7zeroV=" + velocity.zeroVertical
                                    + " &7redH=" + velocity.reducedHorizontal
                                    + " &7rev=" + velocity.reverseKnockback
                                    + " &7imp=" + velocity.impossiblePosition));
                        }
                    }

                    List<DiagnosticsTracker.Entry> recent = plugin.diagnostics() == null
                            ? java.util.Collections.<DiagnosticsTracker.Entry>emptyList()
                            : plugin.diagnostics().recent(online.getUniqueId(), 3);
                    if (!recent.isEmpty()) {
                        sender.sendMessage(pref() + c("&7Recent signals:"));
                        for (DiagnosticsTracker.Entry entry : recent) {
                            sender.sendMessage(pref() + c("&8- &f" + formatTime(entry.getTimeMs())
                                    + " &c" + entry.getCheck()
                                    + " &7[" + entry.getStage() + "] &f" + entry.getDetail()));
                        }
                    }
                }
            } else {
                sender.sendMessage(pref() + c("&cPlayer is offline."));
            }
            return true;
        }

        if (sub.equals("trace")) {
            if (!sender.hasPermission("vez.staff")) {
                sender.sendMessage(pref() + c("&cNo permission."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(pref() + c("&eUsage: /vez trace <player> [count]"));
                return true;
            }

            Player online = Bukkit.getPlayerExact(args[1]);
            if (online == null) {
                sender.sendMessage(pref() + c("&cPlayer is offline."));
                return true;
            }

            int count = 6;
            if (args.length >= 3) {
                try {
                    count = Math.max(1, Math.min(20, Integer.parseInt(args[2])));
                } catch (NumberFormatException ignored) {}
            }

            List<DiagnosticsTracker.Entry> recent = plugin.diagnostics() == null
                    ? java.util.Collections.<DiagnosticsTracker.Entry>emptyList()
                    : plugin.diagnostics().recent(online.getUniqueId(), count);
            sender.sendMessage(pref() + c("&e--- Trace: &f" + online.getName() + " &e---"));
            long now = System.currentTimeMillis();
            formatTierVlSummary(sender, online, plugin.data().get(online));
            com.colin.vezanticheat.data.PlayerData engineData = plugin.data().get(online);
            if (engineData != null && engineData.getLastEngineResult() != null) {
                sender.sendMessage(pref() + c("&7Engine: &f" + engineData.getLastEngineResult().debug));
            }
            if (engineData != null && engineData.getLastCombatResult() != null) {
                com.colin.vezanticheat.engine.CombatResult cr = engineData.getLastCombatResult();
                long txPing = engineData.getTransactionState().getTransactionPingMs();
                sender.sendMessage(pref() + c("&7Combat: &f" + cr.debug
                        + " entities=" + engineData.getCompensatedEntities().size()
                        + " txPing=" + (txPing < 0 ? "n/a" : (txPing + "ms"))));
            }
            KillAuraAggregateUtil.Snapshot killAura = KillAuraAggregateUtil.snapshot(plugin, online.getUniqueId(), now);
            if (killAura != null) {
                sender.sendMessage(pref() + c("&7Shared aura: &f" + killAura.describe(plugin, now)));
            }
            PlayerData traceData = plugin.data().get(online);
            if (traceData != null) {
                LagrangeUtil.CombatTeleportSummary lagTeleport = LagrangeUtil.summarizeCombatTeleports(
                        traceData, now, plugin.getConfig().getLong("lag.profile.active-window-ms", 4500L));
                if (lagTeleport.hasAny() || traceData.getLagrangeTeleportScore() > 0.0D) {
                    sender.sendMessage(pref() + c("&7Lag teleport: &f"
                            + "events=" + lagTeleport.getCount()
                            + " &7same=" + lagTeleport.getSameTargetCount()
                            + " &7avgClose=" + round2(lagTeleport.getAverageCloseDelta())
                            + " &7avgMove=" + round2(lagTeleport.getAverageMoveH())
                            + " &7bestClose=" + round2(lagTeleport.getBestCloseDelta())
                            + " &7bestGap=" + round2(lagTeleport.getBestGapMs())
                            + " &7score=" + round2(traceData.getLagrangeTeleportScore())));
                }
            }
            if (recent.isEmpty()) {
                sender.sendMessage(pref() + c("&7No recent diagnostic entries."));
                return true;
            }

            for (DiagnosticsTracker.Entry entry : recent) {
                sender.sendMessage(pref() + c("&f" + formatTime(entry.getTimeMs())
                        + " &c" + entry.getCheck()
                        + " &7[" + entry.getStage() + "]"));
                sender.sendMessage(pref() + c("&7" + entry.getDetail()));
            }
            return true;
        }

        if (!sender.hasPermission("vez.admin")) {
            sender.sendMessage(c("&cYou do not have permission."));
            return true;
        }

        if (sub.equals("on")) {
            plugin.getConfig().set("anticheat.enabled", true);
            plugin.saveConfig();
            sender.sendMessage(c("&6[Vez] &aAntiCheat enabled."));
            return true;
        }

        if (sub.equals("off")) {
            plugin.getConfig().set("anticheat.enabled", false);
            plugin.saveConfig();
            sender.sendMessage(c("&6[Vez] &cAntiCheat disabled."));
            return true;
        }

        if (sub.equals("reload")) {
            plugin.reloadConfig();
            plugin.cfg().reload();
            plugin.tierCfg().reload();
            if (plugin.tierChecks() != null) plugin.tierChecks().clearBuffers();
            if (plugin.riskScore() != null) plugin.riskScore().reload();
            plugin.reloadCombatSettings();
            if (plugin.perf() != null) {
                plugin.perf().setEnabled(plugin.getConfig().getBoolean("diagnostics.perf-sampling-enabled", false));
            }
            if (plugin.license() != null) plugin.license().initialize();
            if (plugin.updates() != null) plugin.updates().checkAsyncIfEnabled();
            plugin.startDecayTask(); // re-arm scheduled VL decay with reloaded rate/interval
            sender.sendMessage(c("&6[VezAC] &aConfig reloaded."));
            return true;
        }

        if (sub.equals("announce")) {
            plugin.getPunishmentManager().broadcastWatchdogAnnouncement();
            sender.sendMessage(c("&6[Vez] &aBroadcasted WatchDog announcement. &7(24h count: &f"
                    + plugin.getPunishmentManager().getRecentPunishmentCount24Hours() + "&7)"));
            return true;
        }

        if (sub.equals("tune")) {
            if (args.length < 3) {
                sender.sendMessage(pref() + c("&eUsage: /vez tune <check> <key> [value]"));
                sender.sendMessage(pref() + c("&7Examples: &f/vez tune LagrangeA flagConfidence 6.2"));
                sender.sendMessage(pref() + c("&7          &f/vez tune LagrangeA weights.selectiveLag 0.45"));
                sender.sendMessage(pref() + c("&7          &f/vez tune LagrangeA shadow false"));
                return true;
            }

            String checkName = resolveCheckName(args[1]);
            if (checkName == null) {
                sender.sendMessage(pref() + c("&cUnknown check: &f" + args[1]));
                return true;
            }

            String key = args[2];
            boolean tierCheck = plugin.tierCfg().checkSection(checkName) != null;
            Object current = tierCheck
                    ? plugin.tierCfg().checkDouble(checkName, key, Double.NaN)
                    : plugin.cfg().getCheckValue(checkName, key);
            if (tierCheck) {
                current = plugin.tierCfg().checkSection(checkName).get(key);
                if (current == null) {
                    current = plugin.tierCfg().tierConfig(plugin.tierCfg().tierForCheck(checkName)).get("defaults." + key);
                }
            }
            if (current == null && !tierCheck && !plugin.cfg().hasCheckKey(checkName, key)) {
                if (plugin.tierCfg().checkSection(checkName) == null
                        || !plugin.tierCfg().checkSection(checkName).contains(key)) {
                    sender.sendMessage(pref() + c("&cUnknown check key: &f" + checkName + "." + key));
                    return true;
                }
            }

            if (args.length == 3) {
                sender.sendMessage(pref() + c("&e" + checkName + "." + key + " &7= &f" + stringifyValue(current)));
                return true;
            }

            Object updated = coerceValue(args[3], current);
            if (updated == INVALID_VALUE) {
                sender.sendMessage(pref() + c("&cCould not parse value for &f" + checkName + "." + key
                        + "&c. Current type is &f" + typeName(current) + "&c."));
                return true;
            }

            if (updated == REMOVE_VALUE) {
                if (tierCheck) {
                    plugin.tierCfg().setCheckValue(checkName, key, null);
                } else {
                    plugin.cfg().removeCheckKey(checkName, key);
                    plugin.cfg().saveChecksConfig();
                }
                sender.sendMessage(pref() + c("&aReset &f" + checkName + "." + key + "&a to default."));
                return true;
            }

            if (tierCheck) {
                plugin.tierCfg().setCheckValue(checkName, key, updated);
            } else {
                plugin.cfg().setCheckValue(checkName, key, updated);
                plugin.cfg().saveChecksConfig();
            }
            sender.sendMessage(pref() + c("&aUpdated &f" + checkName + "." + key + "&a to &f" + stringifyValue(updated)
                    + " &7(saved)"));
            return true;
        }

        sender.sendMessage(c("&6[Vez] &eUsage: /vez <on|off|status|reload|info|trace|tune|verbose|debug|announce>"));
        return true;
    }

    private String round2(double v) {
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private String formatTime(long when) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(when));
    }

    private String resolveCheckName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        for (String key : plugin.tierCfg().listCheckNames()) {
            if (key.equalsIgnoreCase(raw)) return key;
        }
        for (String key : plugin.cfg().listCheckNames()) {
            if (key.equalsIgnoreCase(raw)) {
                return key;
            }
        }
        return null;
    }

    private Boolean parseBoolean(String raw) {
        if (raw == null) return null;
        if ("true".equalsIgnoreCase(raw) || "on".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(raw) || "off".equalsIgnoreCase(raw) || "no".equalsIgnoreCase(raw)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private Object coerceValue(String raw, Object current) {
        if (raw == null) return INVALID_VALUE;
        if ("default".equalsIgnoreCase(raw) || "unset".equalsIgnoreCase(raw) || "null".equalsIgnoreCase(raw)) {
            return REMOVE_VALUE;
        }

        if (current instanceof Boolean) {
            Boolean parsed = parseBoolean(raw);
            return parsed == null ? INVALID_VALUE : parsed;
        }
        if (current instanceof Integer) {
            try {
                return Integer.valueOf(raw);
            } catch (NumberFormatException ignored) {
                return INVALID_VALUE;
            }
        }
        if (current instanceof Long) {
            try {
                return Long.valueOf(raw);
            } catch (NumberFormatException ignored) {
                return INVALID_VALUE;
            }
        }
        if (current instanceof Double || current instanceof Float) {
            try {
                return Double.valueOf(raw);
            } catch (NumberFormatException ignored) {
                return INVALID_VALUE;
            }
        }
        if (current instanceof String) {
            return raw;
        }
        return inferValue(raw);
    }

    private Object inferValue(String raw) {
        Boolean bool = parseBoolean(raw);
        if (bool != null) return bool;
        try {
            if (raw.contains(".")) {
                return Double.valueOf(raw);
            }
            long asLong = Long.parseLong(raw);
            if (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE) {
                return (int) asLong;
            }
            return asLong;
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private String stringifyValue(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private String typeName(Object value) {
        return value == null ? "unknown" : value.getClass().getSimpleName();
    }

    private void formatTierVlSummary(org.bukkit.command.CommandSender sender, Player online, PlayerData data) {
        if (online == null || plugin.tierChecks() == null) return;
        java.util.List<String> parts = new java.util.ArrayList<String>();
        for (TierCheck check : plugin.tierChecks().registry().all()) {
            double vl = plugin.tierChecks().vlStore().getVl(online.getUniqueId(), check.name());
            if (vl <= 0.01D) continue;
            parts.add(check.name() + "=" + Math.round(vl));
        }
        java.util.Collections.sort(parts);
        if (parts.isEmpty()) {
            sender.sendMessage(pref() + c("&7Tier VL: &f(none)"));
            return;
        }
        int limit = Math.min(8, parts.size());
        String summary = String.join(", ", parts.subList(0, limit));
        if (parts.size() > limit) {
            summary += " +" + (parts.size() - limit) + " more";
        }
        sender.sendMessage(pref() + c("&7Tier VL: &f" + summary));
        if (data != null && data.isPendingSetback()) {
            sender.sendMessage(pref() + c("&7Setback pending: &f"
                    + (System.currentTimeMillis() - data.getPendingSetbackSinceMs()) + "ms"));
        }
    }

    private static final Object INVALID_VALUE = new Object();
    private static final Object REMOVE_VALUE = new Object();
}
