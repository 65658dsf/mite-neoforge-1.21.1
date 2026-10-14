"""Preserve native crafting batches after R196 stack reductions. Author: NingMeng123.

This adapter changes only the recipe serializer. Ingredients, shape, metadata,
conditions and total result quantity remain the pinned Minecraft 1.21.1 data.
Existing source-specific MITE overrides always take priority. The adapter's own
generated files do not suppress extraction on subsequent runs.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADAPTED_TYPES = {"minecraft:crafting_shaped": "mite:stacking_shaped", "minecraft:crafting_shapeless": "mite:stacking_shapeless"}
COMPAT_TYPES = set(ADAPTED_TYPES.values())
DEFAULT_ARCHIVE = ROOT / "build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar"
LOCAL_RESOURCES = (ROOT / "src/main/resources", ROOT / "src/generated/resources")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def existing_overrides(source_path, resource_roots=LOCAL_RESOURCES):
    overrides = []
    for root in resource_roots:
        candidate = root / source_path
        if not candidate.is_file():
            continue
        raw = candidate.read_bytes()
        local = json.loads(raw)
        if local.get("type") in COMPAT_TYPES:
            # Do not let this provider's generated output change the next input.
            continue
        try:
            display_path = candidate.relative_to(ROOT).as_posix()
        except ValueError:
            display_path = candidate.as_posix()
        overrides.append({"path": display_path, "type": local.get("type"), "sha256": digest(raw)})
    return overrides


def extract(archive_path, map_path, resource_roots=LOCAL_RESOURCES):
    limits_data = json.loads(map_path.read_text(encoding="utf-8"))
    limits = {x["modern_id"]: x["max_stack_size"] for x in limits_data["mappedItems"]}
    entries, existing, unsupported = [], [], []
    recipe_count = 0
    with zipfile.ZipFile(archive_path) as archive:
        names = sorted(name for name in archive.namelist() if name.startswith("data/minecraft/recipe/") and name.endswith(".json"))
        for source_path in names:
            recipe_count += 1
            raw = archive.read(source_path)
            original = json.loads(raw)
            result = original.get("result")
            if not isinstance(result, dict):
                continue
            item = result.get("id")
            count = result.get("count", 1)
            maximum = limits.get(item)
            if maximum is None or count <= maximum:
                continue
            recipe_id = "minecraft:" + source_path.removeprefix("data/minecraft/recipe/").removesuffix(".json")
            record = {"id": recipe_id, "source_entry": source_path, "source_entry_sha256": digest(raw),
                      "original_type": original["type"], "output_item": item, "output_count": count, "maximum_stack": maximum}
            overrides = existing_overrides(source_path, resource_roots)
            if overrides:
                existing.append({**record, "disposition": "existing_mite_override_preserved", "overrides": overrides})
                continue
            target_type = ADAPTED_TYPES.get(original["type"])
            if target_type is None:
                unsupported.append({**record, "disposition": "unsupported_native_recipe_type"})
                continue
            entries.append({**record, "adapted_type": target_type, "original_json": original})
    return {
        "schema_version": 1, "author": "NingMeng123", "minecraft": "1.21.1", "neoforge": "21.1.259",
        "purpose": "现代配方单次产物可以跨越多堆；只更换宽松结果编解码器，保留原配方、产量与解锁 ID。",
        "source_artifact": archive_path.relative_to(ROOT).as_posix() if archive_path.is_relative_to(ROOT) else archive_path.as_posix(),
        "source_artifact_sha256": digest(archive_path.read_bytes()), "stacking_map": map_path.relative_to(ROOT).as_posix(),
        "stacking_map_sha256": digest(map_path.read_bytes()),
        "rerun_semantics": "已生成 mite:stacking_shaped/mite:stacking_shapeless 不视作其他覆盖；重跑不减少本批条目。其他覆盖优先保留。",
        "summary": {"native_recipe_count": recipe_count, "native_over_limit_results": len(entries) + len(existing) + len(unsupported),
                    "adapted_recipe_count": len(entries), "adapted_type_counts": dict(collections.Counter(row["adapted_type"] for row in entries)),
                    "existing_override_count": len(existing), "unsupported_recipe_count": len(unsupported)},
        "entries": entries, "existing_overrides": existing, "unsupported": unsupported,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    arguments = parser.parse_args()
    document = extract(arguments.archive.resolve(), ROOT / "spec/stacking-map-030.json")
    output = ROOT / "spec/stacking-recipes-030.json"
    output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(document["summary"], ensure_ascii=False))
    if document["unsupported"]:
        raise SystemExit("Unsupported over-limit native recipe types require explicit handling")


if __name__ == "__main__":
    main()
