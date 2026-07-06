# False-Positive Soak Protocol (7–14 days, competitive)

Run **after** [`staging-results.md`](staging-results.md) aggressive scenarios 1–8 pass.

## Setup

1. Deploy `Perplexion-1.2.0.jar` with **aggressive** profile:
   ```
   /perplexion profile aggressive
   ```
2. Follow shadow → enable workflow in [`competitive-tuning.md`](competitive-tuning.md)
3. Enable staff alerts: `/flags`, `/perplexion verbose on` for triage staff
4. Invite **competitive Pot/HCF PvP** players — sprint-jump, W-tap, blockhit, bridging, inventory, bow, jump-crit chains

## Track daily

| Metric | How to measure |
|--------|----------------|
| False setbacks | Staff reports + `/perplexion trace <player>` |
| False combat cancels | Combat staff alerts + `/perplexion combat` |
| Wrongful bans | Banwave queue review |
| TPS complaints | `/perplexion status` + server TPS monitor |

## Triage flow

1. Confirm profile (`/perplexion status`)
2. `/perplexion info <player>` — exemptions, VL, engine debug
3. `/perplexion trace <player>` — DiagnosticsTracker entries
4. If FP: switch check to shadow via `/perplexion tune <Check> shadow true`
5. File issue with: ping, TPS, reproduction steps, debug line

## Competitive PvP focus (Pot / HCF / UHC)

Track these high-skill mechanics explicitly:

| Mechanic | Expected |
|----------|----------|
| W-tap / S-tap first hits | No combat cancel |
| Blockhit sprint reset | No reach/silent-aim flag |
| Jump-crit chains | No fly/hover false flag |
| Counterstrafe spacing | No movement mismatch FP |
| 8–14 CPS jitter click | No autoclick flag unless macro-smooth |
| Boat/minecart travel | No vehicle speed FP at vanilla speeds |

## Exit gate

- **Zero P0 FPs** (wrongful ban or repeated false setback on known-legit play)
- P1 FPs (single rare edge) documented in SUPPORT.md known limitations
- Minimum **7 days** soak with **20+ hours** of active PvP

## Record results

Append soak summary to bottom of [`staging-results.md`](staging-results.md):

```
Soak: YYYY-MM-DD → YYYY-MM-DD, N players, M staff hours, P0=0, P1=<count>
```
