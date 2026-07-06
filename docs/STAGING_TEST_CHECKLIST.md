# Staging Test Checklist — Perplexion AntiCheat 1.2.0

Run this checklist on a **staging server** (Spigot/Paper 1.8.8 + PacketEvents 2.12.x)
before enabling punishments in production. Record results in `docs/staging-results.md`.
Items marked **[GATE]** block a production rollout or marketplace release.

Setup for all scenarios: `balanced` profile, `punish.safety-mode: alerts-only`
(so testers are never kicked), one staff account with `/alerts` on and
`/perplexion verbose on`.

Expected outcome for every legit scenario: **no flags**, or only isolated
single-buffer verbose lines that never reach an alert. Any repeated alert on a
legit scenario is a finding — capture it with `/perplexion exportdebug <player>`.

## 1. Legit movement (no flags expected)

- [ ] Sprint, sprint-jump, and W-tap/S-tap strafing for 5 minutes of normal PvP movement
- [ ] Jump-resetting on hits (combo practice)
- [ ] Ladders and vines: climb up/down, jump onto ladder mid-air
- [ ] Water/lava: swim, jump in/out, sprint-swim
- [ ] Cobwebs: walk through, fall into, jump inside
- [ ] Ice and packed ice: sprint-jump runs
- [ ] Slabs/stairs: run up and down repeatedly, jump on half-blocks
- [ ] Slime block bounces (if present on your map)
- [ ] Soul-sand-like slow surfaces if custom
- [ ] Speed I/II and Jump Boost potions: sprint-jump with each
- [ ] Eating/drinking while moving (NoSlow surface)
- [ ] Bow drawn while strafing
- [ ] Sneaking at block edges

## 2. Legit combat (no flags expected) **[GATE]**

- [ ] Normal 1v1 PvP, 10+ minutes, both players ~<50ms ping
- [ ] **High-CPS legit clicking: butterfly/jitter clicking 12–18 CPS** while fighting
- [ ] Block-hitting (sword right-click spam mid-fight)
- [ ] W-tapping and S-tapping combos
- [ ] Rod + sword switching mid-fight; fishing rod hits
- [ ] Bow shots at moving targets, including quick-scoping at close range
- [ ] Fast target switching between two agreed fighters
- [ ] Hit-selecting (deliberate slow timing between hits)
- [ ] Fast 180° turns + immediate hits (good aim simulation)
- [ ] Knockback exchange: trade hits without sprint to take full KB

## 3. Legit building (no flags expected) **[GATE]**

- [ ] Normal bridging (sneak-peek bridging) for 50+ blocks
- [ ] Speed bridging (breezily/strafe-style if testers can)
- [ ] **Godbridging or precise pitch-held bridging** (this gates the scaffold pitch tuning)
- [ ] Towering straight up (jump-place) 30+ blocks
- [ ] Build during combat (combat-build context)

## 4. Network and server stress (no flags expected)

- [ ] Player at 150–200ms simulated ping: repeat sections 1–3 highlights
- [ ] Player at 300ms ping: movement + combat basics (checks should mostly gate off)
- [ ] Jittery connection (variable latency): combat for 5 minutes
- [ ] TPS dip: load the server (or use a stress plugin) below `lag.min-tps` and verify
      flags pause and `/perplexion recommendations` notices the lag correlation
- [ ] Teleports: /tp during combat and during falls — no NoFall/Speed flags
- [ ] Respawn after death mid-combat — no flags within grace
- [ ] World change (Nether portal or /mv tp equivalent) — no flags
- [ ] Knockback + immediate teleport (e.g. arena reset) — no velocity flags
- [ ] TNT/creeper explosion knockback — no velocity/speed flags
- [ ] Join during combat zone (login directly into a fight area)

## 5. Compatibility **[GATE if you advertise support]**

- [ ] ViaVersion: 1.19+ client joins, moves, fights — no flags, `/perplexion info` shows client version
- [ ] Geyser/Floodgate: Bedrock client joins — `/perplexion exportdebug` shows `bedrock: true`;
      aim checks stay silent; bridging does not flag
- [ ] Vulcan coexistence (if applicable): both plugins enabled, no startup errors,
      `/perplexion status` shows packet hooks active

## 6. Detection sanity (flags EXPECTED) **[GATE]**

Use a test client on a throwaway account, with server consent, in an isolated arena.

- [ ] Fly (vanilla-style) → Prediction flags within seconds
- [ ] Speed (1.5–2×) → flags within seconds
- [ ] KillAura at 4+ block reach → reach/interaction flags
- [ ] Aim assist / silent aim → Characteristics flags (shadow → check risk score via /perplexion ai)
- [ ] Autoclicker locked at 12–14 CPS → AutoClick flags after the buffer window
- [ ] Scaffold module → Scaffold flags while bridging
- [ ] Anti-KB → velocity flags after several hits
- [ ] Verify a flagged player's VL decays after they stop (watch /perplexion info)

## 7. Punishment pipeline **[GATE]**

Switch to `punish.safety-mode: banwave` for this section, on the staging server only.

- [ ] Cheat until evidence queues; confirm entry appears in /banwave list
- [ ] Banwave executes after its delay; console shows the punish command; ban screen
      shows the Perplexion message and YOUR appeal URL (not a placeholder)
- [ ] `punish.lag-gate`: hold TPS low at execution time → punishment defers with a log
      line and retries later
- [ ] Player with `vez.bypass` permission cheats → zero flags, zero punishments
- [ ] `silent` mode: flags recorded (visible in /flags GUI afterwards) but no staff chat
- [ ] `mitigation` mode: setbacks visibly correct movement cheats, no bans queue

## 8. Lifecycle and operations

- [ ] `/perplexion reload` mid-combat: no errors, checks resume, buffers reset
- [ ] `/perplexion profile lenient` then `aggressive` then `balanced`: applies, backs up
      config, reloads cleanly each time
- [ ] Player quits mid-flag-streak, rejoins: VL state cleared, no stale alerts
- [ ] Server restart: punishments.yml and banwave.yml persist (marks/ban counts/queue)
- [ ] Full server stop/start (NOT /reload or PlugMan) — clean startup banner with
      `safetyMode=` line
- [ ] Tab completion works on /perplexion, /pe, and /vez

## 9. 1.2.0-specific soak items **[GATE for marketplace release]**

- [ ] **PrismAutoClickA/B/C swing reclassification soak**: set `PrismAutoClickA.shadow: true`
      (and B/C) and run 2+ hours of real PvP including mining sessions; review shadow
      flags via the risk score — legit miners and butterfly clickers must not accumulate.
      Then re-enable. This guards the 1.2.0 change from `getTargetBlock` raytracing to
      dig-packet mining detection.
- [ ] `/perplexion recommendations` after 1 hour of mixed traffic: output is sane and
      matches what staff observed
- [ ] `/perplexion perf` with `diagnostics.perf-sampling-enabled: true`: movement and
      packet averages recorded for docs/performance-benchmark.md
- [ ] `/perplexion exportdebug` on 2–3 players: YAML files written and complete

## Sign-off

| Field | Value |
|---|---|
| Tester(s) | |
| Date | |
| Server build | |
| PacketEvents version | |
| Perplexion version | 1.2.0 |
| Sections passed | /9 |
| Findings filed | |
| Production rollout approved | yes / no |
