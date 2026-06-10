#!/usr/bin/env python3
"""Generate checks.yml from config.yml checks section with inline comments."""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "src/main/resources/config.yml"
OUT = ROOT / "src/main/resources/checks.yml"

KEY_COMMENTS = {
    "enabled": "Master switch — false disables this check entirely.",
    "shadow": "Tuning mode — true logs detections without VL, setbacks, or bans.",
    "vl": "Violation level added on each flag (higher = faster punishment ladder).",
    "failVl": "VL weight when this prediction check flags.",
    "bufferToFlag": "Buffer/evidence needed before flagging (raise to reduce false positives).",
    "soloBufferToFlag": "Buffer needed when only one simulation source fired (raise for lenience).",
    "minSourcesToFlag": "Simulation sources required to punish (2 = needs corroboration).",
    "sourceMaxContribution": "Max buffer one source can contribute per tick.",
    "attackFreshnessMs": "Max ms since attack packet — ignore stale attacks.",
    "rewindBaseMs": "Lag-compensation rewind base (ms) added to ping-scaled window.",
    "rewindPingFactor": "Ping multiplier for rewind window (ms per ping point).",
    "maxRewindMs": "Cap on lag-compensation rewind (ms).",
    "minSampleWeight": "Combat sample quality 0–1; below this skips the hit.",
    "minDistance": "Min reach (blocks) — closer hits skip ray/hitbox tests.",
    "maxDistance": "Max reach (blocks) — farther hits ignored by this check.",
    "graceThreshold": "Miss distance (blocks) treated as acceptable noise.",
    "debug": "Verbose console logging for this check.",
    "setbackEnabled": "Teleport player back on flag when true.",
    "setbackCooldownMs": "Min ms between setbacks for this check.",
    "standalone": "true = flags alone; false = feeds aggregate check only.",
    "useSimulationFallback": "Also run simulation engine before flagging.",
    "violationsToFlag": "Consecutive bad windows before flag (statistical checks).",
    "minSamples": "Minimum hits in window before evaluation.",
    "bufferResetMs": "Ms without violations before buffer resets to zero.",
}

CHECK_DESCRIPTIONS = {
    "KillAuraA": "Silent aura / post-hit rotation anomalies.",
    "KillAuraB": "Multi-target switching patterns.",
    "KillAuraC": "Aim snap on attack.",
    "KillAuraD": "Engine-backed aim geometry.",
    "KillAuraE": "Hit ratio / center mass.",
    "KillAuraF": "Aim vs hitbox alignment (feeder).",
    "KillAuraG": "Rotation delta patterns (feeder).",
    "KillAuraH": "Aggregate killaura corroboration.",
    "ReachA": "Primary reach distance check.",
    "ReachB": "Secondary reach pattern.",
    "ReachC": "Statistical reach average.",
    "HitboxA": "Per-hit ray vs hitbox (flick/KB aware).",
    "HitboxB": "Statistical hitbox miss ratio.",
    "BackTrackA": "Stale position / packet lag reach.",
    "LagrangeA": "Combat teleport / lag spoof.",
    "LagrangeB": "Lagrange pattern corroboration.",
    "OffsetPrediction": "Engine total movement offset (primary).",
    "FlyPrediction": "Engine vertical fly simulation.",
    "SpeedPrediction": "Engine horizontal speed simulation.",
    "GroundSpoofPrediction": "Fake on-ground while airborne.",
    "PhasePrediction": "Movement through walls (engine).",
    "TimerPrediction": "Packet timer / game speed.",
    "VelocityA": "Anti-knockback vertical.",
    "VelocityB": "Anti-knockback horizontal.",
    "VelocityC": "Impossible velocity envelope.",
    "VelocityD": "Reverse knockback.",
    "AutoClickA": "Click consistency / CPS.",
    "AutoClickB": "Click outlier ratio.",
    "AutoClickC": "Attack interval variance.",
    "CriticalsA": "Forced crits without jump.",
    "CriticalsB": "Crit pattern statistics.",
    "CriticalsC": "Crit packet timing.",
    "AimAssistA": "Smooth aim / low jitter.",
    "AimAssistB": "Aim acceleration patterns.",
    "AimAssistC": "Post-attack aim correction.",
    "PiercingA": "Hits through solid blocks.",
    "FlyA": "Legacy hover fly (shadow when engine on).",
    "SpeedA": "Legacy horizontal speed cap.",
    "ScaffoldA": "Scaffold placement speed/angle.",
    "TimerA": "Legacy packet timer.",
    "BlinkA": "Packet choke / position desync.",
    "NukerA": "Mass block break.",
}

CATEGORIES = [
    ("COMBAT — KillAura", ["KillAuraA","KillAuraB","KillAuraC","KillAuraD","KillAuraE","KillAuraF","KillAuraG","KillAuraH"]),
    ("COMBAT — Reach / Hitbox / BackTrack", ["ReachA","ReachB","ReachC","HitboxA","HitboxB","BackTrackA","LagrangeA","LagrangeB","PiercingA"]),
    ("COMBAT — Velocity / Click / Aim", ["VelocityA","VelocityB","VelocityC","VelocityD","AutoClickA","AutoClickB","AutoClickC","CriticalsA","CriticalsB","CriticalsC","AimAssistA","AimAssistB","AimAssistC"]),
    ("MOVEMENT — Engine prediction (primary)", ["OffsetPrediction","FlyPrediction","SpeedPrediction","GroundSpoofPrediction","PhasePrediction","TimerPrediction"]),
    ("MOVEMENT — Legacy (shadowed when engine on)", ["FlyA","FlyB","FlyC","FlyD","FlyE","SpeedA","SpeedB","SpeedC","SpeedD","PhaseA","PhaseB","GroundSpoofA","GroundSpoofB"]),
    ("MOVEMENT — Step / NoFall / Jesus / Blink", ["StepA","StepB","StepC","NoFallA","NoFallB","NoFallC","JesusA","JesusB","BlinkA","BlinkB"]),
    ("PLAYER — Scaffold / Inventory / Timer / Misc", ["ScaffoldA","ScaffoldB","ScaffoldC","ScaffoldD","ScaffoldE","ScaffoldF","ScaffoldG","InventoryA","InventoryB","InventoryC","FastBowA","FastBowB","NukerA","NukerB","NukerC","NukerD","TimerA","TimerB","TimerC","BadPacketsA","BadPacketsB","BadPacketsC","BadPacketsD","BadPacketsE","BadPacketsF","BadPacketsG","BadPacketsH","BadPacketsI","BadPacketsJ","BadPacketsK","BadPacketsL","BadPacketsM","BadPacketsN","BadPacketsO","BadPacketsP","BadPacketsQ","BadPacketsR","BadPacketsS","BadPacketsT","BadPacketsU","BadPacketsV","BadPacketsW","BadPacketsX","BadPacketsY","BadPacketsZ","NoRotationA","NoRotationB","FastEatA","NoSlowA","NoSlowB","FastBreakA","FastBreakB"]),
]

HEADER = """# =============================================================================
# VezAntiCheat — Check Tuning (checks.yml)
# =============================================================================
# Edit per-check sensitivity here. Reload with /vez reload.
#
# QUICK GUIDE (every check can use these keys):
#   enabled        — true/false master switch
#   shadow         — true = detect & log only, no punish (use while tuning)
#   vl             — violation level per flag (higher = faster bans)
#   bufferToFlag   — evidence before flag (HIGHER = fewer false positives)
#
# Missing keys inherit from defaults: below.
# Tune live: /vez tune <CheckName> <key> [value]
# =============================================================================

checks-version: 1

defaults:
  enabled: true       # Master switch for checks that omit this key
  shadow: false       # true = tuning mode (no punishments)
  vl: 1.0             # Default VL added per flag
  bufferToFlag: 4     # Default buffer before flagging

"""


def parse_checks(text: str) -> dict:
    checks = {}
    current = None
    for line in text.splitlines():
        m = re.match(r"^  ([A-Za-z][A-Za-z0-9]+):\s*$", line)
        if m:
            current = m.group(1)
            checks[current] = []
            continue
        if current and line.startswith("    "):
            checks[current].append(line[4:])
    return checks


def comment_for_key(key: str) -> str:
    if key in KEY_COMMENTS:
        return f"  # {KEY_COMMENTS[key]}"
    # heuristic comments for common suffixes
    if key.endswith("Ms") or key.endswith("WindowMs"):
        return "  # Time window in milliseconds"
    if key.endswith("Threshold") or key.endswith("Tolerance"):
        return "  # Threshold — raise to reduce false positives"
    if key.endswith("Expansion") or key.endswith("Slack"):
        return "  # Extra lenience (blocks or ms)"
    if key.startswith("min") or key.startswith("max"):
        return "  # Bound/limit for this check"
    if "Ratio" in key or "Factor" in key:
        return "  # Ratio or scale factor"
    return "  # Check-specific tuning value"


def emit_check(name: str, lines: list) -> list:
    out = []
    desc = CHECK_DESCRIPTIONS.get(name, "Anticheat detection check.")
    out.append(f"# --- {name}: {desc} ---")
    out.append(f"{name}:")
    for raw in lines:
        if not raw.strip():
            continue
        # key: value or nested key (strip one indent level from source)
        km = re.match(r"^([A-Za-z0-9_-]+):\s*(.*)$", raw)
        if km:
            key, val = km.group(1), km.group(2)
            if val == "":
                out.append(f"  {key}:")
            else:
                out.append(f"  {key}: {val}{comment_for_key(key)}")
        else:
            out.append(f"  {raw}")
    out.append("")
    return out


def main():
    content = CONFIG.read_text()
    m = re.search(r"^checks:\n(.*)^vl:\n", content, re.M | re.S)
    if not m:
        raise SystemExit("checks section not found")
    parsed = parse_checks(m.group(1))

    lines = [HEADER]
    seen = set()
    for _title, names in CATEGORIES:
        lines.append(f"# {'=' * 77}")
        lines.append(f"# {_title}")
        lines.append(f"# {'=' * 77}\n")
        for name in names:
            if name in parsed:
                lines.extend(emit_check(name, parsed[name]))
                seen.add(name)

    # Remaining checks not in categories
    remaining = [k for k in sorted(parsed.keys()) if k not in seen]
    if remaining:
        lines.append("# OTHER CHECKS")
        for name in remaining:
            lines.extend(emit_check(name, parsed[name]))

    OUT.write_text("\n".join(lines))
    print(f"Wrote {OUT} ({len(lines)} lines)")


if __name__ == "__main__":
    main()
