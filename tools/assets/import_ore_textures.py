"""Copy the three missing R196 ore sprites without resampling. Author: NingMeng123."""
import argparse
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("archive", type=Path)
parser.add_argument("--output", type=Path, default=Path("src/main/resources/assets/mite/textures/block"))
args = parser.parse_args()
with zipfile.ZipFile(args.archive) as archive:
    for metal in ("silver", "mithril", "adamantium"):
        target = args.output / f"{metal}_ore.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(archive.read(f"assets/minecraft/textures/blocks/{metal}_ore.png"))
print("Imported 3 original ore sprites")
