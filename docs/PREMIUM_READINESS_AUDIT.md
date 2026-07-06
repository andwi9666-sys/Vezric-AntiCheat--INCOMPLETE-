# Premium Readiness Audit — Perplexion AntiCheat 1.2.0

> Internal engineering audit performed before the 1.2.0 marketplace release.
> Every finding below was verified against source (file:line cited). This document is
> honest by design: it records what was broken, what was already fine, and what risk
> remains. It is **not** marketing material.

Audit scope: crash risks, PacketEvents lifecycle, null/threading/memory safety,
reload + quit cleanup, teleport/respawn/world-change edge cases, lag/ping false-positive
risk, per-category detection FP risk, Geyser/Via compatibility, config tuning,
test coverage, docs, and marketplace readiness.

Severity scale: **Fatal** (can crash, corrupt state, or punish wrongly under normal
load) · **High** (leak, race, or systemic FP source) · **Medium** (FP hotspot or
quality gap) · **Cosmetic** (polish/professionalism).

---

## 1. Fatal findings (fixed in 1.2.0)

### 1.1 Punishments executed on the Netty packet thread — Fatal
Call chain: `TierCheck.fail()` → `TierPunishmentExecutor.evaluateBan()` →
`PunishmentManager.executeQueuedPunishment()` (`punishment/PunishmentManager.java`
~250–270). The body calls `Player#kickPlayer`, `Bukkit#dispatchCommand`,
`Bukkit#broadcastMessage`, and `YamlConfiguration#save` — all Bukkit-main-thread API —
directly on whatever thread fired the flag, which is the per-connection Netty thread
for packet-driven checks. The same path is reachable from the legacy
`checks/Check.java:426` hybrid punisher.
**Risk:** server-internal state corruption, rare hard crashes, kicks racing the main tick.
**Fix (Pass 2a):** decision logic stays on the calling thread; execution is marshaled
through `utils/MainThread.run()` (inline when already on the primary thread, otherwise
`runTask`). Bans land ≤1 tick later than before; detection unchanged.

### 1.2 Staff alert broadcast + GUI refresh on the Netty thread — Fatal
`tier/TierCheckManager.java` `flagToStaff()` (~127–170) iterates
`Bukkit.getOnlinePlayers()`, sends chat, and calls `FlagsGui.refreshOpen()` (which reads
open inventory state) on the calling thread. Same pattern in `verboseToStaff` and
`verboseFlagToStaff`.
**Risk:** `ConcurrentModificationException` against the online-player collection;
inventory state races.
**Fix (Pass 2b):** flag record/snapshot building (pure data + `CopyOnWriteArrayList`
history append) stays synchronous to preserve detection ordering; only chat delivery
and GUI refresh are marshaled to the main thread.

### 1.3 World entity iteration per attack packet on the Netty thread — Fatal
`listeners/PacketListener.java:322–330`: every `INTERACT_ENTITY` packet loops
`p.getWorld().getEntities()` to resolve the target entity by id.
**Risk:** CME when the main thread mutates the entity list mid-iteration; also O(world
entities) per hit packet — a real cost on busy PvP servers.
**Fix (Pass 2c):** new `utils/EntityIndex` — a `ConcurrentHashMap<Integer, Entity>`
rebuilt once per tick on the main thread. Packet-thread resolution becomes an O(1)
map read. Trade-off: an entity spawned within the last tick can miss (lookup returns
null → check skips), which fails in the lenient, FP-safe direction.

---

## 2. High findings (fixed in 1.2.0)

### 2.1 `Player#getTargetBlock` from the Netty thread — High
`tier/prism/PrismAutoClickA.getTargetBlockCompat()` (~158) performed world block
iteration off-main to classify swings as mining vs combat. Inherited by
PrismAutoClickB/C/D via `isCombatSwing`.
**Fix (Pass 2d):** classification now uses packet-derived dig state already tracked in
`PlayerData` (recent dig-start within a short window = mining swing). **This is the one
fix with real behavioral risk** — it ships with a mandatory shadow-soak item on the
staging checklist before punishments are trusted on autoclicker checks.

### 2.2 Unsynchronized, never-evicted punishment state — High
`punishment/PunishmentManager.java:33–34`: `evidenceStates` and
`blatantBedNukerStates` were plain `HashMap`s mutated from multiple per-player Netty
threads, and entries were never removed on quit.
**Risk:** map corruption under concurrent writes (HashMap resize race), plus a slow
per-unique-player memory leak on long uptimes.
**Fix (Pass 2a):** both converted to `ConcurrentHashMap`; small mutating methods on the
state objects synchronized; new `clearTransient(UUID)` wired into the quit cleanup in
`listeners/PlayerListener.java`. Persistent marks/ban counts intentionally survive quit.

### 2.3 `punishments.yml` I/O off the main thread — High
Reads (`getBanCount`) and writes (`setMarkTime` → `save`) hit a shared
`YamlConfiguration` from packet threads.
**Fix (Pass 2a):** in-memory `ConcurrentHashMap` mirrors (ban counts, mark times)
loaded once in `load()`; packet threads read/write the maps; disk persistence is
debounced onto the main thread.

### 2.4 Cross-thread torn reads on `PlayerData` hot fields — High (partially accepted)
`data/PlayerData.java` (~2600 lines) has fields written by the Netty thread and read by
main-thread tasks (notably the VL decay task started at `VezAntiCheat.java:413`).
**Fix (Pass 2f):** only the hot cross-thread fields (total VL, flag timestamps consumed
by the decay task) were made `volatile`. **Accepted residual risk:** other fields can
still tear (e.g., a stale position read). Verified consequences are stale-data reads,
not crashes — collections are bounded deques written and trimmed on a single thread,
and the `PlayerData` lookup map is a `ConcurrentHashMap`
(`data/PlayerDataManager.java:14`). A broad synchronization rewrite was deliberately
rejected (production-safety mandate: no sweeping rewrites).

---

## 3. Findings that turned out to be FINE (verified — do not "fix")

These were flagged by a first-pass review and **disproven by reading the code**. They
are recorded so future maintenance doesn't regress them out of misunderstanding.

| Suspected issue | Reality |
|---|---|
| `PrismAutoClickA.maxTrackedCps: 15.0` "too low for butterfly clicking" | It is an **exemption upper bound**, not a detection threshold: `PrismAutoClickA.java:101–107` *skips and decays* when mean CPS exceeds it. Raising it would start evaluating legit 16–20 CPS butterfly clickers and **increase** false positives. Kept at 15.0 with an explanatory comment in `tiers/prism.yml`. |
| `PolarFlagHistory` unbounded leak | Bounded ring of 54 (`PolarFlagHistory.java:11`). Too small for analytics, which is why 1.2.0 adds `FlagStatsTracker` rather than growing the ring. |
| `scaffoldIntervals` unbounded | Capped at 20 by both writers (`ScaffoldUtil.java:297–299`, `PlayerListener.java:379–380`). |
| `noFallAPeaks` leak | Bounded by `maxPeaks` (`PlayerData.java:~2277`); `remove(0)` is O(n) on a tiny list — micro-cleanup only. |
| "Checks don't gate on lag" | Every tier flag passes through `TierCheck.fail()` which suppresses flags when TPS < `lag.min-tps` (18.5) or ping > `lag.max-ping` (250) (`TierCheck.java:166–171`). The actual gap was detection-*time* ping compensation, addressed by `exempt.ping-scaling` (Pass 3). |
| "BadPacketsA flags on a single weird pitch" | The out-of-range-pitch path is buffered (`bufferToFlag: 5`). Only the NaN/Infinite path insta-flagged — now a 2-strike rule (Pass 3). |
| PacketEvents lifecycle problems | None. PacketEvents is a `provided` plugin dependency; the plugin never calls `init()`/`load()` itself, verifies the API is ready before registering (`VezAntiCheat.java:135–138, 356`), defers hook registration for Vulcan coexistence, and unregisters listeners on disable. No double-init or injection risk. |
| Reload double-registration | `/vez reload` (now `/perplexion reload`) reloads config/tier YAML and clears buffers; it does **not** re-register packet or Bukkit listeners. Safe. |
| Scheduled-task leaks on disable | All repeating tasks (VL decay, TPS monitor, punishment announcements, banwave auto, transaction tick) are cancelled in `onDisable`/their owners. No raw `Thread`/`ExecutorService` anywhere. |
| Player quit cleanup gaps | `PlayerListener.onQuit` removes `PlayerData`, clears all per-check state, combat analyzer and risk score entries. 1.2.0 adds the missing punishment transient cleanup (2.2). |
| Jar hygiene | `target/Perplexion-*.jar` contains only classes + plugin.yml/config.yml/tiers/profiles. No `.DS_Store`, no test classes, no secrets, no hardcoded tokens/webhooks. |
| Java 8 conformance | No Java 9+ APIs or syntax anywhere in `src/main`. |

---

## 4. False-positive risk inventory (Medium — addressed in Pass 3/4)

| Area | Finding | 1.2.0 action |
|---|---|---|
| BadPackets | NaN/Infinite rotation insta-flag (`PrismBadPacketsA.java:18–21`) — packet corruption can produce one bad packet | 2-strike rule within 30s; first strike is verbose-only |
| BadPackets | Tiny-interval burst thresholds tight for fast networks (`PrismBadPacketsB`) | bufferToFlag 6→7, minNoRotationMs 180→220 |
| Scaffold | Pitch-hold window catches precise godbridge; behind-place dot catches breezily strafes | maxPitchRange 4.0→3.25, behind-dot −0.56→−0.68 (both make the *cheat* signature stricter, i.e. fewer legit catches) |
| Velocity/KB | 450ms velocity exemption clips high-ping knockback tails | 500ms + new `exempt.ping-scaling` (grace windows grow with measured ping, capped) |
| Reach/combat | Reach margins fixed regardless of client type | Bedrock `reach-extra` margin via compat layer |
| Aim | GCD/rotation heuristics assume Java mouse input; Bedrock touch/controller input differs fundamentally | Bedrock players exempt from aim-characteristic checks by default |
| Geyser/Via | **No client-platform or client-version awareness at all** despite softdepends | New `ClientCompatUtil` + `BedrockDetector` (Floodgate API → UUID convention → brand heuristic; ViaVersion protocol lookup), config-gated, exempt-only |
| Lag profile | `boost-factor` can *reduce* VL on high-jitter players (under-detection, not FP) | Documented; left as-is in 1.2.0 (lenient direction) |

Config values judged **too aggressive** for a default: none after Pass 3 (the table
above lists everything that was). Values judged **too lenient**: lag-profile boost cap
(accepted, lenient direction is the safe default for a paid product); aggressive
profile tightens it for competitive servers.

---

## 5. Marketplace/professionalism findings (Cosmetic — fixed in Pass 1/9/10)

| Finding | Detail |
|---|---|
| Hypixel-imitating branding | Default prefix `[WatchDog]`, Hypixel-style ban broadcasts, and — worst — `punish.watchdog.appeal-url` defaulting to `https://www.hypixel.net/appeal` (`config.yml:869`). A paid product cannot ship pointing banned players at Hypixel. Fully rebranded to **Perplexion**; appeal URL now a placeholder the buyer must set. |
| Typos in ban broadcasts | "enviornment", malformed "banned over N within" sentence. Rewritten. |
| Docs version drift | INSTALL/SUPPORT/COMPATIBILITY/MARKETPLACE/DISTRIBUTION/TIER_TUNING referenced v1.1.0 while the jar was 1.1.1. All docs now versioned with the release. |
| Blank evidence docs | `staging-results.md` and `performance-benchmark.md` were empty templates. Benchmark doc now carries methodology + explicit "must be measured on staging" gates; staging results remain operator-filled by design. |
| Missing legal/buyer docs | No ToS, refund policy, license policy, buyer setup guide, FP report template, changelog template, profile recommendations, staging checklist, release checklist. All created in 1.2.0. |
| plugin.yml polish | Empty `usage:` strings; no tab completion on any command. Both fixed. |
| Punishment modes implicit | Safe behavior existed (banwave default, ladder, evidence scores) but was not surfaced as a simple buyer-facing choice. New `punish.safety-mode` key (silent/alerts-only/mitigation/banwave/instant; instant never a default). |

---

## 6. Test coverage gaps (addressed in Pass 7)

Existing: 80 test classes (JUnit 4 + Mockito 2, server-free) with strong combat,
movement-physics, and util coverage. Gaps closed in 1.2.0: punishment threading,
safety-mode matrix, punishment ladder progression, lag-gated punishment deferral,
evidence-state concurrency, banwave scheduling, EntityIndex, Bedrock/Via detection
fallbacks, flag-stats bucketing, recommendation rules, tab completion, debug export,
NaN strike window, profile key-completeness.

Remaining untested by unit tests (inherently in-game; covered by
`docs/STAGING_TEST_CHECKLIST.md`): real packet-order under latency, Geyser client
behavior, banwave under production load, PrismAutoClickA reclassification soak.

---

## 7. Residual risks accepted for 1.2.0

1. **PlayerData torn reads outside hot fields** — stale reads possible, crash-free by
   construction; full fix would require a rewrite disproportionate to the risk.
2. **EntityIndex 1-tick staleness** — a just-spawned target can be missed for one tick
   (check skips; lenient direction).
3. **Alert/punishment delivery deferred ≤1 tick** — cosmetic latency only.
4. **PrismAutoClickA swing reclassification** — behavior-adjacent; gated by mandatory
   shadow soak before enabling punishments on autoclicker checks.
5. **No protocol-level Bedrock movement model** — Bedrock players get exemptions and
   margins, not a dedicated simulation; documented limitation in COMPATIBILITY.md.

## 8. Verdict

Pre-1.2.0: **not marketplace-ready** (netty-thread punishment execution alone is
disqualifying for a paid product; branding pointed at Hypixel).
Post-1.2.0 (after the passes in this audit): code-ready, **staging-required** — the
release checklist blocks listing until the staging checklist (including the
autoclicker shadow soak and a measured performance benchmark) is signed off.
