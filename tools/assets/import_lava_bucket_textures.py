"""Copy six metal lava-bucket sprites byte for byte. Author: NingMeng123."""
import argparse
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("archive", type=Path)
parser.add_argument("--output", type=Path, default=Path("src/main/resources/assets/mite/textures/item"))
args = parser.parse_args()
with zipfile.ZipFile(args.archive) as archive:
    for metal in ("copper", "silver", "gold", "mithril", "adamantium", "ancient_metal"):
        destination = args.output / f"{metal}_lava_bucket.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(archive.read(f"assets/minecraft/textures/items/buckets/{metal}/lava.png"))
print("Imported six unchanged lava-bucket textures")
