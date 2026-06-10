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

## Debug Review

For every intentional flag candidate, verify debug includes:

1. Expected vs observed behavior.
2. Ping and jitter at event time.
3. Sample cleanliness.
4. Any exemptions or downweighting.
5. Current buffer or confidence state.
6. Why the pattern is impossible rather than merely unusual.
