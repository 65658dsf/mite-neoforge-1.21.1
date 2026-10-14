"""Index traced R196 animal AI and reproduce its independent source specification.

Author: NingMeng123. Reads local reference bytes only; never starts a legacy JVM,
world, game or renderer. Full decompiled bodies are deliberately not exported.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / ".reference/r196/decompiled"
BASE = ROOT / ".reference/downloads/minecraft-1.6.4-client.jar"
BASE_SHA = "f4513e51c766cdd1d32ca8acd0835d7e16e2851ebea3f2a3b4ce5ae696baf3ae"
ARCHIVE_SHA = "fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3"
ACTORS = {
    "ro": (65, "Bat", ["minecraft:bat"]),
    "ry": (90, "Pig", ["minecraft:pig"]),
    "rz": (91, "Sheep", ["minecraft:sheep"]),
    "rr": (92, "Cow", ["minecraft:cow"]),
    "rq": (93, "Chicken", ["minecraft:chicken"]),
    "sc": (94, "Squid", ["minecraft:squid"]),
    "sf": (95, "Wolf", ["minecraft:wolf"]),
    "rw": (96, "MushroomCow", ["minecraft:mooshroom"]),
    "rx": (98, "Ozelot", ["minecraft:ocelot", "minecraft:cat"]),
    "rs": (100, "EntityHorse", ["minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:zombie_horse", "minecraft:skeleton_horse"]),
    "EntityHellhound": (516, "Hellhound", ["mite:hellhound"]),
    "EntityDireWolf": (517, "DireWolf", ["mite:dire_wolf"]),
    "EntityVampireBat": (529, "VampireBat", ["mite:vampire_bat"]),
    "EntityGiantVampireBat": (530, "GiantVampireBat", ["mite:giant_vampire_bat"]),
    "EntityNightwing": (533, "Nightwing", ["mite:nightwing"]),
}
SUPPORT = ["EntityLivestock", "rp", "nk", "oq", "og", "on", "of", "nn", "abw", "adr", "rf", "rh", "RNG", "ItemVessel", "nt", "aqz", "yc", "pn", "pk", "qu", "qq", "ps", "qr", "pr", "EntityDemonSpider", "EntityFireElemental", "sb", "ub",
           "EntityAIMovementTask", "EntityAIMoveToItem", "EntityAIMoveToFoodItem", "EntityAISeekFoodIfHungry", "EntityAISeekWaterIfThirsty", "EntityAISeekOpenSpaceIfCrowded", "EntityAIFleeAttackerOrPanic", "EntityAIAvoidPotentialPredators", "EntityAIAvoidFire", "EntityAIGetOutOfWater", "EntityAIGetOutOfLava", "EntityAISeekShelterFromRain"]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def method_index(text: str) -> list[dict]:
    masked = re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*', lambda m: " " * len(m.group()), text, flags=re.S)
    rows = []
    pattern = r"(?m)^    (?:(?:public|protected|private|static|final|abstract|synchronized)\s+)*(?:[\w<>\[\].?]+\s+)?(?P<name>\w+)\((?P<args>[^\n{}]*)\)\s*\{"
    for match in re.finditer(pattern, masked):
        opening = masked.find("{", match.start(), match.end())
        end, depth = opening + 1, 1
        while depth and end < len(masked):
            depth += (masked[end] == "{") - (masked[end] == "}")
            end += 1
        rows.append({"name": match["name"], "arguments": match["args"].strip(), "signature": text[match.start():opening].strip(),
                     "line": text.count("\n", 0, match.start()) + 1, "end_line": text.count("\n", 0, end) + 1,
                     "method_sha256": sha(text[match.start():end].encode()), "_body": text[match.start():end]})
    return rows


def binary_metadata(data: bytes) -> dict:
    """Read identity/SourceFile and method Code hashes without loading classes."""
    offset = 0
    def take(n):
        nonlocal offset
        value = data[offset:offset+n]
        if len(value) != n:
            raise ValueError("Truncated class file")
        offset += n
        return value
    def u2(): return struct.unpack(">H", take(2))[0]
    def u4(): return struct.unpack(">I", take(4))[0]
    assert take(4) == b"\xca\xfe\xba\xbe"
    take(4)
    cp = [None] * u2()
    i = 1
    while i < len(cp):
        tag = take(1)[0]
        if tag == 1:
            cp[i] = take(u2()).decode("utf-8", errors="replace")
        elif tag in (7, 8, 16, 19, 20): cp[i] = u2()
        elif tag in (3, 4): take(4)
        elif tag in (5, 6): take(8); i += 1
        elif tag in (9, 10, 11, 12, 17, 18): take(4)
        elif tag == 15: take(3)
        else: raise ValueError(f"Unsupported constant-pool tag {tag}")
        i += 1
    take(2)
    this_class, parent_class = u2(), u2()
    parent = cp[cp[parent_class]] if parent_class else None
    take(u2() * 2)
    def attributes():
        return [(cp[u2()], take(u4())) for _ in range(u2())]
    for _ in range(u2()):
        take(6); attributes()
    methods = []
    for _ in range(u2()):
        flags, name, descriptor = u2(), cp[u2()], cp[u2()]
        attrs = attributes()
        methods.append({"name": name, "descriptor": descriptor, "access_flags": flags,
                        "code_sha256": next((sha(raw) for key, raw in attrs if key == "Code"), None)})
    attrs = attributes()
    return {"original_source_file": next((cp[struct.unpack(">H", raw)[0]] for key, raw in attrs if key == "SourceFile"), None),
            "parent": parent, "class_sha256": sha(data), "bytecode_methods": methods}


def extract() -> dict:
    assert sha(BASE.read_bytes()) == BASE_SHA
    archive = ROOT / "MITE 1.6.4 R196.zip"
    assert sha(archive.read_bytes()) == ARCHIVE_SHA
    classes, texts = {}, {}
    with zipfile.ZipFile(BASE) as baseline:
        def load(name):
            if name in classes:
                return classes[name]
            file = SOURCE / f"{name}.java"
            binary = ROOT / ".reference/r196/classes" / f"{name}.class"
            overlay = binary.exists()
            data = binary.read_bytes() if overlay else baseline.read(f"{name}.class")
            result = {"class": name, "r196_overlay": overlay, **binary_metadata(data)}
            result["baseline_class_sha256"] = sha(baseline.read(f"{name}.class")) if f"{name}.class" in baseline.namelist() else None
            if file.exists():
                text = file.read_text(encoding="utf-8")
                texts[name] = text
                result.update({"decompiled_path": file.relative_to(ROOT).as_posix(), "decompiled_sha256": sha(text.encode()), "methods": method_index(text)})
            else:
                result.update({"decompiled_path": None, "methods": [], "semantic_status": "unchanged baseline support; no decompiled overlay"})
            classes[name] = result
            return result
        for name in [*ACTORS, *SUPPORT]:
            load(name)
        for name in list(classes):
            text = texts.get(name, "")
            for goal in re.findall(r"new (\w+)\(this(?:,|\))", text):
                if goal not in classes and ((SOURCE / f"{goal}.java").exists() or f"{goal}.class" in baseline.namelist()):
                    load(goal)
        for name in ACTORS:
            parent = load(name)["parent"]
            while parent and parent not in ("java/lang/Object",):
                parent = load(parent)["parent"]

    def ref(cls, method, args=None):
        rows = [row for row in classes[cls]["methods"] if row["name"] == method and (args is None or args in row["arguments"])]
        assert rows, (cls, method, args)
        return [{"class": cls, "file": classes[cls]["decompiled_path"], **{key: row[key] for key in ("signature", "line", "end_line", "method_sha256")}} for row in rows]
    rules = []
    def rule(key, scope, condition, values, refs, cases, dependencies=()):
        inferred_dependencies = ("server_tick_and_goal_scheduler", "source_entity_attributes_031", "save_and_client_sync")
        if key.startswith("movement.") or key.startswith("random_position."):
            inferred_dependencies += ("modern_navigation_and_collision", "loaded_chunk_bounds", "source_world_height_adaptation")
        if key.startswith("livestock.") or key.startswith("chicken.") or key.startswith("sheep."):
            inferred_dependencies += ("livestock_resource_state", "source_vanilla_loot_031", "food_and_vessel_items")
        rules.append({"id": key, "status": "default_call_chain_confirmed", "scope": scope, "enabled_when": condition,
                      "values": values, "sources": [item for cls, method, *args in refs for item in ref(cls, method, args[0] if args else None)],
                      "server_responsibility": "authoritative state/AI/interaction", "client_responsibility": "render synchronized state and feedback only",
                      "dependencies": list(dict.fromkeys((*inferred_dependencies, *dependencies))), "acceptance_cases": cases})

    rule("livestock.state", ["chicken", "cow", "mooshroom", "pig", "sheep"], "server constructor / setters / save-load",
         {"resources": ["food", "water", "freedom"], "initial_each": "0.8 + entityRandom.nextFloat()*0.2", "clamp": [0, 1], "well_minimum_inclusive": .25, "hungry_thirsty_exclusive": .5, "very_exclusive": .25, "desperate_exclusive": .05, "saved_keys": ["food", "water", "freedom", "production_counter", "manure_countdown", "has_been_spooked_by_other_animal"]},
         [("EntityLivestock", "EntityLivestock"), ("EntityLivestock", "isWell"), ("EntityLivestock", "isHungry"), ("EntityLivestock", "isThirsty"), ("EntityLivestock", "b", "NBT"), ("EntityLivestock", "a", "NBT")], ["0.25 resources are well; below any one threshold is not", "Save/reload all resources independently", "Horse/wolf have no livestock resource state"])
    rule("livestock.wellness_tick", ["livestock"], "raw entity ticksExisted%100==0; nextInt(10)>0 gates the entire resource update",
         {"period_ticks": 100, "update_probability": .9, "near_food_gain": .1, "near_water_gain": .1, "rain_water_gain": .01, "outdoor_not_crowded_gain": .1, "missing_penalty": -.005, "production_increment": "+1 only if the updated livestock is well and adult", "produce_goods_called": "every 100 ticks, independently of the 90% update draw"},
         [("EntityLivestock", "c"), ("EntityLivestock", "updateWellness")], ["Skipped 10% update changes no resource", "Rain supplies water but thirsty livestock does not flee rain", "Children update resources but do not increment production"])
    rule("livestock.food_sources", ["livestock"], "nearby scan dx/dz=-1..1, dy=-1..floor(entityHeight), first source wins",
         {"default_legacy_blocks": [2, 31], "default_modern": ["grass_block", "short_grass"], "cow_and_mooshroom_legacy_blocks": [31, 37], "cow_and_mooshroom_modern": ["short_grass", "dandelion"], "cow_destroy_chance_per_wellness_update": .01, "other_destroy_chance": 0, "manual_food": {"chicken": "wheat_seeds", "cow": "wheat", "sheep": "wheat", "pig": ["carrot", "brown_mushroom"]}, "food_eaten_gain": .5},
         [("EntityLivestock", "isNearFood", "int x"), ("EntityLivestock", "isFoodSource"), ("rr", "isFoodSource"), ("EntityLivestock", "onFoodEaten"), ("rq", "isFoodItem"), ("rr", "isFoodItem"), ("rz", "isFoodItem"), ("ry", "isFoodItem")], ["Cow cannot obtain food from grass block alone", "Dandelion is cow food, not wheat crop", "Pig accepts carrot and brown mushroom; potato/beetroot are absent from R196 predicate"])
    rule("livestock.water_sources", ["livestock"], "same near scan; all sources rejected if the block above has a flat solid bottom face",
         {"legacy_blocks": [8, 9, 78, 80, 118], "modern_blocks": ["water", "snow", "snow_block", "water_cauldron"], "cauldron_requires": "nonempty and cauldronY+1 < entityEyeY", "depletion": "none from passive source use", "vessel_feed": "only thirsty; water += .25*standardVolume; empty vessel returned unless creative"},
         [("EntityLivestock", "isWaterSource"), ("EntityLivestock", "isNearWaterSource", "int x"), ("ItemVessel", "tryEntityInteraction")], ["Snow satisfies thirst", "Covered water rejected", "Low cauldron valid for tall adult but not sufficiently low-eyed baby", "Full bucket gives1, bowl gives.25"])
    rule("livestock.crowding", ["livestock"], "isCrowded current position and proposed AI destination",
         {"current_aabb_inflate": [2, .5, 2], "nearby_living_max_inclusive": 2, "indoor_always_crowded": True, "coordinate_aabb_half_size": [2, .5, 2], "living_query_includes_self": True, "source_function": "world.isOutdoors, not merely direct canSeeSky"},
         [("EntityLivestock", "isCrowded"), ("abw", "a", "Class"), ("adr", "a", "Class")], ["Indoor singleton is crowded", "Two nearby mobs allowed, three crowded", "Other mobs/monsters contribute; players extend of rather than og and are excluded"])
    rule("animal.age_and_breeding", ["rp animal descendants"], "ageable server tick and active pk breed goal",
         {"newborn_age": -64000, "post_breed_age": 16000, "love_ticks": 600, "mate_search_aabb": [8, 8, 8], "mating_ticks": 60, "mating_distance_sq_exclusive": 9, "breeding_xp": [1, 7], "horse_baby_advance": "+200 on offsetTicks%200==0", "others_baby_advance": "+1 per tick", "feed_growth_formula": "age=min(age+growthInput*5,0)", "livestock_desperate_blocks_growth": True, "baby_resources": "minimum of each parent's resource", "food_heal": "50% maximum health after love check", "love_requires": "age==0, not already in love, full health before heal; livestock additionally well", "refuse_when": "livestock fullHealth && !well && !hungry"},
         [("nk", "getGrowingAgeOfNewborn"), ("nk", "setGrowingAgeAfterBreeding"), ("nk", "c"), ("nk", "a", "int par1"), ("rp", "canEat"), ("rp", "onFoodEaten"), ("rp", "bX"), ("pk", "e"), ("pk", "g"), ("EntityLivestock", "c", "int par1"), ("EntityLivestock", "adoptWellnessFromParents"), ("EntityLivestock", "willEat")], ["Desperate baby does not grow; restored food/water resumes age", "Breeding creates -64000 baby with parent minima", "Injured animal's first feeding heals but does not start love"])
    rule("livestock.production_and_manure", ["livestock"], "server update/produceGoods adult gates",
         {"manure_period_ticks": {"cow_mooshroom": 24000, "pig_sheep": 48000, "chicken": 384000}, "initial_manure_countdown": "int(Math.random()*period)", "subsequent_manure_countdown": "period/2 + nextInt(period)", "adult_food_desperate_pauses_manure": True, "milk_initial_and_max": 100, "cow_goods": "milk=clamp(milk+production,0,100); production=0", "milking_cost": "25*standardVolume", "chicken_max_feathers": "nextInt(2)+1; if2 and nextInt2==0 decrement => P(1)=.75/P(2)=.25", "feather_goods": "production>=100 && nextInt500==0 => gain feather or drop if at max; production-=100; return", "egg_goods": "production>=200 && nextInt20==0 => egg; production-=200", "pig_sheep_goods": "production=0"},
         [("EntityLivestock", "c"), ("EntityLivestock", "setManurePeriod"), ("rq", "rq"), ("rq", "produceGoods"), ("rq", "gainFeather"), ("rr", "produceGoods"), ("ItemVessel", "tryEntityInteraction"), ("ry", "produceGoods"), ("rz", "produceGoods")], ["Unwell adult does not gain goods production", "Chicken feather branch suppresses egg branch in the same update", "Manure cooldown survives reload"])
    rule("chicken.feathers", ["chicken"], "adult damage knockback or idle be update",
         {"damage_drop_draws": "floor(incomingAmount), each nextFloat<.2 while feathers remain", "damage_exclusions": ["dead", "child", "fire", "lava", "magic", "drown", "starve"], "idle_drop_probability": "nextInt40==0", "idle_drop_requires_not_water": True, "idle_retains_one": True, "saved_keys": ["max_num_feathers", "num_feathers"]},
         [("rq", "attackEntityFrom"), ("rq", "be"), ("rq", "tryDropFeather"), ("rq", "b", "NBT")], ["Adult idle with2 feathers can drop1, never last feather", "Fire/magic damage does not shed feathers"])
    rule("sheep.eat_and_wool", ["sheep"], "pn goal priority5 starts with no hurt/flee; sheep callback n after successful bite",
         {"goal_start_chance": "nextInt(child?50:1000)==0", "food_at_feet": "short grass or living crop (not dead crop)", "below_food": "grass block with no trampling penalty", "source_predator_bug": "if no grass/crop matched, returns predators.isEmpty() and can start empty eating animation", "eat_ticks": 40, "bite_remaining_ticks": 4, "bite_effect": "destroy short grass/crop without drops or grass->dirt", "callback": "regrow wool; baby age +=60*5 capped0; food +=.5", "fire_hits_before_wool_lost": 6, "fire_counter_decay": "1 every100 ticks while not burning", "cube_or_sphere_damage_loses_wool": True, "fire_wool_counter_saved": False},
         [("pn", "a"), ("pn", "e"), ("rz", "n"), ("rz", "onEntityDamaged"), ("rz", "c")], ["Grass bite regrows wool and feeds sheep", "Eating interrupted by harm/flee", "Empty-space grazing animation causes no resource gain"])
    rule("movement.common", ["EntityAIMovementTask descendants"], "goal start / tick / continuation / stop",
         {"mutex_bits": 1, "stuck_threshold_draw": [30, 49], "stuck_check": "floor(x/z) unchanged for >threshold; y ignored", "path_reject": "null/finished or distanceSq(finalPoint,entity)<1", "swim_retry": "if failed and normallyAvoidWater && swimIfNecessary, temporarily disable water avoidance and recalculate", "stop": "stop navigation and restore previous water avoidance", "caveat": "Flee subclass e() does not invoke base e(), so its stuck counter is not advanced by that override"},
         [("EntityAIMovementTask", "c"), ("EntityAIMovementTask", "e"), ("EntityAIMovementTask", "b"), ("EntityAIMovementTask", "d"), ("EntityAIFleeAttackerOrPanic", "e")], ["Goal stop restores water penalty", "Normal goal aborts stalled path after drawn threshold"])
    rule("movement.seek_food_water", ["livestock"], "resource<.5 and nearby source or urgency random success",
         {"start_draw_denominator": {"hungry": 120, "very": 40, "desperate": 10}, "max_candidates": [8, 16, 24], "max_distance": [16, 32, 48], "vertical_range": "maxDistance/8", "search_origin_y": "entityY + height*.75", "candidate_order": "nearest candidates; first path whose final point is near the required source", "fallback": "path away from current XYZ, minimum16/max32", "hold_at_source": "stop nav, keep goal active until resource>.95", "food_swim": True, "water_swim": False},
         [("EntityAISeekFoodIfHungry", "a"), ("EntityAISeekFoodIfHungry", "getMovementPath"), ("EntityAISeekFoodIfHungry", "b"), ("EntityAISeekWaterIfThirsty", "a"), ("EntityAISeekWaterIfThirsty", "getMovementPath"), ("EntityAISeekWaterIfThirsty", "b")], ["Hungry search expands at .25 and .05", ".95 continues holding; >.95 stops", "Food goal can retry swimming, water goal does not enable that retry"])
    rule("movement.seek_space", ["livestock"], "freedom<=.5; immediate if not crowded, else nextInt(freedom<.25?10:40)==0",
         {"mutex_bits": 3, "attempts": "freedom<.25?16:4", "horizontal_offsets_alternate": [[-8, 8], [-16, 16]], "vertical_offsets": [-3, 3], "ground_adjust_limit_each": 8, "path_max": 16, "selection": "shortest valid uncrowded path; stop after foundCount > (very?2:1)", "stop_resource_exclusive": .95},
         [("EntityAISeekOpenSpaceIfCrowded", "a"), ("EntityAISeekOpenSpaceIfCrowded", "getShortestPathToFreedom"), ("EntityAISeekOpenSpaceIfCrowded", "b")], ["Indoor animal seeks outdoor destination", "Crowded path endpoint rejected", "Loop allows3 valid paths when very crowded,2 otherwise because comparison is >"])
    rule("movement.flee_and_panic", ["livestock", "horse", "wolf"], "decided flee or panic; livestock/horse attacker distance<32",
         {"animal_stop_attacker_distance": 40, "panic_ticks": [100, 199], "burning_or_spooked_refresh_panic": True, "panic_extra_speed": .2, "fallback_random_range": [5, 4], "flee_path": "away from attacker minimum16/max48", "reset_done_panic_chance": "world.nextInt10000==0", "species_base_speed_and_panic_probability": {"chicken": [1.3, .75], "cow": [1.6, .5], "pig": [1.4, .5], "sheep": [1.4, .5], "horse": [1.2, .25], "wolf": [1, 0]}, "source_stop_flag_quirk": "distance>40 clears fleeing but not has_decided_to_flee", "generic_mob_flee": "!undead && fleesWhenBadlyWounded && healthFraction<.2 && lastAttackerDist<=32 && nextInt2==0"},
         [("EntityAIFleeAttackerOrPanic", "a"), ("EntityAIFleeAttackerOrPanic", "startPanicking"), ("EntityAIFleeAttackerOrPanic", "getMovementPath"), ("EntityAIFleeAttackerOrPanic", "getMovementSpeed"), ("EntityLivestock", "considerFleeing"), ("EntityLivestock", "considerStopFleeing"), ("rs", "considerFleeing"), ("of", "considerFleeing")], ["Harmed healthy cow flees; wolf generic flee requires low health", "Burning keeps refreshing panic timer", "Source stuck bookkeeping quirk documented"])
    rule("movement.spook_peers", ["livestock"], "flee/decidedFlee/spooked and offsetTicks%20==0",
         {"aabb_inflate": [8, 4, 8], "requires": ["other living livestock", "not already flee/spook", "not previously peer-spooked", "canSeeEntity", "canPathTo max8"], "until": "worldTime+400+worldRandom.nextInt400", "repeat_guard_reset": "world.total_time%4000==0", "attack_warning": "same actual Java class within8/4/8; skips tamed and already has last attacker; assigns attacker and calls considerFleeing"},
         [("EntityLivestock", "c"), ("og", "warnPeersOfAttacker"), ("rp", "attackEntityFrom")], ["Visible reachable neighbor panics", "Occluded neighbor does not gain peer-spook", "Cross-species livestock can relay spook"])
    rule("animal.attacker_memory", ["source animals"], "setLastHarmingEntity and inherited living server tick",
         {"memory_ticks": "900+nextInt200", "saved_keys": ["last_harming_entity_unique_id", "last_harming_entity_memory_countdown"], "reload_resolve": "lookup nearby UUID every10 raw ticks until restored", "clear": "attacker removed or countdown0", "spooked_until_saved": True},
         [("of", "setLastHarmingEntity"), ("of", "c"), ("of", "b", "NBT"), ("og", "b", "NBT")], ["Attacker memory lasts900..1099 ticks", "Saved UUID may resolve after reload", "Countdown exhaustion stops source flee"])
    rule("movement.avoid_predators", ["livestock", "horse"], "nextInt10==0 then nearest predator distanceSq<256",
         {"query_aabb_half_size": [32, 4, 32], "predator_definition": "living.preysUpon(prey), not all hostile mobs", "ocelot_is_not_predator_for_this_query": "rx has prey target goals but no preysUpon override; of returns false", "shy_horse_players_added": True, "shy_horse": "adult && !tamed && temper<=0", "path_attempts": 16, "path_max": 32, "vertical_offsets": [-3, 3], "ground_adjust_each_max": 8, "selection": "maximize nearest-predator distance, recheck actual final node", "speed_multiplier_when_predator_target_is_owner": 1.2},
         [("EntityAIAvoidPotentialPredators", "a"), ("EntityAIAvoidPotentialPredators", "getMovementPath"), ("EntityAIAvoidPotentialPredators", "getMovementSpeed"), ("abw", "getPredatorsWithinAABBForEntity"), ("rs", "isShy")], ["Skeleton not targeting animal is not automatically predator", "Untamed temper0 horse avoids nearby player", "Selected endpoint must actually increase safety"])
    rule("movement.avoid_fire", ["rp descendants except Hellhound goal clear"], "nextInt10==0 and harmedByFire and fire within8/2",
         {"search_fire_range": [16, 4], "max_fire_candidates": 16, "path_attempts": 16, "path_max": 8, "vertical_offsets": [-3, 3], "selection": "maximize nearest-fire distance at candidate and final node"},
         [("rp", "rp"), ("EntityAIAvoidFire", "a"), ("EntityAIAvoidFire", "getMovementPath"), ("EntityHellhound", "EntityHellhound")], ["Ordinary animals avoid fire", "Hellhound clears inherited AvoidFire registration"])
    rule("movement.escape_weather_water_lava", ["livestock", "horse", "wolf", "hellhound"], "1/16 draw; rain/inWater/inLava respectively",
         {"path_attempts": 16, "path_max": 16, "vertical_offsets": [-3, 3], "ground_adjust_each_max": 8, "selection": "shortest distanceSq to valid endpoint", "rain_and_water_thirsty_suppression": "livestock water<.5", "water_other_suppression": ["rider is player", "does not breathe air", "water breathing"], "rain_endpoint": "not in rain and not occupied", "water_lava_endpoint": "passable feet; solid ground; not occupied", "water_lava_fallback": "toward source spawn XYZ max16", "lava_registration": ["EntityHellhound priority4", "EntityDemonSpider priority4"], "ordinary_lava_registered": False},
         [("EntityAIGetOutOfWater", "a"), ("EntityAIGetOutOfWater", "getMovementPath"), ("EntityAIGetOutOfWater", "b"), ("EntityAIGetOutOfLava", "a"), ("EntityAIGetOutOfLava", "getMovementPath"), ("EntityAISeekShelterFromRain", "a"), ("EntityAISeekShelterFromRain", "getMovementPath")], ["Thirsty animal stays in rain/water", "Player-ridden horse does not run water exit goal", "Hellhound exits lava despite source fire/lava immunity"])
    rule("movement.dropped_food", ["rp descendants except cleared Hellhound goals"], "pickupCooldown==0 && nextInt40==0 && canEat",
         {"query_aabb_inflate": [16, 4, 16], "path_max": 16, "selection": "shortest node count with endpoint exactly matching item floor XYZ", "tamed_refrain": "sitting or healthFraction>.5", "contact_pickup_cooldown": 400, "consume_count": 1, "remaining_item_age": -18000, "heal_on_eat": "50% maximum health except undead", "source_herbivore_food_predicates": "species isFoodItem (does not include carrot-on-a-stick for Pig food)"},
         [("EntityAIMoveToFoodItem", "a"), ("EntityAIMoveToItem", "getMovementPath"), ("og", "c"), ("og", "onFoodEaten"), ("rp", "rp")], ["One item eaten per cooldown; remainder persists", "Sitting tamed pet never seeks dropped food", "Breeding-ready herbivore can seek item"])
    rule("movement.tempt", ["chicken", "cow", "sheep", "pig", "ocelot"], "matching held source item within10; visible or retained previous player with reachable path16",
         {"mutex_bits": 3, "previous_visible_player_distance_bias": -4, "previous_occluded_player_distance_bias": 4, "occluded_previous_source_quirk": "held item is not rechecked in this branch", "stop_navigation_distance_sq": 6.25, "stop_cooldown": 100, "scared_distance_sq": 36, "scared_motion_distance_sq": .01, "scared_rotation_degrees": 5, "scared_species": ["ocelot"], "temporarily_allows_water": True, "livestock_refusal": "fullHealth&&!well&&!hungry", "pig_tempt_extra": "all nine source carrot-on-a-stick types, carrot and brown mushroom"},
         [("qu", "a"), ("qu", "b"), ("qu", "c"), ("qu", "d"), ("qu", "e"), ("ry", "ry"), ("rx", "rx")], ["Correct food holds animal at2.5 blocks", "Fast movement scares ocelot within6 blocks", "Bad water/freedom at full food+health suppresses herbivore temptation"])
    rule("wolf.taming_and_targets", ["wolf", "dire_wolf"], "adult untamed bone interaction while not attacking/hostile; tamingCooldown0",
         {"wolf_roll": "r<.05 aggressive failure; r<.1 plain failure; r>.9 success; otherwise r+=nextFloat*level*.02 then r<.2 aggressive/r<.8 plain/else success", "dire_roll": "r<.2 aggressive; r<.4 plain; r>.95 success; otherwise add level draw then r<.5 aggressive/r<1 plain/else success", "failed_cooldown_ticks": 100, "failure_aggro_suppressed": "Blue Moon night", "target_countdown": "clamp(800+nextInt100-nextInt100,0,800)", "blood_moon_hostility": "untamed, every source living update", "preys_upon": "adult untamed wolf, any rp descendant", "tamed_manual_meat_heal": "source nutrition, only health+1<maxHealth", "generic_meat": "ItemMeat incl fish, large fish, rotten flesh", "dire_player_aggro": "adult wild !love !blue and nextFloat<.004 per living tick then nearest vulnerable player within4"},
         [("sf", "getTamingOutcome"), ("sf", "onEntityRightClicked"), ("sf", "preysUpon"), ("sf", "startTargetCountdown"), ("sf", "l_"), ("EntityDireWolf", "getTamingOutcome"), ("EntityDireWolf", "l_")], ["Wolf/Dire use distinct outcome thresholds", "Failure consumes bone and cooldown prevents immediate reroll", "Blue Moon prevents failed-taming aggression"])
    rule("ocelot.taming", ["ocelot", "modern cat mapping"], "bq temptation goal running, raw fish accepted, player distanceSq<9, cooldown0",
         {"food_items": ["raw_fish", "fish_large_raw"], "success_chance": "nextInt3==0", "failed_cooldown_ticks": 400, "success_cat_type": "1+nextInt3", "constructor_bq_overwrite": "ordinary-fish tempt stored then replaced with large-fish tempt; both goals registered but interaction gate checks the second", "prey_targets": ["Chicken priority1, chance750, no sight", "Bat priority1, chance750, no sight"], "tamed_owner_toggle_sitting": True, "wild_litter": "source finalize spawn nextInt7==0 adds2 newborn ocelots", "fall_damage": False},
         [("rx", "rx"), ("rx", "onEntityRightClicked"), ("rx", "getTamingOutcome"), ("rx", "a", "oi"), ("rx", "isFoodItem")], ["Both fish tempt but source actual large-fish gate documented", "Failed taming cooldown400", "Cat split is explicit modern adaptation"])
    rule("horse.shy_rebel_taming", ["horse source variants"], "source horse interaction and RunAroundLikeCrazy qq goal",
         {"temper_max": 100, "shy": "adult&&!tamed&&temper<=0", "bucking_check": "nextInt50==0 during mounted untamed path", "tame_check": "nextInt100<temper", "failed_mount": "temper+=5; rebelliousForRiding=4000; eject rider", "rebelliousForEating_ticks": 4000, "rebel_eating_set": "untamed accepted food and (not healed or healed to full)", "tamed_clears_both_rebels": True, "grazing_blocked": ["hurt", "flee", "shy and nearby players within16/2/16", "active avoidPred/flee goal", "predators within16/2/16"], "feed_rows": {"wheat": [2,60,3], "sugar": [1,30,3], "bread": [7,180,3], "hay_block": [20,180,0], "apple": [3,60,3], "golden_carrot": [4,60,5], "golden_apple": [10,240,10]}, "feed_row_columns": ["healing", "growth_input_times5", "temper_gain"]},
         [("rs", "isShy"), ("rs", "onEntityRightClicked"), ("rs", "i", "boolean"), ("rs", "o", "boolean"), ("qq", "e")], ["Untamed temper0 horse flees visible player", "Bucking gives4000 riding cooldown", "Taming clears rebellion timers"])
    rule("horse.breeding_actual_bugs", ["horse source variants"], "rs.a(nk) creates offspring; independently javap-confirmed",
         {"type": "same type preserved; horse+donkey -> mule", "coat_color_roll": "nextInt9:4/9 this,4/9 partner,1/9 random nextInt7", "markings_roll": "nextInt5:4/5 this,1/5 partner, unreachable random branch", "attribute_receiver": "all three averaged attributes written to this mother (aload_0), not newborn (aload_3)", "newborn_constructor_attributes": {"max_health":53,"speed":.225,"jump_strength":.7}, "javap_offsets": {"markings_bound": [137,145], "mother_max_health_receiver":257,"mother_jump_receiver":303,"mother_speed_receiver":349,"return_newborn":363}},
         [("rs", "a", "nk")], ["Retain confirmed source markings and parent mutation behavior or explicitly document modern divergence", "Do not falsely infer intended inheritance from CFR output"])
    rule("squid.predation", ["squid"], "source legacy non-goal bl movement updates",
         {"prey_radius":16, "prey": "true animals and vulnerable players in/above deep water; not water mobs, not Blue Moon", "retarget": "outer nextInt50==0 or dry or stationary; inner invalid/no target or nextInt10==0", "required_ray": "only full water blocks from squid center to target center, then feet/top alternatives", "full_water_metadata": "still9 metadata0|8; flowing8 metadata0", "direction_velocity":.2,"idle_y": "-.1+nextFloat*.2", "collision_effect": "slowness200 ticks amplifier2; player not boat passenger and distance<1; nonplayer requires line of strike", "boat_break_condition": "boat rider equals current target"},
         [("sc", "preysUpon"), ("sc", "areOnlyFullWaterBlocksBetween"), ("sc", "bl"), ("sc", "b_"), ("sc", "n", "nn entity"), ("sc", "canDestroyBoatOnCollision")], ["Blue Moon squid clears target", "Solid block interrupts all water rays", "Shallow-water player excluded; deep-water prey slowed"])
    rule("bat.flight_and_hanging", ["bat", "vampire_bat", "giant_vampire_bat", "nightwing"], "ro.bi living AI method, inherited by three R196 bat subtypes",
         {"hang_support": "not glass20; solid standard cube or flat solid bottom; air directly underneath; giant requires2 air blocks", "prevent_hang": "detach countdown60 or target!=null or hurtTimer>0 or closest eligible player within4", "hanging_random_turn": "nextInt200==0 then nextInt360", "hanging_random_departure": "nextInt1000==0", "roam_reselect": "no valid air waypoint, nextInt30==0 or waypoint.distanceSq<4", "horizontal_offsets": "nextInt(int(7*scale))-nextInt(int(7*scale))", "vertical_offsets": "nextInt6-2", "legacy_roam_minimum_y": 1, "flight_motion": "velocity += (sign(delta)*[.5,.7,.5] - velocity)*.1", "first_hang_attempt_chance": 1, "later_hang_attempt_chance": .1, "attack_aim": "target eye + per-axis nextFloat-.5 refreshed every20 offset ticks", "saved_state": ["BatFlags", "initial_hang_attempted"], "hanging_y_is_not_saved": True},
         [("ro", "canHangFromBlock"), ("ro", "isPreventedFromHanging"), ("ro", "bi"), ("ro", "a", "boolean"), ("ro", "b", "NBT")], ["Glass ceiling cannot support hanging", "Nearby player detaches resting bat", "Modern roaming/hanging works below Y0 using explicit height adaptation", "Ordinary bat has no invented livestock needs"])
    rule("animal.despawn_guard", ["rp animals other than Hellhound"], "source tryDespawnEntity calls rp.t and inherited mob t",
         {"early_despawn_counter_guard": "counter<800 -> false", "reset_and_guard": "closest player within1024 OR wooden fence85 within16/1 -> counter0 and false", "later_guard": "counter<=2400 -> false", "wolf_extra": "not Witch ally && (raw age>2400 or hostile marker) && untamed", "ocelot_extra": "untamed and raw age>2400", "source_no_animals_death_xp": "livestock/horse/squid return0; breeding still spawns1..7"},
         [("rp", "t"), ("sf", "t"), ("rx", "t"), ("EntityLivestock", "getExperienceValue"), ("rs", "getExperienceValue"), ("sc", "getExperienceValue")], ["Fence/player resets animal despawn counter", "Tamed source wolf/cat protected", "Do not substitute modern persistence for source actual guards without documenting adaptation"])
    rule("random_position.default", ["rh random position users"], "static rh intensive_search is literal false",
         {"default_attempts":10, "unreachable_intensive_attempts":64,"unreachable_intensive_doubles_xz":True,"direction_filter": "horizontal candidate dot direction >=0", "selection":"largest creature path weight", "source_coordinate_quirk":"creature XYZ added to offsets only in the home-restriction conditional short circuit"},
         [("rh", "c")], ["Do not enable unreachable intensive64 search by class-name assumption", "Modern world path helpers must document coordinate fix"])

    goal_map = {}
    variable_defaults = {"bp": "qr"}
    for name, text in texts.items():
        constructor = next((row for row in classes[name]["methods"] if row["name"] == name), None)
        if constructor is None:
            continue
        body = constructor["_body"]
        entries = []
        for match in re.finditer(r"this\.(?P<selector>c|d)\.a\((?P<priority>\d+),\s*(?P<goal>new (?P<newclass>\w+)\([^\n;]+\)|this\.(?P<field>\w+))\);", body):
            field = match["field"]
            resolved = match["newclass"]
            if field:
                defs = list(re.finditer(r"this\."+re.escape(field)+r"\s*=\s*new\s+(\w+)\(", body[:match.start()]))
                resolved = defs[-1][1] if defs else next(iter(re.findall(r"\b"+re.escape(field)+r"\s*=\s*new\s+(\w+)\(", text)), variable_defaults.get(field))
            entries.append({"selector": "goal" if match["selector"] == "c" else "target", "priority": int(match["priority"]), "class": resolved, "constructor_expression": match["goal"], "source_class": name, "line": constructor["line"]+body.count("\n",0,match.start())})
        goal_map[name] = {"direct": entries, "clear_goal": "this.c.clear()" in body, "clear_target": "this.d.clear()" in body}

    entities = []
    for name, (legacy_id, registered_name, modern_ids) in ACTORS.items():
        lineage, parent = [], name
        while parent and parent in classes:
            lineage.append(parent); parent = classes[parent]["parent"]
        effective = []
        for ancestor in reversed(lineage):
            registration = goal_map.get(ancestor, {})
            effective = [row for row in effective if not ((row["selector"] == "goal" and registration.get("clear_goal")) or (row["selector"] == "target" and registration.get("clear_target")))]
            effective.extend(registration.get("direct", []))
        entities.append({"legacy_id": legacy_id, "registered_name": registered_name, "class": name, "modern_ids": modern_ids, "lineage": lineage, "livestock": "EntityLivestock" in lineage, "legacy_goal_ai": "rp" in lineage, "effective_goals_in_insertion_order": effective})
    calls = []
    for path in sorted(SOURCE.glob("*.java")):
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if "new EntityAIGetOutOfLava(" in line:
                calls.append({"class": path.stem, "line": number, "file": path.relative_to(ROOT).as_posix()})
    assert {row["class"] for row in calls} == {"EntityHellhound", "EntityDemonSpider"}
    for row in classes.values():
        for method in row["methods"]:
            method.pop("_body")
    result = {"schema_version":1,"author":"NingMeng123","archive_sha256":ARCHIVE_SHA,"official_baseline_sha256":BASE_SHA,"legacy_world_execution":False,"source_authority":"Registered R196 overlay plus unchanged official 1.6.4 baseline support; traced constructors, inherited methods and confirmed javap bytecode",
              "scope":"Animal AI, livestock needs and dependent age/breeding/production; independent of modern implementation status",
              "counts":{"source_species":len(entities),"modern_identity_mappings":sum(len(row["modern_ids"]) for row in entities),"rules":len(rules),"indexed_classes":len(classes),"indexed_methods":sum(len(row["methods"]) for row in classes.values())},
              "entities":entities,"rules":rules,"direct_goal_registrations":goal_map,"source_classes":sorted(classes.values(),key=lambda row:row["class"]),
              "activation_audit":{"GetOutOfLava":{"confirmed_enabled_call_sites":calls,"ordinary_rp_livestock_horse_wolf":False},"rh_intensive_search":{"default_enabled":False,"reason":"literal intensive_search=false"},"rp_old_mating_branch":{"default_enabled":False,"reason":"rp.bf() is final true; enabled breeding path is pk goal, not legacy a(nn,float) combat AI"},"Hellhound_inheritance":{"clears_both_selectors":True,"reason":"constructor clears inherited animal and wolf selectors before registering hostile set"}},
              "modern_adaptation_requirements":["Retain modern 1.21.1 terrain/world heights and loaded-chunk safe source search; do not clamp negative Y to legacy floor in AI.","Modern cat/donkey/mule/zombiehorse/skeletonhorse IDs represent legacy variants rather than extra R196 source species.","R196 Ocelot actual interaction checks the second (large fish) temptation instance; modern split-cat interaction must explicitly record the chosen adaptation.","Horse breeding mutates parent attributes in actual source bytecode; retain or document divergence, never report intended child inheritance as R196 fact.","RNG lookup table distributions can be preserved with modern entity RNG but seeded legacy random stream parity is not promised.","Modern pathfinder, collision shapes, traversal and entity scheduling differ; source goals/priorities/conditions are specified, exact legacy paths are not promised.","Trampling/crop disease are dependencies: source wellness and sheep grazing must not claim the entire agriculture system is complete.","Species without R196 identity (rabbit/goat/bee/fox/panda/turtle/dolphin/axolotl/frog/tadpole/strider) retain explicit modern behavior until separately adapted."],
              "evidence_gaps":["Unchanged baseline goal pr (EntityAIFollowParent) and other absent overlay helper bodies are represented by binary method hashes; no new semantic numerical claims are made from absent decompiled source.","Native bat R196 movement has already been handled by batch031 where applicable; this document indexes source relationships without claiming visual/runtime verification.","Rule acceptance cases are independent source-derived expectations, not proof the corresponding modern tests have passed.","AI can inherit literal source bugs (horse breeding, ocelot second temptation, panic stuck bookkeeping); each modern repair requires a named adaptation and test."]}
    out = ROOT / "spec/animals-source-032.json"
    out.write_text(json.dumps(result,ensure_ascii=False,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    print(json.dumps({"output":out.relative_to(ROOT).as_posix(),"sha256":sha(out.read_bytes()),**result["counts"]},ensure_ascii=False))
    return result


if __name__ == "__main__":
    extract()
