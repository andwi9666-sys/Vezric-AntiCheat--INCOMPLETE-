# Marketplace Listing Template — Perplexion AntiCheat 1.2.0

Use this when publishing Perplexion AntiCheat on Polymart, BuiltByBit, or
similar. The honesty rules at the bottom are binding for every listing.

## Title

**Perplexion AntiCheat** — Premium 1.8.8 Anticheat · Prediction Engine · Lag-Compensated Combat · Safe-by-Default Punishments

## Short description

Four-tier detection for 1.8.8 PvP: engine-authoritative movement prediction,
transaction-rewound reach, silent-aim signal stacking. Ships safe: banwave
punishments with evidence scoring, three one-command profiles, Bedrock/Via
compatibility layer, and a built-in tuning advisor.

## Feature bullets

- **100+ checks across four tiers** — Characteristics (combat heuristics +
  silent-aim stack) → Prism (packet/interaction/reach/scaffold) → Simulation
  (movement sub-signals) → Prediction (Grim-style offset engine)
- **Combat rewind** — transaction-anchored entity tracking, rewound AABB reach
- **Safe-by-default punishments** — five safety modes (`silent` → `instant`),
  shipped on evidence-scored banwave with a 30d→90d→365d→perm ladder; punishment
  lag gate defers bans during TPS dips; instant mode is never a default
- **Bedrock + Via aware (1.2.0)** — Geyser/Floodgate players auto-detected and
  exempted from Java-mouse aim heuristics; ViaVersion protocol detection
- **Ping-aware false-positive protection** — grace windows scale with player
  ping; corrupt-packet two-strike rules; per-check buffers everywhere
- **Self-tuning** — `/perplexion recommendations` analyzes your live flag data
  and tells you what to adjust; `/perplexion exportdebug` gives support-ready
  FP reports in one command
- **One-command profiles** — `lenient` / `balanced` / `aggressive` with
  automatic backup + reload
- **Staff tools** — `/flags` history GUI, `/perplexion trace|info|tune|perf|checks`,
  verbose triage mode, full tab completion
- **PacketEvents native** — no ProtocolLib required; Vulcan coexistence supported
- **Designed for low overhead** — O(1) packet-path lookups via per-tick entity
  indexing, zero-cost-when-disabled diagnostics, no disk I/O on packet threads

## Requirements

- Minecraft **1.8.8** Spigot or Paper (this line supports 1.8.8 servers only)
- **PacketEvents 2.12.x** (free, separate plugin — hard dependency)
- Java 8+

## Licensing

**Per-network license** — one purchase covers one server network (lobby, game,
test servers under common ownership), unlimited servers within it. No
redistribution/resale/leaking; chargeback fraud revokes the license. Full terms
in the bundled [TERMS_OF_SERVICE.md](TERMS_OF_SERVICE.md),
[LICENSE_POLICY.md](LICENSE_POLICY.md), and [REFUND_POLICY.md](REFUND_POLICY.md)
— link or paste them in the listing per platform rules.

## Screenshots (capture on a test server, current version)

1. `/perplexion status` — health overview incl. punishment mode line
2. `/flags` — flag history GUI
3. `/perplexion recommendations` — the advisor output with real data
4. `/perplexion checks` — check inventory with tiers
5. Banwave/ban screen with a custom appeal URL set
6. Profile comparison table (from PROFILE_RECOMMENDATIONS.md)

## Honest limitations (include in the listing)

- No anticheat detects every cheat; Perplexion reduces cheating, it does not
  promise elimination.
- Bedrock (Geyser) players get exemptions and margins, not a dedicated Bedrock
  movement simulation — Geyser-heavy networks should use the lenient profile.
- Vehicle detection is a speed envelope, not full vehicle physics.
- Buyers must run the bundled staging checklist before enabling punishments in
  production; the 1.2.0 autoclicker change additionally calls for a shadow-soak
  (both bundled as docs).

## Evidence for buyers

- **460+ automated unit tests** run on every build (`mvn clean package`)
- Engineering audit shipped in the jar docs: [PREMIUM_READINESS_AUDIT.md](PREMIUM_READINESS_AUDIT.md)
- Staging protocol + sign-off template: [STAGING_TEST_CHECKLIST.md](STAGING_TEST_CHECKLIST.md), [staging-results.md](staging-results.md)

## Performance claims rule (binding)

Until [performance-benchmark.md](performance-benchmark.md) contains at least one
recorded run, the listing may use only structural claims ("O(1) packet-path
lookups", "no disk I/O on packet threads", "diagnostics off by default") and may
**not** quote TPS, millisecond, or player-count figures.

## Pricing channels

| Channel | License in jar |
|---------|----------------|
| Marketplace | `license.enabled: false` (platform validates buyers) |
| Direct website | `license.enabled: true` + key delivery |

Same jar supports both; configuration per [INSTALL.md](INSTALL.md) §10.

## Support

Link your Discord/ticket system. FP reports require the
`/perplexion exportdebug` YAML per [FALSE_POSITIVE_REPORT_TEMPLATE.md](FALSE_POSITIVE_REPORT_TEMPLATE.md)
— say so in the listing to set expectations.
