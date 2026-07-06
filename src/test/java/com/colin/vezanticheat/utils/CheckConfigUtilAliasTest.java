package com.colin.vezanticheat.utils;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CheckConfigUtilAliasTest {

    @Test
    public void reachAliasesRemovedHitboxBacktrackStillMapped() throws Exception {
        Map<String, String> legacyToTier = legacyToTierMap();
        // Reach checks were removed by request; their config aliases no longer resolve.
        assertNull(legacyToTier.get("PrismReachA"));
        assertNull(legacyToTier.get("PrismReachB"));
        assertNull(legacyToTier.get("PrismReachC"));
        // Other absorbed combat signals remain aliased to PrismInteractionLegality.
        assertEquals("PrismInteractionLegality", legacyToTier.get("PrismHitboxA"));
        assertEquals("PrismInteractionLegality", legacyToTier.get("PrismBackTrack"));
    }

    @Test
    public void absorbedNoRotationAliasesMapToInteractionLegality() throws Exception {
        Map<String, String> legacyToTier = legacyToTierMap();
        assertEquals("PrismInteractionLegality", legacyToTier.get("PrismNoRotationA"));
        assertEquals("PrismInteractionLegality", legacyToTier.get("PrismNoRotationB"));
        assertEquals("PrismInteractionLegality", legacyToTier.get("PrismRotationRay"));
    }

    @Test
    public void lagRangeAliasMapsToInteractionLegality() throws Exception {
        Map<String, String> legacyToTier = legacyToTierMap();
        assertEquals("PrismInteractionLegality", legacyToTier.get("PrismLagRange"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> legacyToTierMap() throws Exception {
        Field field = CheckConfigUtil.class.getDeclaredField("LEGACY_TO_TIER");
        field.setAccessible(true);
        return (Map<String, String>) field.get(null);
    }
}
