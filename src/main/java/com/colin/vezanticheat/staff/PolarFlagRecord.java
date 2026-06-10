package com.colin.vezanticheat.staff;

import com.colin.vezanticheat.tier.CheckTier;

import java.util.UUID;

/** One Polar-style staff flag entry (GUI tooltip + chat metadata). */
public final class PolarFlagRecord {

    public final UUID playerId;
    public final String playerName;
    public final String polarCheck;
    public final CheckTier tier;
    public final String dataTier;
    public final int vl;
    public final String debug;
    public final long timestampMs;
    public final int ping;
    public final String clientBrand;
    public final String clientVersion;
    public final double tps;
    public final String serverName;

    public PolarFlagRecord(UUID playerId, String playerName, String polarCheck, CheckTier tier,
                           String dataTier, int vl, String debug, long timestampMs, int ping,
                           String clientBrand, String clientVersion, double tps, String serverName) {
        this.playerId = playerId;
        this.playerName = playerName == null ? "unknown" : playerName;
        this.polarCheck = polarCheck == null ? "Unknown" : polarCheck;
        this.tier = tier;
        this.dataTier = dataTier == null ? "Unknown" : dataTier;
        this.vl = vl;
        this.debug = debug == null ? "" : debug;
        this.timestampMs = timestampMs;
        this.ping = ping;
        this.clientBrand = clientBrand == null ? "Unknown" : clientBrand;
        this.clientVersion = clientVersion == null ? "Unknown" : clientVersion;
        this.tps = tps;
        this.serverName = serverName == null ? "Unknown" : serverName;
    }
}
