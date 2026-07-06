package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimulationResultTest {

    @Test
    public void mapsSimulationResultToLegacyEngineResultAdapter() {
        SimulationResult result = SimulationResult.builder()
                .timeMs(100L)
                .checked(true)
                .offset(0.2D)
                .rawOffset(0.23D)
                .horizontalOffset(0.18D)
                .verticalOffset(0.05D)
                .expectedMotion(new Vector(0.1D, 0.0D, 0.0D))
                .actualMotion(new Vector(0.28D, 0.0D, 0.0D))
                .predictedGround(true)
                .clientGround(true)
                .onIce(true)
                .blockBelow(Material.ICE)
                .surface("ice")
                .timerDebtMs(130.0D)
                .debug("test")
                .build();

        EngineResult engine = result.toEngineResult();
        assertEquals(0.2D, engine.offset, 0.0001D);
        assertTrue(engine.onIce);
        assertEquals(130.0D, engine.timerDebtMs, 0.0001D);
    }

    @Test
    public void setbackRequiresBlatantOrFamilyPair() {
        SimulationResult result = SimulationResult.builder()
                .offset(0.16D)
                .expectedMotion(new Vector())
                .actualMotion(new Vector(0.2D, 0.0D, 0.0D))
                .violations(Arrays.asList(
                        new MovementViolation(MovementFamily.SPEED, 0.4D, 0.16D, null, null, 0, "speed", true),
                        new MovementViolation(MovementFamily.FRICTION, 0.4D, 0.16D, null, null, 0, "friction", true)))
                .build();

        assertTrue(SetbackManager.shouldSetback(result));
    }
}
