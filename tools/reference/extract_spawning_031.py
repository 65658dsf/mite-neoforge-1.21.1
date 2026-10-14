"""Export R196 biome spawning tables and audited spawn control flow. Author: NingMeng123."""
import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
WORKSPACE = ROOT / ".reference"
HELPER = WORKSPACE / "runtime-helper"
SOURCES = WORKSPACE / "r196/decompiled"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def run_oracle(java):
    java = Path(java).resolve()
    javac = java.with_name("javac.exe" if os.name == "nt" else "javac")
    jars = sorted((WORKSPACE / "downloads/libraries").rglob("*.jar"))
    classpath = os.pathsep.join(map(str, [HELPER, HELPER / "r196-overlay.jar", WORKSPACE / "r196/resources",
                                         WORKSPACE / "downloads/minecraft-1.6.4-client.jar", *jars]))
    compile_run = subprocess.run([str(javac), "--release", "17", "-cp", classpath, "-d", str(HELPER),
                                  str(ROOT / "tools/reference/RegistryExport.java"), str(ROOT / "tools/reference/SpawningRulesExport.java")],
                                 capture_output=True, text=True, encoding="utf-8", errors="replace")
    (HELPER / "spawning-031-compile.log").write_text(compile_run.stdout + compile_run.stderr, encoding="utf-8")
    if compile_run.returncode:
        raise RuntimeError("Guarded spawn-table oracle compilation failed")
    run = subprocess.run([str(java), "-Xmx1G", "-Djava.awt.headless=true", "-Djava.security.manager=allow", "-cp", classpath,
                          "SpawningRulesExport", str(WORKSPACE)], cwd=HELPER, capture_output=True,
                         text=True, encoding="utf-8", errors="replace", timeout=90)
    (HELPER / "spawning-031-oracle.stdout.log").write_text(run.stdout, encoding="utf-8")
    (HELPER / "spawning-031-oracle.stderr.log").write_text(run.stderr, encoding="utf-8")
    if run.returncode:
        raise RuntimeError("Guarded spawn-table oracle failed; no fallback disables its guard")
    siege = subprocess.run([str(java.with_name("javap.exe" if os.name == "nt" else "javap")), "-c", "-p", "-classpath", classpath, "rl"],
                           capture_output=True, text=True, encoding="utf-8", errors="replace", check=True)
    (HELPER / "spawning-031-village-siege.bytecode.txt").write_text(siege.stdout, encoding="utf-8")


def source_span(name, method):
    text = (SOURCES / f"{name}.java").read_text(encoding="utf-8")
    match = re.search(r"(?:public|protected|private)[^\n]*\b" + re.escape(method) + r"\([^\n]*\)\s*\{", text)
    return {"path": f"{name}.java", "method": method,
            "line": text[:match.start()].count("\n") + 1 if match else None,
            "decompiled_source_sha256": digest(SOURCES / f"{name}.java")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-oracle", action="store_true")
    parser.add_argument("--java")
    args = parser.parse_args()
    if args.run_oracle:
        if not args.java:
            parser.error("--run-oracle requires Java 17..23 --java executable")
        run_oracle(args.java)
    text = (HELPER / "spawning-031-oracle.stdout.log").read_text(encoding="utf-8")
    data = json.loads(text.split("SPAWNING_ORACLE_BEGIN\n", 1)[1].split("\nSPAWNING_ORACLE_END", 1)[0])
    registry = read(ROOT / "reference-data/runtime-registry.json")
    entities = {entry["class"]: entry for entry in registry["entities"]}
    blocks = {entry["id"]: entry for entry in registry["blocks"]}
    assert len(data["biomes"]) == 25 and len(data["grounds"]) == 221 and len(data["living_entities"]) == 60
    assert data["activation_flags"] == {"professions_suppressed": True, "in_dev_mode": False,
                                        "spawning_disabled": False, "tournament_type": None}
    assert data["world_created"] is False and data["entity_constructed"] is False and data["game_main_invoked"] is False
    for biome in data["biomes"]:
        for pool in biome["pools"]:
            pool["total_weight"] = sum(entry["weight"] for entry in pool["entries"])
            for entry in pool["entries"]:
                registered = entities[entry["entity_class"]]
                entry["registered_name"] = registered["registered_name"]
                entry["legacy_id"] = registered["id"]
                entry["source"] = registered["source"]
                assert 0 < entry["weight"] and 0 < entry["minimum_group"] <= entry["maximum_group"]
    for pool in data["structure_pools"]:
        for entry in pool["entries"]:
            original = entities[entry["entity_class"]]
            entry.update({"registered_name": original["registered_name"], "legacy_id": original["id"], "source": original["source"]})
    for entry in data["living_entities"]:
        entry["source"] = entities[entry["class"]]["source"]
        text = (SOURCES / f"{entry['maximum_pack_declared_by']}.java").read_text(encoding="utf-8")
        match = re.search(r"public int bv\(\)\s*\{\s*return (\d+);", text)
        entry["maximum_pack"] = int(match.group(1)) if match else None
        assert entry["maximum_pack"] is not None
    exceptions = []
    for block in data["grounds"]:
        block["name"] = blocks[block["legacy_id"]]["scalars"]["aqz.b"]
        block["source"] = blocks[block["legacy_id"]]["source"]
        for subtype in block["metadata"]:
            for decision in subtype["decisions"]:
                if decision.get("exception"):
                    exceptions.append({"legacy_id": block["legacy_id"], "metadata": subtype["metadata"], **decision})
                else:
                    assert decision["natural"] == decision["initial"]
    assert [(e["legacy_id"], e["metadata"], e["category"]) for e in exceptions] == [(36, 6, "monster"), (36, 7, "monster")]
    java_sources = ["aci", "js", "abw", "og", "on", "tm", "rp", "ro", "rs", "rx", "sc", "se", "tj", "tl", "tn", "tq",
                    "EntityCubic", "EntityEarthElemental", "EntityNightwing", "rl", "rj", "abn", "ask", "aet", "aep", "aeu",
                    "ChunkProviderUnderworld", "acq", "agn", "ahh", "aa", "atv", "is", "aei", "aej"]
    depth = {
        "EntityGhoul": {"maximum_source_y": 56, "surface_exception": "blood moon night"},
        "EntityWight": {"maximum_source_y": 48, "surface_exception": "blood moon night AND freezing biome"},
        "EntityVampireBat": {"maximum_source_y": 48, "surface_exception": "blood moon night"},
        "EntityRevenant": {"maximum_source_y": 44, "surface_exception": "blood moon night"},
        "EntityInvisibleStalker": {"maximum_source_y": 40},
        "EntityEarthElemental": {"maximum_source_y": 40},
        "EntityBlob": {"maximum_source_y": 40, "additional": "first non-air block above is stone"},
        "EntityOoze": {"maximum_source_y": 32, "additional": "stone directly below AND first non-air block above is stone"},
        "EntityNightwing": {"maximum_source_y": 32, "surface_exception": "blood moon night"},
        "EntityBoneLord": {"maximum_source_y": 32, "surface_exception": "blood moon night"},
        "EntityPudding": {"maximum_source_y": 24, "additional": "stone directly below AND first non-air block above is stone"},
        "EntityDemonSpider": {"maximum_source_y": 32}, "EntityPhaseSpider": {"maximum_source_y": 32},
        "EntityHellhound": {"maximum_source_y": 32},
        "EntityShadow": {"maximum_source_y": 32, "surface_exception": "blood moon night AND desert biome"},
    }
    data.update({
        "schema_version": 1, "archive_sha256": registry["archive_sha256"], "mode": "guarded_tables_and_pure_ground_predicates_plus_source_control_flow_audit",
        "provenance": {"runtime_registry_sha256": digest(ROOT / "reference-data/runtime-registry.json"),
                       "oracle_source_sha256": digest(ROOT / "tools/reference/SpawningRulesExport.java"),
                       "guard_source_sha256": digest(ROOT / "tools/reference/RegistryExport.java"),
                       "overlay_sha256": digest(HELPER / "r196-overlay.jar"),
                       "oracle_stdout_sha256": digest(HELPER / "spawning-031-oracle.stdout.log"),
                       "decompiled_sources": {name: digest(SOURCES / f"{name}.java") for name in java_sources}},
        "summary": {"registered_biomes": 25, "registered_living_entities": 60, "registered_ground_blocks": 221,
                    "biome_spawn_entries": sum(len(pool["entries"]) for biome in data["biomes"] for pool in biome["pools"]),
                    "ground_metadata_rows": sum(len(block["metadata"]) for block in data["grounds"]),
                    "ground_predicate_exception_count": len(exceptions),
                    "living_categories": dict(collections.Counter(e["category"] for e in data["living_entities"]))},
        "entity_finalization": {
            "zombie": {"source": {**source_span("tw", "a"), "method": "a(oi)", "line": 257},
                       "null_group_draws_in_order": [{"rng": "world.s", "draw": "nextFloat", "less_than": 0.05, "flag": "baby"},
                                                     {"rng": "world.s", "draw": "nextFloat", "less_than": 0.05, "flag": "villager"}],
                       "group_type": "ty", "reuse_group_flags_without_redrawing": True,
                       "baby_flag_consumed_but_source_forces_adult": True,
                       "villager_profession": 0,
                       "set_villager_before_add_random_equipment": True,
                       "villager_flag_excluded_for_revenants": True,
                       "weapon_source": source_span("tw", "addRandomWeapon"),
                       "weapon_chance": {"ordinary": 0.05, "villager": 0.2},
                       "weapon_day": "(unadjustedTick+6000)/24000+1",
                       "weapon_before_armor": True},
            "pig_zombie": {"source": {**source_span("tn", "a"), "method": "a(oi)", "line": 156},
                           "inherits_zombie_group_rng": True, "forces_nonvillager_after_super": True,
                           "equipment_source": source_span("tn", "addRandomEquipment"),
                           "uniform_weapon_pool": ["yc.I", "yc.I", "yc.battleAxeGold", "yc.K"],
                           "quality": "poor", "randomize_for_mob_enchant": False, "calls_random_armor": False}
        },
        "schedule": {"source": source_span("aci", "performRandomLivingEntitySpawning"),
                     "caller": "js.tick -> if gamerule doMobSpawning then aci.performRandomLivingEntitySpawning",
                     "disabled_if": ["aa.spawning_disabled", "world player list empty"], "hostile_gate": "world.E",
                     "below_60_probability_per_tick": 0.1, "at_60_or_higher_probability_per_tick": 0.17,
                     "hostile_probability_rng": "Math.random, independent draws", "non_overworld_rate_modifier": 0.25,
                     "ambient_and_aquatic_gate": "world.F AND timeOfDay % 400 == 0",
                     "animals_gate": "timeOfDay % 400 == 0 AND (tournament_type == score OR blue moon night)",
                     "blue_moon_animals_bypass_animal_light_check": True,
                     "normal_runtime_animal_respawn": False},
        "rate_modulation": {"source": source_span("aci", "calcEffectiveHostileMobSpawningRateModifier"),
                            "overworld_base": 1.0, "decreased_active_multiplier": 0.5, "increased_active_multiplier": 2.0,
                            "no_spawning_active_multiplier": 0.0, "inactive_start_probability_each": "1/24000 per call, three separate java.util.Random draws",
                            "decreased_duration": "nextInt(4000)+1", "increased_duration": "nextInt(2000)",
                            "no_spawning_duration": "nextInt(2000)+nextInt(2000)",
                            "counters_decrement_before_applying_current_tick": True,
                            "blood_moon_or_thunder_minimum_multiplier": 1.0,
                            "non_overworld_ignores_all_counters": True},
        "chunks_density_and_caps": {"source": source_span("aci", "setEligibleChunksForSpawning"),
                                    "eligible_radius_chunks": 8, "inclusive_total_per_isolated_player": 289, "interior_chunks_per_isolated_player": 225,
                                    "edge_included_in_cap_but_not_attempted": True,
                                    "player_exclusions": ["ghost", "Zevimrgv tournament exemption", "dead", "health <= 0"],
                                    "creative_players_are_not_excluded": True,
                                    "overworld_density_formula": "int(8F * (1F + (64 - player.footY) / 32F))",
                                    "minimum_density": {"day": 8, "ordinary_night": 8, "full_moon_night": 10, "blood_moon_night": 12},
                                    "other_dimension_density": 8, "stronghold_density_formula": "int(density + density * proximity)",
                                    "stronghold_proximity": "0 if no nearest stronghold or spawn-to-stronghold distance <2000; otherwise max(0,1-positionDistance/spawnDistance)",
                                    "density_query_aabb_inflation": [32, 8, 32], "density_reject_operator": ">",
                                    "cap_formula": "creatureType.cap * eligibleChunkCount / 256, integer division",
                                    "hostile_low_and_high_counts_are_separate": True, "hostile_low_cap_multiplier": 2,
                                    "hostile_count_type": "th interface, including persistence-marked mobs", "hostile_boundary_y": 60,
                                    "peaceful_count_excludes_persistence": True,
                                    "tournament_animal_cap_multiplier": 2},
        "natural_attempts": {"source": source_span("aci", "trySpawningHostileMobs"), "peaceful_source": source_span("aci", "trySpawningPeacefulMobs"),
                             "random_position": "chunkX*16+nextInt(16), nextInt(chunk.topFilledSegment+16 or world.S), chunkZ*16+nextInt(16)",
                             "low_bin_second_position_draw_if_first_y_ge_60": True, "pack_groups_per_chunk": 3, "ordinary_attempts_per_group": 4,
                             "position_random_walk_xz": "each axis += nextInt(6)-nextInt(6); Y fixed", "world_spawn_exclusion_squared": 576,
                             "normal_player_exclusion_distance": 24, "pitch": 0, "yaw": "nextFloat()*360",
                             "overworld_y63_on_ice_skip_probability": "3/4", "no_alive_player_within48_skip_probability": "1/2",
                             "fully_dark_two_blocks_in_overworld_or_underworld_close_player_distance": 8,
                             "close_player_dark_exception_requires_alive_player_within24": True,
                             "ghast_player_exclusion_distance": 48,
                             "phase_spider_overworld_attempts_per_group": 64,
                             "wide_entity_retries_after_initial_failure": ["x-0.5", "x+0.5", "z-0.5", "z+0.5"], "wide_entity_width_threshold": 1.0,
                             "blue_moon_night_overworld_sky_light_at_entity_eye_must_be_zero": True,
                             "day_outdoors_sun_affected_entity_rejects_remaining_chunk": True,
                             "night_outdoors_accept_denominators": {"blue_moon": 54, "blood_moon": 2, "full_moon": 3, "new_moon": 6, "ordinary": 4},
                             "failed_outdoors_roll_rejects_remaining_chunk": True,
                             "pack_success_limit": "entity.bv(), separate per-bin successes; not biome group_size for natural spawning",
                             "bat_hanging_coinflip": True, "bat_ceiling_scan": "dy=1..15 until first non-air; if hangable move under ceiling and mark initially hanging",
                             "vampire_peaceful_group_attempts_extra_chance": "1/4 sets maxAttempts=4+nextInt(5)",
                             "substitutions": [{"from": "EntityVampireBat", "to": "EntityGiantVampireBat", "condition": "underworld", "chance": "1/6"},
                                               {"from": "EntityLongdead", "to": "EntityLongdeadGuardian", "condition": "any dimension", "chance": "1/6"}]},
        "candidate_selection": {"source": source_span("js", "getSuitableCreature"), "weighted_attempts": 16,
                                "height_filter_only_in_overworld": True, "depth_rules": depth,
                                "above_predicate": "scan up through air; first occupied block must equal requested block, any other occupied block terminates false",
                                "slime_requires_first_occupied_above_not_stone": True, "jelly_requires_first_occupied_above_stone": True,
                                "creeper_outdoors_night_with_skylight_initial_accept_chance": "1/4",
                                "infernal_creeper_substitution": "nextInt(40)>=Y AND nextFloat()<0.5 after creeper is selected",
                                "spider_outdoors_with_skylight_selection_skip_chance": "1/4",
                                "wood_spider_cover": "open sky OR leaves/log above; log within dxz5,dy2; leaves within dxz5,dy5 centred at y+5",
                                "black_widow_extra_skip_chance": "1/2",
                                "ghast_near_ghast_rejection": "each loaded ghast within squared block distance2304 independently rolls80%; any success returns null",
                                "structures": {"overworld": "Temple list overrides when ahh.a reports swamp hut; list empty in this release",
                                               "nether": "Fortress piece bounds OR fortress full bounds with nether_brick directly below override biome list",
                                               "underworld_and_end": "biome list only"}},
        "qualification_rules": {
            "og": {"source": source_span("og", "getCanSpawnHere"), "entity_collision": "world.checkNoEntityCollision(aabb)", "block_collision": "world.getCollidingBoundingBoxes(entity,aabb).empty",
                   "leaves_ground_exception_classes": ["EntityWoodSpider", "EntityBlackWidowSpider", "rx and subclasses"],
                   "ordinary_liquids": "no any liquid in aabb", "shallow_water_hostiles": "no water above head, no water feet under solid head, no lily pad above, no lava in aabb"},
            "on": {"source": source_span("on", "getCanSpawnHere"), "additional": "path weight(position)>=0",
                   "ordinary_mob_path_weight": "0.5-world.q(position); came_from_spawner:0.7-world.q(position)",
                   "brightness_sources": [source_span("abw", "q"), source_span("aei", "a"), source_span("aej", "a")],
                   "brightness_table": "dark=1-light/15; value=(1-dark)/(dark*3+1); nonNether subtract .001*(15-light) for1..14; Nether value*.9+.1",
                   "maximum_light_for_nonnegative_mob_path": {"overworld_default": 12, "nether_default": 11, "spawner": 13}},
            "tm": {"source": source_span("tm", "getCanSpawnHere"), "difficulty": ">peaceful", "light": "unless explicit flag false, i_()"},
            "mob_light": {"source": source_span("tm", "isValidLightLevel"), "spawner_origin": "only require sunlight-fire chance==0",
                          "solid_foot_material_y_adjustment": 1, "saved_sky_gate": "savedSkyLight <= nextInt(32)",
                          "block_light_gate_under_open_sky": "BLV <= nextInt(8)", "block_light_gate_under_cover": "BLV <= nextInt(5)",
                          "storm_override": "temporary skylightSubtracted10 only if local biome rainfall>0 OR blood moon 24h period",
                          "dry_biome_non_blood_period": "ignore rain/thunder for next BLV query"},
            "rp": {"source": source_span("rp", "getCanSpawnHere"), "ordinary": "grass directly below AND (!lightCheck OR world.m(position)>8) AND superclass",
                   "hellhound_exception": "uses overridden tm mob light, not grass/daylight"},
            "ro": {"source": source_span("ro", "getCanSpawnHere"), "overworld_maximum_y_exclusive": 63,
                   "non_halloween_initial_rejection_chance": "1/2", "halloween_window": "October20 through November3 inclusive",
                   "light_gate": "max BLV from starting position down to first opaque cube <= nextInt(halloween?7:4)",
                   "light_flag_does_not_disable_this_bat_rule": True},
            "rs": {"source": source_span("rs", "getCanSpawnHere"), "additional": "cl() prepares horse state, then animal superclass"},
            "rx": {"source": source_span("rx", "getCanSpawnHere"), "rejection_chance": "1/3", "source_minimum_y": 63,
                   "ground": "grass OR leaves", "light": "not checked; only collision and no liquid"},
            "se": {"source": source_span("se", "getCanSpawnHere"), "base": "only world.checkNoEntityCollision(aabb)"},
            "sc": {"source": source_span("sc", "getCanSpawnHere"), "source_y_exclusive": [45, 63], "additional": "aabb inflated2 must be only water"},
            "tj": {"source": source_span("tj", "getCanSpawnHere"), "gate": "nextInt(20)==0 AND base creature checks AND difficulty>peaceful", "maximum_pack": 1},
            "tl_and_tn": {"sources": [source_span("tl", "getCanSpawnHere"), source_span("tn", "getCanSpawnHere")],
                          "gate": "difficulty>peaceful AND no entity/block collision AND no liquid; no light test"},
            "tq": {"source": source_span("tq", "getCanSpawnHere"), "additional": "no alive player within5", "light_override": "true"},
            "EntityEarthElemental": {"source": source_span("EntityEarthElemental", "getCanSpawnHere"), "allowed_ground": ["stone", "netherrack", "clay", "end_stone"],
                                    "light_override": "in Nether OR superclass mob light"},
            "EntityNightwing": {"source": source_span("EntityNightwing", "getCanSpawnHere"), "additional": "lightCheck AND in sunlight rejects, otherwise bat superclass"},
            "EntityCubic": {"source": source_span("EntityCubic", "getCanSpawnHere"), "mite_cubes": "acidic requires stone directly below; optional specialized tm light AND superclass",
                            "slime": "superflat 3/4 rejection; size1 OR difficulty>peaceful; swamp y>50 && y<70, coinflip<0.5, float<moonFraction, BLV<=nextInt(8), OR nextInt(10)==0 AND seeded chunkRandom(987234911).nextInt(10)==0 AND y<40"},
        },
        "initial_chunk_population": {"source": source_span("aci", "performWorldGenSpawning"), "active_callers": ["aet.populate calls animal and aquatic"],
                                     "underworld_nether_end_no_calls": True,
                                     "loop_probability": "while nextFloat()<biome.f(); freezing .05 otherwise .1",
                                     "selection_rng": "world.s for weighted entity entry; method random for placement/group",
                                     "horse_entry_skip_probability": 0.8, "wolf_entry_skip_probability": 0.5,
                                     "group_formula": "minimum + nextInt(1+maximum-minimum)", "placement_attempts_per_member": 4,
                                     "animal_quota_per_chunk": 4, "quota_increment_before_constructor_and_headroom_check": True,
                                     "quota_persisted_nbt_key": "animals_spawned", "quota_save_load_source": "aee.java:253,423",
                                     "hellhound_not_counted_as_initial_animal": True, "uses_entity_getCanSpawnHere": False,
                                     "checks": ["canCreatureTypeSpawnAtLocation(initial=true)", "no opaque standard cube in height above feet"],
                                     "aquatic_spawn_y": "when water depth>3: nextInt(highestWaterY-surfaceY-3)+surfaceY+2",
                                     "aquatic_initial_despawn_counter": -9600,
                                     "subsequent_xz_walk": "nextInt(5)-nextInt(5), re-centred on initialX/Z while outside population bounds",
                                     "source_rectangular_z_bound_uses_range_x": True},
        "spawner_blocks": {"source": source_span("abn", "g"), "activation_player_distance": 16,
                           "initial_delay": 20, "random_delay_minimum": 200, "random_delay_maximum_exclusive": 800,
                           "attempts_per_cycle": 4, "nearby_same_class_cap": 6, "nearby_aabb_inflation": [8, 4, 8],
                           "xz_spread": "(nextDouble()-nextDouble())*4", "y_spread": "nextInt(3)-1",
                           "came_from_spawner_set_before_qualification": True, "qualification": "getCanSpawnHere(true)",
                           "no_success_delay_increment": 10, "disabled_block_metadata": 15,
                           "light_check_changes": "tm spawner-origin branch requires zero sunlight-fire chance rather than ordinary darkness"},
        "village_rules": {"iron_golem_source": source_span("rj", "a"), "iron_golem_rule": "golemCount<villagerCount/10 AND doors>20 AND nextInt(7000)==0 then try10 locations within village with solid flat ground and 2x4x2 clear space",
                          "siege_source": source_span("rl", "a"), "siege_design": "midnight celestialAngle .5..501,1/10 night; village doors>=10,age>=20,villagers>=20; nominal20 zombies every3ticks",
                          "siege_actually_generates": False, "siege_disabled_by_actual_bytecode": "private a(III)Latc invokes Vec3Pool.a then pop; only final aconst_null/areturn",
                          "siege_bytecode_sha256": digest(HELPER / "spawning-031-village-siege.bytecode.txt") if (HELPER / "spawning-031-village-siege.bytecode.txt").is_file() else None,
                          "modern_raids_have_no_r196_source": True},
        "moon_calendar": {"source": "abw.isBloodMoon/isBlueMoon/isFullMoon/isNewMoon; aei.a(long)",
                          "blue_moon": "overworld && (unadjustedTotalTime/24000+1)%128==0, optionally require night",
                          "blood_moon": "overworld && (unadjustedTotalTime/24000+1)%32==0 && !blueMoon, optionally require night",
                          "full_moon": "phase==0", "new_moon": "phase==4", "phase": "(unadjustedTotalTime/24000+1) % 8",
                          "daytime": "5000 < (timeOfDay+6000)%24000 < 19000",
                          "negative_build_heights_are_not_part_of_source": True,
                          "underworld_generation_padding_offset": 120,
                          "underworld_candidate_depth_filter_disabled": True},
        "evidence_gaps": exceptions,
        "outdoor_detection": {"source": source_span("abw", "isOutdoors"),
                              "dimensions": "has-no-sky provider returns false",
                              "head_checks": "if head+1 passable, recurse noninitial at+1 and if+2 passable at+2",
                              "columns": "source heightmap ignoring zero-light-opacity blocks; canSeeSky-style sideways sky diffusion is not an open column",
                              "walk": "monotone passable cardinal paths within Manhattan distance3, plus four2x2 corners, using7x7 marked grid",
                              "passable_source": source_span("abw", "isAirOrPassableBlock")},
        "boundaries": ["No living entity, player, world or actual natural-spawn loop is instantiated by the guarded oracle; control flow is separately traced to source.",
                       "Biome lists are final registered values; natural selection still applies the additional depth, cover, lunar and class predicates.",
                       "Unregistered MushroomIsland biomes cannot be inferred enabled from their class alone.",
                       "Moving-piston metadata6/7 passes its generic validMetadata test but throws in source ground predicate; these two failures remain explicit rather than guessed booleans.",
                       "VillageSiege design is dormant due to confirmed actual bytecode; do not implement it as a default working R196 event.",
                       "Modern negative height, modern-only entity pools, raids and generators require an explicit adapter; no source number is silently shifted by64."]
    })
    destination = ROOT / "spec/spawning-source-031.json"
    destination.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    runtime = {"author": "NingMeng123", "schema_version": 1, "archive_sha256": registry["archive_sha256"],
               "biomes": data["biomes"], "living_entities": data["living_entities"], "structure_pools": data["structure_pools"]}
    (ROOT / "src/main/resources/mite/spawning-031.json").write_text(json.dumps(runtime, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(data["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
