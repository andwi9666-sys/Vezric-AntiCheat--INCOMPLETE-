package com.colin.vezanticheat.checks;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertNull;

public class CheckClearPlayerTest {

    @Test
    public void clearPlayerNullIsNoOp() {
        Check.clearPlayer(null);
    }

    @Test
    public void clearPlayerOnEmptyStateIsSafe() {
        UUID id = UUID.randomUUID();
        Check.clearPlayer(id);
        assertNull(Check.classicSimulationSnapshot(id));
    }
}
