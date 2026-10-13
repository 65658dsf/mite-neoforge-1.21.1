"""Copy only the 35 audited R196 dimension texture files. Author: NingMeng123."""
import argparse
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("archive", type=Path)
parser.add_argument("--output", type=Path, default=Path("src/main/resources/assets/mite/textures/block"))
args = parser.parse_args()
names = ["mantle.png", "core.png", "core.png.mcmeta"]
names += [f"runestones/{metal}/{rune}.png" for metal in ("mithril", "adamantium") for rune in range(16)]
with zipfile.ZipFile(args.archive) as archive:
    for name in names:
        destination = args.output / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(archive.read("assets/minecraft/textures/blocks/" + name))
print(f"Imported {len(names)} original texture/animation files")
