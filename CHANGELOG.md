# Changelog

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
