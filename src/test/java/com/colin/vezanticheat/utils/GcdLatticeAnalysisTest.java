package com.colin.vezanticheat.utils;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.Assert.assertTrue;

public class GcdLatticeAnalysisTest {

    @Test
    public void latticeConformingRotationsRaiseConformitySuspicion() {
        Deque<Float> yaw = new ArrayDeque<Float>();
        Deque<Float> pitch = new ArrayDeque<Float>();
        for (int i = 0; i < 30; i++) {
            yaw.addLast(0.30F);
            pitch.addLast(0.15F);
        }
        assertTrue(GcdLatticeAnalysis.latticeConformitySuspicion(yaw, pitch) > 0.5D);
    }

    @Test
    public void offLatticeRotationsRaiseResidue() {
        Deque<Float> yaw = new ArrayDeque<Float>();
        Deque<Float> pitch = new ArrayDeque<Float>();
        float[] pattern = new float[] {0.11F, 0.37F, 0.53F, 0.19F, 0.41F, 0.67F};
        for (int i = 0; i < 30; i++) {
            yaw.addLast(pattern[i % pattern.length]);
            pitch.addLast(pattern[(i + 1) % pattern.length]);
        }
        assertTrue(GcdLatticeAnalysis.latticeResidueFraction(yaw, pitch) > 0.35D);
    }

    @Test
    public void distributedSnapDetectsEvenlySpreadAlignment() {
        double[] errors = new double[] {40.0D, 32.0D, 24.0D, 16.0D, 8.0D};
        assertTrue(GcdLatticeAnalysis.distributedSnapRatio(errors) >= 0.35D);
    }
}
