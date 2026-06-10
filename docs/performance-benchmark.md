# Performance Benchmark (Competitive / Aggressive Profile)

Protocol for publishable TPS/CPU numbers at 50 / 100 / 200 players.

## Setup

1. Same hardware for baseline and VezAntiCheat runs.
2. **Aggressive profile** (competitive default):
   ```
   /vez profile aggressive
   ```
3. Enable perf sampling for benchmark runs only:

```yaml
diagnostics:
  perf-sampling-enabled: true
```

4. Run each scenario ≥ 10 minutes; record `/vez status` perf line every minute.

## Scenarios

| # | Scenario | Players | Duration |
|---|----------|---------|----------|
| A | Idle lobby | 50 | 10 min |
| B | Moving spread | 50 | 10 min |
| C | Active PvP | 50 | 10 min |
| D | Moving spread | 100 | 10 min |
| E | Stress (bots/NPCs) | 200 | 10 min |

## Metrics to record

| Metric | Source |
|--------|--------|
| Mean TPS | `/vez status` or Spark |
| 5th percentile TPS | Spark / timings |
| CPU % | `top` / host panel |
| GC pauses | JVM logs if available |
| Movement avg ms | `/vez status` Perf line |
| Packet stage avg ms | `/vez status` Perf line |

## Target (premium claim)

**< 0.5 TPS drop at 100 players** vs no-anticheat baseline on reference hardware.

Adjust target after first real run on your host.

## Results (fill after operator run)

| Scenario | Baseline TPS | VezAC TPS | Δ TPS | Mov avg ms | Pkt avg ms | CPU % |
|----------|--------------|-----------|-------|------------|------------|-------|
| A | _pending_ | _pending_ | | | | |
| B | _pending_ | _pending_ | | | | |
| C | _pending_ | _pending_ | | | | |
| D | _pending_ | _pending_ | | | | |
| E | _pending_ | _pending_ | | | | |

**Hardware:** _operator fill_  
**Date:** _operator fill_  
**Jar:** VezAntiCheat-1.1.0.jar

## Post-benchmark

Set `diagnostics.perf-sampling-enabled: false` in production.
