"""Restore byte-identical MITE-ITE 2.0.25 vibranium assets. Author: NingMeng123.

The checked-in manifest is the import lock: every destination, raw upstream URL,
Git blob ID, SHA-256 and byte count is fixed. No images are edited or resized.
"""

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import urllib.request


ROOT = Path(__file__).resolve().parents[2]
COMMIT = "61cbf7c954fab8ac941fb5a24b74e2ec32c49766"
REPOSITORY = "https://github.com/MinecraftIsTooEasy/MITE-ITE"
RAW_BASE = f"https://raw.githubusercontent.com/MinecraftIsTooEasy/MITE-ITE/{COMMIT}/"
MANIFEST = ROOT / "src/main/resources/mite/vibranium-native-assets.json"


def verify_bytes(entry, data):
    """Verify both modern content hash and the commit's Git object identity."""
    digest = hashlib.sha256(data).hexdigest()
    blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if (len(data), digest, blob) != (entry["bytes"], entry["sha256"], entry["git_blob"]):
        raise ValueError(f"Content mismatch: {entry['source']}")
    if entry["destination"].endswith(".png") and not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"Not a PNG: {entry['source']}")
    if entry["destination"].endswith(".mcmeta"):
        json.loads(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="Check installed files without writing or networking")
    parser.add_argument("--offline", action="store_true", help="Restore only from the verified local download cache")
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if manifest["upstream_commit"] != COMMIT or manifest["upstream_repository"] != REPOSITORY:
        raise ValueError("Unexpected upstream revision in vibranium asset lock")
    cache_root = ROOT / ".reference/vibranium-upstream" / COMMIT

    def import_one(entry):
        source = entry["source"]
        if entry["url"] != RAW_BASE + source:
            raise ValueError(f"Unexpected upstream URL: {source}")
        destination = (ROOT / entry["destination"]).resolve()
        destination.relative_to((ROOT / "src/main/resources").resolve())
        if args.verify:
            verify_bytes(entry, destination.read_bytes())
            return
        cache = cache_root / source
        if cache.exists():
            data = cache.read_bytes()
        elif args.offline:
            raise FileNotFoundError(f"Missing cached source: {cache}")
        else:
            with urllib.request.urlopen(entry["url"], timeout=60) as response:
                data = response.read()
        verify_bytes(entry, data)
        cache.parent.mkdir(parents=True, exist_ok=True)
        cache.write_bytes(data)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)

    with ThreadPoolExecutor(max_workers=6) as workers:
        list(workers.map(import_one, manifest["assets"]))
    action = "Verified" if args.verify else "Imported"
    print(f"{action} {len(manifest['assets'])} unchanged MITE-ITE vibranium files ({COMMIT})")


if __name__ == "__main__":
    main()
