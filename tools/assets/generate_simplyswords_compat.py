"""Generate the optional Simply Swords compatibility assets. Author: NingMeng123.

Usage: python tools/assets/generate_simplyswords_compat.py path/to/simplyswords.jar
Pass --check to audit the checked-in files without changing them. Requires Pillow.
New material textures repaint the original Simply Swords metal pixels while
retaining its exact silhouettes, shade structure, grips and model UVs. Source
artwork and templates belong to Simply Swords; material redraw code and MITE
integration are by NingMeng123. See the generated source-attribution manifest.
"""

from __future__ import annotations

import argparse
from copy import deepcopy
import hashlib
from io import BytesIO
import json
from pathlib import Path
import re
import zipfile

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
FAMILIES = {
    "longsword": ("Longsword", "长剑"),
    "twinblade": ("Twinblade", "双头刃"),
    "rapier": ("Rapier", "细剑"),
    "katana": ("Katana", "武士刀"),
    "sai": ("Sai", "钗"),
    "spear": ("Spear", "长矛"),
    "glaive": ("Glaive", "长柄刀"),
    "warglaive": ("Warglaive", "战刃"),
    "cutlass": ("Cutlass", "弯刀"),
    "claymore": ("Claymore", "巨剑"),
    "greataxe": ("Greataxe", "巨斧"),
    "greathammer": ("Greathammer", "巨锤"),
    "chakram": ("Chakram", "环刃"),
    "scythe": ("Scythe", "战镰"),
    "halberd": ("Halberd", "戟"),
}
RETIRED_NATIVE_IDS = {f"simplyswords:netherite_{family}" for family in FAMILIES}
MATERIALS = {
    "rusted_iron": ("Rusted Iron", "锈铁"),
    "copper": ("Copper", "铜"),
    "silver": ("Silver", "银"),
    "ancient_metal": ("Ancient Metal", "远古金属"),
    "mithril": ("Mithril", "秘银"),
    "adamantium": ("Adamantium", "艾德曼"),
    "vibranium": ("Vibranium", "振金"),
}
# Existing-metal shades come from MITE sword sprites; vibranium shades come
# from the generated palette study in docs/art/vibranium-palette.png. The
# source iron/gold difference identifies the original mod's metal region.
STOPS = (24, 68, 107, 150, 190, 216, 255)
PALETTES = {
    "rusted_iron": ((24,24,24),(68,68,68),(107,107,107),(135,120,111),(165,148,137),(189,178,171),(202,195,190)),
    "copper": ((40,0,0),(114,8,0),(107,43,0),(150,86,0),(255,145,1),(255,171,35),(255,220,145)),
    "silver": ((87,87,87),(132,132,132),(139,139,139),(177,177,177),(210,210,210),(220,220,220),(255,255,255)),
    "ancient_metal": ((30,38,26),(78,89,73),(97,105,93),(140,148,136),(189,197,185),(214,225,209),(246,254,242)),
    "mithril": ((41,51,61),(68,84,100),(86,105,124),(128,147,166),(184,213,230),(203,234,253),(255,255,255)),
    "adamantium": ((3,3,45),(22,23,104),(78,79,114),(120,120,162),(164,165,232),(180,180,234),(235,235,255)),
    "vibranium": ((39,35,58),(61,48,85),(75,74,97),(97,96,119),(102,122,163),(116,207,253),(209,251,247)),
}
CONDITIONS = [{"type": "neoforge:mod_loaded", "modid": "simplyswords"}]
SWORD_FAMILIES = set(FAMILIES) - {"sai", "greataxe", "halberd", "greathammer", "scythe"}
# Matches SimplySwordsCompat.durabilityFamily and R196EnchantmentCatalog.supported.
ENCHANT_FAMILIES = {
    "sharpness": SWORD_FAMILIES | {"sai", "greataxe", "halberd", "scythe"},
    "smite": {"greathammer"},
    "bane_of_arthropods": SWORD_FAMILIES | {"sai"},
    "fire_aspect": SWORD_FAMILIES | {"sai"},
    "knockback": {"greathammer"},
    "stun": {"greathammer"},
    "looting": SWORD_FAMILIES | {"sai"},
    "efficiency": {"greataxe", "halberd"},
    "silk_touch": {"sai"},
    "unbreaking": {"greataxe", "halberd", "greathammer"},
    "fertility": {"scythe"},
    "harvesting": {"scythe"},
    "tree_felling": {"greataxe", "halberd"},
    "vampiric": SWORD_FAMILIES | {"sai", "scythe"},
    "disarming": SWORD_FAMILIES,
    "piercing": {"greataxe", "halberd"},
    "butchering": {"sai"},
}
NATIVE_SPECIAL_FAMILY = {
    "soulrender": "scythe", "soulstalker": "scythe", "magiscythe": "scythe",
    "soulpyre": "greataxe", "molten_edge": "greataxe", "livyatan": "greataxe",
    "frostfall": "greathammer", "mjolnir": "greathammer", "hearthflame": "greathammer",
    "soulkeeper": "greathammer", "hiveheart": "greathammer",
    "icewhisper": "halberd", "arcanethyst": "halberd", "thunderbrand": "halberd",
}
# Effective two_handed values from Better Combat 2.4.0+1.21.1, limited to
# parents referenced by Simply Swords 1.70.2. Keeping these here preserves the
# generator's single-archive interface. A child profile can override either
# value: notably trident overrides spear to false, as does SS magispear.
BETTER_COMBAT_TWO_HANDED = {
    "bettercombat:claymore": True,
    "bettercombat:glaive": True,
    "bettercombat:heavy_axe": True,
    "bettercombat:katana": True,
    "bettercombat:rapier": False,
    "bettercombat:scythe": True,
    "bettercombat:sickle": False,
    "bettercombat:spear": True,
    "bettercombat:trident": False,
}


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def png_bytes(image: Image.Image) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", optimize=False)
    return output.getvalue()


def gradient(value: int, material: str) -> tuple[int, int, int]:
    colors = PALETTES[material]
    if value <= STOPS[0]:
        return colors[0]
    for index in range(1, len(STOPS)):
        if value <= STOPS[index]:
            factor = (value - STOPS[index - 1]) / (STOPS[index] - STOPS[index - 1])
            return tuple(round(a + (b - a) * factor) for a, b in zip(colors[index - 1], colors[index]))
    return colors[-1]


def redraw_material(iron: Image.Image, gold: Image.Image, material: str) -> Image.Image:
    """Preserve source art exactly, repainting only its own material-change mask."""
    assert iron.size == gold.size
    assert iron.getchannel("A").tobytes() == gold.getchannel("A").tobytes()
    image = iron.copy()
    pixels, reference = image.load(), gold.load()
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue, alpha = pixels[x, y]
            if not alpha or (red, green, blue) == reference[x, y][:3]:
                continue
            brightness = round((red + green + blue) / 3)
            color = gradient(brightness, material)
            # The original shade hierarchy and all edges survive; a few muted
            # corrosion flecks add rust without redrawing the blade silhouette.
            if material == "rusted_iron" and 150 <= brightness <= 220 and (x * 17 + y * 31) % 11 < 2:
                color = tuple(round(a * 0.8 + b * 0.2) for a, b in zip(color, (135,91,59)))
            pixels[x, y] = (*color, alpha)
    return image


def preview_sheet(source: dict[str, Image.Image], rendered: dict[tuple[str,str], Image.Image]) -> Image.Image:
    """Review-only original iron row followed by exact-shape material rows."""
    width, row_height, header, labels = 108, 114, 38, 135
    rows = ["SS iron reference", *MATERIALS]
    image = Image.new("RGB", (labels + width * len(FAMILIES), header + row_height * len(rows)), (29,32,38))
    draw = ImageDraw.Draw(image)
    for column, family in enumerate(FAMILIES):
        draw.text((labels+column*width+2,8),family if family != "greathammer" else "hammer UV",fill=(230,233,238))
    for row, material in enumerate(rows):
        draw.text((5,header+row*row_height+45),material,fill=(230,233,238))
        for column, family in enumerate(FAMILIES):
            sprite = source[family] if row == 0 else rendered[material, family]
            scale = min(96 // sprite.width, 96 // sprite.height)
            sprite = sprite.resize((sprite.width*scale,sprite.height*scale),Image.Resampling.NEAREST)
            x = labels+column*width+(width-sprite.width)//2
            y = header+row*row_height+(row_height-sprite.height)//2
            image.paste(sprite,(x,y),sprite)
    return image


def optional(ids: list[str], removed: set[str] | None = None) -> dict:
    result = {"replace": False, "values": [{"id": item_id, "required": False} for item_id in ids]}
    if removed:
        result["remove"] = [{"id": item_id, "required": False} for item_id in sorted(removed)]
    return result


def native_weapons(archive: zipfile.ZipFile) -> list[str]:
    """Resolve the upstream sword-tag union without requiring the mod at datagen time."""
    found: set[str] = set()
    seen: set[str] = set()

    def visit(tag: str) -> None:
        if tag in seen:
            return
        seen.add(tag)
        namespace, path = tag.split(":", 1)
        location = f"data/{namespace}/tags/item/{path}.json"
        if location not in archive.namelist():
            return
        for entry in json.loads(archive.read(location))["values"]:
            item_id = entry if isinstance(entry, str) else entry["id"]
            if item_id.startswith("#"):
                visit(item_id[1:])
            elif item_id.startswith("simplyswords:"):
                found.add(item_id)

    visit("simplyswords:swords")
    for location in archive.namelist():
        if location.startswith("data/simplyswords/weapon_attributes/") and location.count("/") == 3 and location.endswith(".json"):
            found.add("simplyswords:" + Path(location).stem)
    return sorted(found - RETIRED_NATIVE_IDS)


def native_family(item_id: str) -> str:
    path = item_id.split(":", 1)[1]
    return next((family for family in FAMILIES if path.endswith("_" + family)), NATIVE_SPECIAL_FAMILY.get(path, "longsword"))


def is_two_handed(archive: zipfile.ZipFile, item_id: str, parents: tuple[str, ...] = ()) -> bool:
    """Resolve the effective upstream flag, preserving explicit false overrides."""
    if item_id in parents:
        raise ValueError(f"Cyclic weapon-attribute parents: {(*parents, item_id)}")
    namespace, path = item_id.split(":", 1)
    location = f"data/{namespace}/weapon_attributes/{path}.json"
    try:
        profile = json.loads(archive.read(location))
    except KeyError:
        if item_id in BETTER_COMBAT_TWO_HANDED:
            return BETTER_COMBAT_TWO_HANDED[item_id]
        # The upstream jar omits ordinary scythe profiles. Better Combat's
        # default fallback rule resolves these item IDs to its two-handed scythe.
        if namespace == "simplyswords" and path.endswith("_scythe"):
            return True
        if parents:
            raise ValueError(f"Unresolved weapon-attribute parent: {item_id}")
        return False
    attributes = profile.get("attributes", {})
    if "two_handed" in attributes:
        value = attributes["two_handed"]
        if not isinstance(value, bool):
            raise ValueError(f"Invalid two_handed value in {location}: {value!r}")
        return value
    parent = profile.get("parent")
    return is_two_handed(archive, parent, (*parents, item_id)) if parent else False


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "src/main/resources")
    parser.add_argument("--preview", type=Path, default=ROOT / "build/reports/simplyswords-material-reference.png")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    archive_path = args.archive.resolve()
    files: dict[str, bytes] = {}
    entries = []
    ids = [f"mite:simplyswords_{metal}_{family}" for metal in MATERIALS for family in FAMILIES]
    translations = {locale: {} for locale in ("en_us", "zh_cn")}
    original_iron, original_gold, rendered = {}, {}, {}

    def put(path: str, value: object) -> None:
        files[path] = json_bytes(value)

    with zipfile.ZipFile(archive_path) as archive:
        native_ids = native_weapons(archive)
        two_handed_ids = [item_id for item_id in native_ids if is_two_handed(archive, item_id)]
        for family in FAMILIES:
            original_iron[family] = Image.open(BytesIO(archive.read(f"assets/simplyswords/textures/item/iron_{family}.png"))).convert("RGBA")
            original_gold[family] = Image.open(BytesIO(archive.read(f"assets/simplyswords/textures/item/gold_{family}.png"))).convert("RGBA")
        for material, (english_metal, chinese_metal) in MATERIALS.items():
            for family, (english_family, chinese_family) in FAMILIES.items():
                path = f"simplyswords_{material}_{family}"
                item_id = f"mite:{path}"
                source_model = json.loads(archive.read(f"assets/simplyswords/models/item/iron_{family}.json"))
                textures = {key: f"mite:item/{path}" for key in source_model["textures"]}
                put(f"assets/mite/models/item/{path}.json", {
                    "parent": f"simplyswords:item/iron_{family}",
                    "textures": textures,
                })
                rendered[material, family] = redraw_material(original_iron[family], original_gold[family], material)
                files[f"assets/mite/textures/item/{path}.png"] = png_bytes(rendered[material, family])
                # The upstream jar has no iron_scythe attributes; use its glaive
                # moveset instead of an unresolved weapon-attribute parent.
                parent_family = "glaive" if family == "scythe" else family
                put(f"data/mite/weapon_attributes/{path}.json", {"parent": f"simplyswords:iron_{parent_family}"})
                if is_two_handed(archive, f"simplyswords:iron_{parent_family}"):
                    two_handed_ids.append(item_id)
                translations["en_us"][f"item.mite.{path}"] = f"{english_metal} {english_family}"
                translations["zh_cn"][f"item.mite.{path}"] = f"{chinese_metal}{chinese_family}"

                recipe = json.loads(archive.read(f"data/simplyswords/recipe/iron_{family}.json"))
                ingots = sum(row.count("X") for row in recipe["pattern"])
                nuggets = sum(row.count("Y") for row in recipe["pattern"])
                if material != "rusted_iron":
                    ingot_id = f"{'minecraft' if material == 'copper' else 'mite'}:{material}_ingot"
                    for ingredient in recipe["key"].values():
                        if ingredient.get("item") == "minecraft:iron_ingot":
                            ingredient["item"] = ingot_id
                        elif ingredient.get("item") == "minecraft:iron_nugget":
                            ingredient["item"] = f"mite:{material}_nugget"
                    recipe["neoforge:conditions"] = deepcopy(CONDITIONS)
                    recipe["category"] = "equipment"
                    recipe["result"]["id"] = item_id
                    put(f"data/mite/recipe/{path}.json", recipe)
                    put(f"data/mite/advancement/recipes/combat/{path}.json", {
                        "neoforge:conditions": deepcopy(CONDITIONS),
                        "parent": "minecraft:recipes/root",
                        "criteria": {
                            "has_material": {"trigger": "minecraft:inventory_changed", "conditions": {
                                "items": [{"items": ingot_id}],
                            }},
                            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": item_id}},
                        },
                        "requirements": [["has_material", "has_the_recipe"]],
                        "rewards": {"recipes": [item_id]},
                        "sends_telemetry_event": False,
                    })
                entries.append({"id": item_id, "material": material, "family": family,
                                "texture_work": "MITE material redraw by NingMeng123 based on the original Simply Swords iron texture",
                                "source_texture": f"assets/simplyswords/textures/item/iron_{family}.png",
                                "craftable": material != "rusted_iron",
                                "ingots": ingots, "nuggets": nuggets})

        # Explicit optional entries avoid missing-registry errors in a MITE-only
        # installation. Upstream sword/enchantable tag unions then see these too.
        for tag in ("swords", "tools", "tools/melee_weapon"):
            put(f"data/c/tags/item/{tag}.json", optional(ids, RETIRED_NATIVE_IDS))
        put("data/mite/tags/item/simplyswords_weapons.json", optional(ids))
        put("data/mite/tags/item/simplyswords/two_handed.json", optional(sorted(two_handed_ids)))
        put("data/simplyswords/tags/item/swords.json", optional(ids, RETIRED_NATIVE_IDS))
        put("data/simplyswords/tags/item/netherite_gear.json", optional([], RETIRED_NATIVE_IDS))
        put("data/simplyswords/tags/item/spears.json", optional([item for item in ids if item.endswith("_spear")],
                {"simplyswords:netherite_spear"}))
        for family in FAMILIES:
            put(f"data/simplyswords/tags/item/implicit/{family}.json", optional(
                [item for item in ids if item.endswith("_" + family)], {f"simplyswords:netherite_{family}"}))
        for weight in ("light", "medium", "heavy"):
            upstream = json.loads(archive.read(f"data/simplyswords/tags/item/{weight}_weapons.json"))
            iron_ids = {value for value in upstream["values"] if isinstance(value, str) and value.startswith("simplyswords:iron_")}
            families = {value.removeprefix("simplyswords:iron_") for value in iron_ids}
            put(f"data/simplyswords/tags/item/{weight}_weapons.json", optional(
                [item for item in ids if item.split("_")[-1] in families],
                {f"simplyswords:netherite_{family}" for family in families}))

        # The source enchantments replace vanilla support tags with exact source
        # families. Keep this optional union stable whether runData has SS or not.
        catalog = (ROOT / "src/main/java/io/github/ningmeng123/mite/enchantment/R196EnchantmentCatalog.java").read_text(encoding="utf-8")
        enchantments = re.findall(r'(?:nativeEntry|miteEntry)\(\d+, "([a-z_]+)"', catalog)
        for enchantment in enchantments:
            allowed = ENCHANT_FAMILIES.get(enchantment, set())
            supported = [item for item in native_ids if native_family(item) in allowed]
            supported.extend(entry["id"] for entry in entries if entry["family"] in allowed
                             and not (enchantment == "vampiric" and entry["material"] in {"silver", "mithril"}))
            put(f"data/mite/tags/item/simplyswords/enchantable/{enchantment}.json", optional(supported))

    for locale, names in translations.items():
        language_path = args.output / f"assets/mite/lang/{locale}.json"
        language = json.loads(language_path.read_text(encoding="utf-8"))
        language.update(names)
        put(f"assets/mite/lang/{locale}.json", language)

    try:
        source_path = archive_path.relative_to(ROOT).as_posix()
    except ValueError:
        source_path = archive_path.name
    put("mite/simplyswords-compat-assets.json", {
        "developer": "NingMeng123",
        "upstream": {
            "name": "Simply Swords", "version": "1.70.2-1.21.1",
            "repository": "https://github.com/Sweenus/SimplySwords",
            "authors": "Sweenus", "art_credits": ["Hootea", "Konsyliarz42"],
            "declared_license": "Timefall Development License",
            "source_archive": source_path,
            "sha256": hashlib.sha256(archive_path.read_bytes()).hexdigest(),
        },
        "vibranium_artwork": {
            "author": "NingMeng123",
            "palette_reference": "docs/art/vibranium-palette.png",
            "palette_source": "Built-in imagegen palette study based on the referenced vibranium navy-violet metal and cyan highlights",
            "palette_rgb": PALETTES["vibranium"],
            "application": "Deterministic luminance repaint of only the original Simply Swords metal region; original grips, silhouettes and model UVs are preserved",
        },
        "notes": [
            f"All {len(ids)} textures are MITE material redraws based on Simply Swords original iron sprites; source artwork belongs to the credited Simply Swords artists.",
            "The native iron/gold texture differences define the exact metal mask. MITE colors repaint that region using source luminance; silhouettes, transparency, grips and non-metal decoration remain unchanged.",
            "The original weapon silhouettes and UV layouts are retained; no new weapon shapes are claimed.",
            "Models inherit the original mod's geometry and display transforms, including the three-dimensional greathammer.",
            "Gold uses simplyswords:gold_<family>; no duplicate gold weapon is registered.",
            "Rusted iron weapons are loot equipment and have no crafting recipes.",
            "Recipe ingot and nugget costs match the original iron weapon recipe.",
            "Weapon models are used only when Simply Swords is loaded; recipes have a NeoForge mod-loaded condition.",
            "Native netherite weapons are retired; optional tag removals preserve all other native weapon materials and unique weapons.",
        ],
        "optional_native_weapon_ids": len(native_ids),
        "retired_native_weapon_ids": sorted(RETIRED_NATIVE_IDS),
        "items": entries,
    })

    differences = []
    for path, expected in files.items():
        target = args.output / path
        if not target.exists() or target.read_bytes() != expected:
            differences.append(path)
            if not args.check:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(expected)
    if args.check and differences:
        raise SystemExit("Compatibility assets differ:\n" + "\n".join(differences))
    if not args.check:
        args.preview.parent.mkdir(parents=True, exist_ok=True)
        preview_sheet(original_iron, rendered).save(args.preview)
        print(f"Material-redraw comparison sheet: {args.preview}")
    recipe_count = (len(MATERIALS) - 1) * len(FAMILIES)
    print(f"{'Verified' if args.check else 'Generated'} {len(ids)} weapons, {recipe_count} recipes, {recipe_count} recipe advancements and {len(files)} resource files.")


if __name__ == "__main__":
    main()
