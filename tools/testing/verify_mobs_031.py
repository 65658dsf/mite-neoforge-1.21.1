"""Audit source coverage, source assets, generated resources and the installable JAR. Author: NingMeng123."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads((ROOT / path).read_text(encoding='utf-8'))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, default=ROOT / 'build/libs/mite-0.1.0.jar')
    args = parser.parse_args()
    failures, checks = [], 0

    def check(value, message):
        nonlocal checks
        checks += 1
        if not value:
            failures.append(message)

    mobs = read('spec/mobs-source-031.json')
    living = [r for r in mobs['entities'] if 512 <= r['legacy_id'] <= 540]
    check(len(living) == 29 and len({r['modern_id'] for r in living}) == 29, '29 source living identities')
    check([r['legacy_id'] for r in living] == list(range(512, 541)), 'Actual source IDs are contiguous 512..540')
    for source, runtime in [('spec/mobs-source-031.json', 'src/main/resources/mite/mobs-031.json'),
                            ('spec/mob-assets-031.json', 'src/main/resources/mite/mob-assets-031.json'),
                            ('spec/stacking-map-030.json', 'src/main/resources/mite/stacking-030.json')]:
        check((ROOT / source).read_bytes() == (ROOT / runtime).read_bytes(), 'Runtime equality: ' + source)
    spawn = read('spec/spawning-source-031.json')
    spawn_runtime = read('src/main/resources/mite/spawning-031.json')
    # The extractor intentionally packages only runtime pools; provenance/control-flow stay in the public spec.
    expected_spawn = {key: spawn[key] for key in ('author', 'schema_version', 'archive_sha256', 'biomes', 'living_entities', 'structure_pools')}
    check(spawn_runtime == expected_spawn, 'Runtime spawn pools equal the extractor projection of the complete source spec')
    for source in ('spec/mobs-source-031.json', 'spec/mob-behavior-source-031.json'):
        text = (ROOT / source).read_text(encoding='utf-8')
        check('"body":' not in text and '"source_code":' not in text, 'No complete decompiled methods: ' + source)
    mapping = {r['modern_id']: r for r in read('spec/stacking-map-030.json')['mappedItems']}
    sounds = read('src/generated/resources/assets/mite/sounds.json')
    manifest = read('spec/mob-assets-031.json')
    check(manifest['png_count'] == manifest['ogg_count'] == 44, 'Confirmed original assets count')
    for lang in ('zh_cn', 'en_us'):
        entries = read('src/main/resources/assets/mite/lang/' + lang + '.json')
        for r in living:
            path = r['modern_id'].split(':', 1)[1]
            check('entity.mite.' + path in entries and 'item.mite.' + path + '_spawn_egg' in entries, 'Localized identity ' + path)
    for r in living:
        mid = r['modern_id'] + '_spawn_egg'
        check(mapping[mid]['max_stack_size'] == 16, 'Source egg limit ' + mid)
        check((ROOT / 'src/generated/resources/assets/mite/models/item' / (mid.split(':')[1] + '.json')).is_file(), 'Generated egg ' + mid)
    for name, event in manifest['sounds'].items():
        check(sounds.get(name) == event, 'Source sound registration ' + name)
    with zipfile.ZipFile(args.jar) as archive:
        names = set(archive.namelist())
        for asset in manifest['files']:
            data = (ROOT / 'src/main/resources' / asset['destination']).read_bytes()
            check(hashlib.sha256(data).hexdigest() == asset['sha256'], 'Original bytes ' + asset['destination'])
            check(asset['destination'] in names and archive.read(asset['destination']) == data, 'Packaged asset ' + asset['destination'])
        for resource in ('mite/mobs-031.json', 'mite/spawning-031.json', 'mite/mob-chemical-items-031.json'):
            check(archive.read(resource) == (ROOT / 'src/main/resources' / resource).read_bytes(), 'Packaged rules ' + resource)
        for name in names:
            check('gametest/' not in name.lower() and not name.startswith(('tools/', '.reference/')), 'No test/research bytecode ' + name)
        check(not any(n.endswith('.class') and '/' not in n for n in names), 'No original obfuscated source classes')
        check(not any(n.startswith(('mezz/jei/', 'net/minecraft/', 'net/neoforged/')) for n in names), 'No bundled Minecraft/NeoForge/JEI')
    model = read('build/verification/031-client-model-audit.json')
    check(model['status'] == 'passed_structure_and_resource_audit_only', 'CPU model audit passed')
    check(not model['game_ui_automated'] and not model['visual_appearance_passed'], 'No invented visual/manual result')
    result = {'author': 'NingMeng123', 'checks': checks, 'failures': failures, 'source_living': 29,
              'pngs': 44, 'oggs': 44, 'jar': str(args.jar), 'jar_sha256': hashlib.sha256(args.jar.read_bytes()).hexdigest(),
              'user_manual_test': 'pending', 'status': 'passed' if not failures else 'failed'}
    output = ROOT / 'build/verification/031-mob-package-audit.json'
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k != 'jar'}, ensure_ascii=False))
    raise SystemExit(bool(failures))


if __name__ == '__main__':
    main()
