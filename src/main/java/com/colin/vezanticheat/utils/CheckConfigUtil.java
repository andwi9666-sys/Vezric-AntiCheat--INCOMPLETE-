package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves check tuning keys from tier YAML first, then legacy checks.yml.
 *
 * <p>Tier movement checks use names like {@code PredictionFly}; legacy grace utilities
 * historically keyed {@code FlyPrediction}. This helper unifies both.</p>
 */
public final class CheckConfigUtil {

    private static final Map<String, String> TIER_TO_LEGACY = new HashMap<String, String>();
    private static final Map<String, String> LEGACY_TO_TIER = new HashMap<String, String>();

    static {
        map("PredictionOffset", "OffsetPrediction");
        map("PredictionFly", "FlyPrediction");
        map("PredictionSpeed", "SpeedPrediction");
        map("PredictionGroundSpoof", "GroundSpoofPrediction");
        map("PredictionStep", "StepPrediction");
        map("PredictionJesus", "JesusPrediction");
        map("PredictionBlink", "BlinkPrediction");
        map("PredictionPhase", "PhasePrediction");
        map("PredictionTimer", "TimerPrediction");
        map("PredictionNoSlow", "NoSlowPrediction");
        map("PredictionVelocity", "VelocityPrediction");
        mapCharToPrism("CharAutoClickA", "PrismAutoClickA");
        mapCharToPrism("CharAutoClickB", "PrismAutoClickB");
        mapCharToPrism("CharAutoClickC", "PrismAutoClickC");
        mapCharToPrism("CharScaffoldA", "PrismScaffoldA");
        mapCharToPrism("CharScaffoldB", "PrismScaffoldB");
        mapCharToPrism("CharScaffoldC", "PrismScaffoldC");
        mapCharToPrism("CharScaffoldD", "PrismScaffoldD");
        mapCharToPrism("CharScaffoldE", "PrismScaffoldE");
        mapCharToPrism("CharInventoryA", "PrismInventoryA");
        mapCharToPrism("CharInventoryB", "PrismInventoryB");
        mapCharToPrism("CharInventoryC", "PrismInventoryC");
        mapCharToPrism("PrismHitboxA", "PrismInteractionLegality");
        mapCharToPrism("PrismHitboxB", "PrismInteractionLegality");
        mapCharToPrism("PrismBackTrack", "PrismInteractionLegality");
        mapCharToPrism("PrismLagRange", "PrismInteractionLegality");
        mapCharToPrism("PrismNoRotationA", "PrismInteractionLegality");
        mapCharToPrism("PrismNoRotationB", "PrismInteractionLegality");
        mapCharToPrism("PrismNoRotationC", "PrismInteractionLegality");
        mapCharToPrism("PrismRotationRay", "PrismInteractionLegality");
    }

    private static void mapCharToPrism(String oldName, String newName) {
        TIER_TO_LEGACY.put(newName, oldName);
        LEGACY_TO_TIER.put(oldName, newName);
    }

    private static void map(String tier, String legacy) {
        TIER_TO_LEGACY.put(tier, legacy);
        LEGACY_TO_TIER.put(legacy, tier);
    }

    private CheckConfigUtil() {}

    public static double checkDouble(VezAntiCheat plugin, String checkName, String key, double def) {
        Double tier = readTierDouble(plugin, checkName, key);
        if (tier != null) return tier.doubleValue();

        String legacy = TIER_TO_LEGACY.get(checkName);
        if (legacy != null) {
            tier = readTierDouble(plugin, legacy, key);
            if (tier != null) return tier.doubleValue();
            if (plugin.cfg().hasCheckKey(legacy, key)) {
                return plugin.cfg().checkDouble(legacy, key, def);
            }
        }

        String tierName = LEGACY_TO_TIER.get(checkName);
        if (tierName != null) {
            tier = readTierDouble(plugin, tierName, key);
            if (tier != null) return tier.doubleValue();
        }

        if (plugin.cfg().hasCheckKey(checkName, key)) {
            return plugin.cfg().checkDouble(checkName, key, def);
        }
        return def;
    }

    public static long checkLong(VezAntiCheat plugin, String checkName, String key, long def) {
        Long tier = readTierLong(plugin, checkName, key);
        if (tier != null) return tier.longValue();

        String legacy = TIER_TO_LEGACY.get(checkName);
        if (legacy != null) {
            tier = readTierLong(plugin, legacy, key);
            if (tier != null) return tier.longValue();
            if (plugin.cfg().hasCheckKey(legacy, key)) {
                return plugin.cfg().checkLong(legacy, key, def);
            }
        }

        if (plugin.cfg().hasCheckKey(checkName, key)) {
            return plugin.cfg().checkLong(checkName, key, def);
        }
        return def;
    }

    private static Double readTierDouble(VezAntiCheat plugin, String checkName, String key) {
        if (plugin.tierCfg() == null || plugin.tierCfg().checkSection(checkName) == null) return null;
        org.bukkit.configuration.ConfigurationSection section = plugin.tierCfg().checkSection(checkName);
        CheckTier tier = plugin.tierCfg().tierForCheck(checkName);
        org.bukkit.configuration.file.FileConfiguration tierFile =
                tier == null ? null : plugin.tierCfg().tierConfig(tier);
        boolean hasKey = section.contains(key)
                || (tierFile != null && tierFile.contains("defaults." + key));
        if (!hasKey) return null;
        double val = plugin.tierCfg().checkDouble(checkName, key, Double.NaN);
        return Double.isNaN(val) ? null : val;
    }

    private static Long readTierLong(VezAntiCheat plugin, String checkName, String key) {
        if (plugin.tierCfg() == null || plugin.tierCfg().checkSection(checkName) == null) return null;
        org.bukkit.configuration.ConfigurationSection section = plugin.tierCfg().checkSection(checkName);
        CheckTier tier = plugin.tierCfg().tierForCheck(checkName);
        org.bukkit.configuration.file.FileConfiguration tierFile =
                tier == null ? null : plugin.tierCfg().tierConfig(tier);
        boolean hasKey = section.contains(key)
                || (tierFile != null && tierFile.contains("defaults." + key));
        if (!hasKey) return null;
        long val = plugin.tierCfg().checkLong(checkName, key, Long.MIN_VALUE);
        return val == Long.MIN_VALUE ? null : val;
    }
}
