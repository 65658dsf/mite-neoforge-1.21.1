"""在复制测试存档中安装完整经验球寿命测试。开发者：NingMeng123。

进游戏先执行 /tick rate 1000，再执行 /function mite_test:lifetime/start。
两种球从 Age=0 开始，观察普通球 6000、死亡球 24000 个实体 tick。
本包不改 Age、不强制加载区块；保持玩家在主世界测试平台 (11000,200,11000) 附近。
仅在独立副本使用。成功、失败、超时或 /function mite_test:lifetime/stop
后都必须在命令栏执行 /tick rate 20；函数的默认权限2不足以运行此权限3命令。
此包不修改TPS，测试未运行时无副作用，无自动启动。
"""
from pathlib import Path
import argparse
import json


def create(world: Path) -> Path:
    root = world / "datapacks/mite-orb-lifetime"
    functions = root / "data/mite_test/function/lifetime"
    functions.mkdir(parents=True, exist_ok=True)
    (root / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 48, "description": "MITE 完整寿命加速测试 — NingMeng123"
    }}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def write(name, lines):
        (functions / (name + ".mcfunction")).write_text("\n".join(lines) + "\n", encoding="utf-8")

    owned = '@e[type=minecraft:experience_orb,tag=mite_full_owned]'
    public = '@e[type=minecraft:experience_orb,tag=mite_full_public]'
    one_owned = owned[:-1] + ',limit=1]'
    one_public = public[:-1] + ',limit=1]'
    write("load", ["scoreboard objectives add mite_life dummy"])
    write("start", [
        "execute unless score #active mite_life matches 1 in minecraft:overworld run function mite_test:lifetime/begin"
    ])
    write("begin", [
        "# 只在专用测试副本、主世界平台附近执行。不会修改任何球的Age。",
        f"kill {owned}", f"kill {public}",
        "scoreboard players set #elapsed mite_life 0",
        "scoreboard players set #ownedLast mite_life -1",
        "scoreboard players set #publicLast mite_life -1",
        "scoreboard players set #publicDone mite_life 0",
        "scoreboard players set #fail mite_life 0",
        'summon minecraft:experience_orb 11005.5 202.2 11005.5 {Tags:["mite_full_owned"],Value:7s,Age:0s,Count:1,NoGravity:1b,Motion:[0.0d,0.0d,0.0d],"neoforge:attachments":{"mite:death_orb_owner":{version:1,owner:[I;0,0,0,1]}}}',
        'summon minecraft:experience_orb 11022.5 202.2 11005.5 {Tags:["mite_full_public"],Value:7s,Age:0s,Count:1,NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}',
        f"execute unless entity {owned} run scoreboard players add #fail mite_life 1",
        f"execute unless entity {public} run scoreboard players add #fail mite_life 1",
        "scoreboard players set #active mite_life 1",
        'tellraw @a {"text":"MITE_LIFETIME START Age=0; ordinary=6000; owned=24000. Use /tick rate 20 after finish/stop.","color":"yellow"}',
        "execute if score #fail mite_life matches 1.. run function mite_test:lifetime/finish",
    ])
    write("tick", [
        "execute if score #active mite_life matches 1 in minecraft:overworld run function mite_test:lifetime/observe"
    ])
    write("observe", [
        "scoreboard players add #elapsed mite_life 1",
        f"execute if entity {owned} store result score #ownedLast mite_life run data get entity {one_owned} Age 1",
        f"execute if entity {public} store result score #publicLast mite_life run data get entity {one_public} Age 1",
        f"execute unless entity {public} if score #publicDone mite_life matches 0 run function mite_test:lifetime/public_gone",
        f"execute if score #active mite_life matches 1 unless entity {owned} run function mite_test:lifetime/owned_gone",
        "execute if score #active mite_life matches 1 if score #elapsed mite_life matches 24005.. run function mite_test:lifetime/timeout",
    ])
    write("public_gone", [
        "execute unless score #publicLast mite_life matches 5999 run scoreboard players add #fail mite_life 1",
        "execute unless score #elapsed mite_life matches 5999..6001 run scoreboard players add #fail mite_life 1",
        "scoreboard players set #publicDone mite_life 1",
        'tellraw @a [{"text":"MITE_LIFETIME PUBLIC removed; lastAge="},{"score":{"name":"#publicLast","objective":"mite_life"}},{"text":"; elapsed="},{"score":{"name":"#elapsed","objective":"mite_life"}}]',
        "execute if score #fail mite_life matches 1.. run function mite_test:lifetime/finish",
    ])
    write("owned_gone", [
        "execute unless score #ownedLast mite_life matches 23999 run scoreboard players add #fail mite_life 1",
        "execute unless score #elapsed mite_life matches 23999..24001 run scoreboard players add #fail mite_life 1",
        "execute unless score #publicDone mite_life matches 1 run scoreboard players add #fail mite_life 1",
        "function mite_test:lifetime/finish",
    ])
    write("timeout", [
        "scoreboard players add #fail mite_life 1",
        'tellraw @a {"text":"MITE_LIFETIME TIMEOUT: check loaded chunks and entity ticking.","color":"red"}',
        "function mite_test:lifetime/finish",
    ])
    write("finish", [
        "scoreboard players set #active mite_life 0",
        f"kill {owned}", f"kill {public}",
        'tellraw @a [{"text":"MITE_LIFETIME elapsed="},{"score":{"name":"#elapsed","objective":"mite_life"}},{"text":" ownedLast="},{"score":{"name":"#ownedLast","objective":"mite_life"}},{"text":" failures="},{"score":{"name":"#fail","objective":"mite_life"}},{"text":"; NOW RUN /tick rate 20"}]',
        'execute if score #fail mite_life matches 0 run tellraw @a {"text":"MITE_LIFETIME PASS: complete Age=0 to expiry under accelerated simulation.","color":"green"}',
        'execute if score #fail mite_life matches 1.. run tellraw @a {"text":"MITE_LIFETIME FAIL","color":"red"}',
    ])
    write("stop", [
        "scoreboard players set #active mite_life 0",
        f"execute in minecraft:overworld run kill {owned}",
        f"execute in minecraft:overworld run kill {public}",
        'tellraw @a {"text":"MITE_LIFETIME STOPPED; NOW RUN /tick rate 20. This is not a pass.","color":"yellow"}',
    ])
    for name in ("load", "tick"):
        path = root / f"data/minecraft/tags/function/{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"values": ["mite_test:lifetime/" + name]}) + "\n", encoding="utf-8")
    return root


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("test_world", type=Path)
    args = parser.parse_args()
    print(create(args.test_world.resolve()))
