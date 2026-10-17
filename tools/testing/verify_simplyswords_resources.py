"""Audit optional Simply Swords resources and material redraws. Author: NingMeng123."""

from __future__ import annotations

import argparse
from io import BytesIO
import json
from pathlib import Path
import zipfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
MATERIALS = {"rusted_iron", "copper", "silver", "ancient_metal", "mithril", "adamantium", "vibranium"}
FAMILIES = {"longsword", "twinblade", "rapier", "katana", "sai", "spear", "glaive", "warglaive",
            "cutlass", "claymore", "greataxe", "greathammer", "chakram", "scythe", "halberd"}


def read(path):
    return json.loads((RESOURCES / path).read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    manifest = read("mite/simplyswords-compat-assets.json")
    entries = manifest["items"]
    assert len(entries) == 105 and len({entry["id"] for entry in entries}) == 105
    assert {entry["material"] for entry in entries} == MATERIALS
    assert {entry["family"] for entry in entries} == FAMILIES
    assert manifest["developer"] == "NingMeng123"
    expected_ids = {entry["id"] for entry in entries}
    en = read("assets/mite/lang/en_us.json")
    zh = read("assets/mite/lang/zh_cn.json")
    shapes = {}
    textures = {family: set() for family in FAMILIES}
    recipe_count = 0
    with zipfile.ZipFile(args.archive) as archive:
        for entry in entries:
            item_id, material, family = entry["id"], entry["material"], entry["family"]
            path = item_id.split(":")[1]
            assert item_id == f"mite:simplyswords_{material}_{family}"
            assert en[f"item.mite.{path}"] and zh[f"item.mite.{path}"]
            model = read(f"assets/mite/models/item/{path}.json")
            assert model["parent"] == f"simplyswords:item/iron_{family}"
            assert f"assets/simplyswords/models/item/iron_{family}.json" in archive.namelist()
            for texture in model["textures"].values():
                assert texture.startswith("mite:item/")
                assert (RESOURCES / f"assets/mite/textures/item/{texture.split(':item/')[1]}.png").is_file()
            texture = Image.open(RESOURCES / f"assets/mite/textures/item/{path}.png").convert("RGBA")
            source = Image.open(BytesIO(archive.read(f"assets/simplyswords/textures/item/iron_{family}.png"))).convert("RGBA")
            gold = Image.open(BytesIO(archive.read(f"assets/simplyswords/textures/item/gold_{family}.png"))).convert("RGBA")
            assert texture.size == source.size
            assert texture.tobytes() != source.tobytes(), f"Missing material redraw: {item_id}"
            alpha = texture.getchannel("A").tobytes()
            assert alpha == source.getchannel("A").tobytes(), f"Changed source silhouette: {item_id}"
            for y in range(source.height):
                for x in range(source.width):
                    if not source.getpixel((x,y))[3] or source.getpixel((x,y))[:3] == gold.getpixel((x,y))[:3]:
                        assert texture.getpixel((x,y)) == source.getpixel((x,y)), f"Changed grip or decoration: {item_id} ({x},{y})"
            assert family not in shapes or shapes[family] == alpha
            shapes[family] = alpha
            textures[family].add(texture.tobytes())
            attributes = read(f"data/mite/weapon_attributes/{path}.json")
            parent = attributes["parent"].split(":")[1]
            assert f"data/simplyswords/weapon_attributes/{parent}.json" in archive.namelist()
            recipe_path = RESOURCES / f"data/mite/recipe/{path}.json"
            if material == "rusted_iron":
                assert not recipe_path.exists(), "Rusted iron is loot-only"
                continue
            recipe = json.loads(recipe_path.read_text(encoding="utf-8"))
            original = json.loads(archive.read(f"data/simplyswords/recipe/iron_{family}.json"))
            assert recipe["pattern"] == original["pattern"]
            assert recipe["result"] == {"id": item_id, "count": 1}
            assert recipe["neoforge:conditions"] == [{"type": "neoforge:mod_loaded", "modid": "simplyswords"}]
            for key, value in original["key"].items():
                expected = value.copy()
                if value.get("item") == "minecraft:iron_ingot":
                    expected["item"] = f"{'minecraft' if material == 'copper' else 'mite'}:{material}_ingot"
                elif value.get("item") == "minecraft:iron_nugget":
                    expected["item"] = f"mite:{material}_nugget"
                assert recipe["key"][key] == expected
            advancement = read(f"data/mite/advancement/recipes/combat/{path}.json")
            assert advancement["rewards"]["recipes"] == [item_id]
            assert advancement["neoforge:conditions"] == recipe["neoforge:conditions"]
            recipe_count += 1
    assert recipe_count == 90
    assert all(len(variants) == len(MATERIALS) for variants in textures.values()), "A material lacks its own colors"
    general = read("data/mite/tags/item/simplyswords_weapons.json")
    assert {entry["id"] for entry in general["values"]} == expected_ids
    for directory in ("data/mite/tags/item/simplyswords", "data/simplyswords/tags/item", "data/c/tags/item"):
        for path in (RESOURCES / directory).rglob("*.json"):
            tag = json.loads(path.read_text(encoding="utf-8"))
            references = [value for value in tag["values"] if (value if isinstance(value, str) else value["id"])
                          .startswith(("mite:simplyswords_", "simplyswords:"))]
            if not references:
                continue
            assert tag.get("replace") is False
            assert all(isinstance(value, dict) and value.get("required") is False for value in references), path
    vampiric = {value["id"] for value in read("data/mite/tags/item/simplyswords/enchantable/vampiric.json")["values"]}
    assert not any(item.startswith(("mite:simplyswords_silver_", "mite:simplyswords_mithril_")) for item in vampiric)
    assert not list((RESOURCES / "assets/mite/models/item").glob("simplyswords_gold_*.json"))
    print("Verified 105 material redraws with exact source silhouettes and preserved grips, 90 conditional recipes and unlocks, optional tags and combat parents.")


if __name__ == "__main__":
    main()
