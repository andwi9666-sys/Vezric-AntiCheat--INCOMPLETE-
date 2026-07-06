# Changelog

## 1.2.0 — Perplexion rebrand & premium release (2026-06-10)

### Rebrand

- Plugin renamed **VezAntiCheat → Perplexion**: plugin.yml name, jar
  (`Perplexion-1.2.0.jar`), data folder (`plugins/Perplexion/`), chat prefix
  (`&0&l[PE&7RPLEX&8ION]`), and alert format (player `&c`, check/VL `&4`).
- Primary command is now `/perplexion` with aliases `/pe` and `/vez`; the
  `/watchdog` and `/wd` aliases are removed. Permissions remain `vez.*`
  (legacy `watchdog.*` still honored).
- Hypixel-imitating defaults removed: `punish.watchdog.*` is now
  `punish.announcements.*` (legacy keys still read as fallback), the default
  appeal URL is a placeholder buyers must set, ban broadcasts rewritten, and the
  hourly announcement no longer fires when zero players were punished.

### Added

- **Punishment safety modes** (`punish.safety-mode`):
  `silent | alerts-only | mitigation | banwave | instant`. Default `banwave`
  reproduces 1.1.x behavior; `instant` is never a shipped default. Missing key
  derives from legacy `punish.enabled`/`execution.type` so old configs keep
  their exact behavior.
- **Punishment lag gate** (`punish.lag-gate`): executions during low TPS are
  re-queued with a retry delay instead of running (or being lost).
- **Bedrock/Geyser + ViaVersion compatibility layer** (`compat.*`): Floodgate
  API → UUID convention → brand detection; Bedrock players are exempt from
  aim-characteristic checks, get scaled scaffold buffers and a reach margin.
  ViaVersion protocol detection for diagnostics. Exempt-only — can never tighten.
- **Ping-scaled exemption windows** (`exempt.ping-scaling`): teleport/velocity/
  blockstate/potion grace grows with measured ping, capped.
- **Self-tuning tools**: `/perplexion recommendations` (rule-based config
  advisor over the last hour of flag analytics), `/perplexion perf`,
  `/perplexion checks [tier] [page]`, `/perplexion exportdebug <player>`
  (YAML report to `plugins/Perplexion/debug/` for FP reports), rolling
  per-check flag statistics (FlagStatsTracker), and tab completion for
  `/perplexion`.
- Buyer/marketplace docs: TERMS_OF_SERVICE, REFUND_POLICY, LICENSE_POLICY
  (per-network), BUYER_SETUP_GUIDE, FALSE_POSITIVE_REPORT_TEMPLATE,
  CHANGELOG_TEMPLATE, PROFILE_RECOMMENDATIONS, STAGING_TEST_CHECKLIST,
  RELEASE_CHECKLIST, PREMIUM_READINESS_AUDIT.

### Fixed (thread safety & stability)

- Punishment execution (kick, command dispatch, broadcast, file save) was
  running on Netty packet threads; now marshaled to the main thread (≤1 tick
  later, decisions unchanged).
- Staff alert delivery and the flags GUI refresh iterated online players on
  Netty threads; delivery now marshals to the main thread (flag records keep
  detection order).
- Every attack packet iterated all world entities on the Netty thread (CME
  risk, O(entities) per hit); replaced by a per-tick **EntityIndex** snapshot
  with O(1) lookups. Also adopted by combat target resolution, Lagrange
  proximity, and the engine's water-proximity uncertainty.
- `Player#getTargetBlock` world raytracing removed from PrismAutoClickA/B/C
  swing classification — replaced with dig-packet mining detection
  (**run the staging shadow-soak before trusting autoclicker punishments**;
  also closes a look-at-ground autoclicker exemption bypass).
- Punishment evidence state was an unsynchronized HashMap mutated from multiple
  Netty threads and never cleaned on quit; now concurrent + cleared on quit.
- `punishments.yml`/`banwave.yml` I/O moved off packet threads behind in-memory
  mirrors with per-tick debounced saves; both flush on disable.
- Global VL counter made atomic; per-check VL store fields made volatile
  (cross-thread visibility for the decay sweep).
- Ping lookups cache their reflection handles (previously `Class.forName` per
  call on hot paths).

### Tuning (false-positive hardening — all changes loosen or add evidence)

- `PrismBadPacketsA`: NaN/Infinite rotation now needs **2 strikes in 30s**
  (`nanStrikesToFlag`/`nanStrikeWindowMs`) — one corrupt packet never flags.
- `PrismBadPacketsB`: `bufferToFlag 6 → 7`, `minNoRotationMs 180 → 220` —
  fast-network packet bursts.
- `PrismScaffoldA`: `maxPitchRange 4.0 → 3.25`, `maxTimingCv 0.08 → 0.07` —
  precise godbridge pitch-holds stay clear of the cheat signature.
- `PrismScaffoldB/D`: `maxBehindDot -0.50/-0.55 → -0.62/-0.68` — breezily/
  strafe-bridge placements no longer read as behind-place.
- `exempt.velocity-ms 450 → 500` — high-ping knockback tails.
- `PrismAutoClick*`: `maxTrackedCps` stays 15.0 with documentation — it is an
  exemption bound; raising it would evaluate legit butterfly clickers.
- Profiles re-tuned with real divergence (ping/TPS gates, ban thresholds, grace
  windows, hitbox expansion, safety modes) — see PROFILE_RECOMMENDATIONS.md.

### Tests

- 460+ unit tests (up from 380+): punishment safety-mode matrix and ladder,
  threading marshal/coalescing, banwave scheduling + persistence, flag
  analytics, recommendation rules, Bedrock detection fallbacks, NaN strike
  window, EntityIndex contract, tab completion, profile key guards
  (`ConfigProfileKeysTest` fails the build if shipped profiles miss 1.2.0 keys),
  plus a shared mock-server harness for threading seams.

### Upgrade notes

- `config-version 17 → 18`: old configs are backed up to `config.yml.old` and
  regenerated. Data folder moves to `plugins/Perplexion/` — copy your old
  `punishments.yml`/`banwave.yml` across if you need history.
- `punish.watchdog.*` keys are read as fallback but new configs use
  `punish.announcements.*`. **Set your own `appeal-url`.**
- Staging required before production punishments: `docs/STAGING_TEST_CHECKLIST.md`,
  including the PrismAutoClick shadow-soak.

---

## 1.1.1 — Deep tuning & flaw remediation (2026-06-09)

### P0 fixes

- **Ghost prism.yml reconciliation:** Registered `PrismBadPacketsG`–`M` and `PrismMultiActionsC`–`G`; removed 14 absorbed combat YAML sections; all combat tuning via `PrismInteractionLegality`.
- **RT3-004:** Packet-order scoring uses position-only age and attack-before-position sequence; combat grace decoupled from `ATTACK_WITHOUT_MOVE` / `ATTACK_WITHOUT_ROTATE`.
- **Registry CI guard:** `TierCheckRegistryTest` fails on unregistered enabled `prism.yml` keys.

### P1 fixes

- **Anti-KB consolidation:** `SimulationKnockback` reads `VelocityProcessor` ratio; partial-KB threshold 0.48 + 3-tick sustain; `CharVelocityPattern` disabled on aggressive; cross-tier `kbDedupeMs` window.
- **PredictionPhase:** Vertical collision axis signal (`minSolidOverlaps: 3`).

### Tuning (aggressive defaults in tier YAML)

- `PrismInteractionLegality`: `maxReach 3.05`, `blatantReachOver 0.30`, `hitboxMinSamples 6`
- `CharSilentAim`: `closeRangeBypass 1.2`, `bufferToFlag 4`, shadow during soak
- `SimulationKnockback`: `minKnockbackRatio 0.48`, `sustainTicks 3`, `bufferToFlag 3`
- `PrismAutoClickA`–`D`: CPS band 8–15

### Tests

- 380+ unit tests including `PrismPacketOrderSupportTest`, `SimulationKnockbackTest`, `PredictionPhaseSignalTest`, registry drift guard.

### Operator gates (unchanged)

- Staging 13/13, FP soak P0=0, benchmark — see [`staging-results.md`](docs/staging-results.md)

---

## 1.1.0 — Premium launch (2026-06-09)

### Security (v1.1 backlog)

- **RT1-002**: `couldSkipTick` lenience no longer stacks fully with block-change in `reduceOffset`; skip-tick uncertainty removed from duplicate uncertainty path.
- **RT1-003/004**: Per-window caps for combat-grace and block-change lenience (`engine.exemption-caps`).
- **RT2-004**: Close-range silent-aim taper — correlation, center-bias, required-rotation, and GCD signals boosted below 1.2 blocks.
- **RT5-002**: `PredictionVehicle` lightweight speed envelope while mounted.
- **RT6-002**: `Check.clearAll()` on `/vez reload` (VL store intentionally persists).

### Product

- `/vez profile lenient|balanced|aggressive` — bundled profile apply + reload.
- Unified branding: `[VezAC]` prefix ( `watchdog` alias retained ).
- Customer docs: INSTALL, SUPPORT, COMPATIBILITY, staging template, FP soak protocol.
- Optional `LicenseManager` and `UpdateChecker` (disabled by default).
- `PerfSampler` + `/vez status` perf line (`diagnostics.perf-sampling-enabled`).

### Tests

- 350+ unit tests including exemption caps, vehicle util, `Check.clearAll`, close-range silent-aim helpers.

---

## 1.0.0-hardened — Hardening baseline

- Engine-authoritative movement with compensation budget cap, offset advantage, chunk-unverified mode.
- Combat: required-rotation util, rewound reach classifier, `CharSilentAim` 8-signal stack, GCD lattice.
- Core: VL decay scheduler, setback ordering, circuit breaker, tier buffer lifecycle.
- Gap closure: simulation buffer purge on quit/reload, inventory-move signal, lenient slab grace.
- Config profiles: lenient / balanced / aggressive YAML sets.
- Tag: `v1.0.0-hardened` @ `ae140d2`.
