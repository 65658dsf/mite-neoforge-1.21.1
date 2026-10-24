"""Draw original transparent phantom-formation pixel sprites. Author: NingMeng123."""
from pathlib import Path
from math import cos, sin, pi
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/mite"
COLORS = {
    "rusted_iron": (176, 112, 76), "copper": (226, 148, 86),
    "silver": (202, 221, 247), "gold": (242, 198, 70),
    "iron": (176, 195, 212), "ancient_metal": (112, 166, 149),
    "mithril": (83, 196, 233), "adamantium": (151, 183, 96),
    "vibranium": (178, 103, 223),
}


def blend(a, b, mix):
    return tuple(round(x * (1 - mix) + y * mix) for x, y in zip(a, b))


def sprite(base):
    image = Image.new("RGBA", (32, 32))
    draw = ImageDraw.Draw(image)
    light = blend(base, (231, 255, 255), .65)
    dark = blend(base, (10, 22, 41), .7)
    cyan = blend(base, (113, 241, 255), .45)
    # A raised octagonal summoning disc, with a dark metal rim and luminous inner orbit.
    draw.ellipse((2, 21, 29, 30), fill=(*dark, 225), outline=(*base, 235))
    draw.ellipse((4, 21, 27, 28), fill=(*cyan, 38), outline=(*light, 215))
    draw.ellipse((7, 22, 24, 27), outline=(*cyan, 160))
    for index in range(8):
        angle = index * pi / 4
        x, y = round(15.5 + 10.5 * cos(angle)), round(24.5 + 2.7 * sin(angle))
        draw.point((x, y), fill=(*light, 255))
        draw.point((x, y + 1), fill=(*cyan, 130))
    draw.line((8, 25, 15, 22, 23, 25, 15, 27, 8, 25), fill=(*base, 170))
    # Two offset translucent sword echoes, rather than copied ordinary sword textures.
    for x, y, alpha in [(7, 9, 135), (24, 7, 165)]:
        draw.polygon([(x, y), (x - 2, y + 5), (x - 1, y + 11), (x + 1, y + 11), (x + 2, y + 5)], fill=(*cyan, alpha))
        draw.line((x, y + 2, x, y + 11), fill=(*light, min(alpha + 55, 255)))
        draw.line((x - 3, y + 12, x + 3, y + 12), fill=(*base, alpha))
        draw.line((x, y + 13, x, y + 15), fill=(*light, alpha))
    # The central blade is an ethereal facet with a material-colored spine and a bright cutting edge.
    draw.polygon([(16, 1), (12, 7), (12, 18), (14, 20), (18, 20), (20, 18), (20, 7)], fill=(*cyan, 44))
    draw.polygon([(16, 2), (13, 7), (13, 17), (16, 20), (19, 17), (19, 7)], fill=(*dark, 235))
    draw.polygon([(16, 3), (14, 7), (14, 17), (16, 19)], fill=(*base, 245))
    draw.polygon([(16, 3), (18, 7), (18, 17), (16, 19)], fill=(*light, 245))
    draw.line((16, 5, 16, 18), fill=(*cyan, 255))
    draw.point((17, 7), fill=(245, 255, 255, 255))
    draw.polygon([(10, 19), (12, 18), (14, 20), (18, 20), (20, 18), (22, 19), (20, 22), (12, 22)], fill=(*dark, 255))
    draw.line((11, 19, 13, 21, 19, 21, 21, 19), fill=(*base, 255))
    draw.polygon([(16, 19), (18, 21), (16, 23), (14, 21)], fill=(*light, 255))
    draw.line((16, 23, 16, 26), fill=(*dark, 255), width=3)
    draw.line((16, 23, 16, 26), fill=(*base, 255))
    draw.point((16, 27), fill=(*light, 255))
    # Small separated glints help the silhouette read as a magical formation at inventory scale.
    for x, y in [(4, 14), (27, 4), (27, 18)]:
        draw.line((x - 1, y, x + 1, y), fill=(*cyan, 125))
        draw.line((x, y - 1, x, y + 1), fill=(*light, 215))
        draw.point((x, y), fill=(240, 255, 255, 255))
    return image


def main():
    texture_dir = ASSETS / "textures/item"
    model_dir = ASSETS / "models/item"
    texture_dir.mkdir(parents=True, exist_ok=True)
    model_dir.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGBA", (3 * 256, 3 * 280), (24, 29, 42, 255))
    draw = ImageDraw.Draw(sheet)
    for index, (material, color) in enumerate(COLORS.items()):
        icon = sprite(color)
        icon.save(texture_dir / f"{material}_phantom_blade.png")
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"mite:item/{material}_phantom_blade"}}
        (model_dir / f"{material}_phantom_blade.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
        x, y = index % 3 * 256, index // 3 * 280
        sheet.alpha_composite(icon.resize((224, 224), Image.Resampling.NEAREST), (x + 16, y + 12))
        draw.text((x + 16, y + 248), material, fill=(221, 235, 248))
    output = ROOT / "build/verification"
    output.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(output / "phantom-blade-icon-sheet.png")
    # Soft cross-ribbon profile: transparent edges, white center; material tint is applied per vertex.
    ribbon = Image.new("RGBA", (16, 16))
    for y in range(16):
        alpha = round(255 * max(0, 1 - abs((y - 7.5) / 7.5)) ** 1.6)
        for x in range(16):
            ribbon.putpixel((x, y), (255, 255, 255, alpha))
    ribbon_dir = ASSETS / "textures/entity/phantom"
    ribbon_dir.mkdir(parents=True, exist_ok=True)
    ribbon.save(ribbon_dir / "blade_light.png")
    print(f"Wrote {len(COLORS)} original item sprites/models and one transparent ribbon texture")


if __name__ == "__main__":
    main()
