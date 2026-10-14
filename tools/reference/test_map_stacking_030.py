"""Independent identity and value checks for stacking mapping. Author: NingMeng123."""
import collections
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(name):
    return json.loads((ROOT / name).read_text(encoding="utf-8"))


class StackingMappingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.map = read("spec/stacking-map-030.json")
        cls.rows = {x["modern_id"]: x for x in cls.map["mappedItems"]}
        cls.source = {x["legacy_id"]: x for x in read("spec/stacking-source-030.json")["items"]}

    def test_source_inventory_is_partitioned_without_duplicate_or_gap(self):
        rows = self.map["source_dispositions"]
        self.assertEqual(667, len(rows))
        self.assertEqual(set(self.source), {x["legacy_id"] for x in rows})
        self.assertFalse(self.map["gaps"])
        self.assertFalse(self.map["conflicts"])

    def test_limits_are_from_dynamic_oracle_and_never_name_heuristics(self):
        for row in self.rows.values():
            for ref in row["source_items"] + row.get("dynamic_source_items", []):
                source = self.source[ref["legacy_id"]]
                limits = {x["subtype"]: x["max_stack_size"] for x in source["subtype_limits"]}
                limits.update({x["subtype"]: x["max_stack_size"] for x in source.get("dynamic_subtype_limits", [])})
                self.assertEqual(limits.get(ref["subtype"], source["base_limit"]), ref["max_stack_size"], row["modern_id"])

    def test_literal_representative_limits(self):
        expected = {
            "minecraft:stone": 4, "minecraft:oak_log": 4, "minecraft:oak_planks": 8,
            "minecraft:oak_slab": 8, "minecraft:oak_sapling": 16, "minecraft:snow": 32,
            "minecraft:oak_sign": 16, "minecraft:oak_door": 1, "minecraft:red_bed": 1,
            "minecraft:cake": 8, "minecraft:flower_pot": 16, "minecraft:brewing_stand": 16,
            "minecraft:cauldron": 4, "minecraft:redstone": 16, "minecraft:iron_ingot": 8,
            "minecraft:diamond": 32, "minecraft:wheat_seeds": 64, "minecraft:bucket": 8,
            "minecraft:water_bucket": 1, "minecraft:glass_bottle": 8,
            "minecraft:gold_nugget": 64, "minecraft:paper": 64, "minecraft:book": 16,
            "minecraft:arrow": 16, "minecraft:potion": 1, "minecraft:splash_potion": 1,
            "mite:mithril_runestone": 4, "mite:adamantium_coin": 64,
            "mite:deepslate_mithril_ore": 4, "mite:carrot_on_a_stick_flint_hook": 1,
        }
        for item, limit in expected.items():
            self.assertEqual(limit, self.rows[item]["max_stack_size"], item)
        self.assertEqual([32, 8], [x["max_stack_size"] for x in self.rows["minecraft:snow"]["state_rules"]])

    def test_old_world_items_do_not_override_carried_items(self):
        excluded = {x["legacy_id"] for x in self.map["exclusions"]}
        self.assertTrue({26, 55, 64, 92, 117, 118, 140, 208, 253}.issubset(excluded))
        self.assertEqual(324, self.rows["minecraft:oak_door"]["source_items"][0]["legacy_id"])
        self.assertEqual(390, self.rows["minecraft:flower_pot"]["source_items"][0]["legacy_id"])

    def test_all_native_registry_and_accepted_carried_catalog_identities_are_covered(self):
        native = {x["id"] for x in read("spec/stacking-modern-items-030.json")["items"]}
        retained = {x["modern_id"] for x in self.map["modern_retained_defaults"]}
        self.assertEqual(1333, len(native))
        self.assertEqual(native, {x for x in self.rows if x.startswith("minecraft:")} | retained)
        for row in read("src/gametest/resources/mite/complete-catalog-028.json")["entries"]:
            if not row["block_only"]:
                self.assertIn(row["modern_id"], self.rows, row["name"])

    def test_source_specific_variants_have_truthful_identity_and_disposition(self):
        self.assertIn({"legacy_id": 24, "subtype": 3, "disposition": "source_sacred_sandstone_no_current_identity"}, self.map["subtype_exclusions"])
        self.assertEqual(4, self.rows["mite:copperspine_stone"]["source_items"][0]["subtype"])
        for row in read("spec/mobs-source-031.json")["entities"]:
            if (row.get("modern_id") or "").startswith("mite:"):
                self.assertEqual(16, self.rows[row["modern_id"] + "_spawn_egg"]["max_stack_size"])
        self.assertEqual(3, self.rows["mite:infested_netherrack"]["source_items"][0]["subtype"])
        self.assertEqual(8, self.rows["mite:ancient_metal_crafting_table"]["source_items"][0]["subtype"])
        self.assertEqual({11, 12, 13, 14}, {x["subtype"] for x in self.rows["mite:obsidian_crafting_table"]["source_items"]})


if __name__ == "__main__":
    unittest.main()
