package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.velocity.PredictedTick;
import com.colin.vezanticheat.velocity.VelocitySession;
import com.colin.vezanticheat.velocity.VelocitySnapshot;
import com.colin.vezanticheat.velocity.VelocitySource;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpeedUtilEnvelopeTest {

    private VelocitySession session;
    private World world;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
        Location start = new Location(world, 0.0D, 64.0D, 0.0D);
        VelocitySnapshot snapshot = new VelocitySnapshot(
                1000L,
                new Vector(0.8D, 0.35D, 0.0D),
                start,
                VelocitySource.COMBAT,
                0.8D,
                0.35D,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                0,
                40,
                20.0D,
                false,
                false,
                false,
                null,
                false,
                null,
                0.0D,
                0);
        PredictedTick tick = new PredictedTick(0, -0.5D, 1.2D, 63.5D, 65.0D, -0.2D, 0.4D, null);
        session = new VelocitySession(snapshot, Arrays.asList(tick), 500L);
        session.maxHorizontal = 0.9D;
    }

    @Test
    public void kbEnvelopeAllowanceExceedsFlatFallback() {
        double allowance = KbSpeedAllowance.horizontalAllowanceFromSession(session, true, 1100L);
        assertTrue(allowance > 0.22D);
    }

    @Test
    public void kbEnvelopeInactiveAfterWindowExpires() {
        assertEquals(0.0D, KbSpeedAllowance.horizontalAllowanceFromSession(session, true, 2000L), 0.0001D);
    }
}
