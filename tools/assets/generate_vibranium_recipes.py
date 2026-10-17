"""Project source-native vibranium upgrade recipes. Author: NingMeng123.

Pinned shapes: MITE-ITE 2.0.25 and Extreme LH 0.6.6. Source components
are preserved by mite:vibranium_upgrade. See docs/vibranium-source.md.
"""
from pathlib import Path
import argparse
import json

ROOT = Path(__file__).resolve().parents[2]
INGOT = 51200.0
RECIPES = {
    "helmet": (["IAI", "I I"], "adamantium_helmet"),
    "chestplate": (["I I", "IAI", "III"], "adamantium_chestplate"),
    "leggings": (["IAI", "I I", "I I"], "adamantium_leggings"),
    "boots": (["A I", "I I"], "adamantium_boots"),
    "pickaxe": (["III", " A ", " S "], "adamantium_pickaxe"),
    "sword": (["I", "I", "A"], "adamantium_sword"),
    "shovel": (["I", "A", "S"], "ancient_metal_shovel"),
    "axe": (["II", "AI", "S "], "ancient_metal_axe"),
    "dagger": (["I", "A"], "ancient_metal_dagger"),
    "war_hammer": (["III", "IAI", " S "], "ancient_metal_war_hammer"),
    "battle_axe": (["I I", "IAI", " S "], "ancient_metal_battle_axe"),
    "bow": (["NSL", "AIL", "NSL"], "ancient_metal_bow"),
}


def generate(check: bool):
    difficulties = {}
    for name in ["quality-equipment-017.json", "quality-equipment-027-armor.json", "quality-equipment-028-weapons.json"]:
        data = json.loads((ROOT / "src/main/resources/mite" / name).read_text(encoding="utf-8"))
        difficulties.update({k: v["lowest_difficulty"] for k, v in data["items"].items()})
    metadata = {}
    emitted = 0

    def put(relative, value):
        nonlocal emitted
        path = ROOT / "src/main/resources" / relative
        raw = (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()
        if check:
            assert path.read_bytes() == raw, f"Outdated vibranium recipe: {path}"
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
        emitted += 1

    for family, (pattern, base) in RECIPES.items():
        item = "mite:vibranium_" + family
        joined = "".join(pattern)
        possible = {"I": "mite:vibranium_ingot", "N": "mite:vibranium_nugget", "S": "mite:obsidian_stick", "L": "minecraft:string", "A": "mite:" + base}
        keys = {k: {"item": v} for k, v in possible.items() if k in joined}
        recipe = {"type": "mite:vibranium_upgrade", "category": "equipment", "pattern": pattern, "key": keys, "result": {"id": item, "count": 1}}
        put("data/mite/recipe/vibranium_" + family + ".json", recipe)
        put("data/mite/advancement/recipes/combat/vibranium_" + family + ".json", {
            "parent": "minecraft:recipes/root", "criteria": {
                "has_vibranium": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": ["mite:vibranium_ingot"]}]}},
                "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": item}}},
            "requirements": [["has_vibranium", "has_the_recipe"]], "rewards": {"recipes": [item]}})
        cost = (joined.count("I") * INGOT + joined.count("N") * INGOT / 9 + joined.count("S") * 520 + joined.count("L") * 25 + difficulties["mite:" + base])
        metadata[item] = {"base": "mite:" + base, "lowest_difficulty": cost, "vibranium_ingots": joined.count("I"), "preserves_quality": True}
    put("data/mite/recipe/obsidian_stick.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["O", "O"], "key": {"O": {"item": "minecraft:obsidian"}}, "result": {"id": "mite:obsidian_stick", "count": 1}})
    put("mite/vibranium-upgrades.json", {"author": "NingMeng123", "items": metadata})
    print(f"{'Verified' if check else 'Generated'} {emitted} vibranium upgrade resources")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    generate(parser.parse_args().check)
