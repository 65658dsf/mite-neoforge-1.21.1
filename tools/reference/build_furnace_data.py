"""Map independently executed R196 furnace facts onto existing modern IDs. Developer: NingMeng123."""
import json
import hashlib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))
def write(path, value):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

oracle = read(".reference/runtime-helper/furnace-fixtures.json")
source = {(row["source_item_id"], row["subtype"]): row for row in oracle["items"]}
mapping = {}
def put(source_id, modern_id, subtype=0):
    if (source_id, subtype) not in source:
        variants = [key for key in source if key[0] == source_id]
        signatures = {(source[key]["ticks"], source[key]["heat"], source[key]["large"]) for key in variants}
        if variants and len(signatures) == 1:
            subtype = variants[0][1]
        else:
            raise ValueError(f"Unknown original item: {source_id}:{subtype} -> {modern_id}")
    mapping[modern_id] = (source_id, subtype)

# Only actual registrations are taken from the historical catalog. Pending proposal IDs are not registrations.
for entry in read("spec/item-catalog-015.json")["entries"]:
    legacy = entry.get("source_registry") or {}
    modern = entry.get("actual_modern_id")
    if modern and entry.get("default_included") and entry.get("status") == "added":
        put(legacy["id"], modern, legacy.get("item_metadata", [0])[0])
for tool in read("spec/r196-tools-016.json")["tools"]:
    put(tool["legacy_id"], tool["id"])

vanilla = {
    1:"stone", 2:"grass_block", 3:"dirt", 4:"cobblestone", 12:"sand", 13:"gravel",
    14:"gold_ore", 15:"iron_ore", 16:"coal_ore", 20:"glass", 21:"lapis_ore",
    24:"sandstone", 25:"note_block", 30:"cobweb", 31:"short_grass", 32:"dead_bush",
    37:"dandelion", 38:"poppy", 39:"brown_mushroom", 40:"red_mushroom", 47:"bookshelf",
    50:"torch", 53:"oak_stairs", 54:"chest", 56:"diamond_ore", 58:"crafting_table",
    61:"furnace", 65:"ladder", 66:"rail", 72:"oak_pressure_plate", 73:"redstone_ore",
    76:"redstone_torch", 78:"snow", 81:"cactus", 82:"clay", 84:"jukebox", 85:"oak_fence",
    86:"pumpkin", 87:"netherrack", 96:"oak_trapdoor", 99:"brown_mushroom_block",
    100:"red_mushroom_block", 103:"melon", 106:"vine", 107:"oak_fence_gate", 111:"lily_pad",
    129:"emerald_ore", 134:"spruce_stairs", 135:"birch_stairs", 136:"jungle_stairs",
    143:"oak_button", 146:"trapped_chest", 151:"daylight_detector", 153:"nether_quartz_ore",
    172:"terracotta", 173:"coal_block", 261:"bow", 263:"coal", 264:"diamond",
    265:"iron_ingot", 266:"gold_ingot", 280:"stick", 281:"bowl", 282:"mushroom_stew", 297:"bread", 318:"flint",
    319:"porkchop", 320:"cooked_porkchop", 321:"painting", 323:"oak_sign", 324:"oak_door",
    325:"bucket", 326:"water_bucket", 327:"lava_bucket", 331:"redstone", 333:"oak_boat",
    334:"leather", 335:"milk_bucket", 336:"brick", 337:"clay_ball", 338:"sugar_cane",
    339:"paper", 340:"book", 342:"chest_minecart", 346:"fishing_rod", 349:"cod",
    350:"cooked_cod", 352:"bone", 353:"sugar", 355:"white_bed", 358:"filled_map",
    359:"shears", 363:"beef", 364:"cooked_beef", 365:"chicken", 366:"cooked_chicken",
    369:"blaze_rod", 386:"writable_book", 387:"written_book", 388:"emerald", 389:"item_frame",
    392:"potato", 393:"baked_potato", 395:"map", 398:"carrot_on_a_stick",
    403:"enchanted_book", 405:"nether_brick", 406:"quartz",
}
for legacy_id, path in vanilla.items():
    put(legacy_id, "minecraft:" + path)
put(263, "minecraft:charcoal", 1)
put(31, "minecraft:fern", 2)
put(351, "minecraft:lapis_lazuli", 4)
put(351, "minecraft:green_dye", 2)
put(351, "minecraft:cocoa_beans", 3)
# R196 removed legacy item262; the retained modern flint-tip arrow corresponds to arrowFlint1040.
# Every original ItemArrow, regardless of its tip material, burns100 atheat1.
put(1040, "minecraft:arrow")
for subtype, wood in enumerate(("oak", "spruce", "birch", "jungle")):
    for source_id, suffix in ((5,"planks"), (6,"sapling"), (17,"log"), (126,"slab")):
        put(source_id, f"minecraft:{wood}_{suffix}", subtype)

for source_id, name in {218:"clay_oven", 250:"large_clay_oven", 220:"sandstone_furnace",
                        222:"obsidian_furnace", 224:"netherrack_furnace"}.items():
    put(source_id, "mite:" + name)
for source_id, name in {200:"minecraft:copper_ore", 201:"mite:silver_ore",
                        202:"mite:mithril_ore", 203:"mite:adamantium_ore",
                        962:"minecraft:copper_ingot", 963:"mite:silver_ingot",
                        964:"mite:mithril_ingot", 965:"mite:adamantium_ingot"}.items():
    put(source_id, name)
for source_id, metal in ((1154,"copper"), (1155,"silver"), (1156,"gold"),
                         (1157,"mithril"), (1158,"adamantium"), (1159,"ancient_metal")):
    put(source_id, "mite:" + metal + "_lava_bucket")
# Workbench variants intentionally unify wood subtypes but preserve furnace fuel properties.
for material in ("flint", "obsidian", "copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium"):
    put(58, f"mite:{material}_crafting_table")

fuels = {}
mapped_source = []
required_heat = {}
smelting = {row["input_item_id"]: row for row in oracle["smelting"]}
for modern_id, legacy_key in sorted(mapping.items()):
    row = source[legacy_key]
    fuels[modern_id] = {"ticks": row["ticks"], "heat": row["heat"], "large": row["large"]}
    mapped_source.append({"id": modern_id, "source_item_id": legacy_key[0], "subtype": legacy_key[1],
                          "ticks": row["ticks"], "heat": row["heat"], "large": row["large"]})
    if legacy_key[0] in smelting:
        required_heat[modern_id] = smelting[legacy_key[0]]["required_heat"]

# New ore substrates and raw-metal items represent the same metal processing stages as their original ore.
for metal, heat in (("copper",2), ("silver",2), ("iron",2), ("gold",2), ("mithril",3), ("adamantium",4)):
    namespace = "minecraft" if metal in {"copper", "iron", "gold"} else "mite"
    for path in (f"{metal}_ore", f"deepslate_{metal}_ore"):
        required_heat[f"{namespace}:{path}"] = heat
    if namespace == "minecraft":
        for path in (f"raw_{metal}", f"raw_{metal}_block"):
            required_heat[f"minecraft:{path}"] = heat
for path in ("redstone", "lapis", "diamond", "emerald"):
    required_heat[f"minecraft:deepslate_{path}_ore"] = 2
required_heat["minecraft:deepslate_coal_ore"] = 1
required_heat["minecraft:nether_gold_ore"] = 2
required_heat["minecraft:ancient_debris"] = 4
required_heat["minecraft:red_sandstone"] = 2
modern_recycling = {}
with zipfile.ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar") as vanilla_resources:
    for metal, file_name in (("iron", "iron_nugget_from_smelting"), ("gold", "gold_nugget_from_smelting")):
        entry = f"data/minecraft/recipe/{file_name}.json"
        encoded = vanilla_resources.read(entry)
        recipe = json.loads(encoded)
        for ingredient in recipe["ingredient"]:
            required_heat[ingredient["item"]] = 2
            modern_recycling[ingredient["item"]] = {"required_heat":2, "metal":metal,
                    "provenance":"explicit_modern_metal_recycling_adaptation",
                    "recipe_source":entry, "recipe_source_sha256":hashlib.sha256(encoded).hexdigest(),
                    "source_counterpart":"Original corresponding metal ore requires heat2; recycling recipe did not exist in R196."}

profiles = {
    "mite:clay_oven": {"max_heat":1, "accepts_large":False, "cycle_ticks":200},
    "mite:large_clay_oven": {"max_heat":1, "accepts_large":True, "cycle_ticks":200},
    "mite:sandstone_furnace": {"max_heat":1, "accepts_large":True, "cycle_ticks":200},
    "minecraft:furnace": {"max_heat":2, "accepts_large":True, "cycle_ticks":200},
    "mite:obsidian_furnace": {"max_heat":3, "accepts_large":True, "cycle_ticks":200},
    "mite:netherrack_furnace": {"max_heat":4, "accepts_large":True, "cycle_ticks":200},
    "minecraft:smoker": {"max_heat":1, "accepts_large":True, "cycle_ticks":100},
    "minecraft:blast_furnace": {"max_heat":2, "accepts_large":True, "cycle_ticks":100},
}
adapted_fuel = {}
def modern_fuel(path, ticks, large, counterpart):
    modern_id = "minecraft:" + path
    if modern_id not in fuels:
        fuels[modern_id] = {"ticks":ticks, "heat":1 if ticks else 0, "large":large}
        adapted_fuel[modern_id] = {"ticks":ticks, "heat":1 if ticks else 0, "large":large,
                                  "provenance":"explicit_modern_adaptation", "counterpart":counterpart}
for wood in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo"):
    for suffix, ticks, large, counterpart in (
            ("planks",400,True,"legacy planks"),
            ("slab",200,True,"legacy wood slab"),
            ("stairs",400,True,"legacy wood stairs"),
            ("button",400,False,"legacy wooden button"),
            ("door",400,True,"legacy wooden door"),
            ("trapdoor",400,True,"legacy wooden trapdoor"),
            ("fence",400,True,"legacy wooden fence"),
            ("pressure_plate",400,True,"oak_pressure_plate"),
            ("fence_gate",400,True,"oak_fence_gate"),
            ("sign",200,False,"oak_sign"),
            ("hanging_sign",200,False,"oak_sign; modern smaller hanging-sign item")):
        modern_fuel(f"{wood}_{suffix}", ticks, large, counterpart)
    if wood != "bamboo":
        for prefix in ("", "stripped_"):
            for suffix in ("log", "wood"):
                modern_fuel(f"{prefix}{wood}_{suffix}", 1600, True, "legacy log")
        modern_fuel(f"{wood}_sapling" if wood != "mangrove" else "mangrove_propagule", 200, False, "legacy sapling")
    else:
        modern_fuel("bamboo_block",1600,True,"compressed bamboo wood; modern log-family adaptation")
        modern_fuel("stripped_bamboo_block",1600,True,"compressed bamboo wood; modern log-family adaptation")
        for suffix, ticks in (("mosaic",400), ("mosaic_stairs",400), ("mosaic_slab",200)):
            modern_fuel("bamboo_"+suffix,ticks,True,"modern bamboo mosaic inherits corresponding wood shape")
for color in ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
              "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"):
    modern_fuel(color + "_bed", 200, True, "legacy bed item")
# Retain Nether wood's modern fire immunity; this is an explicit modern exception to wood inheritance.
for wood in ("crimson", "warped"):
    for suffix in ("planks", "slab", "stairs", "fence", "fence_gate", "door", "trapdoor",
                   "pressure_plate", "button", "sign", "hanging_sign"):
        modern_fuel(f"{wood}_{suffix}", 0, suffix not in {"button", "sign", "hanging_sign"},
                    "nonflammable modern Nether wood")
data = {"version":1, "author":"NingMeng123", "source":"guarded R196 bytecode and explicit modern mappings",
        "reference_archive_sha256":oracle["archive_sha256"], "fuel":fuels, "profiles":profiles,
        "required_heat":dict(sorted(required_heat.items())),
        "large_items":sorted(key for key, value in fuels.items() if value["large"]),
        "bucket_melting_chance":{row["material"]:row["chance"] for row in oracle["bucket_melting"]},
        "modern_adapted_fuel":adapted_fuel, "modern_recycling_heat":modern_recycling}
write("src/main/resources/mite/furnaces-024.json", data)
write(".reference/runtime-helper/furnace-modern-mappings.json", mapped_source)
print(json.dumps({"mapped_items":len(fuels), "mapped_positive_fuel":sum(x["ticks"]>0 for x in fuels.values()),
                  "required_heat_items":len(required_heat), "profiles":len(profiles)}))
