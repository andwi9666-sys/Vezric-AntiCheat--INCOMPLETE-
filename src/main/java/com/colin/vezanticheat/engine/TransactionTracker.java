package com.colin.vezanticheat.engine;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.data.PlayerDataManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * TransactionTracker — sends a window-confirmation ("transaction") to every player each tick and
 * captures the echo to measure transaction-accurate ping and anchor entity-position snapshots.
 *
 * Per tick (main thread): for each player it allocates a transaction token, snapshots that player's
 * tracked entities tagged with the token's sequence, then sends the transaction. On the Netty
 * thread it intercepts the matching client reply, records the round trip, and cancels the packet so
 * the server never sees the injected confirmation. Negative action IDs are used so they never clash
 * with vanilla inventory transactions.
 */
public final class TransactionTracker extends PacketListenerAbstract {

    private final VezAntiCheat plugin;
    private final PlayerDataManager dataManager;
    private int taskId = -1;

    /**
     * Monotonic server-tick counter, advanced once per main-thread tick by {@link #tick()}.
     * Static so packet-thread consumers (e.g. position-packet bucketing) can read the current tick
     * without a plugin reference. Reads of the latest value are safe across threads via AtomicLong.
     */
    private static final AtomicLong SERVER_TICK = new AtomicLong(0L);

    /** The current server tick (advances every main-thread tick while the tracker task runs). */
    public static long currentServerTick() {
        return SERVER_TICK.get();
    }

    public TransactionTracker(VezAntiCheat plugin, PlayerDataManager dataManager) {
        super(PacketListenerPriority.LOWEST);
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void hook() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void unhook() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        stop();
    }

    public void start() {
        if (taskId != -1) return;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tick() {
        // Advance the server-tick counter first so it tracks real ticks even when the combat
        // engine is disabled (position-packet bucketing relies on it independently).
        SERVER_TICK.incrementAndGet();
        if (!plugin.getConfig().getBoolean("combat-engine.enabled", true)) return;
        boolean doTransactions = plugin.getConfig().getBoolean("combat-engine.transactions", true);
        boolean doEntities = plugin.getConfig().getBoolean("combat-engine.entity-tracking", true);
        if (!doTransactions && !doEntities) return;

        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = dataManager.get(player);
            if (data == null) continue;

            TransactionState state = data.getTransactionState();

            if (doTransactions) {
                TransactionState.Transaction txn = state.allocate(now);
                // Tag the current entity positions with this transaction's sequence BEFORE sending,
                // so the client's ack of this id confirms it has seen these positions.
                if (doEntities) {
                    data.getCompensatedEntities().snapshotAll(txn.sequence, now);
                }
                try {
                    User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
                    if (user != null) {
                        user.sendPacketSilently(new WrapperPlayServerWindowConfirmation(0, txn.id, false));
                    }
                } catch (Throwable ex) {
                    logThrottled("send-transaction", ex);
                }
            } else if (doEntities) {
                // No transactions: anchor snapshots by wall-clock time (rewind uses the time window).
                data.getCompensatedEntities().snapshotAll(state.nextSequenceOnly(now), now);
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.WINDOW_CONFIRMATION) return;
        Object playerObj = event.getPlayer();
        if (!(playerObj instanceof Player)) return;
        PlayerData data = dataManager.get((Player) playerObj);
        if (data == null) return;

        try {
            WrapperPlayClientWindowConfirmation wrapper = new WrapperPlayClientWindowConfirmation(event);
            long nowMs = System.currentTimeMillis();
            boolean ours = data.getTransactionState().onAck(wrapper.getActionId(), nowMs);
            data.getTransactionState().noteClientConfirmation(wrapper.getActionId(), nowMs, ours);
            if (ours) {
                PlayerClock.onTransactionAck(plugin, (Player) playerObj, data, nowMs);
                event.setCancelled(true);
            } else {
                plugin.tierChecks().onWindowConfirmation((Player) playerObj, data, wrapper.getActionId(), nowMs);
            }
        } catch (Throwable ex) {
            // Never propagate from the Netty thread; throttle so a recurring fault cannot spam.
            logThrottled("window-confirmation-ack", ex);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_LOG_MS =
            new java.util.concurrent.ConcurrentHashMap<String, Long>();
    private static final long LOG_INTERVAL_MS = 30_000L;

    private void logThrottled(String stage, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOG_MS.get(stage);
        if (last != null && now - last < LOG_INTERVAL_MS) return;
        LAST_LOG_MS.put(stage, now);
        plugin.getLogger().log(Level.WARNING,
                "TransactionTracker " + stage + " failed: "
                        + t.getClass().getSimpleName() + " - " + t.getMessage()
                        + " (further errors throttled for 30s)", t);
    }
}
