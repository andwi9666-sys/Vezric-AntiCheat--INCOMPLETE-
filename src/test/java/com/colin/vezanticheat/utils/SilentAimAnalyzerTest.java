package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.combat.math.RequiredRotationUtil;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.characteristics.CharSilentAimSignals;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Behavioral proof that the rewritten silent-aim detection actually fires on the injected-rotation
 * fingerprint (silent aim / server-side rotation, including a sudden 180 snap onto a target) and
 * stays silent on a legitimate human tracking flick.
 *
 * <p>Geometry mirrors {@code CombatAnalyzerAimCorrelationTest}: attacker eye at the origin, target
 * 3 blocks along +X. In Minecraft yaw space that means yaw=-90 pitch=0 aims EXACTLY at the target
 * (~0 deg error) while yaw=+90 looks 180 deg away (~180 deg error).</p>
 */
public class SilentAimAnalyzerTest {

    private static final double WIDTH = 0.6D;
    private static final double HEIGHT = 1.8D;
    private static final int PING = 30;
    private static final long WINDOW_MS = 200L;
    private static final long ATTACK_TIME = 1000L;

    private World world;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
    }

    private Location eye() {
        return new Location(world, 0.0D, 1.62D, 0.0D);
    }

    private Location targetFeet() {
        return new Location(world, 3.0D, 0.0D, 0.0D);
    }

    private Deque<PlayerData.RotationSample> ring(float[][] samples) {
        Deque<PlayerData.RotationSample> ring = new ArrayDeque<PlayerData.RotationSample>();
        for (float[] s : samples) {
            ring.addLast(new PlayerData.RotationSample(s[0], s[1], (long) s[2]));
        }
        return ring;
    }

    /**
     * Silent aim: the server-side rotation flicks from a heading 180 deg away ONTO the target via a
     * single impossible step, then snaps straight back. Attack-time error is ~0 (perfect aim), so only
     * the transient snap-and-restore exposes it.
     */
    @Test
    public void detectsImpossible180SnapOntoTargetAsSilentAim() {
        Deque<PlayerData.RotationSample> ring = ring(new float[][] {
                {90f, 0f, 880f},   // pre-snap heading: looking ~180 deg away from the target
                {90f, 0f, 930f},
                {-90f, 0f, 980f},  // landing: aims exactly at the target via a 180 deg / 50 ms step
                {90f, 0f, 1030f}   // restore: snaps right back to the away heading (the tell)
        });

        SilentAimAnalyzer.Result r = SilentAimAnalyzer.analyze(
                ring, new ArrayDeque<PlayerData.PositionSample>(),
                eye(), targetFeet(), WIDTH, HEIGHT, ATTACK_TIME, PING, WINDOW_MS);

        assertEquals(4, r.sampleCount);
        assertTrue("snap-TO target must be detected", r.hasSnapTo);
        assertTrue("snap-back/restore is the load-bearing tell", r.hasRestore);

        double snapRestore = SilentAimAnalyzer.snapRestoreScore(r);
        assertTrue("snap-restore score should be strong, was " + snapRestore, snapRestore > 0.5D);

        double vel = SilentAimAnalyzer.hitboxSnapVelocity(r);
        assertTrue("landing velocity must exceed the human snap threshold",
                vel > RequiredRotationUtil.snapAngularVelocityThreshold(PING));

        double velScore = CharSilentAimSignals.hitboxSnapVelocityScore(vel, PING);
        assertTrue("impossible-velocity score should saturate, was " + velScore, velScore > 0.9D);

        // BOTH strong transient signals trip -> the check's multi-signal rule (strong >= 2) is met.
        assertEquals(2, CharSilentAimSignals.strongSignalCount(snapRestore, velScore));
    }

    /**
     * Legit human: flicks smoothly onto the target and KEEPS aiming there. A snap-TO leg may exist,
     * but the player never returns to the old heading, so there is no restore -> no false positive.
     */
    @Test
    public void ignoresSmoothHumanTrackingFlickWithoutRestore() {
        Deque<PlayerData.RotationSample> ring = ring(new float[][] {
                {-130f, 0f, 850f}, // ~40 deg off
                {-120f, 0f, 900f}, // ~30 deg off
                {-110f, 0f, 950f}, // ~20 deg off
                {-100f, 0f, 1000f},// ~10 deg off
                {-90f, 0f, 1050f}, // on target
                {-90f, 0f, 1100f}  // HOLD on target (no snap-back)
        });

        SilentAimAnalyzer.Result r = SilentAimAnalyzer.analyze(
                ring, new ArrayDeque<PlayerData.PositionSample>(),
                eye(), targetFeet(), WIDTH, HEIGHT, ATTACK_TIME, PING, WINDOW_MS);

        assertFalse("a held flick must NOT register a snap-back restore", r.hasRestore);
        assertEquals("no restore -> zero snap-restore score (no false positive)",
                0.0D, SilentAimAnalyzer.snapRestoreScore(r), 1.0E-9D);

        double velScore = CharSilentAimSignals.hitboxSnapVelocityScore(
                SilentAimAnalyzer.hitboxSnapVelocity(r), PING);
        assertEquals("smooth ~200 deg/sec tracking is below the snap threshold",
                0.0D, velScore, 1.0E-9D);

        // No strong transient signal -> mathematically cannot reach the flag buffer.
        assertEquals(0, CharSilentAimSignals.strongSignalCount(
                SilentAimAnalyzer.snapRestoreScore(r), velScore));
    }

    @Test
    public void snapRestoreScoreRequiresBothSnapToAndRestore() {
        // hasSnapTo but NO restore -> 0 (humans who flick keep looking at the target).
        SilentAimAnalyzer.Result noRestore = new SilentAimAnalyzer.Result(
                2, 50.0D, 0.4D, true, false, 0.0D, 2000.0D, 0.02D, 0.3D, 0.5D, 5);
        assertEquals(0.0D, SilentAimAnalyzer.snapRestoreScore(noRestore), 1.0E-9D);

        // Both legs, perfect landing + full restore -> strong.
        SilentAimAnalyzer.Result full = new SilentAimAnalyzer.Result(
                2, 50.0D, 0.0D, true, true, 1.0D, 2000.0D, 0.02D, 0.3D, 0.5D, 5);
        assertTrue(SilentAimAnalyzer.snapRestoreScore(full) > 0.7D);
    }

    @Test
    public void hitboxVelocityScoreZeroBelowThresholdSaturatesAbove() {
        assertEquals(0.0D, CharSilentAimSignals.hitboxSnapVelocityScore(0.0D, PING), 1.0E-9D);
        assertEquals(0.0D, CharSilentAimSignals.hitboxSnapVelocityScore(
                RequiredRotationUtil.snapAngularVelocityThreshold(PING), PING), 1.0E-9D);
        assertTrue(CharSilentAimSignals.hitboxSnapVelocityScore(3000.0D, PING) > 0.9D);
    }

    @Test
    public void centerLockScoreRequiresTinyMarginAndJitter() {
        assertTrue("dead-center + zero jitter is suspicious",
                CharSilentAimSignals.centerLockScore(0.2D, 0.01D) > 0.0D);
        assertEquals("wide center margin is human", 0.0D,
                CharSilentAimSignals.centerLockScore(5.0D, 0.01D), 1.0E-9D);
        assertEquals("natural jitter is human", 0.0D,
                CharSilentAimSignals.centerLockScore(0.2D, 0.5D), 1.0E-9D);
    }

    @Test
    public void fuseStaysInUnitRangeAndWeightsSnapRestoreHeaviest() {
        double onlySnap = CharSilentAimSignals.fuse(1.0D, 0, 0, 0, 0, 0, 0);
        double onlyRamp = CharSilentAimSignals.fuse(0, 0, 0, 0, 0, 0, 1.0D);
        assertTrue("snap-restore must outweigh linear-ramp", onlySnap > onlyRamp);
        double all = CharSilentAimSignals.fuse(1, 1, 1, 1, 1, 1, 1);
        assertTrue("fused score stays in 0..1", all <= 1.0D && all > 0.0D);
    }

    @Test
    public void strongSignalCountCountsTransients() {
        assertEquals(2, CharSilentAimSignals.strongSignalCount(0.5D, 0.5D));
        assertEquals(1, CharSilentAimSignals.strongSignalCount(0.1D, 0.5D));
        assertEquals(0, CharSilentAimSignals.strongSignalCount(0.0D, 0.0D));
    }
}
