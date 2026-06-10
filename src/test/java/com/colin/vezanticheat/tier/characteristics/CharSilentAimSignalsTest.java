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
}
