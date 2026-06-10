package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.BadPacketValidationUtil;
import org.bukkit.entity.Player;

/**
 * Base for Prism BadPackets A–M with per-letter buffer tracking on PlayerData.
 */
public abstract class PrismBadPacketCheck extends TierCheck {

    private final char letter;

    protected PrismBadPacketCheck(VezAntiCheat plugin, String name, char letter) {
        super(plugin, name, com.colin.vezanticheat.tier.CheckTier.PRISM);
        this.letter = letter;
    }

    protected char letter() { return letter; }

    protected int letterBuffer(PlayerData data) {
        return data == null ? 0 : data.getBadPacketsVerbose(letter);
    }

    protected void setLetterBuffer(PlayerData data, int value) {
        if (data != null) data.setBadPacketsVerbose(letter, Math.max(0, value));
    }

    protected int incrementLetterBuffer(Player p, PlayerData data, int gain, int bufferToFlag, String reason) {
        int next = letterBuffer(data) + Math.max(1, gain);
        setLetterBuffer(data, next);
        if (p != null && next >= Math.max(1, bufferToFlag - 1)) {
            verbose(p, "buf=" + next + "/" + bufferToFlag + " " + reason);
        }
        return next;
    }

    protected void decayLetterBuffer(PlayerData data, int amount) {
        setLetterBuffer(data, Math.max(0, letterBuffer(data) - Math.max(1, amount)));
    }

    protected void flagLetter(Player p, PlayerData data, double vl, String debug) {
        fail(p, data, vl, debug);
        setLetterBuffer(data, 0);
    }

    protected boolean isInvalidRotation(float yaw, float pitch) {
        double maxPitch = plugin.tierCfg().checkDouble(name(), "maxAbsPitch", 90.1D);
        return BadPacketValidationUtil.isInvalidRotation(yaw, pitch, maxPitch);
    }

    protected void recordBlatant(PlayerData data, int weight, long nowMs) {
        if (data == null) return;
        data.badPackets().recordBlatantSignal(name(), weight, nowMs,
                plugin.tierCfg().checkLong(name(), "blatantWindowMs", 1200L));
    }

    protected double r(double v) {
        return round3(v);
    }
}
