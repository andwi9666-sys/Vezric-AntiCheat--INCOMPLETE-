package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.AimAssistUtil;
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
}
