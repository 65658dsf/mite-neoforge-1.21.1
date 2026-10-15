"""Compare generated enchantment data with the independently extracted R196 catalogue.

This audit does not call the Java production catalogue, support predicates or selection helper.
Source item eligibility comes from the recorded old runtime IDs and earlier equipment evidence.
Author: NingMeng123
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile


ROOT = Path(__file__).resolve().parents[2]


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "build/verification/enchantments-audit.json")
    args = parser.parse_args()
    report = {"author": "NingMeng123", "checks": 0, "failures": [], "source_item_mappings": {}, "enchantments": []}

    def check(condition, message):
        report["checks"] += 1
        if not condition:
            report["failures"].append(message)

    spec_path = ROOT / "spec/enchantments-r196-catalog.json"
    spec = read_json(spec_path)
    report["spec_sha256"] = digest(spec_path)
    check(digest(ROOT / spec["archive"]["path"]) == spec["archive"]["sha256"], "Pinned R196 archive SHA-256")
    registry = read_json(ROOT / "reference-data/runtime-registry.json")
    binary_classes = {row["name"]: row for row in read_json(ROOT / ".reference/r196/class-details.json")}
    for key, source in spec["source_index"].items():
        if "decompiled_sha256" in source:
            check(digest(ROOT / source["path"]) == source["decompiled_sha256"], f"Decompiled evidence SHA-256: {key}")
            check(binary_classes[key]["sha256"] == source["class_sha256"], f"Binary evidence SHA-256: {key}")
    expected = spec["enchantments"]
    check(len(expected) == spec["registered_count"] == 38, "Exactly 38 registered source enchantments")
    observed = {row["id"]: row for row in registry["enchantments"]}
    check(set(observed) == {row["id"] for row in expected}, "Source offline runtime registry IDs equal the independent catalogue")

    # These mappings predate this feature; none invokes current source-support implementation.
    tools = read_json(ROOT / "spec/r196-tools-016.json")["tools"]
    mapped = {row["legacy_id"]: row["id"] for row in tools}
    mapped.update({row["legacy_id"]: row["id"] for row in read_json(ROOT / "spec/armor-content-027.json")["items"]})
    mapped.update({row["legacy_id"]: row["id"] for row in read_json(ROOT / "spec/weapons-028.json")["bows"]})
    for row in read_json(ROOT / "spec/ranged-027.json")["materials"]:
        if row.get("rod_id") is not None:
            mapped[row["legacy_rod"]] = row["rod_id"]
    mapped.update({
        261: "minecraft:bow", 359: "minecraft:shears", 269: "minecraft:wooden_shovel",
        298: "minecraft:leather_helmet", 299: "minecraft:leather_chestplate", 300: "minecraft:leather_leggings", 301: "minecraft:leather_boots",
        306: "minecraft:iron_helmet", 307: "minecraft:iron_chestplate", 308: "minecraft:iron_leggings", 309: "minecraft:iron_boots",
    })
    source_items = {item for row in expected for item in row["supported_items"]["runtime_registered_item_ids_matching_predicate"]}
    excluded = set(range(1050, 1058))
    check(source_items - set(mapped) == excluded, "Only the eight previously user-excluded metal knives lack a modern mapping")
    report["source_item_mappings"] = {
        "mapped_eligible_source_items": len(source_items & set(mapped)),
        "excluded_legacy_ids": sorted(excluded),
        "exclusion_evidence": "docs/item-catalog-015.md:116; spec/r196-tools-016.json/excluded",
    }
    java_path = ROOT / "src/main/java/io/github/ningmeng123/mite/enchantment/R196EnchantmentCatalog.java"
    java = java_path.read_text(encoding="utf-8")
    rows = re.findall(r'(native|mite)Entry\((\d+), "([^"]+)", (\d+), (\d+), (\d+)\)', java)
    check(len(rows) == 38, "Runtime Java declares exactly 38 source rows")
    by_legacy = {int(old): (kind, name, int(weight), int(difficulty), int(maximum)) for kind, old, name, weight, difficulty, maximum in rows}
    generated = ROOT / "src/generated/resources/data"
    vanilla_jar = ROOT / "build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar"
    check(vanilla_jar.is_file(), "Pinned vanilla resource JAR exists")

    def generated_file(kind, resource_id):
        namespace, name = resource_id.split(":", 1)
        return generated / namespace / kind / (name + ".json")

    with zipfile.ZipFile(vanilla_jar) as vanilla:
        def raw(kind, resource_id):
            path = generated_file(kind, resource_id)
            if path.is_file():
                return read_json(path)
            namespace, name = resource_id.split(":", 1)
            return json.loads(vanilla.read(f"data/{namespace}/{kind}/{name}.json"))

        def tag_values(resource_id, seen=None):
            seen = set() if seen is None else seen
            if resource_id in seen:
                return set()
            seen.add(resource_id)
            data = raw("tags/enchantment", resource_id)
            values = set()
            for entry in data["values"]:
                entry = entry if isinstance(entry, str) else entry["id"]
                values |= tag_values(entry[1:], seen) if entry.startswith("#") else {entry}
            return values

        def item_values(item_set, pinned=False, seen=None):
            """Expand actual 1.21.1 item sets separately from the independently extracted source support."""
            seen = set() if seen is None else seen
            if isinstance(item_set, list):
                result = set()
                for item in item_set:
                    result |= item_values(item, pinned, seen)
                return result
            value = item_set if isinstance(item_set, str) else item_set["id"]
            if not value.startswith("#"):
                return {value}
            resource_id = value[1:]
            if resource_id in seen:
                return set()
            seen.add(resource_id)
            if pinned:
                namespace, name = resource_id.split(":", 1)
                entries = json.loads(vanilla.read(f"data/{namespace}/tags/item/{name}.json"))["values"]
            else:
                entries = raw("tags/item", resource_id)["values"]
            return item_values(entries, pinned, seen)

        definition_data = {}
        for source in expected:
            legacy = source["id"]
            runtime = observed[legacy]
            for field, actual in [("max_level", runtime["max_level"]), ("weight", runtime["scalars"]["aau.a"]),
                                  ("difficulty", runtime["scalars"]["aau.difficulty"])]:
                check(source[field] == actual, f"Source runtime {legacy}/{field}")
            kind, name, weight, difficulty, maximum = by_legacy[legacy]
            resource_id = ("minecraft:" if kind == "native" else "mite:") + name
            check(resource_id == source["suggested_modern_id"], f"Runtime resource identity: {legacy}")
            check((weight, difficulty, maximum) == (source["weight"], source["difficulty"], source["max_level"]), f"Runtime metadata source identity: {resource_id}")
            path = generated_file("enchantment", resource_id)
            check(path.is_file(), f"Generated definition exists: {resource_id}")
            if not path.is_file():
                continue
            data = read_json(path)
            definition_data[resource_id] = data
            check(data["max_level"] == source["max_level"], f"Generated max level: {resource_id}")
            check(data["weight"] == source["weight"], f"Generated selection weight: {resource_id}")
            minimum = [data["min_cost"]["base"] + data["min_cost"]["per_level_above_first"] * (level - 1) for level in range(1, source["max_level"] + 1)]
            check(minimum == source["min_cost_by_level"], f"Generated independent minimum costs: {resource_id}")
            check(data["max_cost"] == {"base": 2147483647, "per_level_above_first": 0}, f"Unbounded source candidate maximum: {resource_id}")
            if kind == "native":
                check(data["supported_items"] == f"#mite:enchantable/native_compat/{name}", f"Native metadata uses the isolated modern-compatibility union: {resource_id}")
            else:
                check(data["supported_items"] == data["primary_items"] == f"#mite:enchantable/{name}", f"Custom enchantments retain source-only item metadata: {resource_id}")
            tag_path = generated_file("tags/item", f"mite:enchantable/{name}")
            check(tag_path.is_file(), f"Generated support tag exists: {resource_id}")
            if not tag_path.is_file():
                continue
            values = set(read_json(tag_path)["values"])
            old_eligible = set(source["supported_items"]["runtime_registered_item_ids_matching_predicate"])
            for old in source_items & set(mapped):
                check((mapped[old] in values) == (old in old_eligible), f"Independently extracted supported item {legacy}/{old}/{mapped[old]}")
            check(not values.intersection({"minecraft:crossbow", "minecraft:trident", "minecraft:mace", "minecraft:elytra", "minecraft:shield"}), f"Modern-only device is not accidentally source equipment: {resource_id}")
            if kind == "native":
                pinned = json.loads(vanilla.read(f"data/minecraft/enchantment/{name}.json"))
                modern = {"minecraft:crossbow", "minecraft:trident", "minecraft:mace", "minecraft:elytra", "minecraft:shield"}
                native_supported = item_values(pinned["supported_items"], pinned=True)
                native_primary = item_values(pinned.get("primary_items", pinned["supported_items"]), pinned=True)
                actual_supported = item_values(data["supported_items"])
                actual_primary = item_values(data["primary_items"])
                check(actual_supported == values | (modern & native_supported), f"Global source-plus-modern support union is exact: {resource_id}")
                check(actual_primary == values | (modern & native_primary), f"Global source-plus-modern primary union is exact: {resource_id}")
                for modern_item in modern:
                    check((modern_item in actual_supported) == (modern_item in native_supported), f"Modern supported compatibility: {resource_id}/{modern_item}")
                    check((modern_item in actual_primary) == (modern_item in native_primary), f"Modern primary compatibility: {resource_id}/{modern_item}")
            report["enchantments"].append({"id": resource_id, "max_level": data["max_level"], "weight": data["weight"], "min_cost_by_level": minimum, "supported_items": len(values)})

        def excluded_set(data):
            value = data.get("exclusive_set", [])
            if isinstance(value, str):
                return tag_values(value[1:]) if value.startswith("#") else {value}
            return set(value)

        for a in expected:
            aid = a["suggested_modern_id"]
            if aid not in definition_data:
                continue
            for b in expected:
                bid = b["suggested_modern_id"]
                if bid not in definition_data:
                    continue
                actual_conflict = aid == bid or bid in excluded_set(definition_data[aid]) or aid in excluded_set(definition_data[bid])
                source_conflict = a["id"] == b["id"] or b["id"] in a["compatibility"]["pairwise_symmetric_conflict_ids"]
                check(actual_conflict == source_conflict, f"Independent source compatibility pair {aid} / {bid}")
        source_ids = {entry["suggested_modern_id"] for entry in expected}
        check(tag_values("mite:r196_source") == source_ids, "Source selection tag contains exactly the actual 38 enabled entries")
        check("minecraft:infinity" not in source_ids and "mite:balance" not in source_ids, "Class-only infinity and language-only balance stay outside R196 source pool")
        added = {value for value in source_ids if value.startswith("mite:")}
        for name in ["in_enchanting_table", "tradeable", "on_random_loot", "non_treasure"]:
            tag = read_json(generated_file("tags/enchantment", "minecraft:" + name))
            check(tag["replace"] is False and set(tag["values"]) == added, f"New enchantment availability is additive for modern tags: {name}")
        check({p.stem for p in (generated / "minecraft/enchantment").glob("*.json")} == {x.split(":", 1)[1] for x in source_ids if x.startswith("minecraft:")}, "Only the 21 old source-equivalent vanilla definitions are overridden")
        check({p.stem for p in (generated / "mite/enchantment").glob("*.json")} == {x.split(":", 1)[1] for x in added}, "Exactly 17 enabled MITE enchantments are generated")
        for name, base, per in [("sharpness", 1, 1), ("smite", 2, 2), ("bane_of_arthropods", 2, 2), ("power", 1, .5)]:
            data = definition_data.get("minecraft:" + name)
            if data:
                effect = data["effects"]["minecraft:damage"][0]["effect"]["value"]
                check(effect == {"type": "minecraft:linear", "base": base, "per_level_above_first": per}, f"Source damage arithmetic: {name}")
        for name in ["fire_protection", "blast_protection"]:
            data = definition_data.get("minecraft:" + name)
            if data:
                check("minecraft:attributes" not in data["effects"], f"No stacked native protection attribute: {name}")
        if "minecraft:thorns" in definition_data:
            check(definition_data["minecraft:thorns"]["effects"] == {}, "Source server thorns replaces the native retaliation and wear")
        if "minecraft:bane_of_arthropods" in definition_data:
            check("minecraft:post_attack" not in definition_data["minecraft:bane_of_arthropods"]["effects"], "R196 bane has no extra modern slowness")

    report["success"] = not report["failures"]
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Enchantment audit: {report['checks']} checks, {len(report['failures'])} failures; report: {args.report}")
    for error in report["failures"][:20]:
        print("FAIL:", error)
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
