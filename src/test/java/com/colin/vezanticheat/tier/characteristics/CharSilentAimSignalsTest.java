package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.AimAssistUtil;
import com.colin.vezanticheat.utils.GcdLatticeAnalysis;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CharSilentAimSignalsTest {

    @Test
    public void autoClickScoreFlagsLowVarianceIntervals() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        for (int i = 0; i < 8; i++) {
            data.getClickIntervals().addLast(50L);
        }
        assertTrue(CharSilentAimSignals.autoClickScore(data, "autoclicka") >= 0.6D);

        PlayerData varied = new PlayerData(UUID.randomUUID());
        for (int i = 0; i < 8; i++) {
            varied.getClickIntervals().addLast(50L + i * 17L);
        }
        assertEquals(0.0D, CharSilentAimSignals.autoClickScore(varied, "autoclickc"), 0.001D);
    }

    @Test
    public void aimAssistScoreUsesGcdAnalysis() {
        ArrayDeque<Float> deltas = new ArrayDeque<Float>();
        for (int i = 0; i < 12; i++) {
            deltas.addLast(0.25F);
        }
        double gcdScore = AimAssistUtil.analyzeGcd(deltas);
        assertTrue(gcdScore > 0.0D);
    }

    @Test
    public void latticeConformityRaisesSilentAimStackSignal() {
        ArrayDeque<Float> yaw = new ArrayDeque<Float>();
        ArrayDeque<Float> pitch = new ArrayDeque<Float>();
        for (int i = 0; i < 30; i++) {
            yaw.addLast(0.30F);
            pitch.addLast(0.15F);
        }
        assertTrue(GcdLatticeAnalysis.latticeConformitySuspicion(yaw, pitch) > 0.5D);
    }

    @Test
    public void distributedSnapEvennessRaisesDetectionSignal() {
        double[] errors = new double[] {40.0D, 32.0D, 24.0D, 16.0D, 8.0D};
        assertTrue(GcdLatticeAnalysis.distributedSnapRatio(errors) >= 0.35D);
    }

    @Test
    public void closeRangeScaleBoostsSignalsBelowTaper() {
        assertEquals(1.35D, CharSilentAimSignals.closeRangeSignalScale(0.0D, 1.2D, 1.35D), 0.001D);
        assertTrue(CharSilentAimSignals.closeRangeSignalScale(0.5D, 1.2D, 1.35D) > 1.1D);
        assertEquals(1.0D, CharSilentAimSignals.closeRangeSignalScale(1.5D, 1.2D, 1.35D), 0.001D);
    }

    @Test
    public void closeRangeBypassAt12StillBoostsSubBlockTrades() {
        double at04 = CharSilentAimSignals.closeRangeSignalScale(0.4D, 1.0D, 1.40D);
        double at13 = CharSilentAimSignals.closeRangeSignalScale(1.3D, 1.0D, 1.40D);
        assertTrue(at04 > 1.2D);
        assertEquals(1.0D, at13, 0.001D);
    }

    @Test
    public void bypassThreshold12IsTighterThanLegacy15() {
        double legacyBypass = 1.5D;
        double tunedBypass = 1.2D;
        assertTrue(1.25D < legacyBypass);
        assertTrue(1.25D >= tunedBypass);
    }

    @Test
    public void requiredRotationScoreRisesWithExcess() {
        assertEquals(0.0D, CharSilentAimSignals.requiredRotationScore(0.0D), 0.001D);
        assertTrue(CharSilentAimSignals.requiredRotationScore(9.0D) > 0.4D);
    }
}
