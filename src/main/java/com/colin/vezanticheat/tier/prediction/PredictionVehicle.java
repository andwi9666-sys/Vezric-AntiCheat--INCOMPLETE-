package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.entity.Player;

/**
 * RT5-002 vehicle speed envelope. Flags are raised by {@link com.colin.vezanticheat.engine.MovementCheckRunner}
 * while the player is mounted; this check owns VL/buffer config only.
 */
public final class PredictionVehicle extends TierCheck {

    public PredictionVehicle(VezAntiCheat plugin) {
        super(plugin, "PredictionVehicle", CheckTier.PREDICTION);
    }

    /**
     * Called from {@link com.colin.vezanticheat.engine.MovementCheckRunner} while the player is mounted.
     */
    public void evaluateMountMovement(Player p, PlayerData data, org.bukkit.Location from,
                                      org.bukkit.Location to) {
        if (p == null || data == null || from == null || to == null) return;
        org.bukkit.entity.Entity vehicle = p.getVehicle();
        if (vehicle == null) {
            data.setVehicleSpeedViolationStreak(0);
            return;
        }
        double horizontal = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        double boatMax = plugin.getConfig().getDouble("engine.vehicle.boat-max-per-tick", 0.65D);
        double cartMax = plugin.getConfig().getDouble("engine.vehicle.minecart-max-per-tick", 0.50D);
        double defaultMax = plugin.getConfig().getDouble("engine.vehicle.default-max-per-tick", 0.55D);
        double tolerance = plugin.getConfig().getDouble("engine.vehicle.tolerance", 1.15D);
        double max = com.colin.vezanticheat.utils.VehicleMovementUtil.maxHorizontalPerTick(
                vehicle, boatMax, cartMax, defaultMax);
        boolean exceeded = com.colin.vezanticheat.utils.VehicleMovementUtil.exceedsEnvelope(
                horizontal, max, tolerance);
        int streak = com.colin.vezanticheat.utils.VehicleMovementUtil.nextViolationStreak(
                data.getVehicleSpeedViolationStreak(), exceeded, 0);
        data.setVehicleSpeedViolationStreak(streak);
        int bufferToFlag = plugin.getConfig().getInt("engine.vehicle.buffer-to-flag", 4);
        if (!com.colin.vezanticheat.utils.VehicleMovementUtil.shouldFlag(streak, bufferToFlag)) return;

        fail(p, data, plugin.tierCfg().checkDouble(name(), "vl", 1.0D),
                "vehicle-speed h=" + horizontal + " max=" + max + " streak=" + streak);
        data.setVehicleSpeedViolationStreak(0);
    }
}
