"""Extract registered achievements and the nine source book texts reproducibly.
Author: NingMeng123.
"""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / '.reference/r196/decompiled'
TITLES = {
    'openInventory':'打开物品栏','stickPicker':'拾枝者','cuttingEdge':'锋利的开端','mineWood':'取得原木',
    'buildWorkBench':'搭建工作台','buildShovel':'制作铲子','nuggets':'金属颗粒','betterTools':'更好的工具',
    'buildPickaxe':'第一把金属镐','buildFurnace':'石质熔炉','acquireIron':'取得铁锭','buildHoe':'耕作的开始',
    'flour':'研磨面粉','makeBread':'烤制面包','bakeCake':'制作蛋糕','buildBetterPickaxe':'开采黑曜石',
    'onARail':'在铁轨上','buildClub':'制作木棒','killEnemy':'击败怪物','killCow':'取得皮革','flyPig':'飞天猪',
    'snipeSkeleton':'狙击骷髅','obsidianFurnace':'黑曜石熔炉','mithrilIngot':'秘银时代','diamonds':'钻石！',
    'portal':'进入地底世界','portalToNether':'深入下界','ghast':'以其人之道还治其人之身','blazeRod':'烈焰棒',
    'potion':'本地酿造厂','theEnd':'进入末地','theEnd2':'结束了？','enchantments':'制作附魔台','overkill':'强力一击',
    'bookcase':'知识的书架','seeds':'觅食者','eggs':'品尝生鸡蛋','buildAxe':'制作斧子','buildScythe':'制作镰刀',
    'wearLeather':'皮革防护','buildChainMail':'制作锁链甲','wearAllPlateArmor':'全套金属防具',
    'wearAllAdamantiumPlateArmor':'艾德曼全甲','buildOven':'黏土烤炉','flintFinder':'找到燧石',
    'buildTorches':'点亮黑暗','soilEnrichment':'施肥','wellRested':'休息充足','seaworthy':'航海者',
    'explorer':'远行者','enlightenment':'启迪','runegate':'末影人的羡慕','fishingRod':'制作钓鱼竿',
    'cookFish':'烤鱼','plantDoctor':'植物医生','makeMycelium':'培育菌丝','supersizeMe':'巨型蘑菇',
    'emeralds':'绿宝石！','netherrackFurnace':'地狱岩熔炉','adamantiumIngot':'艾德曼时代',
    'fineDining':'精致饮食','crystalBreaker':'晶体破坏者',
}
ICONS = {
    'openInventory':'minecraft:book','stickPicker':'minecraft:stick','cuttingEdge':'mite:flint_hatchet',
    'mineWood':'minecraft:oak_log','buildWorkBench':'mite:flint_crafting_table','buildShovel':'mite:flint_shovel',
    'nuggets':'mite:copper_nugget','betterTools':'mite:copper_crafting_table','buildPickaxe':'mite:copper_pickaxe',
    'buildFurnace':'minecraft:furnace','acquireIron':'minecraft:iron_ingot','buildHoe':'mite:copper_hoe',
    'flour':'mite:flour','makeBread':'minecraft:bread','bakeCake':'minecraft:cake','buildBetterPickaxe':'minecraft:iron_pickaxe',
    'onARail':'minecraft:rail','buildClub':'minecraft:wooden_sword','killEnemy':'minecraft:bone',
    'killCow':'minecraft:leather','flyPig':'minecraft:saddle','snipeSkeleton':'minecraft:arrow',
    'obsidianFurnace':'mite:obsidian_furnace','mithrilIngot':'mite:mithril_ingot','diamonds':'minecraft:diamond',
    'portal':'minecraft:obsidian','portalToNether':'minecraft:netherrack','ghast':'minecraft:ghast_tear',
    'blazeRod':'minecraft:blaze_rod','potion':'minecraft:potion','theEnd':'minecraft:ender_eye','theEnd2':'minecraft:dragon_egg',
    'enchantments':'minecraft:enchanting_table','overkill':'mite:mithril_sword','bookcase':'minecraft:bookshelf',
    'seeds':'minecraft:wheat_seeds','eggs':'minecraft:egg','buildAxe':'mite:flint_axe','buildScythe':'mite:copper_scythe',
    'wearLeather':'minecraft:leather_helmet','buildChainMail':'minecraft:chainmail_chestplate',
    'wearAllPlateArmor':'minecraft:iron_chestplate','wearAllAdamantiumPlateArmor':'mite:adamantium_chestplate',
    'buildOven':'mite:clay_oven','flintFinder':'minecraft:flint','buildTorches':'minecraft:torch',
    'soilEnrichment':'mite:manure','wellRested':'minecraft:red_bed','seaworthy':'minecraft:oak_boat',
    'explorer':'minecraft:leather_boots','enlightenment':'minecraft:written_book','runegate':'mite:mithril_runestone',
    'fishingRod':'mite:fishing_rod_copper_hook','cookFish':'minecraft:cooked_cod','plantDoctor':'minecraft:bone_meal',
    'makeMycelium':'minecraft:mycelium','supersizeMe':'minecraft:brown_mushroom_block','emeralds':'minecraft:emerald',
    'netherrackFurnace':'mite:netherrack_furnace','adamantiumIngot':'mite:adamantium_ingot','fineDining':'mite:salad',
    'crystalBreaker':'mite:adamantium_pickaxe',
}

def snake(name):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


def referenced_books():
    """Read source literals, preserving original page boundaries and whitespace.

    These R196 Java literals use JSON-compatible escapes. json.loads decodes them
    without evaluating Java/Python or converting UTF-8 text through unicode_escape.
    Unsupported future source escapes fail explicitly instead of changing prose.
    """
    path = SOURCE / 'ItemReferencedBook.java'
    source = path.read_text(encoding='utf-8')
    literal = r'"(?:[^"\\]|\\.)*"'
    branches = list(re.finditer(r'\bif\s*\(index\s*==\s*(\d+)\)\s*\{', source))
    books = []
    for number, branch in enumerate(branches):
        body = source[branch.end():branches[number + 1].start() if number + 1 < len(branches) else source.index('\n        } else {', branch.end())]
        index = int(branch.group(1))

        def string_field(name):
            match = re.search(r'nbt\.a\("' + name + r'",\s*new ck\("' + name + r'",\s*(' + literal + r')\)\);', body)
            if match is None:
                raise ValueError(f'Book {index} lacks source field {name}')
            return json.loads(match.group(1))

        pages = re.findall(r'pages\.a\(new ck\("(\d+)",\s*(' + literal + r')\)\);', body)
        if not pages or [int(page[0]) for page in pages] != list(range(1, len(pages) + 1)):
            raise ValueError(f'Book {index} has missing or unordered source pages')
        reward = re.search(r'nbt\.a\("xp_reward",\s*(\d+)\);', body)
        if reward is None:
            raise ValueError(f'Book {index} lacks a source XP reward')
        books.append(dict(index=index, title=string_field('title'), author=string_field('author'),
                          xp=int(reward.group(1)), pages=[json.loads(page[1]) for page in pages]))
    constants = [int(value) for value in re.findall(r'public static final int BOOK_\w+\s*=\s*(\d+);', source)]
    if constants != list(range(1, 10)) or [book['index'] for book in books] != constants:
        raise ValueError('Expected precisely the nine registered R196 book identities')
    authors = {book['author'] for book in books}
    if len(authors) != 1:
        raise ValueError('Expected the source books to share one original author')
    result = dict(author='NingMeng123', original_author=next(iter(authors)),
                  source='ItemReferencedBook.generateBookContents', books=books)
    (ROOT / 'src/main/resources/mite/referenced-books.json').write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Extracted', len(books), 'source books;', sum(len(book['pages']) for book in books),
          'unaltered pages; source SHA-256', hashlib.sha256(path.read_bytes()).hexdigest())


def main():
    source = (SOURCE / 'kp.java').read_text(encoding='utf-8')
    declarations = []
    for line_no, line in enumerate(source.splitlines(), 1):
        match = re.match(r'\s*(\w+) = new ko\((\d+), "([^"]+)"', line)
        if not match:
            continue
        field, legacy_id, name = match.groups()
        parent = re.search(r',\s*(?:\(ko\))?(\w+)\)\.', line).group(1)
        declarations.append(dict(field=field,legacy_id=int(legacy_id),name=name,id=snake(name),
                                 parent_field=None if parent=='null' else parent,challenge='.b()' in line,
                                 source_line=line_no,icon=ICONS[name],zh_title=TITLES[name],call_sites=[]))
    fields={x['field']:x for x in declarations}
    for path in sorted(SOURCE.glob('*.java')):
        if path.name=='kp.java':continue
        for line_no,line in enumerate(path.read_text(encoding='utf-8').splitlines(),1):
            for field in re.findall(r'\bkp\.(\w+)\b',line):
                if field in fields:fields[field]['call_sites'].append({'file':path.name,'line':line_no})
    lang={}
    for line in (ROOT/'.reference/r196/resources/assets/minecraft/lang/MITE.lang').read_text(encoding='utf-8').splitlines():
        if '=' in line:
            k,v=line.split('=',1);lang[k]=v
    for entry in declarations:
        entry['parent']=fields[entry.pop('parent_field')]['id'] if entry['parent_field'] else None
        entry['en_title']=lang.get('achievement.'+entry['name'],TITLES[entry['name']])
        entry['en_description']=lang.get('achievement.'+entry['name']+'.desc',entry['en_title'])
        entry['second_parent']='emeralds' if entry['name']=='enchantments' else None
    result=dict(schema_version=1,author='NingMeng123',source_version='MITE R196',
                source_sha256=hashlib.sha256((SOURCE/'kp.java').read_bytes()).hexdigest(),count=len(declarations),
                adaptation='Modern advancement UI; source trigger facts and independent world-first ledger; display parents do not gate source awards.',
                achievements=declarations)
    text=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
    for name in ['spec/r196-achievements.json','src/main/resources/mite/achievements.json']:
        (ROOT/name).write_text(text,encoding='utf-8')
    print('Extracted',len(declarations),'registered source achievements')
    referenced_books()

if __name__=='__main__':main()
