"""Copy the 20 source furnace textures byte for byte. Author: NingMeng123."""
import argparse
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("archive", type=Path)
parser.add_argument("--output", type=Path, default=Path("src/main/resources/assets/mite/textures/block/furnace"))
args = parser.parse_args()
with zipfile.ZipFile(args.archive) as archive:
    for material in ("clay", "hardened_clay", "sandstone", "obsidian", "netherrack"):
        for face in ("side", "top", "front_off", "front_on"):
            name = f"{material}/{face}.png"
            destination = args.output / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.read("assets/minecraft/textures/blocks/furnace/" + name))
print("Imported 20 unchanged furnace textures")
