# False-Positive Soak Protocol (1–2 weeks)

Run **after** [`staging-results.md`](staging-results.md) balanced scenarios pass.

## Setup

1. Deploy `VezAntiCheat-1.1.0.jar` with **balanced** profile (`/vez profile balanced`)
2. Enable staff alerts: `/flags`, `/vez verbose on` for triage staff
3. Invite legit PvP players (sprint, W-tap, blockhit, bridging, inventory, eating, bow)

## Track daily

| Metric | How to measure |
|--------|----------------|
| False setbacks | Staff reports + `/vez trace <player>` |
| False combat cancels | Combat staff alerts + `/vez combat` |
| Wrongful bans | Banwave queue review |
| TPS complaints | `/vez status` + server TPS monitor |

## Triage flow

1. Confirm profile (`/vez status`)
2. `/vez info <player>` — exemptions, VL, engine debug
3. `/vez trace <player>` — DiagnosticsTracker entries
4. If FP: switch check to shadow via `/vez tune <Check> shadow true`
5. File issue with: ping, TPS, reproduction steps, debug line

## Exit gate

- **Zero P0 FPs** (wrongful ban or repeated false setback on known-legit play)
- P1 FPs (single rare edge) documented in SUPPORT.md known limitations
- Minimum **7 days** soak with **20+ hours** of active PvP

## Record results

Append soak summary to bottom of [`staging-results.md`](staging-results.md):

```
Soak: YYYY-MM-DD → YYYY-MM-DD, N players, M staff hours, P0=0, P1=<count>
```
