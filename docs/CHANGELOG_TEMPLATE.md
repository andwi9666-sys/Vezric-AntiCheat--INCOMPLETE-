# Changelog Entry Template — Perplexion AntiCheat

Every release adds one entry to `CHANGELOG.md`, newest first, in this format.
Honesty rules apply: tuning changes list old → new values with a reason; behavior
changes that need re-testing carry an explicit **staging note**.

---

## Format

```markdown
## [X.Y.Z] — YYYY-MM-DD

### Added
- New features, commands, config keys (with their defaults).

### Changed
- Behavior changes that are not bug fixes. Anything that alters detection
  behavior gets a staging note: "(re-run staging section N before trusting)".

### Fixed
- Bug fixes, with the visible symptom first, cause second.

### Tuning
- Config/threshold changes as `key: old → new — one-line why`.

### Compatibility
- Server/dependency/plugin compatibility changes.

### Upgrade notes
- config-version bumps and what regenerates; renamed keys and their legacy
  fallbacks; anything an operator must do by hand.
```

Versioning: MAJOR = breaking config/behavior overhaul, MINOR = new features or
detection changes, PATCH = fixes and safe tuning only.

---

## Example (illustrative)

```markdown
## [1.2.1] — 2026-07-01

### Added
- `/perplexion checks <tier> <page>` now accepts a page argument per tier.

### Fixed
- Banwave entries queued during a reload could lose their reason text
  (entry serialization raced the config reload).

### Tuning
- `PrismScaffoldA.maxAverageMs: 175 → 185` — verified-legit fast godbridge
  sessions from two FP reports sat at 178–182ms.

### Upgrade notes
- No config-version bump; drop-in replacement.
```
