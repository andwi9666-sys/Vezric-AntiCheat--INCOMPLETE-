# False Positive Report — Perplexion AntiCheat

Copy everything below the line into your support message and fill in each
field. Reports without the **exportdebug attachment** cannot be triaged —
the export contains the per-check buffer history we need to reproduce the
flag.

Before filing: confirm the player does not have a known cheat client, and
consider a quick interim fix while you wait
(`/perplexion tune <Check> bufferToFlag <higher>` or
`/perplexion tune <Check> shadow true` — see `PROFILE_RECOMMENDATIONS.md`).

---

## Reporter and server

| Field | Value |
|---|---|
| Your name / handle | |
| Server name (optional) | |
| Server build (exact, e.g. Paper 1.8.8 git-xxx) | |
| Perplexion version | 1.2.0 |
| PacketEvents version | |
| ViaVersion / ViaBackwards / ViaRewind (versions, or "none") | |
| Geyser / Floodgate (versions, or "none") | |
| Other anti-cheat present (e.g. Vulcan, or "none") | |

## Configuration in use

| Field | Value |
|---|---|
| Profile (lenient / balanced / aggressive) | |
| `punish.safety-mode` | |
| Tune overrides (any `/perplexion tune` changes or hand-edited `tiers/*.yml` values; "none" if stock) | |

## Flagged player context

| Field | Value |
|---|---|
| Player name | |
| Ping at the time (approx.) | |
| Client brand / version (from `/perplexion info <player>` if available) | |
| Bedrock player via Geyser? (yes / no) | |
| Server TPS at the time (if known) | |

## What happened

| Field | Value |
|---|---|
| Check name (exact, from the alert) | |
| Full alert line (paste verbatim) | |
| What the player was legitimately doing when flagged (be specific: e.g. "godbridging across a void gap", "butterfly clicking ~16 CPS in a 1v1", "knocked back by TNT then teleported") | |
| Does it reproduce? (always / sometimes / once) | |
| First noticed in 1.2.0, or also in earlier versions? | |

## Attachments

- **REQUIRED:** the YAML from `/perplexion exportdebug <player>`, found in
  `plugins/Perplexion/debug/`. Run the export as soon as possible after the
  flag, while the player is still online if you can.
- **Optional but very helpful:** a short video of the legit action that
  triggers the flag, and any relevant console log excerpt.

---

## What happens to your report

We reproduce the scenario on a 1.8.8 staging server using your export's
buffer history and context (ping, client, Bedrock status). If it is a
genuine false positive, the usual outcome is a threshold adjustment or a new
exemption (ping-scaled grace, Bedrock handling, or a context window), which
ships in the next patch and is noted in the changelog's Tuning section. If
we cannot reproduce it, we will come back to you with specific follow-up
questions — keeping the export and video makes that round-trip fast.
Support is best-effort via the seller's listed channel (see `SUPPORT.md`).
