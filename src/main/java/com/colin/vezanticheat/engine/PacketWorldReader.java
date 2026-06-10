package com.colin.vezanticheat.engine;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.data.PlayerDataManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;

/**
 * PacketWorldReader — feeds server block-change packets into each player's
 * {@link CompensatedWorld} overlay so the prediction engine collides against exactly the
 * blocks the client was told about (latency-compensated), defeating ghost-block phase.
 *
 * Handles BLOCK_CHANGE and MULTI_BLOCK_CHANGE. Full chunk deserialization is intentionally
 * not reproduced here (the live-world fallback covers steady-state chunks); the overlay
 * captures the latency-sensitive single/multi block updates.
 */
public final class PacketWorldReader extends PacketListenerAbstract {

    private final VezAntiCheat plugin;
    private final PlayerDataManager dataManager;

    public PacketWorldReader(VezAntiCheat plugin, PlayerDataManager dataManager) {
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
        if (!plugin.getConfig().getBoolean("engine.world-cache", true)) return;
        Object playerObj = event.getPlayer();
        if (!(playerObj instanceof Player)) return;
        Player player = (Player) playerObj;
        PlayerData data = dataManager.get(player);
        if (data == null) return;
        CompensatedWorld world = data.getCompensatedWorld();

        try {
            if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
                WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
                Vector3i pos = wrapper.getBlockPosition();
                applyState(world, pos.getX(), pos.getY(), pos.getZ(), wrapper.getBlockState());
            } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
                ClientVersion version = event.getUser() == null ? null : event.getUser().getClientVersion();
                WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = wrapper.getBlocks();
                if (blocks == null) return;
                for (WrapperPlayServerMultiBlockChange.EncodedBlock block : blocks) {
                    if (block == null) continue;
                    WrappedBlockState state = version == null ? block.getBlockState(ClientVersion.V_1_8)
                            : block.getBlockState(version);
                    applyState(world, block.getX(), block.getY(), block.getZ(), state);
                }
            }
        } catch (Throwable ignored) {
            // Conversion can fail for exotic/unknown states; the live-world fallback covers it.
        }
    }

    private void applyState(CompensatedWorld world, int x, int y, int z, WrappedBlockState state) {
        if (state == null) return;
        MaterialData materialData = SpigotConversionUtil.toBukkitMaterialData(state);
        if (materialData == null || materialData.getItemType() == null) return;
        world.updateBlock(x, y, z, materialData.getItemType(), materialData.getData());
    }
}
