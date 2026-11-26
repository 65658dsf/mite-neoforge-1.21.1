"""Extract traced R196 mob behavior, without executing a game or instantiating entities.

Developer: NingMeng123. Decompiled inputs are local reference material; generated
method indexes are evidence locations, not assertions that every method was changed.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / ".reference/r196/decompiled"
VANILLA = ROOT / "build/verification/031-vanilla-source"
BASE_JAR = ROOT / ".reference/downloads/minecraft-1.6.4-client.jar"
BASE_SHA = "f4513e51c766cdd1d32ca8acd0835d7e16e2851ebea3f2a3b4ce5ae696baf3ae"
ATTRIBUTES = {"a": "max_health", "b": "follow_range", "c": "knockback_resistance", "d": "movement_speed", "e": "attack_damage"}

# Values below are literal source observations, never modern tuning values.
RULES = {
    "arachnid": {"classes": ["EntityArachnid", "EntityWeb", "tt", "te", "EntityWoodSpider", "EntityBlackWidowSpider", "EntityDemonSpider", "EntityPhaseSpider"],
        "default_web_draw": [0, 1, 2, 3], "non_cave_non_demon_positive_draw_decrement": 1,
        "phase_initial_webs": 0, "web_period_ticks": {"cave_and_demon": 200, "others": 500},
        "web_range": 8, "web_velocity": .8, "web_inaccuracy": 0, "web_target_lead_ticks": 10,
        "web_impact_lead_ticks": 4, "burning_web_target_fire_seconds": 5,
        "demon_web_initial_fire_seconds": 10, "poison_ticks": {"wood": 240, "black_widow": 960, "cave_and_demon": 480},
        "demon_stun_ticks": 50, "demon_stun_amplifier": 5, "poison_amplifier": 0,
        "requires_damage_health_loss_for_poison": True, "fear_of_spiders_refuse_attack_probability": .75,
        "jump_distance_open_interval": [2, 6], "jump_chance_per_attack_check": .1,
        "chicken_predator": True, "target_requires_line_of_sight": False, "web_requires_clear_raycast": True,
        "call_chain": ["nt static registration -> EntityArachnid constructor", "EntityArachnid.l_ -> attackEntityWithRangedAttack -> world.spawn EntityWeb", "attackEntityAsMob -> subtype poison", "EntityWeb.onImpact -> setBlockToWebIfEmpty"]},
    "phase_spider": {"classes": ["EntityPhaseSpider"], "initial_evasions": [2, 3, 4], "recovery_ticks": 100,
        "path_skip_check_ticks": 10, "path_skip_chance": "nextInt(3)==0", "path_skip_points": [1, 4],
        "evade_excluded_damage": ["fall", "fire", "poison"], "evasion_consumed_even_if_teleport_fails": True,
        "away_attempts": 64, "away_dx_dz": [-5, 5], "away_dy": [-4, 4], "minimum_threat_distance": 3,
        "old_minimum_teleport_y": 1, "modern_height_adaptation_required": True,
        "call_chain": ["EntityPhaseSpider constructor initializes evasion count", "c -> periodic recovery/path teleport", "attackEntityFrom -> consume evasion -> tryTeleportAwayFrom -> collision-safe tryTeleportTo"]},
    "ghoul": {"classes": ["EntityGhoul", "EntityAnimalWatcher", "EntityAIWatchAnimal"], "slowness_ticks": 50,
        "slowness_amplifier": 5, "requires_damage_health_loss": True, "prey": ["animals", "players", "villagers"],
        "food_item_class": "ItemMeat", "dig_cooloff_multiplier": .5, "experience_multiplier": 2,
        "call_chain": ["Ghoul constructor adds food-item/animal targets", "EntityAnimalWatcher constructor installs EntityAIWatchAnimal", "attackEntityAsMob adds slowness after health loss"]},
    "wight": {"classes": ["EntityWight"], "allowed_damage_aspects": ["fire", "lava", "silver", "magic"],
        "experience_drain_probability": .4, "experience_drain_formula": "max((playerLevel+1)*10,20), then getDrainAfterResistance",
        "requires_damage_health_loss": True, "experience_multiplier": 2,
        "call_chain": ["isImmuneTo gates target.attackEntityFrom", "attackEntityAsMob checks result.entityLostHealth and player type before random XP debit"]},
    "shadow_and_stalker": {"classes": ["EntityShadow", "EntityInvisibleStalker", "EntityAISeekLitTorch", "og"],
        "shadow_allowed_damage_aspects": ["silver", "magic", "sunlight"], "shadow_sunlight_damage_per_living_tick": 1000,
        "shadow_dark_heal_period_ticks": 40, "shadow_dark_heal_formula": "max(0,(int)((0.4-brightness)*10))",
        "shadow_vision_dimming": 2, "shadow_vision_resistance_kind": 4, "shadow_weakness_ticks": 600,
        "shadow_weakness_amplifier": 0, "seek_torch_random_denominator": {"shadow": 40, "stalker": 200},
        "seek_torch_range_xz": 16, "seek_torch_range_y": 4, "torch_disable_min_player_distance_exclusive": 4,
        "torch_disable_requires_no_hurt_timer": True, "stalker_has_no_special_damage_immunity_override": True,
        "call_chain": ["constructors -> EntityAISeekLitTorch", "living c -> og.tryDisableNearbyLightSource", "Shadow c -> sunlight/dark-heal -> super.c"]},
    "fire_elemental": {"classes": ["EntityFireElemental", "nn", "nb"], "health": 20, "damage": 5,
        "unconditional_self_water_damage": 1, "unconditional_self_water_period_ticks": 40,
        "wet_fizz_period_ticks": [2, 8], "wet_fizz_damage": 1, "immersed_fizz_damage_probability": 1,
        "rain_fizz_damage_probability": .25, "target_fire_seconds": 6,
        "immunity_truth": "water or immediate Snowball: vulnerable; !magic: immune; magic Arrow with Power launcher: immune; magic non-Arrow with Sharpness attacked-item: immune; other magic: vulnerable",
        "lava_heal_during_fire_sound": 4, "fire_sound_period_ticks": [30, 50], "heals_with_time": False,
        "biologically_alive": False, "fire_lava_immune": True, "experience_multiplier": 3,
        "bytecode_confirmed": ["c offsets44..69 apply unconditional water1 before G wet check70", "isImmuneTo offsets19..90 preserve the reversed enchantment exclusions"],
        "call_chain": ["constructor -> melee/player targets and rain/water escape goals", "c -> confirmed periodic self damage -> wet fizz OR dry fire sound/lava heal", "successful health-loss melee -> target fire6"]},
    "earth_and_clay": {"classes": ["EntityEarthElemental", "EntityClayGolem", "EntityAnimalWatcher", "nb"],
        "earth_damage": 12, "clay_damage": 6, "health": 30, "movement_speed": .2,
        "earth_natural_defense": 4, "clay_normal_defense": 0, "clay_hardened_defense": 2,
        "type_magma_bit": 256, "heat_magma_threshold": 100, "conversion_heat": 1000,
        "heat_gain_per_lava_tick": 1, "clay_heat_gain_in_fire": True, "cooling_base_per_tick": 1,
        "cooling_additions": ["precipitation", "world freezing", "feet biome freezing", "below biome freezing"],
        "water_quench_heat": 0, "hardened_clay_never_returns_to_normal": True,
        "normal_earth_allowed_damage": ["fall", "iron golem melee", "tool effective against current block material", "explosion"],
        "normal_clay_uses_base_immunity": True, "magma_ignite_probability": .4, "magma_ignite_seconds": [1, 8],
        "earth_spawn_blocks": ["stone", "obsidian", "netherrack", "end_stone"], "clay_spawn_blocks": ["clay"],
        "drop_blocks_count": 1, "stone_drop_is_cobblestone": True, "experience_multiplier": 3,
        "call_chain": ["finalize spawn a(oi) -> choose material below -> setTypeForBlock", "c -> heat/cooling -> normal/magma or permanent clay hardening", "attackEntityFrom -> isImmuneTo checks effective mining tool", "AnimalWatcher AI permits digging up to own block harvest level"]},
    "gelatinous_cubes": {"classes": ["EntityCubic", "EntityGelatinousCube", "ts", "EntityJelly", "EntityBlob", "EntityOoze", "EntityPudding"],
        "size_draw": [1, 2, 4], "ooze_size_draw_after_cap": [1, 2, 2], "dimension_per_size": .5,
        "health_formula": "size*size", "damage_multiplier": {"slime": 1, "jelly": 2, "blob": 3, "ooze": 3, "pudding": 4},
        "damage_formula": "size*typeMultiplier", "experience_formula": "size*(typeMultiplier+(isAcidic?1:0))",
        "sphere_drop_subtype": {"slime": 0, "jelly": 1, "blob": 2, "ooze": 3, "pudding": 4},
        "sphere_drop_only_size": 1, "chemical_kind": {"slime": "pepsin", "jelly": "pepsin", "blob": "pepsin", "ooze": "acid", "pudding": "acid"},
        "sphere_drop_count_formula": "nextInt(3)+optional nextInt(looting+1); if not recently hit reduce by nextInt(count+1)",
        "idle_jump_delay_ticks": [40, 120], "target_jump_delay_ticks": 10, "ooze_climbing_speed": .2,
        "ooze_max_size": 2, "ooze_allowed_damage_aspects": ["lava", "magic", "snowball"],
        "pudding_allowed_damage_aspects": ["fire", "lava", "magic", "snowball"],
        "ooze_and_pudding_player_thrown_snowball_direct_damage_flag": False,
        "blob_health_loss_stun_ticks": 50, "blob_stun_amplifier": 5,
        "contact_inventory_damage_selection_probability_formula": ".05*size", "contact_inventory_damage_amount_formula": "size*typeMultiplier",
        "attacking_weapon_chemical_wear": "typeMultiplier", "dissolve_update_ticks": 20,
        "dissolve_period_formula": "block.getDissolvePeriod(chemical)/20", "dissolve_progress_gain": "size per update",
        "simultaneous_dissolving_blocks": 64, "dissolve_progress_saved": True, "left_blocks_progress_retained_nearby": True,
        "food_stop_countdown_ticks": 20, "drop_item_pickup_delay_min_ticks": 60,
        "lava_bucket_contact_damage": 5, "lava_bucket_converts_to_stone_bucket": True, "water_bucket_converts_to_empty": True,
        "prey": ["players", "animals", "villagers", "chemically susceptible dropped items"],
        "death_split_count": [2, 4], "death_split_size_formula": "size/2", "burning_death_prevents_split": True,
        "acid_spawn_requires_stone_below": True, "is_biologically_alive": True,
        "pepsin_immune": True, "pudding_and_magma_acid_immune": True,
        "call_chain": ["constructor -> virtual setSize (Ooze cap) -> maxHealth=size²", "Cubic.bl selects player/item/prey and jumps/ooze movement", "contact -> mob damage -> inventory chemical damage", "GelatinousCube.c every20 -> saved dissolution and item damage", "onMeleeAttacked -> held weapon chemical wear", "Cubic.x health<=0 -> split"]},
    "magma_cube": {"classes": ["tl"], "damage_formula": "2*size", "experience_formula": "3*size",
        "natural_defense_formula": "bypassesMundaneArmor ? 0 : 2*size", "movement_speed": .2,
        "idle_jump_delay_ticks": [40, 120], "target_jump_delay_ticks": 20,
        "allowed_damage": ["water", "snowball", "tool effective against stone", "explosion"],
        "is_biologically_alive": False, "fire_and_lava_immune": True, "pepsin_and_acid_immune": True,
        "water_fizz_delay_ticks": [2, 8], "wet_water_damage": 1,
        "underwater_water_damage_probability": .25, "rain_water_damage_probability": .0625,
        "ignite_on_contact_probability_formula": "nextInt(10)<2*size", "contact_ignite_seconds_formula": "3*size",
        "attacks_animals_or_villagers": False, "magma_cream_drop_min_size": 2, "magma_cream_count_formula": "nextInt(4+looting)-2",
        "frozen_block_contact_absolute_damage": 1, "frozen_block_contact_attempts_melt": True,
        "instant_burn": ["tallgrass block", "plants material", "web material"],
        "call_chain": ["tl.az speed override; EntityCubic.size -> health", "tl.isImmuneTo effective stone-tool gate", "tl.c wet fizz", "EntityCubic.attackEntityAsMob magma ignition", "tl.onCollidedWithBlock melt and burning"]},
    "silverfish_family": {"classes": ["tq", "EntityNetherspawn", "EntityCopperspine", "EntityHoarySilverfish"],
        "health": 8, "movement_speed": .6, "damage": 3, "player_target_distance": 8,
        "attack_cooldown_ticks": 20, "attack_distance_exclusive": 1.2, "special_experience": 10, "normal_experience": 5,
        "copperspine_poison_ticks": 480, "poison_amplifier": 0, "hoary_slowness_ticks": 50,
        "hoary_slowness_amplifier": 5, "damage_recruitment_delay_ticks": 20,
        "recruit_infested_search_xyz": [10, 5, 10], "recruit_break_stop_probability": .25,
        "netherspawn_fire_immune": True, "netherspawn_water_fizz_damage": 1, "netherspawn_death_explosion_radius_blocks_and_entities": 1,
        "netherspawn_death_explosion_suppressed_by": ["wet", "inside lava", "snowball", "water damage"],
        "netherspawn_explosion_preserves": ["netherrack", "quartz ore", "nether brick"],
        "can_spawn_within_player_distance_5": False, "prey": ["players", "animals", "villagers"],
        "call_chain": ["subclasses inherit tq attributes and attackEntityAsMob subtype checks", "tq.attackEntityFrom -> recruitment timer -> infested search", "Netherspawn.attackEntityFrom death result -> conditional explosion"]},
    "silverfish_infestation": {"classes": ["aqs"], "registered_final_hardness": .75,
        "metadata_species": {"3": "netherspawn", "4": "copperspine", "0_1_2_underworld": "hoary", "0_1_2_other": "normal"},
        "metadata0_copper_search_xyz": [3, 3, 3], "upgrade_source_metadata": 0, "upgrade_target_metadata": 4,
        "upgrade_neighbor_connectivity": "six faces; same source metadata only",
        "drops_items": False, "release_causes": ["responsible player", "silverfish"],
        "released_target": "responsible player / recruiting silverfish target", "released_despawn_counter": -9600,
        "random_update_requires_player_distance": 16, "random_update_skips_nether": True,
        "random_update_requires_loaded_neighbor_chunks": True,
        "call_chain": ["aqz static registers new aqs(97).c(.75)", "aqs.dropBlockAsEntityItem -> spawnSilverfishEntity", "spawnSilverfishEntity -> getUpdatedSilverfishBlockMetadata -> convertNeighborMetadata -> recursive six-face convertVeinMetadata", "aqs.updateTick player16/loaded chunks -> updateSilverfishType"]},
    "snowballs": {"classes": ["up"], "damage_fire_elemental_or_blaze": 3, "damage_magma_or_netherspawn": 2,
        "damage_other_entities": 1, "burning_target_extinguish_probability": .5,
        "non_burning_magma_earth_quenches": True,
        "call_chain": ["up.onImpact entity branch -> per-kind damage -> burning50% extinguish or earth convertToNormal", "of.attackEntityFrom rejects player snowballs where subtype capability flag false"]},
    "undead_equipment_and_command": {"classes": ["EntityRevenant", "EntityLongdead", "EntityLongdeadGuardian", "EntityBoneLord", "EntityAncientBoneLord", "tr", "tw"],
        "revenant_health": 30, "revenant_damage": 7, "revenant_armor": "full rusted iron plate",
        "bone_lord_health": 20, "bone_lord_damage": 5, "bone_lord_armor": "full rusted iron plate",
        "ancient_bone_lord_health": 24, "ancient_bone_lord_damage": 8, "ancient_armor": "full ancient metal plate",
        "rusted_weapon_weights": {"sword": 2, "battle_axe_day10_non_tournament": 1, "war_hammer_day20_non_tournament": 1},
        "ancient_weapon_weights": {"sword": 2, "battle_axe_non_tournament": 1, "war_hammer_non_tournament": 1},
        "longdead_health": 12, "guardian_health": 24, "longdead_damage": 6, "guardian_damage": 8,
        "longdead_armor": "full ancient metal chain", "longdead_weapon": "type2 swordAncientMetal; otherwise bowAncientMetal",
        "longdead_armor_drop_chance_multiplier": .25, "longdead_defense": 1, "guardian_defense": 2,
        "guardian_swap_check_ticks": 10, "guardian_to_melee_distance_exclusive": 5, "guardian_to_bow_distance_exclusive": 6,
        "guardian_saved_secondary": "stowed_item_stack", "bone_lord_tick_period": 20, "max_summoned_troops": 6,
        "summon_probability_formula": "nextInt(8)<7-numTroops", "optional_second_summon_probability": .5,
        "summon_attempts_formula": "48-numTroops*8", "nearby_skeleton_heal_each_period": 1,
        "nearby_skeleton_box": [16, 8, 16], "experience_multipliers": {"revenant": 3, "longdead": 3, "guardian": 5, "bone_lord": 3, "ancient_bone_lord_relative_base_skeleton": 6},
        "call_chain": ["finalize spawn -> overridden addRandomEquipment -> source randomizeForMob", "LongdeadGuardian.c -> ranged/melee hysteresis swap", "BoneLord.c -> nearby heals/frenzy -> trySummonTroop -> world.spawn"]},
    "bats": {"classes": ["ro", "EntityVampireBat", "EntityGiantVampireBat", "EntityNightwing"],
        "scale": {"vampire": 1, "giant_vampire": 2, "nightwing": 1}, "health_formula": "3*scale", "damage_formula": "1*scale",
        "attack_cooldown_ticks": 20, "target_refresh_ticks": 20, "target_range": 32,
        "vampire_life_steal_formula": "actual health lost by target", "non_giant_full_health_feed_cooldown_ticks": 1200,
        "injury_clears_feed_cooldown": True, "nightwing_allowed_damage_aspects": ["silver", "magic", "sunlight"],
        "nightwing_sunlight_damage": 1000, "nightwing_dark_heal_period_ticks": 40,
        "nightwing_dark_heal_formula": "max(0,(int)((.4-brightness)*10))", "nightwing_player_vision_dimming": 1.25,
        "experience": {"vampire": 5, "giant_vampire": 10, "nightwing": 10},
        "call_chain": ["ro.updateSize and az call getScaleFactor -> only GiantVampireBat doubles", "VampireBat.l_ updates feed state and prey", "n collision -> damage -> actual life steal", "Nightwing.c sunlight/darkness"]},
    "wolves": {"classes": ["sf", "EntityDireWolf", "EntityHellhound"], "dire_wolf_health_wild": 16,
        "dire_wolf_health_tamed": 24, "dire_wolf_damage": 5, "dire_hostility_chance_per_tick": .004,
        "dire_hostility_player_range": 4, "dire_hostility_excludes": ["juvenile", "tamed", "already angry", "blue moon night"],
        "hellhound_health": 20, "hellhound_damage": 4, "hellhound_fire_probability": .4,
        "hellhound_fire_seconds": [1, 8], "hellhound_cannot_tame_or_breed": True,
        "hellhound_not_biologically_alive": True, "hellhound_fire_lava_immune": True,
        "call_chain": ["DireWolf.l_ chance -> find vulnerable player -> attack target", "sf tame re-applies virtual az ->24 health", "Hellhound constructor clears tame/breed tasks and installs hostile/prey goals"]},
    "creeper": {"classes": ["tf", "EntityInfernalCreeper", "of"], "normal_fuse_ticks": 30,
        "normal_explosion_base": 1, "infernal_base_multiplier": 2, "charged_multiplier": 2,
        "block_explosion_multiplier": .715, "living_explosion_multiplier": 1.1,
        "infernal_extra_natural_defense": 2, "infernal_size_multiplier_from_getScale": 1,
        "infernal_fire_lava_immune": True, "infernal_experience_multiplier": 3,
        "call_chain": ["Infernal constructor multiplies inherited bs only; static of.getScale returns1", "tf.l_ fuse -> separate block/living explosion magnitudes -> mobGriefing"]},
    "shared_living_and_monster": {"classes": ["nn", "of", "og", "tm", "on", "nb"],
        "monster_frenzy_bonus_formula": ".5*baseAttackDamage", "natural_regen_period_ticks": 1000,
        "natural_regen_formula": ".1*maxHealth", "natural_regen_enabled_by_virtual_healsWithTime": True,
        "monster_melee_respects_player_grace": True, "monster_melee_uses_actual_damage_result": True,
        "monster_on_hit_enchantments": ["knockback", "fire aspect", "stunning", "vampiric transfer", "thorns"],
        "fire_spread_chance_per_tick": {"fire_elemental": .05, "magma_earth_or_magma_cube": .04},
        "biological_health_and_resistance_matter_for_chemical_damage": True,
        "call_chain": ["entity tick -> nn fire spread", "of living tick -> virtual natural regen", "tm.attackEntityAsMob -> Damage object -> target.attackEntityFrom -> confirmed effect result -> special subclass behavior"]},
    "livestock": {"classes": ["rp", "EntityLivestock", "rr", "rq", "ry", "rz", "rw"],
        "initial_food_water_freedom_each": [.8, 1], "wellness_minimum_each": .25,
        "wellness_update_ticks": 100, "wellness_benefit": .1, "wellness_absence_penalty": -.005,
        "rain_water_benefit": .01, "production_increment_chance": .9, "base_manure_period_ticks": 24000,
        "child_wellness_inherits_minimum_of_parents": True,
        "crowded": "indoors OR more than2 living entities in expanded2/.5/2 box",
        "call_chain": ["Animal constructor installs food-item and fire avoidance", "Livestock constructor adds nutrition/water/freedom/predator/rain goals", "c every100 ->updateWellness ->production ->produceGoods", "c adult manure countdown -> manure drop"]},
}

VANILLA_SUMMARY = {
    "Creeper": "30tick引信；基数1；方块.715与生物1.1分别爆炸；落地推进引信；仙人掌显著击杀点回避。",
    "Skeleton": "6生命、4基础近战、.3移动；骷髅类型/下界亡灵切换、原始矿物箭、物品维修目标、骨领主狂暴、200tick凋零。",
    "Spider": "继承全量Arachnid投网/攀墙/鸡捕食；日间室外和平；地底骑乘Longdead；实际1%骑乘入口。",
    "Zombie": "40跟随距离、5近战、1/8聪明状态及Revenant永远聪明；日龄装备、动物追猎/挖掘父规则；村民感染装备限制。",
    "Slime": "整体父链改为GelatinousCube；消化液、溶方块/物品、动物村民捕食、武器与背包腐蚀、保存进度。",
    "Ghast": "10血；独立火球/爆炸及来源掉落；须按方法索引逐项复核激活分支。",
    "PigZombie": "继承Zombie改动；猪人装备、群体激怒、来源稀有掉落。",
    "Enderman": "40血、10近战；搬运/瞬移检查、追击移动奖励与间接攻击脱身；掉落不照现代默认。",
    "CaveSpider": "16血；永远敌对；480tick毒；200tick有限蛛网；双倍父XP。",
    "Silverfish": "8血3攻.6速度；捕食动物/村民；近身20tick冷却；20tick后招虫；子类特殊攻击藏在此父类。",
    "Blaze": "6基础近战；水/雪球相关伤害与发射序列/燃烧必须源调用链复核。",
    "LavaSlime": "Cubic父链；有效石头采掘工具/爆炸/水/雪球才可伤；天然防御size*2；接触点火；被水湿伤。",
    "Bat": "3血；挂顶/尺寸与飞行目标重写；吸血/夜翼子类共用目标飞行与同步。",
    "Witch": "受玩家攻击后60tick，尝试召1..3盟狼；最大16次；确定性诅咒种子、6000tick诅咒延迟；专属巡游与掉落。",
    "Pig": "Livestock；9材料胡萝卜钓竿；惊逃/同伴警报；粪便周期×2。",
    "Sheep": "Livestock；羊毛灼烧6次转已剪；肉/羊毛来源掉落；生产与剪毛数值。",
    "Cow": "20血；Livestock；milk0..100，生产恢复；取奶资源消耗已有部分在019/020实现。",
    "Chicken": "Livestock；羽毛库存1..2、生产/剥羽；粪便周期×16；蛋生产受健康状态影响。",
    "MushroomCow": "继承Cow健康/奶机制；蘑菇汤容器与剪毛转换。",
    "Wolf": "野生8/驯服12血，3攻；等级影响驯服结果；生物捕食/盟狼状态及警报。",
    "SnowMan": "来源环境与投雪球行为；完整差异以直接方法索引为候选，尚不假称语义全提取。",
    "Ozelot": "捕鸡/驯服食物/逃避；与吸血蝠受击反击目标联动。",
    "VillagerGolem": "100血；原始实际自然防御/攻击/掉落与村庄召唤要求独立复核。",
    "EntityHorse": "动物父规则、鞍/马甲、饥饿与受击惊逃；方法索引保留参数细节。",
    "Villager": "交易来源与阶段、人口/生成/怪物目标联动；交易堆叠已030另做，未并入此语义确认表。",
    "EnderDragon": "整体R196补丁；方法索引作为差异候选，不能仅套现代数值宣称全量复刻。",
    "WitherBoss": "整体R196补丁；护甲/爆炸/投射物及物资来源须逐方法取证。",
    "Giant": "本地发布包未覆盖tk.class，直接实体类沿用官方1.6.4；仍继承已修改的公共Monster父链。",
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def methods(text: str) -> list[dict]:
    masked = re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*', lambda match: " " * len(match.group()), text, flags=re.S)
    result = []
    for match in re.finditer(r"(?m)^    (?:public|protected|private)\s+(?:static\s+|final\s+|abstract\s+)*(?:[\w<>\[\].?]+\s+)?(?P<name>\w+)\((?P<args>[^\n{}]*)\)\s*\{", masked):
        opening = masked.find("{", match.start(), match.end())
        depth, end = 1, opening + 1
        while depth and end < len(masked):
            depth += (masked[end] == "{") - (masked[end] == "}")
            end += 1
        code = text[match.start():end]
        result.append({"name": match["name"], "arguments": match["args"].strip(),
                       "line": text.count("\n", 0, match.start()) + 1, "end_line": text.count("\n", 0, end) + 1,
                       "signature": text[match.start():opening].strip(), "body_sha256": sha(code.encode()), "source_code": code})
    return result


def extract() -> None:
    runtime = json.loads((ROOT / "reference-data/runtime-registry.json").read_text(encoding="utf-8"))
    if sha(BASE_JAR.read_bytes()) != BASE_SHA:
        raise ValueError("Official 1.6.4 baseline hash mismatch")
    with zipfile.ZipFile(BASE_JAR) as base:
        assert b"generic.maxHealth" in base.read("tp.class") and b"generic.followRange" in base.read("tp.class")
        class_cache = {}

        def source_class(name):
            if name in class_cache:
                return class_cache[name]
            path = SOURCE / (name + ".java")
            overlay = path.exists()
            if not overlay:
                path = VANILLA / (name + ".java")
            if not path.exists():
                return None
            text = path.read_text(encoding="utf-8")
            parent = re.search(r"\bclass\s+" + re.escape(name) + r"\s+extends\s+(\w+)", text)
            byte_path = ROOT / ".reference/r196/classes" / (name + ".class")
            byte_data = byte_path.read_bytes() if byte_path.exists() else base.read(name + ".class")
            value = {"class": name, "source_file": path.relative_to(ROOT).as_posix(), "parent": parent[1] if parent else None,
                     "r196_overlay": overlay, "class_sha256": sha(byte_data), "java_sha256": sha(text.encode()), "methods": methods(text)}
            class_cache[name] = value
            return value

        def lineage(name):
            result = []
            while name and name not in result:
                result.append(name)
                source = source_class(name)
                name = source["parent"] if source else None
            return result

        entities = []
        for entry in runtime["entities"]:
            if not (50 <= entry["id"] <= 66 or 90 <= entry["id"] <= 100 or entry["id"] == 120 or 512 <= entry["id"] <= 540):
                continue
            cls = source_class(entry["class"])
            assert cls is not None, entry
            registered = entry["registered_name"]
            anchor = next(i + 1 for i, line in enumerate((SOURCE / "nt.java").read_text().splitlines()) if '"' + registered + '"' in line)
            ancestors = lineage(entry["class"])
            families = [key for key, rule in RULES.items() if set(ancestors).intersection(rule["classes"])]
            attribute_evidence = []
            for ancestor in reversed(ancestors):
                definition = source_class(ancestor)
                if definition is None:
                    continue
                for method in definition["methods"]:
                    if method["name"] != "az":
                        continue
                    for line_no, line in enumerate(method["source_code"].splitlines(), method["line"]):
                        for symbol, label in ATTRIBUTES.items():
                            if re.search(r"(?:setEntityAttribute|\.a)\(tp\." + symbol + r"\b", line):
                                attribute_evidence.append({"attribute": label, "source_class": ancestor, "line": line_no, "expression": line.strip()})
            entities.append({"legacy_id": entry["id"], "registered_name": registered, "class": entry["class"],
                             "is_new_r196_living_entity": entry["id"] >= 512, "default_registration": True,
                             "spawn_egg_registered": entry["spawn_egg"] is not None,
                             "registration_source": {"file": "nt.java", "line": anchor, "guarded_runtime_confirmed": True},
                             "inheritance_chain": ancestors, "behavior_families": families, "attribute_call_chain": attribute_evidence,
                             "vanilla_change_summary": VANILLA_SUMMARY.get(registered),
                             "source_class_sha256": cls["class_sha256"],
                             "status": "traced_family_rules_and_method_index; remaining branches explicitly pending"})
        for rule in RULES.values():
            for name in rule["classes"]:
                assert source_class(name) is not None, name
        private_index = ROOT / ".reference/r196/derived/mob-behavior-method-bodies-031.json"
        private_index.parent.mkdir(parents=True, exist_ok=True)
        private_index.write_text(json.dumps({"author": "NingMeng123", "public_index": "spec/mob-behavior-source-031.json",
                                            "source_classes": list(class_cache.values())}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        classes = [{**row, "methods": [{key: value for key, value in method.items() if key != "source_code"}
                                      for method in row["methods"]]} for row in class_cache.values()]
        # Every direct method has a stable source location and hash. Full decompiled
        # bodies remain in the local reference archive rather than becoming bundled code.
        classes.sort(key=lambda row: row["class"])
        new = [entry for entry in entities if entry["is_new_r196_living_entity"]]
        assert len(new) == 29 and [entry["legacy_id"] for entry in new] == list(range(512, 541))
        result = {"schema_version": 1, "author": "NingMeng123", "archive_sha256": runtime["archive_sha256"],
                  "baseline_sha256": BASE_SHA, "entity_instance_execution": False,
                  "source_authority": "R196 actual registered classes, inherited direct call chains and confirmed bytecode; no live world execution",
                  "attribute_symbol_map": ATTRIBUTES, "baseline_attribute_defaults": {"max_health": 20, "follow_range": 32, "knockback_resistance": 0, "movement_speed": .7, "attack_damage": 2},
                  "counts": {"new_registered_living": 29, "existing_registered_living": len(entities) - 29, "indexed_classes": len(classes), "indexed_methods": sum(len(row["methods"]) for row in classes)},
                  "entities": entities, "behavior_families": RULES, "source_classes": classes,
                  "live_unregistered_support_entities": [{"class": "EntityWeb", "creator": "EntityArachnid.attackEntityWithRangedAttack", "reason_not_disabled": "direct constructor and world.spawn call, despite no EntityList row"}],
                  "confirmed_surprising_actual_behavior": ["FireElemental receives water1 every40 ticks even while dry; javap offsets44..69", "FireElemental rejects magical arrow or melee damage when the bow has only Flame or the melee weapon has only Fire Aspect; javap isImmuneTo offsets19..90 and ye.hasEnchantment offsets0..22", "InfernalCreeper getScale resolves static of.getScale=1; only blast base doubles", "empty subtype classes Copperspine/Hoary/GiantBat/BlackWidow inherit real subtype branches in parents"],
                  "evidence_gaps": ["This is a behavior extraction, not proof all mobs have been implemented or enabled for natural spawning in the modern port.", "Every original living class has a method index, but unsummarized branch differences (notably bosses/horses/golems) are pending semantic extraction, not invented default values.", "Legacy non-goal movement speeds and terrain-height checks require explicit modern adaptation; raw attribute expressions are source facts, not measured modern velocities.", "Player curse/vision resistance and global acid item susceptibility invoke separate shared systems; verify their implementation before claiming exact end-to-end behavior.", "Spawn biome/dimension/day/moon conditions live outside entity classes and must be reconciled with the independent spawn extraction.", "Null-world instance attribute observations are not substituted for size/taming/heat dynamic formulas."]}
        out = ROOT / "spec/mob-behavior-source-031.json"
        out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"output": out.relative_to(ROOT).as_posix(), **result["counts"]}, ensure_ascii=False))


def map_chemical_items() -> None:
    """Stack-limit adaptation IDs are not chemical material evidence: use exact identities only."""
    source = json.loads((ROOT / "spec/mobs-source-031.json").read_text(encoding="utf-8"))
    flags = {row["legacy_id"]: row for row in source["items_corrosion"]}
    mapping = json.loads((ROOT / "spec/stacking-map-030.json").read_text(encoding="utf-8"))
    rows = []
    for entry in mapping["mappedItems"]:
        if entry["mapping_kind"] != "source":
            continue
        values = [flags[row["legacy_id"]] for row in entry["source_items"] if row["legacy_id"] in flags]
        if not values:
            continue
        assert len({(row["pepsin"], row["acid"]) for row in values}) == 1, entry
        rows.append({"item": entry["modern_id"], "pepsin": values[0]["pepsin"], "acid": values[0]["acid"],
                     "source_ids": sorted({row["legacy_id"] for row in values})})
    result = {"schema_version": 1, "author": "NingMeng123",
              "source": "spec/mobs-source-031.json items_corrosion; spec/stacking-map-030.json source identity rows only",
              "items": rows, "legacy_flags": list(flags.values())}
    out = ROOT / "src/main/resources/mite/mob-chemical-items-031.json"
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"chemical_output": str(out.relative_to(ROOT)), "modern_exact_items": len(rows), "legacy_items": len(flags)}))


if __name__ == "__main__":
    extract()
    map_chemical_items()
