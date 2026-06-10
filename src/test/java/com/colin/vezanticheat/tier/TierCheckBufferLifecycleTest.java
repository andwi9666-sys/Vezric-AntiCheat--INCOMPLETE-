package com.colin.vezanticheat.tier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

/**
 * Verifies the static buffer maps in {@link TierCheck} are cleaned up by clearPlayer/clearAll so
 * they do not leak per-player state across quit/reload.
 */
public class TierCheckBufferLifecycleTest {

    /** Concrete probe exposing the protected buffer accessors. No plugin needed for buffer math. */
    private static final class ProbeCheck extends TierCheck {
        ProbeCheck(String name) {
            super(null, name, CheckTier.PRISM);
        }

        int read(UUID id) { return buffer(id); }
        void write(UUID id, int value) { setBuffer(id, value); }
    }

    private ProbeCheck checkA;
    private ProbeCheck checkB;

    @Before
    public void setUp() {
        TierCheck.clearAll();
        checkA = new ProbeCheck("ProbeCheckA");
        checkB = new ProbeCheck("ProbeCheckB");
    }

    @Test
    public void clearPlayerRemovesOnlyThatPlayersKeysAcrossAllChecks() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        checkA.write(alice, 3);
        checkB.write(alice, 5);
        checkA.write(bob, 7);

        Assert.assertEquals(3, checkA.read(alice));
        Assert.assertEquals(5, checkB.read(alice));
        Assert.assertEquals(7, checkA.read(bob));

        TierCheck.clearPlayer(alice);

        // Alice's buffers across both checks are gone.
        Assert.assertEquals(0, checkA.read(alice));
        Assert.assertEquals(0, checkB.read(alice));
        // Bob is untouched.
        Assert.assertEquals(7, checkA.read(bob));
    }

    @Test
    public void clearPlayerNullIsNoOp() {
        UUID alice = UUID.randomUUID();
        checkA.write(alice, 4);
        TierCheck.clearPlayer(null);
        Assert.assertEquals(4, checkA.read(alice));
    }

    @Test
    public void clearAllWipesEveryPlayerAndCheck() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        checkA.write(alice, 2);
        checkB.write(bob, 9);

        TierCheck.clearAll();

        Assert.assertEquals(0, checkA.read(alice));
        Assert.assertEquals(0, checkB.read(bob));
    }

    @Test
    public void setBufferZeroRemovesKeySoClearStaysClean() {
        UUID alice = UUID.randomUUID();
        checkA.write(alice, 1);
        checkA.write(alice, 0);
        Assert.assertEquals(0, checkA.read(alice));
        // clearPlayer on an already-empty player must not throw.
        TierCheck.clearPlayer(alice);
        Assert.assertEquals(0, checkA.read(alice));
    }
}
