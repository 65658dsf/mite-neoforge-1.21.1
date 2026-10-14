"""Extract wp and ItemGelatinousSphere source identities and byte-copy their sprites. Developer: NingMeng123."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def main():
    runtime = json.loads((ROOT / "reference-data/runtime-registry.json").read_text(encoding="utf-8"))
    bows = []
    timed, quality = {}, {}
    for old, material in [(1251, "ancient_metal"), (1048, "mithril")]:
        item = next(x for x in runtime["items"] if x["id"] == old)
        recipe = next(x for x in runtime["crafting_recipes"] if (x.get("output") or {}).get("item_id") == old)
        mat = next(x for x in runtime["materials"] if x["scalars"]["akc.name"] == material)["scalars"]
        row = {"id": f"mite:{material}_bow", "legacy_id": old, "material": material,
               "durability": item["scalars"]["yc.b"], "maximum_quality": mat["akc.max_quality"],
               "enchantability": mat["akc.enchantability"], "velocity_multiplier": 1.25 if material == "mithril" else 1.1,
               "difficulty": recipe["difficulty"], "order": recipe["order"], "pattern": [" #X", "#IX", " #X"],
               "ingredients": {"#": "minecraft:stick", "X": "minecraft:string", "I": f"mite:{material}_ingot"},
               "source": item["source"]}
        bows.append(row)
        timed[row["id"]] = {"difficulty": row["difficulty"], "required_bench": material, "source_order": row["order"]}
        quality[row["id"]] = {"lowest_difficulty": row["difficulty"]}
    spheres = [{"id": modern, "legacy_id": 341, "metadata": index, "source_color": color,
                "damage": 1 + (index + 1 if index < 3 else index), "contact": "acid" if index >= 3 else "pepsin"}
               for index, color, modern in [(0, "green", "minecraft:slime_ball"), (1, "ochre", "mite:ochre_jelly"),
                                           (2, "crimson", "mite:crimson_blob"), (3, "gray", "mite:grey_gelatinous_sphere"),
                                           (4, "black", "mite:black_pudding")]]
    textures = []
    with zipfile.ZipFile(ROOT / "MITE Resource Pack 1.6.4.zip") as pack:
        for material in ["ancient_metal", "mithril"]:
            prefix = f"assets/minecraft/textures/items/bows/{material}/"
            for source in pack.namelist():
                if source.startswith(prefix) and source.endswith(".png"):
                    target = f"assets/mite/textures/item/bows/{material}/{source.rsplit('/', 1)[1]}"
                    raw = pack.read(source)
                    path = ROOT / "src/main/resources" / target
                    path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(raw)
                    textures.append({"source": source, "target": target, "sha256": hashlib.sha256(raw).hexdigest()})
        for sphere in spheres:
            source = f"assets/minecraft/textures/items/gelatinous_sphere/{sphere['source_color']}.png"
            target = f"assets/mite/textures/item/gelatinous_sphere/{sphere['source_color']}.png"
            raw = pack.read(source); path = ROOT / "src/main/resources" / target
            path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(raw)
            textures.append({"source": source, "target": target, "sha256": hashlib.sha256(raw).hexdigest()})
    text = (ROOT / ".reference/runtime-helper/corrosion-028.log").read_text(encoding="utf-8")
    corrosion = json.loads(text.split("SPHERE_CORROSION_BEGIN\n")[1].split("\nSPHERE_CORROSION_END")[0])
    write(ROOT / "spec/sphere-corrosion-028.json", corrosion)
    write(ROOT / "src/main/resources/mite/sphere-corrosion-028.json", corrosion)
    dataset = {"author": "NingMeng123", "archive_sha256": runtime["archive_sha256"], "bows": bows, "spheres": spheres,
               "textures": textures, "source_behavior": {"bow": "wp.java; uh.java:145-162; yc.java:1237-1254; aam.java:12-13",
                   "sphere": "ItemGelatinousSphere.java; EntityGelatinousSphere.java; uq.java:27-91; aqz.java:2139-2234",
                   "sphere_gravity": 0.07, "sphere_launch_speed": 1.2, "sphere_exhaustion": 0.25,
                   "source_skills_enabled": False, "source_quickness_registered_modern": False,
                   "corrosion_snapshot": "spec/sphere-corrosion-028.json"}}
    write(ROOT / "spec/weapons-028.json", dataset)
    write(ROOT / "src/main/resources/mite/weapons-028.json", {"bows": bows, "spheres": spheres})
    write(ROOT / "src/main/resources/mite/timed-crafting-028-weapons.json", {"author": "NingMeng123", "recipes": timed})
    write(ROOT / "src/main/resources/mite/quality-equipment-028-weapons.json", {"author": "NingMeng123", "items": quality})
    write(ROOT / "build/verification/028-weapons-lang.json", {
        "zh_cn": {"item.mite.ancient_metal_bow": "远古金属弓", "item.mite.mithril_bow": "秘银弓",
                  "item.mite.ochre_jelly": "褐色粘液球", "item.mite.crimson_blob": "红色史莱姆球",
                  "item.mite.grey_gelatinous_sphere": "灰色史莱姆球", "item.mite.black_pudding": "黑色史莱姆球",
                  "item.mite.bow.velocity": "箭矢速度：+%s%%", "item.mite.sphere.damage": "投掷伤害：%s"},
        "en_us": {"item.mite.ancient_metal_bow": "Ancient Metal Bow", "item.mite.mithril_bow": "Mithril Bow",
                  "item.mite.ochre_jelly": "Ochre Jelly", "item.mite.crimson_blob": "Crimson Blob",
                  "item.mite.grey_gelatinous_sphere": "Gray Ooze", "item.mite.black_pudding": "Black Pudding",
                  "item.mite.bow.velocity": "Arrow velocity: +%s%%", "item.mite.sphere.damage": "Missile damage: %s"}})
    print(f"2 bows, 5 sphere profiles, {len(textures)} original sprites")

if __name__ == "__main__": main()
