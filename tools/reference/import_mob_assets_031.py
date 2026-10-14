"""Copy only confirmed R196 entity PNGs and used mob sounds, byte for byte. Author: NingMeng123."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CHINESE = '尸鬼 亡魂 隐形潜行者 恶魔蜘蛛 地狱犬 恐狼 树蜘蛛 地狱苦力怕 暗影 火元素 黑寡妇蜘蛛 怨魂 地元素 赭色胶怪 红色胶怪 灰浆怪 黑布丁怪 吸血蝙蝠 巨型吸血蝙蝠 远古骷髅 远古骷髅守卫 暗翼蝠 地狱蠹虫 铜脊蠹虫 苍白蠹虫 黏土魔像 骷髅领主 远古骷髅领主 相位蜘蛛'.split()


def main():
    source = json.loads((ROOT / 'spec/mobs-source-031.json').read_text(encoding='utf-8'))
    pack = ROOT / 'MITE Resource Pack 1.6.4.zip'
    assert hashlib.sha256(pack.read_bytes()).hexdigest() == source['provenance']['source_pack_sha256']
    files, sounds = [], {}
    with zipfile.ZipFile(pack) as archive:
        pngs = {row['source']: row for entity in source['entities'] for row in entity.get('textures', [])}
        for original, row in sorted(pngs.items()):
            data = archive.read(original)
            assert hashlib.sha256(data).hexdigest() == row['sha256']
            destination = 'assets/mite/textures/entity/' + original.split('/textures/entity/', 1)[1]
            target = ROOT / 'src/main/resources' / destination
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            files.append({'source': original, 'destination': destination, 'sha256': row['sha256'], 'bytes': len(data)})
        for original in sorted(archive.namelist()):
            if not original.startswith('assets/minecraft/sound/imported/mob/') or not original.endswith('.ogg'):
                continue
            relative = original.split('/sound/', 1)[1]
            destination = 'assets/mite/sounds/' + relative
            data = archive.read(original)
            target = ROOT / 'src/main/resources' / destination
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            files.append({'source': original, 'destination': destination, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
            event = re.sub(r'\d+$', '', relative[:-4]).replace('/', '.')
            sounds.setdefault(event, {'sounds': []})['sounds'].append('mite:' + relative[:-4])
    manifest = {'schema_version': 1, 'author': 'NingMeng123', 'source_pack_sha256': source['provenance']['source_pack_sha256'],
                'png_count': len(pngs), 'ogg_count': sum(f['source'].endswith('.ogg') for f in files),
                'files': files, 'sounds': sounds}
    stacking = json.loads((ROOT / 'spec/stacking-map-030.json').read_text(encoding='utf-8'))
    manifest['source_meat_items'] = [row['modern_id'] for row in stacking['mappedItems'] if row['mapping_kind'] == 'source'
                                     and any(item['source_class'] == 'ItemMeat' for item in row['source_items'])]
    for target in ('spec/mob-assets-031.json', 'src/main/resources/mite/mob-assets-031.json'):
        (ROOT / target).write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    living = [row for row in source['entities'] if 512 <= row['legacy_id'] <= 540]
    assert len(living) == len(CHINESE) == 29
    for lang in ('zh_cn', 'en_us'):
        target = ROOT / 'src/main/resources/assets/mite/lang' / (lang + '.json')
        strings = json.loads(target.read_text(encoding='utf-8'))
        for row, chinese in zip(living, CHINESE):
            name = chinese if lang == 'zh_cn' else ('Clay Golem' if row['legacy_id'] == 537 else row['original_display_name'])
            path = row['modern_id'].split(':', 1)[1]
            strings['entity.mite.' + path] = name
            strings['item.mite.' + path + '_spawn_egg'] = name + ('刷怪蛋' if lang == 'zh_cn' else ' Spawn Egg')
        strings['entity.mite.thrown_web'] = '蛛网投射物' if lang == 'zh_cn' else 'Thrown Web'
        strings['block.mite.copperspine_stone'] = '铜刺寄生石' if lang == 'zh_cn' else 'Copperspine Stone'
        for name, label in {'water': '水', 'sunlight': '阳光', 'acid': '强酸', 'pepsin': '消化液', 'melt': '融化'}.items():
            strings['death.attack.mite.' + name] = ('%1$s因' + label + '死亡') if lang == 'zh_cn' else '%1$s died from ' + name
        target.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'png': manifest['png_count'], 'ogg': manifest['ogg_count'], 'sound_events': len(sounds)}))


if __name__ == '__main__':
    main()
