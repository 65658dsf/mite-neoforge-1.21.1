"""Export complete R196 manufacturing costs and current item identities. Author: NingMeng123.

The optional oracle runs only registration and pure Item/ItemStack methods under
the existing read-only SecurityManager. It never creates a game, player or world,
downloads dependencies, or removes the security guard. Default regeneration uses
the saved local oracle output, not guessed values for unknown source costs.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / ".reference"
HELPER = REFERENCE / "runtime-helper"
ORACLE_SOURCE = r'''/* Pure R196 manufacturing oracle. Author: NingMeng123. */
import java.nio.file.*;
import java.util.*;
public final class CompleteCraftingTimeOracle {
    @SuppressWarnings("removal")
    public static void main(String[] args) throws Exception {
        Path workspace = Path.of(args[0]).toAbsolutePath().normalize();
        var guard = new RegistryExport.ReadOnlyGuard(List.of(workspace,
                Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()));
        Class.forName("RegistryExport$GuardProbe");
        System.setSecurityManager(guard);
        var checks = RegistryExport.guardTests(workspace);
        Class.forName("aqz"); Class.forName("yc"); aaf.a();
        List<Object> variants = new ArrayList<>();
        for (String request : Files.readAllLines(workspace.resolve("runtime-helper/crafting-time-requests.tsv"))) {
            String[] parts = request.split("\\t");
            int id = Integer.parseInt(parts[0]), subtype = Integer.parseInt(parts[1]);
            yc item = yc.g[id];
            if (item == null) throw new AssertionError("Unregistered requested item " + id);
            ye stack = new ye(item, 1, subtype);
            float component = stack.getCraftingDifficultyAsComponent();
            float lowest = item.getLowestCraftingDifficultyToProduce();
            akc metal = item.getHardestMetalMaterial();
            variants.add(RegistryExport.map("source_id", id, "subtype", subtype,
                    "component_difficulty", component < 0F ? null : component,
                    "lowest_difficulty", lowest == Float.MAX_VALUE ? null : lowest,
                    "hardest_metal", metal == null ? null : RegistryExport.field(metal, "name")));
        }
        System.out.println("COMPLETE_CRAFTING_TIME_BEGIN");
        System.out.println(RegistryExport.json(RegistryExport.map("author", "NingMeng123",
                "java_version", System.getProperty("java.version"), "guard_checks_passed", checks,
                "game_main_invoked", false, "world_created", false, "variants", variants)));
        System.out.println("COMPLETE_CRAFTING_TIME_END");
    }
}
'''


def read(path: str):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path: str, value):
    (ROOT / path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def requests(runtime, identities, source_items):
    requested = {(row["id"], 0) for row in runtime["items"]}
    for row in source_items["items"]:
        requested.update((row["legacy_id"], x["subtype"]) for x in row["subtype_limits"])
        requested.update((row["legacy_id"], x["subtype"]) for x in row["dynamic_subtype_limits"])
    for row in identities["mappedItems"]:
        requested.update((x["legacy_id"], x["subtype"]) for x in row["source_items"])
    for recipe in runtime["crafting_recipes"]:
        for stack in [recipe["output"], *(recipe["components"] or [])]:
            if stack is not None and stack["subtype"] != 32767:
                requested.add((stack["item_id"], stack["subtype"]))
    return sorted(requested)


def run_oracle(java: str, requested):
    java = Path(java).resolve()
    javac = java.with_name("javac.exe" if os.name == "nt" else "javac")
    jars = sorted((REFERENCE / "downloads/libraries").rglob("*.jar"))
    overlay = HELPER / "r196-overlay.jar"
    vanilla = REFERENCE / "downloads/minecraft-1.6.4-client.jar"
    required = [java, javac, overlay, vanilla, ROOT / "tools/reference/RegistryExport.java"]
    if not all(p.is_file() for p in required) or not jars:
        raise FileNotFoundError("Existing guarded reference runtime and Java 17..23 required")
    source = HELPER / "CompleteCraftingTimeOracle.java"
    source.write_text(ORACLE_SOURCE, encoding="utf-8")
    (HELPER / "crafting-time-requests.tsv").write_text(
        "".join(f"{sid}\t{subtype}\n" for sid, subtype in requested), encoding="utf-8")
    classpath = os.pathsep.join(map(str, [HELPER, overlay, REFERENCE / "r196/resources", vanilla, *jars]))
    compile_result = subprocess.run([str(javac), "--release", "17", "-cp", classpath, "-d", str(HELPER),
                                     str(ROOT / "tools/reference/RegistryExport.java"), str(source)],
                                    capture_output=True, text=True, encoding="utf-8", errors="replace")
    (HELPER / "crafting-time-compile.log").write_text(compile_result.stdout + compile_result.stderr, encoding="utf-8")
    if compile_result.returncode:
        raise RuntimeError("Oracle compilation failed; inspect .reference/runtime-helper/crafting-time-compile.log")
    result = subprocess.run([str(java), "-Xmx1G", "-Djava.awt.headless=true", "-Djava.security.manager=allow",
                             "-cp", classpath, "CompleteCraftingTimeOracle", str(REFERENCE)], cwd=HELPER,
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
    (HELPER / "crafting-time-oracle.stdout.log").write_text(result.stdout, encoding="utf-8")
    (HELPER / "crafting-time-oracle.stderr.log").write_text(result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError("Guarded oracle failed; no fallback disables the guard")


def stack(row):
    if row is None:
        return None
    return {"source_id": row["item_id"], "subtype": row["subtype"], "count": row["count"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-oracle", action="store_true")
    parser.add_argument("--java", help="Java 17..23 executable for the guarded oracle")
    args = parser.parse_args()
    runtime = read("reference-data/runtime-registry.json")
    identities = read("spec/stacking-map-030.json")
    source_items = read("spec/stacking-source-030.json")
    requested = requests(runtime, identities, source_items)
    if args.run_oracle:
        if not args.java:
            parser.error("--run-oracle requires --java")
        run_oracle(args.java, requested)
    raw = (HELPER / "crafting-time-oracle.stdout.log").read_text(encoding="utf-8")
    oracle = json.loads(raw.split("COMPLETE_CRAFTING_TIME_BEGIN\n", 1)[1].split("\nCOMPLETE_CRAFTING_TIME_END", 1)[0])
    costs = {(x["source_id"], x["subtype"]): x for x in oracle["variants"]}
    assert set(costs) == set(requested), "Stale local oracle: regenerate with --run-oracle"
    assert oracle["game_main_invoked"] is False and oracle["world_created"] is False
    assert set(oracle["guard_checks_passed"]) == {"write_file", "process", "network", "native_load", "thread_create", "disable_guard", "unrelated_read"}
    items = {}
    for identity in identities["mappedItems"]:
        aliases = [dict(costs[(x["legacy_id"], x["subtype"])]) for x in identity["source_items"]]
        assert aliases
        primary = dict(aliases[0])
        primary["mapping_kind"] = identity["mapping_kind"]
        primary["aliases"] = aliases
        items[identity["modern_id"]] = primary
    recipes = []
    for source in runtime["crafting_recipes"]:
        row = {"order": source["order"], "type": source["class"], "difficulty": source["difficulty"],
               "required_bench": None if source["bench_material"] is None else source["bench_material"]["name"],
               "output": stack(source["output"]),
               "components": None if source["components"] is None else [stack(x) for x in source["components"]],
               "include_in_lowest_difficulty": source["include_in_lowest_difficulty"]}
        for key in ("width", "height"):
            if key in source:
                row[key] = source[key]
        recipes.append(row)
    dynamic = {}
    for name, order, quality, exempt in [("armor_dye", 0, True, True), ("fireworks", 1, False, False),
                                         ("map_cloning", 155, False, False), ("map_extending", 156, False, False)]:
        source = runtime["crafting_recipes"][order]
        dynamic[name] = {"source_order": order, "type": source["class"], "difficulty": source["difficulty"],
                         "quality_override": quality, "experience_exempt": exempt}
    mapped_source = {x["source_id"] for row in items.values() for x in row["aliases"]}
    missing = [x for x in oracle["variants"] if x["component_difficulty"] is None]
    summary = {"source_registered_items": len(runtime["items"]), "source_variant_costs": len(costs),
               "source_recipes": len(recipes), "source_static_recipes": sum(x["type"] in {"aai", "aaj"} for x in recipes),
               "source_dynamic_recipes": len(dynamic), "mapped_modern_items": len(items),
               "mapped_source_items": len(mapped_source), "unmapped_source_items": len(runtime["items"]) - len(mapped_source),
               "modern_mapping_kinds": dict(collections.Counter(x["mapping_kind"] for x in items.values())),
               "source_component_cost_unset_variants": len(missing),
               "mapped_modern_component_cost_unset": sum(x["component_difficulty"] is None for x in items.values()),
               "mapped_modern_lowest_difficulty_unset": sum(x["lowest_difficulty"] is None for x in items.values())}
    document = {"schema_version": 1, "author": "NingMeng123", "source_version": "R196",
                "archive_sha256": runtime["archive_sha256"], "summary": summary,
                "source_recipes": recipes, "items": items, "dynamic": dynamic}
    write("src/main/resources/mite/complete-crafting-time.json", document)
    provenance = {"input_sha256": {p: sha(ROOT / p) for p in ["reference-data/runtime-registry.json", "spec/stacking-map-030.json", "spec/stacking-source-030.json", "tools/reference/RegistryExport.java", "tools/specs/generate_complete_crafting_time.py"]},
                  "oracle_source_sha256": hashlib.sha256(ORACLE_SOURCE.encode("utf-8")).hexdigest(),
                  "oracle_stdout_sha256": sha(HELPER / "crafting-time-oracle.stdout.log"),
                  "overlay_sha256": sha(HELPER / "r196-overlay.jar"),
                  "minecraft_1_6_4_client_sha256": sha(REFERENCE / "downloads/minecraft-1.6.4-client.jar"),
                  "guard_checks_passed": oracle["guard_checks_passed"], "game_main_invoked": False, "world_created": False,
                  "java_version": oracle["java_version"]}
    document.update({"provenance": provenance, "source_variant_costs": oracle["variants"],
                     "source_component_cost_unset": missing,
                     "unmapped_source_items": [{"source_id": x["id"], "class": x["class"], "symbols": x["symbols"]}
                                                for x in runtime["items"] if x["id"] not in mapped_source],
                     "boundaries": [
                         "All 902 registered source recipes retain final whole-batch float difficulty, including explicit overrides and scales; output count does not divide manufacturing time.",
                         "Each nonempty source component slot contributes once; its stored stack count is not a difficulty multiplier.",
                         "The item oracle executes actual metadata-sensitive block component methods and final lowest-production difficulty after registration.",
                         "Null component or lowest-production difficulty preserves an unset source value. No guessed 25-tick source fallback is added.",
                         "Modern adaptation mappings reuse explicitly identified source families; they do not assert that modern items existed in R196.",
                         "Missing modern identities and unmapped source items do not add new items or recipes. This data extends manufacturing time only.",
                         "Repair combinations are algorithmic before registered recipe matching: minimum production difficulty / 2; leather mending uses actual consumption * 50. Quality overrides and exemption are handled by manufacturing code.",
                         "The aad registry output is a matching placeholder; actual map extension preserves the filled input map and marks map_is_scaling.",
                     ]})
    write("spec/complete-crafting-time.json", document)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
