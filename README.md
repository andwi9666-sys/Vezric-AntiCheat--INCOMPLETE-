# Perplexion AntiCheat

Premium anticheat for **Minecraft 1.8.8** Spigot/Paper networks. Engine-authoritative movement prediction, transaction-based lag compensation, silent-aim signal stacking, and evidence-scored banwave punishments — designed for low overhead and tuned to reduce cheating without gambling on false bans.

> **Rebrand notice (1.2.0):** Perplexion AntiCheat is the new name for VezAntiCheat. Same product line, same data formats — see [docs/INSTALL.md](docs/INSTALL.md) for upgrade notes. Legacy `/vez` commands and `watchdog.*` permissions still work.

## Features

- **100+ checks across four tiers:**
  - **Characteristics** — combat heuristics, including the silent-aim signal stack
  - **Prism** — packet, interaction, reach, and scaffold analysis
  - **Simulation** — movement sub-signals
  - **Prediction** — Grim-style offset engine for engine-authoritative movement
- **Transaction-based lag compensation** with combat rewind, so reach and hit checks judge what the attacker actually saw.
- **Bedrock + Via compatibility layer (new in 1.2.0)** — protocol detection for ViaVersion/ViaBackwards/ViaRewind, plus Geyser/Floodgate handling (aim-check exemption, scaled scaffold buffers, reach margin). Details in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- **Ping-scaled exemption windows** — laggy players get proportionally wider grace instead of flat global leniency.
- **Punishment safety modes** — `silent`, `alerts-only`, `mitigation`, `banwave` (default), `instant`. Banwave punishments are evidence-scored and delayed, with a 30d → 90d → 365d → permanent ladder. Punishments defer automatically during low TPS, and `vez.bypass` always wins.
- **Config profiles** — `lenient` / `balanced` (shipped default) / `aggressive`, applied live with automatic backup and reload.
- **Self-tuning tools (new in 1.2.0)** — flag analytics with a built-in advisor (`/perplexion recommendations`), per-check performance counters (`/perplexion perf`), check inventory (`/perplexion checks`), and one-command debug export (`/perplexion exportdebug`).
- **1.2.0 internals** — full thread-safety overhaul of the punishment/alert/entity paths, a per-tick EntityIndex for O(1) packet-path lookups, NaN-rotation two-strike handling, scaffold/badpackets false-positive hardening, and debounced YAML persistence.

No anticheat detects every cheat, and Perplexion does not claim to. It is built to reduce cheating to a manageable level while keeping legitimate players safe — provided you stage your configuration before punishing (see below).

## Requirements

- **Spigot or Paper 1.8.8** (this release line supports 1.8.8 servers only)
- **Java 8**
- **[PacketEvents](https://github.com/retrooper/packetevents) 2.12.x** — hard dependency, installed as a separate plugin (not bundled)
- Optional coexistence: Vulcan, ProtocolLib, ProtocolSupport, ViaVersion, ViaBackwards, ViaRewind, Geyser-Spigot — see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)

## Quick start

1. Install **PacketEvents 2.12.x** into `plugins/`.
2. Drop `Perplexion-1.2.0.jar` into `plugins/`.
3. **Restart the server** (full restart — never Bukkit `/reload`). This generates `plugins/Perplexion/config.yml` at config-version 18.
4. Verify: `/perplexion status` — PacketEvents hooked, checks enabled, TPS readout healthy.
5. Apply a profile:

   ```
   /perplexion profile balanced     # shipped default — general PvP networks
   /perplexion profile lenient      # high-ping / casual / Bedrock-heavy
   /perplexion profile aggressive   # competitive Pot/HCF/UHC — staging required
   ```

6. **Before enabling punishments in production**, run the full [docs/STAGING_TEST_CHECKLIST.md](docs/STAGING_TEST_CHECKLIST.md) on a staging server. False bans caused by enabling `aggressive` or `instant` settings without staging are not covered by support.
7. Staff: `/alerts` to toggle alerts, `/flags` for the flag history GUI, `/perplexion verbose on` for triage.

Full deployment guide: [docs/INSTALL.md](docs/INSTALL.md)

## Config profiles

| Profile | Use case |
|---------|----------|
| `lenient` | High-ping networks, casual hubs, Bedrock-heavy player bases |
| `balanced` | **Shipped default** — general PvP networks |
| `aggressive` | Competitive Pot/HCF/UHC, low-latency arenas — staging sign-off required |

```
/perplexion profile lenient|balanced|aggressive
```

Profiles are bundled in the jar and merged over defaults. A timestamped backup of `config.yml` is created on apply, followed by an automatic reload. Selection guidance: [docs/PROFILE_RECOMMENDATIONS.md](docs/PROFILE_RECOMMENDATIONS.md)

## Punishment safety modes

Set via `punish.safety-mode` in `config.yml`:

| Mode | Behavior |
|------|----------|
| `silent` | Detect and record only — no staff alerts, no punishments |
| `alerts-only` | Staff alerts, no punishments |
| `mitigation` | Alerts plus in-game mitigation (setbacks/blocking), no bans |
| `banwave` | **Default.** Evidence-scored, delayed punishments on the 30d → 90d → 365d → permanent ladder |
| `instant` | Immediate punishments — never a default; requires staging sign-off per [docs/STAGING_TEST_CHECKLIST.md](docs/STAGING_TEST_CHECKLIST.md) |

Punishment execution defers automatically while TPS is low (lag gate). Players with `vez.bypass` are always exempt.

## Commands

Primary command: `/perplexion` — aliases `/pe` and `/vez` (the legacy alias still works after the rebrand). All subcommands have tab completion.

| Command | Permission | Description |
|---------|------------|-------------|
| `/perplexion status` | `vez.admin` | Health overview: PacketEvents hook, checks enabled, TPS, update line |
| `/perplexion checks` | `vez.admin` | List every check with its tier and enabled state |
| `/perplexion perf` | `vez.admin` | Per-check performance counters |
| `/perplexion recommendations` | `vez.admin` | Tuning advisor driven by flag analytics |
| `/perplexion info` | `vez.admin` | Version, build, and environment details |
| `/perplexion profile <name>` | `vez.admin` | Apply `lenient`/`balanced`/`aggressive` (auto-backup + reload) |
| `/perplexion tune <check> <key> [value]` | `vez.admin` | Live check tuning |
| `/perplexion trace <player>` | `vez.staff` | Per-player diagnostics snapshot |
| `/perplexion exportdebug` | `vez.admin` | Export a debug bundle (required for false-positive reports) |
| `/perplexion verbose on/off` | `vez.staff` | Verbose flag output for triage |
| `/perplexion debug` | `vez.admin` | Debug logging controls |
| `/perplexion reload` | `vez.admin` | Reload config — always use this instead of Bukkit `/reload` |
| `/perplexion on` / `off` | `vez.admin` | Master switch |
| `/perplexion announce` | `vez.admin` | Alert/punishment announcement controls |
| `/perplexion ai` | `vez.admin` | Characteristics-tier (combat heuristics) controls |
| `/perplexion combat` | `vez.admin` | Combat and lag-compensation controls |
| `/alerts` | `vez.staff` | Toggle staff alerts |
| `/flags` | `vez.staff` | Flag history GUI |
| `/banwave` | `vez.admin` | Review and manage the banwave queue |

### Permissions

| Permission | Grants |
|------------|--------|
| `vez.admin` | Full access to all commands and configuration |
| `vez.staff` | Alerts and diagnostics (`/alerts`, `/flags`, trace, verbose) |
| `vez.bypass` | Full exemption from checks — never grant to players |

Legacy `watchdog.*` permissions are still honored for servers upgrading from older releases.

## Documentation

- Buyer setup guide: [docs/BUYER_SETUP_GUIDE.md](docs/BUYER_SETUP_GUIDE.md)
- Installation and upgrade: [docs/INSTALL.md](docs/INSTALL.md)
- Profile selection: [docs/PROFILE_RECOMMENDATIONS.md](docs/PROFILE_RECOMMENDATIONS.md)
- Staging checklist (required before punishing): [docs/STAGING_TEST_CHECKLIST.md](docs/STAGING_TEST_CHECKLIST.md)
- Compatibility matrix: [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)
- Support and false positives: [docs/SUPPORT.md](docs/SUPPORT.md)
- Terms of service: [docs/TERMS_OF_SERVICE.md](docs/TERMS_OF_SERVICE.md)
- Refund policy: [docs/REFUND_POLICY.md](docs/REFUND_POLICY.md)
- License policy: [docs/LICENSE_POLICY.md](docs/LICENSE_POLICY.md)

## License

Proprietary, licensed **per network**: one purchase covers one server network (lobby and game servers under common ownership). Redistribution, leaking, reselling, key sharing, and decompiling (except where law permits) are prohibited. Full terms: [docs/LICENSE_POLICY.md](docs/LICENSE_POLICY.md) and [docs/TERMS_OF_SERVICE.md](docs/TERMS_OF_SERVICE.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
