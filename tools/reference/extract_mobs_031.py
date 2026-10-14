"""Extract actual R196 entity registration, stats, inherited rules and render assets. Author: NingMeng123.

No legacy untracked research specs are inputs. The optional JVM oracle constructs
null-world mobs under RegistryExport.ReadOnlyGuard, then reads in-memory getters.
It never creates World/client/server, runs spawn/tick/death callbacks or GL rendering.
"""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / ".reference"
HELPER = WORK / "runtime-helper"
DECOMPILED = WORK / "r196/decompiled"
NATIVE_NAMES = {
    "Creeper": "creeper", "Skeleton": "skeleton", "Spider": "spider", "Giant": "giant", "Zombie": "zombie",
    "Slime": "slime", "Ghast": "ghast", "PigZombie": "zombified_piglin", "Enderman": "enderman",
    "CaveSpider": "cave_spider", "Silverfish": "silverfish", "Blaze": "blaze", "LavaSlime": "magma_cube",
    "EnderDragon": "ender_dragon", "WitherBoss": "wither", "Bat": "bat", "Witch": "witch", "Pig": "pig",
    "Sheep": "sheep", "Cow": "cow", "Chicken": "chicken", "Squid": "squid", "Wolf": "wolf",
    "MushroomCow": "mooshroom", "SnowMan": "snow_golem", "Ozelot": "ocelot", "VillagerGolem": "iron_golem",
    "EntityHorse": "horse", "Villager": "villager",
    "Item": "item", "XPOrb": "experience_orb", "LeashKnot": "leash_knot", "Painting": "painting", "Arrow": "arrow",
    "Snowball": "snowball", "Fireball": "fireball", "SmallFireball": "small_fireball", "ThrownEnderpearl": "ender_pearl",
    "EyeOfEnderSignal": "eye_of_ender", "ThrownPotion": "potion", "ThrownExpBottle": "experience_bottle", "ItemFrame": "item_frame",
    "WitherSkull": "wither_skull", "PrimedTnt": "tnt", "FallingSand": "falling_block", "FireworksRocketEntity": "firework_rocket",
    "Boat": "boat", "MinecartRideable": "minecart", "MinecartChest": "chest_minecart", "MinecartFurnace": "furnace_minecart",
    "MinecartTNT": "tnt_minecart", "MinecartHopper": "hopper_minecart", "MinecartSpawner": "spawner_minecart", "EnderCrystal": "end_crystal",
}
TEXTURES = {
    "Ghoul": ["ghoul"], "Wight": ["wight"], "InvisibleStalker": ["wight"], "Shadow": ["shadow"],
    "FireElemental": ["fire_elemental"], "DemonSpider": ["spider/demon_spider", "spider/demon_spider_glow"],
    "WoodSpider": ["spider/wood_spider", "spider/wood_spider_glow"], "BlackWidowSpider": ["spider/black_widow", "spider/black_widow_glow"],
    "PhaseSpider": ["spider/phase_spider", "spider/phase_spider_glow"], "Hellhound": ["hellhound/hellhound"],
    "DireWolf": ["dire_wolf/neutral", "dire_wolf/tame", "dire_wolf/angry", "dire_wolf/collar"],
    "InfernalCreeper": ["creeper/infernal_creeper", "creeper/creeper_armor"], "Revenant": ["zombie/revenant"],
    "Jelly": ["slime/jelly"], "Blob": ["slime/blob"], "Ooze": ["slime/ooze"], "Pudding": ["slime/pudding"],
    "VampireBat": ["bat/vampire"], "GiantVampireBat": ["bat/vampire"], "Nightwing": ["bat/nightwing"],
    "Longdead": ["skeleton/longdead"], "LongdeadGuardian": ["skeleton/longdead_guardian"],
    "BoneLord": ["skeleton/bone_lord"], "AncientBoneLord": ["skeleton/longdead_guardian"],
    "Netherspawn": ["silverfish/netherspawn"], "Copperspine": ["silverfish/copperspine"], "HoarySilverfish": ["silverfish/hoary"],
    "EarthElemental": [f"earth_elemental/{material}/earth_elemental_{material}{suffix}" for material in ("stone", "obsidian", "netherrack", "end_stone") for suffix in ("", "_magma")]
        + ["earth_elemental/earth_elemental_glow", "earth_elemental/earth_elemental_magma_glow"],
    "ClayGolem": ["earth_elemental/clay/earth_elemental_clay", "earth_elemental/clay/earth_elemental_clay_hardened", "earth_elemental/earth_elemental_glow"],
}
IMPORTANT_METHODS = {
    "az", "dropFewItems", "s", "getDropItemSubtype", "getBaseChanceOfRareDrop", "getExperienceValue", "getNaturalDefense",
    "isImmuneTo", "isHarmedByFire", "isHarmedByLava", "isHarmedByPepsin", "getCanSpawnHere", "isValidLightLevel",
    "addRandomWeapon", "addRandomEquipment", "getResistanceToPoison", "getResistanceToParalysis", "getResistanceToDrain", "getResistanceToShadow",
    "attackEntityAsMob", "attackEntityFrom", "getDamageTypeVsItems", "getAttackStrengthMultiplierForType", "willEat", "onFoodEaten",
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def snake(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def run_oracle(java):
    java = Path(java).resolve()
    jars = sorted((WORK / "downloads/libraries").rglob("*.jar"))
    classpath = os.pathsep.join(map(str, [HELPER, HELPER / "r196-overlay.jar", WORK / "r196/resources", WORK / "downloads/minecraft-1.6.4-client.jar", *jars]))
    compile_result = subprocess.run([str(java.with_name("javac.exe" if os.name == "nt" else "javac")), "--release", "17", "-cp", classpath,
                                    "-d", str(HELPER), str(ROOT / "tools/reference/RegistryExport.java"), str(ROOT / "tools/reference/MobsRulesExport.java")],
                                   capture_output=True, text=True, encoding="utf-8", errors="replace")
    (HELPER / "mobs-031-compile.log").write_text(compile_result.stdout + compile_result.stderr, encoding="utf-8")
    if compile_result.returncode:
        raise RuntimeError("Mob oracle compilation failed; inspect the guarded helper log")
    result = subprocess.run([str(java), "-Xmx1G", "-Djava.awt.headless=true", "-Djava.security.manager=allow", "-cp", classpath, "MobsRulesExport", str(WORK)],
                            cwd=HELPER, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
    (HELPER / "mobs-031-oracle.stdout.log").write_text(result.stdout, encoding="utf-8")
    (HELPER / "mobs-031-oracle.stderr.log").write_text(result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError("Guarded mob oracle failed; no fallback disables the guard")


def methods(class_name):
    path = DECOMPILED / (class_name + ".java")
    if not path.is_file():
        return []
    source = path.read_text(encoding="utf-8")
    masked = re.sub(r'"(?:\\.|[^"\\])*"|/\*.*?\*/|//[^\n]*', lambda m: "".join("\n" if c == "\n" else " " for c in m[0]), source, flags=re.S)
    pattern = r"(?m)^\s*(?:public|protected|private)\s+(?:(?:final|static|synchronized|abstract|native)\s+)*(?:[\w\[\]<>?,]+\s+)?(\w+)\([^)]*\)\s*\{"
    output = []
    for match in re.finditer(pattern, masked):
        at = match.end(); depth = 1
        while depth and at < len(masked):
            depth += (masked[at] == "{") - (masked[at] == "}"); at += 1
        text = source[match.start():at].strip()
        output.append({"class": class_name, "method": match[1], "source_file": path.name,
                       "start_line": source[:match.start()].count("\n") + 1, "end_line": source[:at].count("\n") + 1,
                       "body": text, "body_sha256": hashlib.sha256(text.encode()).hexdigest()})
    return output


def loot_profile(name):
    none = {"algorithm": "no_intrinsic_few_items", "items": [], "equipment_and_contained_items_are_separate": True}
    if name in {"Ghoul", "InvisibleStalker", "Shadow", "FireElemental", "Hellhound", "VampireBat", "GiantVampireBat", "Nightwing", "Netherspawn", "Copperspine", "HoarySilverfish"}:
        return none
    if name == "DireWolf":
        return {"algorithm": "fixed", "items": [{"legacy_item": 334, "modern_id": "minecraft:leather", "count": 1}], "source": "sf.dropFewItems"}
    if name in {"Wight", "Revenant"}:
        return {"algorithm": "source_zombie_flesh_and_nuggets", "flesh_item": "minecraft:rotten_flesh", "flesh_count": 1,
                "flesh_probability_player": .5, "flesh_probability_other": .25,
                "rare_denominator": 50 if name == "Revenant" else 200, "rare_numerator": "5 + looting*2",
                "rare_requires_recent_player_hit": True, "rare_forbidden_after_massive_fall": True,
                "rare_uniform_choices": ["mite:copper_nugget", "mite:silver_nugget", "minecraft:gold_nugget", "minecraft:iron_nugget"],
                "rare_count": 1, "source": "tw.dropFewItems/getBaseChanceOfRareDrop" if name == "Revenant" else "EntityWight.dropFewItems + of.getBaseChanceOfRareDrop"}
    if name in {"Longdead", "LongdeadGuardian", "BoneLord", "AncientBoneLord"}:
        return {"algorithm": "source_skeleton", "arrow_item": "mite:ancient_metal_arrow" if name.startswith("Longdead") else "mite:rusted_iron_arrow",
                "arrow_draw": "nextInt(2 + looting); if not player and n>0, n -= nextInt(n+1)",
                "longdead_arrow_cap": "if n>0 then nextInt(3)==0 ? 1 : 0" if name.startswith("Longdead") else None,
                "bone_item": "minecraft:bone", "bone_draw": "nextInt(3); if not player and n>0, n -= nextInt(n+1)",
                "source": "tr.dropFewItems; bow/melee skeleton subtype and equipment drops remain separate"}
    if name in {"DemonSpider", "WoodSpider", "BlackWidowSpider", "PhaseSpider"}:
        return {"algorithm": "source_arachnid_remaining_webs", "string_item": "minecraft:string", "string_count": "remaining num_webs at death, not a fresh loot roll",
                "requires_recent_player_hit": True, "initial_web_draw": "nextInt(4)" if name == "DemonSpider" else "0 for PhaseSpider; otherwise max(0,nextInt(4)-1)",
                "eye_item": "minecraft:spider_eye", "eye_count": 1, "eye_condition": "nextInt(3)==0 || nextInt(1+butchering)>0", "source": "EntityArachnid.dropFewItems final override"}
    if name in {"Jelly", "Blob", "Ooze", "Pudding"}:
        return {"algorithm": "source_small_cube_common_drop", "requires_size": 1, "legacy_item": 341, "source_subtype": {"Jelly": 1, "Blob": 2, "Ooze": 3, "Pudding": 4}[name],
                "modern_id": {"Jelly": "mite:ochre_jelly", "Blob": "mite:crimson_blob", "Ooze": "mite:grey_gelatinous_sphere", "Pudding": "mite:black_pudding"}[name],
                "draw": "n=nextInt(3); looting>0 adds nextInt(looting+1); non-player n>0 subtracts nextInt(n+1)",
                "larger_death": "2+nextInt(3) children, size/2", "source": "og.dropFewItems + EntityGelatinousCube.s + EntityCubic.x"}
    if name in {"EarthElemental", "ClayGolem"}:
        return {"algorithm": "fixed_material_block", "count": 1, "items_by_variant": {"stone_normal": "minecraft:cobblestone", "stone_magma": "minecraft:cobblestone",
                "obsidian_normal": "minecraft:obsidian", "obsidian_magma": "minecraft:obsidian", "netherrack_normal": "minecraft:netherrack", "netherrack_magma": "minecraft:netherrack",
                "end_stone_normal": "minecraft:end_stone", "end_stone_magma": "minecraft:end_stone", "clay_normal": "minecraft:clay", "clay_hardened": "minecraft:terracotta"}, "source": "EntityEarthElemental.s/dropFewItems"}
    if name == "InfernalCreeper":
        return {"algorithm": "source_infernal_creeper", "item": "minecraft:gunpowder", "count_draw": "n=nextInt(4); if n==0 then n=nextInt(3); positive looting adds nextInt(looting+1); non-player n>0 subtracts nextInt(n+1)",
                "each_candidate_gate": "tf.s returns gunpowder when recently-hit timer>0 OR nextInt(3)==0; otherwise0", "source": "EntityInfernalCreeper.dropFewItems + tf.s"}
    return {"algorithm": "inherited_source_methods_recorded", "source": "nearest dropFewItems in source_methods"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-oracle", action="store_true")
    parser.add_argument("--java", type=Path)
    args = parser.parse_args()
    if args.run_oracle:
        if args.java is None:
            parser.error("--run-oracle requires --java")
        run_oracle(args.java)
    raw = (HELPER / "mobs-031-oracle.stdout.log").read_text(encoding="utf-8")
    oracle = json.loads(raw.split("MOBS_ORACLE_BEGIN\n", 1)[1].split("\nMOBS_ORACLE_END", 1)[0])
    runtime = json.loads((ROOT / "reference-data/runtime-registry.json").read_text(encoding="utf-8"))
    original = {row["id"]: row for row in runtime["entities"]}
    assert len(oracle["entities"]) == len(original) == 85
    assert {row["legacy_id"] for row in oracle["entities"]} == set(original)
    assert not oracle["world_created"] and not oracle["game_main_invoked"] and not oracle["spawn_or_tick_or_death_callbacks_invoked"]
    assert set(oracle["guard_checks_passed"]) == {"write_file", "process", "network", "native_load", "thread_create", "disable_guard", "unrelated_read"}
    assert len(oracle["items_corrosion"]) == 667
    assert {row["legacy_id"] for row in oracle["items_corrosion"]} == {row["id"] for row in runtime["items"]}
    assert all(type(row[key]) is bool for row in oracle["items_corrosion"] for key in ("pepsin", "acid", "fire", "lava"))
    manager = (DECOMPILED / "bgl.java").read_text(encoding="utf-8")
    languages = {}
    for line in (WORK / "r196/resources/assets/minecraft/lang/MITE.lang").read_text(encoding="utf-8-sig").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1); languages[key] = value
    spawn_sites = collections.defaultdict(list)
    for path in DECOMPILED.glob("*.java"):
        text = path.read_text(encoding="utf-8")
        for m in re.finditer(r"new acr\((\w+)\.class,\s*(\d+),\s*(\d+),\s*(\d+)\)", text):
            spawn_sites[m[1]].append({"source_file": path.name, "line": text[:m.start()].count("\n") + 1,
                                      "weight": int(m[2]), "minimum_group": int(m[3]), "maximum_group": int(m[4]), "statement": m[0],
                                      "activation": "source constructor/calling-context must be retained; not an unconditional global spawn claim"})
    entries = []
    pack_path = ROOT / "MITE Resource Pack 1.6.4.zip"
    with zipfile.ZipFile(pack_path) as pack:
        assets = set(pack.namelist())
        for entity in oracle["entities"]:
            entry = dict(entity)
            registered = original[entry["legacy_id"]]
            name = entry["registered_name"]
            entry["source"] = registered["source"]
            entry["spawn_egg"] = registered["spawn_egg"]
            entry["original_display_name"] = languages.get("entity." + name + ".name", name)
            new = entry["legacy_id"] >= 512
            entry["kind"] = snake(name) if new else NATIVE_NAMES.get(name)
            entry["modern_id"] = "mite:" + entry["kind"] if new else "minecraft:" + NATIVE_NAMES[name] if name in NATIVE_NAMES else None
            entry["registration_status"] = "registered_default" if not entry["abstract"] else "registered_abstract_not_instantiable"
            if name == "Giant": entry["registration_status"] = "registered_legacy_command_only_no_source_natural_spawn_or_egg"
            entry["natural_spawn_sites"] = spawn_sites[entry["source_class"]]
            entry["source_methods"] = [method for index, cls in enumerate(entry["hierarchy"]) for method in methods(cls)
                                       if index == 0 or method["method"] in IMPORTANT_METHODS]
            entry["loot_source"] = loot_profile(name) if new else {"algorithm": "inherited_source_methods_recorded", "source": "nearest dropFewItems/s in source_methods; constructor s sample is not a drop distribution"}
            if entry["variants"]:
                base = entry["variants"][0]
                attribute_values = {row["name"]: row["base"] for row in base["attributes"]}
                entry["base_attributes"] = {"max_health": attribute_values.get("generic.maxHealth"), "attack_damage": attribute_values.get("generic.attackDamage", 0),
                                            "movement_speed": attribute_values.get("generic.movementSpeed"), "follow_range": attribute_values.get("generic.followRange"),
                                            "knockback_resistance": attribute_values.get("generic.knockbackResistance")}
                dimensions = base.get("configured_adult_dimensions") or base
                entry["width"], entry["height"] = dimensions["width"], dimensions["height"]
                entry["experience"] = base["experience"]
                entry["base_state_semantics"] = "first explicit variant; cubes size1, earth stone_normal, clay normal; random spawn callback/armor/age/skeleton type not executed"
            else:
                entry["base_attributes"] = None; entry["experience"] = None; entry["width"] = None; entry["height"] = None
            if new:
                match = re.search(r"put\(" + entry["source_class"] + r"\.class, new (\w+)\((.*?)\)\);", manager)
                renderer = match[1]; chain = []; model_classes = []
                cur = renderer
                while (DECOMPILED / (cur + ".java")).is_file() and cur not in chain:
                    text = (DECOMPILED / (cur + ".java")).read_text(encoding="utf-8"); chain.append(cur)
                    model_classes.extend(re.findall(r"new (Model\w+|b[a-z]{2})\(", text))
                    parent = re.search(r"class\s+" + cur + r"\s+extends\s+(\w+)", text)
                    if not parent: break
                    cur = parent[1]
                entry["renderer"] = {"class": renderer, "hierarchy": chain, "registration_arguments": match[2], "model_classes_referenced": list(dict.fromkeys(model_classes)),
                                      "opacity": .05 if name == "InvisibleStalker" else 0 if name == "FireElemental" else 1, "source": "bgl.java"}
                textures = []
                for relative in TEXTURES[name]:
                    source_path = "assets/minecraft/textures/entity/" + relative + ".png"
                    assert source_path in assets, source_path
                    data = pack.read(source_path); assert data[:8] == b"\x89PNG\r\n\x1a\n"
                    width, height = struct.unpack("!II", data[16:24])
                    textures.append({"source": source_path, "width": width, "height": height, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
                entry["textures"] = textures
            entries.append(entry)
    registered_classes = {row["source_class"] for row in entries}
    unregistered = []
    for path in sorted(DECOMPILED.glob("Entity*.java")):
        if path.stem in registered_classes: continue
        text = path.read_text(encoding="utf-8")
        declaration = re.search(r"public\s+(abstract\s+)?(?:final\s+)?class\s+(\w+)\s+extends\s+(\w+)", text)
        if not declaration: continue
        unregistered.append({"class": path.stem, "parent": declaration[3], "abstract": bool(declaration[1]),
                             "classification": "not_in_primary_entity_registry; may be shared AI/base/particle/projectile implementation, not automatically deleted",
                             "source_file": path.name, "sha256": sha(path)})
    private_methods = []
    for entity in entries:
        for method in entity["source_methods"]:
            body = method.pop("body")
            method["signature"] = body.partition("{")[0].strip()
            private_methods.append({"legacy_entity_id": entity["legacy_id"], **method, "body": body})
    # Full decompilation is retained only in the ignored reference area. Public specifications
    # contain source locations, signatures, hashes and separately extracted rule formulae.
    (HELPER / "mobs-031-methods-private.json").write_text(json.dumps(private_methods, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    document = {"schema_version": 1, "author": "NingMeng123", "original_author": "Avernite", "source_version": "MITE 1.6.4 R196",
                "archive_sha256": runtime["archive_sha256"], "activation_flags": runtime["activation_flags"],
                "oracle": {key: value for key, value in oracle.items() if key not in {"entities", "items_corrosion"}},
                "provenance": {"runtime_registry_sha256": sha(ROOT / "reference-data/runtime-registry.json"), "oracle_source_sha256": sha(ROOT / "tools/reference/MobsRulesExport.java"),
                               "oracle_stdout_sha256": sha(HELPER / "mobs-031-oracle.stdout.log"), "guard_source_sha256": sha(ROOT / "tools/reference/RegistryExport.java"),
                               "overlay_sha256": sha(HELPER / "r196-overlay.jar"), "source_pack_sha256": sha(pack_path), "registration_class_sha256": sha(WORK / "r196/classes/nt.class")},
                "summary": {"registered_entities": 85, "registered_living": sum(row["living"] for row in entries), "registered_concrete_living": sum(row["living"] and not row["abstract"] for row in entries),
                            "registered_nonliving": sum(not row["living"] for row in entries), "new_mite_mobs": sum(row["legacy_id"] >= 512 for row in entries),
                            "constructor_failures": sum(row["constructor_error"] is not None for row in entries), "stat_variants": sum(len(row["variants"]) for row in entries),
                            "distinct_new_texture_assets": len({texture["source"] for row in entries for texture in row.get("textures", [])})},
                "entities": entries, "items_corrosion": oracle["items_corrosion"], "unregistered_implementation_classes": unregistered,
                "modern_split_identities": [{"legacy_id": 51, "modern_extra_ids": ["minecraft:wither_skeleton"], "source_state": "tr.bV()==1"},
                                            {"legacy_id": 54, "modern_extra_ids": ["minecraft:zombie_villager"], "source_state": "tw.bT()"},
                                            {"legacy_id": 98, "modern_extra_ids": ["minecraft:cat"], "source_state": "old tamed ocelot variants"},
                                            {"legacy_id": 100, "modern_extra_ids": ["minecraft:donkey", "minecraft:mule", "minecraft:zombie_horse", "minecraft:skeleton_horse"], "source_state": "horse type0..4"}],
                "boundaries": ["nt.entries registration is an identity fact, not proof of unconditional natural spawning.",
                               "Null-world construction executes virtual attribute initialization; it intentionally omits context-dependent world/server setup and spawn finalization.",
                               "Primary drop getter is a single possibly random in-memory sample, not an authoritative loot table; rule formulae and source-method locations/signatures/hashes are separate; complete bodies remain private in .reference.",
                               "Attribute attackDamage excludes held-item, enchantment, frenzy and target-specific bonuses.",
                               "Mob XP getter differs from final XP death conditions, baby/equipment modifiers and player eligibility.",
                               "Configured ageable adult shape is stored by nk.a(float,float) and may differ from width immediately after subclass construction; both are recorded.",
                               "Ooze requests size4 but clamps to actual_size2. Larger cubes split on death; source variants retain requested and actual sizes.",
                               "Textures are copied facts from the user-provided ZIP; model/renderer metadata does not claim modern visuals or mechanics are implemented.",
                               "No untracked prior entity/combat/modern research specification is an input."]}
    output = ROOT / "spec/mobs-source-031.json"
    output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(document["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
