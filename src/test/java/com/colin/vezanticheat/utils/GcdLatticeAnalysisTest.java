package com.colin.vezanticheat.utils;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.Assert.assertTrue;

public class GcdLatticeAnalysisTest {

    @Test
    public void latticeConformingRotationsHaveLowResidue() {
        Deque<Float> yaw = new ArrayDeque<Float>();
        Deque<Float> pitch = new ArrayDeque<Float>();
        for (int i = 0; i < 30; i++) {
            yaw.addLast(0.30F);
            pitch.addLast(0.15F);
        }
        double residue = GcdLatticeAnalysis.latticeResidueFraction(yaw, pitch);
        assertTrue(residue < 0.2D);
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
        double residue = GcdLatticeAnalysis.latticeResidueFraction(yaw, pitch);
        assertTrue(residue > 0.35D);
    }
}
