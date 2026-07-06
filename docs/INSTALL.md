# Installation Guide — Perplexion AntiCheat 1.2.0

New-buyer walkthrough with screenshots-level detail: [BUYER_SETUP_GUIDE.md](BUYER_SETUP_GUIDE.md).
This page is the operator reference.

## 1. Server requirements

| Requirement | Version |
|-------------|---------|
| Server | Spigot or Paper **1.8.8** (only supported line) |
| Java | 8+ |
| PacketEvents | **2.12.x** (separate plugin, hard dependency) |

## 2. Folder layout

```
server/
  plugins/
    packetevents-spigot-2.12.x.jar
    Perplexion-1.2.0.jar
  plugins/Perplexion/
    config.yml          # generated on first run (config-version 18)
    tiers/              # per-check tier YAML (characteristics/prism/simulation/prediction)
    punishments.yml     # marks, ban counts, recent punishments
    banwave.yml         # queued punishments
    debug/              # /perplexion exportdebug output
```

## 3. First boot

1. Start the server with PacketEvents + Perplexion installed. **Always a full
   restart** — never Bukkit `/reload` or PlugMan: packet hooks cannot re-inject
   into live connections.
2. Console must show the startup banner:
   `Perplexion enabled. PacketEvents=true tierChecks=… packetHooks=true safetyMode=BANWAVE`
3. Run `/perplexion status` (aliases `/pe`, `/vez`) — packet hooks true,
   punishment mode shown, TPS healthy.

## 4. Upgrading from VezAntiCheat 1.1.x

- The data folder moves from `plugins/VezAntiCheat/` to `plugins/Perplexion/`.
  Copy `punishments.yml` and `banwave.yml` across if you want to keep marks,
  ban counts, and the queued banwave.
- config-version bumps 17 → 18: your old config is preserved as
  `config.yml.old` and a fresh one is generated. Re-apply custom values by hand
  or re-apply your profile.
- `punish.watchdog.*` keys still work but new configs use
  `punish.announcements.*`. **Set `punish.announcements.appeal-url` to your own
  appeal page** — it ships as a placeholder.
- `/vez` keeps working as an alias; `/watchdog` and `/wd` are removed.
  `watchdog.*` permissions are still honored.

## 5. Choose a profile

```
/perplexion profile balanced     # shipped default — general PvP networks
/perplexion profile lenient      # 150ms+ ping, casual hubs, Bedrock-heavy
/perplexion profile aggressive   # competitive Pot/HCF/UHC — staging sign-off required
```

Profiles merge over jar defaults — keys a profile omits are never dropped.
Applying creates a timestamped `config.yml.bak-*` and reloads automatically.
Selection guidance and the exact value differences: [PROFILE_RECOMMENDATIONS.md](PROFILE_RECOMMENDATIONS.md).
Competitive shadow → enable workflow: [competitive-tuning.md](competitive-tuning.md).

## 6. Choose a punishment safety mode

`punish.safety-mode` in config.yml: `silent` → `alerts-only` → `mitigation` →
`banwave` (default) → `instant` (never a default). Recommended first week:
`alerts-only` or `mitigation`, stepping up to `banwave` once alerts look clean.
Run `/perplexion recommendations` after a day of traffic for data-driven advice.

## 7. Staff setup

- Grant `vez.staff` for `/alerts`, `/flags`, `/perplexion verbose|trace|info`.
- Grant `vez.admin` for `/perplexion profile|reload|tune|on|off`.
- `vez.bypass` fully exempts a player — never grant it to regular players.

## 8. Before production punishments

Run the full [STAGING_TEST_CHECKLIST.md](STAGING_TEST_CHECKLIST.md) on a staging
server — including the 1.2.0 PrismAutoClick shadow-soak — and record results in
[staging-results.md](staging-results.md). False bans caused by enabling
`aggressive`/`instant` without staging are excluded from support and refunds
(see [TERMS_OF_SERVICE.md](TERMS_OF_SERVICE.md) §7).

## 9. Performance sampling

Off by default (zero overhead). For benchmark runs only:

```yaml
diagnostics:
  perf-sampling-enabled: true
```

Then `/perplexion perf`, record per [performance-benchmark.md](performance-benchmark.md),
and disable again in production.

## 10. Licensing (marketplace + direct sales)

### Marketplace (Polymart / BuiltByBit)

Ship the standard jar. Leave the license gate disabled — the platform validates
buyers:

```yaml
license:
  enabled: false
```

### Direct sales

Buyers enable in-plugin validation after purchase:

```yaml
license:
  enabled: true
  key: 'YOUR-LICENSE-KEY-HERE'
  grace-hours: 24
  validation-url: ''   # optional HTTPS endpoint; offline format check if empty
```

During grace, staff see a warning; checks disable only after grace expires if
the key is invalid. License scope and transfers: [LICENSE_POLICY.md](LICENSE_POLICY.md).

## 11. Updates

```yaml
updates:
  check-enabled: true
  manifest-url: 'https://your-cdn.example/perplexion/manifest.json'
```

Manifest JSON shape: `{"latest":"1.2.0","changelog":"https://..."}` — surfaced
in `/perplexion status`.
