"""Check vibranium native hashes, extension masks and generated references. Author: NingMeng123."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RESOURCE_ROOTS = (ROOT / "src/main/resources", ROOT / "src/generated/resources")
TOOLS = ("hatchet", "axe", "pickaxe", "shovel", "hoe", "mattock", "scythe", "shears",
         "dagger", "knife", "sword", "battle_axe", "war_hammer")
ARMOR = ("helmet", "chestplate", "leggings", "boots")
ARROWS = ("flint", "obsidian", "copper", "silver", "rusted_iron", "gold", "iron", "mithril",
          "adamantium", "ancient_metal")


def resource(relative):
    for directory in RESOURCE_ROOTS:
        path = directory / relative
        if path.is_file():
            return path
    raise AssertionError(f"Missing resource: {relative}; run datagen after compiling")


def read(relative):
    return json.loads(resource(relative).read_text(encoding="utf-8"))


def grey_metal(color):
    red, green, blue, alpha = color
    return alpha > 0 and max(red, green, blue) - min(red, green, blue) <= 12 and red + green + blue > 0


def extension_checks():
    manifest = read("mite/vibranium-extension-assets.json")
    assert manifest["developer"] == "NingMeng123"
    assert {entry["kind"] for entry in manifest["assets"]} == {"shield", "shears"}
    counts = {}
    for entry in manifest["assets"]:
        destination = ROOT / entry["destination"]
        assert hashlib.sha256(destination.read_bytes()).hexdigest() == entry["sha256"]
        source = Image.open(ROOT / entry["source"]).convert("RGBA")
        target = Image.open(destination).convert("RGBA")
        comparison = Image.open(ROOT / entry["mask_reference"]).convert("RGBA") if entry["mask_reference"] else None
        assert source.size == target.size == (entry["width"], entry["height"])
        assert source.getchannel("A").tobytes() == target.getchannel("A").tobytes(), "Changed native alpha"
        changed = 0
        for y in range(source.height):
            for x in range(source.width):
                original, actual = source.getpixel((x, y)), target.getpixel((x, y))
                eligible = grey_metal(original)
                if comparison is not None:
                    eligible = eligible and original != comparison.getpixel((x, y))
                if not eligible:
                    assert actual == original, f"Changed handle/wood/UV: {entry['kind']} ({x}, {y})"
                changed += original != actual
        assert changed == entry["changed_metal_pixels"] > 0
        counts[entry["kind"]] = changed
    return counts


def model_checks():
    visited = set()
    texture_count = 0

    def model(model_id):
        nonlocal texture_count
        namespace, name = model_id.split(":", 1)
        if namespace != "mite" or model_id in visited:
            return
        visited.add(model_id)
        data = read(f"assets/{namespace}/models/{name}.json")
        for texture in data.get("textures", {}).values():
            if texture.startswith("mite:"):
                resource(f"assets/mite/textures/{texture.split(':', 1)[1]}.png")
                texture_count += 1
        if data.get("parent", "").startswith("mite:"):
            model(data["parent"])
        for override in data.get("overrides", []):
            model(override["model"])

    item_names = [f"vibranium_{family}" for family in TOOLS + ARMOR]
    item_names += ["vibranium_bow", "vibranium_shield", "vibranium_ingot", "vibranium_nugget", "obsidian_stick",
                   "vibranium_crafting_table", "vibranium_anvil", "vibranium_block"]
    for item in item_names:
        model(f"mite:item/{item}")
    for name in ("vibranium_crafting_table", "vibranium_anvil", "vibranium_block"):
        state = read(f"assets/mite/blockstates/{name}.json")

        def references(value):
            if isinstance(value, dict):
                if "model" in value:
                    model(value["model"])
                for nested in value.values():
                    references(nested)
            elif isinstance(value, list):
                for nested in value:
                    references(nested)

        references(state)
    bow = read("assets/mite/models/item/vibranium_bow.json")
    assert len(bow["overrides"]) == len(ARROWS) * 3, "Missing bow draw/ammunition variants"
    for material in ARROWS:
        for stage in range(3):
            model(f"mite:item/vibranium_bow_{material}_{stage}")
    resource("assets/mite/textures/models/armor/vibranium_layer_1.png")
    resource("assets/mite/textures/models/armor/vibranium_layer_2.png")
    resource("assets/mite/textures/entity/shield/vibranium.png")
    return len(visited), texture_count


def recipe_and_language_checks():
    names = [f"vibranium_{family}" for family in TOOLS + ARMOR]
    names += ["vibranium_bow", "vibranium_shield", "vibranium_crafting_table", "vibranium_anvil", "vibranium_ingot"]
    languages = [read(f"assets/mite/lang/{language}.json") for language in ("en_us", "zh_cn")]
    for name in names:
        recipe = read(f"data/mite/recipe/{name}.json")
        assert recipe["result"]["id"] == "mite:" + name, f"Wrong recipe output: {name}"
        key = ("block" if name.endswith(("_crafting_table", "_anvil")) else "item") + ".mite." + name
        assert all(language.get(key) for language in languages), f"Missing localized name: {key}"
    return len(names)


def fire_registration_source_checks():
    # Static evidence only. Actual item components are exercised by the equipment GameTests.
    base = ROOT / "src/main/java/io/github/ningmeng123/mite/registry"
    for filename in ("MiteTools.java", "MiteArmorContent.java", "MiteShields.java", "MiteWeaponContent.java", "MiteMaterials.java"):
        source = (base / filename).read_text(encoding="utf-8")
        assert "vibranium" in source.lower() and "fireResistant()" in source, f"Missing fire-resistant registration: {filename}"
    return "registration source audit; runtime components require GameTests"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--textures-only", action="store_true", help="Validate source hashes and pixel masks before datagen")
    args = parser.parse_args()
    subprocess.run([sys.executable, str(ROOT / "tools/assets/import_vibranium_assets.py"), "--verify"], check=True)
    result = {"developer": "NingMeng123", "native_asset_hashes": "passed", "extension_changed_metal_pixels": extension_checks()}
    if not args.textures_only:
        result["models"], result["texture_references"] = model_checks()
        result["recipes_and_localized_items"] = recipe_and_language_checks()
        result["fire_resistance"] = fire_registration_source_checks()
    result["status"] = "passed"
    report = ROOT / "build/verification/vibranium-resources.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
