package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.MovementEnforcement;
import org.bukkit.entity.Player;

import java.util.EnumSet;

public final class SetbackManager {

    private SetbackManager() {}

    public static boolean shouldSetback(SimulationResult result) {
        if (result == null || result.violations.isEmpty()) return false;
        EnumSet<MovementFamily> families = result.families();
        for (MovementViolation violation : result.violations) {
            if (violation.setbackEligible && violation.blatant()) return true;
        }
        return pair(families, MovementFamily.SPEED, MovementFamily.FRICTION)
                || pair(families, MovementFamily.FLY, MovementFamily.GROUND_SPOOF)
                || pair(families, MovementFamily.TIMER, MovementFamily.BLINK)
                || pair(families, MovementFamily.NOSLOW, MovementFamily.SPEED)
                || pair(families, MovementFamily.VELOCITY, MovementFamily.SPEED)
                || pair(families, MovementFamily.EXPLOSION, MovementFamily.SPEED)
                || pair(families, MovementFamily.NOFALL, MovementFamily.GROUND_SPOOF)
                || families.contains(MovementFamily.COMBO);
    }

    public static void enforceIfNeeded(VezAntiCheat plugin, Player player, PlayerData data, SimulationResult result) {
        if (plugin == null || player == null || data == null || result == null) return;
        if (!plugin.getConfig().getBoolean("movement-engine.setback.enabled", true)) return;
        if (!shouldSetback(result)) return;
        MovementEnforcement.requestBlatantEnforcement(plugin, player, data, "movement-engine " + result.debug);
    }

    private static boolean pair(EnumSet<MovementFamily> families, MovementFamily a, MovementFamily b) {
        return families.contains(a) && families.contains(b);
    }
}
