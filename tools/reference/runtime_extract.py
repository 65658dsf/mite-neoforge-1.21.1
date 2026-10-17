"""Opt-in, guarded Java 17..23 registration export. Developer: NingMeng123.

This companion executes reference class initializers in a separate read-only JVM.
It does NOT start Minecraft main, instantiate a client/server, or run installers.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

from analyze import ROOT, digest, download_dependencies, fetch, parse_class, write_json


def libraries(workspace):
    metadata = json.loads((workspace / "downloads/1.6.4.json").read_text(encoding="utf-8"))
    records = []
    for library in metadata["libraries"]:
        artifact = library.get("downloads", {}).get("artifact")
        if not artifact or "nightly" in library["name"]: continue
        path = workspace / "downloads/libraries" / artifact["path"]
        raw = fetch(artifact["url"],path,artifact["sha1"])
        records.append({"name":library["name"],"path":str(path),"url":artifact["url"],"sha1":artifact["sha1"],"sha256":digest(raw)})
    return records


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace",type=Path,default=ROOT / ".reference")
    parser.add_argument("--output",type=Path,default=ROOT / "reference-data")
    parser.add_argument("--java",required=True,help="Path to Java 17..23 (guard requires SecurityManager support)")
    parser.add_argument("--javac",help="Defaults to javac beside the selected java")
    args = parser.parse_args()
    workspace = args.workspace.resolve()
    java = Path(args.java).resolve()
    javac = Path(args.javac).resolve() if args.javac else java.with_name("javac.exe" if os.name == "nt" else "javac")
    version = subprocess.run([str(java),"-version"],capture_output=True,text=True,check=True)
    match = re.search(r'version "(\d+)',version.stderr+version.stdout)
    if not match or not 17 <= int(match.group(1)) <= 23:
        raise ValueError("Use Java 17..23; later JVMs cannot install the read-only guard")
    download_dependencies(workspace)
    provenance = libraries(workspace)
    helper = workspace / "runtime-helper"
    helper.mkdir(exist_ok=True)
    overlay = helper / "r196-overlay.jar"
    records = json.loads((workspace / "r196/class-details.json").read_text(encoding="utf-8"))
    with zipfile.ZipFile(overlay,"w",compression=zipfile.ZIP_DEFLATED) as archive:
        for record in sorted(records,key=lambda r:r["path"]):
            content = (workspace / "r196/classes" / record["path"]).read_bytes()
            if digest(content) != record["sha256"]: raise ValueError("Class changed after indexing: "+record["path"])
            entry = zipfile.ZipInfo(record["path"],(1980,1,1,0,0,0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry,content)
    source = ROOT / "tools/reference/RegistryExport.java"
    compile_result = subprocess.run([str(javac),"--release","17","-d",str(helper),str(source)],capture_output=True,text=True)
    (helper / "compile.log").write_text(compile_result.stdout+compile_result.stderr,encoding="utf-8")
    if compile_result.returncode: raise RuntimeError("Helper compilation failed; see .reference/runtime-helper/compile.log")
    classpath = os.pathsep.join([str(helper),str(overlay),str(workspace / "r196/resources"),str(workspace / "downloads/minecraft-1.6.4-client.jar"),*[p["path"] for p in provenance]])
    command = [str(java),"-Xmx1G","-Djava.awt.headless=true","-Djava.security.manager=allow","-cp",classpath,"RegistryExport",str(workspace)]
    run = subprocess.run(command,cwd=helper,capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=90)
    (helper / "export.stdout.log").write_text(run.stdout,encoding="utf-8")
    (helper / "export.stderr.log").write_text(run.stderr,encoding="utf-8")
    write_json(args.output / "runtime-provenance.json",{"author":"NingMeng123","java":str(java),"helper_sha256":digest(source.read_bytes()),"overlay_sha256":digest(overlay.read_bytes()),"class_count":len(records),"dependencies":provenance,"exit_code":run.returncode})
    if run.returncode:
        raise RuntimeError("Guarded registration export did not complete; inspect .reference/runtime-helper/export.stderr.log. No fallback removes the guard.")
    begin, end = "MITE_EXPORT_JSON_BEGIN\n", "\nMITE_EXPORT_JSON_END"
    data = json.loads(run.stdout.split(begin,1)[1].split(end,1)[0])
    by_name = {record["name"]:record for record in records}
    with zipfile.ZipFile(workspace / "downloads/minecraft-1.6.4-client.jar") as vanilla:
        for category in ["items","blocks","materials","crafting_recipes","unregistered_static_items","entities","enchantments"]:
            for row in data[category]:
                info = by_name.get(row["class"])
                origin = "r196_overlay"
                if info is None:
                    info = parse_class(vanilla.read(row["class"].replace(".","/")+".class"))
                    origin = "minecraft_1_6_4_original"
                row["source"] = {"class":row["class"],"source_file":info["source_file"],"class_sha256":info["sha256"],"origin":origin}
    data["archive_sha256"] = json.loads((args.output / "summary.json").read_text(encoding="utf-8"))["archive_sha256"]
    data["limitations"] = ["A guarded registry snapshot is not a complete gameplay specification.","Dynamic recipes remain algorithmic and require explicit semantic review.","Furnace sand batching and heat-dependent output are additional runtime rules beyond the registry map.","Item scalar keys retain declaring-class and original field names; interpretations require source confirmation."]
    write_json(args.output / "runtime-registry.json",data)
    print(json.dumps({"output":str(args.output / "runtime-registry.json"),"counts":{k:len(v) for k,v in data.items() if isinstance(v,list)},"registration_error_message":data.get("registration_error_message")},ensure_ascii=False,indent=2))


if __name__ == "__main__": main()
