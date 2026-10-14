package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Hard-coded source formulas and JavaRandom fixtures verify the real cancellable native death path. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class IndependentNativeMobLootGameTests {
    private static <T extends Mob> T mob(GameTestHelper h,EntityType<T> type) {
        T e=type.create(h.getLevel()); e.moveTo(h.absolutePos(new BlockPos(2,3,2)).getCenter()); e.setNoAi(true); e.setNoGravity(true); h.getLevel().addFreshEntity(e);
        for(EquipmentSlot slot:EquipmentSlot.values()) { e.setItemSlot(slot,ItemStack.EMPTY); e.setDropChance(slot,0F); }
        return e;
    }
    private static Player killer(GameTestHelper h) { return h.makeMockPlayer(GameType.SURVIVAL); }
    private static List<ItemStack> death(GameTestHelper h,Mob mob,long seed,boolean recent,DamageSource source,boolean cancel) {
        List<ItemStack> result=new ArrayList<>();
        Consumer<LivingDropsEvent> listener=event->{ if(event.getEntity()==mob) { for(ItemEntity drop:event.getDrops()) result.add(drop.getItem().copy()); if(cancel) event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class,listener);
        try { mob.tickCount=100; if(recent) mob.setLastHurtByPlayer(killer(h)); mob.getRandom().setSeed(seed); mob.setHealth(0); mob.die(source); }
        finally { NeoForge.EVENT_BUS.unregister(listener); }
        return result;
    }
    private static List<ItemStack> death(GameTestHelper h,Mob mob,long seed,boolean recent) { return death(h,mob,seed,recent,mob.damageSources().generic(),false); }
    private static int count(List<ItemStack> drops,Item item) { return drops.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static long seed(Predicate<Random> condition) { for(long n=0;n<100000;n++) if(condition.test(new Random(n))) return n; throw new AssertionError("No independent source random fixture"); }

    @GameTest(template="empty") public static void actualCowDeathMatchesIndependentSourceCounts(GameTestHelper h) {
        for(long n=0;n<16;n++) { var e=mob(h,EntityType.COW); Random r=new Random(n); int skins=r.nextInt(3)+1,meat=r.nextInt(3)+1+r.nextInt(1); if(meat==1 && r.nextInt(2)==0) ++meat;
            var drops=death(h,e,n,true); h.assertTrue(count(drops,Items.LEATHER)==skins && count(drops,Items.BEEF)==meat,"rr source counts through real native death atseed"+n); }
        h.succeed();
    }
    @GameTest(template="empty") public static void illCowKeepsLeatherButLosesMeat(GameTestHelper h) { var e=mob(h,EntityType.COW); SourceVanillaMobLoot.setLivestockNeeds(e,.249F,1F,1F); var drops=death(h,e,3,true); h.assertTrue(count(drops,Items.LEATHER)>=1 && count(drops,Items.BEEF)==0,"Source minneeds .25 gates onlymeat"); h.succeed(); }
    @GameTest(template="empty") public static void exactlyQuarterWellnessStillProducesMeat(GameTestHelper h) { var e=mob(h,EntityType.MOOSHROOM); SourceVanillaMobLoot.setLivestockNeeds(e,.25F,.25F,.25F); var drops=death(h,e,5,true); h.assertTrue(count(drops,Items.BEEF)>=1 && count(drops,Items.LEATHER)>=1,"Inclusive source .25 across inherited rr mooshroom"); h.succeed(); }
    @GameTest(template="empty") public static void sheepIndependentWoolMeatLeatherDraws(GameTestHelper h) { Sheep e=mob(h,EntityType.SHEEP); e.setColor(DyeColor.BLUE); Random r=new Random(9); boolean wool=r.nextInt(2)==0; int meat=Math.max(1,r.nextInt(2)+r.nextInt(1)); boolean skin=r.nextInt(2)==0; var drops=death(h,e,9,true); h.assertTrue(count(drops,Items.BLUE_WOOL)==(wool?1:0) && count(drops,Items.MUTTON)==meat && count(drops,Items.LEATHER)==(skin?1:0),"Source rz death pools preserve draw order and woolcolor"); h.succeed(); }
    @GameTest(template="empty") public static void shearedSheepHasNoNativeGuaranteedWool(GameTestHelper h) { Sheep e=mob(h,EntityType.SHEEP); e.setSheared(true); var drops=death(h,e,0,true); h.assertTrue(drops.stream().noneMatch(s->s.is(Items.WHITE_WOOL)) && count(drops,Items.MUTTON)==1,"No duplicate native sheep loot survives source scope"); h.succeed(); }
    @GameTest(template="empty") public static void chickenUsesSavedFeathersInsteadOfLootingDraw(GameTestHelper h) { var e=mob(h,EntityType.CHICKEN); SourceVanillaMobLoot.setFeathers(e,0); var drops=death(h,e,0,true); h.assertTrue(count(drops,Items.FEATHER)==0 && count(drops,Items.CHICKEN)==1,"rq actualdepletedstock gives meatonly"); h.succeed(); }
    @GameTest(template="empty") public static void burningChickenDropsCookedMeatAndNoFeathers(GameTestHelper h) { var e=mob(h,EntityType.CHICKEN); e.igniteForSeconds(10); var drops=death(h,e,0,true); h.assertTrue(count(drops,Items.COOKED_CHICKEN)==1 && count(drops,Items.CHICKEN)==0 && count(drops,Items.FEATHER)==0,"Source burning branch removes featherpool"); h.succeed(); }
    @GameTest(template="empty") public static void nativePigSaddleReturnsExactlyOnce(GameTestHelper h) { Pig e=mob(h,EntityType.PIG); e.equipSaddle(new ItemStack(Items.SADDLE),null); var drops=death(h,e,0,true); h.assertTrue(count(drops,Items.SADDLE)==1 && count(drops,Items.PORKCHOP)>=1,"Installed native saddle returned once while source meat replaces table"); h.succeed(); }
    @GameTest(template="empty") public static void nativeZombieRareRewardIsNuggetNotIngot(GameTestHelper h) { long n=seed(r->{ r.nextFloat(); return r.nextInt(200)<5; }); Random r=new Random(n); boolean flesh=r.nextFloat()<.5F; r.nextInt(200); Item[] expected={SourceVanillaMobLoot.item("mite:copper_nugget"),SourceVanillaMobLoot.item("mite:silver_nugget"),Items.GOLD_NUGGET,Items.IRON_NUGGET}; Item nugget=expected[r.nextInt(4)]; var e=mob(h,EntityType.ZOMBIE); var drops=death(h,e,n,true); h.assertTrue(count(drops,nugget)==1 && count(drops,Items.ROTTEN_FLESH)==(flesh?1:0),"Source tw exactrare RNG and metal tier"); h.assertTrue(count(drops,Items.IRON_INGOT)==0 && count(drops,Items.CARROT)==0 && count(drops,Items.POTATO)==0,"Vanilla rare ingot and crops cannot duplicate ordinarysource zombie"); h.succeed(); }
    @GameTest(template="empty") public static void zombieVillagerExactRareOnionHasNoModifierDuplicate(GameTestHelper h) { long n=seed(r->{r.nextFloat();return r.nextInt(50)<5 && r.nextInt(6)==5;}); var e=mob(h,EntityType.ZOMBIE_VILLAGER); var drops=death(h,e,n,true); h.assertTrue(count(drops,SourceVanillaMobLoot.item("mite:onion"))==1 && count(drops,Items.IRON_INGOT)==0,"Source villager rare50 pool6, old NaturalFood modifier never runs again"); h.succeed(); }
    @GameTest(template="empty") public static void environmentalZombieGetsQuarterFleshAndNoRare(GameTestHelper h) { long n=seed(r->r.nextFloat()<.25F); var e=mob(h,EntityType.ZOMBIE); var drops=death(h,e,n,false); h.assertTrue(drops.size()==1 && count(drops,Items.ROTTEN_FLESH)==1,"Source nonplayer quarterflesh cannotrollrare"); h.succeed(); }
    @GameTest(template="empty") public static void skeletonActualDeathHasSourceArrowAndBoneCounts(GameTestHelper h) { var e=mob(h,EntityType.SKELETON); ((OrdinarySkeletonState)e).mite$setSourceSkeletonType(0); Random r=new Random(0); int arrows=r.nextInt(2),bones=r.nextInt(3); var drops=death(h,e,0,true); h.assertTrue(count(drops,MiteRangedItems.arrow(Material.RUSTED_IRON))==arrows && count(drops,Items.BONE)==bones && count(drops,Items.ARROW)==0,"Source tr pools invoked once, no SkeletonArrow modifier second draw"); h.succeed(); }
    @GameTest(template="empty") public static void meleeSkeletonDropsBonesWithoutArrows(GameTestHelper h) { var e=mob(h,EntityType.SKELETON); ((OrdinarySkeletonState)e).mite$setSourceSkeletonType(2); var drops=death(h,e,0,true); h.assertTrue(count(drops,Items.BONE)==new Random(0).nextInt(3) && drops.stream().noneMatch(s->MiteRangedItems.arrowMaterial(s)!=null),"Source subtype2 skips arrow random draw entirely"); h.succeed(); }
    @GameTest(template="empty") public static void separateWitherSkeletonUsesSourceSubtypeOne(GameTestHelper h) { var e=mob(h,EntityType.WITHER_SKELETON); Random r=new Random(11); int coal=r.nextInt(3)-1; boolean skull=r.nextInt(200)<5; int bones=r.nextInt(3); var drops=death(h,e,11,true); h.assertTrue(count(drops,Items.COAL)==Math.max(0,coal) && count(drops,Items.WITHER_SKELETON_SKULL)==(skull?1:0) && count(drops,Items.BONE)==bones,"Modern split uses exactsource tr type1"); h.succeed(); }
    @GameTest(template="empty") public static void ironGolemDropsNuggetsRatherThanIngots(GameTestHelper h) { var e=mob(h,EntityType.IRON_GOLEM); Random r=new Random(4); int roses=r.nextInt(3),nuggets=3+r.nextInt(3); var drops=death(h,e,4,true); h.assertTrue(count(drops,Items.POPPY)==roses && count(drops,Items.IRON_NUGGET)==nuggets && count(drops,Items.IRON_INGOT)==0,"sd playeronly3..5nuggets cannotopen vanilla iron farm"); h.succeed(); }
    @GameTest(template="empty") public static void automaticIronGolemDeathHasNoMetal(GameTestHelper h) { var e=mob(h,EntityType.IRON_GOLEM); var drops=death(h,e,4,false); h.assertTrue(count(drops,Items.IRON_NUGGET)==0 && count(drops,Items.IRON_INGOT)==0,"sd unassisted natural death cannotdropmetal"); h.succeed(); }
    @GameTest(template="empty") public static void squidOnlyDropsOneInkWhenRecentlyHit(GameTestHelper h) { var e=mob(h,EntityType.SQUID); var natural=death(h,e,0,false); h.assertTrue(natural.isEmpty(),"Source unassisted squid noink"); var playerKill=death(h,mob(h,EntityType.SQUID),0,true); h.assertTrue(playerKill.size()==1 && count(playerKill,Items.INK_SAC)==1,"Source recentkill fixedoneink"); h.succeed(); }
    @GameTest(template="empty") public static void nativeSpiderReturnsOnlyRemainingWebStock(GameTestHelper h) { var e=mob(h,EntityType.SPIDER); SourceVanillaMobLoot.setWebStock(e,2); var drops=death(h,e,0,true); h.assertTrue(count(drops,Items.STRING)==2 && SourceVanillaMobLoot.webStock(e)==0,"Source stock is consumed once atdeath"); h.succeed(); }
    @GameTest(template="empty") public static void caveSpiderEnvironmentalDeathDropsNoStringOrEye(GameTestHelper h) { var e=mob(h,EntityType.CAVE_SPIDER); SourceVanillaMobLoot.setWebStock(e,3); h.assertTrue(death(h,e,0,false).isEmpty(),"Source arachnid recentplayer gate applies toboth pools"); h.succeed(); }
    @GameTest(template="empty") public static void blazeRodRequiresRecentlyHitPlayer(GameTestHelper h) { h.assertTrue(death(h,mob(h,EntityType.BLAZE),0,false).isEmpty(),"td no automaticrods"); var drops=death(h,mob(h,EntityType.BLAZE),0,true); h.assertTrue(count(drops,Items.BLAZE_ROD)==new Random(0).nextInt(2),"Source recentplayerrod count"); h.succeed(); }
    @GameTest(template="empty") public static void sourceHorseAddsBeefWhilePreservingLeather(GameTestHelper h) { var e=mob(h,EntityType.HORSE); Random r=new Random(3); int skins=r.nextInt(3)+1; for(int i=0;i<skins;i++) r.nextInt(4); int beef=1+r.nextInt(1)+r.nextInt(2); var drops=death(h,e,3,true); h.assertTrue(count(drops,Items.LEATHER)==skins && count(drops,Items.BEEF)==beef,"rs horse subtype0 source meat and exactskin draws"); h.succeed(); }
    @GameTest(template="empty") public static void sourceWolfDropsOneLeather(GameTestHelper h) { var drops=death(h,mob(h,EntityType.WOLF),0,true); h.assertTrue(drops.size()==1 && count(drops,Items.LEATHER)==1,"sf source fixedleather"); h.succeed(); }
    @GameTest(template="empty") public static void sixWitchPotionDurationsMatchGuardedSourceOracle(GameTestHelper h) { int[] expected={3600,1,676,1351,1351,1}; for(int i=0;i<6;i++) { ItemStack stack=SourceVanillaMobLoot.witchPotion(i); var effects=new ArrayList<net.minecraft.world.effect.MobEffectInstance>(); stack.get(DataComponents.POTION_CONTENTS).getAllEffects().forEach(effects::add); h.assertTrue(stack.is(i<2?Items.POTION:Items.SPLASH_POTION) && effects.size()==1 && effects.get(0).getDuration()==expected[i] && effects.get(0).getAmplifier()==0,"Pure yp/zp oracle subtype effect duration "+i); } h.succeed(); }
    @GameTest(template="empty") public static void witchRealDeathProducesSourcePoolCount(GameTestHelper h) { var e=mob(h,EntityType.WITCH); var drops=death(h,e,2,true); h.assertTrue(drops.stream().mapToInt(ItemStack::getCount).sum()==new Random(2).nextInt(5)+1,"Source tv outerpoolcount1..5, no native loottable buckets"); h.succeed(); }
    @GameTest(template="empty") public static void sourceEquipmentKeepsActualQualityDamageAndReturnsOnce(GameTestHelper h) { var e=mob(h,EntityType.ZOMBIE); ItemStack tool=new ItemStack(SourceVanillaMobLoot.item("mite:flint_knife")); tool.setDamageValue(10); e.setItemSlot(EquipmentSlot.MAINHAND,tool.copy()); var drops=death(h,e,0,true); var tools=drops.stream().filter(s->s.is(tool.getItem())).toList(); h.assertTrue(tools.size()==1 && ItemStack.isSameItemSameComponents(tools.get(0),tool) && e.getMainHandItem().isEmpty(),"Source recentheld return preserves stackcomponents, never damages or duplicates on death"); h.succeed(); }
    @GameTest(template="empty") public static void modernHorseBodyArmorReturnsExactlyOnce(GameTestHelper h) { var horse=mob(h,EntityType.HORSE); horse.setBodyArmorItem(new ItemStack(Items.IRON_HORSE_ARMOR)); var drops=death(h,horse,0,false); h.assertTrue(count(drops,Items.IRON_HORSE_ARMOR)==1 && horse.getItemBySlot(EquipmentSlot.BODY).isEmpty(),"Modern bodyarmor forcedreturn must not duplicate native horse inventorydrop"); h.succeed(); }
    @GameTest(template="empty") public static void forcedEquipmentSurvivesMassiveFallSuppression(GameTestHelper h) { var e=mob(h,EntityType.ZOMBIE); e.getPersistentData().putBoolean("mite_massive_fall_031",true); ItemStack helmet=new ItemStack(Items.LEATHER_HELMET); e.setItemSlot(EquipmentSlot.HEAD,helmet); e.setDropChance(EquipmentSlot.HEAD,2F); var drops=death(h,e,0,false); h.assertTrue(drops.size()==1 && count(drops,Items.LEATHER_HELMET)==1,"FewItems90%suppressed but picked-up forcedequipment remains intact"); h.succeed(); }
    @GameTest(template="empty") public static void canceledNativeDeathLootLeavesNoWorldItems(GameTestHelper h) { var e=mob(h,EntityType.COW); var captured=death(h,e,0,true,e.damageSources().generic(),true); h.assertTrue(!captured.isEmpty() && h.getLevel().getEntitiesOfClass(ItemEntity.class,e.getBoundingBox().inflate(3)).isEmpty(),"All source materials remain inside cancellable captureDrops transaction"); h.succeed(); }
    @GameTest(template="empty") public static void persistentNeedsAndStocksDoNotReroll(GameTestHelper h) { var e=mob(h,EntityType.CHICKEN); SourceVanillaMobLoot.setLivestockNeeds(e,.2F,.3F,.4F); SourceVanillaMobLoot.setFeathers(e,0); CompoundTag saved=e.getPersistentData().getCompound(SourceVanillaMobLoot.STATE).copy(); var restored=mob(h,EntityType.CHICKEN); restored.getPersistentData().put(SourceVanillaMobLoot.STATE,saved); SourceVanillaMobLoot.initialize(restored); h.assertTrue(!SourceVanillaMobLoot.well(restored) && SourceVanillaMobLoot.feathers(restored)==0 && restored.getPersistentData().getCompound(SourceVanillaMobLoot.STATE).equals(saved),"Join/load retains depleted state rather than rerolling spawn rewards"); h.succeed(); }
    @GameTest(template="empty") public static void modernUnmappedRabbitLootRemainsNative(GameTestHelper h) { var e=mob(h,EntityType.RABBIT); h.assertTrue(!SourceVanillaMobLoot.managed(e),"New native species has no R196 identity and stays outside source scope"); var drops=death(h,e,0,true); h.assertTrue(drops.stream().noneMatch(s->s.is(Items.BEEF) || s.is(Items.IRON_NUGGET)),"Modern rabbit rewards are not borrowed from unrelated source identities"); h.succeed(); }
}
