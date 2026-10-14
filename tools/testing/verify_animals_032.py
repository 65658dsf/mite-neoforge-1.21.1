"""Audit independent R196 animal evidence, packaged production code and actual test reports.

Author: NingMeng123. Read-only verification; no Gradle, game, legacy JVM or Git is launched.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import tomllib
import xml.etree.ElementTree as ET
from zipfile import BadZipFile, ZipFile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "io/github/ningmeng123/mite/"
ARCHIVE_SHA = "fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3"
BASELINE_SHA = "f4513e51c766cdd1d32ca8acd0835d7e16e2851ebea3f2a3b4ce5ae696baf3ae"
SOURCE_COUNTS = {
    "source_species": 15,
    "modern_identity_mappings": 20,
    "rules": 28,
    "indexed_classes": 107,
    "indexed_methods": 2919,
}
MIXINS = (
    "Livestock032AnimalMixin", "Livestock032GrowthMixin", "Livestock032ChickenMixin",
    "Livestock032SheepMixin", "Livestock032FoodMixin", "Livestock032MooshroomMixin",
    "Livestock032GrassMixin", "Livestock032GrassRecoveryMixin", "NativeWolf032Mixin",
    "NativeCat032Mixin", "NativeOcelot032Mixin", "NativeHorse032Mixin",
    "NativeHorseBreeding032Mixin", "NativeHorseVariant032Access", "NativeBat032Mixin",
    "NativeBoat032Mixin", "SourceAnimalDespawn032Mixin",
)
ANIMAL_CLASSES = (
    "Livestock032", "LivestockRules032", "LivestockSeeking032", "LivestockFreedomGoal032",
    "LivestockGrazingGoal032", "LivestockGrass032", "Livestock032View", "LivestockSheep032View",
    "SourceMovement032Goal", "R196AnimalAi032", "AnimalCommon032Goals",
    "NativeAnimalSpecial032", "SourcePetGoals032", "SourceAquatic032", "SourceAnimalDespawn032",
)


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def log_text(data: bytes) -> str:
    # PowerShell versions can produce either UTF-8 or UTF-16 when redirecting a log.
    if data.startswith((b"\xff\xfe", b"\xfe\xff")):
        return data.decode("utf-16")
    return data.decode("utf-8-sig", errors="replace")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=ROOT / "build/libs/mite-0.1.0.jar")
    parser.add_argument("--classes", type=Path, default=ROOT / "build/classes/java/main")
    parser.add_argument("--junit-directory", type=Path, default=ROOT / "build/test-results/test")
    parser.add_argument("--game-log", type=Path, required=True,
                        help="Completed headless GameTest/Gradle log for this verification run")
    parser.add_argument("--report", type=Path, default=ROOT / "build/verification/032-animal-package-audit.json")
    args = parser.parse_args()
    failures: list[str] = []
    counts: Counter[str] = Counter()
    checksums: dict[str, str] = {}

    def check(condition: bool, message: str) -> None:
        counts["checks"] += 1
        if not condition:
            failures.append(message)

    def data(path: Path) -> bytes | None:
        try:
            raw = path.read_bytes()
            checksums[str(path)] = sha(raw)
            return raw
        except OSError as error:
            check(False, f"Cannot read audit input {path}: {error}")
            return None

    def read_json(path: Path) -> dict:
        raw = data(path)
        if raw is None:
            return {}
        try:
            value = json.loads(raw)
            check(isinstance(value, dict), f"JSON object required: {path}")
            return value if isinstance(value, dict) else {}
        except (UnicodeError, json.JSONDecodeError) as error:
            check(False, f"Invalid JSON {path}: {error}")
            return {}

    spec_path = ROOT / "spec/animals-source-032.json"
    spec = read_json(spec_path)
    check(spec.get("schema_version") == 1 and spec.get("author") == "NingMeng123", "Animal evidence schema/author")
    check(spec.get("legacy_world_execution") is False, "Source extraction must not claim legacy world execution")
    check(spec.get("archive_sha256") == ARCHIVE_SHA, "Animal spec references the pinned R196 archive")
    check(spec.get("official_baseline_sha256") == BASELINE_SHA, "Animal spec references the pinned official 1.6.4 baseline")
    for path, expected in ((ROOT / "MITE 1.6.4 R196.zip", ARCHIVE_SHA),
                           (ROOT / ".reference/downloads/minecraft-1.6.4-client.jar", BASELINE_SHA)):
        raw = data(path)
        check(raw is not None and sha(raw) == expected, f"Original reference SHA-256: {path.name}")

    entities = spec.get("entities", [])
    rules = spec.get("rules", [])
    source_classes = spec.get("source_classes", [])
    actual_source_counts = {
        "source_species": len(entities),
        "modern_identity_mappings": sum(len(row.get("modern_ids", [])) for row in entities),
        "rules": len(rules),
        "indexed_classes": len(source_classes),
        "indexed_methods": sum(len(row.get("methods", [])) for row in source_classes),
    }
    check(actual_source_counts == SOURCE_COUNTS, "Independently confirmed source species/rules/class/method counts")
    check(spec.get("counts") == actual_source_counts, "Recorded source counts agree with actual evidence rows")
    check(len({row.get("legacy_id") for row in entities}) == len(entities), "Unique source entity IDs")
    modern_ids = [identity for row in entities for identity in row.get("modern_ids", [])]
    check(len(set(modern_ids)) == len(modern_ids), "Unique modern animal identity mappings")
    check(len({row.get("id") for row in rules}) == len(rules), "Unique source rule identities")
    indexed = {row.get("class"): row for row in source_classes}
    check(len(indexed) == len(source_classes), "Unique source class indexes")

    def no_bodies(value: object, location: str = "spec") -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                check(key not in {"_body", "body", "source_code", "decompiled_body", "method_body"},
                      f"No complete decompiled body field: {location}.{key}")
                no_bodies(child, location + "." + key)
        elif isinstance(value, list):
            for index, child in enumerate(value):
                no_bodies(child, f"{location}[{index}]")
        elif isinstance(value, str):
            check("Decompiled with CFR" not in value, f"No full decompiled source embedded: {location}")

    no_bodies(spec)
    baseline_path = ROOT / ".reference/downloads/minecraft-1.6.4-client.jar"
    try:
        with ZipFile(baseline_path) as baseline:
            baseline_names = set(baseline.namelist())
            for row in source_classes:
                name = row["class"]
                binary = data(ROOT / ".reference/r196/classes" / (name + ".class")) if row.get("r196_overlay") else baseline.read(name + ".class")
                check(binary is not None and sha(binary) == row.get("class_sha256"), "Indexed original class bytes: " + name)
                baseline_sha = sha(baseline.read(name + ".class")) if name + ".class" in baseline_names else None
                check(baseline_sha == row.get("baseline_class_sha256"), "Baseline/overlay distinction: " + name)
                if row.get("decompiled_path"):
                    path = ROOT / row["decompiled_path"]
                    raw = data(path)
                    if raw is not None:
                        text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
                        check(sha(text.encode("utf-8")) == row.get("decompiled_sha256"), "Indexed normalized decompilation bytes: " + name)
                    counts["decompiled_files_checked"] += 1
                counts["source_classes_checked"] += 1
    except (OSError, BadZipFile, KeyError, UnicodeError) as error:
        check(False, "Cannot inspect original reference classes: " + str(error))

    for rule in rules:
        check(bool(rule.get("sources")) and bool(rule.get("acceptance_cases")), "Rule evidence and acceptance cases: " + rule.get("id", "?"))
        check(bool(rule.get("enabled_when")) and bool(rule.get("server_responsibility")), "Rule activation/authority: " + rule.get("id", "?"))
        for reference in rule.get("sources", []):
            row = indexed.get(reference.get("class"), {})
            match = [method for method in row.get("methods", [])
                     if all(method.get(key) == reference.get(key) for key in ("signature", "line", "end_line", "method_sha256"))]
            check(bool(match) and reference.get("file") == row.get("decompiled_path"), "Rule method citation resolves: " + rule.get("id", "?"))
            counts["rule_method_citations_checked"] += 1

    compiled = {path.relative_to(args.classes).as_posix(): path for path in args.classes.rglob("*.class")}
    check(bool(compiled), "Current production compilation is available")
    mixin_path = ROOT / "src/main/resources/mite.mixins.json"
    mixins = read_json(mixin_path)
    common_mixins = mixins.get("mixins", [])
    for name in MIXINS:
        check(common_mixins.count(name) == 1, "Exactly one common registration: " + name)
        check(PACKAGE + "mixin/" + name + ".class" in compiled, "Animal mixin compiled: " + name)
    for name in ANIMAL_CLASSES:
        check(PACKAGE + "entity/animal/" + name + ".class" in compiled, "Animal production class compiled: " + name)
    counts["animal_mixins_checked"] = len(MIXINS)
    counts["animal_top_level_classes_checked"] = len(ANIMAL_CLASSES)

    jar_raw = data(args.jar)
    try:
        with ZipFile(args.jar) as jar:
            all_names = jar.namelist()
            names = set(all_names)
            check(len(names) == len(all_names), "No duplicate packaged paths")
            check("mite.mixins.json" in names and jar.read("mite.mixins.json") == mixin_path.read_bytes(), "Packaged Mixin config equals source config")
            for relative, path in compiled.items():
                check(relative in names and jar.read(relative) == path.read_bytes(), "Packaged/current production bytecode equality: " + relative)
                counts["production_bytecode_checked"] += 1
            packaged_classes = {name for name in names if name.endswith(".class")}
            check(packaged_classes == set(compiled), "JAR includes exactly the current production class set")
            forbidden = (".reference/", "tools/", "spec/", "org/junit/", "org/gradle/", "mezz/jei/",
                         "net/minecraft/", "net/neoforged/", "com/llamalad7/mixinextras/", "org/spongepowered/")
            for name in sorted(names):
                check(not name.startswith(forbidden) and "/gametest/" not in name.lower()
                      and not name.endswith((".java", ".zip", ".jar", ".bbmodel")), "No test/research/dependency content: " + name)
            check(not any(name.endswith(".class") and "/" not in name for name in names), "No original obfuscated classes bundled")
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8"))
            declared_mods = metadata.get("mods", [])
            check(len(declared_mods) == 1 and declared_mods[0].get("modId") == "mite", "One MITE mod declaration")
            check(all(mod.get("authors") == "NingMeng123" for mod in declared_mods), "All packaged developer authors are NingMeng123")
            dependencies = metadata.get("dependencies", {}).get("mite", [])
            check(all(dependency.get("type") != "required" for dependency in dependencies if dependency.get("modId") == "jei"), "JEI remains optional integration")
            check({dependency.get("modId") for dependency in dependencies if dependency.get("type") == "required"} == {"minecraft", "neoforge"}, "Only Minecraft/NeoForge are required dependencies")
            counts["packaged_entries_checked"] = len(names)
    except (OSError, BadZipFile, KeyError, UnicodeError, tomllib.TOMLDecodeError) as error:
        check(False, "Cannot inspect installable JAR: " + str(error))

    junit_counts: Counter[str] = Counter()
    junit_reports = []
    xml_files = sorted(args.junit_directory.glob("TEST-*.xml"))
    check(bool(xml_files), "Actual JUnit XML results exist")
    for path in xml_files:
        raw = data(path)
        if raw is None:
            continue
        try:
            suite = ET.fromstring(raw)
            check(suite.tag == "testsuite", "JUnit suite root: " + path.name)
            values = {key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
            check(values["tests"] == len(suite.findall("testcase")), "JUnit declared/actual testcase count: " + path.name)
            check(values["failures"] == values["errors"] == 0, "JUnit passed: " + path.name)
            junit_counts.update(values)
            junit_reports.append({"file": str(path), "suite": suite.get("name"), "timestamp": suite.get("timestamp"), **values})
        except (ValueError, ET.ParseError) as error:
            check(False, f"Invalid JUnit XML {path}: {error}")
    junit_counts["passed"] = junit_counts["tests"] - junit_counts["failures"] - junit_counts["errors"] - junit_counts["skipped"]
    check(any(row["suite"] == "io.github.ningmeng123.mite.entity.animal.LivestockRules032Test" and row["tests"] > 0
              for row in junit_reports), "032 livestock source-rule tests are included in actual JUnit run")

    game_result = {"file": str(args.game_log), "completed": None, "passed": None, "failed_required": None, "failed_optional": None}
    raw = data(args.game_log)
    if raw is not None:
        text = log_text(raw)
        completions = list(re.finditer(r"=+\s+(\d+) GAME TESTS COMPLETE IN[^\r\n]*", text))
        check(bool(completions), "GameTest log contains a final completion count")
        if completions:
            completion = completions[-1]
            # Read the last completed run, rather than an earlier success in an appended log.
            final_segment = text[completion.end():]
            pass_match = re.search(r"All (\d+) required tests passed", final_segment)
            required = re.search(r"(\d+) required tests failed", final_segment)
            optional = re.search(r"(\d+) optional tests failed", final_segment)
            completed = int(completion.group(1))
            failed_required = int(required.group(1)) if required else 0
            failed_optional = int(optional.group(1)) if optional else 0
            game_result.update(completed=completed, failed_required=failed_required, failed_optional=failed_optional,
                               passed=completed - failed_required - failed_optional,
                               logged_pass_count=int(pass_match.group(1)) if pass_match else None)
            # GameTestServer logs the total count in its 'required tests passed' message.
            check(pass_match is not None and int(pass_match.group(1)) == completed and completed > 0,
                  "GameTest final success message agrees with completed count")
            check(failed_required == failed_optional == 0, "No required or optional GameTest failures")
            check("BUILD SUCCESSFUL" in final_segment and "BUILD FAILED" not in final_segment,
                  "Completed GameTest Gradle process succeeded")

    result = {
        "author": "NingMeng123", "status": "passed" if not failures else "failed",
        "failures": failures, "counts": dict(counts), "source_counts": actual_source_counts,
        "jar": str(args.jar), "jar_sha256": sha(jar_raw) if jar_raw is not None else None,
        "junit": {"directory": str(args.junit_directory), **dict(junit_counts), "reports": junit_reports},
        "gametest": game_result, "input_sha256": checksums,
        "user_manual_test": "pending", "game_ui_automated": False,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("status", "failures", "counts", "source_counts", "jar_sha256", "gametest")}, ensure_ascii=False))
    print(json.dumps({"junit": dict(junit_counts), "report": str(args.report)}, ensure_ascii=False))
    raise SystemExit(bool(failures))


if __name__ == "__main__":
    main()
