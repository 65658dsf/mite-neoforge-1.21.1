"""Render actual noise/carver block sections around shallow cave links. Author: NingMeng123."""

import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import BoundaryNorm, ListedColormap
from matplotlib.patches import Patch


PROJECT_ROOT = Path(__file__).resolve().parents[2]


def render(report_path, output_path):
    sections = json.loads(report_path.read_text(encoding="utf-8"))["sections"]
    sections = [s for s in sections if any("4" in row for row in s["afterRows"])]
    if not sections:
        raise ValueError("No section intersects a successfully carved natural cave link")
    plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
    plt.rcParams["axes.unicode_minus"] = False
    colors = ["#334155", "#faf8ef", "#369bce", "#ed7143", "#f6bf45"]
    palette = ListedColormap(colors)
    norm = BoundaryNorm([-.5, .5, 1.5, 2.5, 3.5, 4.5], palette.N)
    figure, axes = plt.subplots(len(sections), 2, figsize=(12, 4 * len(sections)), squeeze=False)
    for index, section in enumerate(sections):
        link = section["selectedLink"]
        low = max(section["minY"], min(link["startY"], link["endY"]) - 9)
        high = min(section["maxY"], max(link["startY"], link["endY"]) + 9)
        for column, (key, title) in enumerate((("beforeRows", "连接前"), ("afterRows", "连接后 · 黄色为新增通道"))):
            rows = [[int(value) for value in row] for row in section[key]]
            axis = axes[index][column]
            axis.imshow(rows, cmap=palette, norm=norm, interpolation="nearest", origin="upper", aspect="equal",
                        extent=(section["minHorizontal"] - .5, section["maxHorizontal"] + .5,
                                section["minY"] - .5, section["maxY"] + .5))
            axis.set_ylim(low - .5, high + .5)
            horizontal = section["axis"].upper()
            fixed = "Z" if horizontal == "X" else "X"
            axis.set_xlabel(f"世界 {horizontal}")
            axis.set_ylabel("世界 Y")
            axis.set_title(f"{title}\n种子 {section['seed']}，{fixed}={section['fixedCoordinate']}", fontsize=11)
    figure.suptitle("浅层洞穴连接：同种子、同坐标的实际方块剖面", fontsize=15)
    figure.legend(handles=[Patch(facecolor=color, label=label) for color, label in
                           zip(colors, ("岩石", "空气", "水", "岩浆", "新挖通道"))],
                  loc="lower center", ncol=5, frameon=False, bbox_to_anchor=(.5, .025))
    figure.text(.5, .012, "每格一个方块；包含真实噪声及 AIR carvers，未生成结构、表层装饰和后续泉眼。剖面不能展示全部三维连通路径。",
                ha="center", fontsize=8)
    figure.tight_layout(rect=(0, .065, 1, .95))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(output_path, dpi=160, facecolor="white")
    plt.close(figure)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, default=PROJECT_ROOT / "build/reports/surface-cave-links-sections.json")
    parser.add_argument("--output", type=Path, default=PROJECT_ROOT / "build/verification/surface-cave-links-comparison.png")
    args = parser.parse_args()
    render(args.report, args.output)
    print(args.output)
