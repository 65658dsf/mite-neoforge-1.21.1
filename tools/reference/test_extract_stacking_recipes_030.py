"""Regression checks for native batch adaptation extraction. Author: NingMeng123."""
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from extract_stacking_recipes_030 import COMPAT_TYPES, DEFAULT_ARCHIVE, ROOT, existing_overrides, extract


class NativeRecipeBatchExtractionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads((ROOT / "spec/stacking-recipes-030.json").read_text(encoding="utf-8"))
        cls.rows = {x["id"]: x for x in cls.data["entries"]}

    def test_pinned_native_recipe_inventory_and_known_batches(self):
        self.assertEqual(1290, self.data["summary"]["native_recipe_count"])
        self.assertEqual(71, self.data["summary"]["native_over_limit_results"])
        self.assertEqual(66, len(self.rows))
        self.assertFalse(self.data["unsupported"])
        for name, count, limit in (("oak_door", 3, 1), ("red_concrete_powder", 8, 4),
                                   ("blue_stained_glass", 8, 4), ("green_terracotta", 8, 4),
                                   ("rail", 16, 8), ("netherite_ingot_from_netherite_block", 9, 8)):
            row = self.rows["minecraft:" + name]
            self.assertEqual(count, row["output_count"])
            self.assertEqual(limit, row["maximum_stack"])
            self.assertEqual(count, row["original_json"]["result"]["count"])

    def test_source_specific_overrides_have_priority(self):
        ids = {x["id"] for x in self.data["existing_overrides"]}
        self.assertEqual({"minecraft:copper_door", "minecraft:copper_ingot",
                          "minecraft:copper_ingot_from_waxed_copper_block",
                          "minecraft:gold_ingot_from_gold_block", "minecraft:iron_ingot_from_iron_block"}, ids)
        self.assertTrue(ids.isdisjoint(self.rows))

    def test_original_json_is_exact_pinned_source_semantics(self):
        with zipfile.ZipFile(DEFAULT_ARCHIVE) as archive:
            for row in self.rows.values():
                self.assertEqual(json.loads(archive.read(row["source_entry"])), row["original_json"])
                self.assertIn(row["adapted_type"], COMPAT_TYPES)

    def test_own_generated_recipes_do_not_suppress_next_extraction(self):
        with tempfile.TemporaryDirectory(prefix="mite-stacking-030-") as directory:
            root = Path(directory)
            for row in self.rows.values():
                path = root / row["source_entry"]
                path.parent.mkdir(parents=True, exist_ok=True)
                adapted = dict(row["original_json"], type=row["adapted_type"])
                path.write_text(json.dumps(adapted), encoding="utf-8")
                self.assertFalse(existing_overrides(row["source_entry"], (root,)))
            replay = extract(DEFAULT_ARCHIVE, ROOT / "spec/stacking-map-030.json",
                             (ROOT / "src/main/resources", ROOT / "src/generated/resources", root))
            self.assertEqual(self.data, replay)

    def test_other_mod_override_is_preserved_even_when_compatibility_file_exists(self):
        with tempfile.TemporaryDirectory(prefix="mite-stacking-030-") as directory:
            root = Path(directory)
            source_path = "data/minecraft/recipe/oak_door.json"
            path = root / source_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps({"type": "mite:quality_shaped", "conditions": [{"type": "neoforge:false"}]}), encoding="utf-8")
            rows = existing_overrides(source_path, (root,))
            self.assertEqual(1, len(rows))
            self.assertEqual("mite:quality_shaped", rows[0]["type"])


if __name__ == "__main__":
    unittest.main()
