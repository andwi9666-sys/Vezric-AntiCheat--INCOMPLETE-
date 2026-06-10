# Anticheat Hardening Workflow

This workflow is the project standard for improving VezAntiCheat checks. No anticheat is 100% foolproof, so the target is measurable: fewer false positives, faster detection for repeatable cheats, stable setbacks to the last validated position, and reproducible evidence for every tuning change.

## Boundaries

- Packet intake stays server-side through PacketEvents. Do not add secret client injection, client-side persistence, or code that runs inside a player's client.
- Raw packet coverage means intercepting client-to-server packets before Bukkit handles them, not modifying player devices.
- Every stricter threshold must be backed by replay data, diagnostics, or a documented manual test.

## 1. Build Baseline

Run this before changing checks:

```bash
mvn -q -DskipTests package
```

Record:

- Current jar hash and config profile.
- Enabled checks and global lag gates.
- TPS, ping range, and server build.
- Any existing false-positive reports with player ping and debug lines.

## 2. Collect Real Data

Use staff verbose/debug output and `DiagnosticsTracker` entries to collect both legit and cheat sessions.

Minimum legit set:

- Sprint, W-tap, S-tap, blockhit, jump reset, counterstrafe.
- Flat, stair, slab, ceiling, wall, corner, and edge fights.
- Normal bridging, speed bridging, inventory movement, eating, bow use.
- 20-40 ms, 50-90 ms, 120-180 ms, jitter, and temporary TPS dips.

Minimum cheat set:

- Anti-KB: zero vertical, reduced horizontal, reverse KB, impossible envelope.
- Movement: speed, fly, phase, blink, step, timer.
- Combat: reach, backtrack, no-swing, killaura, aim assist, autoclicker.
- Player checks: scaffold, no-slow, inventory, fast-break, nuker.

## 3. Improve One Check Family At A Time

For each family:

- Confirm it uses shared context where available: `CombatContextAnalyzer`, `MovementContextAnalyzer`, packet cadence, lag profile, and position history.
- Require repeated evidence for subtle cheats.
- Keep single-sample flags only for impossible protocol or geometry states.
- Add debug fields that explain expected value, observed value, tolerance, buffer, ping, jitter, TPS, exemptions, and sample cleanliness.
- Keep every rolling history bounded in `PlayerData`.

## 4. Setback Validation

Setbacks must target the last validated position before the detected cheat.

Required behavior:

- Prefer `PredictionState.lastKnownGoodLocation`.
- Use `PlayerData.lastMoveFrom` only as a fallback.
- Reject stale or cross-world targets.
- Preserve current yaw and pitch to avoid forced view snaps.
- Mark teleport exemption before teleporting.
- Verify that the next movement packet does not immediately reflag from the correction itself.

Manual tests:

- Speed/fly/phase violation in open ground.
- Blink release after a packet choke.
- Timer violation during a clean fight.
- Anti-KB correction after normal, sprint, jump-reset, wall, and ceiling hits.

## 5. Packet Intake Review

`PacketListener` should only update state and dispatch checks. Detection logic belongs in checks, prediction processors, or utility analyzers.

Check that these packet groups are covered:

- Movement: flying, position, rotation, position+rotation.
- Combat: animation, interact entity.
- Blocks: digging, block placement.
- Item use: sword block, bow/eat fallback through Bukkit events.

## 6. Verification Gate

A hardening pass is not complete until:

- `mvn -q -DskipTests package` passes.
- The changed check has at least one legit scenario and one cheat scenario documented.
- Debug output explains every new flag path.
- Setback behavior was tested for stale target, teleport exemption, and same-world validation.
- Config defaults are reviewed for balanced, lenient, and aggressive use.

## 7. Release Notes

For every release, write:

- Checks changed.
- Thresholds changed.
- New debug fields.
- False-positive cases tested.
- Cheat cases tested.
- Remaining risk.
