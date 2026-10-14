"""Trace actual native R196 death rewards without publishing decompiled method bodies. Author: NingMeng123."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[2]
RULES = {
    "creeper": "og nextInt(3)+positive looting nextInt(looting+1); nonrecent reduce uniformly; each getter is gunpowder when recent or nextInt(3)==0. Skeleton kills separately draw one of legacy discs2256..2267.",
    "skeleton": "type1 coal=nextInt(3+looting)-1, nonrecent reduction; recent and not massive rare nextInt(200)<5+2*looting gives skull1. type0 rusty arrows=nextInt(2+looting), nonrecent reduction. type2 no arrows. All types bones=nextInt(3), nonrecent reduction.",
    "spider": "Recent only: emit each remaining saved num_webs string and consume stock; eye if nextInt(3)==0 OR nextInt(1+butchering)>0.",
    "giant": "Inherited og few-item RNG still draws counts, but s() returns0: no materials.",
    "zombie": "Flesh one with nextFloat<recent?.5:.25. Recent/nonmassive rare nextInt(200)<5+2*looting then uniformly copper/silver/gold/iron nugget.",
    "slime": "Existing cube source family helper; size1 nextInt(3)+positive looting extra, nonrecent reduction. Size>1 no material ball; separate source splitting.",
    "ghast": "Tears nextInt(2), minimum1 on responsible-player reflected fireball. Gunpowder nextInt(3). No looting addition.",
    "zombified_piglin": "Flesh one at .5/.25; nuggets nextInt(2+looting); recent/nonmassive rare nextInt(200)<5+2*looting gives gold ingot.",
    "enderman": "Pearls nextInt(2+looting), including nonrecent kills; no additional nonrecent reduction.",
    "cave_spider": "EntityArachnid same death algorithm as spider; initial saved webs nextInt(4) without ordinary-spider subtraction.",
    "silverfish": "Inherited og few-item counts but zero item getter: no materials.",
    "blaze": "Only recent player hit: rods nextInt(2+looting).",
    "magma_cube": "Existing cube helper; size>1 and recent: cream=nextInt(4+looting)-2. No second new helper invocation.",
    "ender_dragon": "No source item rewards; independent source dragon XP death sequence remains existing implementation.",
    "wither": "One nether star through few-items (global massive-fall10% still applies), modern extended item lifetime retained.",
    "bat": "Inherited og default zero getter: no material rewards.",
    "witch": "nextInt(5+looting)+1 independent picks from 18 slots; duplicate stick weight. Potion slot uniformly six source yp/zp metadata; red flower subtype2.",
    "pig": "If isWell: nextInt(3)+1+nextInt(1+butchering) pork; source burning branch cooked. Installed saddle returned by one modern inventory path.",
    "sheep": "Unsheared/nonburning nextInt(2)==0 gives color wool; healthy meat=max(1,nextInt(2)+nextInt(1+butchering)); independently nextInt(2)==0 leather. Burning cooked meat and no wool.",
    "cow": "Leather nextInt(3)+1. Healthy meat nextInt(3)+1+nextInt(1+butchering); if meat1, nextInt(2)==0 increments. Burning cooked beef.",
    "chicken": "Burning gives healthy cooked chicken only; otherwise remaining saved feathers plus healthy raw chicken, exactly one meat.",
    "squid": "Recent only one ink sack subtype0; environment none.",
    "wolf": "Exactly one leather, independent of recent-player flag.",
    "mooshroom": "Inherited rr cow death algorithm including isWell and leather.",
    "snow_golem": "Snowballs nextInt(8+4*looting).",
    "ocelot": "rx empty override: no materials and no few-item count draws, including source tame cat subtype.",
    "iron_golem": "Red flower subtype0 nextInt(3). Recent/nonmassive only: iron nuggets3+nextInt(3+looting), never native iron ingots.",
    "horse": "Skins1+nextInt(3), s() calls nextInt(4) every skin (skeleton bone, zombie 75% flesh, live leather). Live horse meat1+nextInt(1+butchering)+horseType0 nextInt(2), raw/cooked beef.",
    "villager": "Inherited og zero getter: no death materials; carried modern inventory recovery not silently deleted.",
}

def main():
    source = json.loads((ROOT / "spec/mobs-source-031.json").read_text(encoding="utf-8"))
    native = [row for row in source["entities"] if row.get("living") and str(row.get("modern_id", "")).startswith("minecraft:")]
    assert len(native) == 29 and set(row["kind"] for row in native) == set(RULES)
    rows = []
    for entity in native:
        evidence = []
        for method in entity["source_methods"]:
            name = method["method"]
            if name in {"dropFewItems", "s", "a", "getBaseChanceOfRareDrop", "isWell", "getButcheringModifier"}:
                evidence.append({key: method[key] for key in ("class", "method", "source_file", "start_line", "end_line", "body_sha256")})
        rows.append({"legacy_id": entity["legacy_id"], "source_class": entity["source_class"], "modern_id": entity["modern_id"],
                     "hierarchy": entity["hierarchy"], "algorithm": RULES[entity["kind"]], "evidence": evidence})
    text = (ROOT / ".reference/runtime-helper/vanilla-loot-potions-031.stdout.log").read_text(encoding="utf-8")
    potions = json.loads(text.split("VANILLA_LOOT_POTIONS_BEGIN\n", 1)[1].split("\nVANILLA_LOOT_POTIONS_END", 1)[0])
    assert not potions["world_created"] and len(potions["guard_checks_passed"]) == 7
    result = {"schema_version": 1, "author": "NingMeng123", "native_living_count": 29,
              "source_archive_sha256": "fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3",
              "source_input_sha256": hashlib.sha256((ROOT / "spec/mobs-source-031.json").read_bytes()).hexdigest(),
              "entities": rows, "witch_potion_oracle": potions,
              "source_shared": {"baby_material_loot": False, "massive_fall_material_chance": .1,
                  "massive_fall_rare_loot": False, "held_recent_drop": "exact existing stack; source poor/worn penalty remains",
                  "ordinary_equipment_chance": "og forced/picked-up, recent, looting and massive-fall branches via R196MobEquipment",
                  "livestock_well": "min(food,water,freedom)>=.25", "livestock_initial_needs": ".8+nextFloat*.2",
                  "chicken_maximum_feathers": "nextInt(2)+1; if2 and nextInt(2)==0 decrement", "spider_initial_webs": "nextInt(4), ordinary positive minus1; cave unchanged"},
              "modern_adaptations": ["Source subtype identities use modern split entity IDs, including zombie villager, wither skeleton, cat and horse variants.",
                  "Source drops are captured by NeoForge's native cancellable LivingDropsEvent; no additive source loot modifier runs a second time.",
                  "Modern installed saddles, cargo and charged-creeper heads stay available; pig saddle recovery is outside the old few-items fall suppression.",
                  "Source creeper records retain the twelve original discs but now participate in cancellable doMobLoot death transaction.",
                  "Native modern species without an actual R196 identity keep their native loot tables. Cubic deaths reuse the prior source cube helper.",
                  "Existing attachment-based contained-arrow recovery remains the single stored-arrow drop path."],
              "remaining_gaps": ["Livestock needs are initialized, persisted and read by actual meat gates; full food/water/freedom metabolism and ecology remain the agriculture system.",
                  "mite:butchering is queried when registered; current absent enchantment yields0, rather than substituting modern Looting.",
                  "Chicken saved feather depletion is supported; full source nonfatal feather shedding/regrowth remains animal behavior work."]}
    out = ROOT / "spec/native-mob-loot-source-031.json"
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"path": str(out.relative_to(ROOT)), "entities": len(rows), "sha256": hashlib.sha256(out.read_bytes()).hexdigest()}))

if __name__ == "__main__": main()
