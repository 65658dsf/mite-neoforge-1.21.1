"""Extract numeric equipment facts from the locally decompiled R196 reference.

Author: NingMeng123. This reads reference material; it does not execute game code.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path


def extract(source_root: Path) -> dict:
    material_path = source_root / "EnumEquipmentMaterial.java"
    quality_path = source_root / "EnumQuality.java"
    block_path = source_root / "akc.java"
    material_text = material_path.read_text(encoding="utf-8")
    quality_text = quality_path.read_text(encoding="utf-8")
    block_text = block_path.read_text(encoding="utf-8")
    equipment = []
    for number, line in enumerate(material_text.splitlines(), 1):
        match = re.fullmatch(
            r'\s*(\w+)\(([\d.]+)f, (\d+), EnumQuality\.(\w+), "(\w+)"\)[,;]', line
        )
        if match:
            key, durability, enchantability, quality, name = match.groups()
            if key != name:
                raise ValueError(f"Unexpected equipment alias: {key} != {name}")
            equipment.append({
                "id": key,
                "durability_factor": float(durability),
                "enchantability": int(enchantability),
                "maximum_quality": quality,
                "evidence": {"source": material_path.name, "decompiled_line": number},
            })
    qualities = []
    for number, line in enumerate(quality_text.splitlines(), 1):
        match = re.fullmatch(r'\s*(\w+)\("\w+", "[^"]+", ([\d.]+)f\)[,;]', line)
        if match:
            qualities.append({
                "id": match[1], "durability_multiplier": float(match[2]),
                "evidence": {"source": quality_path.name, "decompiled_line": number},
            })
    for material in equipment:
        pattern = re.compile(
            r'\s*(\w+) = new akc\(EnumEquipmentMaterial\.' + re.escape(material["id"])
            + r'\).*?\.setMinHarvestLevel\((\d+)\)'
        )
        for number, line in enumerate(block_text.splitlines(), 1):
            match = pattern.match(line)
            if match:
                material["legacy_material_field"] = match[1]
                material["minimum_harvest_level_for_material"] = int(match[2])
                material["harvest_evidence"] = {"source": "akc.java", "decompiled_line": number}
    if len(equipment) != 17 or len(qualities) != 8:
        raise ValueError(f"Reference layout changed: {len(equipment)} materials, {len(qualities)} qualities")
    return {
        "schema_version": 1, "author": "NingMeng123", "reference_version": "MITE 1.6.4 R196",
        "status": "numeric_declarations_verified_behavior_integration_pending",
        "source_sha256": {
            path.name: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (material_path, quality_path, block_path)
        },
        "semantics": {
            "durability_factor": "Material multiplier, NOT the final tool durability in uses.",
            "minimum_harvest_level_for_material": "Level required to harvest the material, NOT the tool's harvest capability.",
            "maximum_quality": "Material's declared upper quality, not a guarantee every item supports quality.",
            "decompiled_line": "Line in CFR 0.152 output; not the original SourceFile line table.",
        },
        "equipment_materials": equipment, "qualities": qualities,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path(".reference/r196/decompiled"))
    parser.add_argument("--output", type=Path, default=Path("spec/r196-equipment.json"))
    args = parser.parse_args()
    result = extract(args.source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Extracted {len(result['equipment_materials'])} materials and {len(result['qualities'])} qualities.")


if __name__ == "__main__":
    main()
