#!/usr/bin/env python3
"""Port legacy checks to Char* TierCheck implementations."""
import re
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/java")

PORTS = [
    ("com/colin/vezanticheat/checks/combat/AutoClickA.java", "CharAutoClickA"),
    ("com/colin/vezanticheat/checks/combat/AutoClickB.java", "CharAutoClickB", "AutoClickA"),
    ("com/colin/vezanticheat/checks/combat/AutoClickC.java", "CharAutoClickC", "AutoClickA"),
    ("com/colin/vezanticheat/checks/combat/AimAssistA.java", "CharAimAssistA"),
    ("com/colin/vezanticheat/checks/combat/AimAssistB.java", "CharAimAssistB"),
    ("com/colin/vezanticheat/checks/combat/AimAssistC.java", "CharAimAssistC"),
    ("com/colin/vezanticheat/checks/combat/CriticalsA.java", "CharCriticalsA"),
    ("com/colin/vezanticheat/checks/combat/CriticalsB.java", "CharCriticalsB"),
    ("com/colin/vezanticheat/checks/combat/CriticalsC.java", "CharCriticalsC"),
    ("com/colin/vezanticheat/checks/player/ScaffoldA.java", "CharScaffoldA"),
    ("com/colin/vezanticheat/checks/player/ScaffoldB.java", "CharScaffoldB"),
    ("com/colin/vezanticheat/checks/player/ScaffoldC.java", "CharScaffoldC"),
    ("com/colin/vezanticheat/checks/player/ScaffoldD.java", "CharScaffoldD"),
    ("com/colin/vezanticheat/checks/player/ScaffoldE.java", "CharScaffoldE"),
    ("com/colin/vezanticheat/checks/player/InventoryA.java", "CharInventoryA"),
    ("com/colin/vezanticheat/checks/player/InventoryB.java", "CharInventoryB"),
    ("com/colin/vezanticheat/checks/player/InventoryC.java", "CharInventoryC"),
]

OUT_PKG = "com/colin/vezanticheat/tier/characteristics"


def read_legacy(rel_path):
    with open(os.path.join(SRC, rel_path), "r") as f:
        return f.read()


def merge_autoclick_base(base_src, child_src, char_name, legacy_name):
    """Inline AutoClickA helpers into B/C standalone classes."""
    # Extract stats, ClickStats, isCombatSwing, getTargetBlockCompat, avg, std, r from base
    helpers = []
    for pattern in [
        r"    protected boolean isCombatSwing\([\s\S]*?\n    \}\n",
        r"    @SuppressWarnings\(\"deprecation\"\)\n    private Block getTargetBlockCompat[\s\S]*?\n    \}\n",
        r"    protected ClickStats stats\([\s\S]*?\n    \}\n",
        r"    private double avg\([\s\S]*?\n    \}\n",
        r"    private double std\([\s\S]*?\n    \}\n",
        r"    protected double r\(double v\) \{[\s\S]*?\n    \}\n",
        r"    protected static final class ClickStats \{[\s\S]*?\n    \}\n",
    ]:
        m = re.search(pattern, base_src)
        if m:
            helpers.append(m.group(0))

    child_src = re.sub(r"extends AutoClickA", "extends TierCheck", child_src)
    child_src = re.sub(r"    protected " + legacy_name + r"\(VezAntiCheat plugin, String checkName\) \{[\s\S]*?\n    \}\n\n", "", child_src)
    child_src = re.sub(
        r"    public " + legacy_name + r"\(VezAntiCheat plugin\) \{\n        super\(plugin, \"" + legacy_name + r"\"\);\n    \}\n",
        f"    public {char_name}(VezAntiCheat plugin) {{\n        super(plugin, \"{char_name}\", CheckTier.CHARACTERISTICS);\n    }}\n",
        child_src,
    )
    # Insert helpers before closing brace
    insert = "\n".join(helpers)
    child_src = child_src.rstrip()
    if child_src.endswith("}"):
        child_src = child_src[:-1] + "\n" + insert + "\n}\n"
    return child_src


def transform(content, char_name, legacy_name, parent_legacy=None):
    legacy_simple = legacy_name.replace("Char", "") if char_name.startswith("Char") else legacy_name
    if char_name.startswith("Char"):
        legacy_simple = char_name[4:]  # CharAutoClickA -> AutoClickA

    content = re.sub(
        r"package com\.colin\.vezanticheat\.checks\.(combat|player);",
        "package com.colin.vezanticheat.tier.characteristics;",
        content,
    )
    content = content.replace("import com.colin.vezanticheat.checks.Check;\n", "")
    if "import com.colin.vezanticheat.tier.CheckTier;" not in content:
        content = content.replace(
            "import com.colin.vezanticheat.VezAntiCheat;\n",
            "import com.colin.vezanticheat.VezAntiCheat;\n"
            "import com.colin.vezanticheat.tier.CheckTier;\n"
            "import com.colin.vezanticheat.tier.TierCheck;\n",
        )

    content = re.sub(r"public class " + legacy_simple + r" extends Check", f"public final class {char_name} extends TierCheck", content)
    content = re.sub(r"public class " + legacy_simple + r" extends AutoClickA", f"public final class {char_name} extends TierCheck", content)

    # Constructors
    content = re.sub(
        r"    protected " + legacy_simple + r"\(VezAntiCheat plugin, String checkName\) \{\n        super\(plugin, checkName, \"[^\"]+\"\);\n    \}\n\n",
        "",
        content,
    )
    content = re.sub(
        r"    public " + legacy_simple + r"\(VezAntiCheat plugin\) \{\n        super\(plugin, \"" + legacy_simple + r"\", \"[^\"]+\"\);\n    \}",
        f"    public {char_name}(VezAntiCheat plugin) {{\n        super(plugin, \"{char_name}\", CheckTier.CHARACTERISTICS);\n    }}",
        content,
    )
    content = re.sub(
        r"    public " + legacy_simple + r"\(VezAntiCheat plugin\) \{\n        super\(plugin, \"" + legacy_simple + r"\"\);\n    \}",
        f"    public {char_name}(VezAntiCheat plugin) {{\n        super(plugin, \"{char_name}\", CheckTier.CHARACTERISTICS);\n    }}",
        content,
    )

    content = content.replace("plugin.cfg()", "plugin.tierCfg()")
    content = re.sub(r"\bdecay\(data,", "decay(p,", content)
    content = content.replace("onBlockPlace(", "onBlockPlacePacket(")

    # Remove redundant enabled checks that reference old cfg pattern (tier fail handles)
    content = re.sub(r"        if \(!plugin\.tierCfg\(\)\.checkEnabled\(name\(\)\)\) return;\n", "", content)
    content = re.sub(r"        if \(!enabled\(\)\) \{[^\}]+\}\n", "", content)
    content = re.sub(r"        if \(!enabled\(\)\) return;\n", "", content)

    # name() references in cfg stay as name() which returns Char name - good

    # Fix AutoClickB/C stats/isCombatSwing calls - make methods local if needed
    content = content.replace("extends AutoClickA", "extends TierCheck")

    # Inventory verbose uses verbose() from Check - TierCheck has verbose
    return content


def port_file(rel_path, char_name, parent_legacy=None):
    legacy_name = os.path.basename(rel_path).replace(".java", "")
    content = read_legacy(rel_path)

    if parent_legacy == "AutoClickA":
        base = read_legacy("com/colin/vezanticheat/checks/combat/AutoClickA.java")
        content = merge_autoclick_base(base, content, char_name, legacy_name)
    else:
        content = transform(content, char_name, legacy_name)

    content = transform(content, char_name, legacy_name)

    out_path = os.path.join(SRC, OUT_PKG, char_name + ".java")
    with open(out_path, "w") as f:
        f.write(content)
    print("Wrote", out_path, "lines=", content.count("\n") + 1)


if __name__ == "__main__":
    for entry in PORTS:
        if len(entry) == 2:
            port_file(entry[0], entry[1])
        else:
            port_file(entry[0], entry[1], entry[2])
