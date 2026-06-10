package com.colin.vezanticheat.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/**
 * Records combat-relevant damage and velocity timestamps for knockback leniency.
 */
public final class CombatListener implements Listener {

    private final CombatAnalyzer analyzer;

    public CombatListener(CombatAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (analyzer == null || !(event.getEntity() instanceof Player)) {
            return;
        }
        // Timestamps feed knockback leniency windows in CombatAnalyzer.
        analyzer.markCombatDamage(((Player) event.getEntity()).getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (analyzer == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        analyzer.markCombatVelocity(player.getUniqueId(), System.currentTimeMillis());
    }
}
