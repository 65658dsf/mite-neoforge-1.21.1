"""Data-consistency regressions. Developer: NingMeng123."""
import copy
import unittest
from validate_registry import validate


def fixtures():
    stack = {"item_id":256,"count":1}
    runtime = {"game_main_invoked":False,"registration_error_message":None,"archive_sha256":"same", "items":[{"id":256,"symbols":["yc.test"]}],
               "blocks":[],"materials":[],"entities":[],"enchantments":[],"unregistered_static_items":[],
               "guard_checks_passed":["write_file","process","network","native_load","thread_create","disable_guard","unrelated_read"],
               "crafting_recipes":[{"order":0,"class":"aai","output":stack,"components":[stack],"width":1,"height":1,"difficulty":25.0,"dynamic":False}],
               "furnace_recipes":[{"input_item_id":256,"output":stack,"required_heat":1}]}
    catalog = {"metadata":{"archive_sha256":"same"},"items":[{"symbol":"yc.test","initializer":{"kind":"construct","type":"Item","arguments":[{"kind":"literal","value":0}]}}],"blocks":[],"materials":[]}
    return runtime,catalog


class RegistryValidationTest(unittest.TestCase):
    def test_matches_independent_static_constructor_id(self):
        runtime,catalog = fixtures()
        self.assertTrue(validate(runtime,catalog)["success"])
        runtime["items"][0]["id"] = 257
        self.assertIn("Constructor ID differs: yc.test",validate(runtime,catalog)["errors"])

    def test_rejects_shape_and_nonpositive_static_count(self):
        runtime,catalog = fixtures()
        runtime["crafting_recipes"][0]["width"] = 2
        runtime["crafting_recipes"][0]["components"] = [{"item_id":256,"count":0}]
        errors = validate(runtime,catalog)["errors"]
        self.assertIn("Wrong shaped dimensions 0",errors)
        self.assertIn("Static recipe has nonpositive stack 0",errors)

    def test_special_algorithmic_recipe_allows_placeholder(self):
        runtime,catalog = fixtures()
        row = runtime["crafting_recipes"][0]
        row["class"],row["dynamic"] = "aad",True
        row["components"] = [{"item_id":256,"count":0}]
        self.assertTrue(validate(runtime,catalog)["success"])
        row["dynamic"] = False
        self.assertIn("Special recipe not marked dynamic: 0",validate(runtime,catalog)["errors"])

    def test_guard_and_archive_provenance_are_required(self):
        runtime,catalog = fixtures()
        runtime["archive_sha256"] = "other"
        runtime["guard_checks_passed"].remove("write_file")
        errors = validate(runtime,catalog)["errors"]
        self.assertIn("Archive hash differs between evidence sources",errors)
        self.assertIn("Guard probes missing",errors)


if __name__ == "__main__": unittest.main()
