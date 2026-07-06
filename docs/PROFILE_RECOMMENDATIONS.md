# Profile Recommendations — Perplexion AntiCheat 1.2.0

Perplexion ships three config profiles. Apply one in-game with:

```
/perplexion profile <lenient|balanced|aggressive>
```

Applying a profile backs up your current `config.yml` (timestamped `.bak` file in
`plugins/Perplexion/`), merges the profile over the bundled defaults (so no key is
ever lost), and reloads. Per-check thresholds in `tiers/*.yml` are shared by all
profiles — tune those live with `/perplexion tune <check> <key> <value>`.

## Which profile should I use?

| Your server | Profile |
|---|---|
| Just installed, still evaluating | **lenient** (or balanced with `punish.safety-mode: alerts-only`) |
| Typical PvP/survival/skyblock server | **balanced** (the shipped default) |
| Players regularly above 150ms ping, or Bedrock players via Geyser | **lenient** |
| Shared/budget host with TPS dips | **lenient** |
| Competitive 1.8 PvP (practice/HCF/pot) with low ping, stable TPS, and active staff | **aggressive** |

When in doubt: **balanced**. It is what the defaults, docs, and staging checklist
are tuned around.

## What actually differs

| Setting | lenient | balanced | aggressive |
|---|---|---|---|
| Punishment mode (`punish.safety-mode`) | mitigation (no bans) | banwave | banwave |
| Ban threshold (`punish.ban-vl`) | 35 | 25 | 18 |
| Evidence queue score | 15.0 | 12.0 | 10.0 |
| Hybrid fast-track for blatant cheats | off | on | on (min-flags 5) |
| Ping gate (`lag.max-ping`) | 200ms | 250ms | 350ms |
| TPS gate (`lag.min-tps`) | 19.0 | 18.5 | 17.5 |
| Teleport / velocity / blockstate / potion grace (ms) | 1200 / 600 / 700 / 900 | 900 / 500 / 550 / 700 | 700 / 350 / 450 / 550 |
| Ping-scaled grace cap (`exempt.ping-scaling.cap-ms`) | 200 | 150 | 100 |
| Combat hitbox expansion (full) | 0.12 | 0.10 | 0.08 |
| Bedrock scaffold buffer multiplier | 2.0 | 1.5 | 1.25 |

Reading the table: lenient widens every grace window and disables bans entirely
(flags become alerts + setbacks); aggressive narrows windows, lowers the ban
threshold, and gates fewer high-ping players out of checks — which is only safe
when your playerbase genuinely has low ping.

Two deliberate quirks worth understanding:

- **Aggressive raises `lag.max-ping` (350) instead of lowering it.** The ping gate
  *excludes* players from being checked. A competitive server wants more players
  checked, so the gate widens — the ping-scaled grace windows (cap 100ms) are what
  keep marginal-ping players safe instead.
- **Lenient uses mitigation mode, not banwave.** On a casual server a wrong setback
  costs nothing; a wrong ban costs a refund and a review. Staff still see every
  alert and can ban manually.

## The first 7 days (recommended rollout)

1. **Day 0** — Install on a **staging server** first (see `docs/STAGING_TEST_CHECKLIST.md`).
2. **Day 1–2** — Production with `lenient`, or `balanced` plus
   `punish.safety-mode: alerts-only`. Watch `/flags` and `/alerts`.
3. **Day 3** — Run `/perplexion recommendations`. It analyzes the last hour of flag
   data (volume, ping distribution, TPS correlation, per-player concentration) and
   tells you in plain language what to adjust.
4. **Day 3–5** — If alerts look clean (no known-legit players flagging), step up to
   `balanced`. Banwave punishments now arm, but bans are delayed and evidence-gated.
5. **Day 7+** — Competitive servers with clean data may step up to `aggressive`.
   Keep `instant` mode off unless you have completed the full staging checklist and
   accept the risk; it is intentionally never a profile default.

## False positive on a specific check?

1. `/perplexion exportdebug <player>` — writes a YAML report to `plugins/Perplexion/debug/`.
2. Raise that one check's buffer: `/perplexion tune <Check> bufferToFlag <higher>` —
   or shadow it (`/perplexion tune <Check> shadow true`) while you investigate.
3. Report it with the export attached (see `docs/FALSE_POSITIVE_REPORT_TEMPLATE.md`).
   Do not jump profiles over a single check; profiles are blunt instruments.
