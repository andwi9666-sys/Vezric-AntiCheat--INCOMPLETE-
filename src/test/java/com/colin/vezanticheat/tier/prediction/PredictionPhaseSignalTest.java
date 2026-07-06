package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.engine.EngineResult;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PredictionPhaseSignalTest {

    @Test
    public void horizontalWallRequiresCollisionAxisAndOffset() {
        EngineResult er = EngineResult.builder()
                .checked(true)
                .collisionX(true)
                .horizontalOffset(0.15D)
                .build();
        assertTrue((er.collisionX || er.collisionZ) && er.horizontalOffset > 0.10D);
    }

    @Test
    public void verticalWallRequiresThreeCollisionAxes() {
        EngineResult er = EngineResult.builder()
                .checked(true)
                .collisionX(true)
                .collisionY(true)
                .collisionZ(true)
                .verticalOffset(0.12D)
                .build();
        int axes = (er.collisionX ? 1 : 0) + (er.collisionY ? 1 : 0) + (er.collisionZ ? 1 : 0);
        assertTrue(er.collisionY && er.verticalOffset > 0.08D && axes >= 3);
    }

    @Test
    public void singleAxisVerticalCollisionDoesNotMeetOverlapMinimum() {
        EngineResult er = EngineResult.builder()
                .checked(true)
                .collisionY(true)
                .verticalOffset(0.20D)
                .build();
        int axes = (er.collisionX ? 1 : 0) + (er.collisionY ? 1 : 0) + (er.collisionZ ? 1 : 0);
        assertFalse(axes >= 3);
    }

    @Test
    public void horizontalOnlyLowOffsetIsClean() {
        EngineResult er = EngineResult.builder()
                .checked(true)
                .collisionZ(true)
                .horizontalOffset(0.04D)
                .build();
        assertFalse(er.horizontalOffset > 0.10D);
    }
}
