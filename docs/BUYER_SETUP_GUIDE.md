# Buyer Setup Guide — Perplexion AntiCheat 1.2.0

This guide walks you from purchase to a safely-tuned production install. No
anti-cheat expertise required — just follow the steps in order. Expect about
15 minutes for the install and a week for the careful rollout.

## 1. Prerequisites

Before installing, make sure you have all three:

- **Minecraft 1.8.8 Spigot or Paper.** Perplexion targets 1.8.8 servers only.
  Newer-version *clients* can join through ViaVersion/Geyser (see
  `COMPATIBILITY.md`), but the server itself must be 1.8.8.
- **Java 8 or newer.**
- **PacketEvents 2.12.x — installed FIRST.** PacketEvents is a separate, free
  plugin (download it from its Modrinth or SpigotMC page) and is a hard
  dependency. Perplexion will not start without it. Install PacketEvents,
  restart once, and confirm it loads cleanly before adding Perplexion.

Optional plugins Perplexion detects and works with automatically: Vulcan
(coexistence mode), ProtocolLib, ProtocolSupport, ViaVersion, ViaBackwards,
ViaRewind, and Geyser-Spigot.

## 2. Install

1. Drop `Perplexion-1.2.0.jar` into your `plugins/` folder.
2. **Fully restart the server.** Never use `/reload` or PlugMan to load or
   update Perplexion — packet hooks cannot re-inject into live player
   connections, so a soft reload leaves players invisible to the anti-cheat.
3. On first start, Perplexion creates its data folder at `plugins/Perplexion/`
   with `config.yml` (config-version 18) and the `tiers/` threshold files.

## 3. Verify the install

Run `/perplexion status` (aliases: `/pe`, `/vez`) as console or an operator.
A healthy install shows, among other lines:

- `Packet hooks: true` — PacketEvents injection succeeded. If this is false,
  check that PacketEvents 2.12.x loaded before Perplexion and review the
  startup log.
- The punishment mode line (e.g. `Safety mode: banwave`) — confirms which
  punishment behavior is armed. Fresh installs ship with the balanced
  profile's default, `banwave`.
- Version `1.2.0` and the active profile name.

If the plugin is missing from `/plugins` or status reports hooks as false,
see `INSTALL.md` for troubleshooting before going any further.

## 4. Choose a profile

Perplexion ships three profiles: **lenient**, **balanced** (the shipped
default), and **aggressive**. As a rule of thumb: stay on **balanced** for a
typical PvP/survival/skyblock server; pick **lenient** if your players often
sit above 150ms ping, you host Bedrock players via Geyser, or your host has
TPS dips; reserve **aggressive** for competitive 1.8 PvP servers with low
ping, stable TPS, and active staff. Apply one with
`/perplexion profile <name>` — it automatically backs up your current
`config.yml` and reloads, so it is safe to try. The full comparison table and
reasoning are in `PROFILE_RECOMMENDATIONS.md`.

## 5. Choose a safety mode (week-1 recipe)

`punish.safety-mode` in `config.yml` controls what happens when players flag:

| Mode | What it does |
|---|---|
| `silent` | Records flags only; no staff chat, no punishments |
| `alerts-only` | Staff alerts, no automatic action |
| `mitigation` | Alerts plus setbacks (corrects movement cheats); no bans |
| `banwave` (default) | Evidence-scored, delayed bans on a 30d → 90d → 365d → permanent ladder |
| `instant` | Immediate punishments — never a default; requires staging sign-off |

Recommended first week:

1. **Days 1–2:** run `alerts-only` (or `mitigation`) and watch `/alerts` and
   `/flags`. No one can be wrongly banned while you learn what normal looks
   like on your server.
2. **Days 3–5:** if no known-legit players are flagging, switch to `banwave`.
   Bans are delayed and evidence-gated, so a stray flag will not ban anyone
   on its own.
3. Leave `instant` off unless you have completed the full
   `STAGING_TEST_CHECKLIST.md` and accept the risk.

Two built-in safety nets apply in every mode: punishments defer automatically
during low TPS (the lag gate), and the `vez.bypass` permission always wins.

## 6. Set YOUR appeal URL

Ban screens and announcements include an appeal link. It ships as a
placeholder — change it before any real punishment runs:

```yaml
punish:
  announcements:
    appeal-url: https://your-server.example/appeal   # <- replace this
```

Set it to your Discord invite, website appeal form, or wherever players
should go. Then `/perplexion reload`.

## 7. Staff setup

- Grant trusted staff `vez.staff` — it covers alerts and diagnostics without
  full admin access. `vez.admin` is full control; `vez.bypass` exempts a
  player from all checks (use sparingly — bypassed players are never flagged
  or punished). Legacy `watchdog.*` permissions from earlier releases are
  still honored.
- `/alerts` — toggles live flag alerts in chat for that staff member.
- `/flags` — opens the flag history GUI for reviewing past flags per player.
- `/perplexion verbose` — per-staff firehose of every buffer tick on every
  check; useful when watching one suspicious player, too noisy to leave on.

## 8. The self-tuning loop

After about a day of real traffic:

1. Run `/perplexion recommendations`. It analyzes recent flag data (volume,
   ping distribution, TPS correlation, per-player concentration) and tells
   you in plain language what to adjust.
2. For a single noisy check, prefer a targeted tune over a profile change:
   `/perplexion tune <Check> <key> <value>` (see `PROFILE_RECOMMENDATIONS.md`
   for the false-positive recipe).
3. If a legit player flags, run `/perplexion exportdebug <player>` — it
   writes a YAML report to `plugins/Perplexion/debug/` — and file it using
   `FALSE_POSITIVE_REPORT_TEMPLATE.md`. The export is required for support to
   act on a report.

## 9. Pre-production gate

Before enabling punishments (`banwave` or stricter) in production, run
`STAGING_TEST_CHECKLIST.md` on a staging server. It covers legit movement,
combat, building, network stress, compatibility, detection sanity, and the
punishment pipeline. This is not optional box-ticking: the license terms make
clear the seller is not responsible for false bans when aggressive or instant
settings are enabled without staging. Perplexion reduces cheating and is
designed for low overhead, but no anti-cheat detects every cheat or never
errs — the checklist is how you find your server's edge cases before your
players do.

## Quick-reference card

```
/perplexion status                  Health check: hooks, mode, profile
/perplexion profile <name>          Apply lenient | balanced | aggressive
/alerts                             Toggle staff flag alerts
/flags                              Flag history GUI
/perplexion recommendations         Plain-language tuning advisor
/perplexion tune <Check> <k> <v>    Live-tune one check threshold
/perplexion exportdebug <player>    Write FP debug YAML to plugins/Perplexion/debug/
/perplexion perf                    Performance diagnostics
/banwave                            View/manage the pending ban queue
/perplexion reload                  Reload config (full restart for jar updates)
```
