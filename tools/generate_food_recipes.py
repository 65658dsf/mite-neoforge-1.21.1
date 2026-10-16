"""Generate the active R196 food recipes and their vessel dependencies. Author: NingMeng123."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
REFERENCE = ROOT / "reference-data/runtime-registry.json"

NATIVE = {
    37: "dandelion", 39: "brown_mushroom", 40: "red_mushroom", 86: "pumpkin",
    260: "apple", 265: "iron_ingot", 266: "gold_ingot", 281: "bowl", 282: "mushroom_stew",
    295: "wheat_seeds", 296: "wheat", 297: "bread", 319: "porkchop", 320: "cooked_porkchop",
    322: "golden_apple", 325: "bucket", 326: "water_bucket", 332: "snowball", 335: "milk_bucket",
    338: "sugar_cane", 344: "egg", 349: "cod", 350: "cooked_cod", 353: "sugar", 354: "cake",
    357: "cookie", 360: "melon_slice", 361: "pumpkin_seeds", 362: "melon_seeds", 363: "beef",
    364: "cooked_beef", 365: "chicken", 366: "cooked_chicken", 371: "gold_nugget",
    384: "experience_bottle", 391: "carrot", 392: "potato", 393: "baked_potato",
    396: "golden_carrot", 400: "pumpkin_pie", 962: "copper_ingot", 1172: "mutton", 1173: "cooked_mutton",
}
CUSTOM = {
    963: "silver_ingot", 964: "mithril_ingot", 965: "adamantium_ingot", 966: "ancient_metal_ingot",
    1166: "milk_bowl", 1167: "water_bowl", 1183: "cheese", 1184: "flour", 1185: "dough",
    1186: "chocolate", 1187: "onion", 1188: "beef_stew", 1189: "chicken_soup", 1190: "vegetable_soup",
    1206: "ice_cream", 1209: "salad", 1215: "cream_of_mushroom_soup", 1216: "cream_of_vegetable_soup",
    1217: "pumpkin_soup", 1218: "orange", 1219: "banana", 1223: "mashed_potato", 1224: "sorbet",
    1225: "blueberries", 1226: "porridge", 1227: "cereal", 1229: "raw_large_fish", 1230: "cooked_large_fish",
    1280: "raw_worm", 1281: "cooked_worm",
}
METALS = ["copper", "silver", "gold", "mithril", "adamantium", "ancient_metal"]
for index, metal in enumerate(METALS):
    CUSTOM[1142 + index] = metal + "_bucket"
    CUSTOM[1148 + index] = metal + "_water_bucket"
    CUSTOM[1160 + index] = metal + "_milk_bucket"

BOWL_OUTPUTS = {282, 1166, 1167, 1188, 1189, 1190, 1206, 1209, 1215, 1216, 1217, 1223, 1224, 1226, 1227}
EMPTY_BUCKETS = {325, *range(1142, 1148)}
WATER_BUCKETS = {326, *range(1148, 1154)}


def modern_id(stack: dict) -> str:
    item_id = stack["item_id"]
    subtype = stack["subtype"]
    if item_id == 351 and subtype == 3:
        return "minecraft:cocoa_beans"
    if item_id == 322 and subtype == 1:
        return "minecraft:enchanted_golden_apple"
    if subtype not in (0, 32767):
        raise ValueError(f"Unmapped subtype {item_id}:{subtype}")
    if item_id in NATIVE:
        return "minecraft:" + NATIVE[item_id]
    return "mite:" + CUSTOM[item_id]


def write(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def crafting_json(source: dict) -> dict:
    shaped = source["class"] == "aai"
    contains_bowl = source["output"]["item_id"] in BOWL_OUTPUTS
    bench = source["bench_material"]
    recipe = {
        "type": "mite:food_shaped" if shaped else "mite:food_shapeless",
        # CraftingBookCategory has no FOOD category; that category exists only for cooking.
        "category": "misc",
        "result": {"id": modern_id(source["output"]), "count": source["output"]["count"]},
        "manufacturing": {
            "difficulty": source["difficulty"],
            "required_bench": bench["name"] if bench else "",
            "source_order": source["order"],
            "output_contains_bowl": contains_bowl,
            "any_milk_buckets": not shaped and all(stack["item_id"] in {335, *range(1160, 1166)} for stack in source["components"]),
        },
    }
    if not shaped:
        recipe["ingredients"] = [{"item": modern_id(stack)} for stack in source["components"]]
        assert 1 <= len(recipe["ingredients"]) <= 9
    else:
        width, height = source["scalars"]["aai.b"], source["scalars"]["aai.c"]
        keys: dict[str, str] = {}
        pattern = []
        for row in range(height):
            line = ""
            for col in range(width):
                stack = source["components"][row * width + col]
                if stack is None:
                    line += " "
                else:
                    item = modern_id(stack)
                    if item not in keys:
                        keys[item] = chr(ord("A") + len(keys))
                    line += keys[item]
            pattern.append(line)
        recipe["pattern"] = pattern
        recipe["key"] = {symbol: {"item": item} for item, symbol in keys.items()}
    return recipe


def main() -> None:
    archive = json.loads(REFERENCE.read_text(encoding="utf-8"))
    items = {item["id"]: item for item in archive["items"]}
    # This is the original snapshot, including wheat seeds' original S1/N0; user food overrides
    # do not alter which historical manufacturing recipes are part of the evidence.
    food_ids = {item_id for item_id, item in items.items()
                if item["scalars"].get("yc.satiation", 0) > 0 or item["scalars"].get("yc.nutrition", 0) > 0}
    food_recipes = [recipe for recipe in archive["crafting_recipes"]
                    if recipe.get("output") and recipe["output"]["item_id"] in food_ids]
    dependencies = {354, 1184, 1167} | WATER_BUCKETS | EMPTY_BUCKETS
    recipes = [recipe for recipe in archive["crafting_recipes"]
               if recipe.get("output") and (recipe["output"]["item_id"] in food_ids
                   or recipe["output"]["item_id"] in dependencies)
               # Stone-filled bucket dismantling is an independent material system, not a food vessel recipe.
               and not (recipe["output"]["item_id"] in EMPTY_BUCKETS and recipe["class"] != "aai")]
    assert len(food_recipes) == 202, len(food_recipes)
    assert len(recipes) == 254, len(recipes)
    evidence = {}
    for source in recipes:
        recipe = crafting_json(source)
        name = "food_019_" + str(source["order"])
        write(RESOURCES / "data/mite/recipe" / (name + ".json"), recipe)
        evidence["mite:" + name] = {
            **recipe["manufacturing"],
            "output": recipe["result"],
            "source_class": source["class"],
            "source_output": source["output"],
            "source_components": source["components"],
            "skillsets": source["skillsets"],
            "include_in_lowest_difficulty": source["include_in_lowest_difficulty"],
        }

    disabled = ["bread", "cookie", "cake", "pumpkin_pie", "mushroom_stew", "golden_apple", "golden_carrot",
                "melon_seeds", "pumpkin_seeds", "sugar", "bucket"]
    for name in disabled:
        write(RESOURCES / "data/minecraft/recipe" / (name + ".json"), {
            "neoforge:conditions": [{"type": "neoforge:false"}],
            "type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:barrier"}],
            "result": {"id": "minecraft:barrier"},
        })

    furnace = [recipe for recipe in archive["furnace_recipes"] if recipe["output"]["item_id"] in food_ids]
    assert len(furnace) == 9
    cooking = []
    for source in furnace:
        input_id = modern_id({"item_id": source["input_item_id"], "subtype": 0})
        output_id = modern_id(source["output"])
        xp = items[source["output"]["item_id"]]["scalars"]["yc.xp_reward"]
        entry = {"input": input_id, "output": output_id, "experience": xp, "cooking_time": 200,
                 "modern_device_times": {"smelting": 200, "smoking": 100, "campfire_cooking": 200},
                 "source_required_heat": source["required_heat"], "recipe_ids": []}
        for method, suffix in [("smelting", ""), ("smoking", "_from_smoking"), ("campfire_cooking", "_from_campfire_cooking")]:
            # Existing native recipe IDs are overridden, so their former time/XP cannot bypass this data.
            native = output_id.startswith("minecraft:") and source["input_item_id"] != 1185
            namespace = "minecraft" if native else "mite"
            name = output_id.split(":")[1] + suffix if native else "food_019_cook_" + str(source["input_item_id"]) + "_" + method
            # Historical019 evidence below keeps the original200-tick mapping. Live
            # modern campfire adaptations belong exclusively to the Java data provider,
            # which also removes non-meat recipe pages and declares the user's15s time.
            # Do not recreate src/main copies beside src/generated campfire assets.
            if method != "campfire_cooking":
                write(RESOURCES / "data" / namespace / "recipe" / (name + ".json"), {
                    "type": "minecraft:" + method, "category": "food", "ingredient": {"item": input_id},
                    "result": {"id": output_id, "count": 1}, "experience": float(xp), "cookingtime": 100 if method == "smoking" else 200,
                })
            entry["recipe_ids"].append(namespace + ":" + name)
        cooking.append(entry)
    write(RESOURCES / "mite/food-manufacturing-019.json", {
        "author": "NingMeng123", "archive_sha256": archive["archive_sha256"],
        "original_food_recipe_count": 202, "dependency_recipe_count": 52,
        "excluded_stone_bucket_dismantling_orders": list(range(887, 894)),
        "recipes": evidence, "cooking": cooking,
        "modern_adaptations": ["Source food recipes require heat1; furnace heat/fuel enforcement is provided by024.",
            "Source furnaces and campfires use200-tick food recipes; modern smokers explicitly use100ticks in024.",
            "Native recipe history remains for unlocking; source cooking XP follows actual player and hopper withdrawals, without a duplicate bank. Unmapped modern results retain native experience.",
            "Skills remain disabled like the default R196 mode; skillsets remain recorded as evidence."]
    })
    print(f"Generated {len(recipes)} crafting recipes, {len(furnace)} source cooking recipes across 3 devices, {len(disabled)} disabled conflicts")


if __name__ == "__main__":
    main()
