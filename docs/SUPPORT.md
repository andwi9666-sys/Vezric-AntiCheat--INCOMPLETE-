# Support Playbook

## Response SLA (premium tier)

- **P0** (wrongful ban / mass FP): acknowledge within 24h, hotfix or profile guidance within 72h.
- **P1** (bypass report with repro): triage within 48h.
- **P2** (tuning / docs): within 5 business days.

Detection of all cheats is not guaranteed. Support covers configuration, false positives, and confirmed regressions.

## False positive triage

1. **Profile** — try `lenient` on high-ping networks: `/vez profile lenient`
2. **Shadow** — enable shadow on the failing check in `tiers/*.yml`, reproduce, collect flags.
3. **Verbose** — staff runs `/vez verbose on`, reproduce, note check name + VL + debug suffix.
4. **Trace** — `/vez trace <player>` exports diagnostics snapshot.
5. **Export** — attach `plugins/VezAntiCheat/config.yml`, profile name, player ping, TPS, and timestamped flag lines.

## FP report template

```
Server version: Paper/Spigot 1.8.8 build ___
VezAntiCheat version: 1.1.0
Profile: balanced / lenient / aggressive
PacketEvents version: ___
Mean TPS during incident: ___
Player ping: ___ ms
Check name: e.g. PredictionSpeed
What the player was doing: sprint-jump PvP / bridging / etc.
Flag message (full line): ___
/vez trace output: (paste)
Expected behavior: no flag / no setback
```

## Known limitations

| Area | Limitation |
|------|------------|
| Vehicle | Envelope check only while mounted; no full vehicle physics sim |
| RT3-004 packet-order | Monitor in staging; patch if attack-before-position reproduces |
| VL on reload | CheckVLStore persists across `/vez reload` by design |
| Geyser/Bedrock | Higher ping tolerance recommended; use lenient profile |
| `/reload` | Use `/vez reload` instead — Bukkit reload can desync PacketEvents |

## Escalation

Include staging evidence from [staging-results.md](staging-results.md) when reporting bypasses tied to v1.1 backlog items.
