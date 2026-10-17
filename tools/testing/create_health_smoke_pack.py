"""Create an opt-in datapack for real-player health smoke tests. Author: NingMeng123.

Use only in a disposable test world: the test changes gamerules, effects, and XP.
This pack is never included in the mod JAR.
"""
import argparse
import json
from pathlib import Path


def create(world: Path) -> Path:
    root = world / 'datapacks' / 'mite-health-smoke'
    functions = root / 'data' / 'mite_test' / 'function' / 'health'
    functions.mkdir(parents=True, exist_ok=True)
    (root / 'pack.mcmeta').write_text(json.dumps({'pack': {
        'pack_format': 48, 'description': 'MITE health smoke test by NingMeng123'
    }}, indent=2) + '\n', encoding='utf-8')

    def write(name, lines):
        (functions / (name + '.mcfunction')).write_text('\n'.join(lines) + '\n', encoding='utf-8')

    # Expected values are the independently extracted R196 level boundaries.
    # Two ticks separate XP mutations and assertions to exercise server events.
    cases = [
        ('zero', 6, 6, 6, ['experience set @s 4 levels']),
        ('four', 6, 6, 6, ['experience set @s 5 levels']),
        ('five_no_heal', 8, 8, 6, ['experience set @s 10 levels']),
        ('ten_no_heal', 10, 10, 6, ['experience set @s 34 levels']),
        ('thirty_four', 18, 18, 6, ['experience set @s 35 levels']),
        ('thirty_five', 20, 20, 6, ['experience set @s 40 levels']),
        ('forty_cap', 20, 20, 6, ['effect give @s minecraft:instant_health 1 5 true']),
        ('healed', 20, 20, 20, ['experience set @s 5 levels', 'effect clear @s minecraft:instant_health']),
        ('downlevel_clamp', 8, 8, 8, ['attribute @s minecraft:generic.max_health modifier add mite_test:bonus 4 add_value']),
        ('modifier_preserved', 8, 12, 8, ['attribute @s minecraft:generic.max_health modifier remove mite_test:bonus']),
        ('modifier_removed', 8, 8, 8, ['gamemode creative @s']),
        ('creative', 8, 8, 8, ['gamemode spectator @s']),
        ('spectator', 8, 8, 8, ['gamemode survival @s']),
        ('survival', 8, 8, 8, []),
    ]
    write('start', [
        'scoreboard objectives add mite_hp dummy',
        'scoreboard objectives add mite_fail dummy',
        'tag @s add mite_health_test',
        'scoreboard players set @s mite_fail 0',
        'gamerule naturalRegeneration false',
        'gamerule doMobSpawning false',
        'time set day',
        'gamemode survival @s',
        'effect give @s minecraft:resistance infinite 4 true',
        'experience set @s 0 levels',
        'attribute @s minecraft:generic.max_health modifier remove mite_test:bonus',
        'effect give @s minecraft:instant_health 1 5 true',
        'schedule function mite_test:health/zero 2t replace',
    ])
    for i, (name, base, effective, current, mutations) in enumerate(cases):
        write(name, [f'execute as @a[tag=mite_health_test] at @s run function mite_test:health/{name}_body'])
        lines = []
        for label, command, value in [
            ('base', 'attribute @s minecraft:generic.max_health base get 1', base),
            ('effective', 'attribute @s minecraft:generic.max_health get 1', effective),
            ('current', 'data get entity @s Health 1', current),
        ]:
            lines += [f'execute store result score @s mite_hp run {command}',
                      f'execute unless score @s mite_hp matches {value} run scoreboard players add @s mite_fail 1',
                      'tellraw @s ' + json.dumps([
                          {'text': f'MITE_HEALTH {name} {label} expected={value} actual='},
                          {'score': {'name': '@s', 'objective': 'mite_hp'}}
                      ])]
        lines += mutations
        if i + 1 < len(cases):
            lines.append(f'schedule function mite_test:health/{cases[i+1][0]} 2t replace')
        else:
            lines += [
                'tellraw @s [{"text":"MITE_HEALTH failures="},{"score":{"name":"@s","objective":"mite_fail"}}]',
                'execute if score @s mite_fail matches 0 run title @s title {"text":"Health checks PASS","color":"green"}',
                'execute unless score @s mite_fail matches 0 run title @s title {"text":"Health checks FAIL","color":"red"}',
                'tag @s remove mite_health_test',
            ]
        write(name + '_body', lines)
    return root


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('test_world', type=Path)
    args = parser.parse_args()
    print(create(args.test_world.resolve()))
