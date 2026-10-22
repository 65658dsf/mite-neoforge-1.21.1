"""Render paired real-generation cave sections. Author: NingMeng123."""

import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as pyplot
from matplotlib.colors import BoundaryNorm, ListedColormap
from matplotlib.patches import Patch


PROJECT_ROOT = Path(__file__).resolve().parents[2]
PROFILES = (
    ("vanilla", "原版洞穴噪声"),
    ("smallCaves", "收紧高层大洞"),
    ("reducedWater", "同时减少地下含水层"),
)
COLORS = ("#263548", "#f5f4ef", "#379ccf", "#ef9639")


def render(report_path, output_path):
    report = json.loads(report_path.read_text(encoding="utf-8"))
    sections = report.get("sections", [])[:2]
    if not sections:
        raise ValueError("The generation report contains no real block sections")
    pyplot.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
    pyplot.rcParams["axes.unicode_minus"] = False
    figure, axes = pyplot.subplots(
        len(sections), len(PROFILES), figsize=(9, 8 * len(sections)), squeeze=False
    )
    palette = ListedColormap(COLORS)
    normalization = BoundaryNorm([-0.5, 0.5, 1.5, 2.5, 3.5], palette.N)
    for section_index, section in enumerate(sections):
        minimum = section["minY"]
        maximum = section["maxY"]
        for profile_index, (profile, title) in enumerate(PROFILES):
            axis = axes[section_index][profile_index]
            rows = section[profile]
            if len(rows) != maximum - minimum + 1:
                raise ValueError("Section height does not match its inclusive Y bounds")
            if any(len(row) != 16 for row in rows):
                raise ValueError("Each section row must contain sixteen actual block columns")
            axis.imshow(
                rows,
                cmap=palette,
                norm=normalization,
                interpolation="nearest",
                origin="upper",
                extent=(-0.5, 15.5, minimum - 0.5, maximum + 0.5),
                aspect="equal",
            )
            axis.set_ylim(max(minimum, -16), min(maximum, 96))
            axis.set_xlim(-0.5, 15.5)
            axis.set_xticks([0, 8, 15])
            axis.set_xlabel("区块内 X")
            axis.set_ylabel("世界 Y")
            axis.set_title(title, fontsize=11, pad=12)
            axis.axhline(63, color="#78b1d5", linewidth=0.6, linestyle="--")
        axes[section_index][0].text(
            -1.1,
            0.5,
            f"种子 {section['seed']}\n区块 ({section['chunkX']}, {section['chunkZ']})\nZ = {section['blockZ']}",
            transform=axes[section_index][0].transAxes,
            rotation=90,
            verticalalignment="center",
            horizontalalignment="center",
            fontsize=9,
        )
    figure.suptitle("同种子、同坐标的真实噪声生成剖面", fontsize=16, y=0.975)
    figure.legend(
        handles=[
            Patch(facecolor=color, label=label)
            for color, label in zip(COLORS, ("岩石", "空气", "水", "岩浆"))
        ],
        loc="lower center",
        ncol=4,
        frameon=False,
        bbox_to_anchor=(0.5, 0.035),
    )
    figure.text(
        0.5,
        0.015,
        "每个像素代表一个方块；剖面只含噪声填充，新增细隧道由独立的原生 carver 测试验证。",
        horizontalalignment="center",
        fontsize=8,
    )
    figure.subplots_adjust(left=0.18, right=0.96, top=0.93, bottom=0.085, hspace=0.2)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(output_path, dpi=160, facecolor="white")
    pyplot.close(figure)
    print(output_path.resolve())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input", type=Path, default=PROJECT_ROOT / "build/verification/caves/cave-generation.json"
    )
    parser.add_argument(
        "--output", type=Path, default=PROJECT_ROOT / "build/verification/caves/cave-noise-comparison.png"
    )
    arguments = parser.parse_args()
    render(arguments.input, arguments.output)


if __name__ == "__main__":
    main()
