"""Bind reviewed vanilla recipe IDs to final R196 whole-batch costs. Author: NingMeng123.

Uses only the saved R196 registry and a locally installed Minecraft 1.21.1 jar.
No source item stack-limit mappings, inferred component costs, or downloads are used.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import struct
import math
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RESOURCE = "src/main/resources/mite/timed-crafting-r196-common.json"
AUDIT = "spec/r196-common-crafting-audit.json"
DOCUMENT = "docs/common-crafting-times.md"
COLORS = "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split()
WOODS = "oak spruce birch jungle acacia dark_oak mangrove cherry bamboo crimson warped".split()
STATIC_TYPES = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless"}


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def default_jar():
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    return gradle_home / "caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"


def vanilla_recipes(jar):
    with zipfile.ZipFile(jar) as archive:
        return {"minecraft:" + Path(name).stem: json.loads(archive.read(name))
                for name in archive.namelist()
                if name.startswith("data/minecraft/recipe/") and name.endswith(".json")}


def existing_rules():
    result = {}
    for path in sorted((ROOT / "src/main/resources/mite").glob("timed-crafting-*.json")):
        if path.name == Path(RESOURCE).name:
            continue
        for recipe_id, rule in json.loads(path.read_text(encoding="utf-8")).get("recipes", {}).items():
            result.setdefault(recipe_id, []).append({"resource": path.name, **rule})
    return result


def declarations():
    """Each row is a reviewed recipe/family binding, never an output-cost lookup."""
    rows = {}

    def add(order, ids, group, note=None, family=False):
        for recipe_id in ids.split():
            recipe_id = "minecraft:" + recipe_id
            if recipe_id in rows:
                raise ValueError("Duplicate declaration " + recipe_id)
            rows[recipe_id] = {"source_order": order, "group": group,
                               "mapping_kind": "family_adaptation" if family else ("source_batch_migration" if note else "source_recipe"),
                               "adaptation_note": note}

    # Direct, individually reviewed identities. The legacy final cost is retained
    # even where the modern recipe changes its input ingredients or batch yield.
    for order, recipe_id in [
        (43, "gold_block"), (44, "iron_block"), (48, "diamond_block"),
        (49, "emerald_block"), (50, "lapis_block"), (51, "redstone_block"),
        (52, "coal_block"), (53, "hay_block"), (470, "diamond"),
        (471, "emerald"), (472, "lapis_lazuli"), (473, "redstone"),
        (474, "coal"), (475, "wheat"),
    ]:
        add(order, recipe_id, "storage_and_dismantling")
    for order, recipe_id in [
        (67, "ender_chest"), (68, "chest"), (105, "redstone_lamp"),
        (106, "beacon"), (157, "jukebox"), (158, "lead"),
        (160, "note_block"), (161, "bookshelf"), (164, "tnt"),
        (166, "rail"), (167, "powered_rail"), (168, "activator_rail"),
        (169, "detector_rail"), (170, "cauldron"), (182, "painting"),
        (183, "item_frame"), (186, "glistering_melon_slice"),
        (187, "comparator"), (190, "map"), (191, "dispenser"),
        (192, "dropper"), (193, "piston"), (194, "enchanting_table"),
        (197, "daylight_detector"), (198, "hopper"), (233, "iron_bars"),
        (278, "bowl"), (279, "glass_bottle"), (280, "minecart"),
        (281, "brewing_stand"), (283, "flower_pot"), (284, "repeater"),
        (388, "paper"), (402, "tripwire_hook"), (427, "trapped_chest"),
        (448, "stick"), (449, "torch"), (451, "jack_o_lantern"),
        (452, "chest_minecart"), (453, "furnace_minecart"),
        (454, "tnt_minecart"), (455, "hopper_minecart"), (456, "lever"),
        (457, "redstone_torch"), (458, "stone_pressure_plate"),
        (460, "heavy_weighted_pressure_plate"), (461, "light_weighted_pressure_plate"),
        (462, "sticky_piston"), (507, "stone_button"), (702, "book"),
        (706, "fermented_spider_eye"), (755, "writable_book"),
        (756, "fire_charge"), (759, "magma_cream"), (762, "ender_eye"),
        (849, "blaze_powder"),
    ]:
        add(order, recipe_id, "utility_and_redstone")
    add(165, "ladder", "utility_and_redstone",
        "R196 produces 2 ladders; modern produces 3 from the same seven sticks. Keep the R196 whole-batch difficulty.")
    add(188, "clock", "utility_and_redstone",
        "R196 uses eight gold nuggets around redstone; modern uses four gold ingots. Migrate the final source batch difficulty and gold bench requirement without recalculation.")
    add(189, "compass", "utility_and_redstone",
        "R196 uses eight iron nuggets around redstone; modern uses four iron ingots. Migrate the final source batch difficulty and iron bench requirement without recalculation.")
    add(222, "iron_door", "utility_and_redstone",
        "Six iron ingots produce 1 door in R196 and 3 doors in modern Minecraft. Preserve the final source whole-batch difficulty and bench requirement; do not divide by yield.")
    add(901, "bone_meal", "dyes_and_textiles",
        "R196 one bone produces 1 bone meal; modern produces 3. Keep the final source whole-batch difficulty.")

    for order, recipe_id, note in [
        (162, "bricks", "R196 uses eight bricks and sand to produce 2 blocks; modern uses four bricks to produce 1. Explicit migration of the final source batch cost."),
        (163, "nether_bricks", "R196 uses eight nether bricks and soul sand to produce 2 blocks; modern uses four nether bricks to produce 1. Explicit migration of the final source batch cost."),
        (273, "cobblestone_wall", "R196 six blocks produce 8 walls; modern six blocks produce 6. Keep the source batch cost."),
        (274, "mossy_cobblestone_wall", "R196 six blocks produce 8 walls; modern six blocks produce 6. Keep the source batch cost."),
        (275, "nether_brick_fence", "R196 six nether brick blocks produce 8 fences; modern four blocks and two nether brick items produce 6. Keep the source batch cost."),
        (304, "quartz_block", "R196 records four quartz component stacks with count 4; modern has four single quartz slots. Preserve final registered difficulty, without multiplying component or output counts."),
        (355, "cut_sandstone", None),
        (356, "stone_bricks", "Four stone blocks produce 2 stone bricks in R196 and 4 in modern Minecraft. Keep the source whole-batch difficulty."),
        (359, "glowstone", None), (360, "white_wool_from_string", None),
        (389, "cobblestone_slab", None), (390, "smooth_stone_slab", "The old stone slab identity is the modern smooth-stone slab. Its modern input is smooth stone; keep the final R196 stone-input slab batch cost."),
        (391, "sandstone_slab", None), (392, "brick_slab", None),
        (393, "stone_brick_slab", None), (394, "nether_brick_slab", None),
        (395, "quartz_slab", None),
        (418, "snow_block", "R196 combines two four-layer snow piles into a block; modern combines four snowballs. Keep this explicitly migrated source block-making batch cost."),
        (428, "chiseled_sandstone", None), (429, "chiseled_quartz_block", None),
        (430, "quartz_pillar", None),
        (482, "snow", "R196 one snow block produces two four-layer piles; modern three blocks produce six one-layer snow items. Keep the final source layer-making batch difficulty for one modern batch."),
        (500, "glass_pane", "R196 one glass block produces 6 panes; modern six blocks produce 16. Preserve the final source batch difficulty rather than inferring a new component sum."),
    ]:
        add(order, recipe_id, "building_materials", note)

    for order, recipe_id in [(175, "cobblestone_stairs"), (176, "brick_stairs"),
                             (177, "stone_brick_stairs"), (178, "nether_brick_stairs"),
                             (179, "sandstone_stairs"), (180, "quartz_stairs")]:
        add(order, recipe_id, "building_materials")
    # Closely identified material variants, not arbitrary new rock families.
    for order, ids, note in [
        (175, "mossy_cobblestone_stairs", "Moss-covered variant of the source cobblestone stair family."),
        (177, "mossy_stone_brick_stairs", "Moss-covered variant of the source stone-brick stair family."),
        (179, "red_sandstone_stairs smooth_sandstone_stairs smooth_red_sandstone_stairs", "Red and smoothed variants retain the source sandstone stair batch benchmark."),
        (180, "smooth_quartz_stairs", "Smoothed variant retains the source quartz stair batch benchmark."),
        (389, "mossy_cobblestone_slab", "Moss-covered variant of the source cobblestone slab family."),
        (390, "stone_slab", "Modern stone slab uses the source three-stone slab manufacturing benchmark; the legacy slab identity otherwise maps to smooth_stone_slab."),
        (391, "red_sandstone_slab smooth_sandstone_slab smooth_red_sandstone_slab cut_sandstone_slab cut_red_sandstone_slab", "Red, cut and smoothed variants retain the source sandstone slab batch benchmark."),
        (393, "mossy_stone_brick_slab", "Moss-covered variant of the source stone-brick slab family."),
        (395, "smooth_quartz_slab", "Smoothed variant retains the source quartz slab batch benchmark."),
        (355, "cut_red_sandstone", "Red-sandstone counterpart of the old cut-sandstone recipe."),
        (428, "chiseled_red_sandstone", "Red-sandstone counterpart of the old chiseled-sandstone recipe."),
    ]:
        add(order, ids, "building_materials", note, family=True)

    for wood in WOODS:
        family_note = "Modern wood species retain the explicitly selected R196 wooden recipe family cost. "
        old_species = wood in {"oak", "spruce", "birch", "jungle"}
        add({"spruce": 173, "birch": 172, "jungle": 174}.get(wood, 171), wood + "_stairs", "wooden_building",
            None if old_species else family_note, family=not old_species)
        add({"spruce": 398, "birch": 397, "jungle": 399}.get(wood, 396), wood + "_slab", "wooden_building",
            None if old_species else family_note, family=not old_species)
        for order, suffix, note in [
            (218, "door", "R196 six planks produce 1 door; modern six planks produce 3. Retain the source whole-batch difficulty."),
            (272, "fence", "R196 six sticks produce 2 fences; modern four planks and two sticks produce 3. Explicitly migrate the source whole-batch difficulty, without repricing the new inputs."),
            (276, "fence_gate", None), (277, "trapdoor", None),
            (447, "sign", "R196 one wooden slab plus a stick produces 1 sign; modern six planks plus a stick produce 3. Explicit migration of the final source batch cost."),
            (459, "pressure_plate", None), (508, "button", None),
        ]:
            add(order, wood + "_" + suffix, "wooden_building",
                family_note + (note or "The operation and modern batch remain unchanged."), family=True)
        if wood not in {"bamboo", "crimson", "warped"}:
            add(282, wood + "_boat", "wooden_building", family_note + "Five matching planks make one boat.", family=True)
    add(282, "bamboo_raft", "wooden_building", "Modern bamboo raft uses the R196 five-plank wooden boat family batch benchmark.", family=True)
    # Bamboo crafting has no R196 source, but the alternative stick route must not
    # silently bypass the explicitly migrated stick family cost.
    add(448, "stick_from_bamboo_item", "wooden_building",
        "Modern alternative two-bamboo route produces 1 stick, rather than two-plank route producing 4. Explicitly retain the source stick-manufacturing whole-batch benchmark.", family=True)

    for meta, color in enumerate(COLORS):
        add(285, color + "_bed", "beds", "Modern colored beds share R196's three-wool, three-plank bed recipe and final whole-batch cost.", family=True)
        add(154 - meta, color + "_terracotta", "dyes_and_textiles")
        add(431 + meta, color + "_carpet", "dyes_and_textiles")
        add(826 - meta, "dye_" + color + "_wool", "dyes_and_textiles",
            "Modern explicit dye items replace legacy dye subtypes and the input accepts recoloring any wool; retain the matching R196 white-wool dyeing batch cost.", family=True)
        add(826 - meta, "dye_" + color + "_bed", "beds",
            "Modern bed recoloring is a textile-dyeing adaptation of the matching R196 wool-dyeing operation; it is not manufacturing another bed.", family=True)
        add(826 - meta, "dye_" + color + "_carpet", "dyes_and_textiles",
            "Modern eight-carpet recoloring batch uses the matching R196 wool-dyeing operation benchmark; preserve the whole source difficulty without multiplying or dividing by eight.", family=True)

    for order, recipe_id in [
        (701, "magenta_dye_from_blue_red_white_dye"),
        (753, "light_gray_dye_from_black_white_dye"),
        (754, "magenta_dye_from_blue_red_pink"),
        (827, "pink_dye_from_red_white_dye"), (828, "orange_dye_from_red_yellow"),
        (829, "lime_dye"), (830, "gray_dye"),
        (831, "light_gray_dye_from_gray_white_dye"),
        (832, "light_blue_dye_from_blue_white_dye"), (833, "cyan_dye"),
        (834, "purple_dye"), (835, "magenta_dye_from_purple_and_pink"),
    ]:
        add(order, recipe_id, "dyes_and_textiles",
            "Modern separate dye item identities replace legacy dye subtypes (including lapis/ink/bone-meal dye identities); use the corresponding registered whole mixing-batch difficulty.")
    for order, recipe_id in [(894, "yellow_dye_from_dandelion"), (895, "red_dye_from_poppy"),
                             (896, "light_blue_dye_from_blue_orchid"), (897, "magenta_dye_from_allium"),
                             (898, "orange_dye_from_orange_tulip"), (900, "light_gray_dye_from_oxeye_daisy")]:
        add(order, recipe_id, "dyes_and_textiles")
    for order, ids in [
        (894, "yellow_dye_from_sunflower"),
        (895, "red_dye_from_tulip red_dye_from_rose_bush"),
        (896, "blue_dye_from_cornflower"), (897, "magenta_dye_from_lilac"),
        (898, "orange_dye_from_torchflower"),
        (899, "pink_dye_from_peony pink_dye_from_pink_tulip"),
        (900, "light_gray_dye_from_azure_bluet light_gray_dye_from_white_tulip"),
    ]:
        add(order, ids, "dyes_and_textiles",
            "Modern flower species/yield use the selected source flower-to-dye preparation family benchmark; the source zero difficulty still has the existing minimum 25-tick period.", family=True)
    return rows


def f32(value):
    return struct.unpack("f", struct.pack("f", value))[0]


def ticks(difficulty):
    base = (math.floor(math.pow(f32(difficulty - 100), f32(.8)) + .5) + 100
            if difficulty > 100 else max(25, math.floor(difficulty + .5)))
    return base, max(25, int(f32(f32(base) / f32(1.2))))


def generate(jar):
    source = read("reference-data/runtime-registry.json")
    source_recipes = source["crafting_recipes"]
    vanilla = vanilla_recipes(jar)
    existing = existing_rules()
    rules, audit_rows, skipped = {}, {}, {}
    declared = declarations()
    for recipe_id, declaration in sorted(declared.items()):
        if recipe_id not in vanilla:
            raise ValueError("Declared recipe is absent from local Minecraft 1.21.1 jar: " + recipe_id)
        modern = vanilla[recipe_id]
        if modern["type"] not in STATIC_TYPES:
            raise ValueError("Not a static crafting recipe: " + recipe_id)
        origin = "minecraft_1.21.1_client.jar:" + recipe_id
        for tree in ["src/generated/resources", "src/main/resources"]:
            path = ROOT / tree / "data/minecraft/recipe" / (recipe_id.split(":")[1] + ".json")
            if path.is_file():
                modern = json.loads(path.read_text(encoding="utf-8"))
                origin = path.relative_to(ROOT).as_posix()
        if any(c.get("type") == "neoforge:false" for c in modern.get("neoforge:conditions", [])):
            skipped[recipe_id] = {"reason": "disabled_recipe", "modern_origin": origin}
            continue
        if recipe_id in existing:
            skipped[recipe_id] = {"reason": "existing_explicit_timing_has_priority", "rules": existing[recipe_id]}
            continue
        row = source_recipes[declaration["source_order"]]
        assert row["order"] == declaration["source_order"]
        assert row["class"] in {"aai", "aaj"} and not row["dynamic"]
        assert row["difficulty"] >= 0
        rules[recipe_id] = {"difficulty": row["difficulty"],
                            "required_bench": (row["bench_material"] or {}).get("name"),
                            "source_order": row["order"]}
        source_count, modern_count = row["output"]["count"], modern["result"].get("count", 1)
        audit_rows[recipe_id] = {**declaration, **rules[recipe_id],
                                "source_output_count": source_count, "modern_output_count": modern_count,
                                "output_count_changed": source_count != modern_count,
                                "modern_origin": origin, "modern_recipe": modern}

    required = {"minecraft:chest", "minecraft:stick", "minecraft:bowl",
                *("minecraft:" + color + "_bed" for color in COLORS)}
    assert required <= (rules.keys() | existing.keys()), "Missing core reported recipe"
    assert not rules.keys() & existing.keys(), "Never overwrite an existing rule"
    summary = {"added_recipe_ids": len(rules), "source_recipe_orders": len({r["source_order"] for r in rules.values()}),
               "mapping_kinds": dict(collections.Counter(x["mapping_kind"] for x in audit_rows.values())),
               "groups": dict(collections.Counter(x["group"] for x in audit_rows.values())),
               "output_count_changed_recipe_ids": sum(x["output_count_changed"] for x in audit_rows.values()),
               "skipped_existing_or_disabled": len(skipped)}
    runtime_resource = {"author": "NingMeng123", "source": "reference-data/runtime-registry.json final whole-batch difficulty; reviewed mappings in spec/r196-common-crafting-audit.json",
                        "recipes": rules}
    excluded_ids = [name for name in [
        "crafter", "observer", "target", "lightning_rod", "respawn_anchor", "lodestone", "scaffolding",
        "chiseled_bookshelf", "decorated_pot", "iron_trapdoor", "tinted_glass", "spyglass", "end_crystal",
        "bamboo_mosaic", "bamboo_mosaic_slab", "bamboo_mosaic_stairs", "pink_dye_from_pink_petals",
        "cyan_dye_from_pitcher_plant", "white_dye", "black_dye", "blue_dye", "brown_dye", "bone_meal_from_bone_block",
        "netherite_ingot", "netherite_block", "netherite_ingot_from_netherite_block", "sugar_from_honey_bottle",
        *[wood + "_hanging_sign" for wood in WOODS],
        *[wood + "_chest_boat" for wood in WOODS], "bamboo_chest_raft",
    ] if "minecraft:" + name in vanilla and "minecraft:" + name not in rules and "minecraft:" + name not in existing]
    audit = {"schema_version": 1, "author": "NingMeng123", "source_version": "R196", "modern_version": "1.21.1",
             "archive_sha256": source["archive_sha256"],
             "provenance": {"runtime_registry_sha256": digest((ROOT / "reference-data/runtime-registry.json").read_bytes()),
                            "minecraft_client_jar_sha256": digest(jar.read_bytes()),
                            "generator_sha256": digest(Path(__file__).read_bytes()),
                            "uses_stacking_map_for_crafting_costs": False, "game_main_invoked": False},
             "summary": summary, "recipes": audit_rows,
             "source_recipes": {str(order): source_recipes[order] for order in sorted({r["source_order"] for r in rules.values()})},
             "expected_recipe_ids": sorted(rules), "skipped_declarations": skipped,
             "not_expected_recipe_ids": ["minecraft:" + name for name in sorted(excluded_ids)],
             "not_expected_recipe_ids_meaning": "Verified existing vanilla IDs without a rule in this supplement or previous explicit timing tables; these are intentional boundaries of this source-only supplement, not assertions about runtime fallback handling.",
             "no_matching_modern_recipe": [
                 {"source_order": order, "reason": reason} for order, reason in [
                     (66, "Saddle has no Minecraft 1.21.1 crafting recipe."),
                     (217, "Glass-pane to glass reconstruction has no vanilla crafting recipe."),
                     (307, "Cobblestone to stone crafting has no vanilla recipe; modern stone is smelted."),
                     (308, "The legacy four-layer snow-pile recipe has no identical modern recipe."),
                     (309, "Four one-layer snow piles to one four-layer pile has no identical modern recipe."),
                     (358, "Brick-item to hardened clay has no vanilla crafting recipe; modern terracotta is smelted."),
                     (476, "Quartz-block dismantling has no vanilla crafting recipe."),
                     (479, "Snow-pile to snowball dismantling has no vanilla crafting recipe."),
                     (480, "Single snowball to snow layer has no vanilla crafting recipe."),
                     (481, "Four-layer pile to single-layer piles has no identical modern recipe."),
                     (501, "Hardened-clay dismantling to brick items has no vanilla crafting recipe."),
                     (760, "Gold-nugget gilded sandstone recipe has no vanilla counterpart."),
                     (761, "Gold-nugget gilded cut-sandstone recipe has no vanilla counterpart."),
                     (836, "Dandelion plus old red flower to orange dye has no direct modern counterpart."),
                 ]],
             "dynamic_source_boundaries": [
                 {"source_order": 0, "source_type": "zw", "difficulty": 100.0, "meaning": "armor dyeing; existing runtime quality path"},
                 {"source_order": 1, "source_type": "zz", "difficulty": 100.0, "meaning": "fireworks; modern rocket/star/fade serializers require explicit dynamic handling"},
                 {"source_order": 155, "source_type": "aac", "difficulty": 100.0, "meaning": "map cloning; dynamic output is not a static empty map recipe"},
                 {"source_order": 156, "source_type": "aad", "difficulty": 300.0, "meaning": "map extending; registered empty-map output is a placeholder for copied/scaled filled map"},
             ],
             "boundaries": [
                 "Every stored difficulty is the final registered whole-batch source float. Never multiply/divide by output count, or recompute from source component stack count.",
                 "Existing explicit timing tables have priority. The supplement only adds absent recipe IDs; it creates no recipes and changes no ingredients or yields.",
                 "Modern wood, color and reviewed same-material decorative families are declared adaptations, not assertions that those items existed in R196.",
                 "Input and yield migrations retain the selected old whole-batch benchmark; both original source and effective modern recipe snapshots are stored for review.",
                 "Source zero difficulty uses the existing minimum 25-tick formula; it is never a permission for instant crafting.",
                 "The stacking identity map is deliberately not an input: stack-limit fallback families are not manufacturing-cost evidence.",
                 "Only explicit reviewed vanilla static recipe IDs are bound. Equipment, food, upgrades and dynamic serializers may have independent runtime rules; an absent explicit ID is not proof they craft instantly.",
                 "Pure modern materials, copper/weathering/waxing families, new rock/prismarine/deepslate/tuff blocks, banners, maps/books/arrows special recipes and external mod recipes need separate reviewed policies or runtime auditing.",
                 "No furnace, stonecutting, smithing or brewing recipe is assigned a crafting-grid time by this supplement.",
             ]}
    core_rows = []
    for name in ["chest", "stick", "bowl", "white_bed"]:
        rule = rules.get("minecraft:" + name) or existing["minecraft:" + name][0]
        base, bench = ticks(rule["difficulty"])
        core_rows.append(f"| `{name}` | {rule['source_order']} | {rule['difficulty']:g} | {base} | {bench} / {bench / 20:g} 秒 |")
    document = f"""# 常见原版配方制造计时补齐

开发者：NingMeng123。

本次补充 {summary['added_recipe_ids']} 个原版静态配方 ID，引用 {summary['source_recipe_orders']} 条 R196 注册配方的最终整批 difficulty 和工作台材料门槛。普通箱子、木棍、碗及全部 16 色床已列入。此前未登记的普通原版配方会从 `CraftingQuality.rule` 返回空规则，绕过已有计时；补表沿用现有服务端制造系统。

| 配方 | R196 order | 整批 difficulty | 0 级背包公式 tick | 0 级基础工作台 tick / 秒 |
|---|---:|---:|---:|---:|
{chr(10).join(core_rows)}

木棍一次出 4 根、碗一次出 4 个，difficulty 不随产量再次乘除。箱子、碗和床受实际网格大小限制，表中的背包列仅说明公式。等级、诅咒和现有工作台加速继续由现有规则计算。

补齐范围包括储存块及拆分、红石与矿车铁轨、书本和容器、火把、木制建筑、来源明确的石制装饰、羊毛/地毯/陶瓦与混色染料。原有计时表优先，生成器跳过已有规则的 ID。

首次实际 RecipeManager 审计确认全部 350 个补表 ID 已加载，没有不存在的补表 ID；当时统计为 1071 个有计时的静态配方、445 个仍即时的静态配方，以及 12 个需要真实输入另行检查的动态配方。复核还发现静态的 `firework_rocket_simple` 与 R196 动态烟花入口具有明确来源对应，已由运行时单独沿用 order 1 的整批 difficulty 100，静态计时总数因此为 1072，仍即时的静态条目为 444。剩余项包括现代专属的箱船、木桶、自动合成器、侦测器、悬挂告示牌及铜风化/涂蜡等操作。本轮没有把这些现代专属操作全部套用猜测成本；这个补表不代表全体合成配方均已计时。最终逐项状态以[实际运行时配方清单](../spec/common-crafting-runtime-audit.json)和[验证记录](testing/common-crafting-times.md)为准。

现代木种、16 色床和明确的同材料装饰变体采用声明的来源配方族。输入或产量发生变化时仍按明确选定的 R196 整批值迁移：木栅栏从六木棍变为四木板两木棍，门从每批 1 扇变为 3 扇，告示牌从半砖加木棍变为六木板加木棍，玻璃板从单玻璃变为六玻璃；这些区别逐项保存在审计中，未改配方材料或产量。床重新染色使用相应羊毛染色操作的来源基准，不按再次制造床计算。

未把堆叠映射当作制造成本来源，也未给未知配方任意设成 25 tick。来源实际 difficulty 为 0 的花染料和红石拆块，仍经过现有最低 25 tick 的计时公式。烟花、地图等动态配方独立于本静态表；纯现代方块、材料和特殊合成需要另行核对。旧版有而现代没有的合成没有新增配方。

生成器为 `tools/specs/generate_common_crafting_times.py`，默认只读取本地 Gradle 缓存的 Minecraft 1.21.1 client jar，并校验所有声明 ID 确实存在、属于静态合成配方。可使用 `--minecraft-jar` 指定本地文件，`--check` 核对已保存结果。运行不联网，不启动游戏。

`spec/r196-common-crafting-audit.json` 保存来源完整配方、当前原版/覆盖配方快照、现代适配说明、既有规则跳过项、确实存在的现代边界 ID 和输入哈希。`not_expected_recipe_ids` 表示本补表及已有显式表未接入的已核实原版 ID；它不是运行时秒合成断言，装备、食品、动态配方仍可能走独立规则。完整剩余项以实际 RecipeManager/运行时审计为准。

生成器的静态校验只能证明来源与映射一致；实际菜单、N−1/N 完成边界、连续制造、自动合成器和 JEI 仍由本轮相应游戏测试确认，本文件不宣称实机验收通过。
"""
    return {RESOURCE: runtime_resource, AUDIT: audit, DOCUMENT: document}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-jar", type=Path, default=default_jar())
    parser.add_argument("--check", action="store_true", help="Compare generated output without writing files")
    args = parser.parse_args()
    if not args.minecraft_jar.is_file():
        parser.error("Minecraft 1.21.1 jar must already exist locally; provide --minecraft-jar")
    outputs = generate(args.minecraft_jar)
    for path, value in outputs.items():
        text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2) + "\n"
        destination = ROOT / path
        if args.check:
            if not destination.is_file() or destination.read_text(encoding="utf-8") != text:
                raise SystemExit("Generated output is stale: " + path)
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(text, encoding="utf-8")
    print(json.dumps(outputs[AUDIT]["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
