# VezAntiCheat

Polar-tier anticheat for **Minecraft 1.8.8** Spigot/Paper networks. Engine-authoritative movement prediction, lag-compensated combat reach, silent-aim signal stacking, and configurable punishment ladders.

## Requirements

- **Java 8** server (1.8.8)
- **[PacketEvents](https://github.com/retrooper/packetevents)** (hard dependency — install in `plugins/` before VezAntiCheat)
- Optional coexistence: Vulcan, ViaVersion, Geyser (see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md))

## Quick start

1. Drop `VezAntiCheat-1.1.0.jar` into `plugins/`.
2. Install PacketEvents for your 1.8.8 build.
3. Start the server once to generate `plugins/VezAntiCheat/config.yml`.
4. Apply a profile (recommended **balanced**):

   ```
   /vez profile balanced
   ```

5. Verify: `/vez status` — PacketEvents ready, checks enabled, TPS healthy.
6. Staff: `/flags` for alerts, `/vez verbose on` for triage.

Full steps: [docs/INSTALL.md](docs/INSTALL.md)

## Config profiles

| Profile | Use case |
|---------|----------|
| `lenient` | High-ping / casual PvP, fewer setbacks |
| `balanced` | Default production tuning |
| `aggressive` | Competitive networks, tighter compensation cap |

```
/vez profile lenient|balanced|aggressive
```

Profiles are bundled in the jar; a timestamped backup of `config.yml` is created on apply.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/vez status` | `vez.admin` | Health, TPS, perf metrics, update line |
| `/vez on` / `/vez off` | `vez.admin` | Master switch |
| `/vez reload` | `vez.admin` | Reload config (prefer over `/reload`) |
| `/vez profile <name>` | `vez.admin` | Apply lenient/balanced/aggressive |
| `/vez tune <check> <key> [value]` | `vez.admin` | Live tier tuning |
| `/vez trace <player>` | `vez.staff` | Diagnostics snapshot |
| `/flags` | `vez.staff` | Alert toggle / flag history GUI |

`watchdog` and `wd` are silent aliases for `/vez`.

## Support

- Install & tuning: [docs/INSTALL.md](docs/INSTALL.md)
- False positives: [docs/SUPPORT.md](docs/SUPPORT.md)
- Known limits: [docs/TIER_TUNING.md](docs/TIER_TUNING.md)

## License

Proprietary — see [LICENSE](LICENSE). Marketplace purchases may use platform licensing without in-plugin keys.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
