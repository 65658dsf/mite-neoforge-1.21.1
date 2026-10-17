"""Reproducible, standard-library-only R196 reference analysis. Author: NingMeng123.

Never executes the supplied archive. Third-party binaries and decompiled sources
are kept beneath .reference and must not be distributed in the mod source tree.
"""
from __future__ import annotations

import argparse
import collections
import csv
import hashlib
import io
import json
import math
from pathlib import Path, PurePosixPath
import re
import stat
import struct
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
CFR_URL = "https://www.benf.org/other/cfr/cfr-0.152.jar"
CFR_MD5 = "8a85ada8cec494121246805a5562b82b"
CFR_SHA256 = "f686e8f3ded377d7bc87d216a90e9e9512df4156e75b06c655a16648ae8765b2"
MAX_ENTRY = 128 * 1024 * 1024
MAX_TOTAL = 512 * 1024 * 1024
RESERVED = {"con", "prn", "aux", "nul", *[f"com{i}" for i in range(1, 10)], *[f"lpt{i}" for i in range(1, 10)]}


def digest(data: bytes, algorithm="sha256") -> str:
    return hashlib.new(algorithm, data).hexdigest()


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def safe_name(name: str) -> PurePosixPath:
    """Reject platform-independent traversal and Windows path ambiguities."""
    if not name or "\\" in name or "\x00" in name or ":" in name or name.startswith("/"):
        raise ValueError(f"Unsafe archive path: {name!r}")
    components = name.rstrip("/").split("/")
    if any(p in {"", ".", ".."} or p.rstrip(" .") != p or
           p.split(".")[0].lower() in RESERVED for p in components):
        raise ValueError(f"Unsafe archive path: {name!r}")
    return PurePosixPath(*components)


def validated_entries(z: zipfile.ZipFile):
    entries = z.infolist()
    total = 0
    seen = set()
    for item in entries:
        p = safe_name(item.filename)
        key = str(p).casefold()
        if key in seen:
            raise ValueError(f"Duplicate/case-colliding archive path: {item.filename}")
        seen.add(key)
        mode = item.external_attr >> 16
        if stat.S_ISLNK(mode):
            raise ValueError(f"Symbolic link in archive: {item.filename}")
        if item.file_size > MAX_ENTRY:
            raise ValueError(f"Archive entry exceeds size limit: {item.filename}")
        total += item.file_size
        if total > MAX_TOTAL:
            raise ValueError("Archive exceeds total uncompressed size limit")
    return entries


def safe_write(root: Path, name: str, data: bytes):
    relative = safe_name(name)
    root = root.resolve()
    target = root.joinpath(*relative.parts)
    # Also reject pre-existing symlinks that redirect an otherwise safe entry.
    if not target.resolve().is_relative_to(root):
        raise ValueError(f"Archive target leaves destination: {name}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)


def extract_archive(archive: Path, workspace: Path):
    """Extract classes, text metadata and resources, deliberately skipping executables."""
    records = []
    languages = []
    with zipfile.ZipFile(archive) as z:
        entries = validated_entries(z)
        for entry in entries:
            if entry.is_dir():
                continue
            data = z.read(entry)
            record = {"path": entry.filename, "size": len(data), "sha256": digest(data), "action": "not_extracted"}
            if "/class/" in entry.filename and entry.filename.endswith(".class"):
                relative = entry.filename.split("/class/", 1)[1]
                safe_write(workspace / "r196/classes", relative, data)
                record["action"] = "class"
            elif entry.filename.endswith(".zip"):
                with zipfile.ZipFile(io.BytesIO(data)) as nested:
                    for resource in validated_entries(nested):
                        if resource.is_dir():
                            continue
                        content = nested.read(resource)
                        safe_write(workspace / "r196/resources", resource.filename, content)
                        if "/lang/" in resource.filename or resource.filename.endswith(".lang"):
                            rows = []
                            for line in content.decode("utf-8-sig", errors="replace").splitlines():
                                if line and not line.lstrip().startswith("#") and "=" in line:
                                    key, value = line.split("=", 1)
                                    rows.append({"key": key, "value": value})
                            languages.append({"path": resource.filename, "sha256": digest(content), "entries": rows})
                    record["nested_entries"] = len(nested.infolist())
                record["action"] = "resource_pack"
            elif entry.filename.endswith((".txt", ".json")):
                safe_write(workspace / "r196/metadata", entry.filename.split("/", 1)[-1], data)
                record["action"] = "metadata"
            records.append(record)
    return records, languages


class Reader:
    def __init__(self, data: bytes):
        self.data, self.offset = data, 0

    def read(self, n: int) -> bytes:
        if n < 0 or self.offset + n > len(self.data):
            raise ValueError(f"Truncated class at byte {self.offset} (need {n})")
        out = self.data[self.offset:self.offset+n]
        self.offset += n
        return out

    def u1(self): return self.read(1)[0]
    def u2(self): return struct.unpack(">H", self.read(2))[0]
    def u4(self): return struct.unpack(">I", self.read(4))[0]


def modified_utf8(data: bytes):
    # JVM strings use modified UTF-8, including encoded NUL and surrogate pairs.
    value = data.replace(b"\xc0\x80", b"\0").decode("utf-8", errors="surrogatepass")
    return value.encode("utf-16-le", errors="surrogatepass").decode("utf-16-le", errors="replace")


def json_number(value):
    return value if not isinstance(value, float) or math.isfinite(value) else str(value)


class ClassFile:
    def __init__(self, data: bytes):
        self.reader = Reader(data)
        self.pool = [None]
        self.data = data

    def cp(self, index):
        if index <= 0 or index >= len(self.pool) or self.pool[index] is None:
            raise ValueError(f"Invalid constant pool reference {index}")
        return self.pool[index]

    def utf(self, index):
        value = self.cp(index)
        if value["tag"] != 1:
            raise ValueError(f"Expected UTF8 constant at {index}")
        return value["value"]

    def class_name(self, index):
        if not index: return None
        value = self.cp(index)
        if value["tag"] != 7: raise ValueError("Expected Class constant")
        return self.utf(value["index"])

    def constant(self, index):
        value = self.cp(index)
        tag = value["tag"]
        if tag in {1, 3, 4, 5, 6}: return value["value"]
        if tag == 8: return self.utf(value["index"])
        if tag == 7: return {"class": self.class_name(index)}
        if tag in {9, 10, 11}:
            nt = self.cp(value["name_type"])
            return {"kind": {9: "field", 10: "method", 11: "interface_method"}[tag],
                    "owner": self.class_name(value["class"]), "name": self.utf(nt["name"]),
                    "descriptor": self.utf(nt["descriptor"])}
        if tag in {17, 18}:
            nt = self.cp(value["name_type"])
            return {"kind": "dynamic", "bootstrap": value["bootstrap"], "name": self.utf(nt["name"]), "descriptor": self.utf(nt["descriptor"])}
        return {"constant_pool_index": index, **value}

    def attributes(self, reader):
        attrs = []
        for _ in range(reader.u2()):
            name = self.utf(reader.u2())
            raw = reader.read(reader.u4())
            attrs.append((name, raw))
        return attrs

    def code(self, raw):
        r = Reader(raw)
        maximum_stack, maximum_locals = r.u2(), r.u2()
        code = r.read(r.u4())
        exceptions = []
        for _ in range(r.u2()):
            start, end, handler, caught = r.u2(), r.u2(), r.u2(), r.u2()
            exceptions.append({"start": start, "end": end, "handler": handler, "type": self.class_name(caught)})
        lines = []
        for name, content in self.attributes(r):
            if name == "LineNumberTable":
                lr = Reader(content)
                lines = [{"offset": lr.u2(), "line": lr.u2()} for _ in range(lr.u2())]
        refs, literals = self.instructions(code, lines)
        return {"max_stack": maximum_stack, "max_locals": maximum_locals, "length": len(code), "sha256": digest(code),
                "lines": lines, "references": refs, "literals": literals, "exception_handlers": exceptions}

    def instructions(self, code, lines):
        # JVM opcodes have fixed operand widths except tableswitch, lookupswitch, wide.
        widths = {16: 1, 17: 2, 18: 1, 19: 2, 20: 2, 132: 2, 169: 1, 188: 1, 197: 3, 200: 4, 201: 4}
        widths.update({i: 1 for i in [*range(21, 26), *range(54, 59)]})
        widths.update({i: 2 for i in [*range(153, 169), *range(178, 185), 187, 189, 192, 193, 198, 199]})
        widths.update({185: 4, 186: 4})
        refs, literals = [], []
        r = Reader(code)
        ordered_lines = sorted(lines, key=lambda x: x["offset"])
        def location(pc):
            candidates = [v["line"] for v in ordered_lines if v["offset"] <= pc]
            return {"offset": pc, "source_line": candidates[-1] if candidates else None}
        while r.offset < len(code):
            pc, opcode = r.offset, r.u1()
            if opcode > 201: raise ValueError(f"Invalid bytecode opcode {opcode} at {pc}")
            if opcode in {170, 171}:
                r.read((-r.offset) % 4)
                r.read(4)  # default branch
                if opcode == 170:
                    low, high = struct.unpack(">ii", r.read(8))
                    count = high - low + 1
                    if count < 0: raise ValueError("Invalid tableswitch")
                    r.read(count * 4)
                else:
                    count = struct.unpack(">i", r.read(4))[0]
                    if count < 0: raise ValueError("Invalid lookupswitch")
                    r.read(count * 8)
                continue
            if opcode == 196:
                widened = r.u1()
                if widened not in {21,22,23,24,25,54,55,56,57,58,132,169}:
                    raise ValueError(f"Invalid wide opcode {widened}")
                r.read(4 if widened == 132 else 2)
                continue
            operands = r.read(widths.get(opcode, 0))
            if opcode in {18, 19, 20, *range(178, 188), 189, 192, 193, 197}:
                index = operands[0] if opcode == 18 else int.from_bytes(operands[:2], "big")
                value = self.constant(index)
                refs.append({**location(pc), "opcode": opcode, "pool_index": index, "target": value})
                if opcode in {18, 19, 20}: literals.append({**location(pc), "value": value})
            elif 2 <= opcode <= 8:
                literals.append({**location(pc), "value": opcode - 3})
            elif 9 <= opcode <= 15:
                literals.append({**location(pc), "value": (opcode - 9 if opcode <= 10 else opcode - 11 if opcode <= 13 else opcode - 14)})
            elif opcode in {16, 17}:
                literals.append({**location(pc), "value": int.from_bytes(operands, "big", signed=True)})
        return refs, literals

    def members(self):
        result = []
        r = self.reader
        for _ in range(r.u2()):
            member = {"access": r.u2(), "name": self.utf(r.u2()), "descriptor": self.utf(r.u2())}
            attrs = self.attributes(r)
            member["attributes"] = [n for n, _ in attrs]
            for name, raw in attrs:
                if name == "Code": member["code"] = self.code(raw)
                elif name == "ConstantValue": member["constant_value"] = self.constant(Reader(raw).u2())
                elif name == "Signature": member["signature"] = self.utf(Reader(raw).u2())
            result.append(member)
        return result

    def parse(self):
        r = self.reader
        if r.u4() != 0xCAFEBABE: raise ValueError("Invalid class magic")
        minor, major = r.u2(), r.u2()
        count = r.u2()
        while len(self.pool) < count:
            tag = r.u1()
            value = {"tag": tag}
            if tag == 1: value["value"] = modified_utf8(r.read(r.u2()))
            elif tag in {3,4,5,6}:
                fmt, length = {3: (">i",4), 4: (">f",4), 5: (">q",8), 6: (">d",8)}[tag]
                value["value"] = json_number(struct.unpack(fmt,r.read(length))[0])
            elif tag in {7,8,16,19,20}: value["index"] = r.u2()
            elif tag in {9,10,11}: value.update({"class": r.u2(), "name_type": r.u2()})
            elif tag == 12: value.update({"name": r.u2(), "descriptor": r.u2()})
            elif tag == 15: value.update({"reference_kind": r.u1(), "reference_index": r.u2()})
            elif tag in {17,18}: value.update({"bootstrap": r.u2(), "name_type": r.u2()})
            else: raise ValueError(f"Unsupported constant pool tag {tag}")
            self.pool.append(value)
            if tag in {5,6}: self.pool.append(None)
        if len(self.pool) != count: raise ValueError("Invalid constant pool size")
        result = {"sha256": digest(self.data), "minor_version": minor, "major_version": major,
                  "access": r.u2(), "name": self.class_name(r.u2()), "super": self.class_name(r.u2())}
        result["interfaces"] = [self.class_name(r.u2()) for _ in range(r.u2())]
        result["fields"], result["methods"] = self.members(), self.members()
        attrs = self.attributes(r)
        result["attributes"] = [n for n, _ in attrs]
        result["source_file"] = next((self.utf(Reader(raw).u2()) for name, raw in attrs if name == "SourceFile"), None)
        result["constants"] = [{"index": i, "tag": v["tag"], "value": self.constant(i)}
                               for i,v in enumerate(self.pool) if v and v["tag"] in {3,4,5,6,8}]
        result["pool_references"] = [{"index": i, **self.constant(i)} for i,v in enumerate(self.pool) if v and v["tag"] in {9,10,11}]
        if r.offset != len(self.data): raise ValueError("Trailing data in class")
        return result


def parse_class(data: bytes):
    return ClassFile(data).parse()


MODULE_PATTERNS = [
    ("manufacturing", r"Recipe|Craft|Furnace|Anvil|Workbench|Repair|SlotResult"),
    ("player_survival", r"Player|FoodStats|Nutrition|Experience|Potion|Skill"),
    ("agriculture", r"Crop|Plant|Farmland|Stem|Seed|Fertilizer|Animal|Cow|Pig|Chicken|Sheep|Horse|Wolf|Ocelot|Food|Meat|Bread|Soup|Milk"),
    ("world_progression", r"World|Chunk|Biome|Gen|Dimension|Portal|Village|Structure|Fortress|Stronghold|Ore|Map|Weather|Moon|Achievement"),
    ("combat_entities", r"Entity|AI|Path|Damage|Armor|Sword|Bow|Arrow|Enchantment|Combat|Spawner"),
    ("items_blocks", r"Item|Block|Material|Tool|EnumQuality|EnumEquipment|StepSound"),
    ("interface_rendering", r"Gui|Render|Texture|Model|Font|Sound|Music|Mouse|Keyboard|KeyBinding|Color|OpenGl|Tessellator|Shader|Particle|Effect|Icon"),
    ("network_storage", r"Packet|Net|Server|NBT|Save|Region|Data|Stat|Command|Score|Container|Inventory|Slot"),
]


def classify(source):
    if re.match(r"Packet|NetHandler|Network", source or ""): return "network_storage"
    if re.match(r"Gui|Render", source or ""): return "interface_rendering"
    for module, pattern in MODULE_PATTERNS:
        if re.search(pattern, source or ""): return module
    return "infrastructure_or_unclassified"


def fetch(url: str, target: Path, expected=None, algorithm="sha1"):
    if target.exists() and (expected is None or digest(target.read_bytes(), algorithm) == expected):
        return target.read_bytes()
    request = urllib.request.Request(url, headers={"User-Agent": "MITE-reference-analysis/1.0 NingMeng123"})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read(MAX_TOTAL + 1)
    if len(data) > MAX_TOTAL: raise ValueError("Download exceeds size limit")
    if expected and digest(data, algorithm) != expected:
        raise ValueError(f"Checksum mismatch for {url}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return data


def download_dependencies(workspace: Path):
    manifest_raw = fetch(MANIFEST_URL, workspace / "downloads/version_manifest_v2.json")
    version = next(v for v in json.loads(manifest_raw)["versions"] if v["id"] == "1.6.4")
    metadata_raw = fetch(version["url"], workspace / "downloads/1.6.4.json", version["sha1"])
    client = json.loads(metadata_raw)["downloads"]["client"]
    client_raw = fetch(client["url"], workspace / "downloads/minecraft-1.6.4-client.jar", client["sha1"])
    cfr_raw = fetch(CFR_URL, workspace / "downloads/cfr-0.152.jar", CFR_SHA256, "sha256")
    sources = [
        {"name": "Mojang version manifest", "url": MANIFEST_URL, "sha256": digest(manifest_raw)},
        {"name": "Minecraft 1.6.4 version metadata", "url": version["url"], "sha1": digest(metadata_raw,"sha1"), "sha256": digest(metadata_raw)},
        {"name": "Minecraft 1.6.4 official client", "url": client["url"], "sha1": digest(client_raw,"sha1"), "sha256": digest(client_raw)},
        {"name": "CFR 0.152", "url": CFR_URL, "publisher_md5": CFR_MD5, "sha256": digest(cfr_raw)},
    ]
    write_json(workspace / "downloads/provenance.json", sources)
    return sources


def compare_class(modified, original):
    if original is None: return {"status": "added_class", "name": modified["name"]}
    result = {"name": modified["name"], "status": "identical" if modified["sha256"] == original["sha256"] else "modified",
              "original_source_file": original["source_file"], "original_sha256": original["sha256"]}
    for kind in ["fields", "methods"]:
        before = {(m["name"], m["descriptor"]): m for m in original[kind]}
        after = {(m["name"], m["descriptor"]): m for m in modified[kind]}
        name = lambda k: {"name": k[0], "descriptor": k[1]}
        result[kind] = {"added": [name(k) for k in sorted(after.keys() - before.keys())],
                        "removed": [name(k) for k in sorted(before.keys() - after.keys())],
                        "changed": [name(k) for k in sorted(before.keys() & after.keys()) if before[k] != after[k]]}
    return result


def index(workspace: Path, output: Path, archive: Path, records, languages):
    classes = []
    original_path = workspace / "downloads/minecraft-1.6.4-client.jar"
    vanilla = zipfile.ZipFile(original_path) if original_path.exists() else None
    original_names = set(vanilla.namelist()) if vanilla else set()
    differences = []
    # Work only from this input's manifest, never include stale files left by an
    # earlier reference archive or unrelated classes in the workspace.
    class_paths = [workspace / "r196/classes" / r["path"].split("/class/", 1)[1]
                   for r in records if r["action"] == "class"]
    for path in sorted(class_paths):
        parsed = parse_class(path.read_bytes())
        parsed["module"] = classify(parsed["source_file"])
        parsed["classification_basis"] = "source_file_name_heuristic_requires_semantic_review"
        parsed["path"] = path.relative_to(workspace / "r196/classes").as_posix()
        classes.append(parsed)
        if vanilla:
            match = parsed["name"] + ".class"
            original = parse_class(vanilla.read(match)) if match in original_names else None
            differences.append(compare_class(parsed, original))
    if vanilla: vanilla.close()
    output.mkdir(parents=True, exist_ok=True)
    # Detailed metadata can be regenerated and is large; keep it with local reference.
    write_json(workspace / "r196/class-details.json", classes)
    write_json(output / "class-differences.json", differences)
    write_json(output / "archive-manifest.json", {"archive": archive.name, "sha256": digest(archive.read_bytes()), "entries": records})
    write_json(output / "language-index.json", [{"path": l["path"], "sha256": l["sha256"], "entry_count": len(l["entries"])} for l in languages])
    write_json(workspace / "r196/languages.json", languages)
    mapping = []
    for c in classes:
        mapping.append({"class": c["name"], "source_file": c["source_file"], "module": c["module"], "sha256": c["sha256"],
                        "fields": len(c["fields"]), "methods": len(c["methods"]),
                        "decompiled_path": c["name"].split("$")[0] + ".java", "semantic_status": "unreviewed"})
    write_json(output / "class-index.json", mapping)
    with (output / "class-index.csv").open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(mapping[0]) if mapping else ["class"])
        w.writeheader()
        w.writerows(mapping)
    summary = {"author": "NingMeng123", "archive": archive.name, "archive_sha256": digest(archive.read_bytes()), "class_count": len(classes),
               "source_file_count": sum(c["source_file"] is not None for c in classes), "classification": dict(sorted(collections.Counter(c["module"] for c in classes).items())),
               "comparison": dict(sorted(collections.Counter(d["status"] for d in differences).items())), "language_file_count": len(languages),
               "semantic_review_complete": False,
               "limitations": ["Module labels are filename heuristics, not verified behavior.", "Same obfuscated class name is the comparison key; descriptor and constant-pool renumbering can inflate member changes.", "Detailed class metadata includes original source line tables; decompiled output lines differ.", "Constant-pool references do not prove an execution path is enabled; method Code references identify concrete instructions only."]}
    write_json(output / "summary.json", summary)
    return classes, summary


def decompile(workspace: Path, java: str):
    cfr = workspace / "downloads/cfr-0.152.jar"
    if not cfr.exists() or digest(cfr.read_bytes()) != CFR_SHA256:
        raise ValueError("CFR checksum mismatch or missing; run --download to obtain the pinned official release")
    jar = workspace / "r196/reference-classes.jar"
    class_records = json.loads((workspace / "r196/class-details.json").read_text(encoding="utf-8"))
    with zipfile.ZipFile(jar, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for record in sorted(class_records, key=lambda r: r["path"]):
            path = workspace / "r196/classes" / record["path"]
            data = path.read_bytes()
            if digest(data) != record["sha256"]:
                raise ValueError(f"Reference class changed after indexing: {record['path']}")
            info = zipfile.ZipInfo(path.relative_to(workspace / "r196/classes").as_posix(), (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    destination = workspace / "r196/decompiled"
    destination.mkdir(parents=True, exist_ok=True)
    command = [java, "-Xmx2G", "-jar", str(workspace / "downloads/cfr-0.152.jar"), str(jar),
               "--outputdir", str(destination), "--silent", "true", "--caseinsensitivefs", "true",
               "--extraclasspath", str(workspace / "downloads/minecraft-1.6.4-client.jar")]
    with (workspace / "r196/cfr.log").open("w", encoding="utf-8") as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=False)
    if result.returncode:
        raise RuntimeError(f"CFR failed with exit {result.returncode}; inspect .reference/r196/cfr.log")
    summary_path = destination / "summary.txt"
    summary_text = summary_path.read_text(encoding="utf-8") if summary_path.exists() else ""
    warnings = [line.strip() for line in summary_text.splitlines() if "Unable to" in line or "Exception" in line or "failed" in line]
    expected = {r["name"].split("$")[0] + ".java" for r in class_records}
    missing = sorted(p for p in expected if not (destination / p).exists())
    return {"tool": "CFR 0.152", "exit_code": result.returncode,
            "java_file_count": len(expected) - len(missing), "expected_java_file_count": len(expected), "missing_outputs": missing, "log": ".reference/r196/cfr.log",
            "warnings": warnings, "summary": ".reference/r196/decompiled/summary.txt",
            "note": "Nested classes may be emitted inside their enclosing Java file; exit 0 does not guarantee every method was fully structured."}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--archive", type=Path, default=ROOT / "MITE 1.6.4 R196.zip")
    p.add_argument("--workspace", type=Path, default=ROOT / ".reference")
    p.add_argument("--output", type=Path, default=ROOT / "reference-data")
    p.add_argument("--download", action="store_true", help="Fetch verified official client and CFR")
    p.add_argument("--decompile", action="store_true", help="Run verified CFR on reference classes")
    p.add_argument("--java", default="java")
    args = p.parse_args()
    records, languages = extract_archive(args.archive, args.workspace)
    print(f"Extracted {sum(r['action'] == 'class' for r in records)} classes; skipped installer executables.", flush=True)
    if args.download:
        sources = download_dependencies(args.workspace)
        write_json(args.output / "tool-provenance.json", sources)
        print("Official client and CFR downloads verified.", flush=True)
    _, summary = index(args.workspace, args.output, args.archive, records, languages)
    print(json.dumps(summary, ensure_ascii=False), flush=True)
    if args.decompile:
        result = decompile(args.workspace, args.java)
        write_json(args.output / "decompilation.json", result)
        print(json.dumps(result), flush=True)


if __name__ == "__main__": main()
