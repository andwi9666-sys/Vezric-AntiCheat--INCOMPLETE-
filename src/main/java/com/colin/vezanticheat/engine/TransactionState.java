package com.colin.vezanticheat.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TransactionState — per-player transaction (window-confirmation) bookkeeping for ping rewind.
 *
 * The server sends a window-confirmation ("transaction") packet each tick; the client echoes it
 * back. Because client packets are processed strictly in order, the most recently acknowledged
 * transaction at the moment an attack arrives tells us exactly how far the client had rendered.
 * Entity position snapshots are tagged with a monotonic {@code sequence}; on attack we rewind to
 * the snapshots around {@link #getLastAckedSequence()} to reconstruct what the client could see.
 *
 * Wire action IDs are 16-bit shorts allocated from -1 downward (skipping 0) so they never collide
 * with vanilla inventory transactions (which use small positive IDs on the open container).
 */
public final class TransactionState {

    /** A sent-but-not-yet-acknowledged transaction. */
    public static final class Transaction {
        public final short id;
        public final long sequence;
        public final long sendTimeMs;

        Transaction(short id, long sequence, long sendTimeMs) {
            this.id = id;
            this.sequence = sequence;
            this.sendTimeMs = sendTimeMs;
        }
    }

    private final ConcurrentHashMap<Short, Transaction> outstanding = new ConcurrentHashMap<Short, Transaction>();
    private final AtomicLong sequenceCounter = new AtomicLong(0L);

    private short nextId = -1;

    private volatile long lastSentSequence = -1L;
    private volatile long lastAckedSequence = -1L;
    private volatile long lastAckedTimeMs;
    private volatile long lastSentTimeMs;
    private volatile long transactionPingMs = -1L;

    // Client transaction order tracking (Disabler / lifeboat detection).
    private volatile int consecutiveSentWithoutAck;
    private volatile long lastClientConfirmMs;
    private volatile short lastClientConfirmId;
    private volatile int clientConfirmBurst;
    private volatile boolean clientConfirmBurstAfterSilence;

    /**
     * Advance the sequence counter without sending/tracking a transaction. Used when transactions
     * are disabled so entity snapshots still get a monotonic sequence and the rewind falls back to
     * wall-clock-time bracketing (lastAckedSequence stays -1).
     */
    public synchronized long nextSequenceOnly(long nowMs) {
        long sequence = sequenceCounter.incrementAndGet();
        lastSentSequence = sequence;
        lastSentTimeMs = nowMs;
        return sequence;
    }

    /** Allocate the next transaction token. Called on the main thread (tick task). */
    public synchronized Transaction allocate(long nowMs) {
        short id = nextId;
        nextId--;
        if (nextId == 0) nextId = -1;
        if (nextId == Short.MIN_VALUE) nextId = -1;

        long sequence = sequenceCounter.incrementAndGet();
        Transaction txn = new Transaction(id, sequence, nowMs);
        outstanding.put(id, txn);
        lastSentSequence = sequence;
        lastSentTimeMs = nowMs;

        noteTransactionSent();

        // Bound memory if a client stops responding.
        if (outstanding.size() > 256) {
            long cutoff = sequence - 200L;
            for (java.util.Map.Entry<Short, Transaction> e : outstanding.entrySet()) {
                if (e.getValue().sequence < cutoff) {
                    outstanding.remove(e.getKey(), e.getValue());
                }
            }
        }
        return txn;
    }

    /**
     * Process a client window-confirmation reply. Runs on the Netty thread.
     *
     * @return true if this ID belonged to one of our injected transactions (so the caller should
     *         cancel the packet to keep it from reaching the server).
     */
    public boolean onAck(short id, long nowMs) {
        Transaction txn = outstanding.remove(id);
        if (txn == null) {
            return false;
        }
        // In-order delivery: everything sent before this is implicitly acknowledged.
        if (txn.sequence > lastAckedSequence) {
            lastAckedSequence = txn.sequence;
            lastAckedTimeMs = nowMs;
            transactionPingMs = Math.max(0L, nowMs - txn.sendTimeMs);
        }
        for (java.util.Map.Entry<Short, Transaction> e : outstanding.entrySet()) {
            if (e.getValue().sequence <= txn.sequence) {
                outstanding.remove(e.getKey(), e.getValue());
            }
        }
        return true;
    }

    public long getLastSentSequence() { return lastSentSequence; }
    public long getLastAckedSequence() { return lastAckedSequence; }
    public long getLastAckedTimeMs() { return lastAckedTimeMs; }
    public long getLastSentTimeMs() { return lastSentTimeMs; }

    /** Transaction-measured round trip in ms, or -1 if no transaction has completed yet. */
    public long getTransactionPingMs() { return transactionPingMs; }

    public boolean hasCompletedTransaction() { return transactionPingMs >= 0L; }

    /** True when a transaction ack arrived recently (used by PrismNoRotationC). */
    public boolean hasRecentAck(long nowMs) {
        return lastAckedTimeMs > 0L && (nowMs - lastAckedTimeMs) <= 500L;
    }

    /** Record a client window confirmation (vanilla or injected). */
    public void noteClientConfirmation(short id, long nowMs, boolean ours) {
        if (ours) {
            consecutiveSentWithoutAck = 0;
            clientConfirmBurst = 0;
            clientConfirmBurstAfterSilence = false;
            return;
        }
        long gap = lastClientConfirmMs > 0L ? nowMs - lastClientConfirmMs : Long.MAX_VALUE;
        if (gap >= 400L) {
            clientConfirmBurst = 1;
            clientConfirmBurstAfterSilence = true;
        } else if (gap <= 20L) {
            clientConfirmBurst = Math.min(512, clientConfirmBurst + 1);
        } else {
            clientConfirmBurst = 1;
            clientConfirmBurstAfterSilence = false;
        }
        lastClientConfirmId = id;
        lastClientConfirmMs = nowMs;
    }

    public void noteTransactionSent() {
        consecutiveSentWithoutAck = Math.min(512, consecutiveSentWithoutAck + 1);
    }

    public int getConsecutiveSentWithoutAck() { return consecutiveSentWithoutAck; }
    public long getLastClientConfirmMs() { return lastClientConfirmMs; }
    public int getClientConfirmBurst() { return clientConfirmBurst; }
    public boolean isClientConfirmBurstAfterSilence() { return clientConfirmBurstAfterSilence; }

    public void reset() {
        outstanding.clear();
        lastAckedSequence = lastSentSequence;
    }
}
