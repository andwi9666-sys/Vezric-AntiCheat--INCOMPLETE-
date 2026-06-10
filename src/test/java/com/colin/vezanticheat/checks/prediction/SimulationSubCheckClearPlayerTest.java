package com.colin.vezanticheat.checks.prediction;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SimulationSubCheckClearPlayerTest {

    @Test
    public void clearPlayerRemovesSharedSimulationBuffers() throws Exception {
        UUID id = UUID.randomUUID();
        seedBuffer(id, 4);
        assertNotNull(SimulationSubCheck.predictionSimulationSnapshot(id));

        SimulationSubCheck.clearPlayer(id);
        assertNull(SimulationSubCheck.predictionSimulationSnapshot(id));
    }

    @Test
    public void clearAllWipesEveryPlayer() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        seedBuffer(alice, 2);
        seedBuffer(bob, 3);

        SimulationSubCheck.clearAll();
        assertNull(SimulationSubCheck.predictionSimulationSnapshot(alice));
        assertNull(SimulationSubCheck.predictionSimulationSnapshot(bob));
    }

    @SuppressWarnings("unchecked")
    private static void seedBuffer(UUID id, int buffer) throws Exception {
        Field field = SimulationSubCheck.class.getDeclaredField("SHARED_BUFFER");
        field.setAccessible(true);
        Map<UUID, Integer> map = (ConcurrentHashMap<UUID, Integer>) field.get(null);
        map.put(id, buffer);
    }
}
