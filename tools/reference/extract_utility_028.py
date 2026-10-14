"""Extract 028 utility identities, recipes and byte-identical pack assets. Author: NingMeng123."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = json.loads((ROOT / "reference-data/runtime-registry.json").read_text(encoding="utf-8"))
PACK = ROOT / "MITE Resource Pack 1.6.4.zip"
METALS = ["copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium"]
COINS = {"copper": 5, "silver": 25, "gold": 100, "ancient_metal": 500, "mithril": 2500, "adamantium": 10000}
NUGGETS = {"gold": "minecraft:gold_nugget", "iron": "minecraft:iron_nugget"}
ITEMS = {i["id"]: i for i in RUNTIME["items"]}
SYMBOLS = {s: i for i in RUNTIME["items"] for s in i["symbols"]}
MATERIALS = {m['scalars']['akc.name']:m['scalars'] for m in RUNTIME['materials']}
IDS = {}
entries = []
assets = []
songs = []

def write(path, data):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def title(metal):
    return "AncientMetal" if metal == "ancient_metal" else metal.title()

def add(symbol, path, kind, **facts):
    source = SYMBOLS["yc." + symbol]
    modern = "mite:" + path
    IDS[source["id"]] = modern
    materials = source['materials']
    entries.append({"id": modern, "kind": kind, "legacy_id": source["id"], "symbol": "yc." + symbol,
                    "source": source["source"], "max_stack": source["scalars"]["yc.cw"], 'source_materials':materials,
                    'harmed_by_fire':any(MATERIALS[m]['akc.is_harmed_by_fire'] for m in materials),
                    'harmed_by_lava':any(MATERIALS[m]['akc.is_harmed_by_lava'] for m in materials), **facts})
    return source

def copy(z, source, target, kind):
    data = z.read(source)
    dest = ROOT / target
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    assets.append({"kind": kind, "archive": PACK.name, "source": source, "target": target,
                   "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
    return data

def ogg_length(data):
    ident = data.index(b"\x01vorbis")
    rate = struct.unpack_from("<I", data, ident + 12)[0]
    pos, granule = 0, 0
    while pos < len(data):
        start = data.find(b"OggS", pos)
        if start < 0: break
        segments = data[start + 26]
        length = sum(data[start + 27:start + 27 + segments])
        current = struct.unpack_from("<Q", data, start + 6)[0]
        if current != 0xffffffffffffffff: granule = max(granule, current)
        pos = start + 27 + segments + length
    return granule / rate

with zipfile.ZipFile(PACK) as z:
    for metal, xp in COINS.items():
        source = add("coin" + title(metal), metal + "_coin", "coin", material=metal, experience=xp)
        IDS[SYMBOLS["yc." + {"copper":"copperNugget", "silver":"silverNugget", "gold":"bs", "ancient_metal":"ancientMetalNugget", "mithril":"mithrilNugget", "adamantium":"adamantiumNugget"}[metal]]["id"]] = NUGGETS.get(metal, "mite:" + metal + "_nugget")
        copy(z, "assets/minecraft/textures/items/" + source["scalars"]["yc.cA"] + ".png", "src/main/resources/assets/mite/textures/item/" + metal + "_coin.png", "texture")
    source = add("bottleOfDisenchanting", "bottle_of_disenchanting", "bottle", duration_ticks=32, drinkable=True, throwable=False)
    copy(z, "assets/minecraft/textures/items/bottle_of_disenchanting.png", "src/main/resources/assets/mite/textures/item/bottle_of_disenchanting.png", "texture")
    for metal in METALS:
        source = add("bucket" + title(metal) + "Stone", metal + "_stone_bucket", "stone_bucket", material=metal, volume=4)
        empty = SYMBOLS["yc.bucket" + title(metal) + "Empty"]
        IDS[empty["id"]] = "minecraft:bucket" if metal == "iron" else "mite:" + metal + "_bucket"
        copy(z, "assets/minecraft/textures/items/" + source["scalars"]["yc.cA"] + ".png", "src/main/resources/assets/mite/textures/item/" + metal + "_stone_bucket.png", "texture")
    for index, name in enumerate(["underworld", "descent", "wanderer", "legends"]):
        source = add("record" + name.title(), "music_disc_" + name, "record", title=name.title(), composer="The Fat Man", unique_signature=101+index)
        copy(z, "assets/minecraft/textures/items/" + source["scalars"]["yc.cA"] + ".png", "src/main/resources/assets/mite/textures/item/music_disc_" + name + ".png", "texture")
        data = copy(z, "assets/minecraft/records/imported/" + name + ".ogg", "src/main/resources/assets/mite/sounds/records/" + name + ".ogg", "sound")
        songs.append({"name": name, "length_in_seconds": ogg_length(data), "sample_length_source": "Ogg Vorbis final granule / sample rate", "comparator_output": index + 1,
                      "comparator_adaptation": "New modern comparator output follows source unique-record ordering; R196 did not expose 1.21 jukebox_song data."})

IDS.update({373:"minecraft:potion", 372:"minecraft:nether_wart", 263:"minecraft:coal"})
utility_ids = {i["legacy_id"] for i in entries}
recipes = []
timed = {}
for r in RUNTIME["crafting_recipes"]:
    output = r.get("output")
    if not output: continue
    components = r.get("components") or []
    if output["item_id"] not in utility_ids and not any(c and c["item_id"] in utility_ids for c in components): continue
    if any(c is None or c["item_id"] not in IDS for c in components) or output["item_id"] not in IDS:
        raise ValueError(f"Missing utility recipe map {r['order']}")
    id = f"mite:utility_028_{r['order']}"
    bench = (r.get("bench_material") or {}).get("name")
    recipes.append({"id":id,"source_order":r["order"],"output":IDS[output["item_id"]],"count":output["count"],
                    "inputs":[{"item":IDS[c["item_id"]],"count":c["count"],"subtype":c["subtype"]} for c in components],
                    "difficulty":r["difficulty"],"bench_material":bench,"propagate_components":r["scalars"].get("aaj.propagate_tag_compound",False),"source":r["source"]})
    timed[id] = {"difficulty":r["difficulty"],"required_bench":bench,"source_order":r["order"]}

source = (ROOT / ".reference/r196/decompiled/afp.java").read_text(encoding="utf-8")
underworld_line = next(l for l in source.splitlines() if "chest_contents_for_underworld =" in l)
rows = re.findall(r"new mk\(yc\.(\w+)\.cv, 0, (\d+), (\d+), (\d+)\)", underworld_line)
total = sum(int(row[3]) for row in rows)
loot = {"source":"afp.java:9 / 91-94; mk.java:61-118; ye.java:1145; yr.java:86","modern_table":"minecraft:chests/simple_dungeon","dimension":"mite:underworld",
        "minimum_rolls":8,"maximum_rolls":8,"source_total_weight":total+1,"enchantment_book_weight":1,
        "world_unique_records":True,"maximum_records_per_chest":1,"maximum_attempts_per_roll":4,
        "unique_edgecase_source": "mk.generateChestContents bytecode offsets85/100 goto283 without clearing local9; the fourth duplicate draw can leak a prohibited record through offset289 ifnonnull297.",
        "unique_edgecase_modern_fix": "Explicitly discard unavailable unique draws, including the final attempt; world uniqueness and one-record-per-chest are enforced rather than reproducing the confirmed duplicate bug.",
        "entries":[{"item":IDS[SYMBOLS["yc."+name]["id"]],"minimum":int(lo),"maximum":int(hi),"weight":int(weight)}
                   for name,lo,hi,weight in rows if name.startswith("record") or name.startswith("coin")]}
loot_fragments = {'dungeon_underworld':loot['entries']}
for profile, source_class in [('dungeon_surface','afp'),('desert','aji')]:
    line = next(l for l in (ROOT / '.reference/r196/decompiled' / (source_class+'.java')).read_text(encoding='utf-8').splitlines() if 'private static final mk[] a =' in l)
    pool = re.findall(r"new mk\(yc\.(\w+)\.cv, 0, (\d+), (\d+), (\d+)\)",line)
    loot_fragments[profile] = [{"item":IDS[SYMBOLS['yc.'+name]['id']],"minimum":int(lo),"maximum":int(hi),"weight":int(weight),"minimum_overworld_day":20}
                             for name,lo,hi,weight in pool if name.startswith('coin')]
data = {"schema_version":1,"author":"NingMeng123","archive_sha256":RUNTIME["archive_sha256"],
        "source_pack_sha256":hashlib.sha256(PACK.read_bytes()).hexdigest(),"entries":entries,"crafting_recipes":recipes,"songs":songs,"assets":assets,
        "source_loot":loot,"loot_pool_fragments":loot_fragments,
        "rules":{"coins":{"values":COINS,"enabled_by_default":True,"enable_source":"als.java:304 returns true","mint_xp_cost":"value per result coin","redeem_xp_reward":"value per consumed grid coin","mint_difficulty":100,"redeem_difficulty":25,"right_click_redeems":False},
                 "stone_buckets":{"source":["jv.java:426-442","ss.java:239-249","RecipesMITE.java:112"],"acquisition":"lava bucket in player main inventory converts when player eyes enter water; dropped lava bucket converts while submerged", "smelting_recipe":False,"dismantle_output":"matching empty bucket only","dismantle_difficulty":100},
                 "disenchanting":{"source":["ItemBottleOfDisenchanting.java:13-35","va.java:119/186","vc.java:72-87","uf.java:1694-1708/1747-1768"],"drink_ticks":32,"drink_output":"minecraft:glass_bottle","drink_clears":"all active effects and R196 player-world curses; not equipment enchantments","blocked_by_nausea":True,"blocked_underwater":True,"cannot_drink_curse_exempt":True,"anvil_consumption":1,"anvil_xp":0,"anvil_wear":0,"anvil_empty_bottle":False,"throwable":False}}}
write("spec/utility-028.json",data)
write("src/main/resources/mite/utility-028.json",data)
write("src/main/resources/mite/timed-crafting-028-utility.json",{"author":"NingMeng123","recipes":timed})
zh={};en={}
names={"copper":"铜","silver":"银","gold":"金","iron":"铁","ancient_metal":"远古金属","mithril":"秘银","adamantium":"艾德曼"}
for e in entries:
    key="item."+e['id'].replace(':','.')
    if e['kind']=='coin':zh[key]=names[e['material']]+"币";en[key]=e['material'].replace('_',' ').title()+" Coin"
    elif e['kind']=='stone_bucket':zh[key]="装满石头的"+names[e['material']]+"桶";en[key]=e['material'].replace('_',' ').title()+" Bucket of Stone"
    elif e['kind']=='bottle':zh[key]="祛魔之瓶";en[key]="Bottle of Disenchanting"
    else:zh[key]="音乐唱片";en[key]="Music Disc";zh[key+".desc"]=en[key+".desc"]="The Fat Man - "+e['title']
zh['item.mite.coin.experience']='每枚储存 %s 点经验';en['item.mite.coin.experience']='Stores %s XP per coin'
zh['item.mite.disenchanting.help']='饮用清除状态；放入铁砧移除装备附魔';en['item.mite.disenchanting.help']='Drink to clear effects; use in an anvil to remove equipment enchantments'
write("build/verification/028-utility-lang.json",{"zh_cn":zh,"en_us":en})
print(f"utility identities={len(entries)}, source recipes={len(recipes)}, assets={len(assets)}, loot denominator={total+1}")
