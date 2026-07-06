# Support Playbook — Perplexion AntiCheat 1.2.0

## Response targets (best-effort, not guarantees — see TERMS_OF_SERVICE.md §5)

- **P0** (wrongful ban / mass false positives): acknowledge within 24h, hotfix or
  profile guidance within 72h.
- **P1** (bypass report with reproduction steps): triage within 48h.
- **P2** (tuning / docs questions): within 5 business days.

Detection of all cheats is not guaranteed. Support covers configuration, false
positives, and confirmed regressions on the documented setup (Minecraft 1.8.8
Spigot/Paper + PacketEvents 2.12.x).

## False positive triage (staff-side, in order)

1. **Export first** — `/perplexion exportdebug <player>` the moment a suspected
   FP is reported. The YAML in `plugins/Perplexion/debug/` captures ping, client
   brand, Bedrock verdict, VL pools, and the recent flag history with debug
   strings. Reports without it may be closed as incomplete.
2. **Check the advisor** — `/perplexion recommendations` often identifies the
   pattern outright ("check X mostly flags high-ping players").
3. **Verbose** — `/perplexion verbose on`, reproduce, note check name + VL +
   debug suffix.
4. **Contain** — raise that one check's buffer
   (`/perplexion tune <Check> bufferToFlag <n>`) or shadow it
   (`/perplexion tune <Check> shadow true`) while investigating. Only switch
   profiles for systemic patterns, not single checks.
5. **Report** — file using [FALSE_POSITIVE_REPORT_TEMPLATE.md](FALSE_POSITIVE_REPORT_TEMPLATE.md)
   with the export attached.

## Known limitations (honest list)

| Area | Limitation |
|------|------------|
| Bedrock/Geyser | No dedicated Bedrock movement simulation. Bedrock players get exemptions instead: aim checks skipped, scaffold buffers scaled, reach margin added (`compat.bedrock`). Movement checks still assume Java physics — pair Geyser-heavy networks with the `lenient` profile. |
| Vehicle | Envelope check only while mounted; no full vehicle physics simulation |
| Versions | 1.8.8 servers only. Newer clients via ViaVersion are supported on 1.8.8 servers; newer servers are not. |
| VL on reload | CheckVLStore persists across `/perplexion reload` by design |
| Bukkit `/reload` | Unsupported — packet hooks cannot re-inject. Use `/perplexion reload` or a full restart. |
| AutoClick (1.2.0) | Swing classification moved from world raytrace to dig packets. Run the staging shadow-soak before trusting autoclicker punishments on a new install. |

## Escalation

For bypass reports, include the cheat/module name if known, a reproduction
description, and staging evidence per [staging-results.md](staging-results.md).
For wrongful-ban reports, include the exportdebug YAML and your `punish.*`
settings — note that bans issued under `aggressive`/`instant` without a
completed staging checklist are the operator's responsibility
(TERMS_OF_SERVICE.md §7).
