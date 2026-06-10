package com.colin.vezanticheat.combat;

import org.junit.Assert;
import org.junit.Test;

public class CombatScoreComposerTest {

    @Test
    public void impossibleClassificationFloorIsFive() {
        double score = CombatScoreComposer.computeGeometryScore(
                HitboxExpansionTier.MISS,
                CombatHitClassification.IMPOSSIBLE,
                30,
                false,
                true);
        Assert.assertEquals(5.0D, score, 0.001D);
    }

    @Test
    public void badClassificationFloorIsThree() {
        double score = CombatScoreComposer.computeGeometryScore(
                HitboxExpansionTier.MISS,
                CombatHitClassification.BAD,
                30,
                false,
                true);
        Assert.assertEquals(5.0D, score, 0.001D);
    }

    @Test
    public void lineOfSightPenaltyAppliedForLenientMiss() {
        double score = CombatScoreComposer.computeGeometryScore(
                HitboxExpansionTier.SMALL,
                CombatHitClassification.LENIENT,
                30,
                false,
                false);
        Assert.assertEquals(0.75D, score, 0.001D);
    }

    @Test
    public void highPingExpansionDiscountNotOverriddenByFloor() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("combat-analysis.scoring.very-lenient-score", 1.0D);
        CombatConfig config = CombatConfig.fromConfig(yaml);

        double score = CombatScoreComposer.computeGeometryScore(
                HitboxExpansionTier.FULL,
                CombatHitClassification.VERY_LENIENT,
                200,
                true,
                true,
                config);
        Assert.assertEquals(1.05D, score, 0.001D);
    }

    @Test
    public void knockbackLeniencyReducesBehaviorOnly() {
        CombatConfig config = CombatConfig.defaults();
        double geometry = 1.0D;
        double behavior = 2.0D;
        double base = CombatScoreComposer.computeBaseScore(geometry, behavior);
        double finalScore = CombatScoreComposer.computeFinalScore(
                geometry,
                behavior,
                CombatHitClassification.VERY_LENIENT,
                HitboxExpansionTier.FULL,
                true,
                config);

        Assert.assertEquals(3.0D, base, 0.001D);
        Assert.assertEquals(2.5D, finalScore, 0.001D);
    }

    @Test
    public void finalizeScoresCombinesGeometryAndBehavior() {
        CombatHitResult merged = CombatHitResult.builder()
                .classification(CombatHitClassification.CLEAN)
                .expansionTier(HitboxExpansionTier.NORMAL)
                .ping(30)
                .lineOfSightValid(true)
                .preAimResult(PreAimResult.builder().suspiciousScore(1.0D).build())
                .build();

        CombatScoreComposer.ScorePair scores = CombatScoreComposer.finalizeScores(
                merged, false, CombatConfig.defaults());

        Assert.assertEquals(1.0D, scores.getBaseScore(), 0.001D);
        Assert.assertEquals(1.0D, scores.getFinalScore(), 0.001D);
    }
}
