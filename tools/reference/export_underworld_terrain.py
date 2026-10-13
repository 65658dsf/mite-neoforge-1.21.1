"""Export R196 terrain fixtures under the read-only Java guard. Developer: NingMeng123.

Uses already extracted archives/libraries; never starts Minecraft or downloads dependencies.
"""
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
    parser.add_argument("--java", required=True, type=Path, help="Java 17..23 executable")
    parser.add_argument("--workspace", type=Path, default=ROOT / ".reference")
    parser.add_argument("--output", type=Path, default=ROOT / "spec/r196-underworld-terrain-fixtures.json")
    args = parser.parse_args()
    java = args.java.resolve()
    javac = java.with_name("javac.exe" if os.name == "nt" else "javac")
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    match = re.search(r'version "(\d+)', version.stdout + version.stderr)
    if not match or not 17 <= int(match.group(1)) <= 23:
        raise ValueError("The read-only Java guard requires Java 17..23")
    workspace = args.workspace.resolve()
    helper = workspace / "runtime-helper"
    overlay = helper / "r196-overlay.jar"
    # Reject a stale or modified overlay before executing reference class initializers.
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
    compile_result = subprocess.run([str(javac), "--release", "17", "-cp", classpath, "-d", str(helper),
                                     str(ROOT / "tools/reference/RegistryExport.java"),
                                     str(ROOT / "tools/reference/UnderworldTerrainExport.java")],
                                    capture_output=True, text=True, encoding="utf-8", errors="replace")
    (helper / "underworld-compile.log").write_text(compile_result.stdout + compile_result.stderr, encoding="utf-8")
    if compile_result.returncode:
        raise RuntimeError("Reference helper compilation failed; see underworld-compile.log")
    run = subprocess.run([str(java), "-Xmx1G", "-Djava.awt.headless=true", "-Djava.security.manager=allow",
                          "-cp", classpath, "UnderworldTerrainExport", str(ROOT)], cwd=helper,
                         capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
    (helper / "underworld-oracle.stdout.log").write_text(run.stdout, encoding="utf-8")
    (helper / "underworld-oracle.stderr.log").write_text(run.stderr, encoding="utf-8")
    if run.returncode:
        raise RuntimeError("Read-only reference export failed; see underworld-oracle.stderr.log")
    data = json.loads(run.stdout.split("UNDERWORLD_FIXTURE_JSON_BEGIN\n", 1)[1]
                      .split("\nUNDERWORLD_FIXTURE_JSON_END", 1)[0])
    data["mode"] = "guarded_reference_bytecode_terrain_only"
    data["game_main_invoked"] = False
    data["block_order"] = "(local_x * 16 + local_z) * 256 + world_y"
    data["mantle_code"] = 255
    data["helper_sha256"] = hashlib.sha256((ROOT / "tools/reference/UnderworldTerrainExport.java").read_bytes()).hexdigest()
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"cases": len(data["cases"]), "output": str(args.output), "mode": data["mode"]}))


if __name__ == "__main__":
    main()
