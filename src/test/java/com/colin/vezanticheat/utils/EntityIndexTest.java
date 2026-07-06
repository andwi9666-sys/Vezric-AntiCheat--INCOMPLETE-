package com.colin.vezanticheat.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

/**
 * Pure-surface tests: start() needs a live scheduler/world list, so lifecycle is
 * covered by the staging checklist instead. These guard the netty-thread read API
 * contract — empty index answers must be safe nulls/empties, never exceptions.
 */
public class EntityIndexTest {

    @Test
    public void emptyIndexAnswersSafely() {
        EntityIndex index = new EntityIndex(Mockito.mock(Plugin.class));
        Assert.assertNull(index.get(1));
        Assert.assertNull(index.getByUuid(UUID.randomUUID()));
        Assert.assertNull(index.getByUuid(null));
        Assert.assertEquals(0, index.size());
    }

    @Test
    public void proximityQueriesHandleNullAndEmpty() {
        EntityIndex index = new EntityIndex(Mockito.mock(Plugin.class));
        Assert.assertTrue(index.nearby(null, 2.0D).isEmpty());
        Assert.assertFalse(index.anyOtherEntityNear(null, 2.0D));
        // Non-null player against an empty snapshot: fast-path empty, player never dereferenced.
        Player player = Mockito.mock(Player.class);
        Assert.assertTrue(index.nearby(player, 2.0D).isEmpty());
        Assert.assertFalse(index.anyOtherEntityNear(player, 2.0D));
        // Zero/negative range is a no-op regardless of player.
        Assert.assertTrue(index.nearby(player, 0.0D).isEmpty());
        Assert.assertFalse(index.anyOtherEntityNear(player, -1.0D));
    }
}
