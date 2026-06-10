package com.colin.vezanticheat.combat;

/**
 * Recommended enforcement action for one combat hit after evidence review.
 * Actions map to buffer thresholds rather than instant bans: ALLOW on low buffer,
 * ALERT staff at flag buffer, PUNISH at punish buffer (alert-only for now),
 * CANCEL the packet for impossible hits or sustained suspicious patterns.
 */
public enum CombatAction {

    ALLOW,
    CANCEL,
    ALERT,
    PUNISH
}
