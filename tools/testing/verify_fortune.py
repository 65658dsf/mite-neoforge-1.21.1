"""Audit the released R196 Fortune loot contract and package. Author: NingMeng123."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=ROOT / "build/libs/mite-0.1.10.jar")
    parser.add_argument("--report", type=Path, default=ROOT / "build/verification/fortune-0.1.10-package.json")
    args = parser.parse_args()
    report = {"author": "NingMeng123", "checks": 0, "failures": [], "source_sha256": {}}

    def check(condition, message):
        report["checks"] += 1
        if not condition:
            report["failures"].append(message)

    def nodes(value):
        if isinstance(value, dict):
            yield value
            for child in value.values():
                yield from nodes(child)
        elif isinstance(value, list):
            for child in value:
                yield from nodes(child)

    for name in ["apr", "aqc", "aoo", "aon", "apl", "aqz", "aom", "aqv", "BlockBreakInfo"]:
        path = ROOT / f".reference/r196/decompiled/{name}.java"
        report["source_sha256"][name] = hashlib.sha256(path.read_bytes()).hexdigest()

    with zipfile.ZipFile(args.jar) as jar:
        names = jar.namelist()
        check(len(names) == len(set(names)), "No duplicate package entries")
        metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        mod = next(row for row in metadata["mods"] if row["modId"] == "mite")
        check(mod["version"] == "0.1.10", "Embedded mod version is 0.1.10")
        check("NingMeng123" in mod["authors"], "Developer credit is NingMeng123")
        check(not any("/gametest/" in name or name.startswith("org/junit/") for name in names), "Test classes are excluded")
        check(not any(name.startswith((".reference/", "spec/", "build/")) for name in names), "Source archives and build evidence are excluded")
        check("io/github/ningmeng123/mite/loot/R196OreBonusFunction.class" in names, "Source ore function is packaged")
        check("io/github/ningmeng123/mite/mining/FlintGravelDrops.class" in names, "Source gravel event is packaged")
        for block in ["gravel", "glowstone", "short_grass", "fern"]:
            name = f"data/minecraft/loot_table/blocks/{block}.json"
            check(jar.read(name) == (ROOT / "src/main/resources" / name).read_bytes(), f"Released {block} loot matches saved source")
            value = json.loads(jar.read(name))
            check(not any(row.get("enchantment") == "minecraft:fortune" for row in nodes(value)), f"{block} has no leaked vanilla Fortune increment")
        gravel = json.loads(jar.read("data/minecraft/loot_table/blocks/gravel.json"))
        check([row["name"] for row in nodes(gravel) if row.get("type") == "minecraft:item"] == ["minecraft:gravel"], "Non-player gravel has only its block fallback")
        glowstone = json.loads(jar.read("data/minecraft/loot_table/blocks/glowstone.json"))
        check(any(row.get("count") == {"type": "minecraft:uniform", "min": 2.0, "max": 4.0} for row in nodes(glowstone)), "Glowstone keeps the independent two-to-four base yield")
        for block in ["coal_ore", "diamond_ore", "emerald_ore", "lapis_ore", "redstone_ore", "nether_quartz_ore", "iron_ore", "nether_gold_ore"]:
            value = json.loads(jar.read(f"data/minecraft/loot_table/blocks/{block}.json"))
            check(any(row.get("function") == "mite:r196_ore_bonus" for row in nodes(value)), f"{block} uses the R196 per-unit function")
            check(not any(row.get("function") == "minecraft:apply_bonus" for row in nodes(value)), f"{block} has no second modern multiplier")
        fortune = json.loads(jar.read("data/minecraft/enchantment/fortune.json"))
        check(fortune["max_level"] == 3 and fortune["weight"] == 5, "Ordinary Fortune retains source max III and weight five")
        check("minecraft:silk_touch" in fortune["exclusive_set"], "Ordinary Fortune conflicts with Silk Touch")
        json_count = 0
        for name in names:
            if name.endswith(".json"):
                json.loads(jar.read(name))
                json_count += 1
        report["json_resources"] = json_count
        report["production_classes"] = sum(name.endswith(".class") for name in names)

    report["jar"] = str(args.jar.resolve())
    report["jar_sha256"] = hashlib.sha256(args.jar.read_bytes()).hexdigest()
    report["success"] = not report["failures"]
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Fortune package audit: {report['checks']} checks, {len(report['failures'])} failures; {args.report}")
    for failure in report["failures"]:
        print("FAIL:", failure)
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
