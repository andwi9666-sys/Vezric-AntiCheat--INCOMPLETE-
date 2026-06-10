# Context-Aware Testing Plan

Date: March 13, 2026

## Legit PvP Scenarios

Run these with low, medium, and high ping where possible:

1. W-tap and S-tap first-hit sequences.
2. Blockhit sprint resets in close trades.
3. Quarter-select and deep-select hit selecting.
4. Midtrading with intentional rhythm changes.
5. Triple-hit sequencing with slight strafe offset.
6. Jump resetting on second, third, and fourth trade hits.
7. Counterstrafing into incoming knockback.
8. Walking through an opponent and reacquiring aim.
9. Combo jumping and jumping during sustained combos.
10. Defensive trade disruption near the edge of range.
11. Flat-ground clean combos at near-perfect 3 block distance.
12. Stair, slab, wall, corner, ceiling, and edge fights.
13. Inventory open/close spam with normal movement and clicks.
14. Fast diagonal bridge, sprint jump bridge, and safe speed bridge.

Expected result:
- One-off strange samples should not flag.
- Dirty samples should decay or contribute reduced evidence.
- Repeated clean legit mechanics should remain safe.

## Cheat Scenarios

1. Blatant horizontal anti-KB.
2. Reduced but repeated anti-KB in clean open-ground trades.
3. Repeated impossible reach over clean line-of-sight hits.
4. Backtrack using delayed packet manipulation or fake latency.
5. KillAura/no-swing packet-order abuse.
6. AimAssist with machine-like follow precision.
7. Autoclicker with narrow timing spread and stable attack correlation.
8. Scaffold placing behind/without legit aim support.
9. GUI movement hacks.
10. NoSlow while eating/using items.
11. Fly, phase, blink, and step in clean areas.

Expected result:
- Repeated impossible clean samples should build confidence quickly.
- Severe blatant cases should still punish faster than subtle cases.

## Network And Server Noise

1. Stable 20ms to 40ms.
2. Stable 50ms to 90ms.
3. Stable 120ms to 180ms.
4. Jitter spikes of 10ms to 25ms.
5. Packet burst simulation after brief choke.
6. Recent teleport or setback.
7. Recent velocity stack from multiple hits.
8. Temporary TPS dip.

Expected result:
- Dirty sample quality should drop.
- Debug output should show why the sample was discounted.
- Noise should not create standalone punishable evidence.

## 4. Manual Setback Validation

Run on a live test server after each hardening pass:

1. Speed violation in open ground — setback lands on last validated position, no immediate re-flag loop.
2. Fly/hover violation in open ground — windowed hover triggers setback without rubber-banding the correction itself.
3. Phase violation through a wall — setback rejects stale/cross-world/chunk-unloaded anchors.
4. Blink release after packet choke — player resumes without timer debt false-flag from the release burst.
5. Timer violation during a clean fight — only position packets contribute drift; rotation-only packets do not inflate debt.
6. Anti-KB after normal, sprint, jump-reset, wall, and ceiling hits — punitive setback uses teleport exemption before packet send.
7. Silent-aim bot at 3–4 blocks — required-rotation median rises over 8 hits; CharSilentAim snap ratio reader fires.
8. Reach bot with rewound positions — classifier uses compensated AABB, not live Bukkit location.
9. Legit sprint/W-tap/blockhit/bridging at 20/50/120/180ms ping — no false setback or combat cancel.

Expected result:
- Setback executes with teleport exemption **before** position packet send.
- Circuit breaker freezes movement after 3 setbacks / 2s instead of granting free movement.
- Next movement packet after setback does not immediately re-flag from the correction.

## 5. Staging Release Checklist (v1.0.0-hardened)

Run on a 1.8.8 Spigot test server before production deploy. Mark each row when validated.

| # | Scenario | Automated coverage | Staging status |
|---|----------|-------------------|----------------|
| 1 | Speed setback, no re-flag loop | `SetbackRateLimiterTest`, `SetbackUtilTest` | _pending operator_ |
| 2 | Fly/hover windowed detection | `FlyPatternUtilTest`, `PredictionTierSignalsTest` | _pending operator_ |
| 3 | Phase stale-anchor rejection | `SetbackUtilTest` | _pending operator_ |
| 4 | Blink release / timer debt | `PlayerClockTest` | _pending operator_ |
| 5 | Rotation-only packets skip timer | `PlayerClock` position gate | _pending operator_ |
| 6 | Anti-KB punitive setback ordering | `MovementEnforcement` code path | _pending operator_ |
| 7 | Silent aim required-rotation median | `RequiredRotationUtilTest`, `CharSilentAimSignalsTest` | _pending operator_ |
| 8 | Reach rewound AABB | `CombatHitClassifierTest`, `CombatRewindNullSafetyTest` | _pending operator_ |
| 9 | Legit ping sweep 20–180ms | `CombatFalsePositiveGuardTest` | _pending operator_ |
| 10 | Quit buffer purge | `CheckClearPlayerTest`, `SimulationSubCheckClearPlayerTest`, `TierCheckBufferLifecycleTest` | _pending operator_ |
| 11 | `/vez reload` buffer flush | `TierCheckManager.clearBuffers` + `SimulationSubCheck.clearAll` | _pending operator_ |
| 12 | Inventory-move silent aim signal | `CharSilentAimSignalsInventoryTest` | _pending operator_ |

**Release gate (CI):** PASSED @ `2f41a2c` — `mvn clean package` green, **339** unit tests, jar at `target/VezAntiCheat-1.0.0.jar`.

**Release gate (git):** tag `v1.0.0-hardened` @ `2f41a2c`. Push when remote is configured:

```bash
git remote add origin <repository-url>
git push -u origin main
git push origin v1.0.0-hardened
```

**Live staging (§5 table):** requires a 1.8.8 Spigot test server with PacketEvents; automated suites above cover logic paths — mark each row after in-game validation.

## Debug Review

For every intentional flag candidate, verify debug includes:

1. Expected vs observed behavior.
2. Ping and jitter at event time.
3. Sample cleanliness.
4. Any exemptions or downweighting.
5. Current buffer or confidence state.
6. Why the pattern is impossible rather than merely unusual.
