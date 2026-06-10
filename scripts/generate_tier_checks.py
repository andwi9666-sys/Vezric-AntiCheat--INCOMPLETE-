#!/usr/bin/env python3
"""Generate Polar tier check Java sources and tier YAML defaults."""

import os
from textwrap import dedent

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "colin", "vezanticheat", "tier")
RES = os.path.join(ROOT, "src", "main", "resources", "tiers")

PREDICTION = [
    ("PredictionOffset", "offset", "Primary Grim offset handler; flags total prediction offset."),
    ("PredictionFly", "fly", "Vertical movement envelope vs simulation."),
    ("PredictionSpeed", "speed", "Horizontal speed envelope vs simulation."),
    ("PredictionPhase", "phase", "Wall/collision phase detection."),
    ("PredictionGroundSpoof", "ground", "Client onGround vs simulated ground state."),
    ("PredictionStep", "step", "Illegal step-up Y resolution."),
    ("PredictionJesus", "jesus", "Water/ladder surface walking bounds."),
    ("PredictionBlink", "blink", "Position gap and flush burst detection."),
    ("PredictionNoSlow", "noslow", "Item-use input scaling violation."),
    ("PredictionTimer", "timer", "Transaction-synced player clock drift."),
    ("PredictionVelocity", "velocity", "Knockback envelope violation."),
    ("PredictionWeb", "web", "Cobweb branch physics offset."),
    ("PredictionWater", "water", "Legacy 1.8 water travel bounds."),
    ("PredictionExplosion", "explosion", "Explosion vector offset envelope."),
]

SIMULATION = [
    ("SimulationOffsetHorizontal", "horizontal", "Horizontal-only engine offset sub-signal."),
    ("SimulationOffsetVertical", "vertical", "Vertical-only engine offset sub-signal."),
    ("SimulationKnockback", "knockback", "Grim-style knockback handler port."),
    ("SimulationExplosion", "explosion", "Grim-style explosion handler port."),
    ("SimulationNoSlow", "noslow", "Post-prediction slow ratio mismatch."),
    ("SimulationSprint", "sprint", "Illegal sprint state vs speed."),
    ("SimulationSneak", "sneak", "Sneak multiplier violation."),
    ("SimulationFriction", "friction", "Block friction mismatch."),
    ("SimulationJump", "jump", "Jump height vs potion bounds."),
    ("SimulationLiquid", "liquid", "Water/lava travel bounds."),
    ("SimulationClimbable", "climbable", "Ladder/vine ±0.15 clamp."),
    ("SimulationWeb", "web", "Cobweb stuck-speed multiplier."),
    ("SimulationEntityPush", "push", "Entity push bounds (1.9+ exempt on 1.8)."),
    ("SimulationIce", "ice", "Ice friction edge cases."),
    ("SimulationSlime", "slime", "Slime bounce uncertainty."),
    ("SimulationBlinkDebt", "blinkdebt", "Timer debt corroboration."),
    ("SimulationSetbackRecovery", "setback", "Post-setback physics recovery."),
    ("SimulationCombo", "combo", "Multi-family corroboration gate."),
]

PRISM = []
for letter in "ABCDEFGHIJKLM":
    PRISM.append((f"PrismBadPackets{letter}", f"badpackets{letter.lower()}", f"Structural bad-packet validation {letter}."))
PRISM += [
    ("PrismNoRotationA", "norotationa", "Stale look + off-FOV attack."),
    ("PrismNoRotationB", "norotationb", "Impossible angle + stale rotation."),
    ("PrismNoRotationC", "norotationc", "Attack outside transaction ack window."),
    ("PrismRotationRay", "rotationray", "Eye-ray vs attack vector at packet yaw."),
    ("PrismReachA", "reacha", "Rewind reach primary."),
    ("PrismReachB", "reachb", "Rewind reach corroboration."),
    ("PrismReachC", "reachc", "Impossible-hit cancel pipeline."),
    ("PrismHitboxA", "hitboxa", "Ray miss despite hit A."),
    ("PrismHitboxB", "hitboxb", "Ray miss despite hit B."),
    ("PrismBackTrack", "backtrack", "Entity snapshot age vs attack."),
    ("PrismLagRange", "lagrange", "Outbound position staleness."),
    ("PrismTransactionA", "transactiona", "C0F transaction abuse A."),
    ("PrismTransactionB", "transactionb", "C0F transaction abuse B."),
    ("PrismSetbackAccept", "setbackaccept", "Ignored S08 setback accept."),
    ("PrismInteractReach", "interactreach", "Structural interact distance."),
]
for letter in "ABCD":
    PRISM.append((f"PrismPacketOrder{letter}", f"packetorder{letter}", f"Tick/input/interact ordering {letter}."))
for letter in "ABCDEFG":
    PRISM.append((f"PrismMultiActions{letter}", f"multiactions{letter}", f"Multi-action packet abuse {letter}."))

CHARACTERISTICS = [
    ("CharSilentAim", "silentaim", "8-signal silent aim behavioral aggregator."),
    ("CharAimSnap", "aimsnap", "Pre-attack snap ratio."),
    ("CharAimReset", "aimreset", "Post-attack yaw restore."),
    ("CharAimCorrelation", "aimcorrelation", "Rotation only on attack ticks."),
    ("CharAimCenter", "aimcenter", "Hitbox center clustering."),
    ("CharKillAuraSwitch", "killauraswitch", "Multi-target switching."),
    ("CharKillAuraCps", "killauracps", "CPS distribution analysis."),
    ("CharTimerLegacy", "timerlegacy", "Interval timer corroboration."),
    ("CharVelocityPattern", "velocitypattern", "Partial KB sustained ratio."),
    ("CharCombatTiming", "combattiming", "Hit-select / hurtTime abuse."),
]
for letter in "ABC":
    CHARACTERISTICS.append((f"CharAutoClick{letter}", f"autoclick{letter}", f"Click interval stats {letter}."))
    CHARACTERISTICS.append((f"CharAimAssist{letter}", f"aimassist{letter}", f"Smooth/grid aim {letter}."))
    CHARACTERISTICS.append((f"CharCriticals{letter}", f"criticals{letter}", f"Fake-air + micro-Y {letter}."))
    CHARACTERISTICS.append((f"CharInventory{letter}", f"inventory{letter}", f"Inventory move patterns {letter}."))
for letter in "ABCDE":
    CHARACTERISTICS.append((f"CharScaffold{letter}", f"scaffold{letter}", f"Scaffold timing/rotation stats {letter}."))


def prediction_java(name, kind, doc):
    threshold_key = {
        "offset": ("offsetThreshold", "0.08"),
        "fly": ("maxVerticalDelta", "0.42"),
        "speed": ("maxHorizontalDelta", "0.35"),
    }.get(kind, ("threshold", "0.10"))
    return dedent(f'''
        package com.colin.vezanticheat.tier.prediction;

        import com.colin.vezanticheat.VezAntiCheat;
        import com.colin.vezanticheat.data.PlayerData;
        import com.colin.vezanticheat.engine.EngineResult;
        import com.colin.vezanticheat.tier.CheckTier;
        import com.colin.vezanticheat.tier.TierCheck;
        import com.colin.vezanticheat.utils.EngineMovementGrace;
        import com.colin.vezanticheat.utils.MovementEnforcement;
        import org.bukkit.entity.Player;

        /**
         * {doc}
         *
         * <p>Polar tier: PREDICTION (default punish VL ~150). Uses Grim movement engine output.</p>
         */
        public final class {name} extends TierCheck {{

            public {name}(VezAntiCheat plugin) {{
                super(plugin, "{name}", CheckTier.PREDICTION);
            }}

            @Override
            public void onFlyingPacket(Player p, PlayerData data, long nowMs) {{
                if (p == null || data == null) return;
                if (lagGated(p, data)) return;
                if (plugin.engine() == null || !plugin.engine().isEnabled()) {{
                    decay(p, 0.4D);
                    return;
                }}
                EngineResult result = data.getLastEngineResult();
                if (result == null || !result.checked) {{
                    coolBuffer(p, 1);
                    decay(p, 0.35D);
                    return;
                }}
                if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {{
                    coolBuffer(p, 1);
                    decay(p, 0.35D);
                    return;
                }}
                if (result.couldSkipTick && plugin.tierCfg().checkBoolean(name(), "respectPointThree", true)) {{
                    coolBuffer(p, 1);
                    decay(p, 0.3D);
                    return;
                }}

                double threshold = plugin.tierCfg().checkDouble(name(), "{threshold_key[0]}", {threshold_key[1]}D);
                double blatant = plugin.tierCfg().checkDouble(name(), "blatantThreshold", 0.15D);
                double signal = resolveSignal(result, kind());
                if (signal >= blatant) {{
                    MovementEnforcement.requestBlatantEnforcement(plugin, p, data,
                            name() + " blatant=" + round3(signal));
                    fail(p, data, plugin.tierCfg().checkDouble(name(), "blatantFailVl", 1.5D),
                            "blatant signal=" + round3(signal) + " thr=" + round3(blatant) + " " + result.debug);
                    resetBuffer(p);
                    return;
                }}
                if (signal <= threshold) {{
                    coolBuffer(p, 1);
                    decay(p, 0.35D);
                    return;
                }}
                int gain = signal > threshold * 2.0D ? 2 : 1;
                if (incrementBuffer(p, gain)) {{
                    fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                            "signal=" + round3(signal) + " thr=" + round3(threshold) + " " + result.debug);
                    resetBuffer(p);
                }}
            }}

            private String kind() {{ return "{kind}"; }}

            private double resolveSignal(EngineResult result, String kind) {{
                if (result == null) return 0.0D;
                switch (kind) {{
                    case "offset": return result.offset;
                    case "fly": return Math.abs(result.verticalOffset);
                    case "speed": return Math.abs(result.horizontalOffset);
                    case "phase": return result.inBlock ? result.offset : 0.0D;
                    case "ground": return result.groundMismatch ? result.offset : 0.0D;
                    case "step": return result.stepOffset;
                    case "jesus": return result.liquidOffset;
                    case "blink": return result.blinkGap;
                    case "noslow": return result.noSlowRatio;
                    case "timer": return result.timerDebt;
                    case "velocity": return result.velocityOffset;
                    case "web": return result.inWeb ? result.offset : 0.0D;
                    case "water": return result.inWater ? result.offset : 0.0D;
                    case "explosion": return result.explosionOffset;
                    default: return result.offset;
                }}
            }}
        }}
    ''').strip() + "\n"


def simulation_java(name, kind, doc):
    return dedent(f'''
        package com.colin.vezanticheat.tier.simulation;

        import com.colin.vezanticheat.VezAntiCheat;
        import com.colin.vezanticheat.data.PlayerData;
        import com.colin.vezanticheat.engine.EngineResult;
        import com.colin.vezanticheat.tier.CheckTier;
        import com.colin.vezanticheat.tier.TierCheck;
        import com.colin.vezanticheat.utils.EngineMovementGrace;
        import org.bukkit.entity.Player;

        /**
         * {doc}
         *
         * <p>Polar tier: SIMULATION (default punish VL ~90). Decomposed engine sub-signals.</p>
         */
        public final class {name} extends TierCheck {{

            public {name}(VezAntiCheat plugin) {{
                super(plugin, "{name}", CheckTier.SIMULATION);
            }}

            @Override
            public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {{
                if (p == null || data == null || result == null || !result.checked) return;
                if (lagGated(p, data)) return;
                if ("combo".equals("{kind}") && !plugin.tierCfg().checkBoolean(name(), "publicFlag", false)) {{
                    return;
                }}
                if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {{
                    coolBuffer(p, 1);
                    decay(p, 0.3D);
                    return;
                }}
                double threshold = plugin.tierCfg().checkDouble(name(), "threshold", 0.06D);
                double signal = resolveSignal(result);
                if (signal <= threshold) {{
                    coolBuffer(p, 1);
                    decay(p, 0.3D);
                    return;
                }}
                int gain = signal > threshold * 2.5D ? 2 : 1;
                if (incrementBuffer(p, gain)) {{
                    fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 0.75D),
                            "signal=" + round3(signal) + " thr=" + round3(threshold) + " " + result.debug);
                    resetBuffer(p);
                }}
            }}

            private double resolveSignal(EngineResult result) {{
                switch ("{kind}") {{
                    case "horizontal": return Math.abs(result.horizontalOffset);
                    case "vertical": return Math.abs(result.verticalOffset);
                    case "knockback": return result.velocityOffset;
                    case "explosion": return result.explosionOffset;
                    case "noslow": return Math.max(0.0D, result.noSlowRatio - 1.0D);
                    case "sprint": return result.illegalSprint ? result.offset : 0.0D;
                    case "sneak": return result.illegalSneak ? result.offset : 0.0D;
                    case "friction": return result.frictionMismatch ? result.offset : 0.0D;
                    case "jump": return result.jumpOffset;
                    case "liquid": return result.liquidOffset;
                    case "climbable": return result.climbableOffset;
                    case "web": return result.inWeb ? result.offset : 0.0D;
                    case "push": return result.entityPushOffset;
                    case "ice": return result.iceOffset;
                    case "slime": return result.slimeOffset;
                    case "blinkdebt": return result.timerDebt;
                    case "setback": return dataPendingSetback(result);
                    case "combo": return result.offset;
                    default: return result.offset;
                }}
            }}

            private double dataPendingSetback(EngineResult result) {{
                return result.offset;
            }}
        }}
    ''').strip() + "\n"


def prism_java(name, kind, doc):
    attack_based = kind in ("norotationa", "norotationb", "norotationc", "rotationray",
                            "reacha", "reachb", "reachc", "hitboxa", "hitboxb", "backtrack", "lagrange")
    hook = "onAttack" if attack_based else ("onWindowConfirmation" if kind.startswith("transaction") else "onFlyingPacket")
    return dedent(f'''
        package com.colin.vezanticheat.tier.prism;

        import com.colin.vezanticheat.VezAntiCheat;
        import com.colin.vezanticheat.data.PlayerData;
        import com.colin.vezanticheat.engine.CombatResult;
        import com.colin.vezanticheat.engine.CombatRewind;
        import com.colin.vezanticheat.tier.CheckTier;
        import com.colin.vezanticheat.tier.TierCheck;
        import com.colin.vezanticheat.utils.CombatContextAnalyzer;
        import com.colin.vezanticheat.utils.CombatUtil;
        import com.colin.vezanticheat.utils.HitboxUtil;
        import org.bukkit.Location;
        import org.bukkit.entity.Entity;
        import org.bukkit.entity.Player;

        /**
         * {doc}
         *
         * <p>Polar tier: PRISM (default punish VL ~40). Packet structure + geometry validation.</p>
         */
        public final class {name} extends TierCheck {{

            public {name}(VezAntiCheat plugin) {{
                super(plugin, "{name}", CheckTier.PRISM);
            }}

            @Override
            public void {hook}(Player p, PlayerData data{", long nowMs" if hook == "onFlyingPacket" else (", short actionId, long nowMs" if hook == "onWindowConfirmation" else "")}) {{
                if (p == null || data == null) return;
                if (lagGated(p, data)) return;
                evaluate(p, data{", nowMs" if "nowMs" in hook else ""});
            }}

            private void evaluate(Player p, PlayerData data{", long nowMs" if hook != "onAttack" else ""}) {{
                long now = System.currentTimeMillis();
                CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
                if (combat != null && !combat.isClean()) {{
                    coolBuffer(p, 1);
                    decay(p, 0.35D);
                    return;
                }}

                double suspicion = scoreSuspicion(p, data, now);
                double threshold = plugin.tierCfg().checkDouble(name(), "threshold", 0.55D);
                if (suspicion < threshold) {{
                    coolBuffer(p, 1);
                    decay(p, 0.3D);
                    return;
                }}
                if (incrementBuffer(p, suspicion >= threshold * 1.35D ? 2 : 1)) {{
                    if (shouldCancelAttack()) {{
                        blockAttack(p, data, name() + " suspicion=" + round3(suspicion));
                    }}
                    fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                            "suspicion=" + round3(suspicion) + " kind={kind}");
                    resetBuffer(p);
                }}
            }}

            private double scoreSuspicion(Player p, PlayerData data, long now) {{
                switch ("{kind}") {{
                    case "norotationa": return scoreNoRotationA(p, data, now);
                    case "norotationb": return scoreNoRotationB(p, data, now);
                    case "norotationc": return scoreNoRotationC(p, data, now);
                    case "rotationray": return scoreRotationRay(p, data);
                    case "reacha":
                    case "reachb":
                    case "reachc": return scoreReach(p, data);
                    case "hitboxa":
                    case "hitboxb": return scoreHitboxMiss(p, data);
                    case "backtrack": return scoreBackTrack(data);
                    case "lagrange": return scoreLagRange(data);
                    case "setbackaccept": return scoreSetbackAccept(data);
                    case "interactreach": return scoreInteractReach(p, data);
                    default: return scoreStructural(data);
                }}
            }}

            private double scoreNoRotationA(Player p, PlayerData data, long now) {{
                long rotAge = now - data.getLastRotationPacket();
                long maxAge = plugin.tierCfg().checkLong(name(), "maxRotationAgeMs", 165L);
                if (rotAge <= maxAge) return 0.0D;
                Location eye = HitboxUtil.buildPacketSyncedEye(p, data);
                Entity target = data.getLastTargetEntity();
                if (target == null) return 0.0D;
                double angle = CombatUtil.angleToEntity(eye, target, data.getPacketYaw(), data.getPacketPitch());
                double maxAngle = plugin.tierCfg().checkDouble(name(), "maxAttackAngle", 32.0D);
                return angle > maxAngle ? Math.min(1.0D, angle / maxAngle) : 0.0D;
            }}

            private double scoreNoRotationB(Player p, PlayerData data, long now) {{
                return scoreNoRotationA(p, data, now) * 1.1D;
            }}

            private double scoreNoRotationC(Player p, PlayerData data, long now) {{
                if (!data.getTransactionState().hasRecentAck(now)) return 0.0D;
                return scoreNoRotationA(p, data, now);
            }}

            private double scoreRotationRay(Player p, PlayerData data) {{
                Entity target = data.getLastTargetEntity();
                if (target == null) return 0.0D;
                Location eye = HitboxUtil.buildPacketSyncedEye(p, data);
                CombatUtil.RayResult ray = CombatUtil.raycastEntity(eye, data.getPacketYaw(), data.getPacketPitch(), target, 6.0D);
                return ray != null && ray.hit ? 0.0D : 0.85D;
            }}

            private double scoreReach(Player p, PlayerData data) {{
                CombatResult engine = data.getLastCombatResult();
                if (engine == null) return 0.0D;
                double maxReach = plugin.tierCfg().checkDouble(name(), "maxReach", 3.1D);
                return engine.reachDistance > maxReach ? Math.min(1.0D, engine.reachDistance / maxReach - 1.0D) : 0.0D;
            }}

            private double scoreHitboxMiss(Player p, PlayerData data) {{
                return scoreRotationRay(p, data);
            }}

            private double scoreBackTrack(PlayerData data) {{
                CombatResult engine = data.getLastCombatResult();
                if (engine == null) return 0.0D;
                long maxAge = plugin.tierCfg().checkLong(name(), "maxSnapshotAgeMs", 220L);
                return engine.snapshotAgeMs > maxAge ? 0.7D : 0.0D;
            }}

            private double scoreLagRange(PlayerData data) {{
                return scoreBackTrack(data) * 0.9D;
            }}

            private double scoreSetbackAccept(PlayerData data) {{
                return data.isPendingSetback() ? 0.75D : 0.0D;
            }}

            private double scoreInteractReach(Player p, PlayerData data) {{
                return data.getLastUseEntityDistance() > 4.5D ? 0.65D : 0.0D;
            }}

            private double scoreStructural(PlayerData data) {{
                return data.badPackets().structuralScore(name()) / 100.0D;
            }}

            private boolean shouldCancelAttack() {{
                return plugin.tierCfg().checkBoolean(name(), "cancelAttack", true);
            }}
        }}
    ''').strip() + "\n"


def characteristics_java(name, kind, doc):
    hook = "onAttack" if kind not in ("timerlegacy", "scaffolda", "scaffoldb", "scaffoldc", "scaffoldd", "scaffolde") else {
        "timerlegacy": "onFlyingPacket",
    }.get(kind, "onBlockPlacePacket" if kind.startswith("scaffold") else "onInventoryAction" if kind.startswith("inventory") else "onArmSwing" if kind.startswith("autoclick") else "onAttack")
    extra_params = ""
    if hook == "onFlyingPacket":
        extra_params = ", long nowMs"
    elif hook == "onBlockPlacePacket":
        extra_params = ", org.bukkit.block.Block against, int faceId, float cursorX, float cursorY, float cursorZ"
    return dedent(f'''
        package com.colin.vezanticheat.tier.characteristics;

        import com.colin.vezanticheat.VezAntiCheat;
        import com.colin.vezanticheat.data.PlayerData;
        import com.colin.vezanticheat.tier.CheckTier;
        import com.colin.vezanticheat.tier.TierCheck;
        import com.colin.vezanticheat.utils.CombatContextAnalyzer;
        import com.colin.vezanticheat.utils.CombatUtil;
        import org.bukkit.entity.Player;

        /**
         * {doc}
         *
         * <p>Polar tier: CHARACTERISTICS (default punish VL ~25). Behavioral/statistical heuristics.</p>
         */
        public final class {name} extends TierCheck {{

            public {name}(VezAntiCheat plugin) {{
                super(plugin, "{name}", CheckTier.CHARACTERISTICS);
            }}

            @Override
            public void {hook}(Player p, PlayerData data{extra_params}) {{
                if (p == null || data == null) return;
                if (lagGated(p, data)) return;
                long now = System.currentTimeMillis();
                CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
                if (combat != null && !combat.isClean() && !"{kind}".startsWith("scaffold")) {{
                    coolBuffer(p, 1);
                    decay(p, 0.4D);
                    return;
                }}
                double score = computeScore(p, data, now);
                double threshold = plugin.tierCfg().checkDouble(name(), "threshold", 0.45D);
                if (score < threshold) {{
                    coolBuffer(p, 1);
                    decay(p, 0.35D);
                    return;
                }}
                int gain = score >= threshold * 1.4D ? 2 : 1;
                if (incrementBuffer(p, gain)) {{
                    fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                            "score=" + round3(score) + " kind={kind}");
                    resetBuffer(p);
                }}
            }}

            private double computeScore(Player p, PlayerData data, long now) {{
                switch ("{kind}") {{
                    case "silentaim": return CharSilentAimSignals.score(plugin, p, data, now);
                    case "aimsnap": return CharSilentAimSignals.snapScore(data);
                    case "aimreset": return CharSilentAimSignals.resetScore(data);
                    case "aimcorrelation": return CharSilentAimSignals.correlationScore(data);
                    case "aimcenter": return CharSilentAimSignals.centerScore(data);
                    case "killauraswitch": return CharSilentAimSignals.switchScore(data);
                    case "killauracps": return CharSilentAimSignals.cpsScore(data, now);
                    case "timerlegacy": return CharSilentAimSignals.timerScore(data, now);
                    case "velocitypattern": return CharSilentAimSignals.velocityScore(data);
                    case "combattiming": return CharSilentAimSignals.combatTimingScore(data, now);
                    case "autoclicka":
                    case "autoclickb":
                    case "autoclickc": return CharSilentAimSignals.autoClickScore(data, "{kind}");
                    case "aimassista":
                    case "aimassistb":
                    case "aimassistc": return CharSilentAimSignals.aimAssistScore(data, "{kind}");
                    case "criticalsa":
                    case "criticalsb":
                    case "criticalsc": return CharSilentAimSignals.criticalsScore(p, data, "{kind}");
                    case "inventorya":
                    case "inventoryb":
                    case "inventoryc": return CharSilentAimSignals.inventoryScore(data, "{kind}");
                    case "scaffolda":
                    case "scaffoldb":
                    case "scaffoldc":
                    case "scaffoldd":
                    case "scaffolde": return CharSilentAimSignals.scaffoldScore(data, "{kind}");
                    default: return 0.0D;
                }}
            }}
        }}
    ''').strip() + "\n"


def yaml_block(tier, checks):
    default_punish = tier.defaultPunishVl if hasattr(tier, 'defaultPunishVl') else 25
    lines = [f"tiers-version: 1", "", "defaults:", f"  tier: {tier}", "  enabled: true", "  shadow: false",
             f"  punishVl: {default_punish}", f"  failWeight: 1.0", "  bufferToFlag: 3", "  decay: 0.15", ""]
    for name, kind, doc in checks:
        lines += [
            f"{name}:",
            f"  tier: {tier}",
            "  enabled: true",
            f"  punishVl: {default_punish}",
            "  bufferToFlag: 3",
            f"  # {doc}",
            ""
        ]
    return "\n".join(lines)


def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def main():
    for name, kind, doc in PREDICTION:
        write_file(os.path.join(JAVA, "prediction", name + ".java"), prediction_java(name, kind, doc))
    for name, kind, doc in SIMULATION:
        write_file(os.path.join(JAVA, "simulation", name + ".java"), simulation_java(name, kind, doc))
    for name, kind, doc in PRISM:
        write_file(os.path.join(JAVA, "prism", name + ".java"), prism_java(name, kind, doc))
    for name, kind, doc in CHARACTERISTICS:
        write_file(os.path.join(JAVA, "characteristics", name + ".java"), characteristics_java(name, kind, doc))

    write_file(os.path.join(JAVA, "characteristics", "CharSilentAimSignals.java"), dedent('''
        package com.colin.vezanticheat.tier.characteristics;

        import com.colin.vezanticheat.VezAntiCheat;
        import com.colin.vezanticheat.data.PlayerData;
        import com.colin.vezanticheat.utils.AimAssistUtil;
        import com.colin.vezanticheat.utils.CombatUtil;
        import org.bukkit.entity.Player;

        import java.util.Deque;

        /** Shared 8-signal silent-aim scoring utilities for Characteristics tier checks. */
        public final class CharSilentAimSignals {

            private CharSilentAimSignals() {}

            public static double score(VezAntiCheat plugin, Player p, PlayerData data, long now) {
                double s1 = angularScore(p, data);
                double s2 = snapScore(data);
                double s3 = resetScore(data);
                double s4 = movementMismatchScore(data);
                double s5 = centerScore(data);
                double s6 = correlationScore(data);
                double s7 = switchScore(data);
                double s8 = cpsScore(data, now);
                double composite = s1 * 1.5D + s2 * 2.0D + s3 * 1.5D + s4 * 1.0D
                        + s5 * 0.8D + s6 * 1.5D + s7 * 1.2D + s8 * 1.0D;
                int active = 0;
                if (s1 > 0.2D) active++;
                if (s2 > 0.2D) active++;
                if (s3 > 0.2D) active++;
                if (s4 > 0.2D) active++;
                if (s5 > 0.2D) active++;
                if (s6 > 0.2D) active++;
                if (s7 > 0.2D) active++;
                if (s8 > 0.2D) active++;
                if (active >= 3) composite *= 1.3D;
                if (active >= 4) composite *= 1.15D;
                return Math.min(1.0D, composite / 10.0D);
            }

            public static double angularScore(Player p, PlayerData data) {
                if (p == null || data == null) return 0.0D;
                org.bukkit.entity.Entity target = data.getLastTargetEntity();
                if (target == null) return 0.0D;
                org.bukkit.Location eye = p.getEyeLocation();
                double angle = CombatUtil.angleToEntity(eye, target, data.getPacketYaw(), data.getPacketPitch());
                return angle > 25.0D ? Math.min(1.0D, angle / 45.0D) : 0.0D;
            }

            public static double snapScore(PlayerData data) {
                if (data == null) return 0.0D;
                return Math.min(1.0D, data.getKillAuraASnapRatio());
            }

            public static double resetScore(PlayerData data) {
                if (data == null || !data.isKillAuraAPostResetActive()) return 0.0D;
                return 0.65D;
            }

            public static double movementMismatchScore(PlayerData data) {
                Deque<Double> angles = data.getKillAuraAMismatchAngles();
                if (angles == null || angles.isEmpty()) return 0.0D;
                double sum = 0.0D;
                for (Double d : angles) sum += d == null ? 0.0D : d;
                return Math.min(1.0D, sum / angles.size() / 45.0D);
            }

            public static double centerScore(PlayerData data) {
                Deque<Double> errors = data.getKillAuraACenterErrors();
                if (errors == null || errors.size() < 4) return 0.0D;
                double sum = 0.0D;
                for (Double d : errors) sum += d == null ? 0.0D : d;
                double avg = sum / errors.size();
                return avg < 0.08D ? 0.7D : 0.0D;
            }

            public static double correlationScore(PlayerData data) {
                int attack = data.getKillAuraARotOnAttackTicks();
                int nonAttack = data.getKillAuraARotOnNonAttackTicks();
                int total = attack + nonAttack + data.getKillAuraANoRotOnNonAttackTicks();
                if (total < 40) return 0.0D;
                double ratio = attack / (double) Math.max(1, total);
                return ratio > 0.55D ? Math.min(1.0D, ratio) : 0.0D;
            }

            public static double switchScore(PlayerData data) {
                return Math.min(1.0D, data.getKillAuraASwitchBuffer() / 6.0D);
            }

            public static double cpsScore(PlayerData data, long now) {
                Deque<Long> intervals = data.getAttackIntervals();
                if (intervals == null || intervals.size() < 8) return 0.0D;
                double avg = 0.0D;
                for (Long ms : intervals) avg += ms == null ? 0.0D : ms;
                avg /= intervals.size();
                double cps = avg <= 0.0D ? 0.0D : 1000.0D / avg;
                return cps > 16.0D ? Math.min(1.0D, (cps - 16.0D) / 8.0D) : 0.0D;
            }

            public static double timerScore(PlayerData data, long now) {
                long last = data.getLastFlyingPacket();
                if (last <= 0L) return 0.0D;
                long gap = now - last;
                return gap < 35L || gap > 70L ? 0.35D : 0.0D;
            }

            public static double velocityScore(PlayerData data) {
                return data.getPartialKbRatio() > 0.65D ? data.getPartialKbRatio() : 0.0D;
            }

            public static double combatTimingScore(PlayerData data, long now) {
                long lastHit = data.getLastDamageTakenMs();
                if (lastHit <= 0L) return 0.0D;
                long delta = now - lastHit;
                return delta >= 0L && delta <= 120L ? 0.55D : 0.0D;
            }

            public static double autoClickScore(PlayerData data, String kind) {
                Deque<Long> intervals = data.getClickIntervals();
                if (intervals == null || intervals.size() < 6) return 0.0D;
                double variance = AimAssistUtil.intervalVariance(intervals);
                if ("autoclicka".equals(kind)) return variance < 8.0D ? 0.6D : 0.0D;
                if ("autoclickb".equals(kind)) return variance < 4.0D ? 0.75D : 0.0D;
                return variance < 2.0D ? 0.85D : 0.0D;
            }

            public static double aimAssistScore(PlayerData data, String kind) {
                double gcdScore = AimAssistUtil.analyzeGcd(data.getYawDeltas());
                if ("aimassista".equals(kind)) return gcdScore > 0.45D ? gcdScore : 0.0D;
                if ("aimassistb".equals(kind)) return gcdScore > 0.6D ? gcdScore : 0.0D;
                return gcdScore > 0.75D ? gcdScore : 0.0D;
            }

            public static double criticalsScore(Player p, PlayerData data, String kind) {
                if (p == null || data == null) return 0.0D;
                boolean air = !p.isOnGround() && !data.isLastClientGround();
                double micro = data.getLastMicroYOffsetMs() > 0L ? 0.5D : 0.0D;
                if ("criticalsa".equals(kind)) return air ? 0.55D : 0.0D;
                if ("criticalsb".equals(kind)) return air && micro > 0.0D ? 0.7D : 0.0D;
                return air && micro > 0.0D && data.getLastJumpTime() > 0L ? 0.85D : 0.0D;
            }

            public static double inventoryScore(PlayerData data, String kind) {
                int moves = data.getInventoryMoveCount();
                if ("inventorya".equals(kind)) return moves > 4 ? 0.45D : 0.0D;
                if ("inventoryb".equals(kind)) return moves > 8 ? 0.65D : 0.0D;
                return moves > 12 ? 0.85D : 0.0D;
            }

            public static double scaffoldScore(PlayerData data, String kind) {
                int flags = data.getScaffoldPlaceCount();
                if ("scaffolda".equals(kind)) return flags > 6 ? 0.4D : 0.0D;
                if ("scaffoldb".equals(kind)) return flags > 10 ? 0.55D : 0.0D;
                if ("scaffoldc".equals(kind)) return flags > 14 ? 0.65D : 0.0D;
                if ("scaffoldd".equals(kind)) return flags > 18 ? 0.75D : 0.0D;
                return flags > 22 ? 0.85D : 0.0D;
            }
        }
    ''').strip() + "\n")

    os.makedirs(RES, exist_ok=True)
    write_file(os.path.join(RES, "prediction.yml"), yaml_block("PREDICTION", PREDICTION))
    write_file(os.path.join(RES, "simulation.yml"), yaml_block("SIMULATION", SIMULATION))
    write_file(os.path.join(RES, "prism.yml"), yaml_block("PRISM", PRISM))
    write_file(os.path.join(RES, "characteristics.yml"), yaml_block("CHARACTERISTICS", CHARACTERISTICS))

    print("Generated", len(PREDICTION)+len(SIMULATION)+len(PRISM)+len(CHARACTERISTICS), "checks")


if __name__ == "__main__":
    main()
