"""Extract this batch's independent source oracle and unchanged artwork. Author: NingMeng123."""
import json
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = json.loads((ROOT / "reference-data/runtime-registry.json").read_text(encoding="utf-8-sig"))
ANVILS = {229: "copper", 230: "silver", 231: "gold", 145: "iron", 254: "ancient_metal", 232: "mithril", 233: "adamantium"}
BOXES = {239: "copper", 240: "silver", 241: "gold", 242: "iron", 255: "ancient_metal", 243: "mithril", 244: "adamantium"}
MATERIAL_IDS = {266: "minecraft:gold_ingot", 265: "minecraft:iron_ingot", 962: "minecraft:copper_ingot", 963: "mite:silver_ingot", 964: "mite:mithril_ingot", 965: "mite:adamantium_ingot", 966: "mite:ancient_metal_ingot", 204: "minecraft:copper_block", 205: "mite:silver_block", 206: "mite:mithril_block", 207: "mite:adamantium_block", 252: "mite:ancient_metal_block", 41: "minecraft:gold_block", 42: "minecraft:iron_block", 340: "minecraft:book", 388: "minecraft:emerald", 49: "minecraft:obsidian"}

def modern(item_id):
    if item_id in ANVILS:
        metal = ANVILS[item_id]
        return "minecraft:anvil" if metal == "iron" else f"mite:{'golden' if metal == 'gold' else metal}_anvil"
    if item_id in BOXES:
        metal = BOXES[item_id]
        return f"mite:{'golden' if metal == 'gold' else metal}_strongbox"
    if item_id == 245:
        return "mite:emerald_enchanting_table"
    return MATERIAL_IDS[item_id]

selected = []
timed = {}
recipes = []
for row in SOURCE["crafting_recipes"]:
    if not row.get("output") or row["output"]["item_id"] not in ANVILS.keys() | BOXES.keys() | {245}:
        continue
    selected.append(row)
    output = modern(row["output"]["item_id"])
    keys, pattern = {}, []
    for component in row["components"]:
        if component is not None and component["item_id"] not in keys:
            keys[component["item_id"]] = chr(65 + len(keys))
    for start in range(0, len(row["components"]), row["width"]):
        pattern.append("".join(keys[c["item_id"]] if c else " " for c in row["components"][start:start + row["width"]]))
    recipes.append({"id": output, "pattern": pattern, "key": {letter: {"item": modern(item_id)} for item_id, letter in keys.items()}, "result": {"id": output, "count": row["output"]["count"]}})
    timed[output] = {"difficulty": row["difficulty"], "required_bench": (row["bench_material"] or {}).get("name"), "source_order": row["order"]}

spec = {"author": "NingMeng123", "archive_sha256": SOURCE["archive_sha256"], "source": "reference-data/runtime-registry.json plus amv,va,vc,BlockStrongbox,TileEntityStrongbox,ary,vm,aaw", "activation": SOURCE["activation_flags"], "recipes": selected,
        "blocks": [row for row in SOURCE["blocks"] if row["id"] in ANVILS.keys() | BOXES.keys() | {245}],
        "rule_facts": {"anvil_base_per_ingot": 1600, "anvil_ingots": 31, "anvil_stage_thresholds": [.5, .8, 1], "repair_cost_tool": "components*2", "repair_cost_chain": "nonchain components*2, restored max*2/cost", "repair_cost_fishing_rod": 1, "repair_wear_tool": 1, "repair_wear_armor": 200, "repair_wear_metal_bow": 200, "repair_wear_fishing_rod": 22, "repair_xp": 0, "combine_wear": 0, "strongbox_slots": 27, "strongbox_double": False, "strongbox_stack": 4, "strongbox_open": "owner or creative", "strongbox_self_drop": "noncreative owner only", "strongbox_contents_drop": "ordinary chest on removal", "emerald_energy_per_shelf_plus_base": 2, "diamond_energy_per_shelf_plus_base": 4, "bookshelf_cap": 24, "enchantability_increment_bands": [1, .5, .25, 0]},
        "modern_adaptations": ["owner UUID replaces mutable source player name; display name retained", "modern native enchanting table menu, lapis and payment retained; emerald energy uses source formula", "native diamond table retains 1.21.1 enchanting formula", "anvil rename and enchanted-book operations retain modern compatibility costs; source material/combine repairs are zero-XP", "source skills disabled by default", "native iron damaged IDs retained; added anvil wear uses damage_stage property"],
        "validation": {"client_manual": "pending_user_test", "automatic": "pending"}}
for path, obj in [("spec/functional-blocks-027.json", spec), ("src/main/resources/mite/functional-blocks-027.json", {"author": "NingMeng123", "recipes": recipes}), ("src/main/resources/mite/timed-crafting-027-functional.json", {"author": "NingMeng123", "source": "source runtime recipes", "recipes": timed})]:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
with zipfile.ZipFile(ROOT / "MITE Resource Pack 1.6.4.zip") as archive:
    for name in archive.namelist():
        if not name.endswith(".png"):
            continue
        if name.startswith("assets/minecraft/textures/blocks/anvil/"):
            destination = "src/main/resources/assets/mite/textures/block/anvil/" + name.split("blocks/anvil/")[1]
        elif name.startswith("assets/minecraft/textures/entity/chest/"):
            destination = "src/main/resources/assets/mite/textures/entity/chest/" + name.rsplit("/", 1)[1]
        elif "emerald_enchanting_table_" in name:
            destination = "src/main/resources/assets/mite/textures/block/" + name.rsplit("/", 1)[1]
        else:
            continue
        target = ROOT / destination
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(archive.read(name))
print(f"Extracted {len(selected)} source recipes and functional artwork")
