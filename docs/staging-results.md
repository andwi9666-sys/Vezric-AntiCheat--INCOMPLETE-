# Staging Results — v1.1.0 Premium Launch

Record live validation from [`testing-plan.md`](testing-plan.md) §5 on a **1.8.8 Spigot/Paper** test server with **PacketEvents** installed.

**Jar under test:** `target/VezAntiCheat-1.1.0.jar`  
**Profile:** balanced (repeat lenient + aggressive after balanced passes)  
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
| 1 | Speed setback, no re-flag loop | balanced | | | _pending_ | |
| 2 | Fly/hover windowed detection | balanced | | | _pending_ | |
| 3 | Phase stale-anchor rejection | balanced | | | _pending_ | |
| 4 | Blink release / timer debt | balanced | | | _pending_ | |
| 5 | Rotation-only skips timer | balanced | | | _pending_ | |
| 6 | Anti-KB punitive setback ordering | balanced | | | _pending_ | |
| 7 | Silent aim required-rotation median | balanced | | | _pending_ | |
| 8 | Reach rewound AABB | balanced | | | _pending_ | |
| 9 | Legit ping sweep 20–180ms | lenient | | | _pending_ | |
| 10 | Quit buffer purge | balanced | | | _pending_ | |
| 11 | `/vez reload` buffer flush | balanced | | | _pending_ | |
| 12 | Inventory-move silent aim signal | balanced | | | _pending_ | |

## Sign-off

- [ ] 12/12 balanced scenarios **PASS**
- [ ] Lenient legit ping sweep **PASS**
- [ ] Aggressive catches cheat bots without mass FP on 1–2 week soak
- [ ] Zero P0 false positives during soak

**Approved for premium launch:** _yes / no_ — _signature / date_
