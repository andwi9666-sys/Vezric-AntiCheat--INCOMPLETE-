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
