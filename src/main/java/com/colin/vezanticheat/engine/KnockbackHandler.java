package com.colin.vezanticheat.engine;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.data.PlayerDataManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerExplosion;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * KnockbackHandler — captures outgoing ENTITY_VELOCITY and EXPLOSION packets aimed at the
 * player and records them as the pending external impulse the prediction engine folds into
 * its candidate start vectors.
 *
 * Reading the actual outgoing packet (at send time) is strictly more accurate than relying on
 * Bukkit's {@code PlayerVelocityEvent}: it is the exact vector the client will apply, captured
 * before the client moves. (A full transaction-sandwiched queue, as GrimAC uses, would tie the
 * impulse to a specific client confirmation; here the short knockback-window in
 * {@link MovementCheckRunner} aligns it to the next movement tick, which is sufficient on 1.8.)
 */
public final class KnockbackHandler extends PacketListenerAbstract {

    private final VezAntiCheat plugin;
    private final PlayerDataManager dataManager;

    public KnockbackHandler(VezAntiCheat plugin, PlayerDataManager dataManager) {
        super(PacketListenerPriority.MONITOR);
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void hook() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void unhook() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!plugin.getConfig().getBoolean("engine.packet-knockback", true)) return;
        Object playerObj = event.getPlayer();
        if (!(playerObj instanceof Player)) return;
        Player player = (Player) playerObj;
        PlayerData data = dataManager.get(player);
        if (data == null) return;

        // Ignore the anticheat's OWN setback correction velocity — re-ingesting it as a real knockback
        // creates a self-feedback setback loop (legit players flung forward every velocity window).
        if (System.currentTimeMillis() < data.getSuppressVelocityCaptureUntilMs()) return;

        try {
            if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
                WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
                if (wrapper.getEntityId() != player.getEntityId()) return;
                Vector3d v = wrapper.getVelocity();
                if (v == null) return;
                Vector kb = new Vector(v.getX(), v.getY(), v.getZ());
                if (kb.lengthSquared() < 1.0E-6D) return;
                long now = System.currentTimeMillis();
                data.setLastVelocity(kb);
                data.setLastVelocityTime(now);
                data.setSpeedLastVelocityExemptMs(now);
                data.markVelocityExempt(plugin.cfg().velocityExemptMs());
            } else if (event.getPacketType() == PacketType.Play.Server.EXPLOSION) {
                WrapperPlayServerExplosion wrapper = new WrapperPlayServerExplosion(event);
                Vector3f motion = wrapper.getPlayerMotion();
                if (motion == null) return;
                Vector explosion = new Vector(motion.getX(), motion.getY(), motion.getZ());
                if (explosion.lengthSquared() < 1.0E-6D) return;
                long now = System.currentTimeMillis();
                data.setLastExplosionVelocity(explosion);
                data.setLastVelocity(explosion);
                data.setLastVelocityTime(now);
                data.setSpeedLastVelocityExemptMs(now);
                data.markVelocityExempt(plugin.cfg().velocityExemptMs());
            }
        } catch (Throwable ignored) {
        }
    }
}
