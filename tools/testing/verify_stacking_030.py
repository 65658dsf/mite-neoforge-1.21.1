"""Audit R196 stacking identities, modern batches and packaged code. Author: NingMeng123."""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import re
import subprocess
import sys
import time
import tomllib
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
NEW_MAIN_CLASSES = (
    "rules/StackingRules", "registry/MiteItemStacking", "registry/StackingRecipeSerializers",
    "crafting/StackBatchTransfers", "crafting/StackBatchShapedRecipe", "crafting/StackBatchShapelessRecipe",
    "crafting/LegacyStackTransfers", "crafting/MerchantBatchTransfers",
    "data/StackingRecipeAssetsProvider", "mixin/SourceStackLimitMixin", "mixin/SlotStackSafetyMixin",
    "mixin/HopperStackTransferMixin", "mixin/StackBatchPickupMixin",
    "mixin/LegacyDispenserStackMixin", "mixin/LegacyDropperStackMixin", "mixin/LegacyCapabilityStackMixin",
    "mixin/LegacySwapStackMixin", "mixin/MerchantBatchContainerMixin", "mixin/MerchantBatchClickMixin",
    "mixin/MerchantBatchQuickMoveMixin",
)
NEW_MIXINS = {
    "SourceStackLimitMixin", "SlotStackSafetyMixin", "HopperStackTransferMixin", "StackBatchPickupMixin",
    "LegacyDispenserStackMixin", "LegacyDropperStackMixin", "LegacyCapabilityStackMixin", "LegacySwapStackMixin",
    "MerchantBatchContainerMixin", "MerchantBatchClickMixin", "MerchantBatchQuickMoveMixin",
}
PYTHON_TESTS = (
    ("tools/reference/test_map_stacking_030.py", 6),
    ("tools/reference/test_extract_stacking_recipes_030.py", 5),
)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=ROOT / "build/libs/mite-0.1.0.jar")
    parser.add_argument("--classes", type=Path, default=ROOT / "build/classes/java/main")
    parser.add_argument("--report", type=Path, default=ROOT / "build/verification/030-stacking-audit.json")
    args = parser.parse_args()
    failures = []
    counts = collections.Counter()
    checksums = {}

    def check(condition, message):
        if not condition:
            failures.append(message)

    def file_bytes(path):
        target = ROOT / path
        if not target.is_file():
            failures.append("Missing audit input: " + path)
            return None
        return target.read_bytes()

    mapping = read("spec/stacking-map-030.json")
    recipes = read("spec/stacking-recipes-030.json")
    source = read("spec/stacking-source-030.json")
    rows = mapping["mappedItems"]
    identities = {row["modern_id"] for row in rows}
    check(len(rows) == len(identities) == 1661, "Expected 1661 distinct carried item limits")
    check(all(row["modern_id"].startswith(("minecraft:", "mite:")) and 1 <= row["max_stack_size"] <= 64 for row in rows),
          "Invalid namespace or limit in source stacking table")
    check(not mapping["gaps"] and not mapping["conflicts"], "Unconfirmed identity gaps or conflicting limits in source table")
    counts["mapped_modern_items_checked"] = len(rows)
    counts["source_identity_mappings"] = sum(row["mapping_kind"] == "source" for row in rows)
    counts["modern_adaptations"] = sum(row["mapping_kind"] == "modern_adaptation" for row in rows)
    counts["native_mapped_items"] = sum(row["modern_id"].startswith("minecraft:") for row in rows)
    counts["mite_mapped_items"] = sum(row["modern_id"].startswith("mite:") for row in rows)
    counts["modern_retained_defaults"] = len(mapping["modern_retained_defaults"])
    dispositions = mapping["source_dispositions"]
    source_ids = {row["legacy_id"] for row in source["items"]}
    check(len(dispositions) == len({row["legacy_id"] for row in dispositions}) == len(source_ids) == 667,
          "Source item dispositions must partition all 667 registered items")
    check({row["legacy_id"] for row in dispositions} == source_ids, "Source disposition IDs differ from the guarded oracle")
    check(not any(row["disposition"] == "identity_not_yet_confirmed" for row in dispositions), "Unresolved source identity disposition")
    counts["source_item_dispositions_checked"] = len(dispositions)
    counts["source_items_mapped"] = sum(row["disposition"] == "mapped" for row in dispositions)
    counts["source_items_excluded"] = len(mapping["exclusions"])
    counts["source_subtype_exclusions"] = len(mapping["subtype_exclusions"])
    counts["source_blocks_recorded"] = len(source["blocks"])
    counts["source_creative_subtype_vectors"] = sum(len(row["subtype_limits"]) for row in source["items"])
    source_by_id = {row["legacy_id"]: row for row in source["items"]}
    for row in rows:
        for ref in row["source_items"] + row.get("dynamic_source_items", []):
            counts["source_limit_references_checked"] += 1
            oracle = source_by_id.get(ref["legacy_id"])
            if oracle is None:
                failures.append("Missing source item reference: " + str(ref))
                continue
            limits = {value["subtype"]: value["max_stack_size"] for value in oracle["subtype_limits"]}
            limits.update({value["subtype"]: value["max_stack_size"] for value in oracle.get("dynamic_subtype_limits", [])})
            check(ref["max_stack_size"] == limits.get(ref["subtype"], oracle["base_limit"]),
                  "Source limit reference differs from dynamic oracle: " + row["modern_id"])
    snow = next(row for row in rows if row["modern_id"] == "minecraft:snow")
    check([row["max_stack_size"] for row in snow["state_rules"]] == [32, 8], "Source snow must retain single-layer32 / deeper8")
    native = {row["id"] for row in read("spec/stacking-modern-items-030.json")["items"]}
    retained = {row["modern_id"] for row in mapping["modern_retained_defaults"]}
    check(len(native) == 1333 and native == {item for item in identities if item.startswith("minecraft:")} | retained,
          "Native 1.21.1 registry must be fully classified")

    recipe_rows = recipes["entries"]
    recipe_ids = {row["id"] for row in recipe_rows}
    check(len(recipe_rows) == len(recipe_ids) == 66, "Expected 66 distinct native batch adaptations")
    check(not recipes["unsupported"], "Unsupported over-limit native recipes remain")
    check(len(recipes["existing_overrides"]) == 5, "Expected five preserved source-specific recipe overrides")
    check(recipe_ids.isdisjoint(row["id"] for row in recipes["existing_overrides"]), "Native batch adapter overwrites a source recipe")
    check(sha((ROOT / "spec/stacking-map-030.json").read_bytes()) == recipes["stacking_map_sha256"],
          "Native batch extraction references stale stacking map bytes")
    counts["native_recipes_scanned"] = recipes["summary"]["native_recipe_count"]
    counts["native_over_limit_results"] = recipes["summary"]["native_over_limit_results"]
    counts["adapted_batch_recipes_checked"] = len(recipe_rows)
    counts["source_recipe_overrides_preserved"] = len(recipes["existing_overrides"])

    jar_bytes = args.jar.read_bytes()
    compiled = {path.relative_to(args.classes).as_posix(): path for path in args.classes.rglob("*.class")}
    check(bool(compiled), "No current production compilation available for bytecode audit")
    with ZipFile(args.jar) as mod, ZipFile(ROOT / recipes["source_artifact"]) as base:
        names_list = mod.namelist()
        names = set(names_list)
        check(len(names) == len(names_list), "Duplicate paths in delivered JAR")
        check(sha((ROOT / recipes["source_artifact"]).read_bytes()) == recipes["source_artifact_sha256"],
              "Pinned Minecraft recipe artifact SHA-256 differs from extraction source")
        for spec, local, packaged in (
            ("spec/stacking-map-030.json", "src/main/resources/mite/stacking-030.json", "mite/stacking-030.json"),
            ("spec/stacking-recipes-030.json", "src/main/resources/mite/stacking-recipes-030.json", "mite/stacking-recipes-030.json"),
        ):
            expected = file_bytes(spec)
            runtime = file_bytes(local)
            check(expected == runtime, "Specification/runtime snapshot differs: " + spec)
            check(packaged in names and mod.read(packaged) == expected, "Packaged snapshot differs: " + packaged)
            counts["snapshot_byte_identity_checks"] += 2
            checksums[spec] = sha(expected) if expected is not None else None
        for row in recipe_rows:
            raw = base.read(row["source_entry"])
            check(sha(raw) == row["source_entry_sha256"], "Original native recipe SHA mismatch: " + row["id"])
            original = json.loads(raw)
            check(original == row["original_json"], "Recorded original native recipe changed: " + row["id"])
            expected = {**original, "type": row["adapted_type"]}
            check(expected["type"] in {"mite:stacking_shaped", "mite:stacking_shapeless"}, "Invalid adapter serializer: " + row["id"])
            check(original["result"]["count"] == row["output_count"] > row["maximum_stack"],
                  "Batch quantity no longer preserves original over-limit result: " + row["id"])
            path = row["source_entry"]
            generated = file_bytes("src/generated/resources/" + path)
            check(generated is not None and json.loads(generated) == expected, "Generated batch changes fields beyond type: " + row["id"])
            check(path in names and json.loads(mod.read(path)) == expected, "Packaged batch missing/changes original fields: " + row["id"])
            counts["packaged_adapted_batch_recipes_checked"] += 1
        for row in recipes["existing_overrides"]:
            for override in row["overrides"]:
                local = file_bytes(override["path"])
                check(local is not None and sha(local) == override["sha256"], "Preserved source recipe bytes changed: " + row["id"])
                path = row["source_entry"]
                check(path in names and mod.read(path) == local, "Source-specific recipe missing or replaced in JAR: " + row["id"])
                counts["packaged_source_recipe_overrides_checked"] += 1

        for class_path in NEW_MAIN_CLASSES:
            full_path = "io/github/ningmeng123/mite/" + class_path + ".class"
            check(full_path in names, "Missing new stacking production class: " + full_path)
            counts["required_stacking_main_classes_checked"] += 1
        for class_path in ("crafting/StackBatchShapedRecipe$Serializer", "crafting/StackBatchShapelessRecipe$Serializer"):
            check("io/github/ningmeng123/mite/" + class_path + ".class" in names, "Missing batch serializer class: " + class_path)
            counts["required_batch_serializers_checked"] += 1
        for name, path in compiled.items():
            check(name in names and mod.read(name) == path.read_bytes(), "Packaged production bytecode is stale: " + name)
        counts["current_production_classes_byte_checked"] = len(compiled)
        for name in names:
            if name.endswith(".class"):
                check(name in compiled, "Foreign, old-overlay, API or test class leaked into JAR: " + name)
            check(not (name.startswith(("mezz/jei/", ".reference/", "reference-data/", "spec/", "docs/", "tools/", "build/", "META-INF/jarjar/"))
                       or "/gametest/" in name or "/.cache/" in name
                       or name.endswith((".java", ".zip", ".jar", ".bbmodel"))),
                  "Development, reference, test or bundled-dependency resource leaked: " + name)
            if name.endswith(".json"):
                try:
                    json.loads(mod.read(name))
                    counts["packaged_json_files_checked"] += 1
                except (ValueError, UnicodeDecodeError) as failure:
                    failures.append(f"Invalid packaged JSON {name}: {failure}")
        mixin_bytes = file_bytes("src/main/resources/mite.mixins.json")
        check("mite.mixins.json" in names and mod.read("mite.mixins.json") == mixin_bytes, "Packaged Mixin configuration differs from current source")
        mixins = json.loads(mod.read("mite.mixins.json"))
        check(NEW_MIXINS.issubset(mixins["mixins"]), "New stacking Mixins are not declared")
        for name in mixins["mixins"] + mixins.get("client", []):
            path = mixins["package"].replace(".", "/") + "/" + name.replace(".", "/") + ".class"
            check(path in names, "Declared Mixin class missing: " + path)
            counts["declared_mixins_checked"] += 1
        counts["required_stacking_mixins_checked"] = len(NEW_MIXINS)
        metadata = tomllib.loads(mod.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        for dependencies in metadata.get("dependencies", {}).values():
            for dependency in dependencies:
                check(dependency.get("modId") != "jei" or dependency.get("type") != "required", "JEI must remain optional integration")
        for row in metadata.get("mods", []):
            if row.get("modId") == "mite":
                check(row.get("authors") == "NingMeng123", "Incorrect developer attribution in JAR metadata")
        counts["jar_entries_inspected"] = len(names)

    tests = []
    for relative, expected_count in PYTHON_TESTS:
        started = time.monotonic()
        result = subprocess.run([sys.executable, str(ROOT / relative)], cwd=ROOT, capture_output=True, text=True, encoding="utf-8", timeout=60)
        output = result.stdout + result.stderr
        match = re.search(r"Ran (\d+) tests?", output)
        count = int(match[1]) if match else 0
        tests.append({"path": relative, "sha256": sha((ROOT / relative).read_bytes()), "exit_code": result.returncode,
                      "test_count": count, "passed": result.returncode == 0 and count == expected_count,
                      "duration_seconds": round(time.monotonic() - started, 3), "output": output.strip()})
        check(result.returncode == 0 and count == expected_count, "Python mapping/extraction regression failed: " + relative)
        counts["python_test_suites_run"] += 1
        counts["python_tests_run"] += count

    report = {"author": "NingMeng123", "batch": "030", "scope": "独立规格、配方语义、当前编译与打包审计；不代替客户端手测。",
              **dict(counts), "snapshot_sha256": checksums, "jar_path": args.jar.as_posix(), "jar_bytes": len(jar_bytes),
              "jar_sha256": sha(jar_bytes), "python_regressions": tests,
              "failure_count": len(failures), "failures": failures, "passed": not failures,
              "manual_client_test": "pending_user_test"}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key not in {"python_regressions", "failures"}} | {"failures": failures[:20]}, ensure_ascii=False))
    raise SystemExit(bool(failures))


if __name__ == "__main__":
    main()
