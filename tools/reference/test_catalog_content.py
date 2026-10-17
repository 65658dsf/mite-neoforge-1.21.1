"""Content extraction boundary tests. Author: NingMeng123."""
import unittest
from catalog_content import expression, recipe_calls, static_initializers, tokenize


class ContentCatalogTest(unittest.TestCase):
    def parse(self, text): return expression(tokenize(text))

    def test_literals_are_evaluated_but_arithmetic_is_explicit(self):
        self.assertEqual(self.parse("-2.5f"), {"kind": "literal", "value": -2.5})
        self.assertEqual(self.parse("0xFFL"), {"kind": "literal", "value": 255})
        self.assertEqual(self.parse("null"), {"kind": "literal", "value": None})
        self.assertEqual(self.parse("j * 4")["kind"], "expression")
        self.assertEqual(self.parse("Skill.FARMING.id")["evaluation"], "pending")

    def test_nested_arrays_preserve_recipe_spaces_and_separators(self):
        result = self.parse('new Object[][]{{yc.a, 3}, {new String[]{" ? ", "?,;"}, Character.valueOf(\',\')}}')
        self.assertEqual(result["kind"], "array")
        self.assertEqual(len(result["elements"]), 2)
        matrix = result["elements"][1]["elements"][0]
        self.assertEqual([r["value"] for r in matrix["elements"]], [" ? ", "?,;"])

    def test_constructor_cast_and_fluent_values(self):
        result = self.parse('(ItemMeat)new ItemMeat(63, 4, false, "VANILLA").b("pork").setFoodValue(2, 4)')
        self.assertEqual(result["kind"], "cast")
        self.assertEqual(result["value"]["type"], "ItemMeat")
        self.assertEqual(result["value"]["arguments"][0]["value"], 63)
        self.assertEqual(result["value"]["calls"][1]["arguments"][1]["value"], 4)

    def test_static_initializers_exclude_method_local_assignments(self):
        text = 'class Demo { static Item a = new Item(1); static Item b; static { b = new Item(2); for (int a = 0; a < 10; ++a) {} } void f() { a = new Item(3); } }'
        rows = static_initializers(tokenize(text), {"a", "b"})
        self.assertEqual([r["field"] for r in rows], ["a", "b"])
        self.assertEqual([r["initializer"]["arguments"][0]["value"] for r in rows], [1, 2])

    def test_recipe_call_sites_preserve_parameters_and_do_not_expand_loops(self):
        info = {"name": "R", "source_file": "Recipes.java", "sha256": "abc"}
        text = 'for (int i=0;i<9;++i) { manager.addShapelessRecipe(new ye(yc.x, i), yc.y).setDifficulty(25); } manager.a(world); manager.a(new ye(yc.z), "##", Character.valueOf(\'#\'), yc.x);'
        calls = recipe_calls(tokenize(text), info)
        self.assertEqual(len(calls), 2)
        args = calls[0]["call"]["arguments"][0]["arguments"]
        self.assertEqual(args[1], {"kind": "symbol", "reference": "i", "evaluation": "pending"})
        self.assertEqual(calls[0]["call"]["calls"][0]["arguments"][0]["value"], 25)


if __name__ == "__main__": unittest.main()
