"""Extract every registered R196 stack cap and its merge invariants. Author: NingMeng123.

Default consumes the guarded oracle log. --run-oracle rebuilds and reruns that
oracle using existing, pinned local reference dependencies; it never downloads
dependencies, disables the guard, invokes Minecraft main, or creates a world.
"""
import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
WORKSPACE = ROOT / ".reference"
HELPER = WORKSPACE / "runtime-helper"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def run_oracle(java):
    java = Path(java).resolve()
    javac = java.with_name("javac.exe" if os.name == "nt" else "javac")
    jars = sorted((WORKSPACE / "downloads/libraries").rglob("*.jar"))
    overlay = HELPER / "r196-overlay.jar"
    vanilla = WORKSPACE / "downloads/minecraft-1.6.4-client.jar"
    required = [java, javac, overlay, vanilla, ROOT / "tools/reference/RegistryExport.java"]
    if not all(p.is_file() for p in required) or not jars:
        raise FileNotFoundError("Prepare the guarded local reference runtime before exporting stack rules")
    classpath = os.pathsep.join(map(str, [HELPER, overlay, WORKSPACE / "r196/resources", vanilla, *jars]))
    compile_run = subprocess.run([str(javac), "--release", "17", "-cp", classpath,
                                  "-d", str(HELPER), str(ROOT / "tools/reference/RegistryExport.java"),
                                  str(ROOT / "tools/reference/StackingRulesExport.java")],
                                 capture_output=True, text=True, encoding="utf-8", errors="replace")
    (HELPER / "stacking-030-compile.log").write_text(compile_run.stdout + compile_run.stderr, encoding="utf-8")
    if compile_run.returncode:
        raise RuntimeError("Stack oracle compilation failed; inspect .reference/runtime-helper/stacking-030-compile.log")
    run = subprocess.run([str(java), "-Xmx1G", "-Djava.awt.headless=true", "-Djava.security.manager=allow",
                          "-cp", classpath, "StackingRulesExport", str(WORKSPACE)],
                         cwd=HELPER, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
    (HELPER / "stacking-030-oracle.stdout.log").write_text(run.stdout, encoding="utf-8")
    (HELPER / "stacking-030-oracle.stderr.log").write_text(run.stderr, encoding="utf-8")
    if run.returncode:
        raise RuntimeError("Guarded stack oracle failed; no fallback removes the security guard")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-oracle", action="store_true")
    parser.add_argument("--java", help="Java 17..23 java executable for --run-oracle")
    args = parser.parse_args()
    if args.run_oracle:
        if not args.java:
            parser.error("--run-oracle requires --java pointing to Java 17..23")
        run_oracle(args.java)
    text = (HELPER / "stacking-030-oracle.stdout.log").read_text(encoding="utf-8")
    data = json.loads(text.split("STACKING_ORACLE_BEGIN\n", 1)[1].split("\nSTACKING_ORACLE_END", 1)[0])
    runtime = load(ROOT / "reference-data/runtime-registry.json")
    original_items = {entry["id"]: entry for entry in runtime["items"]}
    original_blocks = {entry["id"]: entry for entry in runtime["blocks"]}
    assert len(data["items"]) == len(original_items) == 667
    assert len(data["blocks"]) == len(original_blocks) == 221
    assert {entry["legacy_id"] for entry in data["items"]} == original_items.keys()
    assert data["game_main_invoked"] is False and data["world_created"] is False
    expected_guards = {"write_file", "process", "network", "native_load", "thread_create", "disable_guard", "unrelated_read"}
    assert set(data["guard_checks_passed"]) == expected_guards
    assert all(test["actual"] == test["expected"] for test in data["identity_checks"])
    for entry in data["items"]:
        original = original_items[entry["legacy_id"]]
        assert entry["class"] == original["class"]
        assert entry["base_limit"] == original["scalars"]["yc.cw"]
        assert 1 <= entry["base_limit"] <= 64
        entry["source"] = original["source"]
        entry["materials"] = original["materials"]
        entry["symbols"] = original["symbols"]
        entry["limit_kind"] = "snow_depth" if entry["limit_method_declared_by"] == "zc" else "constant"
        for subtype in entry["subtype_limits"]:
            assert subtype["max_stack_size"] == subtype["limit_damage_1"] == subtype["limit_damage_max_int"]
        if entry["damageable"]:
            assert entry["base_limit"] == 1  # No default registered damaged item can normally merge.
        if entry["legacy_id"] in original_blocks:
            entry["block_source"] = original_blocks[entry["legacy_id"]]["source"]
    for entry in data["blocks"]:
        original = original_blocks[entry["legacy_id"]]
        entry["source_name"] = original["scalars"]["aqz.b"]
        entry["source"] = original["source"]
        assert entry["max_stack_size"] == original["scalars"]["aqz.max_stack_size"]
        assert entry["registered_item_limit"] is None or entry["registered_item_limit"] == entry["max_stack_size"]
    sources = ["yc", "ye", "zc", "aqz", "ud", "uy", "ss", "asi", "we", "yr", "zo", "ItemReferencedBook"]
    data.update({
        "schema_version": 1,
        "archive_sha256": runtime["archive_sha256"],
        "mode": "guarded_registration_and_pure_item_stack_methods",
        "provenance": {
            "runtime_registry_sha256": digest(ROOT / "reference-data/runtime-registry.json"),
            "oracle_source_sha256": digest(ROOT / "tools/reference/StackingRulesExport.java"),
            "helper_guard_source_sha256": digest(ROOT / "tools/reference/RegistryExport.java"),
            "overlay_sha256": digest(HELPER / "r196-overlay.jar"),
            "minecraft_1_6_4_client_sha256": digest(WORKSPACE / "downloads/minecraft-1.6.4-client.jar"),
            "oracle_stdout_sha256": digest(HELPER / "stacking-030-oracle.stdout.log"),
            "source_spans": {
                "default_item_limit": "yc.java:468, 515-519, 542-545, 574-575",
                "block_override_after_registration": "aqz.java:1669-1679, 2538-2542",
                "stack_limit_and_stackable": "ye.java:180-185",
                "strict_identity": "ye.java:388-460",
                "inventory_merge": "ud.java:185-191, 288-334, 376-387, 402-423",
                "container_click_and_shift": "uy.java:224-250, 386-434, 461-470",
                "slot_capacity": "we.java:112-114; ud.java:563-564",
                "hopper_merge": "asi.java:267-291, 326-331",
                "ground_merge": "ss.java:138-169",
                "snow_subtype_limit": "zc.java:52-53",
                "unique_signatures": "ye.java:1141-1167; yr.java:18-21; zo.java:8-11",
            },
            "decompiled_source_sha256": {name: digest(WORKSPACE / f"r196/decompiled/{name}.java") for name in sources},
        },
        "summary": {
            "registered_items": 667,
            "registered_blocks": 221,
            "enumerated_getSubItems_variants": sum(len(e["subtype_limits"]) for e in data["items"]),
            "item_base_limit_counts": dict(sorted(collections.Counter(e["base_limit"] for e in data["items"]).items())),
            "limit_method_declaring_classes": dict(collections.Counter(e["limit_method_declared_by"] for e in data["items"])),
            "damageable_items_with_limit_above_one": 0,
            "dynamic_limit_items": [e["legacy_id"] for e in data["items"] if e["limit_kind"] != "constant"],
        },
        "rules": {
            "default_item_limit": 16,
            "default_limit_is_overridden_by_item_constructors_and_final_block_registration": True,
            "limit_formula": "item.getItemStackLimit(subtype, damage); all registered classes inherit yc.cw except zc snow",
            "snow_limit_formula": "subtype == 0 ? 32 : 8",
            "snow_creative_subtypes": [0, 3],
            "snow_world_depth_subtypes": list(range(8)),
            "damage_changes_limit": False,
            "stackable_formula": "limit > 1 && (!damageable || damage == 0)",
            "strict_inventory_identity": ["item_id", "subtype", "damage", "quality", "is_artifact", "tag_compound"],
            "enchantments_and_names_are_nested_tag_compound_identity": True,
            "signature_itself_has_no_special_stack_limit_override": True,
            "unique_records_and_written_books_have_constructor_limit_one": True,
            "inventory_base_slot_limit": 64,
            "inventory_slot_capacity_formula": "min(item_limit, inventory_limit); specialized slots retain their own restrictions",
            "ground_merge_checks": ["same item instance", "same tag presence and equal tag data", "same subtype for subtype items", "entire combined count <= item limit"],
            "ground_merge_is_whole_stack_only": True,
            "ground_merge_separately_checks_quality_or_damage": False,
            "default_ground_merge_omission_is_inert_for_damageable_items": "Every registered damageable item has limit 1, so two normal item entities cannot combine",
            "hopper_identity_formula": "strict ItemStack equality ignoring count; transfer <= item limit minus destination count",
            "professions_affect_stacking": False,
        },
        "boundaries": [
            "953 getSubItems entries enumerate creative/reference variants, not every possible map id, potion encoding, or block metadata.",
            "All non-enumerated subtypes are covered by the proven constant yc getter; snow's full 0..7 depth range is probed separately.",
            "No player, container, world, item entity, Minecraft client or server is constructed by the oracle. Inventory/container/hopper/ground behavior is traced from source and identified separately from directly executed methods.",
            "Old ground merging accepts only a complete combination; Minecraft 1.21.1 supports partial merging. The modern adaptation must decide this explicitly rather than claiming exact source parity from item limits alone.",
            "Malformed/command-created over-limit stacks are not evidence of a normal larger capacity; old constructors and some empty-slot transfer paths do not themselves clamp the entire input count.",
            "Additional modern-only items and third-party mods have no R196 source identity; their adaptations belong in a separate modern mapping dataset.",
        ],
    })
    destination = ROOT / "spec/stacking-source-030.json"
    destination.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(data["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
