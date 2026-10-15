"""Verify R196 livestock health evidence, source skins, packaged code and actual test reports.

Author: NingMeng123. Never launches Gradle, a game, a legacy JVM, OpenGL or Git.
The client report proves transformed bytecode/resource wiring, not visual appearance.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import struct
import tomllib
import xml.etree.ElementTree as ET
from zipfile import BadZipFile, ZipFile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "io/github/ningmeng123/mite/"
ARCHIVE_SHA = "fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3"
PACK_SHA = "4a9bb9f7ab5afee7c0e4e9f57f3b81ff185a0c619d29036b8c2f1ece7450e67a"
SPECIES = ("chicken", "cow", "pig", "sheep")
MIXINS = tuple("client.Sick" + species.title() + "Texture033Mixin" for species in SPECIES)
SOURCE_COUNTS = {
    "source_livestock_species": 5, "sick_renderer_species": 4, "semantic_rules": 13,
    "indexed_classes": 19, "selected_decompiled_methods": 83, "isWell_call_sites": 16,
    "decompiled_classes_scanned": 1183, "class_binaries_scanned": 1186,
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def log_text(data: bytes) -> str:
    return data.decode("utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig", errors="replace")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=ROOT / "build/libs/mite-0.1.0.jar")
    parser.add_argument("--classes", type=Path, default=ROOT / "build/classes/java/main")
    parser.add_argument("--junit-directory", type=Path, default=ROOT / "build/test-results/test")
    parser.add_argument("--game-log", type=Path, required=True, help="Completed headless GameTest/Gradle log")
    parser.add_argument("--client-audit", type=Path, default=ROOT / "build/verification/033-client-health-audit.json")
    parser.add_argument("--report", type=Path, default=ROOT / "build/verification/033-livestock-health-package-audit.json")
    args = parser.parse_args()
    failures: list[str] = []
    counts: Counter[str] = Counter()
    input_hashes: dict[str, str] = {}

    def check(condition: bool, message: str) -> None:
        counts["checks"] += 1
        if not condition:
            failures.append(message)

    def data(path: Path) -> bytes | None:
        try:
            raw = path.read_bytes()
            input_hashes[str(path)] = sha(raw)
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

    spec = read_json(ROOT / "spec/livestock-health-source-033.json")
    assets = read_json(ROOT / "spec/livestock-health-assets-033.json")
    check(spec.get("author") == assets.get("author") == "NingMeng123", "Evidence/asset developer author")
    check(spec.get("schema_version") == assets.get("schema_version") == 1, "Evidence/asset schema versions")
    check(spec.get("legacy_world_execution") is False, "Source audit does not claim legacy world execution")
    check(spec.get("source_archive_sha256") == ARCHIVE_SHA, "Source archive authority")
    check(spec.get("source_resource_pack_sha256") == assets.get("source_pack_sha256") == PACK_SHA, "Source resource pack authority")
    check(spec.get("counts") == SOURCE_COUNTS, "Confirmed source rule/class/method/call-site counts")
    rules = spec.get("rules", [])
    indexed = {row.get("class"): row for row in spec.get("source_classes", [])}
    check(len(rules) == SOURCE_COUNTS["semantic_rules"] and len({row.get("id") for row in rules}) == len(rules), "Unique semantic health rules")
    check(len(indexed) == SOURCE_COUNTS["indexed_classes"], "Unique source class indexes")
    check(sum(len(row.get("selected_methods", [])) for row in indexed.values()) == SOURCE_COUNTS["selected_decompiled_methods"], "Selected source methods agree with counts")
    check(len(spec.get("entity_scope", [])) == 5 and len(spec.get("texture_assets", [])) == 4, "Five health species, four sick body skins")
    check(len(spec.get("isWell_call_sites", [])) == 16, "All indexed wellness call sites")
    check(bool(spec.get("explicit_non_rules")), "Unsupported infection/HP/slowdown rules explicitly excluded")
    for row in indexed.values():
        raw = data(ROOT / row["file"])
        binary = data(ROOT / ".reference/r196/classes" / (row["class"] + ".class"))
        check(binary is not None and sha(binary) == row.get("class_sha256"), "Original R196 class identity: " + row["class"])
        if raw is not None:
            normalized = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
            check(sha(normalized.encode()) == row.get("decompiled_sha256"), "Normalized source identity: " + row["class"])
            lines = normalized.splitlines()
            for method in row.get("selected_methods", []):
                first, last = method.get("line", 0), method.get("end_line", 0)
                check(1 <= first <= last <= len(lines), "Method source line range: " + row["class"] + "." + method.get("name", "?"))
                segment = "\n".join(lines[first - 1:last]).encode()
                check(sha(segment) == method.get("method_sha256"), "Selected method bytes: " + row["class"] + "." + method.get("signature", "?"))
                counts["source_methods_checked"] += 1
        counts["source_classes_checked"] += 1
    for rule in rules:
        check(bool(rule.get("activation")) and bool(rule.get("formula")) and bool(rule.get("acceptance")), "Rule activation/formula/acceptance: " + rule.get("id", "?"))
        check(bool(rule.get("references")) and bool(rule.get("server_role")) and bool(rule.get("client_role")), "Rule citations and authority: " + rule.get("id", "?"))
        for reference in rule.get("references", []):
            indexed_class = indexed.get(reference.get("class"), {})
            fields = ("name", "arguments", "signature", "line", "end_line", "method_sha256")
            check(reference.get("file") == indexed_class.get("file") and any(
                all(method.get(key) == reference.get(key) for key in fields)
                for method in indexed_class.get("selected_methods", [])), "Rule citation resolves: " + rule.get("id", "?"))
            counts["rule_citations_checked"] += 1

    archive = data(ROOT / "MITE 1.6.4 R196.zip")
    resource_pack = data(ROOT / "MITE Resource Pack 1.6.4.zip")
    check(archive is not None and sha(archive) == ARCHIVE_SHA, "Pinned release ZIP checksum")
    check(resource_pack is not None and sha(resource_pack) == PACK_SHA, "Pinned resource ZIP checksum")
    check(assets.get("png_count") == len(assets.get("files", [])) == 4, "Four source sick textures")
    asset_rows = {row.get("entity_id"): row for row in assets.get("files", [])}
    check(set(asset_rows) == {"minecraft:" + species for species in SPECIES}, "Only four source sick-renderer species")
    payloads: dict[str, bytes] = {}
    try:
        with ZipFile(ROOT / "MITE Resource Pack 1.6.4.zip") as pack:
            names = pack.namelist()
            for identity, row in asset_rows.items():
                source = row["source"]
                check(names.count(source) == 1, "Unique original texture entry: " + source)
                original = pack.read(source)
                check(len(original) == row.get("bytes") and sha(original) == row.get("sha256"), "Original texture hash/size: " + source)
                check(original[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack_from(">II", original, 16) == (64, 32), "Source texture 64x32 PNG: " + source)
                copied = data(ROOT / "src/main/resources" / row["destination"])
                check(copied == original, "Unmodified imported texture: " + row["destination"])
                reference = next((item for item in spec.get("texture_assets", []) if item.get("entity") == identity), {})
                check(reference.get("source_zip_entry") == source and reference.get("sha256") == sha(original), "Independent source/asset manifest agreement: " + identity)
                payloads[row["destination"]] = original
                counts["source_textures_checked"] += 1
    except (OSError, BadZipFile, KeyError, struct.error) as error:
        check(False, "Cannot inspect original textures: " + str(error))

    client = read_json(args.client_audit)
    check(client.get("author") == "NingMeng123" and client.get("status") == "passed_transformed_bytecode_and_resource_audit_only", "Actual client bytecode/resource audit passed")
    for flag in ("minecraft_instance_created", "world_created", "opengl_or_render_invoked", "game_ui_automated", "visual_appearance_passed", "target_initialization_requested"):
        check(client.get(flag) is False, "Client audit must not claim UI/visual/GL execution: " + flag)
    check(client.get("renderer_count") == len(client.get("renderers", [])) == 4, "Four actual transformed renderer audits")
    check(client.get("texture_count") == len(client.get("textures", [])) == 4, "Four runtime-classpath texture audits")
    audited_mixins = set()
    for renderer in client.get("renderers", []):
        audited_mixins.add(renderer.get("mixin"))
        check(renderer.get("typed_handler_calls") == renderer.get("shared_helper_calls") == 1, "Typed renderer dispatch and common helper call: " + str(renderer.get("target")))
        raw = data(Path(renderer.get("export", "missing-renderer-export")))
        check(raw is not None and sha(raw) == renderer.get("export_sha256"), "Actual transformed export bytes: " + str(renderer.get("target")))
    check(audited_mixins == {"io.github.ningmeng123.mite.mixin." + name for name in MIXINS}, "Exactly the four expected renderer Mixins were transformed")
    runtime_textures = {row.get("path"): row for row in client.get("textures", [])}
    check(set(runtime_textures) == set(payloads), "Runtime classpath and original asset set agree")
    for path, original in payloads.items():
        check(runtime_textures.get(path, {}).get("sha256") == sha(original), "Audited runtime texture is original: " + path)

    config_path = ROOT / "src/main/resources/mite.mixins.json"
    config = read_json(config_path)
    compiled = {path.relative_to(args.classes).as_posix(): path for path in args.classes.rglob("*.class")}
    check(bool(compiled), "Current production compilation exists")
    for mixin in MIXINS:
        check(config.get("client", []).count(mixin) == 1 and mixin not in config.get("mixins", []), "Client-only registration: " + mixin)
        check(PACKAGE + "mixin/" + mixin.replace(".", "/") + ".class" in compiled, "Compiled sick texture Mixin: " + mixin)
    helper_path = PACKAGE + "entity/animal/LivestockHealth033.class"
    check(helper_path in compiled, "Common synced wellness texture helper compiled")
    if helper_path in compiled:
        check(b"net/minecraft/client/" not in compiled[helper_path].read_bytes(), "Common helper does not load client render classes on dedicated servers")
    jar_raw = data(args.jar)
    try:
        with ZipFile(args.jar) as jar:
            entries = jar.namelist()
            names = set(entries)
            check(len(names) == len(entries), "No duplicate packaged paths")
            check(jar.read("mite.mixins.json") == config_path.read_bytes(), "Packaged Mixin config matches current source")
            for path, original in payloads.items():
                check(path in names and jar.read(path) == original, "Packaged original disease texture: " + path)
            for path, current in compiled.items():
                check(path in names and jar.read(path) == current.read_bytes(), "Current/package bytecode equality: " + path)
                counts["production_bytecode_checked"] += 1
            check({path for path in names if path.endswith(".class")} == set(compiled), "Installable JAR contains exactly current production classes")
            forbidden = (".reference/", "tools/", "spec/", "org/junit/", "org/gradle/", "mezz/jei/", "net/minecraft/", "net/neoforged/", "org/spongepowered/")
            for path in names:
                check(not path.startswith(forbidden) and "/gametest/" not in path.lower() and not path.endswith((".java", ".zip", ".jar")), "No tests/research/dependencies bundled: " + path)
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8"))
            mods = metadata.get("mods", [])
            check(len(mods) == 1 and mods[0].get("modId") == "mite" and mods[0].get("authors") == "NingMeng123", "Packaged namespace/developer author")
            dependencies = metadata.get("dependencies", {}).get("mite", [])
            check({row.get("modId") for row in dependencies if row.get("type") == "required"} == {"minecraft", "neoforge"}, "Only Minecraft/NeoForge are required dependencies")
            counts["packaged_entries_checked"] = len(names)
    except (OSError, BadZipFile, KeyError, UnicodeError, tomllib.TOMLDecodeError) as error:
        check(False, "Cannot inspect installable JAR: " + str(error))

    junit_counts: Counter[str] = Counter()
    junit_suites = []
    xml_files = sorted(args.junit_directory.glob("TEST-*.xml"))
    check(bool(xml_files), "Actual JUnit XML reports exist")
    for path in xml_files:
        raw = data(path)
        if raw is None:
            continue
        try:
            suite = ET.fromstring(raw)
            values = {key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
            check(suite.tag == "testsuite" and values["tests"] == len(suite.findall("testcase")), "Actual JUnit case count: " + path.name)
            check(values["failures"] == values["errors"] == 0, "JUnit regression passed: " + path.name)
            junit_counts.update(values)
            junit_suites.append({"suite": suite.get("name"), "timestamp": suite.get("timestamp"), "file": str(path), **values})
        except (ET.ParseError, ValueError) as error:
            check(False, f"Invalid JUnit XML {path}: {error}")
    junit_counts["passed"] = junit_counts["tests"] - junit_counts["failures"] - junit_counts["errors"] - junit_counts["skipped"]
    check(any(row["suite"] == "io.github.ningmeng123.mite.entity.animal.LivestockRules032Test" and row["tests"] > 0 for row in junit_suites), "Source wellness boundary JUnit regressions included")
    game_result = {"file": str(args.game_log), "completed": None, "passed": None, "failed_required": None, "failed_optional": None}
    game_log = data(args.game_log)
    if game_log is not None:
        text = log_text(game_log)
        completions = list(re.finditer(r"=+\s+(\d+) GAME TESTS COMPLETE IN[^\r\n]*", text))
        check(bool(completions), "Headless GameTest log has a final completion count")
        if completions:
            final = completions[-1]
            segment = text[final.end():]
            passed = re.search(r"All (\d+) required tests passed", segment)
            required = re.search(r"(\d+) required tests failed", segment)
            optional = re.search(r"(\d+) optional tests failed", segment)
            completed = int(final.group(1))
            failed_required = int(required.group(1)) if required else 0
            failed_optional = int(optional.group(1)) if optional else 0
            game_result.update(completed=completed, passed=completed-failed_required-failed_optional,
                               failed_required=failed_required, failed_optional=failed_optional)
            check(passed is not None and int(passed.group(1)) == completed and completed > 0, "Actual GameTest final success/completion counts agree")
            check(failed_required == failed_optional == 0, "No required or optional GameTest failures")
            check("BUILD SUCCESSFUL" in segment and "BUILD FAILED" not in segment, "GameTest Gradle run completed successfully")

    result = {
        "author": "NingMeng123", "status": "passed" if not failures else "failed", "failures": failures,
        "counts": dict(counts), "source_counts": spec.get("counts"), "jar": str(args.jar),
        "jar_sha256": sha(jar_raw) if jar_raw is not None else None,
        "client_audit": str(args.client_audit), "client_audit_scope": "transformed bytecode and source resource wiring only",
        "junit": {**dict(junit_counts), "suites": junit_suites}, "gametest": game_result,
        "input_sha256": input_hashes, "game_ui_automated": False, "visual_appearance_passed": False,
        "user_manual_test": "pending",
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("status", "failures", "counts", "source_counts", "jar_sha256", "gametest")}, ensure_ascii=False))
    print(json.dumps({"junit": dict(junit_counts), "report": str(args.report)}, ensure_ascii=False))
    raise SystemExit(bool(failures))


if __name__ == "__main__":
    main()
