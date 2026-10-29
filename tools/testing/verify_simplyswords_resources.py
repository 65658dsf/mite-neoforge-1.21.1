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
RETIRED_NATIVE_IDS = {f"simplyswords:{material}_{family}"
                      for material in ("diamond", "netherite") for family in FAMILIES}
TWO_HANDED_FAMILIES = {"twinblade", "katana", "glaive", "claymore", "greataxe", "greathammer", "scythe", "halberd"}


def read(path):
    return json.loads((RESOURCES / path).read_text(encoding="utf-8"))


def verify_chakram_ranges(archive, entries, manifest):
    """Compare every upstream chakram, including optional integrations, independently."""
    paths = []
    for location in sorted(archive.namelist()):
        if not location.startswith("data/simplyswords/weapon_attributes/") or not location.endswith(".json"):
            continue
        original = json.loads(archive.read(location))
        if original.get("attributes", {}).get("category") != "chakram" and not Path(location).stem.endswith("_chakram"):
            continue
        expected = json.loads(archive.read(location))
        expected.setdefault("attributes", {}).update({"attack_range": 4.25, "range_bonus": 0})
        assert read(location) == expected, f"Chakram range override changed its original moveset: {location}"
        paths.append(location)
    assert len(paths) == 30, f"Review new upstream chakram coverage: {paths}"
    for entry in entries:
        if entry["family"] == "chakram":
            location = f"data/mite/weapon_attributes/{entry['id'].split(':', 1)[1]}.json"
            assert read(location) == {"parent": "simplyswords:iron_chakram",
                                      "attributes": {"attack_range": 4.25, "range_bonus": 0}}, location
    assert manifest["chakram_balance"] == {
        "damage_multiplier_of_previous_base_total": .8,
        "attack_range": 4.25,
        "native_profile_overrides": paths,
    }
    return len(paths) + sum(entry["family"] == "chakram" for entry in entries)


def verify_two_handed(archive, entries):
    """Independently audit declarations, inherited flags and known exceptions."""
    expected = {entry["id"] for entry in entries if entry["family"] in TWO_HANDED_FAMILIES}
    assert len(expected) == 56
    native_ids, visited = set(), set()

    def visit(tag):
        if tag in visited:
            return
        visited.add(tag)
        namespace, path = tag.split(":", 1)
        location = f"data/{namespace}/tags/item/{path}.json"
        if location not in archive.namelist():
            return
        for value in json.loads(archive.read(location))["values"]:
            item_id = value if isinstance(value, str) else value["id"]
            if item_id.startswith("#"):
                visit(item_id[1:])
            elif item_id.startswith("simplyswords:"):
                native_ids.add(item_id)

    visit("simplyswords:swords")
    native_ids.update("simplyswords:" + Path(path).stem for path in archive.namelist()
                      if path.startswith("data/simplyswords/weapon_attributes/")
                      and path.count("/") == 3 and path.endswith(".json"))
    native_ids.difference_update(RETIRED_NATIVE_IDS)
    # Effective values checked against Better Combat 2.4.0+1.21.1. Its trident
    # explicitly turns off the two-handed spear parent; SS spears use trident.
    two_handed_parents = {"bettercombat:claymore", "bettercombat:glaive", "bettercombat:heavy_axe",
                          "bettercombat:katana", "bettercombat:scythe", "bettercombat:spear"}
    one_handed_parents = {"bettercombat:rapier", "bettercombat:sickle", "bettercombat:trident"}
    for item_id in native_ids:
        location = f"data/simplyswords/weapon_attributes/{item_id.split(':', 1)[1]}.json"
        if location in archive.namelist():
            profile = json.loads(archive.read(location))
            parent = profile.get("parent")
            assert parent is None or parent in two_handed_parents | one_handed_parents, parent
            flag = profile.get("attributes", {}).get("two_handed", parent in two_handed_parents)
        else:
            flag = item_id.endswith("_scythe")  # Better Combat's missing-profile fallback.
        if flag:
            expected.add(item_id)
    tag = read("data/mite/tags/item/simplyswords/two_handed.json")
    actual = {value["id"] for value in tag["values"]}
    assert len(tag["values"]) == len(actual), "Duplicate two-handed weapons"
    assert actual == expected, f"Two-handed tag: missing {expected - actual}, unexpected {actual - expected}"
    assert {"simplyswords:iron_scythe", "simplyswords:electric_claymore", "simplyswords:sword_on_a_stick"} <= actual
    assert not {"simplyswords:iron_spear", "simplyswords:magispear", "simplyswords:mjolnir",
                "simplyswords:soulrender", "simplyswords:soulstalker", "simplyswords:iron_warglaive"} & actual
    return len(actual)


def verify_retired_native_tags(archive):
    """Keep retired native swords out of upstream unions and compatibility tags."""
    for directory in ("data/mite/tags/item/simplyswords", "data/simplyswords/tags/item", "data/c/tags/item"):
        for path in (RESOURCES / directory).rglob("*.json"):
            tag = json.loads(path.read_text(encoding="utf-8"))
            ids = {value if isinstance(value, str) else value["id"] for value in tag["values"]}
            assert not ids & RETIRED_NATIVE_IDS, f"Retired weapon remains in {path}"

    targets = {f"data/c/tags/item/{tag}.json": RETIRED_NATIVE_IDS
               for tag in ("swords", "tools", "tools/melee_weapon")}
    targets["data/simplyswords/tags/item/swords.json"] = RETIRED_NATIVE_IDS
    for material in ("diamond", "netherite"):
        targets[f"data/simplyswords/tags/item/{material}_gear.json"] = {
            f"simplyswords:{material}_{family}" for family in FAMILIES}
    targets["data/simplyswords/tags/item/spears.json"] = {
        "simplyswords:diamond_spear", "simplyswords:netherite_spear"}
    for family in FAMILIES:
        targets[f"data/simplyswords/tags/item/implicit/{family}.json"] = {
            f"simplyswords:diamond_{family}", f"simplyswords:netherite_{family}"}
    for weight in ("light", "medium", "heavy"):
        path = f"data/simplyswords/tags/item/{weight}_weapons.json"
        upstream = json.loads(archive.read(path))
        targets[path] = {value if isinstance(value, str) else value["id"] for value in upstream["values"]} & RETIRED_NATIVE_IDS
    for path, expected in targets.items():
        tag = read(path)
        removed = tag.get("remove", [])
        assert {value["id"] for value in removed} == expected, f"Native diamond/netherite exclusion: {path}"
        assert all(value.get("required") is False for value in removed), f"Optional mod removal must be optional: {path}"


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
    assert set(manifest["retired_native_weapon_ids"]) == RETIRED_NATIVE_IDS
    expected_ids = {entry["id"] for entry in entries}
    en = read("assets/mite/lang/en_us.json")
    zh = read("assets/mite/lang/zh_cn.json")
    shapes = {}
    textures = {family: set() for family in FAMILIES}
    recipe_count = 0
    with zipfile.ZipFile(args.archive) as archive:
        chakram_count = verify_chakram_ranges(archive, entries, manifest)
        two_handed_count = verify_two_handed(archive, entries)
        verify_retired_native_tags(archive)
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
    print(f"Verified 105 material redraws with exact source silhouettes and preserved grips, 90 conditional recipes and unlocks, optional tags and combat parents, {two_handed_count} two-handed weapon entries, {chakram_count} fixed-range chakram profiles with preserved movesets.")


if __name__ == "__main__":
    main()
