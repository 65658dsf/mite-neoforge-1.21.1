"""Generate shared Better Combat profiles for missing axe/weapons. Author: NingMeng123.

Run without arguments to write the resources, or pass --check to verify them.
Only axes count as weapons among tools. Explicit item resources let players and
mobs share the missing hatchet and cudgel presets without enabling mining,
farming or crafting tools as weapons or editing user settings.
"""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
DEFINITIONS = ROOT / "src/main/java/io/github/ningmeng123/mite/registry/MiteTools.java"
RESOURCES = ROOT / "src/main/resources"
PRESETS = {
    "HATCHET": "axe",
    "CLUB": "mace",
    "CUDGEL": "mace",
}
DEFINE = re.compile(r'\bdefine\(\s*"([a-z0-9_.-]+:[a-z0-9_./-]+)"\s*,\s*([A-Z_]+)\s*,')
# Better Combat already explicitly assigns this item its sword combo.
PRESERVE = {"minecraft:wooden_sword"}


def expected_profiles() -> dict[str, tuple[str, str]]:
    definitions = DEFINE.findall(DEFINITIONS.read_text(encoding="utf-8"))
    if not definitions:
        raise ValueError(f"No MiteTools definitions found in {DEFINITIONS}")
    result: dict[str, tuple[str, str]] = {}

    def add(item: str, family: str) -> None:
        if item in PRESERVE or family not in PRESETS:
            return
        value = (family, f"bettercombat:{PRESETS[family]}")
        if item in result and result[item] != value:
            raise ValueError(f"Conflicting tool definitions for {item}")
        result[item] = value

    for item, family in definitions:
        add(item, family)
    # Vibranium is registered through the shared family loop instead of literal define calls.
    add("mite:vibranium_hatchet", "HATCHET")
    return dict(sorted(result.items()))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify expected files without writing them")
    args = parser.parse_args()
    profiles = expected_profiles()
    differences: list[str] = []
    for item, (_, parent) in profiles.items():
        namespace, path = item.split(":", 1)
        relative = Path("data") / namespace / "weapon_attributes" / f"{path}.json"
        target = RESOURCES / relative
        expected = (json.dumps({"parent": parent}, indent=2) + "\n").encode("utf-8")
        if target.exists() and target.read_bytes() == expected:
            continue
        differences.append(relative.as_posix())
        if not args.check:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(expected)
    if args.check and differences:
        raise SystemExit("Better Combat tool resources are missing or differ:\n" + "\n".join(differences))
    counts = Counter(family for family, _ in profiles.values())
    summary = ", ".join(f"{family}={count}" for family, count in sorted(counts.items()))
    print(f"{'Verified' if args.check else 'Generated'} {len(profiles)} shared tool profiles ({summary}).")


if __name__ == "__main__":
    main()
