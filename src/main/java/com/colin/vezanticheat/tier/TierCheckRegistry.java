package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;

import com.colin.vezanticheat.tier.characteristics.CharAimAssistA;
import com.colin.vezanticheat.tier.characteristics.CharAimAssistB;
import com.colin.vezanticheat.tier.characteristics.CharAimAssistC;
import com.colin.vezanticheat.tier.characteristics.CharAimCenter;
import com.colin.vezanticheat.tier.characteristics.CharAimCorrelation;
import com.colin.vezanticheat.tier.characteristics.CharAimReset;
import com.colin.vezanticheat.tier.characteristics.CharAimSnap;
import com.colin.vezanticheat.tier.characteristics.CharCombatTiming;
import com.colin.vezanticheat.tier.characteristics.CharCriticalsA;
import com.colin.vezanticheat.tier.characteristics.CharCriticalsB;
import com.colin.vezanticheat.tier.characteristics.CharCriticalsC;
import com.colin.vezanticheat.tier.characteristics.CharSilentAim;
import com.colin.vezanticheat.tier.characteristics.CharTimerLegacy;
import com.colin.vezanticheat.tier.characteristics.CharVelocityPattern;
import com.colin.vezanticheat.tier.prism.PrismAutoClickA;
import com.colin.vezanticheat.tier.prism.PrismAutoClickB;
import com.colin.vezanticheat.tier.prism.PrismAutoClickC;
import com.colin.vezanticheat.tier.prism.PrismAutoClickD;
import com.colin.vezanticheat.tier.prism.PrismBadPacketsA;
import com.colin.vezanticheat.tier.prism.PrismBadPacketsB;
import com.colin.vezanticheat.tier.prism.PrismBadPacketsC;
import com.colin.vezanticheat.tier.prism.PrismBadPacketsD;
import com.colin.vezanticheat.tier.prism.PrismBadPacketsE;
import com.colin.vezanticheat.tier.prism.PrismBadPacketsF;
import com.colin.vezanticheat.tier.prism.PrismBadPacketsK;
import com.colin.vezanticheat.tier.prism.PrismBadPacketsL;
import com.colin.vezanticheat.tier.prism.PrismBlockSight;
import com.colin.vezanticheat.tier.prism.PrismFastBreakA;
import com.colin.vezanticheat.tier.prism.PrismFastBreakB;
import com.colin.vezanticheat.tier.prism.PrismInteractReach;
import com.colin.vezanticheat.tier.prism.PrismInteractionLegality;
import com.colin.vezanticheat.tier.prism.PrismInventoryA;
import com.colin.vezanticheat.tier.prism.PrismInventoryB;
import com.colin.vezanticheat.tier.prism.PrismInventoryC;
import com.colin.vezanticheat.tier.prism.PrismMultiActionsA;
import com.colin.vezanticheat.tier.prism.PrismMultiActionsB;
import com.colin.vezanticheat.tier.prism.PrismPacketOrderA;
import com.colin.vezanticheat.tier.prism.PrismPacketOrderB;
import com.colin.vezanticheat.tier.prism.PrismPacketOrderC;
import com.colin.vezanticheat.tier.prism.PrismPacketOrderD;
import com.colin.vezanticheat.tier.prism.PrismScaffoldA;
import com.colin.vezanticheat.tier.prism.PrismScaffoldB;
import com.colin.vezanticheat.tier.prism.PrismScaffoldC;
import com.colin.vezanticheat.tier.prism.PrismScaffoldD;
import com.colin.vezanticheat.tier.prism.PrismScaffoldE;
import com.colin.vezanticheat.tier.prism.PrismSetbackAccept;
import com.colin.vezanticheat.tier.prism.PrismTransactionA;
import com.colin.vezanticheat.tier.prism.PrismTransactionB;
import com.colin.vezanticheat.tier.simulation.SimulationBlinkDebt;
import com.colin.vezanticheat.tier.simulation.SimulationClimbable;
import com.colin.vezanticheat.tier.simulation.SimulationCombo;
import com.colin.vezanticheat.tier.simulation.SimulationEntityPush;
import com.colin.vezanticheat.tier.simulation.SimulationExplosion;
import com.colin.vezanticheat.tier.simulation.SimulationFriction;
import com.colin.vezanticheat.tier.simulation.SimulationIce;
import com.colin.vezanticheat.tier.simulation.SimulationJump;
import com.colin.vezanticheat.tier.simulation.SimulationKnockback;
import com.colin.vezanticheat.tier.simulation.SimulationLiquid;
import com.colin.vezanticheat.tier.simulation.SimulationNoSlow;
import com.colin.vezanticheat.tier.simulation.SimulationOffsetHorizontal;
import com.colin.vezanticheat.tier.simulation.SimulationOffsetVertical;
import com.colin.vezanticheat.tier.simulation.SimulationSetbackRecovery;
import com.colin.vezanticheat.tier.simulation.SimulationSlime;
import com.colin.vezanticheat.tier.simulation.SimulationSneak;
import com.colin.vezanticheat.tier.simulation.SimulationSpider;
import com.colin.vezanticheat.tier.simulation.SimulationSprint;
import com.colin.vezanticheat.tier.simulation.SimulationWeb;
import com.colin.vezanticheat.tier.prediction.PredictionBlink;
import com.colin.vezanticheat.tier.prediction.PredictionExplosion;
import com.colin.vezanticheat.tier.prediction.PredictionFly;
import com.colin.vezanticheat.tier.prediction.PredictionFlyBob;
import com.colin.vezanticheat.tier.prediction.PredictionFlyBurst;
import com.colin.vezanticheat.tier.prediction.PredictionFlyGravity;
import com.colin.vezanticheat.tier.prediction.PredictionGroundSpoof;
import com.colin.vezanticheat.tier.prediction.PredictionGroundSpoofDescent;
import com.colin.vezanticheat.tier.prediction.PredictionJesus;
import com.colin.vezanticheat.tier.prediction.PredictionNoFall;
import com.colin.vezanticheat.tier.prediction.PredictionNoFallBlink;
import com.colin.vezanticheat.tier.prediction.PredictionNoFallReset;
import com.colin.vezanticheat.tier.prediction.PredictionNoSlow;
import com.colin.vezanticheat.tier.prediction.PredictionOffset;
import com.colin.vezanticheat.tier.prediction.PredictionPhase;
import com.colin.vezanticheat.tier.prediction.PredictionSpeed;
import com.colin.vezanticheat.tier.prediction.PredictionSpider;
import com.colin.vezanticheat.tier.prediction.PredictionStep;
import com.colin.vezanticheat.tier.prediction.PredictionTimer;
import com.colin.vezanticheat.tier.prediction.PredictionVelocity;
import com.colin.vezanticheat.tier.prediction.PredictionWater;
import com.colin.vezanticheat.tier.prediction.PredictionWeb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Registry of all Polar-tier checks. */
public final class TierCheckRegistry {

    private final List<TierCheck> checks = new ArrayList<TierCheck>();
    private final Map<String, TierCheck> byName = new HashMap<String, TierCheck>();

    public TierCheckRegistry(VezAntiCheat plugin) {
        // Characteristics — combat heuristics only
        add(new CharAimAssistA(plugin));
        add(new CharAimAssistB(plugin));
        add(new CharAimAssistC(plugin));
        add(new CharAimCenter(plugin));
        add(new CharAimCorrelation(plugin));
        add(new CharAimReset(plugin));
        add(new CharAimSnap(plugin));
        add(new CharCombatTiming(plugin));
        add(new CharCriticalsA(plugin));
        add(new CharCriticalsB(plugin));
        add(new CharCriticalsC(plugin));
        add(new CharSilentAim(plugin));
        add(new CharTimerLegacy(plugin));
        add(new CharVelocityPattern(plugin));

        // Prism — Polar pattern + interaction + invalid protocol subset
        add(new PrismAutoClickA(plugin));
        add(new PrismAutoClickB(plugin));
        add(new PrismAutoClickC(plugin));
        add(new PrismAutoClickD(plugin));
        add(new PrismScaffoldA(plugin));
        add(new PrismScaffoldB(plugin));
        add(new PrismScaffoldC(plugin));
        add(new PrismScaffoldD(plugin));
        add(new PrismScaffoldE(plugin));
        add(new PrismInventoryA(plugin));
        add(new PrismInventoryB(plugin));
        add(new PrismInventoryC(plugin));
        add(new PrismInteractionLegality(plugin));
        add(new PrismFastBreakA(plugin));
        add(new PrismFastBreakB(plugin));
        add(new PrismBlockSight(plugin));
        add(new PrismBadPacketsA(plugin));
        add(new PrismBadPacketsB(plugin));
        add(new PrismBadPacketsC(plugin));
        add(new PrismBadPacketsD(plugin));
        add(new PrismBadPacketsE(plugin));
        add(new PrismBadPacketsF(plugin));
        add(new PrismBadPacketsK(plugin));
        add(new PrismBadPacketsL(plugin));
        add(new PrismMultiActionsA(plugin));
        add(new PrismMultiActionsB(plugin));
        add(new PrismInteractReach(plugin));
        add(new PrismPacketOrderA(plugin));
        add(new PrismPacketOrderB(plugin));
        add(new PrismPacketOrderC(plugin));
        add(new PrismPacketOrderD(plugin));
        add(new PrismSetbackAccept(plugin));
        add(new PrismTransactionA(plugin));
        add(new PrismTransactionB(plugin));

        add(new SimulationBlinkDebt(plugin));
        add(new SimulationClimbable(plugin));
        add(new SimulationCombo(plugin));
        add(new SimulationEntityPush(plugin));
        add(new SimulationExplosion(plugin));
        add(new SimulationFriction(plugin));
        add(new SimulationIce(plugin));
        add(new SimulationJump(plugin));
        add(new SimulationKnockback(plugin));
        add(new SimulationLiquid(plugin));
        add(new SimulationNoSlow(plugin));
        add(new SimulationOffsetHorizontal(plugin));
        add(new SimulationOffsetVertical(plugin));
        add(new SimulationSetbackRecovery(plugin));
        add(new SimulationSlime(plugin));
        add(new SimulationSneak(plugin));
        add(new SimulationSpider(plugin));
        add(new SimulationSprint(plugin));
        add(new SimulationWeb(plugin));
        add(new PredictionBlink(plugin));
        add(new PredictionExplosion(plugin));
        add(new PredictionFly(plugin));
        add(new PredictionFlyBob(plugin));
        add(new PredictionFlyBurst(plugin));
        add(new PredictionFlyGravity(plugin));
        add(new PredictionGroundSpoof(plugin));
        add(new PredictionGroundSpoofDescent(plugin));
        add(new PredictionNoFall(plugin));
        add(new PredictionNoFallBlink(plugin));
        add(new PredictionNoFallReset(plugin));
        add(new PredictionJesus(plugin));
        add(new PredictionNoSlow(plugin));
        add(new PredictionOffset(plugin));
        add(new PredictionPhase(plugin));
        add(new PredictionSpeed(plugin));
        add(new PredictionSpider(plugin));
        add(new PredictionStep(plugin));
        add(new PredictionTimer(plugin));
        add(new PredictionVelocity(plugin));
        add(new PredictionWater(plugin));
        add(new PredictionWeb(plugin));
    }

    private void add(TierCheck check) {
        checks.add(check);
        byName.put(check.name(), check);
    }

    public List<TierCheck> all() { return Collections.unmodifiableList(checks); }

    public TierCheck get(String name) { return byName.get(name); }

    public int count() { return checks.size(); }

    public List<TierCheck> forTier(CheckTier tier) {
        List<TierCheck> out = new ArrayList<TierCheck>();
        for (TierCheck check : checks) {
            if (check.tier() == tier) out.add(check);
        }
        return out;
    }
}
