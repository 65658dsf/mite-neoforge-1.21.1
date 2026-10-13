"""Export ore fixtures by running guarded R196 bytecode only. Developer: NingMeng123."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / ".reference/runtime-helper/ore-fixtures.json")
    parser.add_argument("--verify-spec", action="store_true", help="Compare freshly executed original bytecode with saved source_oracle")
    args = parser.parse_args()
    java = args.java.resolve()
    javac = java.with_name("javac.exe" if os.name == "nt" else "javac")
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    match = re.search(r'version "(\d+)', version.stdout + version.stderr)
    if not match or not 17 <= int(match.group(1)) <= 23:
        raise ValueError("Read-only Java guard requires Java 17..23")
    workspace = ROOT / ".reference"
    helper = workspace / "runtime-helper"
    overlay = helper / "r196-overlay.jar"
    index = json.loads((ROOT / "reference-data/class-index.json").read_text(encoding="utf-8"))
    with zipfile.ZipFile(overlay) as archive:
        for record in index:
            path = record["class"].replace(".", "/") + ".class"
            if hashlib.sha256(archive.read(path)).hexdigest() != record["sha256"]:
                raise ValueError("Reference overlay changed: " + path)
    metadata = json.loads((workspace / "downloads/1.6.4.json").read_text(encoding="utf-8"))
    libraries = [workspace / "downloads/libraries" / library["downloads"]["artifact"]["path"]
                 for library in metadata["libraries"]
                 if library.get("downloads", {}).get("artifact") and "nightly" not in library["name"]]
    classpath = os.pathsep.join(map(str, [helper, overlay, workspace / "r196/resources",
                                         workspace / "downloads/minecraft-1.6.4-client.jar", *libraries]))
    compiled = subprocess.run([str(javac), "--release", "17", "-cp", classpath, "-d", str(helper),
                               str(ROOT / "tools/reference/RegistryExport.java"),
                               str(ROOT / "tools/reference/OreRulesExport.java")],
                              capture_output=True, text=True, encoding="utf-8", errors="replace")
    (helper / "ore-compile.log").write_text(compiled.stdout + compiled.stderr, encoding="utf-8")
    if compiled.returncode:
        raise RuntimeError("Compilation failed; see ore-compile.log")
    run = subprocess.run([str(java), "-Xmx1G", "-Djava.awt.headless=true", "-Djava.security.manager=allow",
                          "-cp", classpath, "OreRulesExport", str(ROOT)], cwd=helper,
                         capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
    (helper / "ore-oracle.stdout.log").write_text(run.stdout, encoding="utf-8")
    (helper / "ore-oracle.stderr.log").write_text(run.stderr, encoding="utf-8")
    if run.returncode:
        raise RuntimeError("Reference export failed; see ore-oracle.stderr.log")
    fixtures = json.loads(run.stdout.split("ORE_FIXTURE_JSON_BEGIN\n", 1)[1].split("\nORE_FIXTURE_JSON_END", 1)[0])
    fixtures.update(mode="guarded_reference_bytecode_ore_sampling_and_growth", game_main_invoked=False,
                    archive_sha256="fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3",
                    helper_sha256=hashlib.sha256((ROOT / "tools/reference/OreRulesExport.java").read_bytes()).hexdigest())
    fixtures["growth_world"] = {"loaded_xz_inclusive": [-16, 31], "loaded_y_inclusive": [0, 255],
                                "initial_block": "stone", "initial_block_id": 1,
                                "replacement_block_id": 202, "must_be_supported": False, "is_dirt": False,
                                "output_xz_inclusive": [-8, 24], "output_y_inclusive": [16, 48],
                                "fresh_world_per_case": True}
    for case in fixtures["growth_cases"]:
        if case["grown"] != len(case["positions"]):
            raise ValueError("Output scan omitted grown blocks")
    args.output.write_text(json.dumps(fixtures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.verify_spec:
        saved = json.loads((ROOT / "spec/ores-023.json").read_text(encoding="utf-8"))["source_oracle"]
        if saved != fixtures:
            raise ValueError("Saved source_oracle differs from original bytecode export")
    print(json.dumps({"output": str(args.output), "height_cases": len(fixtures["height_cases"]),
                      "size_cases": len(fixtures["size_cases"]), "growth_cases": len(fixtures["growth_cases"]),
                      "verified_spec": args.verify_spec}))


if __name__ == "__main__":
    main()
