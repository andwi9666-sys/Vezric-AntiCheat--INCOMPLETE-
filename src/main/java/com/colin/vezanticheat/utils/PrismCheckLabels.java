package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.tier.CheckTier;

/**
 * Polar-style public check labels shown to staff (e.g. "Auto Clicker", tier "Prism").
 */
public final class PrismCheckLabels {

    private PrismCheckLabels() {}

    /** Public check name like Polar's alert title. */
    public static String polarCheckName(String internalName, CheckTier tier) {
        if (internalName == null) return "Unknown";
        if (internalName.startsWith("PrismAutoClick")) return "Auto Clicker";
        if (internalName.startsWith("PrismScaffold")) return "Bridging suspiciously";
        if (internalName.startsWith("PrismInventory")) return "Looting items suspiciously";
        if (internalName.startsWith("PrismFastBreak")) return "Block interaction";
        if ("PrismBlockSight".equals(internalName)) return "Block interaction";
        if ("PrismInteractionLegality".equals(internalName)) return "Fighting suspiciously";
        if ("PrismHitboxB".equals(internalName) || "PrismBackTrack".equals(internalName)
                || "PrismLagRange".equals(internalName)) return "Fighting suspiciously";
        if (internalName.startsWith("PrismBadPackets") || internalName.startsWith("PrismPacketOrder")
                || internalName.startsWith("PrismTransaction") || internalName.startsWith("PrismMultiActions")
                || "PrismSetbackAccept".equals(internalName)) {
            return "Invalid protocol";
        }
        if (internalName.startsWith("PrismInteractReach")) return "Fighting suspiciously";
        if (tier == CheckTier.CHARACTERISTICS) {
            if (internalName.contains("SilentAim") || internalName.startsWith("CharAim")) return "Heuristics";
            if (internalName.startsWith("CharCriticals")) return "Heuristics";
            if (internalName.startsWith("CharCombat")) return "Heuristics";
        }
        if (tier == CheckTier.PREDICTION || tier == CheckTier.SIMULATION) return "Movement";
        return internalName;
    }

    /** Polar data layer label (screenshot: "Data: - Prism"). */
    public static String polarDataTier(CheckTier tier) {
        if (tier == null) return "Unknown";
        switch (tier) {
            case PRISM: return "Prism";
            case CHARACTERISTICS: return "Heuristics";
            case SIMULATION: return "Simulation";
            case PREDICTION: return "Prediction";
            default: return tier.name();
        }
    }

    /** Shared VL pool for variants of the same Polar public check. */
    public static String vlPoolName(String internalName) {
        if (internalName == null) return "Unknown";
        if (internalName.startsWith("PrismAutoClick")) return "PrismAutoClick";
        if (internalName.startsWith("PrismScaffold")) return "PrismScaffold";
        if (internalName.startsWith("PrismInventory")) return "PrismInventory";
        if (internalName.startsWith("PrismFastBreak")) return "PrismFastBreak";
        if (internalName.startsWith("PrismHitbox") || internalName.startsWith("PrismReach")
                || "PrismBackTrack".equals(internalName) || "PrismLagRange".equals(internalName)) {
            return interactionVlPool();
        }
        return internalName;
    }

    /** Polar alert reason for combat interaction flags. */
    public static String fightingReason(String label, String debug) {
        String base = label == null || label.isEmpty() ? "fighting_suspiciously" : label;
        if (debug == null || debug.isEmpty()) return base;
        if (debug.startsWith(base)) return debug;
        return base + " " + debug;
    }

    /** Shared VL pool for combat interaction (reach/LOS/hitbox absorbed). */
    public static String interactionVlPool() {
        return "PrismInteractionLegality";
    }

    /** Polar alert reason for autoclicker flags. */
    public static String autoClickerReason(String debug) {
        if (debug == null || debug.isEmpty()) return "Clicking suspiciously";
        if (debug.startsWith("clicking_suspiciously")) return debug;
        return "clicking_suspiciously " + debug;
    }
}
