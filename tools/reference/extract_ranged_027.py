"""Reproducible R196 registered recipes and unchanged source assets. Author: NingMeng123."""
import hashlib
import gzip
import struct
import json
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = json.loads((ROOT / "reference-data/runtime-registry.json").read_text(encoding="utf-8"))
MATERIALS = ["flint", "obsidian", "copper", "silver", "rusted_iron", "gold", "iron", "ancient_metal", "mithril", "adamantium"]
ARROW_IDS = [1040, 1049, 1041, 1042, 1047, 1043, 1044, 1250, 1045, 1046]
ROD_IDS = [1207, 1212, 1192, 1193, None, 1194, 346, 1263, 1195, 1196]
CARROT_IDS = [1208, 1213, 1197, 1198, None, 1199, 398, 1264, 1200, 1201]
HEAD_IDS = [1121, 1122, 956, 957, None, 371, 958, 961, 959, 960]
DURABILITY = [1, 2, 4, 4, 4, 4, 8, 16, 64, 256]
DAMAGE = [1, 2, 3, 3, 2, 2, 4, 4, 5, 6]
RECOVERY = [.3, .4, .6, .6, .5, .5, .7, .8, .8, .9]
ENCHANTABILITY = [0, 0, 30, 30, 0, 50, 30, 40, 100, 40]

def arrow_id(material):
    return "minecraft:arrow" if material == "flint" else "mite:" + ("golden" if material == "gold" else material) + "_arrow"

def rod_id(material):
    return "minecraft:fishing_rod" if material == "iron" else "mite:fishing_rod_" + material + "_hook"

def carrot_id(material):
    return "minecraft:carrot_on_a_stick" if material == "iron" else "mite:carrot_on_a_stick_" + material + "_hook"

MAP = {280: "minecraft:stick", 287: "minecraft:string", 288: "minecraft:feather", 391: "minecraft:carrot"}
for i, material in enumerate(MATERIALS):
    MAP[ARROW_IDS[i]] = arrow_id(material)
    if ROD_IDS[i] is not None:
        MAP[ROD_IDS[i]] = rod_id(material)
        MAP[CARROT_IDS[i]] = carrot_id(material)
        MAP[HEAD_IDS[i]] = ("mite:flint_chip" if material == "flint" else "mite:obsidian_shard" if material == "obsidian"
                            else "minecraft:gold_nugget" if material == "gold" else "minecraft:iron_nugget" if material == "iron"
                            else "mite:" + material + "_nugget")

RECIPES, TIMED, EVIDENCE = [], {}, []
for source in SOURCE["crafting_recipes"]:
    old_result = (source.get("output") or {}).get("item_id")
    if old_result is None:
        continue
    components = source.get("components") or []
    kind = None
    if old_result in ARROW_IDS or old_result in ROD_IDS + CARROT_IDS:
        kind = "rod_conversion" if old_result in CARROT_IDS or len(components) == 1 else "shaped"
        material = MATERIALS[(ARROW_IDS if old_result in ARROW_IDS else ROD_IDS if old_result in ROD_IDS else CARROT_IDS).index(old_result)]
        recipe_id = MAP[old_result] if kind == "shaped" or old_result in CARROT_IDS else "mite:" + rod_id(material).split(":")[1] + "_from_carrot"
    elif len(components) == 1 and components[0] and components[0]["item_id"] in ARROW_IDS:
        kind = "shapeless"
        material = MATERIALS[ARROW_IDS.index(components[0]["item_id"])]
        recipe_id = "mite:" + ("golden" if material == "gold" else material) + "_arrow_dismantle"
    if kind is None:
        continue
    row = {"id": recipe_id, "kind": kind, "output": MAP[old_result], "count": source["output"]["count"], "source_order": source["order"]}
    if kind == "shaped":
        width, height = source["width"], source["height"]
        ingredient_ids = list(dict.fromkeys(c["item_id"] for c in components if c))
        codes = {value: chr(65 + index) for index, value in enumerate(ingredient_ids)}
        row["key"] = {codes[value]: MAP[value] for value in ingredient_ids}
        row["pattern"] = ["".join(codes[components[index]["item_id"]] if components[index] else " "
                          for index in range(y * width, (y + 1) * width)) for y in range(height)]
    elif kind == "shapeless":
        row["input"] = MAP[components[0]["item_id"]]
    else:
        row["material"], row["attach_carrot"] = material, old_result in CARROT_IDS
    RECIPES.append(row)
    TIMED[recipe_id] = {"difficulty": source["difficulty"], "required_bench": (source.get("bench_material") or {}).get("name"), "source_order": source["order"]}
    EVIDENCE.append(source)

def save(path, data):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

save("src/main/resources/mite/ranged-recipes-027.json", {"author": "NingMeng123", "recipes": RECIPES})
save("src/main/resources/mite/timed-crafting-027-ranged.json", {"author": "NingMeng123", "recipes": TIMED})

pack = ROOT / "MITE Resource Pack 1.6.4.zip"
assets = []
with zipfile.ZipFile(pack) as archive:
    for material in MATERIALS:
        name = arrow_id(material).split(":")[1]
        assets += [("assets/minecraft/textures/items/arrows/" + material + "_arrow.png", "assets/mite/textures/item/" + name + ".png"),
                   ("assets/minecraft/textures/entity/arrows/" + material + ".png", "assets/mite/textures/entity/arrows/" + material + ".png")]
        if material != "rusted_iron":
            assets.append(("assets/minecraft/textures/items/fishing_rods/" + material + "_uncast.png", "assets/mite/textures/item/" + rod_id(material).split(":")[1] + ".png"))
    assets += [("assets/minecraft/textures/items/fishing_rod_cast.png", "assets/mite/textures/item/fishing_rod_cast.png"),
               ("assets/minecraft/textures/items/carrot_on_a_stick.png", "assets/mite/textures/item/carrot_on_a_stick.png")]
    for source, target in assets:
        content = archive.read(source)
        path = ROOT / "src/main/resources" / target
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)

facts = []
lowest = {}
for recipe in SOURCE["crafting_recipes"]:
    output = recipe.get("output") or {}
    if recipe.get("include_in_lowest_difficulty") and output.get("item_id") in ROD_IDS + CARROT_IDS:
        value = recipe["difficulty"] / output["count"]
        lowest[output["item_id"]] = min(lowest.get(output["item_id"], float("inf")), value)
for i, material in enumerate(MATERIALS):
    facts.append({"material": material, "arrow_id": arrow_id(material), "legacy_arrow": ARROW_IDS[i], "arrow_damage": .5 + DAMAGE[i] * .5,
                  "recovery": RECOVERY[i], "arrow_stack": 16, "craftable_arrow": material != "rusted_iron",
                  "rod_id": None if material == "rusted_iron" else rod_id(material), "legacy_rod": ROD_IDS[i],
                  "rod_durability": None if material == "rusted_iron" else int(2 * DURABILITY[i] + (1 if material == "flint" else 0)),
                  "rod_enchantability": ENCHANTABILITY[i], "rod_has_quality": False,
                  "carrot_rod_id": None if material == "rusted_iron" else carrot_id(material), "legacy_carrot_rod": CARROT_IDS[i],
                  "rod_lowest_difficulty": lowest.get(ROD_IDS[i]), "carrot_rod_lowest_difficulty": lowest.get(CARROT_IDS[i])})
save("spec/ranged-027.json", {"author": "NingMeng123", "archive_sha256": SOURCE["archive_sha256"], "resource_pack_sha256": hashlib.sha256(pack.read_bytes()).hexdigest(),
     "source_locations": ["ItemArrow.java:33-81", "akc.java:351-389", "xv.java:13-22", "ul.java:128-164,290-318,395-440", "uh.java:310-340", "of.java:1152-1161", "ws.java:14-22,102-140"],
     "materials": facts, "registered_recipes": EVIDENCE, "activation_flags": SOURCE["activation_flags"],
     "two_item_repair": {"source": "aaf.java:294-324;awy.java:171", "difficulty": "lowest_difficulty/2", "experience_cost_exempt": True,
                         "requires_both_damaged": True, "requires_unenchanted": True, "modern_five_percent_bonus": False,
                         "copies_arbitrary_nbt": False, "copies_matching_dyed_color": True, "bench": "hardest metal on output"},
     "adaptation": {"modern_fishing_loot": "Preserved modern loot events/tables; source bite probability and costs restored",
                    "fishing_fortune": "Luck of the Sea supplies source 9/10 per level probability multiplier while preserving modern loot luck",
                    "modern_piercing": "Modern Piercing retains its projectile; no source contained-arrow recovery roll for piercing missiles, preventing duplicate returns",
                    "arrow_enchantments": "Native weapon enchantment damage is preserved before source speed/fractional impact/critical multiplication",
                    "waterlogged": "Full water fluid states count as fish habitat in modern world; physical collision ray remains required"},
     "textures": [{"source": source, "target": target} for source, target in assets]})

zh_materials = ["燧石", "黑曜石", "铜", "银", "锈铁", "金", "铁", "远古金属", "秘银", "艾德曼"]
zh, en = {}, {}
for i, material in enumerate(MATERIALS):
    def key(value): return "item." + value.replace(":", ".")
    zh[key(arrow_id(material))] = zh_materials[i] + "箭"
    en[key(arrow_id(material))] = material.replace("_", " ").title() + " Arrow"
    if material != "rusted_iron":
        zh[key(rod_id(material))] = "钓鱼竿[" + zh_materials[i] + "钓钩]"
        en[key(rod_id(material))] = "Fishing Rod [" + material.replace("_", " ").title() + " Hook]"
        zh[key(carrot_id(material))] = "胡萝卜钓竿[" + zh_materials[i] + "钓钩]"
        en[key(carrot_id(material))] = "Carrot on a Stick [" + material.replace("_", " ").title() + " Hook]"
save("build/verification/027-ranged-lang.json", {"zh_cn": zh, "en_us": en})
template = bytearray(gzip.decompress((ROOT / "src/gametest/resources/data/mite/structure/empty.nbt").read_bytes()))
struct.pack_into(">iii", template, template.index(b"size") + 9, 9, 8, 9)
(ROOT / "src/gametest/resources/data/mite/structure/ranged_027.nbt").write_bytes(gzip.compress(template, mtime=0))
print(f"Extracted {len(facts)} materials, {len(RECIPES)} registered recipes and {len(assets)} original textures")
