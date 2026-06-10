package com.colin.vezanticheat.combat;

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class CombatStaffAlerterTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Test
    public void cooldownBlocksRepeatAlerts() {
        CombatAnalysisSettings settings = new CombatAnalysisSettings();
        settings.load(null);
        CombatStaffAlerter alerter = new CombatStaffAlerter(null);

        long start = 10_000L;
        Assert.assertTrue(alerter.shouldSendAlert(ATTACKER, 16.0D, start, settings));
        alerter.recordAlertForTest(ATTACKER, start, 16.0D);

        Assert.assertFalse(alerter.shouldSendAlert(ATTACKER, 17.0D, start + 1000L, settings));
    }

    @Test
    public void bufferSpikeBypassesCooldown() {
        CombatAnalysisSettings settings = new CombatAnalysisSettings();
        settings.load(null);
        CombatStaffAlerter alerter = new CombatStaffAlerter(null);

        long start = 10_000L;
        alerter.recordAlertForTest(ATTACKER, start, 16.0D);

        Assert.assertTrue(alerter.shouldSendAlert(ATTACKER, 19.5D, start + 500L, settings));
    }

    @Test
    public void formatAlertReplacesPlaceholders() {
        CombatAnalysisSettings settings = new CombatAnalysisSettings();
        settings.load(null);
        CombatStaffAlerter alerter = new CombatStaffAlerter(null);

        String formatted = alerter.formatAlertForTest("Steve", 15.5D, "reach=4.0", settings);
        Assert.assertTrue(formatted.contains("Steve"));
        Assert.assertTrue(formatted.contains("15.50"));
        Assert.assertTrue(formatted.contains("reach=4.0"));
        Assert.assertTrue(formatted.contains("CombatAnalysis"));
    }

    @Test
    public void resolveReasonPrefersSuspiciousReason() {
        CombatHitResult result = CombatHitResult.builder()
                .classification(CombatHitClassification.BAD)
                .addReason("reach=4.0")
                .addReason("class=BAD")
                .build();

        Assert.assertEquals("reach=4.0", CombatStaffAlerter.resolveReason(result));
    }
}
