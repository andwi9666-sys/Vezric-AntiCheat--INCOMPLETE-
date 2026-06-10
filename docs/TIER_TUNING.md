# VezAntiCheat v1.1.0 — shipped patches and competitive tuning

Release tags: **`v1.0.0-hardened`** @ `ae140d2`, **`v1.1.0`** @ `d44019c`

## Tier defaults

| Tier | File | Default ban VL | Role |
|------|------|----------------|------|
| CHARACTERISTICS | `plugins/VezAntiCheat/tiers/characteristics.yml` | 25 | Behavioral heuristics (fast ban) |
| PRISM | `tiers/prism.yml` | 40 | Packet structure + geometry |
| SIMULATION | `tiers/simulation.yml` | 90 | Engine sub-signals |
| PREDICTION | `tiers/prediction.yml` | 150 | Full Grim offset (most lenient) |

## Commands

- `/vez status` — TPS, perf metrics, license/update line
- `/vez profile lenient|balanced|aggressive` — apply bundled profile (merge-over-defaults)
- `/vez tune <CheckName> <key> [value]` — live tune tier keys
- `/vez reload` — reload config + tiers; clears check buffers (VL store persists)

## Check inventory (v1.1.0)

| Tier | Count | Package |
|------|------:|---------|
| CHARACTERISTICS | 28 | `tier/characteristics/` |
| PRISM | 32 | `tier/prism/` |
| SIMULATION | 18 | `tier/simulation/` |
| PREDICTION | 15 | `tier/prediction/` (+ `PredictionVehicle`) |
| **Total** | **93** | ~11k LOC tier code |

## Competitive tuning (Pot / HCF / UHC)

**Recommended profile:** `aggressive`

```
/vez profile aggressive
```

See [competitive-tuning.md](competitive-tuning.md) for shadow → enable workflow.

Key aggressive deltas vs balanced:

| Key | Aggressive | Balanced |
|-----|------------|----------|
| `engine.compensation.leniency-budget-cap` | 0.08 | 0.12 |
| `combat-analysis.required-rotation.median-threshold-deg` | 10.0 | 12.0 |
| `engine.exemption-caps.combat-grace-max-ticks` | 5 | 8 |
| `engine.vehicle.buffer-to-flag` | 3 | 4 |
| `engine.vehicle.tolerance` | 1.10 | 1.15 |

## Shadow mode

Set `shadow: true` on any tier check to collect flags without ban. Recommended shadow-first order:

1. Movement: `PredictionSpeed`, `PredictionFly`, `PredictionVehicle`
2. Prism: `PrismRotationRay`, `PrismNoRotationA/B/C`, `PrismReachC`
3. Last: `CharSilentAim`, `CharAimCorrelation`

## Silent aim stack

1. Shadow Prism rotation/ray checks; verify no FOV FP at ping &lt; 120 ms
2. Enable reach cancel only after ray checks clean
3. Enable `CharSilentAim` last (8-signal aggregator + s8 required-rotation at close range)

## v1.1.0 shipped patches (was v1.1 backlog)

| ID | Fix | Files |
|----|-----|-------|
| RT1-002 | `couldSkipTick` no longer double-stacks in uncertainty + reduceOffset | `UncertaintyHandler.java` |
| RT1-003/004 | Per-window exemption caps for combat-grace and block-change | `PlayerData.java`, `MovementCheckRunner.java`, `engine.exemption-caps` |
| RT2-004 | Close-range silent aim: s8 required-rotation + boosted s5/s6/s7 below 1.2 blocks | `CharSilentAim.java` |
| RT5-002 | Vehicle speed envelope while mounted | `PredictionVehicle.java`, `VehicleMovementUtil.java` |
| RT6-002 | `Check.clearAll()` on `/vez reload` | `Check.java`, `TierCheckManager.java` |

**Tests:** 350 JUnit methods including `ExemptionCapTest`, `VehicleMovementUtilTest`, `ConfigProfileManagerMergeTest`.

## Config profiles

`/vez profile` merges bundled YAML over jar `config.yml` defaults (license, vehicle, exemption-caps never dropped).

Profiles include v1.1 keys: `engine.exemption-caps`, `engine.vehicle`, `license`, `diagnostics`, `updates`, `combat-mitigation`.

## Residual risks (monitor in staging)

| ID | Risk | Status |
|----|------|--------|
| RT3-004 | Packet-order (attack before position) | **Monitor** — patch only if staging reproduces |
| RT4-003 | VL persists across reload (`CheckVLStore`) | By design |
| RT4-003 | Quit/rejoin VL vs buffer asymmetry | Staff `/vez clear` for reset |

## Lag gates

Global gates in `config.yml` (`lag.enable-gates`, `max-ping`, `min-tps`) apply to all tiers. VL does not accumulate when gated.

## Legacy checks

Monolithic `checks.yml` is backed up as `checks.yml.legacy` on v16 migration. Tier checks replace legacy dispatch.

## v1.0.0-hardened highlights

Engine-authoritative movement, compensation budget cap, rewound reach classifier, CharSilentAim hardening, VL decay scheduler, simulation buffer purge on quit/reload, inventory-move signal.

Full red-team audit and RT3 table preserved in git history @ `56e880d` / `ae140d2`.
