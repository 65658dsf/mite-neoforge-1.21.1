"""创建真实玩家的饥饿上限测试数据包。开发者：NingMeng123。

仅用于一次性测试存档：会修改等级、游戏模式、难度、效果和游戏规则。
数据包不含 load/tick 标签，不会自动启动，也不会打入模组 JAR。
安装后执行 /reload，再由真实玩家执行 /function mite_test:food/start。
测试约需 2.5 秒，期间保持静止、不进食；/function mite_test:food/stop 可中止。

已核对 1.21.1 的 EffectCommands、SaturationMobEffect、FoodData 源码：
饱和属于瞬时效果，命令持续时间 1 对应 1 tick，而不是普通效果的 20 tick。
每次等级变更和断言间隔 2 tick，饱和补给在下一次升级之前已结束。
本包覆盖 eat(int,float) 饱和路径；真实物品进食 eat(FoodProperties) 仍需实机验证。
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


TAG = "mite_food_test"
OBJECTIVE = "mite_food"
FAILURES = "mite_ffail"
SATURATION_SCALE = 1000
LEVEL_CAPS = ((0, 6), (4, 6), (5, 8), (10, 10), (34, 18), (35, 20), (40, 20))
FILL_COMMAND = "effect give @s minecraft:saturation 1 255 true"


def cases() -> list[dict]:
    """固定预期来自已确认的等级边界，不读取待测 Java 实现。"""
    result = []
    for index, (level, cap) in enumerate(LEVEL_CAPS):
        if index:
            previous_cap = LEVEL_CAPS[index - 1][1]
            result.append({
                "name": f"level_{level}_upgrade_keeps_current",
                "level": level,
                "food": previous_cap,
                "saturation": previous_cap,
                "after": [FILL_COMMAND],
            })
        after = [f"experience set @s {LEVEL_CAPS[index + 1][0]} levels"] if index + 1 < len(LEVEL_CAPS) else ["experience set @s 5 levels"]
        result.append({
            "name": f"level_{level}_filled_to_cap",
            "level": level,
            "food": cap,
            "saturation": cap,
            "after": after,
        })
    result.extend([
        {"name": "down_to_five_clamps_both", "level": 5, "food": 8, "saturation": 8, "after": ["experience set @s 0 levels"]},
        {"name": "down_to_zero_clamps_both", "level": 0, "food": 6, "saturation": 6, "after": []},
    ])
    return result


def check_lines(case: dict, label: str, command: str, expected: int) -> list[str]:
    # 先写入哨兵；读取失败也不能沿用上一步数值而误报通过。
    return [
        f"scoreboard players set @s {OBJECTIVE} -2147483647",
        f"execute store result score @s {OBJECTIVE} run {command}",
        f"execute unless score @s {OBJECTIVE} matches {expected} run scoreboard players add @s {FAILURES} 1",
        "tellraw @s " + json.dumps([
            {"text": f"MITE_FOOD {case['name']} {label} expected={expected} actual="},
            {"score": {"name": "@s", "objective": OBJECTIVE}},
        ], ensure_ascii=False),
    ]


def create(world: Path) -> Path:
    root = world / "datapacks" / "mite-food-smoke"
    functions = root / "data" / "mite_test" / "function" / "food"
    functions.mkdir(parents=True, exist_ok=True)
    (root / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 48,
        "description": "MITE 饥饿上限开发测试 — NingMeng123",
    }}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def write(name: str, lines: list[str]):
        (functions / (name + ".mcfunction")).write_text("\n".join(lines) + "\n", encoding="utf-8")

    vectors = cases()
    # 调度命令以服务器身份执行，包装函数必须重新选中被测真实玩家。
    def wrapper(name: str):
        write(name, [f"execute as @a[tag={TAG}] at @s run function mite_test:food/{name}_body"])

    write("stop", [
        "# 仅中止本测试包；缺少调度或效果时命令可返回失败，不会修改失败计数。",
        "schedule clear mite_test:food/prepare",
        *[f"schedule clear mite_test:food/{case['name']}" for case in vectors],
        f"execute as @a[tag={TAG}] run effect clear @s minecraft:saturation",
        f"tag @a[tag={TAG}] remove {TAG}",
    ])
    write("start", [
        "# 单次只测试一名真实玩家；重启时清除本包先前调度。",
        "function mite_test:food/stop",
        f"scoreboard objectives add {OBJECTIVE} dummy",
        f"scoreboard objectives add {FAILURES} dummy",
        f"tag @s add {TAG}",
        f"scoreboard players set @s {FAILURES} 0",
        "gamerule naturalRegeneration false",
        "gamerule doMobSpawning false",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "difficulty normal",
        "weather clear",
        "time set day",
        "gamemode survival @s",
        "effect clear @s",
        "experience set @s 0 levels",
        'tellraw @s {"text":"MITE_FOOD 开始：请保持静止、不进食，约2.5秒后报告；本包仅供一次性测试存档。","color":"yellow"}',
        # FoodData.exhaustionLevel 最大 40；先等待 20 tick 消化残留消耗。
        # 不用非法的 data modify entity 修改玩家 foodLevel，也不长期施加 hunger。
        "schedule function mite_test:food/prepare 20t replace",
    ])
    wrapper("prepare")
    write("prepare_body", [
        FILL_COMMAND,
        f"schedule function mite_test:food/{vectors[0]['name']} 2t replace",
    ])

    for index, case in enumerate(vectors):
        wrapper(case["name"])
        lines = [
            "# 饱和效果已至少经过2tick；此处清除任何残留，防止跨升级步骤补食物。",
            "effect clear @s minecraft:saturation",
        ]
        for label, command, expected in [
            ("level", "experience query @s levels", case["level"]),
            ("food", "data get entity @s foodLevel 1", case["food"]),
            ("saturation_x1000", f"data get entity @s foodSaturationLevel {SATURATION_SCALE}", case["saturation"] * SATURATION_SCALE),
        ]:
            lines.extend(check_lines(case, label, command, expected))
        lines.extend(case["after"])
        if index + 1 < len(vectors):
            lines.append(f"schedule function mite_test:food/{vectors[index + 1]['name']} 2t replace")
        else:
            lines.extend([
                f'tellraw @s [{{"text":"MITE_FOOD assertions={len(vectors) * 3} failures="}},{{"score":{{"name":"@s","objective":"{FAILURES}"}}}}]',
                f'execute if score @s {FAILURES} matches 0 run title @s title {{"text":"Food checks PASS","color":"green"}}',
                f'execute unless score @s {FAILURES} matches 0 run title @s title {{"text":"Food checks FAIL","color":"red"}}',
                'tellraw @s {"text":"MITE_FOOD 完成：饱和命令路径已检查；真实食物、重登与独立服务器仍需另外实测。"}',
                f"tag @s remove {TAG}",
            ])
        write(case["name"] + "_body", lines)
    validate_generated(root)
    return root


def validate_generated(root: Path) -> dict:
    """静态核对生成的格式与引用；不冒充 Brigadier 或实际游戏执行。"""
    pack = json.loads((root / "pack.mcmeta").read_text(encoding="utf-8"))
    if pack["pack"]["pack_format"] != 48:
        raise ValueError("1.21.1 数据包格式必须为 48")
    functions = root / "data/mite_test/function/food"
    paths = list(functions.glob("*.mcfunction"))
    names = {path.stem for path in paths}
    command_count = 0
    saturation_commands = 0
    for path in paths:
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not line or line.startswith("#"):
                continue
            command_count += 1
            if line.startswith("/") or "data modify entity" in line or "data merge entity" in line:
                raise ValueError(f"不允许的函数命令：{path.name}:{number}")
            for target in re.findall(r"function mite_test:food/([a-z0-9_]+)", line):
                if target not in names:
                    raise ValueError("函数引用不存在：" + target)
            if line.startswith("schedule function "):
                expected = "20t" if "food/prepare " in line else "2t"
                if not line.endswith(f" {expected} replace"):
                    raise ValueError("测试步骤调度时间不正确：" + line)
            if line.startswith("effect give @s minecraft:saturation "):
                saturation_commands += 1
                if line != FILL_COMMAND:
                    raise ValueError("饱和效果必须严格限制为 1 tick")
            if "tellraw @s " in line:
                json.loads(line.split("tellraw @s ", 1)[1])
            if "run title @s title " in line:
                json.loads(line.split("run title @s title ", 1)[1])
    if saturation_commands != len(LEVEL_CAPS):
        raise ValueError("每个等级边界必须且只能安排一次饱和补给")
    if (root / "data/minecraft/tags/function").exists():
        raise ValueError("开发测试包不能通过自动标签启动")
    return {"functions": len(paths), "commands": command_count, "cases": len(cases()), "assertions": len(cases()) * 3}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("test_world", type=Path)
    arguments = parser.parse_args()
    destination = create(arguments.test_world.resolve())
    print(destination)
    print(json.dumps(validate_generated(destination), ensure_ascii=False))
