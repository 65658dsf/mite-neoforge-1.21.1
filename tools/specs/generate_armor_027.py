"""Extract 027 armor identities and recipes from guarded R196 registration. Author: NingMeng123."""
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = json.loads((ROOT / "reference-data/runtime-registry.json").read_text(encoding="utf-8-sig"))
CATALOG = json.loads((ROOT / "spec/item-catalog-015.json").read_text(encoding="utf-8-sig"))
BY_ID = {item["id"]: item for item in RUNTIME["items"]}
MATERIALS = {
    "copper": (4, 7, 30, "EXCELLENT", 4),
    "silver": (4, 7, 30, "EXCELLENT", 4),
    "gold": (4, 6, 50, "SUPERB", 3),
    "rusted_iron": (4, 6, 0, "POOR", 0),
    "iron": (8, 8, 30, "MASTERWORK", 5),
    "ancient_metal": (16, 8, 40, "MASTERWORK", 5),
    "mithril": (64, 9, 100, "LEGENDARY", 6),
    "adamantium": (256, 10, 40, "LEGENDARY", 7),
}
ARMOR_CLASSES = {"ItemHelmet": ("helmet", 5), "ItemCuirass": ("chestplate", 8),
                 "ItemLeggings": ("leggings", 7), "ItemBoots": ("boots", 4)}
MAP = {entry["source_registry"]["id"]: entry.get("actual_modern_id", entry["proposed_modern_id"])
       for entry in CATALOG["entries"] if entry.get("source_registry") and entry.get("proposed_modern_id")}
MAP.update({265: "minecraft:iron_ingot", 266: "minecraft:gold_ingot", 371: "minecraft:gold_nugget",
            958: "minecraft:iron_nugget", 306: "minecraft:iron_helmet", 307: "minecraft:iron_chestplate",
            308: "minecraft:iron_leggings", 309: "minecraft:iron_boots", 302: "minecraft:chainmail_helmet",
            303: "minecraft:chainmail_chestplate", 304: "minecraft:chainmail_leggings", 305: "minecraft:chainmail_boots"})


def write(path, data):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    items = []
    chains = []
    texture_manifest = []
    names = {}
    catalog_ids = {e["source_registry"]["id"] for e in CATALOG["entries"] if e.get("category") == "装备"}
    selected = catalog_ids | {302, 303, 304, 305}
    for legacy in sorted(selected):
        original = BY_ID[legacy]
        material = original["materials"][0]
        durability, protection, enchantability, quality, horse = MATERIALS[material]
        horse_item = original["class"] == "ItemHorseArmor"
        slot, components = ("body", 0) if horse_item else ARMOR_CLASSES[original["class"]]
        chain = bool(original["scalars"].get("wh.is_chain_mail", False))
        override = original["scalars"]["yc.lowest_crafting_difficulty_to_produce_override"]
        source_recipes = [r for r in RUNTIME["crafting_recipes"] if (r.get("output") or {}).get("item_id") == legacy]
        minimum = override if override < 3e38 else min(
            (r["difficulty"] / r["output"]["count"] for r in source_recipes if r["include_in_lowest_difficulty"]), default=-1)
        items.append({"id": MAP[legacy], "legacy_id": legacy, "material": material, "slot": slot,
                      "components": components, "chainmail": chain, "horse": horse_item,
                      "material_durability": durability, "material_protection": protection,
                      "enchantability": enchantability, "maximum_quality": quality,
                      "horse_protection": horse, "base_durability": 0 if horse_item else original["scalars"]["yc.b"],
                      "default_quality": None if horse_item else ("POOR" if material == "rusted_iron" else "AVERAGE"),
                      "lowest_difficulty": minimum, "source_orders": [r["order"] for r in source_recipes],
                      "source_sprite": original["scalars"]["yc.cA"], "source": original["source"],
                      "source_pointer": f"/items/id={legacy}", "catalog_leaf": legacy in catalog_ids})
    for original in RUNTIME["items"]:
        if original["class"] != "ItemChain":
            continue
        chains.append({"id": MAP[original["id"]], "legacy_id": original["id"], "material": original["materials"][0],
                       "maximum_stack": original["scalars"]["yc.cw"], "source_sprite": original["scalars"]["yc.cA"],
                       "source": original["source"]})
    target_ids = selected | {c["legacy_id"] for c in chains}
    recipes = []
    timed = {}
    for original in RUNTIME["crafting_recipes"]:
        output = original.get("output") or {}
        if output.get("item_id") not in target_ids:
            # Include exact reverse chain -> four nuggets, which is part of the chain material system.
            components = original.get("components") or []
            if len(components) != 1 or not components[0] or components[0]["item_id"] not in {c["legacy_id"] for c in chains}:
                continue
        if original.get("dynamic"):
            continue
        result = MAP[output["item_id"]]
        components = original["components"]
        width, height = original.get("width", 1), original.get("height", 1)
        inputs = [MAP[c["item_id"]] if c else None for c in components]
        reverse = output["item_id"] not in target_ids
        recipe_id = "mite:" + MAP[components[0]["item_id"]].split(":")[1] + "_to_nuggets" if reverse else result
        item = {"id": recipe_id, "output": result, "count": output["count"], "width": width,
                "height": height, "ingredients": inputs, "difficulty": original["difficulty"],
                "required_bench": (original.get("bench_material") or {}).get("name"),
                "source_order": original["order"], "source_class": original["class"], "source": original["source"]}
        recipes.append(item)
        timed[recipe_id] = {k: item[k] for k in ("difficulty", "required_bench", "source_order")}
    quality_items = {i["id"]: {k: i[k] for k in ("lowest_difficulty", "legacy_id", "source_orders")}
                     for i in items if not i["horse"]}
    with zipfile.ZipFile(ROOT / "MITE Resource Pack 1.6.4.zip") as archive:
        for item in items + chains:
            modern = item["id"]
            # Only newly registered equipment overrides old-style item/armor images.
            if modern.startswith("minecraft:") and modern != "minecraft:chain":
                continue
            namespace, path = modern.split(":")
            old = "assets/minecraft/textures/items/" + item["source_sprite"] + ".png"
            new = f"src/main/resources/assets/{namespace}/textures/item/{path}.png"
            # Vanilla iron chain keeps its 1.21 block/item model; the source icon is separate documentation evidence.
            if modern == "minecraft:chain":
                continue
            binary = archive.read(old)
            target = ROOT / new
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(binary)
            texture_manifest.append({"source": old, "target": new, "sha256": hashlib.sha256(binary).hexdigest()})
        for material in MATERIALS:
            for chain in (False, True):
                if material == "iron" or material == "gold" and not chain:
                    continue
                stem = material + ("_chainmail" if chain else "")
                for layer in (1, 2):
                    old = f"assets/minecraft/textures/models/armor/{stem}_layer_{layer}.png"
                    new = f"src/main/resources/assets/mite/textures/models/armor/{stem}_layer_{layer}.png"
                    binary = archive.read(old)
                    target = ROOT / new
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(binary)
                    texture_manifest.append({"source": old, "target": new, "sha256": hashlib.sha256(binary).hexdigest()})
        for item in items:
            if not item["horse"]:
                continue
            material = item["material"]
            old = f"assets/minecraft/textures/entity/horse/armor/horse_armor_{material}.png"
            new = f"src/main/resources/assets/mite/textures/entity/horse/armor/horse_armor_{material}.png"
            binary = archive.read(old)
            target = ROOT / new
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(binary)
            texture_manifest.append({"source": old, "target": new, "sha256": hashlib.sha256(binary).hexdigest()})
    for entry in CATALOG["entries"]:
        if (entry.get("source_registry") or {}).get("id") in target_ids:
            modern = MAP[entry["source_registry"]["id"]]
            if modern.startswith("mite:"):
                names["item." + modern.replace(":", ".")] = entry["name"]
    metadata = {"author": "NingMeng123", "archive_sha256": RUNTIME["archive_sha256"],
                "source": "guarded R196 runtime registry; wh, EnumEquipmentMaterial, ItemHorseArmor, ItemChain, zx, RecipesMITE",
                "items": items, "chains": chains, "recipes": recipes}
    write("src/main/resources/mite/armor-content-027.json", metadata)
    write("spec/armor-content-027.json", {**metadata, "texture_manifest": texture_manifest,
          "limits": ["Source flat defense restored for players whose equipped armor is entirely source-profiled; bare players, unprofiled mixed armor and non-player combat retain native damage mitigation. Modern damage tags explicitly map source distinctions.",
                     "Mithril/adamantium horse armor has no source manufacturing recipe or confirmed survival loot entry; creative-only source availability retained.",
                     "Professions suppressed in the reference default runtime; blacksmithing labels do not enable a skill gate."]})
    write("src/main/resources/mite/timed-crafting-027-armor.json", {"author": "NingMeng123", "recipes": timed})
    write("src/main/resources/mite/quality-equipment-027-armor.json", {"author": "NingMeng123", "items": quality_items})
    write("spec/armor-language-027-fragment.json", {"author": "NingMeng123", "zh_cn": names,
         "en_us": {"item." + e["proposed_modern_id"].replace(":", "."): e["english"]
                   for e in CATALOG["entries"] if (e.get("source_registry") or {}).get("id") in target_ids
                   and e["proposed_modern_id"].startswith("mite:")}})
    # Preserve original gear probabilities: remove only armor from mapped modern pools,
    # then make the original number of weighted rolls; non-gear entries are empty outcomes.
    symbol_ids = {symbol: item["id"] for item in RUNTIME["items"] for symbol in item["symbols"]}
    gear_ids = target_ids | {298, 299, 300, 301, 306, 307, 308, 309, 314, 315, 316, 317, 417, 418}
    MAP.update({298: "minecraft:leather_helmet", 299: "minecraft:leather_chestplate", 300: "minecraft:leather_leggings",
                301: "minecraft:leather_boots", 417: "minecraft:iron_horse_armor", 418: "minecraft:golden_horse_armor"})
    pools = {}
    mappings = [("dungeon_surface", "afp", "a", "minecraft:chests/simple_dungeon", 8, 8, True),
                ("dungeon_underworld", "afp", "chest_contents_for_underworld", "minecraft:chests/simple_dungeon", 8, 8, True),
                ("mineshaft", "agh", "a", "minecraft:chests/abandoned_mineshaft", 3, 6, True),
                ("village", "ahw", "a", "minecraft:chests/village/village_weaponsmith", 2, 3, True),
                ("desert", "ahl", "i", "minecraft:chests/desert_pyramid", 2, 6, True),
                ("jungle", "ahm", "l", "minecraft:chests/jungle_temple", 2, 6, True),
                ("nether", "ahc", "a", "minecraft:chests/nether_bridge", 2, 5, False)]
    for key, class_name, field, table, min_rolls, max_rolls, book in mappings:
        text = (ROOT / f".reference/r196/decompiled/{class_name}.java").read_text(encoding="utf-8")
        array = re.search(r"mk\[\] " + field + r" = new mk\[\]\{(.*?)\};", text).group(1)
        raw = re.findall(r"new mk\(([^,]+), (\d+), (\d+), (\d+), (\d+)\)", array)
        total_weight = sum(int(row[4]) for row in raw) + int(book)
        entries = []
        for symbol, subtype, lower, upper, weight in raw:
            legacy = symbol_ids.get(symbol.removesuffix(".cv"))
            if legacy in gear_ids:
                entries.append({"item": MAP[legacy], "legacy_id": legacy, "minimum": int(lower), "maximum": int(upper), "weight": int(weight)})
        pools[key] = {"source_class": class_name, "source_field": field, "modern_table": table,
                      "minimum_rolls": min_rolls, "maximum_rolls": max_rolls,
                      "source_total_weight": total_weight, "enchantment_book_weight_included": int(book), "entries": entries}
    write("src/main/resources/mite/armor-loot-027.json", {"author": "NingMeng123", "pools": pools})
    write("spec/armor-loot-027.json", {"author": "NingMeng123", "pools": pools,
          "adaptation": "Retain modern non-equipment loot. Replace only managed source humanoid armor, iron/gold horse armor, and source chains with source weighted gear outcomes; modern diamond/netherite/horse armor remains. Source rolls and denominators include non-gear empty outcomes and the extra enchanted-book weight. Does not reproduce old random-slot overwrites, early-day non-gear retries or unique book artifacts. Underworld simple dungeon uses its own source pool. Mithril/adamantium barding receives no invented survival source."})
    print(f"Armor: {len(items)} definitions ({len(catalog_ids)} catalog), chains: {len(chains)}, source recipes: {len(recipes)}, textures: {len(texture_manifest)}")


if __name__ == "__main__":
    main()
