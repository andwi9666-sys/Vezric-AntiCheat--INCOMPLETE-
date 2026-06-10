package com.colin.vezanticheat.combat;

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class CombatEvidenceReasonsTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    public void getLastSuspiciousReasonsReturnsTail() {
        CombatEvidence evidence = new CombatEvidence(ATTACKER, CombatConfig.defaults());

        for (int i = 0; i < 8; i++) {
            evidence.addResult(CombatHitResult.builder()
                    .attackerUuid(ATTACKER)
                    .classification(CombatHitClassification.BAD)
                    .finalScore(1.0D)
                    .addReason("reason-" + i)
                    .build());
        }

        Assert.assertEquals(5, evidence.getLastSuspiciousReasons(5).size());
        Assert.assertEquals("reason-3", evidence.getLastSuspiciousReasons(5).get(0));
        Assert.assertEquals("reason-7", evidence.getLastSuspiciousReasons(5).get(4));
    }

    @Test
    public void getLastSuspiciousReasonsZeroLimitReturnsEmpty() {
        CombatEvidence evidence = new CombatEvidence(ATTACKER, CombatConfig.defaults());
        evidence.addResult(CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .classification(CombatHitClassification.BAD)
                .finalScore(1.0D)
                .addReason("test")
                .build());

        Assert.assertTrue(evidence.getLastSuspiciousReasons(0).isEmpty());
    }
}
