package com.colin.vezanticheat.engine;

import com.colin.vezanticheat.utils.CombatUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * CombatRewind must never hand back null and must surface "could not compute" as an invalid result
 * rather than a zeroed-but-valid one. Consumers are hardened in Phase 3; here we lock the producer.
 */
public class CombatRewindNullSafetyTest {

    @Test
    public void computeWithNullPluginReturnsInvalidNotNull() {
        CombatResult result = CombatRewind.compute(null, null, null, null, 1, null, null, 1234L);
        Assert.assertNotNull("compute must never return null", result);
        Assert.assertFalse("missing inputs must yield an invalid result", result.isValid());
        Assert.assertEquals(1234L, result.timeMs);
    }

    @Test
    public void invalidFactoryProducesValidFalseResult() {
        CombatResult result = CombatResult.invalid(500L, "no-target");
        Assert.assertNotNull(result);
        Assert.assertFalse(result.isValid());
        Assert.assertFalse(result.tracked);
        Assert.assertEquals(500L, result.timeMs);
        Assert.assertEquals("no-target", result.debug);
    }

    @Test
    public void invalidFactoryNullDebugIsSafe() {
        CombatResult result = CombatResult.invalid(0L, null);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.isValid());
        Assert.assertNotNull(result.debug);
    }

    @Test
    public void builderDefaultsToValid() {
        CombatResult result = CombatResult.builder().timeMs(1L).tracked(true).build();
        Assert.assertTrue("a built result is valid unless marked otherwise", result.isValid());
    }

    @Test
    public void toReachContextReturnsNullForNull() {
        Assert.assertNull(CombatRewind.toReachContext(null));
    }

    @Test
    public void toReachContextReturnsNullForInvalidResult() {
        CombatResult invalid = CombatResult.invalid(10L, "untracked no-ctx");
        CombatUtil.ReachContext ctx = CombatRewind.toReachContext(invalid);
        Assert.assertNull("invalid results must not be adapted into a usable reach context", ctx);
    }
}
