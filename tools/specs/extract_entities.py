"""Build a source-located entity declaration catalogue without executing legacy code.

Author: NingMeng123.
"""
from __future__ import annotations

import json
import re
from pathlib import Path


ATTRIBUTE_NAMES = {
    "a": "max_health", "b": "follow_range", "c": "knockback_resistance",
    "d": "movement_speed", "e": "attack_damage",
}
ATTRIBUTE_PATTERN = re.compile(
    r'(?:this\.a\(tp\.(?P<old>[abcde])\)\.a\(|'
    r'this\.setEntityAttribute\(tp\.(?P<new>[abcde]),\s*)'
    r'(?P<value>[-+]?\d+(?:\.\d+)?(?:[Ee][-+]?\d+)?)[fFdD]?\)'
)
METHOD_PATTERN = re.compile(
    r'^\s*(?:public|protected|private)\s+(?:static\s+|final\s+)*'
    r'[\w<>\[\]]+\s+(\w+)\([^;]*\)\s*\{'
)


def parse_declarations(text: str) -> dict:
    declaration = re.search(r'\bclass\s+(\w+)\s*(?:extends\s+(\w+))?', text)
    if not declaration:
        return {}
    attributes = []
    method = None
    for number, line in enumerate(text.splitlines(), 1):
        method_match = METHOD_PATTERN.match(line)
        if method_match:
            method = method_match[1]
        for match in ATTRIBUTE_PATTERN.finditer(line):
            field = match['old'] or match['new']
            attributes.append({
                "attribute": ATTRIBUTE_NAMES[field], "value": float(match['value']),
                "method": method, "decompiled_line": number,
                "condition_review": "required",
            })
    flags = []
    for match in re.finditer(
        r'(?:public|protected)\s+(?:final\s+)?boolean\s+(\w+)\(\)\s*\{\s*return\s+(true|false);\s*\}', text
    ):
        flags.append({
            "method": match[1], "returns": match[2] == "true",
            "decompiled_line": text[:match.start()].count('\n') + 1,
        })
    return {"class": declaration[1], "superclass": declaration[2],
            "attribute_assignments": attributes, "constant_boolean_methods": flags}


def extract(source_root: Path, index_path: Path) -> dict:
    index = json.loads(index_path.read_text(encoding="utf-8"))
    classes = {}
    for item in index:
        path = source_root / item['decompiled_path']
        if not path.is_file():
            continue
        parsed = parse_declarations(path.read_text(encoding="utf-8"))
        if parsed:
            parsed['source_file'] = item['source_file']
            parsed['decompiled_path'] = item['decompiled_path']
            classes[parsed['class']] = parsed

    def is_entity(name: str, seen: frozenset = frozenset()) -> bool:
        if name == 'nn':
            return True
        if name in seen or name not in classes:
            return False
        parent = classes[name]['superclass']
        return bool(parent) and is_entity(parent, seen | {name})

    entities = [value for key, value in classes.items() if is_entity(key)]
    return {
        "schema_version": 1, "author": "NingMeng123", "reference": "MITE 1.6.4 R196",
        "status": "declaration_catalogue_not_effective_runtime_stats",
        "attribute_mapping_evidence": "Vanilla 1.6.4 tp.class static initializer, verified using javap -c -p.",
        "limitations": [
            "Attribute writes can be conditional or overwritten later; these are NOT final spawn statistics.",
            "Inherited defaults, dynamic formulas, equipment and potion modifiers are not evaluated.",
            "Constant boolean methods do not prove a class is registered or naturally spawned.",
        ],
        "entity_count": len(entities), "entities": entities,
    }


def main() -> None:
    result = extract(Path('.reference/r196/decompiled'), Path('reference-data/class-index.json'))
    output = Path('spec/r196-entity-declarations.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"Indexed {result['entity_count']} entity classes with source-located declarations.")


if __name__ == '__main__':
    main()
