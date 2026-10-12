"""创建真实玩家的经验曲线测试数据包。开发者：NingMeng123。

只安装到复制的专用测试存档：本包修改经验、游戏模式和该存档的游戏规则。
默认 /reload 后由玩家执行 /function mite_test:xp/start；约 1.5 秒完成。
可用 /function mite_test:xp/stop 中止。请保持静止，不拾取经验或执行其他命令。

--auto-start-once 会添加 minecraft:tick 标签：玩家首次进入且没有完成标记时
自动启动，启动立即标记。以后重进不会重置经验；手工 start 仍可重复运行。
测试保留玩家坐标，不给予物品、食物、药效或治疗；请先确认测试角色处于安全处。
最终保留 5 级、总经验 235、等级内 35 点、半条经验，供截图与保存重进检查。

本包仅用于开发，不写入模组资源。预期是原 R196 字节码阈值以及已选定的现代
运行策略：经验下限 0，上限 203000，200 级空经验条且下一等级需求为 1。
静态校验覆盖格式和引用，不代表已通过 Brigadier 解析或真实游戏测试。
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


NAMESPACE = "mite_test:xp/"
ACTIVE_TAG = "mite_xp_smoke_active"
DONE_TAG = "mite_xp_smoke_done"
VALUE_OBJECTIVE = "mite_xp_value"
FAILURES_OBJECTIVE = "mite_xp_fail"
COMMAND_OBJECTIVE = "mite_xp_cmd"


def cases() -> list[dict]:
    """预期值固定于已核实的规格，不读取待测 Java 或从其公式生成。"""
    return [
        {"name": "zero", "commands": ["experience set @s 0 levels", "experience set @s 0 points"],
         "level": 0, "total": 0, "points": 0, "health": 6, "food_cap": 6},
        {"name": "nineteen", "commands": ["experience add @s 19 points"],
         "level": 0, "total": 19, "points": 19},
        {"name": "level_one", "commands": ["experience add @s 1 points"],
         "level": 1, "total": 20, "points": 0},
        {"name": "forty_nine", "commands": ["experience add @s 29 points"],
         "level": 1, "total": 49, "points": 29},
        {"name": "level_two", "commands": ["experience add @s 1 points"],
         "level": 2, "total": 50, "points": 0},
        {"name": "level_five", "commands": ["experience add @s 150 points"],
         "level": 5, "total": 200, "points": 0, "health": 8, "food_cap": 8},
        {"name": "set_eight_and_points", "commands": ["experience set @s 8 levels", "experience set @s 53 points"],
         "level": 8, "total": 493, "points": 53},
        {"name": "add_two_levels", "commands": ["experience add @s 2 levels"],
         "level": 10, "total": 713, "points": 63},
        {"name": "subtract_three_levels", "commands": ["experience add @s -3 levels"],
         "level": 7, "total": 397, "points": 47},
        {"name": "minimum_int_points", "commands": ["experience add @s -2147483648 points"],
         "level": 0, "total": 0, "points": 0},
        {"name": "level_thirty_five", "commands": ["experience add @s 6650 points"],
         "level": 35, "total": 6650, "points": 0, "health": 20, "food_cap": 20},
        {"name": "maximum_int_points", "commands": ["experience add @s 2147483647 points"],
         "level": 200, "total": 203000, "points": 0, "bar_x1000": 0, "health": 20, "food_cap": 20},
        {"name": "reject_level_201", "commands": [
            f"scoreboard players set @s {COMMAND_OBJECTIVE} -2147483647",
            f"execute store success score @s {COMMAND_OBJECTIVE} run experience set @s 201 levels"],
         "level": 200, "total": 203000, "points": 0, "command_success": 0},
        {"name": "reject_top_level_point", "commands": [
            f"scoreboard players set @s {COMMAND_OBJECTIVE} -2147483647",
            f"execute store success score @s {COMMAND_OBJECTIVE} run experience set @s 1 points"],
         "level": 200, "total": 203000, "points": 0, "command_success": 0},
        {"name": "final_level_five_half", "commands": ["experience set @s 0 levels",
            "experience set @s 0 points", "experience add @s 235 points"],
         "level": 5, "total": 235, "points": 35, "bar_x1000": 500, "health": 8, "food_cap": 8},
    ]


def checks(case: dict) -> list[tuple[str, str, str]]:
    result = [
        ("level", "experience query @s levels", str(case["level"])),
        ("points", "experience query @s points", str(case["points"])),
        ("total", "data get entity @s XpTotal 1", str(case["total"])),
    ]
    if "command_success" in case:
        result.append(("command_success", f"scoreboard players get @s {COMMAND_OBJECTIVE}", str(case["command_success"])))
    if "bar_x1000" in case:
        # 只比较精确可表示的 0 与 0.5，避免把 float 截断误差误认为 XP 错误。
        result.append(("bar_x1000", "data get entity @s XpP 1000", str(case["bar_x1000"])))
    if "health" in case:
        result.append(("health_base", "attribute @s minecraft:generic.max_health base get 1", str(case["health"])))
    if "food_cap" in case:
        # 不给测试角色补食物，因此只检查实际食物量未突破当前容量。
        result.append(("food_within_cap", "data get entity @s foodLevel 1", f"0..{case['food_cap']}"))
    return result


def check_lines(case: dict, label: str, command: str, expected: str) -> list[str]:
    return [
        f"scoreboard players set @s {VALUE_OBJECTIVE} -2147483647",
        f"execute store result score @s {VALUE_OBJECTIVE} run {command}",
        f"execute unless score @s {VALUE_OBJECTIVE} matches {expected} run scoreboard players add @s {FAILURES_OBJECTIVE} 1",
        "tellraw @s " + json.dumps([
            {"text": f"MITE_XP {case['name']} {label} expected={expected} actual="},
            {"score": {"name": "@s", "objective": VALUE_OBJECTIVE}},
        ], ensure_ascii=False),
    ]


def create(world: Path, auto_start_once: bool = False) -> Path:
    root = world / "datapacks" / "mite-experience-smoke"
    functions = root / "data/mite_test/function/xp"
    functions.mkdir(parents=True, exist_ok=True)
    (root / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 48,
        "description": "MITE 经验曲线开发测试 — NingMeng123",
    }}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def write(name: str, lines: list[str]):
        (functions / (name + ".mcfunction")).write_text("\n".join(lines) + "\n", encoding="utf-8")

    vectors = cases()
    assertions = sum(len(checks(case)) for case in vectors)
    write("start", [
        "# 必须从真实玩家上下文调用，控制台直接调用不会改变世界。",
        f"execute as @s[type=minecraft:player] at @s run function {NAMESPACE}start_body",
    ])
    write("start_body", [
        "# 首先标记，防止自动 tick 再次启动；stop 不会移除此标记。",
        f"tag @s add {DONE_TAG}",
        f"function {NAMESPACE}stop",
        f"scoreboard objectives add {VALUE_OBJECTIVE} dummy",
        f"scoreboard objectives add {FAILURES_OBJECTIVE} dummy",
        f"scoreboard objectives add {COMMAND_OBJECTIVE} dummy",
        f"tag @s add {ACTIVE_TAG}",
        f"scoreboard players set @s {FAILURES_OBJECTIVE} 0",
        "gamerule naturalRegeneration false",
        "gamerule doMobSpawning false",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "difficulty peaceful",
        "time set day",
        "weather clear",
        "gamemode survival @s",
        'tellraw @s {"text":"MITE_XP 开始：15步测试约1.5秒。保持静止、不拾取经验；只供复制的测试存档使用。","color":"yellow"}',
        *vectors[0]["commands"],
        f"schedule function {NAMESPACE}{vectors[0]['name']} 2t replace",
    ])
    write("stop", [
        "# 仅清除本包调度和活动标记；保留一次性启动标记及当前经验。",
        *[f"schedule clear {NAMESPACE}{case['name']}" for case in vectors],
        f"tag @a[tag={ACTIVE_TAG}] remove {ACTIVE_TAG}",
    ])

    for index, case in enumerate(vectors):
        write(case["name"], [
            f"execute as @a[tag={ACTIVE_TAG}] at @s run function {NAMESPACE}{case['name']}_body",
        ])
        lines = ["# 所有读数均在上次变更后 2 tick 检查。"]
        for label, command, expected in checks(case):
            lines.extend(check_lines(case, label, command, expected))
        if index + 1 < len(vectors):
            following = vectors[index + 1]
            lines.extend(following["commands"])
            lines.append(f"schedule function {NAMESPACE}{following['name']} 2t replace")
        else:
            lines.extend([
                "tellraw @s " + json.dumps([
                    {"text": f"MITE_XP stages={len(vectors)} assertions={assertions} failures="},
                    {"score": {"name": "@s", "objective": FAILURES_OBJECTIVE}},
                ], ensure_ascii=False),
                "title @s times 10 600 20",
                f'execute if score @s {FAILURES_OBJECTIVE} matches 0 run title @s subtitle {{"text":"5级 · 总经验235 · 等级内35点 · 半条经验","color":"green"}}',
                f'execute if score @s {FAILURES_OBJECTIVE} matches 0 run title @s title {{"text":"XP checks PASS","color":"green"}}',
                f'execute unless score @s {FAILURES_OBJECTIVE} matches 0 run title @s title {{"text":"XP checks FAIL","color":"red"}}',
                f'execute if score @s {FAILURES_OBJECTIVE} matches 0 run tellraw @s {{"text":"MITE_XP PASS：L5 / XpTotal=235 / points=35 / XpP=0.5。请截图，保存退出后重进核对；不会自动重置。","color":"green"}}',
                f'execute unless score @s {FAILURES_OBJECTIVE} matches 0 run tellraw @s {{"text":"MITE_XP FAIL：请查看上面的预期与实际值；不能据此认定经验功能已通过。","color":"red"}}',
                f"tag @s remove {ACTIVE_TAG}",
            ])
        write(case["name"] + "_body", lines)

    tick_tag = root / "data/minecraft/tags/function/tick.json"
    tick_function = functions / "tick.mcfunction"
    if auto_start_once:
        write("tick", [
            "# 一次只选择一名无标记玩家；断线重进后已有 done 标记，不会重置经验。",
            f"execute unless entity @a[tag={ACTIVE_TAG}] as @a[tag=!{DONE_TAG},limit=1,sort=arbitrary] at @s run function {NAMESPACE}start",
        ])
        tick_tag.parent.mkdir(parents=True, exist_ok=True)
        tick_tag.write_text(json.dumps({"values": [NAMESPACE + "tick"]}, indent=2) + "\n", encoding="utf-8")
    else:
        # 允许把本包从自动模式重新生成为手动模式；只删除本包自己拥有的两个文件。
        tick_tag.unlink(missing_ok=True)
        tick_function.unlink(missing_ok=True)

    validate_generated(root, auto_start_once)
    return root


def validate_generated(root: Path, auto_start_once: bool | None = None) -> dict:
    """检查 1.21.1 的目录格式、JSON、内部引用和调度；不代替实机执行。"""
    pack = json.loads((root / "pack.mcmeta").read_text(encoding="utf-8"))
    if pack["pack"]["pack_format"] != 48:
        raise ValueError("Minecraft 1.21.1 数据包格式必须为 48")
    functions = root / "data/mite_test/function/xp"
    paths = sorted(functions.glob("*.mcfunction"))
    names = {path.stem for path in paths}
    tick_tag = root / "data/minecraft/tags/function/tick.json"
    is_automatic = tick_tag.exists()
    if auto_start_once is not None and auto_start_once != is_automatic:
        raise ValueError("自动启动标签与生成模式不一致")
    if (root / "data/minecraft/tags/function/load.json").exists():
        raise ValueError("测试包不应在 load 时启动")
    if (root / "data/mite_test/functions").exists() or (root / "data/minecraft/tags/functions").exists():
        raise ValueError("1.21.1 使用单数 function 目录")

    expected_names = {"start", "start_body", "stop"}
    expected_names.update(case["name"] for case in cases())
    expected_names.update(case["name"] + "_body" for case in cases())
    if is_automatic:
        expected_names.add("tick")
    if names != expected_names:
        raise ValueError("存在缺失或多余的测试函数：" + repr(names ^ expected_names))
    for path in root.rglob("*.json"):
        payload = json.loads(path.read_text(encoding="utf-8"))
        if path == tick_tag and payload != {"values": [NAMESPACE + "tick"]}:
            raise ValueError("tick 标签必须只引用本包 tick 函数")

    command_count = 0
    schedules = []
    assertion_count = 0
    for path in paths:
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not line or line.startswith("#"):
                continue
            command_count += 1
            if line.startswith("/") or "data modify entity" in line or "data merge entity" in line:
                raise ValueError(f"不允许的函数命令：{path.name}:{number}")
            if re.search(r"(?:^| run )(?:give|clear|effect|tp|teleport|summon) ", line):
                raise ValueError("本包不得补给资源、施加效果或改变玩家位置：" + line)
            for target in re.findall(r"(?:function|schedule clear) mite_test:xp/([a-z0-9_]+)", line):
                if target not in names:
                    raise ValueError("函数引用不存在：" + target)
            if line.startswith("schedule function "):
                match = re.fullmatch(r"schedule function mite_test:xp/([a-z0-9_]+) 2t replace", line)
                if not match:
                    raise ValueError("变更后必须等待 2 tick 再断言：" + line)
                schedules.append(match.group(1))
            if line.startswith(f"execute unless score @s {VALUE_OBJECTIVE} matches "):
                assertion_count += 1
            if "tellraw @s " in line:
                json.loads(line.split("tellraw @s ", 1)[1])
            for marker in ("run title @s title ", "run title @s subtitle "):
                if marker in line:
                    json.loads(line.split(marker, 1)[1])
    if sorted(schedules) != sorted(case["name"] for case in cases()):
        raise ValueError("每个测试阶段必须且只能安排一次延迟断言")
    if assertion_count != sum(len(checks(case)) for case in cases()):
        raise ValueError("断言数量与固定测试向量不符")

    start_lines = [line for line in (functions / "start_body.mcfunction").read_text(encoding="utf-8").splitlines()
                   if line and not line.startswith("#")]
    if start_lines[0] != f"tag @s add {DONE_TAG}":
        raise ValueError("start 必须首先设置一次性标记，防止重复启动")
    all_commands = "\n".join(path.read_text(encoding="utf-8") for path in paths)
    if re.search(rf"tag .* remove {DONE_TAG}(?:\n|$)", all_commands):
        raise ValueError("本包不能自动移除一次性启动标记")
    return {
        "author": "NingMeng123", "functions": len(paths), "commands": command_count,
        "stages": len(cases()), "assertions": assertion_count, "scheduled_ticks": len(schedules) * 2,
        "auto_start_once": is_automatic,
        "final_state": {"level": 5, "total": 235, "points": 35, "progress": 0.5},
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("test_world", type=Path, help="复制的专用测试存档路径")
    parser.add_argument("--auto-start-once", action="store_true", help="首次进服自动测试一次；重进保留最终经验")
    arguments = parser.parse_args()
    destination = create(arguments.test_world.resolve(), arguments.auto_start_once)
    print(destination)
    print(json.dumps(validate_generated(destination, arguments.auto_start_once), ensure_ascii=False))
