# Performance Benchmark — Perplexion AntiCheat 1.2.0

> **Status: methodology defined; numbers must be measured on YOUR staging server
> before being quoted anywhere.** This project does not publish synthetic or
> estimated performance claims. Any number quoted in a marketplace listing must
> come from a run recorded in this file, with the environment listed.

## What 1.2.0 changed for performance

These are structural facts about the code, not benchmark claims:

- **Attack-packet entity lookup is now O(1)** (`EntityIndex` map read) instead of
  O(world entities) per attack packet. The index is rebuilt once per tick on the
  main thread — one world-entity iteration per tick total, replacing one per
  attack packet plus one per combat target resolution.
- **`Player#getTargetBlock` raytracing removed** from the autoclicker swing path
  (replaced by dig-packet state already in memory).
- **Ping lookups cache their reflection handles** (previously `Class.forName` +
  `getMethod` per call on hot paths).
- **YAML persistence (punishments/banwave) is debounced** to at most one disk write
  per tick, off the packet threads entirely.
- **Staff alert delivery and punishments marshal to the main thread** — one queued
  task per flag (flags are rare; cost negligible) in exchange for removing
  unsynchronized cross-thread Bukkit access.
- Perf sampling (`diagnostics.perf-sampling-enabled`) remains **off by default** and
  costs nothing when disabled.

## How to measure (staging, ~30 minutes per scenario)

1. Spigot/Paper 1.8.8 server dedicated to the test. Record CPU model, RAM, Java
   version, server build, and PacketEvents version.
2. `diagnostics.perf-sampling-enabled: true`, `balanced` profile,
   `punish.safety-mode: alerts-only`.
3. Load: ideally 10+ real players in PvP; otherwise bot tools that send real
   movement/combat packets. Record the player count.
4. Record every 10 minutes:
   - `/perplexion perf` → movement avg ms, packet avg ms, sample counts, entities indexed
   - `/perplexion status` → TPS
   - CPU % (`top` / host panel); GC pauses from JVM logs if available
5. Run the same server and load **without** Perplexion for a baseline comparison.

Suggested scenarios (scale to your hardware):

| # | Scenario | Players | Duration |
|---|----------|---------|----------|
| A | Idle lobby | 50 | 10 min |
| B | Moving spread | 50 | 10 min |
| C | Active PvP | 50 | 10 min |
| D | Moving spread | 100 | 10 min |
| E | Stress (bots/NPCs) | 200 | 10 min |

Healthy expectations (guidance for interpreting your numbers, not claims):
per-packet processing should stay well under 0.5ms average; TPS under normal PvP
load should be indistinguishable from baseline. If packet averages exceed 0.5ms,
run `/perplexion recommendations` and check for debug options left enabled.

## Recorded runs

| Date | Version | Server (CPU/RAM/Java/build) | Players | Duration | Movement avg ms | Packet avg ms | TPS (min/avg) | Baseline TPS | Notes |
|---|---|---|---|---|---|---|---|---|---|
| _pending operator_ | 1.2.0 | | | | | | | | |

## Marketplace rule

Until at least one row above is filled from a real staging run, the listing may
say: "Designed for low overhead: O(1) packet-path lookups, per-tick entity
indexing, zero-cost-when-disabled diagnostics" — and may NOT quote TPS or
millisecond figures.
