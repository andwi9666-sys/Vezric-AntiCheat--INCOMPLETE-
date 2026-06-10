# Context-Aware Check Audit

Date: March 13, 2026

This audit reflects the current refactor state of the anticheat after the context-aware rewrite pass. "Rewritten" means the check now consumes shared combat or movement context, discounts dirty samples, and favors repeated evidence over one-off flags.

## Shared Systems

| File | Status | Notes |
| --- | --- | --- |
| `src/main/java/com/colin/vezanticheat/checks/Check.java` | Rewritten | Punishment handoff now feeds evidence-based punishment flow instead of flat instant ban logic. |
| `src/main/java/com/colin/vezanticheat/checks/CheckManager.java` | Compatible | Existing dispatcher remains valid for the new shared analyzers. |
| `src/main/java/com/colin/vezanticheat/utils/CombatContextAnalyzer.java` | New | Per-hit combat snapshot with ping, jitter, trade state, spacing, jump/blockhit indicators, cleanliness, and structured debug. |
| `src/main/java/com/colin/vezanticheat/utils/MovementContextAnalyzer.java` | New | Shared movement cleanliness scoring for packet burst, jitter, combat contamination, recent velocity, jump windows, and constrained movement. |
| `src/main/java/com/colin/vezanticheat/utils/LegacyKbBridge.java` | New | Runtime hook into LegacyKB so velocity/reach logic follows live server KB values. |
| `src/main/java/com/colin/vezanticheat/utils/ConfigManager.java` | Rewritten | Centralized context, punishment, and LegacyKB-backed knockback profile reads. |
| `src/main/java/com/colin/vezanticheat/data/PlayerData.java` | Rewritten | Stores click, hit, jump, inventory, packet, jitter, and combat timing state used across checks. |
| `src/main/java/com/colin/vezanticheat/listeners/PacketListener.java` | Rewritten | Feeds click intervals, packet timing, and movement history into shared state. |
| `src/main/java/com/colin/vezanticheat/listeners/PlayerListener.java` | Rewritten | Feeds combat, inventory, and velocity windows used by context analyzers. |

## Combat Checks

| File | Status | Notes |
| --- | --- | --- |
| `src/main/java/com/colin/vezanticheat/checks/combat/AimAssistA.java` | Rewritten | Clean-sample rotational grid analysis; downweights legit strafe tracking and reacquisition. |
| `src/main/java/com/colin/vezanticheat/checks/combat/AimAssistB.java` | Rewritten | Repeated machine-follow pattern detection with context-aware follow windows. |
| `src/main/java/com/colin/vezanticheat/checks/combat/AimAssistC.java` | Rewritten | Pattern-based rotational consistency detection with combat cleanliness gating. |
| `src/main/java/com/colin/vezanticheat/checks/combat/AutoClickA.java` | Rewritten | Uses repeated interval structure and attack correlation, not raw CPS alone. |
| `src/main/java/com/colin/vezanticheat/checks/combat/AutoClickB.java` | Rewritten | Adds clean-sample and attack-rhythm correlation before confidence grows. |
| `src/main/java/com/colin/vezanticheat/checks/combat/AutoClickC.java` | Rewritten | Detects impossible burst regularity across multiple slices instead of single timing. |
| `src/main/java/com/colin/vezanticheat/checks/combat/BackTrackA.java` | Rewritten | Packet timing, stale-position hits, order anomalies, ping anomalies, and repeated pattern ratios. |
| `src/main/java/com/colin/vezanticheat/checks/combat/CriticalsA.java` | Legacy but acceptable | Still mostly local-heuristic, but lower FP risk than prior target checks. |
| `src/main/java/com/colin/vezanticheat/checks/combat/CriticalsB.java` | Legacy but acceptable | Local airborne pattern check; not part of the main false-flag problem set. |
| `src/main/java/com/colin/vezanticheat/checks/combat/CriticalsC.java` | Legacy but acceptable | Already uses windowed evidence, though not on the shared analyzer. |
| `src/main/java/com/colin/vezanticheat/checks/combat/KillAuraA.java` | Rewritten | Context-aware timing consistency instead of naive attack timing. |
| `src/main/java/com/colin/vezanticheat/checks/combat/KillAuraB.java` | Rewritten | Context-aware aim/attack angle analysis with rewind and close-range handling. |
| `src/main/java/com/colin/vezanticheat/checks/combat/KillAuraC.java` | Rewritten | Snap-follow detection only on clean combat samples. |
| `src/main/java/com/colin/vezanticheat/checks/combat/KillAuraD.java` | Rewritten | No longer flags fast legit clicking as aura; requires no-swing style packet anomalies. |
| `src/main/java/com/colin/vezanticheat/checks/combat/KillAuraE.java` | Legacy | Low-priority legacy check; not part of the major context rewrite. |
| `src/main/java/com/colin/vezanticheat/checks/combat/KillAuraF.java` | Disabled | Remains disabled. |
| `src/main/java/com/colin/vezanticheat/checks/combat/KillAuraG.java` | Rewritten | Context-aware no-swing family check with dirty-sample rejection. |
| `src/main/java/com/colin/vezanticheat/checks/combat/PiercingA.java` | Legacy but acceptable | Narrow-purpose check; lower false-positive exposure. |
| `src/main/java/com/colin/vezanticheat/checks/combat/ReachA.java` | Rewritten | Combo, hit-select, spacing, ping, and dirty-sample tolerant repeated reach model. |
| `src/main/java/com/colin/vezanticheat/checks/combat/ReachB.java` | Rewritten | Harder reach layer with combo and spacing-aware tolerance. |
| `src/main/java/com/colin/vezanticheat/checks/combat/ReachC.java` | Rewritten | Sample-window clustering of outliers instead of isolated stretched hits. |
| `src/main/java/com/colin/vezanticheat/checks/combat/VelocityA.java` | Rewritten | Repeated clean-sample horizontal under-response detection. |
| `src/main/java/com/colin/vezanticheat/checks/combat/VelocityB.java` | Rewritten | Context-aware near-zero-response detector on clean hits only. |
| `src/main/java/com/colin/vezanticheat/checks/combat/VelocityC.java` | Rewritten | Clean-sample vertical under-response detector. |
| `src/main/java/com/colin/vezanticheat/checks/combat/VelocityD.java` | Rewritten | Severe pressure layer for blatant clean anti-KB or converging A/B/C evidence. |
| `src/main/java/com/colin/vezanticheat/checks/combat/VelocityShared.java` | Rewritten | Shared clean-sample classification, jump-reset, counterstrafe, collision, and KB response logic. |

## Movement Checks

| File | Status | Notes |
| --- | --- | --- |
| `src/main/java/com/colin/vezanticheat/checks/movement/BlinkA.java` | Rewritten | Dirty movement windows no longer fully count; debug now shows contamination state. |
| `src/main/java/com/colin/vezanticheat/checks/movement/BlinkB.java` | Rewritten | Shared movement cleanliness gates burst-related false positives. |
| `src/main/java/com/colin/vezanticheat/checks/movement/FlyA.java` | Rewritten | Cleanliness gate added before upward/hover evidence is trusted. |
| `src/main/java/com/colin/vezanticheat/checks/movement/FlyB.java` | Rewritten | Packet/jitter contamination filtered before sustained fly evidence counts. |
| `src/main/java/com/colin/vezanticheat/checks/movement/FlyC.java` | Rewritten | Context-aware gating added. |
| `src/main/java/com/colin/vezanticheat/checks/movement/FlyD.java` | Rewritten | Context-aware gating added. |
| `src/main/java/com/colin/vezanticheat/checks/movement/FlyE.java` | Rewritten | Context-aware gating added. |
| `src/main/java/com/colin/vezanticheat/checks/movement/GroundSpoofA.java` | Rewritten | Dirty movement windows discounted before spoof evidence grows. |
| `src/main/java/com/colin/vezanticheat/checks/movement/GroundSpoofB.java` | Rewritten | Same shared movement-quality gate. |
| `src/main/java/com/colin/vezanticheat/checks/movement/JesusA.java` | Rewritten | Water-contact anomalies now require cleaner samples. |
| `src/main/java/com/colin/vezanticheat/checks/movement/JesusB.java` | Rewritten | Same shared movement-quality gate. |
| `src/main/java/com/colin/vezanticheat/checks/movement/NoFallA.java` | Rewritten | Pending no-fall evaluation now includes movement cleanliness and debug context. |
| `src/main/java/com/colin/vezanticheat/checks/movement/NoFallB.java` | Rewritten | Shared movement-quality gate added. |
| `src/main/java/com/colin/vezanticheat/checks/movement/NoFallC.java` | Rewritten | Shared movement-quality gate added. |
| `src/main/java/com/colin/vezanticheat/checks/movement/PhaseA.java` | Rewritten | Dirty movement windows discounted before phase evidence is trusted. |
| `src/main/java/com/colin/vezanticheat/checks/movement/PhaseB.java` | Rewritten | Same shared movement-quality gate. |
| `src/main/java/com/colin/vezanticheat/checks/movement/SpeedA.java` | Rewritten | Recent combat/jump/velocity contamination removed from primary speed evidence. |
| `src/main/java/com/colin/vezanticheat/checks/movement/SpeedB.java` | Rewritten | Same shared movement-quality gate. |
| `src/main/java/com/colin/vezanticheat/checks/movement/SpeedC.java` | Rewritten | Air-speed layer now rejects dirty movement windows before building bhop evidence. |
| `src/main/java/com/colin/vezanticheat/checks/movement/SpeedD.java` | Rewritten | Spike-speed layer now trusts only clean samples and reports movement context in debug. |
| `src/main/java/com/colin/vezanticheat/checks/movement/StepA.java` | Rewritten | Dirty movement windows discounted before step evidence grows. |
| `src/main/java/com/colin/vezanticheat/checks/movement/StepB.java` | Rewritten | Same shared movement-quality gate. |
| `src/main/java/com/colin/vezanticheat/checks/movement/StepC.java` | Rewritten | Same shared movement-quality gate. |

## Player / Placement / Packet Checks

| File | Status | Notes |
| --- | --- | --- |
| `src/main/java/com/colin/vezanticheat/checks/player/BadPacketsA.java` | Legacy but acceptable | Packet-structure check with relatively low PvP mechanic overlap. |
| `src/main/java/com/colin/vezanticheat/checks/player/BadPacketsB.java` | Legacy but acceptable | Same reason as above. |
| `src/main/java/com/colin/vezanticheat/checks/player/FastBowA.java` | Legacy but acceptable | Narrow-purpose timing check. |
| `src/main/java/com/colin/vezanticheat/checks/player/FastBreakA.java` | Legacy but acceptable | Block-break check, not central to PvP rewrite. |
| `src/main/java/com/colin/vezanticheat/checks/player/FastBreakB.java` | Legacy but acceptable | Same. |
| `src/main/java/com/colin/vezanticheat/checks/player/FastEatA.java` | Legacy but acceptable | Same. |
| `src/main/java/com/colin/vezanticheat/checks/player/InventoryA.java` | Rewritten | GUI movement now requires cleaner impossible movement. |
| `src/main/java/com/colin/vezanticheat/checks/player/InventoryB.java` | Rewritten | GUI autoclick overlap now requires impossible rhythm, not just fast clicking. |
| `src/main/java/com/colin/vezanticheat/checks/player/InventoryC.java` | Rewritten | Inventory-open sprint/attack overlap now tolerates packet-order noise and recent UI actions. |
| `src/main/java/com/colin/vezanticheat/checks/player/NoRotationA.java` | Rewritten | Silent-rotation evidence now requires clean combat context. |
| `src/main/java/com/colin/vezanticheat/checks/player/NoRotationB.java` | Rewritten | Impossible FOV attacks now require clean combat context. |
| `src/main/java/com/colin/vezanticheat/checks/player/NoSlowA.java` | Rewritten | Context-aware move cleanliness gate for combat-induced irregularity. |
| `src/main/java/com/colin/vezanticheat/checks/player/NoSlowB.java` | Rewritten | Same. |
| `src/main/java/com/colin/vezanticheat/checks/player/NukerA.java` | Legacy but acceptable | Not part of the PvP-focused rewrite target. |
| `src/main/java/com/colin/vezanticheat/checks/player/NukerB.java` | Legacy but acceptable | Same. |
| `src/main/java/com/colin/vezanticheat/checks/player/NukerC.java` | Legacy but acceptable | Same. |
| `src/main/java/com/colin/vezanticheat/checks/player/NukerD.java` | Legacy but acceptable | Same. |
| `src/main/java/com/colin/vezanticheat/checks/player/ScaffoldA.java` | Rewritten | Shared scaffold cleanliness and movement-quality gating added. |
| `src/main/java/com/colin/vezanticheat/checks/player/ScaffoldB.java` | Rewritten | Same. |
| `src/main/java/com/colin/vezanticheat/checks/player/ScaffoldC.java` | Rewritten | Same. |
| `src/main/java/com/colin/vezanticheat/checks/player/ScaffoldD.java` | Rewritten | Same. |
| `src/main/java/com/colin/vezanticheat/checks/player/TimerA.java` | Legacy but acceptable | Broad packet-rate check; outside advanced PvP false-flag core. |
| `src/main/java/com/colin/vezanticheat/checks/player/TimerB.java` | Legacy but acceptable | Same. |

## Residual Risk

- `KillAuraE` and some narrow utility checks remain mostly legacy.
- `Criticals`, `Timer`, and several block-interaction checks are not major PvP false-positive sources, but they are not as fully normalized onto the new analyzers as the core combat stack.
- The highest-risk PvP false-positive families are now the rewritten ones: velocity, reach, backtrack, aim, autoclick, scaffold, inventory overlap, no-slow, and the primary speed/fly/phase/step movement layers.
