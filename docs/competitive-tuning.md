# Competitive PvP Tuning Guide (Pot / HCF / UHC)

**Primary profile:** `aggressive`  
**Apply:** `/vez profile aggressive` then `/vez reload`

This guide walks through shadow → validate → enable punish for competitive networks. Full tier reference: [TIER_TUNING.md](TIER_TUNING.md).

## Week 1 — Movement authority (PREDICTION + SIMULATION)

### Day 1–2: Shadow all movement checks

In `plugins/VezAntiCheat/tiers/prediction.yml` and `simulation.yml`, set `shadow: true` on:

| Check | Why first |
|-------|-----------|
| `PredictionSpeed` | Sprint-jump, ice, KB chains |
| `PredictionFly`, `PredictionFlyBob` | Stair/slab hover FP risk |
| `PredictionPhase` | Corner/wall clips |
| `PredictionOffset` | Sub-threshold speed |
| `PredictionVehicle` | Boat/minecart on HCF roads |
| `SimulationKnockback` | Anti-KB ratio 0.85–0.95 |

Run [testing-plan.md](testing-plan.md) §1 legit PvP at **20 / 80 / 150 ms** ping for 2 hours each.

### Day 3–4: Enable movement punish incrementally

For each check with **zero P0 FP** in shadow:

1. `/vez tune <CheckName> shadow false`
2. If P1 edge case appears, raise `bufferToFlag` by 1: `/vez tune PredictionSpeed bufferToFlag 5`
3. Re-test sprint-jump and KB chains before moving to next check

**Aggressive defaults to verify:**

```yaml
# config-profiles/aggressive.yml (already applied via /vez profile aggressive)
engine.compensation.leniency-budget-cap: 0.08
combat-analysis.required-rotation.median-threshold-deg: 10.0
engine.exemption-caps.combat-grace-max-ticks: 5
engine.vehicle.buffer-to-flag: 3
```

## Week 2 — Combat geometry (PRISM)

### Shadow order (do not skip)

1. **`PrismInteractionLegality`** (absorbs reach, hitbox, backtrack, lag-range, no-rotation, rotation-ray)  
   - Tune: `/vez tune PrismInteractionLegality maxReach 3.05`  
   - Pass: rewound AABB at 2.8–3.2 block trades; no cancel on legit edge hits
2. `PrismInteractReach` — structural interact distance (separate from player-hit combat)
3. `PrismPacketOrderA`–`D`  
   - RT3-004 fixed in v1.1.1 (attack-before-position no longer bypasses via combat grace)
4. `PrismAutoClickA`–`D`  
   - Legit competitive CPS band: 8–15 CPS; macro should flag within 30s

### Enable combat checks

Same shadow → enable flow. Tune `cancelOnFlag` on **`PrismInteractionLegality`** only after packet-order checks are clean.

## Week 2–3 — Silent aim stack (CHARACTERISTICS) — enable last

1. Shadow `CharSilentAim` + `CharAimCorrelation` for **48 hours** of real Pot PvP
2. Staging scenario 7: silent-aim bot at &lt; 1.2 blocks must flag (uses s8 required-rotation + close-range boost)
3. Enable punish only after zero FP on:
   - W-tap / S-tap first hits
   - Blockhit sprint resets
   - Jump-crit chains
   - Counterstrafe spacing

## Punishment ladder (aggressive)

Verify in `config-profiles/aggressive.yml`:

| Setting | Competitive value |
|---------|-------------------|
| `punish.ban-vl` | 25 (CHARACTERISTICS tier checks ban faster) |
| `combat-mitigation.drop-hits-on-flag` | true |
| `combat-mitigation.punitive-setback` | true |
| `punish.execution.type` | BANWAVE |

Test banwave on **alt accounts** before production. Confirm staging scenario 6 (Anti-KB setback ordering).

## Live tune commands

```
/vez tune CharSilentAim shadow true
/vez tune CharSilentAim bufferToFlag 5
/vez tune PredictionSpeed engineHorizontalOffset 0.032
/vez status
/vez trace <player>
```

## Exit gate

- Cheat bots flagged on test arena within 60s sustained use
- Zero P0 FP on known-legit competitive mechanics (see [fp-soak-protocol.md](fp-soak-protocol.md))
- All 12 staging scenarios pass on aggressive ([staging-results.md](staging-results.md))
