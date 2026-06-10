package com.colin.vezanticheat.engine;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.data.PlayerDataManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * EntityTrackerListener — feeds server->client entity packets into each viewer's
 * {@link CompensatedEntities}, mirroring {@link PacketWorldReader} for blocks.
 *
 * Handles SPAWN_PLAYER / SPAWN_LIVING_ENTITY / SPAWN_ENTITY (create at initial position),
 * ENTITY_RELATIVE_MOVE(+AND_ROTATION) (add the per-axis block delta), ENTITY_TELEPORT (absolute),
 * ENTITY_METADATA (sneak flag for hitbox height), and DESTROY_ENTITIES (remove). Positions are
 * tagged into per-tick snapshots by {@link TransactionTracker} so they can be rewound by ack.
 */
public final class EntityTrackerListener extends PacketListenerAbstract {

    private final VezAntiCheat plugin;
    private final PlayerDataManager dataManager;

    public EntityTrackerListener(VezAntiCheat plugin, PlayerDataManager dataManager) {
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
        if (!plugin.getConfig().getBoolean("combat-engine.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("combat-engine.entity-tracking", true)) return;
        Object playerObj = event.getPlayer();
        if (!(playerObj instanceof Player)) return;
        Player viewer = (Player) playerObj;
        PlayerData data = dataManager.get(viewer);
        if (data == null) return;
        CompensatedEntities entities = data.getCompensatedEntities();

        try {
            if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
                WrapperPlayServerSpawnPlayer w = new WrapperPlayServerSpawnPlayer(event);
                Vector3d pos = w.getPosition();
                if (w.getEntityId() == viewer.getEntityId()) return;
                entities.addPlayer(w.getEntityId(), pos.getX(), pos.getY(), pos.getZ());
                applyMetadata(entities, w.getEntityId(), w.getEntityMetadata());
            } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
                WrapperPlayServerSpawnLivingEntity w = new WrapperPlayServerSpawnLivingEntity(event);
                Vector3d pos = w.getPosition();
                entities.addLiving(w.getEntityId(), pos.getX(), pos.getY(), pos.getZ());
                applyMetadata(entities, w.getEntityId(), w.getEntityMetadata());
            } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(event);
                Vector3d pos = w.getPosition();
                entities.addGeneric(w.getEntityId(), pos.getX(), pos.getY(), pos.getZ());
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
                WrapperPlayServerEntityRelativeMove w = new WrapperPlayServerEntityRelativeMove(event);
                entities.relativeMove(w.getEntityId(), w.getDeltaX(), w.getDeltaY(), w.getDeltaZ());
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
                WrapperPlayServerEntityRelativeMoveAndRotation w = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                entities.relativeMove(w.getEntityId(), w.getDeltaX(), w.getDeltaY(), w.getDeltaZ());
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
                WrapperPlayServerEntityTeleport w = new WrapperPlayServerEntityTeleport(event);
                Vector3d pos = w.getPosition();
                entities.teleport(w.getEntityId(), pos.getX(), pos.getY(), pos.getZ());
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                WrapperPlayServerEntityMetadata w = new WrapperPlayServerEntityMetadata(event);
                applyMetadata(entities, w.getEntityId(), w.getEntityMetadata());
            } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
                WrapperPlayServerDestroyEntities w = new WrapperPlayServerDestroyEntities(event);
                int[] ids = w.getEntityIds();
                if (ids != null) {
                    for (int id : ids) entities.remove(id);
                }
            }
        } catch (Throwable ignored) {
            // Defensive: unknown metadata/state shouldn't break tracking; rewind falls back to time window.
        }
    }

    private void applyMetadata(CompensatedEntities entities, int entityId, List<EntityData<?>> metadata) {
        if (metadata == null) return;
        for (EntityData<?> entry : metadata) {
            if (entry == null || entry.getIndex() != 0) continue;
            Object value = entry.getValue();
            if (value instanceof Number) {
                byte flags = ((Number) value).byteValue();
                entities.setSneaking(entityId, (flags & 0x02) != 0);
            }
        }
    }
}
