package com.colin.vezanticheat.punishment;

import java.util.Locale;

/**
 * Buyer-facing punishment safety mode (punish.safety-mode). One knob that decides
 * how far the anticheat is allowed to act on a flag:
 *
 *   SILENT       — record flags internally (history, risk score); no staff chat,
 *                  no mitigation, no punishments. For passive data gathering.
 *   ALERTS_ONLY  — + staff chat alerts; still no mitigation or punishments.
 *   MITIGATION   — + setbacks / hit-drops on flag; still no bans.
 *   BANWAVE      — + evidence scoring and deferred banwave punishment (default).
 *   INSTANT      — + immediate execution allowed. Never a shipped default;
 *                  operators must opt in after staging.
 *
 * When the key is missing (pre-1.2.0 configs) the mode is derived from legacy keys:
 * punish.enabled=false → ALERTS_ONLY; punish.execution.type=IMMEDIATE → INSTANT;
 * otherwise BANWAVE (which reproduces pre-1.2.0 behavior exactly).
 */
public enum PunishmentMode {
    SILENT,
    ALERTS_ONLY,
    MITIGATION,
    BANWAVE,
    INSTANT;

    /** Staff chat alerts allowed? */
    public boolean alertsEnabled() {
        return this != SILENT;
    }

    /** Setbacks / combat hit-drops allowed? */
    public boolean mitigationEnabled() {
        return this == MITIGATION || this == BANWAVE || this == INSTANT;
    }

    /** Any ban/kick execution allowed (queued or immediate)? */
    public boolean punishmentsEnabled() {
        return this == BANWAVE || this == INSTANT;
    }

    /** Immediate (non-banwave) execution allowed? */
    public boolean immediateAllowed() {
        return this == INSTANT;
    }

    public static PunishmentMode parse(String raw, PunishmentMode fallback) {
        if (raw == null) return fallback;
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (PunishmentMode mode : values()) {
            if (mode.name().equals(key)) return mode;
        }
        return fallback;
    }
}
