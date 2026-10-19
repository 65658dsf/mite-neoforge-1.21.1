"""Import generated modern ore sprites at Minecraft's native scale. Author: NingMeng123.

This only crops transparent padding and resamples with nearest-neighbor; all artwork
comes from the built-in imagegen tool. See modern_ore_textures.prompts.json.
"""

import argparse
from pathlib import Path

from PIL import Image, ImageDraw


METALS = ("silver", "mithril", "adamantium")
ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src/main/resources/assets/mite/textures"


def import_sprite(source: Path, target: Path, item: bool) -> Image.Image:
    with Image.open(source) as image:
        image = image.convert("RGBA")
        if item:
            # Ignore almost invisible generated edge noise when finding the crop.
            bounds = image.getchannel("A").point(lambda alpha: 255 if alpha >= 16 else 0).getbbox()
            if bounds is None:
                raise ValueError(f"Empty raw ore sprite: {source}")
            image = image.crop(bounds)
            image.thumbnail((14, 14), Image.Resampling.NEAREST)
            result = Image.new("RGBA", (16, 16))
            result.paste(image, ((16 - image.width) // 2, (16 - image.height) // 2))
        else:
            if image.getextrema()[3] != (255, 255):
                raise ValueError(f"Block texture must be opaque: {source}")
            result = image.resize((16, 16), Image.Resampling.NEAREST)
    target.parent.mkdir(parents=True, exist_ok=True)
    result.save(target)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_dir", type=Path, help="Generated PNGs named for their target sprites")
    parser.add_argument("--only", nargs="+", help="Import named sprites; default imports all six")
    parser.add_argument("--preview", type=Path, help="Save a labeled nearest-neighbor contact sheet")
    args = parser.parse_args()
    names = args.only or [f"deepslate_{metal}_ore" for metal in METALS] + [f"raw_{metal}" for metal in METALS]
    for name in names:
        valid_names = {f"deepslate_{metal}_ore" for metal in METALS} | {f"raw_{metal}" for metal in METALS}
        if name not in valid_names:
            parser.error(f"Unknown sprite: {name}")
        target = TEXTURES / ("item" if name.startswith("raw_") else "block") / f"{name}.png"
        import_sprite(args.source_dir / f"{name}.png", target, name.startswith("raw_"))
        print(target.relative_to(ROOT))
    if args.preview:
        names = [f"{metal}_ore" for metal in METALS] + [f"deepslate_{metal}_ore" for metal in METALS] + [f"raw_{metal}" for metal in METALS]
        sheet = Image.new("RGB", (1008, 990), "#20242c")
        draw = ImageDraw.Draw(sheet)
        for index, name in enumerate(names):
            folder = "item" if name.startswith("raw_") else "block"
            with Image.open(TEXTURES / folder / f"{name}.png") as sprite:
                sprite = sprite.convert("RGBA").resize((288, 288), Image.Resampling.NEAREST)
                x, y = (index % 3) * 336 + 24, (index // 3) * 330 + 12
                sheet.paste(sprite, (x, y), sprite)
                draw.text((x, y + 298), name, fill="white")
        args.preview.parent.mkdir(parents=True, exist_ok=True)
        sheet.save(args.preview)


if __name__ == "__main__":
    main()
