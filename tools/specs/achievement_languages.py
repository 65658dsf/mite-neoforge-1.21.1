"""Append achievement translations while preserving existing language edits. Author: NingMeng123."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
DESCRIPTIONS={
    'openInventory':'打开自己的物品栏。','stickPicker':'拾取一根木棍。','cuttingEdge':'制作燧石手斧或燧石小刀。',
    'mineWood':'拾取原木。','buildWorkBench':'制作燧石或黑曜石工作台。','buildShovel':'制作铲子。',
    'nuggets':'拾取铜、银、金、铁、秘银或艾德曼粒。','betterTools':'制作金属工作台。',
    'buildPickaxe':'制作铜、银或金镐。','buildFurnace':'制作圆石熔炉。','acquireIron':'从熔炉取出铁锭。',
    'buildHoe':'制作锄或鹤嘴锄。','flour':'制作面粉。','makeBread':'从烤炉取出面包。','bakeCake':'制作蛋糕。',
    'buildBetterPickaxe':'制作能开采黑曜石的工具。','onARail':'乘矿车离最初上车点至少1000格。',
    'buildClub':'制作木棒。','killEnemy':'击败一个怪物。','killCow':'拾取皮革。','flyPig':'骑乘的猪从超过5格高处落下。',
    'snipeSkeleton':'用箭射杀水平距离至少50格外的普通骷髅。','obsidianFurnace':'制作黑曜石熔炉。',
    'mithrilIngot':'从熔炉取出秘银锭。','diamonds':'拾取钻石。','portal':'进入地底世界。',
    'portalToNether':'进入下界。','ghast':'将火球打回去杀死恶魂。','blazeRod':'拾取烈焰棒。',
    'potion':'从酿造台取出一瓶药水。','theEnd':'进入末地。','theEnd2':'完成末地返回流程。',
    'enchantments':'制作普通或绿宝石附魔台。另一条展示前置为取得绿宝石。',
    'overkill':'一次有效近战攻击的原始伤害达到18点。','bookcase':'制作书架。',
    'seeds':'拾取小麦种子、蓝莓或生虫子。','eggs':'吃下一枚生鸡蛋。','buildAxe':'制作一把斧子。',
    'buildScythe':'制作镰刀。','wearLeather':'穿上一件皮革防具。','buildChainMail':'制作锁链防具。',
    'wearAllPlateArmor':'头、胸、腿、脚都穿戴符合来源材质的金属防具。',
    'wearAllAdamantiumPlateArmor':'头、胸、腿、脚都穿戴艾德曼防具。','buildOven':'制作黏土烤炉。',
    'flintFinder':'筛取砂砾获得燧石或燧石碎片。','buildTorches':'制作火把。','soilEnrichment':'用粪肥给耕地施肥。',
    'wellRested':'完整睡过至少6000刻，期间没有被饥饿或怪物唤醒。',
    'seaworthy':'乘船时，下方17×17×4区域全部为水。','explorer':'抵达主世界出生点10000至不足20000格的水平距离。',
    'enlightenment':'阅读Father Phoonzang的全部九种藏书。每种首次阅读奖励100经验。',
    'runegate':'成功通过符文传送门。','fishingRod':'制作钓鱼竿。','cookFish':'从熔炉取出熟鱼。',
    'plantDoctor':'成功用骨粉处理作物。','makeMycelium':'施肥后成功将蘑菇下方的耕地培育成菌丝。',
    'supersizeMe':'使用粪肥成功培育巨型蘑菇。','emeralds':'拾取绿宝石。',
    'netherrackFurnace':'制作地狱岩熔炉。','adamantiumIngot':'从熔炉取出艾德曼锭。',
    'fineDining':'制作木碗盛装的沙拉、汤或炖菜。','crystalBreaker':'制作能有效开采秘银块的工具。',
}

def main():
    catalog=json.loads((ROOT/'spec/r196-achievements.json').read_text(encoding='utf-8'))['achievements']
    extra={
      'zh_cn':{
        'screen.mite.auto_respawn':'自动重生倒计时：%s 秒',
        'message.mite.sleep_mobs':'附近有怪物能够接近你的床。','message.mite.sleep_shelter':'床需要有足够的遮蔽。',
        'message.mite.sleep_digging':'附近有怪物正在挖掘。','message.mite.sleep_hungry':'营养已耗尽，无法继续睡眠。',
        'message.mite.sleep_poison':'中毒时无法入睡。',
        'command.mite.achievement_unknown':'未知的 MITE 成就：%s','command.mite.achievement_unearned':'%s：全服尚未达成',
        'command.mite.achievement_first':'%s：%s 于第 %s 天首次达成',
        'death.attack.mite.hand_damage':'%s 的手受伤了','death.attack.mite.hand_damage.player':'%s 攻击 %s 时手部受伤致死',
      },
      'en_us':{
        'screen.mite.auto_respawn':'Automatic respawn in %s seconds',
        'message.mite.sleep_mobs':'A nearby monster can reach your bed.','message.mite.sleep_shelter':'Your bed needs shelter.',
        'message.mite.sleep_digging':'A nearby monster is digging.','message.mite.sleep_hungry':'You cannot sleep without nutrition.',
        'message.mite.sleep_poison':'You cannot fall asleep while poisoned.',
        'command.mite.achievement_unknown':'Unknown MITE achievement: %s','command.mite.achievement_unearned':'%s: no world-first completion yet',
        'command.mite.achievement_first':'%s: first completed by %s on day %s',
        'death.attack.mite.hand_damage':'%s hurt their hands','death.attack.mite.hand_damage.player':'%s fatally hurt their hands attacking %s',
      }
    }
    for language in ['zh_cn','en_us']:
        values=extra[language]
        for a in catalog:
            values['advancement.mite.'+a['id']+'.title']=a['zh_title'] if language=='zh_cn' else a['en_title']
            values['advancement.mite.'+a['id']+'.description']=DESCRIPTIONS[a['name']] if language=='zh_cn' else a['en_description']
        path=ROOT/f'src/main/resources/assets/mite/lang/{language}.json'
        text=path.read_text(encoding='utf-8')
        existing=json.loads(text)
        additions={k:v for k,v in values.items() if k not in existing}
        if additions:
            tail=json.dumps(additions,ensure_ascii=False,indent=2)[1:-2]
            path.write_text(text.rstrip()[:-1].rstrip()+','+tail+'\n}\n',encoding='utf-8')
        check=json.loads(path.read_text(encoding='utf-8'))
        assert all(check[k]==v for k,v in values.items())
    print('Achievement and rest translations verified')

if __name__=='__main__':main()
