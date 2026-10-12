"""创建死亡经验球的真实客户端测试数据包。开发者：NingMeng123。

仅用于独立单人的复制测试存档。本包会在主世界
x=9995..10035、y=199..204、z=9995..10005 构造石头平台并清出上方空间，
临时强制加载对应区块，传送玩家，修改重生点/经验/游戏规则并真正死亡一次。
测试会清理平台区域的经验球；不会清空玩家背包，但死亡可能正常掉落物品。
不要安装到原存档。末尾移除本包区域的强制加载，保留平台、重生点和测试规则。

默认 /reload 后 /function mite_test:orb/start；--auto-start-once 首次进服运行。
持久计分板跨重生保存步骤，启动立即标记，重新登录不会重置测试。
--owner-uuid 可指定玩家 UUID；默认要求目标世界 playerdata 中唯一 UUID.dat。
开始前核验实际玩家 UUID，防止把自己的球错误标为陌生归属。

客户端验证：235 XP 死亡后1tick无球、30tick已生成73+3+1+1共78点；
精确第20tick属于GameTest。本包验证真实碰撞拒绝陌生归属球、允许自身归属球，
41颗相同归属/价值球加1普通球的禁止合并，以及Age23998/5998的短时过期边界。
寿命测试不是实际等待20分钟；经验修补拦截由GameTest另外覆盖。

最终留下1颗陌生归属7点球供截图和保存重进，玩家保持7 XP。
重进后可执行 /function mite_test:orb/rejoin_assertions，只回读球与玩家状态。
/function mite_test:orb/stop 中止流程并移除本包区域强制加载，不重置经验。
此工具只生成独立开发数据包，不写入模组资源或运行游戏。
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import uuid


NS = "mite_test:orb/"
STEP, WAIT, AGE = "mite_orb_step", "mite_orb_wait", "mite_orb_age"
HEALTH, VALUE = "mite_orb_hp", "mite_orb_value"
FAIL, COUNT, DONE = "mite_orb_fail", "mite_orb_count", "mite_orb_done"
RFAIL, RCOUNT = "mite_orb_rfail", "mite_orb_rcount"
OBJECTIVES = (STEP, WAIT, AGE, HEALTH, VALUE, FAIL, COUNT, DONE, RFAIL, RCOUNT)
AREA = "x=9995,y=198,z=9995,dx=40,dy=8,dz=10"
DEATH_AREA = "x=9995,y=198,z=9995,dx=10,dy=8,dz=10"
FORCE_RANGE = "9995 9995 10035 10005"
ATTACHMENT = '"neoforge:attachments":{"mite:death_orb_owner":'


def resolve_owner(world: Path, supplied: str | uuid.UUID | None) -> uuid.UUID:
    if supplied is not None:
        return uuid.UUID(str(supplied))
    candidates = []
    for path in (world / "playerdata").glob("*.dat"):
        if path.is_file():
            try:
                candidates.append(uuid.UUID(path.stem))
            except ValueError:
                continue
    if len(candidates) != 1:
        raise ValueError("目标世界必须有唯一 playerdata/UUID.dat；否则请显式传 --owner-uuid")
    return candidates[0]


def uuid_array(value: uuid.UUID) -> str:
    words = [int.from_bytes(value.bytes[index:index + 4], "big", signed=True) for index in range(0, 16, 4)]
    return "[I;" + ",".join(map(str, words)) + "]"


def ownership(owner: uuid.UUID) -> str:
    return "{" + ATTACHMENT + "{version:1,owner:" + uuid_array(owner) + "}}}"


def selector(*parts: str) -> str:
    return "@e[type=minecraft:experience_orb," + ",".join(parts) + "]"


def summon(tag: str, value: int, position: str, owner: uuid.UUID | None = None,
           age: int = 0, extra_tag: str | None = None) -> str:
    tags = [tag] + ([extra_tag] if extra_tag else [])
    payload = ("{Tags:" + json.dumps(tags, separators=(",", ":"))
               + f",Value:{value}s,Age:{age}s,Count:1,NoGravity:1b,Motion:[0.0d,0.0d,0.0d]")
    if owner is not None:
        payload += "," + ownership(owner)[1:-1]
    return f"summon minecraft:experience_orb {position} " + payload + "}"


def say(message: str, color: str | None = None) -> str:
    payload = {"text": message}
    if color:
        payload["color"] = color
    return "tellraw @s " + json.dumps(payload, ensure_ascii=False)


def create(world: Path, auto_start_once: bool = False, owner_uuid: str | uuid.UUID | None = None) -> Path:
    owner = resolve_owner(world, owner_uuid)
    foreign = uuid.UUID(int=1 if owner.int != 1 else 2)
    root = world / "datapacks/mite-death-orb-smoke"
    functions = root / "data/mite_test/function/orb"
    functions.mkdir(parents=True, exist_ok=True)
    (root / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 48, "description": "MITE 死亡经验球开发测试 — NingMeng123",
    }}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    assertions = {COUNT: 0, RCOUNT: 0}

    def write(name: str, lines: list[str]):
        (functions / (name + ".mcfunction")).write_text("\n".join(lines) + "\n", encoding="utf-8")

    def check(label: str, command: str, expected: str | int, rejoin: bool = False) -> list[str]:
        counter, failures = (RCOUNT, RFAIL) if rejoin else (COUNT, FAIL)
        assertions[counter] += 1
        return [
            f"scoreboard players set @s {VALUE} -2147483647",
            f"execute store result score @s {VALUE} run {command}",
            f"scoreboard players add @s {counter} 1",
            f"execute unless score @s {VALUE} matches {expected} run scoreboard players add @s {failures} 1",
            "tellraw @s " + json.dumps([
                {"text": f"MITE_ORB {label} expected={expected} actual="},
                {"score": {"name": "@s", "objective": VALUE}},
            ], ensure_ascii=False),
        ]

    def arm(step: int, delay: int) -> list[str]:
        return [f"scoreboard players set @s {STEP} {step}",
                f"scoreboard players set @s {WAIT} {delay}",
                f"scoreboard players set @s {AGE} 0"]

    def count_orbs(*parts: str) -> str:
        return "execute if entity " + selector(*parts)

    def orb_data(tag: str, path: str) -> str:
        return f"data get entity {selector('tag=' + tag, 'limit=1')} {path} 1"

    owned = "nbt=" + ownership(owner)
    foreign_owned = "nbt=" + ownership(foreign)
    write("load", ["# 只注册目标；不重置已经保存的步骤或启动标记。",
                   *[f"scoreboard objectives add {name} dummy" for name in OBJECTIVES]])
    write("start", [f"execute as @s[type=minecraft:player] at @s run function {NS}start_body"])
    player_selector = "@s[nbt={UUID:" + uuid_array(owner) + "}]"
    write("start_body", [
        f"scoreboard players set @s {DONE} 1",
        f"scoreboard players set @s {STEP} 0",
        f"scoreboard players set @s {FAIL} 0",
        f"scoreboard players set @s {COUNT} 0",
        f"execute unless entity {player_selector} run scoreboard players set @s {FAIL} 1",
        f"execute unless entity {player_selector} run " + say(
            f"MITE_ORB FAIL：当前玩家UUID不等于生成参数 {owner}，未修改平台或角色；请更正--owner-uuid。", "red"),
        f"execute if entity {player_selector} run function {NS}begin",
    ])
    write("begin", [
        "# 所有世界修改只允许用于单人复制测试存档。",
        "difficulty peaceful",
        "gamerule naturalRegeneration false",
        "gamerule doMobSpawning false",
        "gamerule doMobLoot true",
        "gamerule keepInventory false",
        "gamerule doImmediateRespawn true",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "time set day",
        "weather clear",
        "gamemode survival @s",
        f"execute in minecraft:overworld run forceload add {FORCE_RANGE}",
        *arm(1, 0),
        say("MITE_ORB 准备测试平台；将真实死亡一次，并验证球的归属、延迟、合并和寿命边界。", "yellow"),
    ])
    write("build_platform", [
        "fill 9995 199 9995 10035 199 10005 minecraft:stone",
        "fill 9995 200 9995 10035 204 10005 minecraft:air",
        "kill " + selector(AREA),
        "tp @s 10000.5 200 10000.5",
        "spawnpoint @s 10020 200 10000",
        "experience set @s 0 levels",
        "experience set @s 0 points",
        "experience add @s 235 points",
        *arm(2, 1),
        "# kill为最后一条命令；新玩家实体通过持久计分板接续测试。",
        "kill @s",
    ])
    tick = [f"execute as @a[scores={{{STEP}=1..}}] at @s run function {NS}tick_player"]
    if auto_start_once:
        tick.append(f"execute as @a unless score @s {DONE} matches 1.. at @s run function {NS}start")
    write("tick", tick)
    tick_player = [
        f"scoreboard players add @s {AGE} 1",
        f"scoreboard players remove @s {WAIT} 1",
        f"scoreboard players set @s {HEALTH} -2147483647",
        f"execute store result score @s {HEALTH} run data get entity @s Health 1000",
        f"execute if score @s {AGE} matches 200.. run function {NS}timeout",
        f"execute if score @s {STEP} matches 1 if score @s {HEALTH} matches 1.. "
        "in minecraft:overworld if loaded 9995 199 9995 if loaded 10035 199 10005 "
        "if loaded 9995 199 10005 if loaded 10035 199 9995 "
        f"run function {NS}build_platform",
        # 检查1tick无球必须允许玩家仍处于死亡状态；其余读玩家结果的阶段要求活着。
        f"execute if score @s {STEP} matches 2 if score @s {WAIT} matches ..0 "
        f"in minecraft:overworld run function {NS}check_early",
    ]
    for step, name in [(3, "check_death"), (4, "check_foreign"), (5, "check_own"),
                       (6, "check_merge"), (7, "check_expiry"), (8, "check_display")]:
        tick_player.append(
            f"execute if score @s {STEP} matches {step} if score @s {HEALTH} matches 1.. "
            f"if score @s {WAIT} matches ..0 in minecraft:overworld run function {NS}{name}"
        )
    write("tick_player", tick_player)
    write("check_early", [
        *check("death_plus_1_tick_no_orbs", count_orbs(DEATH_AREA), 0),
        # 从前一阶段起再过29tick，即死亡后约30tick；精确20tick在GameTest验证。
        *arm(3, 29),
    ])
    write("read_weighted_total", [
        f"scoreboard players set #orb_sum {VALUE} 0",
        f"execute as {selector(DEATH_AREA)} run function {NS}add_orb_value",
    ])
    write("add_orb_value", [
        f"scoreboard players set #orb_value {VALUE} -1000000",
        f"scoreboard players set #orb_count {VALUE} -1000000",
        f"execute store result score #orb_value {VALUE} run data get entity @s Value 1",
        f"execute store result score #orb_count {VALUE} run data get entity @s Count 1",
        f"scoreboard players operation #orb_value {VALUE} *= #orb_count {VALUE}",
        f"scoreboard players operation #orb_sum {VALUE} += #orb_value {VALUE}",
    ])
    write("check_death", [
        *check("respawn_level", "experience query @s levels", 0),
        *check("respawn_total", "data get entity @s XpTotal 1", 0),
        *check("death_orb_count", count_orbs(DEATH_AREA), 4),
        *check("death_owner_nbt", count_orbs(DEATH_AREA, owned), 4),
        f"function {NS}read_weighted_total",
        *check("death_total_value_times_count", f"scoreboard players get #orb_sum {VALUE}", 78),
        *check("death_value_73", count_orbs(DEATH_AREA, "nbt={Value:73s}"), 1),
        *check("death_value_3", count_orbs(DEATH_AREA, "nbt={Value:3s}"), 1),
        *check("death_value_1", count_orbs(DEATH_AREA, "nbt={Value:1s}"), 2),
        *check("death_each_count_one", count_orbs(DEATH_AREA, "nbt={Count:1}"), 4),
        # 数据已验收；清理这4颗真实掉落球，避免后续位置移动污染XP。
        "kill " + selector(DEATH_AREA),
        "tp @s 10020.5 200 10000.5",
        summon("mite_orb_foreign", 7, "10020.5 200.2 10000.5", foreign),
        *arm(4, 10),
    ])
    write("check_foreign", [
        *check("foreign_touch_no_xp", "data get entity @s XpTotal 1", 0),
        *check("foreign_touch_still_present", count_orbs("tag=mite_orb_foreign"), 1),
        *check("foreign_value", orb_data("mite_orb_foreign", "Value"), 7),
        *check("foreign_count", orb_data("mite_orb_foreign", "Count"), 1),
        *check("foreign_owner_nbt", count_orbs("tag=mite_orb_foreign", foreign_owned), 1),
        summon("mite_orb_self", 7, "10020.5 200.2 10000.5", owner),
        *arm(5, 10),
    ])
    write("check_own", [
        *check("self_touch_total", "data get entity @s XpTotal 1", 7),
        *check("self_touch_points", "experience query @s points", 7),
        *check("self_touch_consumed", count_orbs("tag=mite_orb_self"), 0),
        *check("foreign_remains_after_self_pickup", count_orbs("tag=mite_orb_foreign"), 1),
        f"function {NS}prepare_merge",
    ])
    write("prepare_merge", [
        "# 41颗球必有两个ID模40相同；不会因只有两颗球碰巧不同组合而假通过。",
        *[summon("mite_orb_merge_owned", 11, "10030.5 200.2 10000.5", foreign,
                 extra_tag="mite_orb_merge") for _ in range(41)],
        summon("mite_orb_merge_public", 11, "10030.5 200.2 10000.5", extra_tag="mite_orb_merge"),
        *arm(6, 45),
    ])
    write("check_merge", [
        *check("merge_probe_all_entities", count_orbs("tag=mite_orb_merge"), 42),
        *check("merge_probe_owned_entities", count_orbs("tag=mite_orb_merge_owned", foreign_owned), 41),
        *check("merge_probe_public_entity", count_orbs("tag=mite_orb_merge_public"), 1),
        *check("merge_probe_each_count_one", count_orbs("tag=mite_orb_merge", "nbt={Count:1}"), 42),
        "kill " + selector("tag=mite_orb_merge"),
        summon("mite_orb_expiry_owned", 7, "10030.5 200.2 10000.5", foreign, age=23998),
        summon("mite_orb_expiry_public", 7, "10032.5 200.2 10000.5", age=5998),
        *check("expiry_owned_initial_count", count_orbs("tag=mite_orb_expiry_owned"), 1),
        *check("expiry_owned_initial_age", orb_data("mite_orb_expiry_owned", "Age"), 23998),
        *check("expiry_public_initial_count", count_orbs("tag=mite_orb_expiry_public"), 1),
        *check("expiry_public_initial_age", orb_data("mite_orb_expiry_public", "Age"), 5998),
        *arm(7, 5),
    ])
    write("check_expiry", [
        *check("expiry_owned_disappeared", count_orbs("tag=mite_orb_expiry_owned"), 0),
        *check("expiry_public_disappeared", count_orbs("tag=mite_orb_expiry_public"), 0),
        *check("expiry_did_not_grant_xp", "data get entity @s XpTotal 1", 7),
        "kill " + selector(AREA),
        "tp @s 10020.5 200 10000.5 -90 35",
        summon("mite_orb_display", 7, "10022.5 200.2 10000.5", foreign),
        *arm(8, 2),
    ])

    def display_checks(rejoin: bool = False) -> list[str]:
        lines = [
            *check("display_count", count_orbs("tag=mite_orb_display"), 1, rejoin),
            *check("display_owner_nbt", count_orbs("tag=mite_orb_display", foreign_owned), 1, rejoin),
            *check("display_value", orb_data("mite_orb_display", "Value"), 7, rejoin),
            *check("display_count_field", orb_data("mite_orb_display", "Count"), 1, rejoin),
            *check("player_total_unchanged", "data get entity @s XpTotal 1", 7, rejoin),
        ]
        if rejoin:
            lines.extend(check("display_age_in_lifetime", orb_data("mite_orb_display", "Age"), "0..23999", True))
        return lines

    write("check_display", [*display_checks(), f"function {NS}finish"])
    total_assertions = assertions[COUNT]
    write("timeout", [
        f"scoreboard players add @s {FAIL} 1",
        say("MITE_ORB FAIL：当前阶段超过200个在线tick；区块未就绪或未成功立即重生，流程已停止。", "red"),
        f"function {NS}finish",
    ])
    write("finish", [
        f"scoreboard players set @s {STEP} 0",
        f"execute in minecraft:overworld run forceload remove {FORCE_RANGE}",
        "tellraw @s " + json.dumps([
            {"text": f"MITE_ORB expected_assertions={total_assertions} executed="},
            {"score": {"name": "@s", "objective": COUNT}},
            {"text": " failures="}, {"score": {"name": "@s", "objective": FAIL}},
        ], ensure_ascii=False),
        "title @s times 10 600 20",
        f'execute if score @s {FAIL} matches 0 if score @s {COUNT} matches {total_assertions} run title @s title {{"text":"Death orbs PASS","color":"green"}}',
        f'execute if score @s {FAIL} matches 1.. run title @s title {{"text":"Death orbs FAIL","color":"red"}}',
        f"execute if score @s {FAIL} matches 0 if score @s {COUNT} matches {total_assertions} run " + say(
            "MITE_ORB PASS：前方留有陌生归属7点球，玩家7XP。请截图后保存退出重进，再运行 mite_test:orb/rejoin_assertions。", "green"),
        say("MITE_ORB 寿命仅测Age23998/5998边界；并未实际等待20分钟。精确20tick、经验修补与生成时合并另由GameTest覆盖。"),
    ])
    write("stop", [
        f"scoreboard players set @s {STEP} 0",
        f"execute in minecraft:overworld run forceload remove {FORCE_RANGE}",
        say("MITE_ORB 已停止并移除测试区域强制加载；经验、平台和一次性标记保留。", "yellow"),
    ])
    write("rejoin_assertions", [
        f"execute as @s[type=minecraft:player] if score @s {STEP} matches 0 "
        f"in minecraft:overworld run function {NS}rejoin_body",
    ])
    write("rejoin_body", [
        "# 只读取保存后的实体附件、年龄与玩家XP，不重新召唤球、不重置经验。",
        f"scoreboard players set @s {RFAIL} 0",
        f"scoreboard players set @s {RCOUNT} 0",
        *display_checks(True),
        "tellraw @s " + json.dumps([
            {"text": "MITE_ORB REJOIN expected_assertions=6 executed="},
            {"score": {"name": "@s", "objective": RCOUNT}},
            {"text": " failures="}, {"score": {"name": "@s", "objective": RFAIL}},
        ], ensure_ascii=False),
        f"execute if score @s {RFAIL} matches 0 run " + say(
            "MITE_ORB REJOIN PASS：归属附件、球值和玩家XP仍一致；只有实际保存退出重进后才算持久化验收。", "green"),
        f"execute if score @s {RFAIL} matches 1.. run " + say("MITE_ORB REJOIN FAIL：请检查上面的实际读数。", "red"),
    ])
    for name in ("load", "tick"):
        path = root / f"data/minecraft/tags/function/{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"values": [NS + name]}, indent=2) + "\n", encoding="utf-8")
    (root / "test_manifest.json").write_text(json.dumps({
        "author": "NingMeng123", "owner_uuid": str(owner), "foreign_uuid": str(foreign),
        "auto_start_once": auto_start_once, "suite_assertions": total_assertions,
        "rejoin_assertions": assertions[RCOUNT], "scope": "独立单人复制世界的固定主世界测试平台",
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    validate_generated(root)
    return root


def validate_generated(root: Path) -> dict:
    """检查格式与引用；不冒充Brigadier/SNBT解析或真实客户端验收。"""
    metadata = json.loads((root / "pack.mcmeta").read_text(encoding="utf-8"))
    if metadata["pack"]["pack_format"] != 48:
        raise ValueError("Minecraft1.21.1必须使用数据包格式48")
    manifest = json.loads((root / "test_manifest.json").read_text(encoding="utf-8"))
    owner, foreign = uuid.UUID(manifest["owner_uuid"]), uuid.UUID(manifest["foreign_uuid"])
    if owner == foreign:
        raise ValueError("陌生owner不能等于玩家UUID")
    paths = sorted((root / "data/mite_test/function/orb").glob("*.mcfunction"))
    text = {path.stem: path.read_text(encoding="utf-8") for path in paths}
    names = set(text)
    expected = {"load", "start", "start_body", "begin", "build_platform", "tick", "tick_player",
                "check_early", "read_weighted_total", "add_orb_value", "check_death", "check_foreign",
                "check_own", "prepare_merge", "check_merge", "check_expiry", "check_display", "timeout",
                "finish", "stop", "rejoin_assertions", "rejoin_body"}
    if names != expected:
        raise ValueError("测试函数缺失或多余：" + repr(names ^ expected))
    for path in root.rglob("*.json"):
        payload = json.loads(path.read_text(encoding="utf-8"))
        if path.parent == root / "data/minecraft/tags/function":
            if path.stem not in {"load", "tick"} or payload != {"values": [NS + path.stem]}:
                raise ValueError("函数标签引用不正确")
    for name in ("load", "tick"):
        if not (root / f"data/minecraft/tags/function/{name}.json").is_file():
            raise ValueError("缺少必要的函数标签：" + name)
    if len(set(OBJECTIVES)) != len(OBJECTIVES) or any(len(name) > 16 for name in OBJECTIVES):
        raise ValueError("计分板名称不合法")
    if set(re.findall(r"^scoreboard objectives add (\w+) dummy$", text["load"], re.M)) != set(OBJECTIVES):
        raise ValueError("计分板声明不完整")
    commands, summons = 0, []
    counts = {COUNT: 0, RCOUNT: 0}
    for path in paths:
        for number, line in enumerate(text[path.stem].splitlines(), 1):
            if not line or line.startswith("#"):
                continue
            commands += 1
            if line.startswith("/") or "data modify entity" in line or "data merge entity" in line:
                raise ValueError(f"不允许的命令：{path.name}:{number}")
            if re.search(r"(?:^| run )(?:give|clear|effect|tag|schedule) ", line):
                raise ValueError("不允许补给、清空背包或依赖玩家标签调度：" + line)
            for target in re.findall(r"\bfunction mite_test:orb/([a-z0-9_]+)", line):
                if target not in names:
                    raise ValueError("不存在的函数引用：" + target)
            if "tellraw @s " in line:
                json.loads(line.split("tellraw @s ", 1)[1])
            if "run title @s title " in line:
                json.loads(line.split("run title @s title ", 1)[1])
            for counter in counts:
                if line == f"scoreboard players add @s {counter} 1":
                    counts[counter] += 1
            if line.startswith("summon "):
                if not line.startswith("summon minecraft:experience_orb "):
                    raise ValueError("只能召唤测试经验球")
                summons.append(line)
                if '"mite:death_orb_owner"' in line and ownership(owner)[1:-1] not in line and ownership(foreign)[1:-1] not in line:
                    raise ValueError("球的owner附件不是固定的version1/UUID整数数组")
    if counts != {COUNT: 35, RCOUNT: 6} or manifest["suite_assertions"] != 35 or manifest["rejoin_assertions"] != 6:
        raise ValueError("断言计数不符：" + repr(counts))
    if sum('"mite_orb_merge_owned"' in line for line in summons) != 41:
        raise ValueError("合并测试必须有41颗归属球，以覆盖ID模40分组")
    if sum('"mite_orb_merge_public"' in line for line in summons) != 1:
        raise ValueError("合并测试必须包含1颗普通球")
    if "Age:23998s" not in text["check_merge"] or "Age:5998s" not in text["check_merge"]:
        raise ValueError("寿命边界向量缺失")
    if f"scoreboard players set @s {DONE} 1" != text["start_body"].splitlines()[0]:
        raise ValueError("启动必须立即持久标记")
    if (f"run function {NS}start" in text["tick"]) != manifest["auto_start_once"]:
        raise ValueError("自动启动模式不一致")
    if "UUID:" + uuid_array(owner) not in text["start_body"]:
        raise ValueError("缺少真实玩家UUID核验")
    if not text["build_platform"].endswith("kill @s\n"):
        raise ValueError("死亡前必须完成持久步骤设置")
    for snippet in (f"{AGE} matches 200..", "if loaded 9995 199 9995", "if loaded 10035 199 10005"):
        if snippet not in text["tick_player"]:
            raise ValueError("缺少区块或超时保护：" + snippet)
    for name in ("finish", "stop"):
        if f"forceload remove {FORCE_RANGE}" not in text[name]:
            raise ValueError("结束或停止时必须移除测试区域强制加载")
    if re.search(r"(?:^| run )(?:summon|kill|tp|experience (?:add|set)) ", text["rejoin_body"], re.M):
        raise ValueError("重进检查只能回读，不能改变球或经验")
    return {"author": "NingMeng123", "functions": len(paths), "commands": commands,
            "suite_assertions": 35, "rejoin_assertions": 6, "summoned_probes": len(summons),
            "owner_uuid": str(owner), "foreign_uuid": str(foreign),
            "auto_start_once": manifest["auto_start_once"], "timeout_ticks": 200}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("test_world", type=Path, help="独立单人的复制测试存档目录")
    parser.add_argument("--auto-start-once", action="store_true")
    parser.add_argument("--owner-uuid", help="被测真实玩家UUID；默认读取唯一playerdata文件名")
    arguments = parser.parse_args()
    destination = create(arguments.test_world.resolve(), arguments.auto_start_once, arguments.owner_uuid)
    print(destination)
    print(json.dumps(validate_generated(destination), ensure_ascii=False))
