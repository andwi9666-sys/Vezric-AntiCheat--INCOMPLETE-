# Staging Results — v1.1.1 Deep Tuning (Competitive)

Record live validation from [`testing-plan.md`](testing-plan.md) §5 on a **1.8.8 Spigot/Paper** test server with **PacketEvents** installed.

**Jar under test:** `target/Perplexion-1.2.0.jar`  
**Primary profile:** **aggressive** (`/perplexion profile aggressive`) — repeat lenient ping sweep + balanced spot-check  
**Tuning guide:** [`competitive-tuning.md`](competitive-tuning.md)  
**Registry audit:** [`check-registry-audit.md`](check-registry-audit.md)  
**Tester:** _operator name_  
**Date range:** _YYYY-MM-DD → YYYY-MM-DD_

## Environment

| Field | Value |
|-------|--------|
| Server | Spigot/Paper 1.8.8 build |
| PacketEvents version | |
| VezAntiCheat commit / tag | |
| Hardware | |
| Online players during test | |
| Mean TPS | |
| Test client ping(s) | 20 / 50 / 120 / 180 ms |

## Scenario results

| # | Scenario | Profile | Ping | TPS | Result | Check / debug notes |
|---|----------|---------|------|-----|--------|---------------------|
| 1 | Speed setback, no re-flag loop | aggressive | | | _pending operator_ | Sprint-jump after setback |
| 2 | Fly/hover windowed detection | aggressive | | | _pending operator_ | Slab/stair fights |
| 3 | Phase stale-anchor rejection | aggressive | | | _pending operator_ | |
| 4 | Blink release / timer debt | aggressive | | | _pending operator_ | |
| 5 | Rotation-only skips timer | aggressive | | | _pending operator_ | |
| 6 | Anti-KB punitive setback ordering | aggressive | | | _pending operator_ | `combat-mitigation.punitive-setback` |
| 7 | Silent aim required-rotation median | aggressive | | | _pending operator_ | &lt; 1.2 block trades |
| 8 | Reach rewound AABB | aggressive | | | _pending operator_ | 2.8–3.2 block trades |
| 9 | Legit ping sweep 20–180ms | lenient | | | _pending operator_ | W-tap/blockhit/jump-crit |
| 10 | Quit buffer purge | aggressive | | | _pending operator_ | |
| 11 | `/perplexion reload` buffer flush | aggressive | | | _pending operator_ | |
| 12 | Inventory-move silent aim signal | aggressive | | | _pending operator_ | |
| 13 | InteractionLegality tune observable | aggressive | | | _pending operator_ | `/perplexion tune PrismInteractionLegality maxReach` changes cancel behavior |

## Sign-off

- [ ] 13/13 **aggressive** scenarios **PASS**
- [ ] Lenient legit ping sweep **PASS** (scenario 9)
- [ ] Aggressive catches cheat bots without mass FP on 7–14 day soak
- [ ] Zero P0 false positives during soak
- [ ] [`performance-benchmark.md`](performance-benchmark.md) filled with real numbers

**Approved for premium launch:** _yes / no_ — _signature / date_
