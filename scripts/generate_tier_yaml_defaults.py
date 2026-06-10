#!/usr/bin/env python3
"""Generate tier YAML defaults from legacy checks.yml prediction sections."""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECKS = ROOT / "src/main/resources/checks.yml"

LEGACY_TO_TIER = {
    "OffsetPrediction": "PredictionOffset",
    "FlyPrediction": "PredictionFly",
    "SpeedPrediction": "PredictionSpeed",
    "GroundSpoofPrediction": "PredictionGroundSpoof",
    "StepPrediction": "PredictionStep",
    "JesusPrediction": "PredictionJesus",
    "BlinkPrediction": "PredictionBlink",
    "PhasePrediction": "PredictionPhase",
    "TimerPrediction": "PredictionTimer",
    "NoSlowPrediction": "PredictionNoSlow",
}

SIM_DEFAULTS = {
    "SimulationOffsetHorizontal": {"threshold": 0.08, "failVl": 0.75},
    "SimulationOffsetVertical": {"threshold": 0.10, "failVl": 0.75},
    "SimulationKnockback": {"threshold": 0.08, "minKnockbackRatio": 0.55, "failVl": 0.75},
    "SimulationExplosion": {"threshold": 0.08, "minExplosionRatio": 0.50, "failVl": 0.75},
    "SimulationNoSlow": {"threshold": 0.04, "failVl": 0.75},
    "SimulationSprint": {"threshold": 0.06, "failVl": 0.75},
    "SimulationSneak": {"threshold": 0.04, "failVl": 0.75},
    "SimulationFriction": {"threshold": 0.06, "failVl": 0.75},
    "SimulationJump": {"threshold": 0.12, "failVl": 0.75},
    "SimulationLiquid": {"threshold": 0.08, "failVl": 0.75},
    "SimulationClimbable": {"threshold": 0.08, "failVl": 0.75},
    "SimulationWeb": {"threshold": 0.06, "failVl": 0.75},
    "SimulationEntityPush": {"threshold": 0.05, "failVl": 0.75},
    "SimulationIce": {"threshold": 0.06, "failVl": 0.75},
    "SimulationSlime": {"threshold": 0.08, "failVl": 0.75},
    "SimulationBlinkDebt": {"timerDebtMs": 120.0, "gapMs": 100, "offsetThreshold": 0.12, "failVl": 0.75},
    "SimulationSetbackRecovery": {"threshold": 0.10, "recoveryWindowMs": 2000, "maxTargetDistance": 1.5, "failVl": 0.75},
    "SimulationCombo": {"publicFlag": True, "minFamilies": 3, "offsetThreshold": 0.06, "failVl": 0.75},
}

PRED_EXTRA = {
    "PredictionVelocity": {"bufferToFlag": 4, "failVl": 1.2},
    "PredictionWeb": {"engineOffsetThreshold": 0.08, "bufferToFlag": 5, "failVl": 1.0},
    "PredictionWater": {"engineHorizontalOffset": 0.10, "bufferToFlag": 5, "failVl": 1.0},
    "PredictionExplosion": {"engineOffsetThreshold": 0.10, "minExplosionRatio": 0.50, "bufferToFlag": 4, "failVl": 1.0},
}


def parse_section(text, name):
    pattern = rf"^{re.escape(name)}:\n((?:  .+\n)*)"
    m = re.search(pattern, text, re.MULTILINE)
    if not m:
        return {}
    out = {}
    for line in m.group(1).splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if ":" not in line:
            continue
        k, v = line.split(":", 1)
        k, v = k.strip(), v.strip()
        if v.lower() in ("true", "false"):
            out[k] = v.lower() == "true"
        else:
            try:
                if "." in v or "e" in v.lower():
                    out[k] = float(v)
                else:
                    out[k] = int(v)
            except ValueError:
                out[k] = v
    return out


def emit_prediction_yml(text):
    lines = [
        "tiers-version: 1",
        "",
        "defaults:",
        "  tier: PREDICTION",
        "  enabled: true",
        "  shadow: false",
        "  punishVl: 150",
        "  failWeight: 0.35",
        "  bufferToFlag: 7",
        "  decay: 0.15",
        "  setbackEnabled: true",
        "  respectPointThree: true",
        "",
    ]
    skip = {"enabled", "shadow", "vl", "soloBufferToFlag", "minSourcesToFlag", "sourceMaxContribution", "setback-offset"}
    for legacy, tier in LEGACY_TO_TIER.items():
        sec = parse_section(text, legacy)
        extra = PRED_EXTRA.get(tier, {})
        merged = {**sec, **extra}
        lines.append(f"{tier}:")
        lines.append("  tier: PREDICTION")
        lines.append("  punishVl: 150")
        lines.append("  failWeight: 0.35")
        lines.append("  setbackEnabled: true")
        buf = merged.get("bufferToFlag", 7)
        lines.append(f"  bufferToFlag: {buf}")
        for k, v in sorted(merged.items()):
            if k in skip or k in ("bufferToFlag", "failVl", "tier"):
                continue
            if isinstance(v, bool):
                lines.append(f"  {k}: {'true' if v else 'false'}")
            elif isinstance(v, float) and v == int(v):
                lines.append(f"  {k}: {int(v)}")
            else:
                lines.append(f"  {k}: {v}")
        lines.append("")
    for tier, extra in PRED_EXTRA.items():
        if tier in LEGACY_TO_TIER.values():
            continue
        lines.append(f"{tier}:")
        lines.append("  tier: PREDICTION")
        lines.append("  punishVl: 150")
        lines.append("  failWeight: 0.35")
        lines.append("  setbackEnabled: true")
        for k, v in sorted(extra.items()):
            lines.append(f"  {k}: {v}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def emit_simulation_yml():
    lines = [
        "tiers-version: 1",
        "",
        "defaults:",
        "  tier: SIMULATION",
        "  enabled: true",
        "  shadow: false",
        "  punishVl: 90",
        "  failWeight: 0.5",
        "  bufferToFlag: 5",
        "  decay: 0.15",
        "  setbackEnabled: true",
        "",
    ]
    for name, keys in SIM_DEFAULTS.items():
        lines.append(f"{name}:")
        lines.append("  tier: SIMULATION")
        lines.append("  punishVl: 90")
        lines.append("  failWeight: 0.5")
        lines.append("  setbackEnabled: true")
        lines.append("  bufferToFlag: 5")
        for k, v in sorted(keys.items()):
            if k == "failVl":
                lines.append(f"  failVl: {v}")
            else:
                lines.append(f"  {k}: {v}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def main():
    text = CHECKS.read_text()
    (ROOT / "src/main/resources/tiers/prediction.yml").write_text(emit_prediction_yml(text))
    (ROOT / "src/main/resources/tiers/simulation.yml").write_text(emit_simulation_yml())
    print("Wrote prediction.yml and simulation.yml")


if __name__ == "__main__":
    main()
