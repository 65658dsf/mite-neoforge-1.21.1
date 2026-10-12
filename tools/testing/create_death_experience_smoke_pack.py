"""创建死亡惩罚和负经验的真实客户端测试包。开发者：NingMeng123。

仅用于独立单人的复制测试存档。测试会真正 /kill 玩家，修改经验、模式及规则；
不清空背包、不传送、不补给物品。原有物品可能随测试死亡正常掉落。
环境：和平、固定晴天白昼、禁怪、禁自然恢复、doMobLoot=false；主流程开启
doImmediateRespawn，按步骤切换 keepInventory，结束后 keepInventory=false。

默认 /reload 后 /function mite_test:death_xp/start。--auto-start-once 使首次
进服自动启动一次。所有进度、计时、启动标记使用持久计分板，不依赖实体 tag。
主流程每次死亡最多等待 200 个在线 tick，活着后才等待至少 2 tick 再断言。
负经验同步步骤等 40 tick。最终停在总经验 -21、等级 -2、等级内 19 点，供
检查真实红色等级 HUD；自动启动不会在重进后把经验重置。

手动持久化断点（不会自动启动）：
  /function mite_test:death_xp/death_screen     从 0 XP 死亡，预期重生 -20
  /function mite_test:death_xp/death_screen_19  从 19 XP 死亡，预期重生 -1
断点关闭立即重生，允许停在死亡界面保存退出、重进后手动点击重生。人工暂停
不计超时；检测活着后自动开始 40 tick 验证，并启用 200 tick 超时保护。
  /function mite_test:death_xp/resume             检查暂停断点是否已经复活
  /function mite_test:death_xp/rejoin_assertions  再次检查该断点保存后的经验
  /function mite_test:death_xp/stop               停止本包流程，不修改经验
断点与主流程使用不同的失败/完成计分板。重新验收不重写 XP。

此工具仅生成独立开发数据包，不写入模组资源；静态校验不替代实际游戏验证。
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


NS = "mite_test:death_xp/"
STEP = "mite_dx_step"
WAIT = "mite_dx_wait"
AGE = "mite_dx_age"
HEALTH = "mite_dx_hp"
VALUE = "mite_dx_value"
FAIL = "mite_dx_fail"
COUNT = "mite_dx_count"
DONE = "mite_dx_done"
PFAIL = "mite_dx_pfail"
PCOUNT = "mite_dx_pcount"
PDONE = "mite_dx_pdone"
PKIND = "mite_dx_kind"
OBJECTIVES = (STEP, WAIT, AGE, HEALTH, VALUE, FAIL, COUNT, DONE, PFAIL, PCOUNT, PDONE, PKIND)
TIMEOUT_TICKS = 200
RESET_XP = ["experience set @s 0 levels", "experience set @s 0 points"]


def cases() -> list[dict]:
    """独立固定预期，不读取待测实现或从生产公式计算。"""
    return [
        {"name": "zero", "commands": RESET_XP, "level": 0, "total": 0, "points": 0, "capacity": 6},
        {"name": "death_zero", "death": True, "level": -1, "total": -20, "points": 0},
        {"name": "death_again", "death": True, "level": -2, "total": -40, "points": 0},
        {"name": "gain_thirty_nine", "commands": ["experience add @s 39 points"],
         "level": -1, "total": -1, "points": 19, "wait": 40},
        {"name": "return_to_zero", "commands": ["experience add @s 1 points"],
         "level": 0, "total": 0, "points": 0},
        {"name": "prepare_nineteen", "commands": ["experience add @s 19 points"],
         "level": 0, "total": 19, "points": 19},
        {"name": "death_nineteen", "death": True, "level": -1, "total": -1, "points": 19},
        {"name": "prepare_twenty", "commands": [*RESET_XP, "experience add @s 20 points"],
         "level": 1, "total": 20, "points": 0},
        {"name": "death_twenty", "death": True, "level": 0, "total": 0, "points": 0},
        {"name": "prepare_two_thirty_five", "commands": [*RESET_XP, "experience add @s 235 points"],
         "level": 5, "total": 235, "points": 35, "capacity": 8},
        {"name": "death_two_thirty_five", "death": True, "level": 0, "total": 0, "points": 0},
        {"name": "prepare_keep_inventory", "commands": ["experience add @s -1 points", "gamerule keepInventory true"],
         "level": -1, "total": -1, "points": 19},
        {"name": "keep_inventory_death", "death": True, "level": -1, "total": -1, "points": 19},
        {"name": "minimum_experience", "commands": ["gamerule keepInventory false", "experience add @s -2147483648 points"],
         "level": -40, "total": -800, "points": 0, "capacity": 6},
        {"name": "death_at_minimum", "death": True, "level": -40, "total": -800, "points": 0},
        {"name": "final_negative_hud", "commands": [*RESET_XP, "experience add @s -21 points"],
         "level": -2, "total": -21, "points": 19, "capacity": 6, "wait": 40},
    ]


def checkpoints() -> list[dict]:
    return [
        {"step": 101, "name": "saved_zero_death", "before": 0, "level": -1, "total": -20, "points": 0},
        {"step": 102, "name": "saved_nineteen_death", "before": 19, "level": -1, "total": -1, "points": 19},
    ]


def checks(case: dict) -> list[tuple[str, str, str]]:
    values = [
        ("level", "experience query @s levels", str(case["level"])),
        ("points", "experience query @s points", str(case["points"])),
        ("total", "data get entity @s XpTotal 1", str(case["total"])),
    ]
    if "capacity" in case:
        values.extend([
            ("health_base", "attribute @s minecraft:generic.max_health base get 1", str(case["capacity"])),
            ("food_within_cap", "data get entity @s foodLevel 1", f"0..{case['capacity']}"),
        ])
    return values


def say(text: str, color: str | None = None) -> str:
    payload = {"text": text}
    if color:
        payload["color"] = color
    return "tellraw @s " + json.dumps(payload, ensure_ascii=False)


def assertion_lines(case: dict, failures: str, counter: str) -> list[str]:
    lines = []
    for label, command, expected in checks(case):
        lines.extend([
            f"scoreboard players set @s {VALUE} -2147483647",
            f"execute store result score @s {VALUE} run {command}",
            f"scoreboard players add @s {counter} 1",
            f"execute unless score @s {VALUE} matches {expected} run scoreboard players add @s {failures} 1",
            "tellraw @s " + json.dumps([
                {"text": f"MITE_DEATH_XP {case['name']} {label} expected={expected} actual="},
                {"score": {"name": "@s", "objective": VALUE}},
            ], ensure_ascii=False),
        ])
    return lines


def create(world: Path, auto_start_once: bool = False) -> Path:
    root = world / "datapacks/mite-death-experience-smoke"
    functions = root / "data/mite_test/function/death_xp"
    functions.mkdir(parents=True, exist_ok=True)
    (root / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 48, "description": "MITE 死亡与负经验开发测试 — NingMeng123",
    }}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def write(name: str, lines: list[str]):
        (functions / (name + ".mcfunction")).write_text("\n".join(lines) + "\n", encoding="utf-8")

    def player_entry(name: str, body: str):
        write(name, [f"execute as @s[type=minecraft:player] at @s run function {NS}{body}"])

    vectors = cases()
    checkpoints_data = checkpoints()
    assertion_total = sum(len(checks(case)) for case in vectors)
    write("load", [
        "# 只注册目标，不重置任何玩家的持久计分板或经验。",
        *[f"scoreboard objectives add {objective} dummy" for objective in OBJECTIVES],
    ])
    write("environment", [
        "# 仅允许在复制的专用测试世界使用。本包不清空背包、不传送、不补给。",
        "difficulty peaceful",
        "gamerule naturalRegeneration false",
        "gamerule doMobSpawning false",
        "gamerule doMobLoot false",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "gamerule doImmediateRespawn true",
        "gamerule keepInventory false",
        "time set day",
        "weather clear",
        "gamemode survival @s",
    ])
    player_entry("start", "start_body")
    write("start_body", [
        "# 第一步锁定自动启动；持久计分板在换玩家实体、保存和重进后仍保留。",
        f"scoreboard players set @s {DONE} 1",
        f"scoreboard players set @s {STEP} 0",
        f"scoreboard players set @s {FAIL} 0",
        f"scoreboard players set @s {COUNT} 0",
        f"function {NS}environment",
        say(f"MITE_DEATH_XP 开始：{len(vectors)}阶段、7次死亡、{assertion_total}断言。请保持静止；只用于单人测试副本。", "yellow"),
        f"function {NS}apply_{vectors[0]['name']}",
    ])
    player_entry("stop", "stop_body")
    write("stop_body", [
        f"execute if score @s {STEP} matches ..-100 run scoreboard players set @s {PDONE} -1",
        f"execute if score @s {STEP} matches 100.. run scoreboard players set @s {PDONE} -1",
        f"scoreboard players set @s {STEP} 0",
        say("MITE_DEATH_XP 已停止；经验与一次性启动标记保留。", "yellow"),
    ])

    # 调度顺序很重要：本 tick 新启动的步骤要到下一 tick 才开始计时。
    tick_lines = [
        f"execute as @a[scores={{{STEP}=1..}}] at @s run function {NS}tick_player",
        f"execute as @a[scores={{{STEP}=-102..-101}}] at @s run function {NS}pending_watch",
    ]
    if auto_start_once:
        tick_lines.append(
            f"execute as @a unless score @s {DONE} matches 1.. unless score @s {STEP} matches ..-1 "
            f"unless score @s {STEP} matches 1.. unless score @s {PDONE} matches -1..1 "
            f"at @s run function {NS}start"
        )
    write("tick", tick_lines)
    tick_player = [
        f"scoreboard players add @s {AGE} 1",
        f"scoreboard players set @s {HEALTH} -2147483647",
        f"execute store result score @s {HEALTH} run data get entity @s Health 1000",
        f"execute if score @s {HEALTH} matches 1.. run scoreboard players remove @s {WAIT} 1",
    ]
    for index, case in enumerate(vectors, 1):
        tick_player.append(
            f"execute if score @s {STEP} matches {index} unless score @s {HEALTH} matches 1.. "
            f"run scoreboard players set @s {WAIT} {case.get('wait', 2)}"
        )
    tick_player.extend([
        f"execute if score @s {STEP} matches 101..102 unless score @s {HEALTH} matches 1.. run scoreboard players set @s {WAIT} 40",
        f"execute if score @s {AGE} matches {TIMEOUT_TICKS}.. run function {NS}timeout",
    ])
    for index, case in enumerate(vectors, 1):
        tick_player.append(
            f"execute if score @s {STEP} matches {index} if score @s {HEALTH} matches 1.. "
            f"if score @s {WAIT} matches ..0 run function {NS}check_{case['name']}"
        )
    for checkpoint in checkpoints_data:
        tick_player.append(
            f"execute if score @s {STEP} matches {checkpoint['step']} if score @s {HEALTH} matches 1.. "
            f"if score @s {WAIT} matches ..0 run function {NS}check_{checkpoint['name']}"
        )
    write("tick_player", tick_player)

    for index, case in enumerate(vectors, 1):
        apply_lines = [
            f"scoreboard players set @s {STEP} {index}",
            f"scoreboard players set @s {WAIT} {case.get('wait', 2)}",
            f"scoreboard players set @s {AGE} 0",
            *case.get("commands", []),
        ]
        if case.get("death"):
            # 不在 kill 后写任何依赖旧玩家实体的状态。
            apply_lines.append("kill @s")
        write("apply_" + case["name"], apply_lines)
        lines = assertion_lines(case, FAIL, COUNT)
        lines.append(f"function {NS}apply_{vectors[index]['name']}" if index < len(vectors) else f"function {NS}finish")
        write("check_" + case["name"], lines)

    write("timeout", [
        f"execute if score @s {STEP} matches 1..99 run function {NS}timeout_suite",
        f"execute if score @s {STEP} matches 100.. run function {NS}timeout_pending",
    ])
    write("timeout_suite", [
        say("MITE_DEATH_XP FAIL：本阶段等待达到200个在线tick。未能确认及时重生/恢复到可断言状态，已停止。", "red"),
        f"scoreboard players add @s {FAIL} 1",
        f"function {NS}finish",
    ])
    write("finish", [
        f"scoreboard players set @s {STEP} 0",
        "tellraw @s " + json.dumps([
            {"text": f"MITE_DEATH_XP expected_assertions={assertion_total} executed="},
            {"score": {"name": "@s", "objective": COUNT}},
            {"text": " failures="}, {"score": {"name": "@s", "objective": FAIL}},
        ], ensure_ascii=False),
        "title @s times 10 600 20",
        f'execute if score @s {FAIL} matches 0 if score @s {COUNT} matches {assertion_total} run title @s subtitle {{"text":"预期红色 -2 级 · 总经验 -21 · 等级内 19 点","color":"green"}}',
        f'execute if score @s {FAIL} matches 0 if score @s {COUNT} matches {assertion_total} run title @s title {{"text":"Death XP PASS","color":"green"}}',
        f'execute if score @s {FAIL} matches 1.. run title @s title {{"text":"Death XP FAIL","color":"red"}}',
        f"execute if score @s {FAIL} matches 0 if score @s {COUNT} matches {assertion_total} run " + say(
            "MITE_DEATH_XP PASS：总经验 -21 / 等级 -2 / 等级内19点。请截图确认负等级文字为红色，再保存重进；本包不会自动重置。", "green"),
    ])

    player_entry("death_screen", "begin_saved_zero_death")
    player_entry("death_screen_19", "begin_saved_nineteen_death")
    for checkpoint in checkpoints_data:
        index = checkpoint["step"]
        write("begin_" + checkpoint["name"], [
            "# 人工断点独立于主流程的 done/fail；直到实际重生之前故意暂停计时。",
            f"scoreboard players set @s {PDONE} 0",
            f"scoreboard players set @s {PFAIL} 0",
            f"scoreboard players set @s {PCOUNT} 0",
            f"scoreboard players set @s {PKIND} {index}",
            f"scoreboard players set @s {STEP} {-index}",
            f"scoreboard players set @s {AGE} 0",
            f"scoreboard players set @s {WAIT} 40",
            f"function {NS}environment",
            "gamerule doImmediateRespawn false",
            *RESET_XP,
            f"experience add @s {checkpoint['before']} points",
            say(f"MITE_DEATH_XP 人工断点：{checkpoint['before']}XP死亡。停在死亡界面保存退出并重进，再点重生；预期总经验 {checkpoint['total']}。", "yellow"),
            "kill @s",
        ])
        write("arm_" + checkpoint["name"], [
            f"scoreboard players set @s {STEP} {index}",
            f"scoreboard players set @s {WAIT} 40",
            f"scoreboard players set @s {AGE} 0",
            f"scoreboard players set @s {PFAIL} 0",
            f"scoreboard players set @s {PCOUNT} 0",
            f"scoreboard players set @s {PDONE} 0",
            say("MITE_DEATH_XP 已检测到活着的玩家；40tick后检查该断点经验，不再修改XP。", "yellow"),
        ])
        write("check_" + checkpoint["name"], [
            *assertion_lines(checkpoint, PFAIL, PCOUNT),
            f"function {NS}finish_pending",
        ])

    write("pending_watch", [
        f"scoreboard players set @s {HEALTH} -2147483647",
        f"execute store result score @s {HEALTH} run data get entity @s Health 1000",
        *[f"execute if score @s {STEP} matches {-case['step']} if score @s {HEALTH} matches 1.. run function {NS}arm_{case['name']}"
          for case in checkpoints_data],
    ])
    player_entry("resume", "pending_watch")
    player_entry("rejoin_assertions", "rejoin_body")
    write("rejoin_body", [
        "# 显式重检只在流程已停止且玩家活着时开始，不重置经验或模式。",
        f"scoreboard players set @s {HEALTH} -2147483647",
        f"execute store result score @s {HEALTH} run data get entity @s Health 1000",
        *[f"execute if score @s {STEP} matches 0 if score @s {PKIND} matches {case['step']} if score @s {HEALTH} matches 1.. run function {NS}arm_{case['name']}"
          for case in checkpoints_data],
        f"execute if score @s {STEP} matches -102..-101 run function {NS}pending_watch",
    ])
    write("timeout_pending", [
        say("MITE_DEATH_XP PENDING FAIL：复活后的验证超过200tick，已停止。", "red"),
        f"scoreboard players add @s {PFAIL} 1",
        f"function {NS}finish_pending",
    ])
    write("finish_pending", [
        f"scoreboard players set @s {STEP} 0",
        f"scoreboard players set @s {PDONE} -1",
        f"execute if score @s {PFAIL} matches 0 if score @s {PCOUNT} matches 3 run scoreboard players set @s {PDONE} 1",
        "gamerule doImmediateRespawn true",
        "tellraw @s " + json.dumps([
            {"text": "MITE_DEATH_XP PENDING expected_assertions=3 executed="},
            {"score": {"name": "@s", "objective": PCOUNT}},
            {"text": " failures="}, {"score": {"name": "@s", "objective": PFAIL}},
        ], ensure_ascii=False),
        "title @s times 10 600 20",
        f'execute if score @s {PDONE} matches 1 run title @s title {{"text":"Saved death XP PASS","color":"green"}}',
        f'execute unless score @s {PDONE} matches 1 run title @s title {{"text":"Saved death XP FAIL","color":"red"}}',
        f"execute if score @s {PDONE} matches 1 run " + say(
            "MITE_DEATH_XP PENDING PASS：经验符合所选死亡断点。只有实际完成死亡界面保存、退出、重进后，这次结果才是持久化验证。", "green"),
    ])

    tags = root / "data/minecraft/tags/function"
    tags.mkdir(parents=True, exist_ok=True)
    for name in ("load", "tick"):
        (tags / (name + ".json")).write_text(json.dumps({"values": [NS + name]}, indent=2) + "\n", encoding="utf-8")
    validate_generated(root, auto_start_once)
    return root


def validate_generated(root: Path, auto_start_once: bool | None = None) -> dict:
    """检查输出结构、JSON、引用、状态保存与退出保护，不声称进行游戏解析。"""
    metadata = json.loads((root / "pack.mcmeta").read_text(encoding="utf-8"))
    if metadata["pack"]["pack_format"] != 48:
        raise ValueError("Minecraft 1.21.1 数据包格式应为48")
    functions = root / "data/mite_test/function/death_xp"
    paths = sorted(functions.glob("*.mcfunction"))
    contents = {path.stem: path.read_text(encoding="utf-8") for path in paths}
    names = set(contents)
    expected = {"load", "environment", "start", "start_body", "stop", "stop_body", "tick", "tick_player",
                "timeout", "timeout_suite", "finish", "death_screen", "death_screen_19", "pending_watch",
                "resume", "rejoin_assertions", "rejoin_body", "timeout_pending", "finish_pending"}
    expected.update(prefix + case["name"] for case in cases() for prefix in ("apply_", "check_"))
    expected.update(prefix + case["name"] for case in checkpoints() for prefix in ("begin_", "arm_", "check_"))
    if names != expected:
        raise ValueError("测试函数缺失或多余：" + repr(names ^ expected))
    for path in root.rglob("*.json"):
        payload = json.loads(path.read_text(encoding="utf-8"))
        if path.parent == root / "data/minecraft/tags/function":
            if path.stem not in {"load", "tick"} or payload != {"values": [NS + path.stem]}:
                raise ValueError("自动函数标签必须准确引用本包函数")
    for tag in ("load", "tick"):
        if not (root / f"data/minecraft/tags/function/{tag}.json").is_file():
            raise ValueError("缺少状态机必要的函数标签：" + tag)
    if (root / "data/minecraft/tags/functions").exists() or (root / "data/mite_test/functions").exists():
        raise ValueError("1.21.1 function目录必须为单数")
    auto_line = next((line for line in contents["tick"].splitlines() if line.endswith(f"run function {NS}start")), None)
    is_automatic = auto_line is not None
    if auto_start_once is not None and is_automatic != auto_start_once:
        raise ValueError("自动启动模式不符")
    if auto_line and f"unless score @s {DONE} matches 1.." not in auto_line:
        raise ValueError("自动启动缺少持久计分板锁")
    if len(OBJECTIVES) != len(set(OBJECTIVES)) or any(len(name) > 16 for name in OBJECTIVES):
        raise ValueError("计分板目标必须唯一且不超过16字符")
    declarations = set(re.findall(r"^scoreboard objectives add ([a-z0-9_]+) dummy$", contents["load"], re.M))
    if declarations != set(OBJECTIVES):
        raise ValueError("计分板目标声明不完整")

    commands = 0
    observed_assertions = {COUNT: 0, PCOUNT: 0}
    for path in paths:
        for number, line in enumerate(contents[path.stem].splitlines(), 1):
            if not line or line.startswith("#"):
                continue
            commands += 1
            if line.startswith("/") or "data modify entity" in line or "data merge entity" in line:
                raise ValueError(f"不允许的命令：{path.name}:{number}")
            if re.search(r"(?:^| run )(?:give|clear|effect|tp|teleport|summon|tag|schedule) ", line):
                raise ValueError("测试不能补给、传送或依赖重生不持久的标签/调度：" + line)
            for target in re.findall(r"\bfunction mite_test:death_xp/([a-z0-9_]+)", line):
                if target not in names:
                    raise ValueError("不存在的函数引用：" + target)
            if "tellraw @s " in line:
                json.loads(line.split("tellraw @s ", 1)[1])
            for marker in ("run title @s title ", "run title @s subtitle "):
                if marker in line:
                    json.loads(line.split(marker, 1)[1])
            for counter in observed_assertions:
                if line == f"scoreboard players add @s {counter} 1":
                    observed_assertions[counter] += 1
    expected_assertions = sum(len(checks(case)) for case in cases())
    if observed_assertions != {COUNT: expected_assertions, PCOUNT: 6}:
        raise ValueError("实际断言计数与固定向量不一致")
    start = [line for line in contents["start_body"].splitlines() if line and not line.startswith("#")]
    if start[0] != f"scoreboard players set @s {DONE} 1":
        raise ValueError("主流程必须首先标记已启动，防止重进重置")
    if f"execute if score @s {AGE} matches {TIMEOUT_TICKS}.. run function {NS}timeout" not in contents["tick_player"]:
        raise ValueError("缺少200tick超时保护")
    for index, case in enumerate(cases(), 1):
        body = contents["apply_" + case["name"]].splitlines()
        if body[:3] != [f"scoreboard players set @s {STEP} {index}",
                       f"scoreboard players set @s {WAIT} {case.get('wait', 2)}",
                       f"scoreboard players set @s {AGE} 0"]:
            raise ValueError("每次变更前必须保存步骤及计时：" + case["name"])
        if case.get("death") and body[-1] != "kill @s":
            raise ValueError("死亡命令必须最后执行：" + case["name"])
        dispatch = (f"execute if score @s {STEP} matches {index} if score @s {HEALTH} matches 1.. "
                    f"if score @s {WAIT} matches ..0 run function {NS}check_{case['name']}")
        if dispatch not in contents["tick_player"]:
            raise ValueError("断言必须在活着且等待结束后运行：" + case["name"])
    for checkpoint in checkpoints():
        body = contents["begin_" + checkpoint["name"]]
        if not body.endswith("kill @s\n") or "gamerule doImmediateRespawn false" not in body:
            raise ValueError("手动死亡界面断点没有正确停止立即重生")
        if f"scoreboard players set @s {STEP} {-checkpoint['step']}" not in body:
            raise ValueError("手动断点必须进入独立暂停状态")
        if re.search(r"\bexperience (?:add|set) ", contents["arm_" + checkpoint["name"]]):
            raise ValueError("恢复验证不能重写玩家经验")
    return {
        "author": "NingMeng123", "functions": len(paths), "commands": commands,
        "suite_stages": len(cases()), "suite_deaths": sum(bool(case.get("death")) for case in cases()),
        "suite_assertions": expected_assertions, "manual_checkpoints": 2, "assertions_per_checkpoint": 3,
        "timeout_ticks": TIMEOUT_TICKS, "auto_start_once": is_automatic,
        "final_state": {"total": -21, "level": -2, "points": 19},
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("test_world", type=Path, help="独立单人的复制测试存档目录")
    parser.add_argument("--auto-start-once", action="store_true", help="仅首次进服自动运行主流程，重进保留经验")
    arguments = parser.parse_args()
    destination = create(arguments.test_world.resolve(), arguments.auto_start_once)
    print(destination)
    print(json.dumps(validate_generated(destination, arguments.auto_start_once), ensure_ascii=False))
