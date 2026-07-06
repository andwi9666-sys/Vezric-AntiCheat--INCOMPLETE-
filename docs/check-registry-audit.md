# Check Registry Audit — v1.1.1

Matrix of tier check registration, YAML presence, and runtime dispatch after deep-tuning reconciliation.

## Summary

| Metric | v1.1.0 | v1.1.1 |
|--------|--------|--------|
| Registered checks | 90 | **100** |
| Ghost `prism.yml` entries | 21 | **0** |
| CI drift guard | none | `TierCheckRegistryTest` |

## Absorbed combat checks (YAML removed — tune `PrismInteractionLegality`)

| Legacy YAML key | Runtime authority | `CheckConfigUtil` alias |
|-----------------|-------------------|-------------------------|
| `PrismReachA/B/C` | `PrismInteractionEvaluator.evaluateReach` | → `PrismInteractionLegality` |
| `PrismHitboxA/B` | Hitbox miss-ratio in evaluator | → `PrismInteractionLegality` |
| `PrismBackTrack` | Rewind staleness in evaluator | → `PrismInteractionLegality` |
| `PrismLagRange` | `LagrangeUtil` corroboration | → `PrismInteractionLegality` |
| `PrismNoRotationA/B/C` | `evaluateNoRotationA/B` | → `PrismInteractionLegality` |
| `PrismRotationRay` | `evaluateRotationRay` | → `PrismInteractionLegality` |

Tunable keys consolidated under `PrismInteractionLegality` in `tiers/prism.yml`:

- `maxReach`, `blatantReachOver`, `hitboxMinSamples`, `missRatioThreshold`
- `lagrangeMinConfidence`, `backtrackMaxStaleMs`, `noRotationMaxAgeMs`, `rotationRayMinAngle`

## Newly registered checks (were dead code)

| Check | Dispatch hook |
|-------|---------------|
| `PrismBadPacketsG` | `onHeldItemChange` |
| `PrismBadPacketsH` | `onHeldItemChange` |
| `PrismBadPacketsI` | `onFlyingPacket` |
| `PrismBadPacketsJ` | `onFlyingPacket` |
| `PrismBadPacketsM` | `onFlyingPacket` |
| `PrismMultiActionsC` | `onWindowClick` |
| `PrismMultiActionsD` | `onCloseInventory` |
| `PrismMultiActionsE` | `onArmSwing` |
| `PrismMultiActionsF` | `onInteractEntity` |
| `PrismMultiActionsG` | `onAttack` |

## CI guard

`TierCheckRegistryTest.everyEnabledPrismYamlKeyIsRegistered` fails the build if any enabled section in `tiers/prism.yml` lacks a matching `TierCheckRegistry` entry.

## Operator tuning

Use `/vez tune PrismInteractionLegality <key> <value>` instead of legacy reach/hitbox keys. Legacy names still resolve via `CheckConfigUtil` for migrated servers.
