# Marketplace Listing Template

Use this when publishing VezAntiCheat 1.1.0 on Polymart, BuiltByBit, or similar.

## Title

**VezAntiCheat 1.1.0** — Polar-tier 1.8.8 Anticheat (Engine Prediction + Rewound Reach)

## Short description

Four-tier Polar architecture for competitive 1.8.8 PvP. Engine-authoritative movement, lag-compensated reach, silent-aim signal stacking, configurable profiles (lenient / balanced / aggressive), and banwave punishment.

## Feature bullets

- **4-tier Polar pipeline** — Characteristics → Prism → Simulation → Prediction (93 checks)
- **Grim-style movement engine** — offset prediction, compensation budget cap, chunk-unverified mode
- **Combat rewind** — transaction-anchored entity tracking, rewound AABB reach classifier
- **Silent aim stack** — 8+ signals including required-rotation, GCD lattice, close-range taper
- **Config profiles** — `/vez profile aggressive|balanced|lenient` one-command deploy
- **Staff tools** — `/flags` GUI, `/vez trace`, `/vez tune`, verbose mode
- **PacketEvents native** — no ProtocolLib required; Vulcan coexistence supported

## Requirements

- Minecraft **1.8.8** Spigot or Paper
- **PacketEvents** (hard dependency)
- Java 8+

## Screenshots (capture on test server)

1. `/flags` — Polar flag history GUI
2. `/vez status` — TPS, perf metrics, check count
3. Setback in action (staff verbose line)
4. Profile comparison table (aggressive vs balanced key diffs)
5. Banwave announcement in chat

## Honest limitations

- Vehicle detection uses speed envelope, not full vehicle physics simulation
- Packet-order bypass (RT3-004) monitored; patch if reproduced on staging
- VL persists across `/vez reload` by design (banwave continuity)
- Bedrock/Geyser clients: use lenient profile
- Requires live staging + FP soak before production (see bundled docs)

## Comparison vs heuristic-only anticheats

| | VezAntiCheat | Heuristic-only |
|---|--------------|----------------|
| Movement | Full physics prediction + offset advantage | Speed/fly thresholds |
| Reach | Rewound AABB + transaction tracking | Static distance check |
| Silent aim | Multi-signal aggregator + required rotation | Single angle check |
| Tuning | Per-check tier YAML + live `/vez tune` | Monolithic config |
| Profiles | One-command aggressive/balanced/lenient | Manual YAML editing |

## Support

Link to your Discord/ticket system. Include [docs/SUPPORT.md](SUPPORT.md) FP report template in the listing description or pinned post.

## Evidence for buyers

- **350** automated unit tests (`mvn clean package`)
- Staging checklist: [docs/staging-results.md](staging-results.md) (fill before listing goes live)
- Performance protocol: [docs/performance-benchmark.md](performance-benchmark.md)

## Pricing channels

| Channel | License in jar |
|---------|----------------|
| Marketplace | `license.enabled: false` |
| Direct website | `license.enabled: true` + key delivery |

Same jar supports both; buyers configure per [INSTALL.md](INSTALL.md) §8.
