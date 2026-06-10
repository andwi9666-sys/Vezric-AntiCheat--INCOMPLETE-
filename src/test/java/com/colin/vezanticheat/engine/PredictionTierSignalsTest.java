package com.colin.vezanticheat.engine;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for Polar Prediction tier signal decomposition.
 */
public class PredictionTierSignalsTest {

    @Test
    public void offsetSignalUsesUncertaintyReducedOffset() {
        EngineResult result = EngineResult.builder()
                .checked(true)
                .offset(0.12D)
                .rawOffset(0.15D)
                .horizontalOffset(0.08D)
                .verticalOffset(0.04D)
                .build();
        assertEquals(0.12D, result.offset, 1.0E-6);
    }

    @Test
    public void couldSkipTickLenienceFlagPresent() {
        EngineResult skip = EngineResult.builder().checked(true).offset(0.05D).couldSkipTick(true).build();
        EngineResult noSkip = EngineResult.builder().checked(true).offset(0.05D).couldSkipTick(false).build();
        assertTrue(skip.couldSkipTick);
        assertFalse(noSkip.couldSkipTick);
    }

    @Test
    public void webBranchUsesInWebGate() {
        EngineResult inWeb = EngineResult.builder().checked(true).offset(0.09D).inWeb(true).build();
        EngineResult dry = EngineResult.builder().checked(true).offset(0.09D).inWeb(false).build();
        assertTrue(inWeb.inWeb);
        assertEquals(0.09D, inWeb.offset, 1.0E-6);
        assertFalse(dry.inWeb);
    }

    @Test
    public void waterBranchUsesInWaterGate() {
        EngineResult inWater = EngineResult.builder().checked(true).offset(0.07D).inWater(true).build();
        assertTrue(inWater.inWater);
        assertEquals(0.07D, inWater.liquidOffset(), 1.0E-6);
    }

    @Test
    public void groundMismatchDerivedFromPredictedVsClientGround() {
        EngineResult mismatch = EngineResult.builder()
                .checked(true)
                .predictedOnGround(true)
                .clientGround(false)
                .offset(0.1D)
                .build();
        assertTrue(mismatch.groundMismatch());
    }

    @Test
    public void timerDebtAndBlinkGapExposeMovementSignals() {
        EngineResult result = EngineResult.builder()
                .checked(true)
                .timerDebtMs(145.0D)
                .flyingGapMs(130L)
                .build();
        assertEquals(145.0D, result.timerDebt(), 1.0E-6);
        assertEquals(130.0D, result.blinkGap(), 1.0E-6);
    }

    @Test
    public void noSlowRatioReflectsUsingItemExcess() {
        EngineResult slow = EngineResult.builder()
                .checked(true)
                .usingItem(true)
                .noSlowExcess(0.04D)
                .build();
        assertEquals(1.04D, slow.noSlowRatio(), 1.0E-6);
        EngineResult normal = EngineResult.builder().checked(true).usingItem(false).build();
        assertEquals(1.0D, normal.noSlowRatio(), 1.0E-6);
    }

    @Test
    public void illegalSprintAndSneakFlagsReadable() {
        EngineResult sprint = EngineResult.builder()
                .checked(true)
                .illegalSprint(true)
                .illegalSneak(false)
                .build();
        assertTrue(sprint.illegalSprint());
        assertFalse(sprint.illegalSneak());
    }
}
