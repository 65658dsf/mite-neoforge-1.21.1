"""Reference extraction regressions. Author: NingMeng123."""
import unittest
from pathlib import Path

from extract_entities import parse_declarations
from extract_equipment import extract


class EntityParsingTests(unittest.TestCase):
    def test_follow_range_is_not_health(self):
        data = parse_declarations('''public class Ghoul extends Mob {
            protected void az() {
                this.a(tp.b).a(40.0);
                this.setEntityAttribute(tp.a, 6.0);
            }
        }''')
        self.assertEqual([('follow_range', 40.0), ('max_health', 6.0)],
                         [(v['attribute'], v['value']) for v in data['attribute_assignments']])

    def test_dynamic_values_are_not_fabricated(self):
        data = parse_declarations('''public class Mob extends Entity {
            protected void az() { this.a(tp.a).a(20.0 * multiplier); }
            public boolean isSafe() { return condition; }
            public boolean isHarmedByFire() { return false; }
        }''')
        self.assertEqual([], data['attribute_assignments'])
        self.assertEqual([False], [v['returns'] for v in data['constant_boolean_methods']])


@unittest.skipUnless(Path('.reference/r196/decompiled/EnumEquipmentMaterial.java').is_file(),
                     'Local user-supplied R196 reference is not installed')
class R196FixtureTests(unittest.TestCase):
    def test_reference_equipment_facts(self):
        result = extract(Path('.reference/r196/decompiled'))
        materials = {m['id']: m for m in result['equipment_materials']}
        self.assertEqual(17, len(materials))
        self.assertEqual(256.0, materials['adamantium']['durability_factor'])
        self.assertEqual(5, materials['adamantium']['minimum_harvest_level_for_material'])
        self.assertEqual(100, materials['mithril']['enchantability'])
        self.assertEqual('poor', materials['rusted_iron']['maximum_quality'])

    def test_reference_wood_spider_facts(self):
        text = Path('.reference/r196/decompiled/EntityWoodSpider.java').read_text(encoding='utf-8')
        result = parse_declarations(text)
        self.assertEqual('EntityArachnid', result['superclass'])
        self.assertIn(('max_health', 6.0), [(a['attribute'], a['value']) for a in result['attribute_assignments']])
        self.assertIn(('peacefulDuringDay', False), [(a['method'], a['returns']) for a in result['constant_boolean_methods']])


if __name__ == '__main__':
    unittest.main()
