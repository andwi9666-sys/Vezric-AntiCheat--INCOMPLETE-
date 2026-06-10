package com.colin.vezanticheat.data;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final VezAntiCheat plugin;
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerDataManager(VezAntiCheat plugin) {
        this.plugin = plugin;

        // preload online
        for (Player p : Bukkit.getOnlinePlayers()) get(p);
    }

    public PlayerData get(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), id -> {
            PlayerData d = new PlayerData(id);
            d.setFlagsEnabled(plugin.cfg().staffDefaultFlagsOn());
            return d;
        });
    }

    public PlayerData get(UUID uuid) {
        return data.computeIfAbsent(uuid, PlayerData::new);
    }

    public void remove(Player p) {
        data.remove(p.getUniqueId());
    }

    public void shutdown() {
        // nothing heavy here
    }

    public File folder() {
        File f = new File(plugin.getDataFolder(), "data");
        if (!f.exists()) f.mkdirs();
        return f;
    }
}
