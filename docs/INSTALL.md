# Installation Guide

## 1. Server requirements

| Requirement | Version |
|-------------|---------|
| Server | Spigot or Paper **1.8.8** |
| Java | 8+ |
| PacketEvents | 2.x build compatible with 1.8.8 (pin the version you test) |

## 2. Folder layout

```
server/
  plugins/
    packetevents-<version>.jar
    VezAntiCheat-1.1.0.jar
  plugins/VezAntiCheat/
    config.yml          # generated on first run
    tiers/              # per-check tier YAML
```

## 3. First boot

1. Start the server with PacketEvents + VezAntiCheat.
2. Confirm console shows PacketEvents ready and VezAntiCheat enabled.
3. Run `/vez status` — all green before going live.

## 4. Choose a profile

```
/vez profile aggressive   # competitive Pot/HCF/UHC (recommended)
/vez profile balanced     # general PvP
/vez profile lenient      # high-ping / casual
```

| Profile | When to use |
|---------|-------------|
| `aggressive` | Competitive Pot/HCF/UHC, low-latency arenas |
| `balanced` | General PvP networks |
| `lenient` | 150ms+ ping, casual hubs |

Profiles merge over jar defaults — v1.1 keys (`license`, `vehicle`, `exemption-caps`) are never dropped.

Competitive shadow → enable workflow: [competitive-tuning.md](competitive-tuning.md)

## 5. Staff setup

- Grant `vez.staff` for `/flags` and `/vez verbose`.
- Grant `vez.admin` for `/vez profile`, `/vez reload`, `/vez tune`.
- Optional bypass: `vez.bypass` (do not grant to players).

## 6. Shadow mode tuning

Before enabling punishments on a live network:

1. Set suspicious checks to `shadow: true` in `plugins/VezAntiCheat/tiers/*.yml`.
2. Run staging scenarios from [testing-plan.md](testing-plan.md) §5.
3. Record results in [staging-results.md](staging-results.md).
4. Run 1–2 week FP soak per [fp-soak-protocol.md](fp-soak-protocol.md).

## 7. Performance benchmarking

Enable sampling for benchmark runs only:

```yaml
diagnostics:
  perf-sampling-enabled: true
```

Run scenarios in [performance-benchmark.md](performance-benchmark.md), then disable in production.

## 8. Licensing (marketplace + direct sales)

### Marketplace (Polymart / BuiltByBit)

Ship the standard jar. Leave license disabled — the platform handles keys:

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

During grace, staff see a warning; checks disable only after grace expires if the key is invalid.

See [DISTRIBUTION.md](DISTRIBUTION.md) for release packaging.

## 9. Updates

```yaml
updates:
  check-enabled: true
  manifest-url: 'https://your-cdn.example/vezac/manifest.json'
```

Manifest JSON shape: `{"latest":"1.1.0","changelog":"https://..."}`
