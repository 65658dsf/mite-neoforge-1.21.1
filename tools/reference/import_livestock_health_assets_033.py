"""Import the four R196 sick livestock skins without changing any bytes.

Author: NingMeng123. Original MITE artwork/source: Avernite.
Run normally to import; --verify checks the existing files and manifest without
writing. --verify --jar <path> also verifies the packaged texture bytes.
Only the Python standard library is required.
"""

import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile
import zlib


ROOT = Path(__file__).resolve().parents[2]
PACK_NAME = "MITE Resource Pack 1.6.4.zip"
PACK_SHA256 = "4a9bb9f7ab5afee7c0e4e9f57f3b81ff185a0c619d29036b8c2f1ece7450e67a"
MANIFEST_PATH = ROOT / "spec/livestock-health-assets-033.json"
SPECIES = ("chicken", "cow", "pig", "sheep")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def png_dimensions(data, label):
    """Validate the PNG structure/CRCs and read dimensions without decoding it."""
    if data[:8] != PNG_SIGNATURE:
        raise ValueError(f"{label}: invalid PNG signature")
    offset = 8
    dimensions = None
    saw_idat = False
    saw_iend = False
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError(f"{label}: truncated PNG chunk")
        size = struct.unpack_from(">I", data, offset)[0]
        end = offset + 12 + size
        if end > len(data):
            raise ValueError(f"{label}: truncated PNG payload")
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + size]
        crc = struct.unpack_from(">I", data, offset + 8 + size)[0]
        if zlib.crc32(kind + payload) & 0xffffffff != crc:
            raise ValueError(f"{label}: incorrect {kind!r} CRC")
        if offset == 8 and kind != b"IHDR":
            raise ValueError(f"{label}: IHDR is not the first chunk")
        if kind == b"IHDR":
            if dimensions is not None or size != 13:
                raise ValueError(f"{label}: invalid or duplicate IHDR")
            dimensions = struct.unpack(">II", payload[:8])
        elif kind == b"IDAT":
            saw_idat = True
        elif kind == b"IEND":
            if size != 0 or end != len(data):
                raise ValueError(f"{label}: invalid IEND or trailing bytes")
            saw_iend = True
            break
        offset = end
    if dimensions != (64, 32) or not saw_idat or not saw_iend:
        raise ValueError(f"{label}: expected a complete 64x32 PNG, got {dimensions}")
    return dimensions


def source_files():
    pack = ROOT / PACK_NAME
    actual_pack_hash = sha256(pack.read_bytes())
    if actual_pack_hash != PACK_SHA256:
        raise ValueError(f"{pack}: source pack hash changed: {actual_pack_hash}")
    files = []
    payloads = {}
    with zipfile.ZipFile(pack) as archive:
        names = archive.namelist()
        for species in SPECIES:
            source = f"assets/minecraft/textures/entity/{species}/sick.png"
            if names.count(source) != 1:
                raise ValueError(f"{source}: expected exactly one ZIP entry")
            data = archive.read(source)
            width, height = png_dimensions(data, source)
            destination = f"assets/mite/textures/entity/livestock/{species}_sick.png"
            files.append({
                "entity_id": f"minecraft:{species}",
                "source": source,
                "destination": destination,
                "sha256": sha256(data),
                "bytes": len(data),
                "width": width,
                "height": height,
                "transformation": "none; original ZIP entry bytes",
            })
            payloads[destination] = data
    manifest = {
        "schema_version": 1,
        "author": "NingMeng123",
        "source_creator": "Avernite",
        "source_project": "Minecraft Is Too Easy (MITE)",
        "source_website": "https://avernite.ca/MITE/",
        "source_pack": PACK_NAME,
        "source_pack_sha256": PACK_SHA256,
        "png_count": len(files),
        "import_policy": "Copy confirmed R196 sick livestock skins byte for byte; no image conversion or redraw.",
        "files": files,
    }
    return manifest, payloads


def verify(manifest, payloads, jar_path=None):
    existing = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if existing != manifest:
        raise ValueError(f"{MANIFEST_PATH}: manifest differs from the source archive")
    for destination, expected in payloads.items():
        target = ROOT / "src/main/resources" / destination
        actual = target.read_bytes()
        if actual != expected:
            raise ValueError(f"{target}: texture differs from original ZIP bytes")
        png_dimensions(actual, str(target))
    if jar_path is not None:
        with zipfile.ZipFile(jar_path) as jar:
            names = jar.namelist()
            for destination, expected in payloads.items():
                if names.count(destination) != 1:
                    raise ValueError(f"{jar_path}: expected exactly one {destination}")
                if jar.read(destination) != expected:
                    raise ValueError(f"{jar_path}: {destination} differs from original ZIP bytes")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="Audit existing files and manifest without writing")
    parser.add_argument("--jar", type=Path, help="Also audit the specified built JAR (requires --verify)")
    args = parser.parse_args()
    if args.jar is not None and not args.verify:
        parser.error("--jar requires --verify")
    manifest, payloads = source_files()
    if not args.verify:
        for destination, data in payloads.items():
            target = ROOT / "src/main/resources" / destination
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
        MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    verify(manifest, payloads, args.jar)
    print(json.dumps({
        "mode": "verify" if args.verify else "import",
        "png_count": len(payloads),
        "total_bytes": sum(map(len, payloads.values())),
        "source_pack_sha256": PACK_SHA256,
        "manifest_sha256": sha256(MANIFEST_PATH.read_bytes()),
        "jar": str(args.jar) if args.jar is not None else None,
        "verified": True,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
