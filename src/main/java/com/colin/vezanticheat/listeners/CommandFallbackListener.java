package com.colin.vezanticheat.listeners;

import com.colin.vezanticheat.commands.AlertsCommand;
import com.colin.vezanticheat.commands.BanwaveCommand;
import com.colin.vezanticheat.commands.FlagsCommand;
import com.colin.vezanticheat.commands.VezCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Manual dispatch for Vez commands when Bukkit/PlugMan classloader state prevents normal
 * PluginCommand routing. Cancels preprocess so usage templates are not echoed.
 */
public final class CommandFallbackListener implements Listener {

    private final VezCommand vezCommand;
    private final AlertsCommand alertsCommand;
    private final BanwaveCommand banwaveCommand;
    private final FlagsCommand flagsCommand;

    public CommandFallbackListener(VezCommand vezCommand, AlertsCommand alertsCommand,
                                   BanwaveCommand banwaveCommand, FlagsCommand flagsCommand) {
        this.vezCommand = vezCommand;
        this.alertsCommand = alertsCommand;
        this.banwaveCommand = banwaveCommand;
        this.flagsCommand = flagsCommand;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() <= 1) return;

        ParsedCommand parsed = parse(message.substring(1));
        if (parsed == null) return;

        if (!dispatch(parsed.root, event.getPlayer(), parsed.args)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand();
        if (command == null || command.trim().isEmpty()) return;

        ParsedCommand parsed = parse(command);
        if (parsed == null) return;

        if (!dispatch(parsed.root, event.getSender(), parsed.args)) return;

        event.setCommand("");
    }

    private boolean dispatch(String root, org.bukkit.command.CommandSender sender, String[] args) {
        if ("vez".equals(root) || "watchdog".equals(root) || "wd".equals(root)) {
            vezCommand.onCommand(sender, null, root, args);
            return true;
        }
        if ("alerts".equals(root)) {
            alertsCommand.onCommand(sender, null, root, args);
            return true;
        }
        if ("banwave".equals(root)) {
            banwaveCommand.onCommand(sender, null, root, args);
            return true;
        }
        if ("flags".equals(root)) {
            flagsCommand.onCommand(sender, null, root, args);
            return true;
        }
        return false;
    }

    private ParsedCommand parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return null;

        String rootToken = trimmed.split("\\s+", 2)[0];
        rootToken = stripNamespace(rootToken).toLowerCase();
        if (!"vez".equals(rootToken) && !"watchdog".equals(rootToken) && !"wd".equals(rootToken)
                && !"alerts".equals(rootToken) && !"banwave".equals(rootToken) && !"flags".equals(rootToken)) {
            return null;
        }

        String[] split = trimmed.split("\\s+");
        String[] args = new String[Math.max(0, split.length - 1)];
        if (split.length > 1) {
            System.arraycopy(split, 1, args, 0, split.length - 1);
        }
        return new ParsedCommand(rootToken, args);
    }

    private static String stripNamespace(String token) {
        if (token == null) return "";
        int colon = token.indexOf(':');
        return colon >= 0 ? token.substring(colon + 1) : token;
    }

    private static final class ParsedCommand {
        private final String root;
        private final String[] args;

        private ParsedCommand(String root, String[] args) {
            this.root = root;
            this.args = args;
        }
    }
}
