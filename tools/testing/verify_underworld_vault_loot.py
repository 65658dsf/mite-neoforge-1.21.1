"""Independently evaluate generated underworld vault loot, without running Minecraft.

The evaluator reads the serialized loot graph, rather than calling the production
data generator or copying its probability helper. Author: NingMeng123.
"""
from __future__ import annotations

import argparse
from fractions import Fraction
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
PREFIX = "chests/underworld_trial_chambers/"
VANILLA_PREFIX = "chests/trial_chambers/"
BASE_TABLES = (
    "reward", "reward_common", "reward_rare", "reward_unique",
    "reward_ominous", "reward_ominous_common", "reward_ominous_rare", "reward_ominous_unique",
)
ARROW_FACTORS = {
    "minecraft:arrow": 1,
    "mite:rusted_iron_arrow": 1,
    "mite:obsidian_arrow": 2,
    "mite:iron_arrow": 2,
    "mite:copper_arrow": 3,
    "mite:golden_arrow": 4,
    "mite:silver_arrow": 5,
    "mite:ancient_metal_arrow": 8,
    "mite:mithril_arrow": 16,
    "mite:adamantium_arrow": 32,
}
NUGGETS = tuple("mite:" + metal + "_nugget" for metal in ("ancient_metal", "mithril", "adamantium"))
REPLACEMENTS = {
    "minecraft:diamond_axe": "mite:ancient_metal_axe",
    "minecraft:diamond_chestplate": "mite:ancient_metal_chestplate",
}


def fraction(value) -> Fraction:
    return Fraction(str(value))


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key: " + key)
        result[key] = value
    return result


def parse(raw):
    return json.loads(raw, object_pairs_hook=unique_object)


def number_values(provider):
    """The shipped tables use integer constants and uniform integer roll counts."""
    if isinstance(provider, (int, float)):
        value = fraction(provider)
        if value.denominator != 1:
            raise ValueError("Expected integer roll/count: " + str(provider))
        return (int(value),)
    if provider.get("type") != "minecraft:uniform":
        raise ValueError("Unsupported number provider: " + repr(provider))
    lower, upper = fraction(provider["min"]), fraction(provider["max"])
    if lower.denominator != 1 or upper.denominator != 1 or lower < 0 or upper < lower:
        raise ValueError("Invalid integer uniform provider: " + repr(provider))
    return tuple(range(int(lower), int(upper) + 1))


class Graph:
    def __init__(self, tables):
        self.tables = tables
        self.memo = {}
        self.visiting = set()

    def no_target(self, name, target):
        key = name, frozenset(target)
        if key in self.memo:
            return self.memo[key]
        if key in self.visiting:
            raise ValueError("Cyclic loot table: " + name)
        self.visiting.add(key)
        missing = Fraction(1)
        for pool in self.tables[name]["pools"]:
            if pool.get("bonus_rolls", 0) != 0:
                raise ValueError("Luck-dependent bonus rolls require an explicit luck assumption")
            chance = Fraction(1)
            for condition in pool.get("conditions", []):
                if condition["condition"] != "minecraft:random_chance":
                    raise ValueError("Unsupported pool condition: " + repr(condition))
                chance *= fraction(condition["chance"])
            entries = pool["entries"]
            total = sum(entry.get("weight", 1) for entry in entries)
            per_draw = sum(entry.get("weight", 1) * self.entry_missing(entry, target) for entry in entries) / total
            counts = number_values(pool["rolls"])
            absent = sum(per_draw ** count for count in counts) / len(counts)
            missing *= 1 - chance + chance * absent
        self.visiting.remove(key)
        self.memo[key] = missing
        return missing

    def entry_missing(self, entry, target):
        if entry.get("conditions") or entry.get("quality", 0):
            raise ValueError("Conditional or luck-dependent entry is not part of these authored tables")
        kind = entry["type"]
        if kind == "minecraft:empty":
            return Fraction(1)
        if kind == "minecraft:loot_table":
            if not isinstance(entry["value"], str):
                raise ValueError("Inline loot tables are not supported")
            return self.no_target(entry["value"], target)
        if kind != "minecraft:item":
            raise ValueError("Unsupported loot entry: " + kind)
        name = item_name(entry)
        if name not in target:
            return Fraction(1)
        counts = (1,)
        for function in entry.get("functions", []):
            if function["function"] == "minecraft:set_count":
                if function.get("add", False):
                    raise ValueError("Additive set_count requires a separate evaluator")
                counts = number_values(function["count"])
        return Fraction(sum(count <= 0 for count in counts), len(counts))

    def probability(self, name, target):
        return 1 - self.no_target(name, frozenset(target))


def item_name(entry):
    name = entry["name"]
    if name == "minecraft:book" and any(function["function"] in (
        "minecraft:enchant_randomly", "minecraft:enchant_with_levels", "minecraft:set_enchantments",
    ) for function in entry.get("functions", [])):
        return "minecraft:enchanted_book"
    return name


def entries(table):
    return [entry for pool in table["pools"] for entry in pool["entries"]]


def summary(value):
    return {"numerator": value.numerator, "denominator": value.denominator,
            "probability": float(value), "percent": round(float(value * 100), 9)}


def audit(args):
    failures = []
    checks = 0

    def check(condition, message):
        nonlocal checks
        checks += 1
        if not condition:
            failures.append(message)

    configuration = ROOT / "src/main/resources/mite/underworld-vault-loot.json"
    config_bytes = configuration.read_bytes()
    config = parse(config_bytes)
    check(config.get("author") == "NingMeng123" and config.get("schemaVersion") == 1, "Unexpected loot specification author/version")
    check(config.get("sourcePrefix") == "minecraft:" + VANILLA_PREFIX, "Unexpected source-table namespace")
    check(config.get("targetPrefix") == "mite:" + PREFIX, "Unexpected target-table namespace")
    check(config.get("itemReplacements") == REPLACEMENTS, "Requested equipment replacements changed")
    configured_arrows = {row["item"]: row["rarityFactor"] for row in config["materialArrows"]["materials"]}
    check(configured_arrows == ARROW_FACTORS, "Declared inverse-rarity factors changed")
    check(config["materialArrows"]["inverseWeightBase"] == 480, "Declared inverse-weight base changed")
    ranged = parse((ROOT / "spec/ranged-027.json").read_bytes())
    registered_arrows = {material["arrow_id"] for material in ranged["materials"]}
    check(registered_arrows == set(ARROW_FACTORS), "All ten registered arrow materials must be represented")

    resource_roots = [ROOT / "src/main/resources", ROOT / "src/generated/resources"]
    table_paths = sorted(resource_roots[1].glob("data/mite/loot_table/" + PREFIX + "*.json"))
    modern = {"mite:" + PREFIX + path.stem: parse(path.read_bytes()) for path in table_paths}
    check(len(modern) == 10, "Expected eight reward and two ammunition tables")
    check(all("mite:" + PREFIX + name in modern for name in BASE_TABLES), "Missing reward entry points/subtables")
    for root in resource_roots:
        overwritten = list(root.glob("data/minecraft/loot_table/" + VANILLA_PREFIX + "reward*.json"))
        check(not overwritten, "Vanilla trial reward tables must not be overwritten: " + repr(overwritten))

    with ZipFile(args.minecraft_resources) as archive:
        vanilla = {"minecraft:" + VANILLA_PREFIX + name: parse(archive.read(
            "data/minecraft/loot_table/" + VANILLA_PREFIX + name + ".json")) for name in BASE_TABLES}

    for variant in ("reward", "reward_ominous"):
        original = vanilla["minecraft:" + VANILLA_PREFIX + variant]
        expected = parse(json.dumps(original).replace("minecraft:" + VANILLA_PREFIX, "mite:" + PREFIX))
        check(modern["mite:" + PREFIX + variant] == expected,
              "Root roll counts, weighting or unique trigger changed: " + variant)
    expected_totals = {"reward_common": 2550, "reward_rare": 2330,
                       "reward_ominous_common": 1570, "reward_ominous_rare": 2960}
    for table, expected in expected_totals.items():
        check(sum(entry.get("weight", 1) for entry in entries(modern["mite:" + PREFIX + table])) == expected,
              "Unexpected total reward weight: " + table)
    for table, overrides in config["weightOverrides"].items():
        for item, expected in overrides.items():
            matching = [entry for entry in entries(modern["mite:" + PREFIX + table]) if entry.get("name") == item]
            check(len(matching) == 1 and matching[0].get("weight", 1) == expected,
                  "Serialized weight differs from configuration: " + table + "/" + item)
    for table, definitions in config["uniquePools"].items():
        actual = entries(modern["mite:" + PREFIX + table])
        check(len(actual) == len(definitions), "Unique reward count differs from specification: " + table)
        for definition in definitions:
            matching = [entry for entry in actual if entry.get("name") == definition["item"]]
            check(len(matching) == 1, "Unique reward missing/duplicated: " + definition["item"])
            if len(matching) != 1:
                continue
            entry = matching[0]
            check(entry.get("weight", 1) == definition["weight"], "Unique weight differs from specification: " + definition["item"])
            counts = (1,)
            for function in entry.get("functions", []):
                if function["function"] == "minecraft:set_count":
                    counts = number_values(function["count"])
            check((min(counts), max(counts)) == (definition["minCount"], definition["maxCount"]),
                  "Unique quantity differs from specification: " + definition["item"])
    for table in ("reward_rare", "reward_ominous_rare"):
        for before, after in REPLACEMENTS.items():
            original = next(entry for entry in entries(vanilla["minecraft:" + VANILLA_PREFIX + table])
                            if entry.get("name") == before)
            replacement = next(entry for entry in entries(modern["mite:" + PREFIX + table])
                               if entry.get("name") == after)
            check(original.get("functions") == replacement.get("functions"),
                  "Replacement equipment must retain authored enchantment levels/options: " + table + "/" + after)

    leaves = set()
    arrow_tables = {}
    for name, table in modern.items():
        check(table.get("type") == "minecraft:chest", "Expected chest loot context: " + name)
        table_items = {}
        for entry in entries(table):
            if entry["type"] == "minecraft:loot_table":
                check(entry["value"] in modern, "Reward graph escapes its underworld tables: " + entry["value"])
            elif entry["type"] == "minecraft:item":
                item = item_name(entry)
                leaves.add(item)
                table_items[item] = entry
                forbidden = "music_disc_" in item or "banner_pattern" in item or "smithing_template" in item
                check(not forbidden, "Removed cosmetic reward remains: " + item)
                check(item not in REPLACEMENTS, "Diamond equipment remains: " + item)
        if set(table_items) == registered_arrows:
            arrow_tables[name] = table_items
            check(len(entries(table)) == len(registered_arrows), "Arrow table contains extra/duplicate entries: " + name)
            weights = {item: entry.get("weight", 1) for item, entry in table_items.items()}
            check(sum(weights.values()) == 1921, "Unexpected material-arrow denominator: " + name)
            products = {weights[item] * ARROW_FACTORS[item] for item in registered_arrows}
            check(products == {480}, "Arrow probability must be inversely proportional to declared rarity factors")
            for entry in table_items.values():
                counts = [number_values(function["count"]) for function in entry.get("functions", [])
                          if function["function"] == "minecraft:set_count"]
                check(len(counts) == 1 and min(counts[0]) > 0 and max(counts[0]) <= 16,
                      "Arrow bundle must be positive and within its source stack size: " + entry["name"])
    check(len(arrow_tables) == 2, "Expected separate normal/ominous tables for all ten arrow materials")
    for definition in config["materialArrows"]["pools"]:
        name = "mite:" + PREFIX + definition["table"]
        check(name in arrow_tables, "Configured ammunition sub-table is missing: " + name)
        for entry in arrow_tables.get(name, {}).values():
            counts = next(number_values(function["count"]) for function in entry.get("functions", [])
                          if function["function"] == "minecraft:set_count")
            check((min(counts), max(counts)) == (definition["minCount"], definition["maxCount"]),
                  "Arrow quantity differs from configuration: " + name + "/" + entry["name"])
    check(set(NUGGETS).issubset(leaves), "A requested nugget material is missing")
    check(set(REPLACEMENTS.values()).issubset(leaves), "Corresponding ancient-metal equipment is missing")

    old_graph, new_graph = Graph(vanilla), Graph(modern)
    # Frozen original probabilities make parser mistakes visible independently of new data.
    baselines = {
        ("reward", "minecraft:emerald"): Fraction(17268704, 44921875),
        ("reward", "minecraft:diamond"): Fraction(166533, 1953125),
        ("reward_ominous", "minecraft:enchanted_golden_apple"): Fraction(9, 40),
        ("reward_ominous", "minecraft:heavy_core"): Fraction(3, 40),
    }
    for (table, item), expected in baselines.items():
        check(old_graph.probability("minecraft:" + VANILLA_PREFIX + table, {item}) == expected,
              "Pinned vanilla probability fingerprint changed: " + table + "/" + item)

    items = sorted(leaves | {entry["name"] for table in vanilla.values() for entry in entries(table)
                             if entry["type"] == "minecraft:item"})
    probabilities = {}
    for variant in ("reward", "reward_ominous"):
        before, after = "minecraft:" + VANILLA_PREFIX + variant, "mite:" + PREFIX + variant
        rows = {}
        for item in items:
            old, new = old_graph.probability(before, {item}), new_graph.probability(after, {item})
            rows[item] = {"vanilla": summary(old), "underworld": summary(new),
                          "change_percentage_points": round(float((new - old) * 100), 9)}
        for item in ("minecraft:emerald", "minecraft:diamond"):
            delta = new_graph.probability(after, {item}) - old_graph.probability(before, {item})
            check(0 < delta < Fraction(1, 20), "Gem reward must increase by less than five percentage points: " + variant + "/" + item)
        for item in NUGGETS:
            check(new_graph.probability(after, {item}) > 0, "Nugget absent from a vault variant: " + variant + "/" + item)
        for item in registered_arrows:
            check(new_graph.probability(after, {item}) > 0, "Arrow absent from a vault variant: " + variant + "/" + item)
        for old, new in REPLACEMENTS.items():
            check(new_graph.probability(after, {old}) == 0, "Removed diamond gear remains reachable")
            check(new_graph.probability(after, {new}) > 0, "Ancient-metal replacement is unreachable")
        rows["all_material_arrows"] = {"vanilla": summary(old_graph.probability(before, registered_arrows)),
                                       "underworld": summary(new_graph.probability(after, registered_arrows))}
        probabilities[variant] = rows
    ominous = "mite:" + PREFIX + "reward_ominous"
    check(new_graph.probability(ominous, {"minecraft:enchanted_golden_apple"}) == Fraction(1, 4),
          "Enchanted golden apple should rise only from 22.5% to 25%")
    check(new_graph.probability(ominous, {"minecraft:heavy_core"}) == Fraction(3, 40),
          "Heavy-core probability must remain 7.5%")
    for item in ("minecraft:emerald_block", "minecraft:diamond_block"):
        delta = new_graph.probability(ominous, {item}) - old_graph.probability(
            "minecraft:" + VANILLA_PREFIX + "reward_ominous", {item})
        check(0 < delta < Fraction(1, 20), "Gem-block reward must increase slightly: " + item)

    report = {
        "author": "NingMeng123", "checks": checks, "failures": failures,
        "probability_definition": "At least one of this item per successful vault opening, all pools and duplicate paths combined; default loot luck zero.",
        "method": "Exact rational probability from independent serialized loot-graph interpretation; no Monte Carlo and no production probability helper.",
        "configuration_sha256": hashlib.sha256(config_bytes).hexdigest(),
        "minecraft_resources": str(args.minecraft_resources),
        "minecraft_resources_sha256": hashlib.sha256(args.minecraft_resources.read_bytes()).hexdigest(),
        "generated_tables": {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in table_paths},
        "arrow_relative_rarity_factors": ARROW_FACTORS,
        "probabilities": probabilities,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"checks": checks, "failures": failures, "report": str(args.report)}, ensure_ascii=False))
    return 1 if failures else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-resources", type=Path, default=ROOT / "build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar")
    parser.add_argument("--report", type=Path, default=ROOT / "build/verification/underworld-vault-loot-audit.json")
    args = parser.parse_args()
    try:
        return audit(args)
    except (OSError, ValueError, KeyError, TypeError, ZeroDivisionError) as error:
        print(json.dumps({"error": str(error)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
