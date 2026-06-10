# Polar Tier Tuning Guide

VezAntiCheat v16 uses a four-tier Polar-style architecture. Each check has its own VL pool and `punishVl` ban threshold.

## Tier defaults

| Tier | File | Default ban VL | Role |
|------|------|----------------|------|
| CHARACTERISTICS | `plugins/VezAntiCheat/tiers/characteristics.yml` | 25 | Behavioral heuristics (fast ban) |
| PRISM | `tiers/prism.yml` | 40 | Packet structure + geometry |
| SIMULATION | `tiers/simulation.yml` | 90 | Engine sub-signals |
| PREDICTION | `tiers/prediction.yml` | 150 | Full Grim offset (most lenient) |

## Per-check keys

```yaml
CharSilentAim:
  tier: CHARACTERISTICS
  enabled: true
  shadow: false      # log only, no punish
  punishVl: 25       # ban when this check's VL reaches this
  failWeight: 1.0    # VL added per flag multiplier
  bufferToFlag: 4    # consecutive hits before flag
  decay: 0.15        # VL decay rate after grace period
  setbackEnabled: false
```

## Commands

- `/vez status` — tier check count and TPS
- `/vez tune <CheckName> <key> [value]` — live tune tier or legacy check keys
- `/vez reload` — reload `config.yml`, legacy `checks.yml.legacy`, and all `tiers/*.yml`

## Shadow mode

Set `shadow: true` on any tier check to collect flags without ban. Use for live tuning silent-aim stack:

- `PrismRotationRay`, `PrismNoRotationA/B/C`
- `CharSilentAim`, `CharAimCorrelation`

## Silent aim stack

Attack path runs Characteristics → Prism tiers. Recommended tuning order:

1. Enable shadow on Prism rotation/ray checks; verify no FOV false positives at 1.8 ping < 120ms
2. Lower `PrismReachC.cancelAttack` only after ray checks are clean
3. Enable `CharSilentAim` last (aggregates 8 behavioral signals)

## Lag gates

Global gates in `config.yml` (`lag.enable-gates`, `max-ping`, `min-tps`) apply to all tiers. VL does not accumulate when gated.

## Legacy checks

Monolithic `checks.yml` is backed up as `checks.yml.legacy` on v16 migration. Tier checks replace legacy dispatch; legacy files remain for reference only.

## Check inventory (v16)

| Tier | Count | Package |
|------|------:|---------|
| CHARACTERISTICS | 28 | `tier/characteristics/` |
| PRISM | 32 | `tier/prism/` |
| SIMULATION | 18 | `tier/simulation/` |
| PREDICTION | 14 | `tier/prediction/` |
| **Total** | **92** | ~11k LOC tier code |

All tier checks are standalone implementations with per-check VL pools. Tune thresholds under each check name in the matching `tiers/*.yml` file.

## v1.0.0-hardened release notes

### Checks / thresholds changed
- **PredictionGroundSpoof** — flags client-vs-**serverGround** mismatch (engine collision truth), not only predicted ground.
- **PredictionFly / FlyPatternUtil** — hover uses windowed count (8 of last 12 near-zero dy ticks), not consecutive hover ticks.
- **EngineMovementGrace** — slab/stair offset grace default tightened to **0.08** (was 0.12).
- **SpeedUtil** — post-KB allowance derived from `VelocityPredictionEngine` envelope via `KbSpeedAllowance`, not flat 0.22 + 500ms grace.
- **CombatHitClassifier** — uses rewound target AABB when `CombatResult.valid`; live-position fallback widens expansion by `combat-analysis.rewind.live-position-fallback-expansion-bonus`.
- **RequiredRotationUtil** — sustained median angular error over 8 hits feeds combat behavior score (closes classic silent aim).
- **CharSilentAim** — wires `killAuraASnapRatio`, ping-scaled snap threshold (no hard disable at 150ms), GCD lattice residue signal, center-bias range 1–4.5 blocks distance-tapered; close-range keeps correlation + center signals.
- **PrismAutoClickA** — CV band extended below 9 CPS with outlier-free-streak humanizer detection.
- **TargetSwitchAnalyzer** — 30s suspicious-switch counter; pre-aim halving removed for repeated suspicious switches.

### Evidence basis
- **339** JUnit tests green (`mvn test`); regression suites include `RequiredRotationUtilTest`, `GcdLatticeAnalysisTest`, `SpeedUtilEnvelopeTest`, `FlyPatternUtilTest`, `CheckClearPlayerTest`, `SimulationSubCheckClearPlayerTest`, `PrismAutoClickCvTest`, `CharSilentAimSignalsInventoryTest`.
- Manual matrix documented in `docs/testing-plan.md` §4.

### Phase 4 red-team dry loop (2 × 6 hunters)

Code-path audit across six hunter personas (movement, setback, combat rewind, silent aim, autclicker, session hygiene). Two consecutive rounds required a clean round with zero new P0/P1 findings before tag.

| Round | Outcome | Patches |
|-------|---------|---------|
| **1** | 8 actionable findings | RT4-001 setback pending clear; RT4-002 `max-pending-ms` ≥ circuit breaker; RT6-001 decay lag gate; RT5-001 enforcement anchor freeze; RT1-005 hover on exempt ticks; RT1-001 unverified accrual floor; RT4-004 `Check.clearPlayer` on quit; RT2-001/002 distributed snap + lattice conformity; RT2-003/005 pre-attack snap + live-rewind penalty (ping ≥ 100) |
| **2** | **CLEAN** | No new P0/P1; gap-closure pass verified by 339 tests + `mvn clean package` |

Hunter matrix (manual live validation still recommended on staging):

1. **Movement** — fly/hover, speed envelope, ground spoof, offset advantage
2. **Setback** — accept spam, pending timeout, blatant enforcement hold
3. **Combat rewind** — live fallback expansion, invalid-rewind behavior score at high ping
4. **Silent aim** — distributed snap, GCD lattice, pre-attack snap, center-bias close range
5. **Autoclicker** — PrismAutoClickA CV band below 9 CPS
6. **Session** — quit buffer purge (`Check` + `TierCheck` + `SimulationSubCheck`), VL decay under lag

### RT3 protocol / transaction audit (gap-closure pass)

| ID | Finding | Status |
|----|---------|--------|
| RT3-001 | Blink via movement-gap ledger farming | **Mitigated** — `PlayerClock.applyDriftToLedger` debits bounded amount on behind-ticks; teleport pauses accrual; `PredictionTimer` reads cumulative ledger |
| RT3-002 | Position-packet tick mis-bucketing under lag | **Fixed** — `positionPacketsThisTick` buckets on `TransactionTracker.currentServerTick()` |
| RT3-003 | Transaction-ID prediction for reach rewind | **Low risk** — negative action IDs; echoes cancelled on Netty thread; `CombatRewind` null-safe invalid result |
| RT3-004 | Packet-order abuse (attack before position) | **Monitor** — `PrismPacketOrderSupport` + combat grace windows; no standalone P0 in code audit |
| RT3-005 | Rotation-only timer inflation | **Mitigated** — `PlayerClock.onFlyingPacket` ignores rotation-only packets (`positionIncluded` gate) |

### Config profiles

`config-profiles/{lenient,balanced,aggressive}.yml` mirror hardened `combat-analysis.rewind`, `required-rotation`, `engine.unverified`, `engine.compensation.leniency-budget-cap`, and `setback-blocker.max-pending-ms`. **Lenient** adds `engine.slab-offset-grace: 0.12` (balanced/aggressive use tier default 0.08). Profiles inherit any keys omitted from `config.yml` defaults.

### Residual risks (documented, not blocking tag)

| ID | Risk | Mitigation / note |
|----|------|-------------------|
| RT1-002 | Positionless packet `couldSkipTick` leniency stacking | Monitor shadow flags; tighten in 1.1 if reproduced live |
| RT1-003/004 | Combat-grace / block-place exemption farming | Exemption ordering hardened; live soak on PvP arenas |
| RT2-004 | Close-range silent aim still down-weights some long-range signals | s5/s6 retained ≤ 1.2 blocks; tune `CharSilentAim` shadow first |
| RT4-003 | Quit/rejoin VL vs tier-buffer asymmetry (`CheckVLStore` persists) | By design for banwave continuity; `/vez clear` for staff reset |
| RT4-005 | `SimulationSubCheck` shared buffer bleed on quit | **Fixed** — `SimulationSubCheck.clearPlayer` wired via `Check.clearPlayer`; `clearAll` on `/vez reload` |
| RT6-002 | `/vez reload` may desync in-flight tier buffers | **Partial** — `TierCheck.clearAll` + `SimulationSubCheck.clearAll` on reload; prefer low-pop reload or full restart for prod |
| RT5-002 | Vehicle-mounted engine exempt path | Expected; vehicle checks are separate tier family |

- `partialKbRatio` retained — still read by `CharVelocityPattern` / `CharSilentAimSignals`.
- `inventoryMoveCount` now incremented on horizontal movement while inventory is open (`PacketListener` → `PlayerData.noteInventoryMoveTick`).
