"""Map authentic R196 stack limits to the current item registry. Developer: NingMeng123.

This is an identity/adaptation exporter, not a registry-name guesser. Native aliases
and metadata splits below are deliberate semantic mappings. ItemBlock objects for
world-only states do not overwrite the separately registered carried item.
"""
from __future__ import annotations

import collections
import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COLORS = "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split()
OLD_WOODS = "oak spruce birch jungle".split()


def read(relative: str):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def sha(relative: str):
    return hashlib.sha256((ROOT / relative).read_bytes()).hexdigest()


def modern_snapshot():
    """Extract the pinned native Item identities without unrelated research files.

    The small committed snapshot also works when Gradle artifacts are unavailable.
    """
    artifact = ROOT / "build/moddev/artifacts/neoforge-21.1.259-sources.jar"
    target = ROOT / "spec/stacking-modern-items-030.json"
    if not artifact.is_file():
        if not target.is_file():
            raise FileNotFoundError("Pinned source artifact or stacking-modern-items-030.json required")
        return read("spec/stacking-modern-items-030.json")
    entry = "net/minecraft/world/item/Items.java"
    with zipfile.ZipFile(artifact) as archive:
        raw = archive.read(entry)
    source = raw.decode("utf-8")
    items = []
    for match in re.finditer(r"public static final Item\s+([A-Z][A-Z0-9_]*)\s*=\s*(.*?);", source, re.S):
        symbol, body = match.groups()
        literal = re.search(r'registerItem\(\s*"([a-z0-9_]+)"', body)
        resource_key = re.search(r"registerItem\(\s*net\.minecraft\.references\.Items\.([A-Z][A-Z0-9_]*)", body)
        block = re.search(r"Blocks\.([A-Z][A-Z0-9_]*)", body)
        if literal:
            item_name = literal.group(1)
        elif resource_key:
            item_name = resource_key.group(1).lower()
        elif block:
            item_name = block.group(1).lower()
        else:
            raise ValueError(f"Unresolved pinned native item identity: {symbol}: {body}")
        implementations = [name for name in re.findall(r"new\s+([A-Za-z][A-Za-z0-9_]*)(?:<[^>]*>)?\s*\(", body) if name != "Properties"]
        row = {"id": "minecraft:" + item_name, "symbol": symbol,
               "declared_implementation": implementations[0] if implementations else "",
               "source_line": source[:match.start()].count("\n") + 1,
               "category": "food_requires_nutrition_mapping" if re.search(r"\.food\(Foods\.", body) else "item"}
        if block:
            row["block_symbol"] = block.group(1)
        items.append(row)
    if len(items) != 1333 or len({x["id"] for x in items}) != 1333:
        raise ValueError(f"Pinned 1.21.1 item inventory changed: {len(items)}")
    snapshot = {"schema_version": 1, "author": "NingMeng123", "minecraft": "1.21.1", "neoforge": "21.1.259",
                "source_artifact": artifact.relative_to(ROOT).as_posix(), "source_artifact_sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
                "source_entry": entry, "source_entry_sha256": hashlib.sha256(raw).hexdigest(), "count": len(items), "items": items}
    target.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return snapshot


# No aliases for world-only fluid, crop, portal, door-half or burning-furnace IDs.
# Their ItemBlock constructors run, but the modern carried identity is a distinct
# high-ID Item in R196. Mapping those low IDs would cause incorrect 4/8-item limits.
BLOCK_IDENTITIES = {
    1: "stone", 2: "grass_block", 3: "dirt", 4: "cobblestone", 7: "bedrock",
    12: "sand", 19: "sponge", 20: "glass", 21: "lapis_ore", 22: "lapis_block",
    23: "dispenser", 25: "note_block", 27: "powered_rail", 28: "detector_rail",
    29: "sticky_piston", 30: "cobweb", 33: "piston", 37: "dandelion",
    39: "brown_mushroom", 40: "red_mushroom", 41: "gold_block", 42: "iron_block",
    45: "bricks", 46: "tnt", 47: "bookshelf", 48: "mossy_cobblestone",
    49: "obsidian", 50: "torch", 52: "spawner", 53: "oak_stairs", 54: "chest",
    56: "diamond_ore", 57: "diamond_block", 60: "farmland", 61: "furnace",
    65: "ladder", 66: "rail", 67: "cobblestone_stairs", 69: "lever",
    70: "stone_pressure_plate", 72: "oak_pressure_plate", 73: "redstone_ore",
    76: "redstone_torch", 77: "stone_button", 78: "snow", 79: "ice",
    80: "snow_block", 81: "cactus", 82: "clay", 84: "jukebox",
    85: "oak_fence", 86: "carved_pumpkin", 87: "netherrack", 88: "soul_sand",
    89: "glowstone", 91: "jack_o_lantern", 96: "oak_trapdoor",
    99: "brown_mushroom_block", 100: "red_mushroom_block", 101: "iron_bars",
    102: "glass_pane", 103: "melon", 106: "vine", 107: "oak_fence_gate",
    108: "brick_stairs", 109: "stone_brick_stairs", 110: "mycelium",
    111: "lily_pad", 112: "nether_bricks", 113: "nether_brick_fence",
    114: "nether_brick_stairs", 116: "enchanting_table", 120: "end_portal_frame",
    121: "end_stone", 122: "dragon_egg", 123: "redstone_lamp",
    128: "sandstone_stairs", 129: "emerald_ore", 130: "ender_chest",
    131: "tripwire_hook", 133: "emerald_block", 134: "spruce_stairs",
    135: "birch_stairs", 136: "jungle_stairs", 137: "command_block",
    138: "beacon", 143: "oak_button", 146: "trapped_chest", 147: "light_weighted_pressure_plate",
    148: "heavy_weighted_pressure_plate", 151: "daylight_detector",
    152: "redstone_block", 153: "nether_quartz_ore", 154: "hopper",
    156: "quartz_stairs", 157: "activator_rail", 158: "dropper", 170: "hay_block",
    172: "terracotta", 173: "coal_block",
}
ITEM_IDENTITIES = {
    256: "iron_shovel", 257: "iron_pickaxe", 258: "iron_axe", 259: "flint_and_steel",
    260: "apple", 261: "bow", 264: "diamond", 265: "iron_ingot", 266: "gold_ingot",
    267: "iron_sword", 269: "wooden_shovel", 280: "stick", 281: "bowl",
    282: "mushroom_stew", 283: "golden_sword", 284: "golden_shovel",
    285: "golden_pickaxe", 286: "golden_axe", 287: "string", 288: "feather",
    289: "gunpowder", 292: "iron_hoe", 294: "golden_hoe", 295: "wheat_seeds",
    296: "wheat", 297: "bread", 298: "leather_helmet", 299: "leather_chestplate",
    300: "leather_leggings", 301: "leather_boots", 302: "chainmail_helmet",
    303: "chainmail_chestplate", 304: "chainmail_leggings", 305: "chainmail_boots",
    306: "iron_helmet", 307: "iron_chestplate", 308: "iron_leggings", 309: "iron_boots",
    314: "golden_helmet", 315: "golden_chestplate", 316: "golden_leggings",
    317: "golden_boots", 318: "flint", 319: "porkchop", 320: "cooked_porkchop",
    321: "painting", 323: "oak_sign", 324: "oak_door", 325: "bucket",
    326: "water_bucket", 327: "lava_bucket", 328: "minecart", 329: "saddle",
    330: "iron_door", 331: "redstone", 332: "snowball", 333: "oak_boat",
    334: "leather", 335: "milk_bucket", 336: "brick", 337: "clay_ball",
    338: "sugar_cane", 339: "paper", 340: "book", 342: "chest_minecart",
    343: "furnace_minecart", 344: "egg", 345: "compass", 346: "fishing_rod",
    347: "clock", 348: "glowstone_dust", 349: "cod", 350: "cooked_cod",
    352: "bone", 353: "sugar", 354: "cake", 355: "red_bed", 356: "repeater",
    357: "cookie", 358: "filled_map", 359: "shears", 360: "melon_slice",
    361: "pumpkin_seeds", 362: "melon_seeds", 363: "beef", 364: "cooked_beef",
    365: "chicken", 366: "cooked_chicken", 367: "rotten_flesh", 368: "ender_pearl",
    369: "blaze_rod", 370: "ghast_tear", 371: "gold_nugget", 372: "nether_wart",
    373: "potion", 374: "glass_bottle", 375: "spider_eye", 376: "fermented_spider_eye",
    377: "blaze_powder", 378: "magma_cream", 379: "brewing_stand", 380: "cauldron",
    381: "ender_eye", 382: "glistering_melon_slice", 384: "experience_bottle",
    385: "fire_charge", 386: "writable_book", 387: "written_book", 388: "emerald",
    389: "item_frame", 390: "flower_pot", 391: "carrot", 392: "potato",
    393: "baked_potato", 394: "poisonous_potato", 395: "map", 396: "golden_carrot",
    398: "carrot_on_a_stick", 399: "nether_star", 400: "pumpkin_pie",
    401: "firework_rocket", 402: "firework_star", 403: "enchanted_book",
    404: "comparator", 405: "nether_brick", 406: "quartz", 407: "tnt_minecart",
    408: "hopper_minecart", 417: "iron_horse_armor", 418: "golden_horse_armor",
    420: "lead", 421: "name_tag", 958: "iron_nugget", 1025: "wooden_sword",
    1040: "arrow", 1172: "mutton", 1173: "cooked_mutton", 1229: "salmon",
    1230: "cooked_salmon",
}
WORLD_STATE_IDS = {
    8, 9, 10, 11, 26, 34, 36, 43, 51, 55, 59, 62, 63, 64, 68, 71, 74, 75,
    83, 90, 92, 93, 94, 95, 104, 105, 115, 117, 118, 119, 124, 125, 127,
    132, 140, 141, 142, 144, 149, 150, 208, 209, 210, 211, 212, 219, 221,
    223, 225, 226, 234, 235, 236, 237, 238, 246, 248, 251, 253,
}
EXCLUDED_CONFLICT_IDS = set(range(1050, 1058))
UNIMPLEMENTED_SOURCE_IDS = {214, 227, 228, 1210, 1211, 1228, 1276, 1282}


def main():
    runtime = read("reference-data/runtime-registry.json")
    probe = read("spec/stacking-source-030.json")
    catalog = read("spec/item-catalog-015.json")
    modern = modern_snapshot()
    probed = {x["legacy_id"]: x for x in probe["items"]}
    modern_items = {x["id"]: x for x in modern["items"]}
    maps: dict[str, dict] = {}
    used: dict[int, set[int]] = collections.defaultdict(set)
    conflicts = []

    def limit(sid, meta=0):
        item = probed[sid]
        dynamic = [x for x in item.get("dynamic_subtype_limits", []) if x["subtype"] == meta]
        if dynamic:
            return dynamic[0]["max_stack_size"]
        matches = [x for x in item["subtype_limits"] if x["subtype"] == meta]
        return matches[0]["max_stack_size"] if matches else item["base_limit"]

    def add(modern_id, sid, meta=0, kind="source", evidence="explicit_native_identity", note=None):
        if sid not in probed:
            raise ValueError(f"Source item absent: {sid}")
        if modern_id.startswith("minecraft:") and modern_id not in modern_items:
            raise ValueError(f"Modern item not declared: {modern_id}")
        value = limit(sid, meta)
        subtype_matches = [x for x in probed[sid]["subtype_limits"] if x["subtype"] == meta]
        source = {"legacy_id": sid, "subtype": meta,
                  "source_name": subtype_matches[0]["source_name"] if subtype_matches else probed[sid].get("source_name"), "source_class": probed[sid]["class"],
                  "max_stack_size": value}
        if modern_id in maps:
            row = maps[modern_id]
            if row["max_stack_size"] != value:
                conflicts.append({"modern_id": modern_id, "existing": row, "incoming": source, "evidence": evidence})
                raise ValueError(f"Conflicting stack limits for {modern_id}")
            if source not in row["source_items"]:
                row["source_items"].append(source)
        else:
            maps[modern_id] = {"modern_id": modern_id, "max_stack_size": value,
                               "mapping_kind": kind, "source_items": [source], "evidence": [evidence]}
            if note:
                maps[modern_id]["note"] = note
        if evidence not in maps[modern_id]["evidence"]:
            maps[modern_id]["evidence"].append(evidence)
        used[sid].add(meta)

    # Exact registered current identities from the accepted source catalog.
    for row in catalog["entries"]:
        source = row.get("source_registry")
        mid = row.get("actual_modern_id")
        if not row["default_included"] or not source or not mid or source["id"] == 95:
            continue
        for subtype in source.get("item_metadata", [0]):
            add(mid, source["id"], subtype, evidence="spec/item-catalog-015.json actual_modern_id")

    for sid, name in BLOCK_IDENTITIES.items():
        add("minecraft:" + name, sid)
    for sid, name in ITEM_IDENTITIES.items():
        add("minecraft:" + name, sid)

    # Metadata splits are semantic identities; world orientation is never an item subtype.
    splits = {
        13: {0: "minecraft:gravel", 2: "mite:nether_gravel"},
        14: {0: "minecraft:gold_ore", 2: "minecraft:nether_gold_ore"},
        24: {0: "minecraft:sandstone", 1: "minecraft:chiseled_sandstone", 2: "minecraft:cut_sandstone"},
        31: {1: "minecraft:short_grass", 2: "minecraft:fern"},
        32: {0: "minecraft:dead_bush", 1: "mite:witherwood_bush"},
        38: {0: "minecraft:poppy", 1: "minecraft:blue_orchid", 2: "minecraft:allium", 5: "minecraft:orange_tulip", 7: "mite:dahlia", 8: "minecraft:oxeye_daisy"},
        44: {0: "minecraft:smooth_stone_slab", 1: "minecraft:sandstone_slab", 3: "minecraft:cobblestone_slab", 4: "minecraft:brick_slab", 5: "minecraft:stone_brick_slab", 6: "minecraft:nether_brick_slab", 7: "minecraft:quartz_slab"},
        58: {**{x: "mite:flint_crafting_table" for x in range(4)}, 4: "mite:copper_crafting_table", 5: "mite:silver_crafting_table", 6: "mite:gold_crafting_table", 7: "mite:iron_crafting_table", 8: "mite:ancient_metal_crafting_table", 9: "mite:mithril_crafting_table", 10: "mite:adamantium_crafting_table", **{x: "mite:obsidian_crafting_table" for x in range(11, 15)}},
        97: {0: "minecraft:infested_stone", 1: "minecraft:infested_cobblestone", 2: "minecraft:infested_stone_bricks", 3: "mite:infested_netherrack"},
        98: {0: "minecraft:stone_bricks", 1: "minecraft:mossy_stone_bricks", 2: "minecraft:cracked_stone_bricks", 3: "minecraft:chiseled_stone_bricks"},
        139: {0: "minecraft:cobblestone_wall", 1: "minecraft:mossy_cobblestone_wall"},
        145: {0: "minecraft:anvil", 1: "minecraft:chipped_anvil", 2: "minecraft:damaged_anvil"},
        155: {0: "minecraft:quartz_block", 1: "minecraft:chiseled_quartz_block", 2: "minecraft:quartz_pillar"},
        263: {0: "minecraft:coal", 1: "minecraft:charcoal"},
        322: {0: "minecraft:golden_apple", 1: "minecraft:enchanted_golden_apple"},
        341: {0: "minecraft:slime_ball", 1: "mite:ochre_jelly", 2: "mite:crimson_blob", 3: "mite:grey_gelatinous_sphere", 4: "mite:black_pudding"},
        397: {0: "minecraft:skeleton_skull", 1: "minecraft:wither_skeleton_skull", 2: "minecraft:zombie_head", 3: "minecraft:player_head", 4: "minecraft:creeper_head"},
    }
    for sid, suffix in ((5, "planks"), (6, "sapling"), (17, "log"), (18, "leaves"), (126, "slab")):
        splits[sid] = {n: "minecraft:" + wood + "_" + suffix for n, wood in enumerate(OLD_WOODS)}
    for sid, suffix in ((35, "wool"), (159, "terracotta"), (171, "carpet")):
        splits[sid] = {n: "minecraft:" + color + "_" + suffix for n, color in enumerate(COLORS)}
    dye_names = "ink_sac red_dye green_dye cocoa_beans lapis_lazuli purple_dye cyan_dye light_gray_dye gray_dye pink_dye lime_dye yellow_dye light_blue_dye magenta_dye orange_dye bone_meal".split()
    splits[351] = {n: "minecraft:" + name for n, name in enumerate(dye_names)}
    for sid, subs in splits.items():
        for meta, mid in subs.items():
            add(mid, sid, meta, evidence="explicit_source_metadata_split")

    for sid, name in zip(range(2256, 2268), "13 cat blocks chirp far mall mellohi stal strad ward 11 wait".split()):
        add("minecraft:music_disc_" + name, sid)
    old_spawn_entities = {
        50: "creeper", 51: "skeleton", 52: "spider", 54: "zombie", 55: "slime",
        56: "ghast", 57: "zombified_piglin", 58: "enderman", 59: "cave_spider",
        60: "silverfish", 61: "blaze", 62: "magma_cube", 65: "bat", 66: "witch",
        90: "pig", 91: "sheep", 92: "cow", 93: "chicken", 94: "squid", 95: "wolf",
        96: "mooshroom", 98: "ocelot", 100: "horse", 120: "villager",
    }
    for meta, name in old_spawn_entities.items():
        add("minecraft:" + name + "_spawn_egg", 383, meta, evidence="runtime entity ID and source spawn-egg metadata identity")
    for subtype in probed[373]["subtype_limits"]:
        meta = subtype["subtype"]
        add("minecraft:splash_potion" if meta & 16384 else "minecraft:potion", 373, meta,
            evidence="yp.java splash bit16384 becomes distinct modern potion item")

    # Additional implemented recipes/containers/tools not visible as catalog leaves.
    profiles = read("src/main/resources/mite/foods-018.json")
    for row in profiles["foods"] + profiles["non_food_items"]:
        if row.get("source_kind", "r196") == "r196" and row.get("source_id") in probed:
            add(row["id"], row["source_id"], row.get("source_subtype", 0), evidence="mite/foods-018.json source identity")
    for row in read("spec/r196-tools-016.json")["tools"]:
        add(row["id"], row["legacy_id"], evidence="spec/r196-tools-016.json definition")
    for row in read("spec/ranged-027.json")["materials"]:
        for id_key, legacy_key in (("arrow_id", "legacy_arrow"), ("rod_id", "legacy_rod"), ("carrot_rod_id", "legacy_carrot_rod")):
            if row.get(id_key) and row.get(legacy_key):
                add(row[id_key], row[legacy_key], evidence="spec/ranged-027.json registered material")
    for name, sid in (("silver", 201), ("mithril", 202), ("adamantium", 203)):
        add("mite:deepslate_" + name + "_ore", sid, kind="modern_adaptation", evidence="MiteOreBlocks.java deepslate host peer")
    for sid, name in ((229, "copper"), (230, "silver"), (231, "golden"), (232, "mithril"), (233, "adamantium"), (254, "ancient_metal")):
        for subtype in (1, 2):
            add("mite:" + name + "_anvil", sid, subtype, evidence="source damaged-anvil subtype becomes persistent ANVIL_DAMAGE component")
    add("mite:blueberry_bush", 249, 12, evidence="source bush metadata12 ripe/empty placement peer retains same carried identity")

    # Native dye items separated from legacy pigment ingredients; compatibility is explicit.
    adaptations = {
        "black_dye": (351, 0), "red_dye": (351, 1), "brown_dye": (351, 3),
        "blue_dye": (351, 4), "white_dye": (351, 15),
        "pumpkin": (86, 0), "red_sand": (12, 0), "wet_sponge": (19, 0), "smooth_sandstone": (24, 2),
        "crafting_table": (58, 0), "mushroom_stem": (99, 0), "infested_chiseled_stone_bricks": (97, 2),
        "infested_mossy_stone_bricks": (97, 2), "infested_cracked_stone_bricks": (97, 2),
        "stone_slab": (44, 0), "petrified_oak_slab": (44, 0), "smooth_stone": (1, 0),
        "lingering_potion": (373, 0), "tipped_arrow": (1040, 0),
        "spectral_arrow": (1040, 0), "glow_item_frame": (389, 0), "recovery_compass": (345, 0),
        "turtle_egg": (344, 0), "sniffer_egg": (344, 0), "breeze_rod": (369, 0),
        "glow_ink_sac": (351, 0), "soul_torch": (50, 0),
        "deepslate": (1, 0), "cobbled_deepslate": (4, 0),
        "deepslate_coal_ore": (16, 0), "deepslate_iron_ore": (15, 0),
        "deepslate_copper_ore": (200, 0), "deepslate_gold_ore": (14, 0),
        "deepslate_redstone_ore": (73, 0), "deepslate_emerald_ore": (129, 0),
        "deepslate_lapis_ore": (21, 0), "deepslate_diamond_ore": (56, 0),
        "copper_ingot": (962, 0), "raw_iron": (265, 0), "raw_gold": (266, 0), "raw_copper": (962, 0),
        "netherite_ingot": (965, 0), "netherite_scrap": (960, 0),
        "amethyst_shard": (1119, 0), "echo_shard": (1119, 0), "prismarine_shard": (1119, 0),
        "prismarine_crystals": (406, 0), "disc_fragment_5": (1119, 0),
        "armadillo_scute": (288, 0), "turtle_scute": (288, 0), "phantom_membrane": (288, 0),
        "rabbit_hide": (334, 0), "rabbit_foot": (370, 0), "shulker_shell": (370, 0),
        "honeycomb": (353, 0), "popped_chorus_fruit": (260, 0),
        "dragon_breath": (374, 0), "heart_of_the_sea": (399, 0), "nautilus_shell": (370, 0),
        "wind_charge": (385, 0), "trial_key": (345, 0), "ominous_trial_key": (345, 0),
        "goat_horn": (256, 0), "armor_stand": (321, 0), "end_crystal": (122, 0),
        "command_block_minecart": (328, 0), "knowledge_book": (387, 0),
        "totem_of_undying": (256, 0), "debug_stick": (256, 0), "bundle": (256, 0),
        "warped_fungus_on_a_stick": (398, 0),
    }
    for name, (sid, meta) in adaptations.items():
        mid = "minecraft:" + name
        if mid not in maps:
            add(mid, sid, meta, "modern_adaptation", "explicit_modern_peer", "现代同族物品；数值是适配，不声称 R196 存在该物品。")

    # Metadata splitting does not cover invalid/unused source metadata slots.
    # Snow is the one authentic dynamic Item.getItemStackLimit override.
    maps["minecraft:snow"]["state_rules"] = [
        {"condition": "BLOCK_STATE.layers absent or 1", "max_stack_size": limit(78, 0), "source_subtype": 0},
        {"condition": "BLOCK_STATE.layers 2..8", "max_stack_size": limit(78, 1), "source_subtype": "1..7"},
    ]
    maps["minecraft:snow"]["dynamic_source_items"] = [{"legacy_id": 78, "subtype": subtype, "max_stack_size": limit(78, subtype)} for subtype in range(1, 8)]
    used[78].update(range(1, 8))

    # Every current native Item receives either a reviewed source-family mapping or
    # an explicit retained-modern-default disposition. The latter avoids invented
    # claims for new functional containers and equipment with no R196 counterpart.
    retained = []
    for mid, row in sorted(modern_items.items()):
        if mid in maps:
            continue
        name = mid.split(":", 1)[1]
        impl = row.get("declared_implementation", "")
        sid = None
        meta = 0
        evidence = None
        if name.endswith("_spawn_egg"):
            sid, evidence = 383, "source_spawn_egg_family"
        elif name.endswith("_pottery_sherd"):
            sid, evidence = 1119, "source_shard_family_modern_pottery_fragment"
        elif name.endswith("_banner"):
            sid, evidence = 35, "source_cloth_decoration_family_modern_banner"
        elif name.endswith("_banner_pattern") or name.endswith("_smithing_template"):
            sid, evidence = 339, "source_paper_design_family_modern_pattern"
        elif name.startswith("music_disc_"):
            sid, evidence = 2256, "source_record_family"
        elif name.endswith("_bed"):
            sid, evidence = 355, "source_bed_colour_split"
        elif name.endswith("_door"):
            sid, evidence = (1111 if "copper" in name else 324), "source_carried_door_family"
        elif name.endswith("_sign") and not name.endswith("_hanging_sign"):
            sid, evidence = 323, "source_sign_wood_family"
        elif name.endswith("_hanging_sign"):
            sid, evidence = 323, "source_sign_family_modern_hanging_variant"
        elif name.endswith("_chest_boat") or name.endswith("_chest_raft"):
            sid, evidence = 333, "source_vehicle_family_modern_chest_variant"
        elif name.endswith("_boat") or name.endswith("_raft"):
            sid, evidence = 333, "source_boat_wood_family"
        elif name.endswith("_slab"):
            sid, evidence = (126 if any(name.startswith(x + "_") for x in "oak spruce birch jungle acacia dark_oak mangrove cherry bamboo crimson warped".split()) else 44), "source_half_slab_family"
        elif name.endswith("_carpet"):
            sid, evidence = 171, "source_carpet_family"
        elif name.endswith("_wool"):
            sid, evidence = 35, "source_wool_colour_family"
        elif name.endswith("_planks") or name.endswith("_mosaic"):
            sid, evidence = 5, "source_plank_family"
        elif name.endswith("_sapling") or name in {"mangrove_propagule", "azalea", "flowering_azalea"}:
            sid, evidence = 6, "source_sapling_family"
        elif name.endswith("_leaves"):
            sid, evidence = 18, "source_leaf_family"
        elif name.endswith("_fence"):
            sid, evidence = 85, "source_fence_family"
        elif name.endswith("_wall"):
            sid, evidence = 139, "source_wall_family"
        elif name.endswith("_pane"):
            sid, evidence = 102, "source_glass_pane_family"
        elif name.endswith("_pressure_plate"):
            sid, evidence = 70, "source_pressure_plate_family"
        elif name.endswith("_anvil"):
            sid, evidence = 145, "source_anvil_damage_split"
        elif name in {"blast_furnace", "smoker"}:
            sid, evidence = 61, "source_furnace_family_modern_processor"
        elif name in {"tall_grass", "large_fern"}:
            sid, meta, evidence = 31, 1, "source_grass_family_modern_double_height"
        elif name in {"rose_bush", "peony", "lilac", "sunflower", "azure_bluet", "cornflower", "lily_of_the_valley", "wither_rose", "pink_petals", "torchflower", "pitcher_plant", "red_tulip", "white_tulip", "pink_tulip"}:
            sid, evidence = 38, "source_flower_family"
        elif name.endswith("_seeds") or name in {"torchflower_seeds", "pitcher_pod"}:
            sid, evidence = 295, "source_seed_family"
        elif name.endswith("_bars") or name.endswith("_grate"):
            sid, evidence = 101, "source_metal_bar_family"
        elif name.endswith("_bucket"):
            sid, evidence = 326, "source_filled_bucket_family"
        elif row.get("category") == "food_requires_nutrition_mapping":
            if name in {"rabbit_stew", "suspicious_stew", "beetroot_soup"}:
                sid, evidence = 282, "source_filled_bowl_food_family"
            elif name in {"honey_bottle"}:
                sid, evidence = 374, "source_bottle_food_family"
            elif name == "ominous_bottle":
                sid, evidence = 1214, "source_functional_drink_family"
            else:
                sid, evidence = 260, "source_ordinary_food_family"
        elif row.get("block_symbol") and mid != "minecraft:air":
            # All remaining carried cubes, stairs, trapdoors, buttons, functional
            # blocks and their new materials share the source default Block limit4.
            sid, evidence = 1, "source_default_carried_block_family"
        elif impl in {"SwordItem", "PickaxeItem", "ShovelItem", "AxeItem", "HoeItem", "ArmorItem", "DyeableArmorItem", "HorseArmorItem", "AnimalArmorItem", "CrossbowItem", "TridentItem", "ShieldItem", "ElytraItem", "MaceItem", "BrushItem", "SpyglassItem", "FishingRodItem", "FoodOnAStickItem"}:
            sid, evidence = 256, "source_single_equipment_family"
        if sid is not None:
            add(mid, sid, meta, "modern_adaptation", evidence, "现代同族适配；保留新版使用行为。")
        else:
            retained.append({"modern_id": mid, "disposition": "retain_modern_default", "declared_implementation": impl,
                             "reason": "AIR 表示空物品；保持原版空堆语义。" if mid == "minecraft:air" else "全新非方块物品未找到可确认的 R196 同族；保留 1.21.1 默认上限，不以猜测数字充当复刻。"})

    source_dispositions = []
    exclusions = []
    gaps = []
    for sid, item in sorted(probed.items()):
        if sid in used:
            source_dispositions.append({"legacy_id": sid, "disposition": "mapped", "mapped_subtypes": sorted(used[sid])})
        else:
            if sid in WORLD_STATE_IDS:
                reason = "world_state_without_current_carried_identity"
            elif sid in EXCLUDED_CONFLICT_IDS:
                reason = "user_excluded_deleted_metal_knives"
            elif sid in UNIMPLEMENTED_SOURCE_IDS:
                reason = "source_registered_but_no_current_modern_item"
            else:
                reason = "identity_not_yet_confirmed"
            record = {"legacy_id": sid, "source_name": item.get("source_name"), "class": item["class"], "disposition": reason}
            source_dispositions.append(record)
            (gaps if reason == "identity_not_yet_confirmed" else exclusions).append(record)
    if conflicts:
        raise ValueError(f"Unresolved mapping conflicts: {len(conflicts)}")
    subtype_exclusions = [
        {"legacy_id": 24, "subtype": 3, "disposition": "source_sacred_sandstone_no_current_identity"},
        {"legacy_id": 97, "subtype": 4, "disposition": "source_copperspine_host_variant_no_current_identity"},
        *[{"legacy_id": 383, "subtype": subtype["subtype"], "disposition": "source_monster_spawn_egg_entity_not_yet_registered"}
          for subtype in probed[383]["subtype_limits"] if subtype["subtype"] >= 512],
    ]
    document = {
        "schema_version": 1, "author": "NingMeng123", "source_version": "MITE 1.6.4 R196",
        "archive_sha256": runtime["archive_sha256"], "source_probe": "spec/stacking-source-030.json",
        "input_sha256": {name: sha(name) for name in ["spec/stacking-source-030.json", "reference-data/runtime-registry.json", "spec/item-catalog-015.json", "spec/stacking-modern-items-030.json", "spec/r196-tools-016.json", "spec/ranged-027.json", "src/main/resources/mite/foods-018.json"]},
        "semantics": [
            "source mappings are actual item identities; metadata is carried subtype, never block orientation.",
            "modern_adaptation rows use explicit source-family values, not claims of R196 registry existence.",
            "World-only ItemBlock initialization does not override separately registered carried item limits.",
            "Item/component identity compatibility remains Minecraft 1.21.1, retaining distinct modern data components.",
            "No third-party item ID is matched by family heuristics at runtime; adaptations are frozen explicit vanilla IDs.",
            "Unknown new non-block items retain modern defaults and are separately disclosed.",
        ],
        "summary": {"source_items": len(probed), "source_items_mapped": len(used), "source_items_excluded": len(exclusions), "source_items_gaps": len(gaps),
                    "mapped_modern_items": len(maps), "mapping_kinds": dict(collections.Counter(x["mapping_kind"] for x in maps.values())),
                    "modern_retained_default": len(retained), "excluded_subtypes_of_mapped_sources": len(subtype_exclusions), "conflicting_modern_limits": len(conflicts)},
        "mappedItems": sorted(maps.values(), key=lambda x: x["modern_id"]), "source_dispositions": source_dispositions,
        "exclusions": exclusions, "subtype_exclusions": subtype_exclusions, "gaps": gaps, "modern_retained_defaults": retained, "conflicts": conflicts,
    }
    output = ROOT / "spec/stacking-map-030.json"
    output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(document["summary"], ensure_ascii=False))
    if gaps:
        print("Unconfirmed source identities:", [(x["legacy_id"], x["source_name"]) for x in gaps])


if __name__ == "__main__":
    main()
