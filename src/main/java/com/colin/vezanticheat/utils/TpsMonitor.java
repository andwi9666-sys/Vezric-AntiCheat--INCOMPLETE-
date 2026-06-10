package com.colin.vezanticheat.utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TpsMonitor {

    private volatile double tps = 20.0;

    private long lastCheckMs = 0L;
    private int tickCount = 0;

    public void start(JavaPlugin plugin) {
        lastCheckMs = System.currentTimeMillis();
        tickCount = 0;

        new BukkitRunnable() {
            @Override
            public void run() {
                tickCount++;

                if (tickCount >= 40) { // ~2 seconds
                    long now = System.currentTimeMillis();
                    long diff = now - lastCheckMs;
                    lastCheckMs = now;
                    tickCount = 0;

                    if (diff > 0L) {
                        double calc = (40.0 * 1000.0) / (double) diff;
                        if (calc > 20.0) calc = 20.0;
                        if (calc < 0.0) calc = 0.0;
                        tps = calc;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public double getTps() {
        return tps;
    }
}
