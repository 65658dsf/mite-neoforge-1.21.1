package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.animal.*;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.CowFoodState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source literals are asserted through real goals, entity persistence, interactions, birth and passive production. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class Livestock032GameTests {
    private static final BlockPos ORIGIN=new BlockPos(1,10,1);
    private static <T extends Mob> T mob(GameTestHelper h,EntityType<T> type) {
        T mob=type.create(h.getLevel()); BlockPos pos=h.absolutePos(ORIGIN); mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); mob.setNoAi(true); mob.setNoGravity(true); h.getLevel().addFreshEntity(mob); return mob;
    }
    private static void needs(Mob mob,float food,float water,float freedom) { Livestock032.needs(mob,new LivestockRules032.Needs(food,water,freedom)); }
    private static long seed(Predicate<Random> test) { for(long seed=0;seed<100000;seed++) if(test.test(new Random(seed))) return seed; throw new AssertionError("No literal source random fixture"); }
    private static List<ItemEntity> capture(Mob mob) { var list=new ArrayList<ItemEntity>(); mob.captureDrops(list); return list; }
    private static int count(List<ItemEntity> drops,Item item) { return drops.stream().filter(drop->drop.getItem().is(item)).mapToInt(drop->drop.getItem().getCount()).sum(); }

    @GameTest(template="empty") public static void sourceInitialNeedsAndProductionAreSavedWithoutReroll(GameTestHelper h) {
        var e=mob(h,EntityType.PIG); var n=Livestock032.needs(e); h.assertTrue(n.food()>=.8F && n.food()<1F && n.water()>=.8F && n.freedom()>=.8F,"Three actual constructor resources in .8..1");
        needs(e,.31F,.42F,.83F); var s=Livestock032.state(e); s.putInt("production_counter",147); s.putInt("manure_countdown",321); Livestock032.save(e,s);
        CompoundTag save=new CompoundTag(); e.saveWithoutId(save); var restored=EntityType.PIG.create(h.getLevel()); restored.load(save);
        h.assertValueEqual(Livestock032.needs(restored),new LivestockRules032.Needs(.31F,.42F,.83F),"Whole entity load preserves all needs");
        h.assertTrue(Livestock032.state(restored).getInt("production_counter")==147 && Livestock032.state(restored).getInt("manure_countdown")==321,"Production and manure timers survive restart"); e.discard(); restored.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void existing031NeedsAndCowMilkAreMigratedTogether(GameTestHelper h) {
        var cow=EntityType.COW.create(h.getLevel()); SourceVanillaMobLoot.initialize(cow);var old=cow.getPersistentData().getCompound(SourceVanillaMobLoot.STATE);
        old.putFloat("food",.11F);old.putFloat("water",.22F);old.putFloat("freedom",.33F);cow.getPersistentData().put(SourceVanillaMobLoot.STATE,old);
        cow.setData(MiteAttachments.COW_FOOD,new CowFoodState(1,37,.9F,.9F,.9F)); cow.setData(MiteAttachments.COW_MANURE_COUNTDOWN,543);
        var state=Livestock032.state(cow); h.assertTrue(state.getFloat("food")==.11F && state.getFloat("water")==.22F && state.getFloat("freedom")==.33F,"031 persisted needs are not replaced by independent legacy cow attachment");
        h.assertTrue(state.getInt("milk")==37 && state.getInt("manure_countdown")==543,"Existing actual milk and manure retained"); h.succeed();
    }
    @GameTest(template="empty") public static void foodSourcesDifferForCowAndPig(GameTestHelper h) {
        var cow=mob(h,EntityType.COW); var pig=mob(h,EntityType.PIG);
        h.assertFalse(Livestock032.foodBlock(cow,Blocks.GRASS_BLOCK.defaultBlockState()),"rr overrides grass block food");
        h.assertTrue(Livestock032.foodBlock(cow,Blocks.DANDELION.defaultBlockState()) && Livestock032.foodBlock(cow,Blocks.SHORT_GRASS.defaultBlockState()),"rr ai is dandelion37, plus tallgrass31");
        h.assertTrue(Livestock032.foodBlock(pig,Blocks.GRASS_BLOCK.defaultBlockState()),"Base source livestock eat grass terrain");
        h.assertFalse(Livestock032.foodBlock(cow,Blocks.WHEAT.defaultBlockState()),"Cow source ai is not guessed to mean wheat59"); cow.discard(); pig.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void drinkingAcceptsOpenWaterSnowAndReachableCauldronOnly(GameTestHelper h) {
        var cow=mob(h,EntityType.COW); var origin=cow.blockPosition(); var source=origin.below();
        h.getLevel().setBlock(source,Blocks.WATER.defaultBlockState(),3); h.assertTrue(Livestock032.isNearWater(cow),"Open water source supplies livestock");
        h.getLevel().setBlock(source.above(),Blocks.STONE.defaultBlockState(),3); h.assertFalse(Livestock032.isNearWater(cow),"Solid bottom cover prevents drinking");
        h.getLevel().setBlock(source.above(),Blocks.AIR.defaultBlockState(),3); h.getLevel().setBlock(source,Blocks.SNOW_BLOCK.defaultBlockState(),3); h.assertTrue(Livestock032.isNearWater(cow),"Snow is actual source water");
        h.getLevel().setBlock(source,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,1),3); h.assertTrue(Livestock032.isNearWater(cow),"Water cauldron below eye accessible");
        h.getLevel().setBlock(source,Blocks.CAULDRON.defaultBlockState(),3); h.assertFalse(Livestock032.isNearWater(cow),"Empty cauldron supplies none"); cow.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void twoAnimalsAreFreeThreeIncludingOwnerAreCrowded(GameTestHelper h) {
        var first=mob(h,EntityType.COW); var second=mob(h,EntityType.PIG);
        h.assertFalse(Livestock032.isCrowded(first),"Outdoor source class query has exactly two"); var third=mob(h,EntityType.CHICKEN);
        h.assertTrue(Livestock032.isCrowded(first),"Original class AABB includes owner so third animal crosses size>2"); first.discard();second.discard();third.discard();h.succeed();
    }
    @GameTest(template="empty") public static void actualPigWaterBowlSpendsOneContainerAndRestoresQuarterWater(GameTestHelper h) {
        var pig=mob(h,EntityType.PIG);needs(pig,.6F,.125F,.8F);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MiteFoods.WATER_BOWL.get(),2));
        var event=NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player,InteractionHand.MAIN_HAND,pig));
        h.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction(),"Registered livestock interaction accepts source water vessel");
        h.assertTrue(Livestock032.needs(pig).water()==.375F && Livestock032.needs(pig).food()==.6F,"Actual interaction restores water only");
        h.assertTrue(player.getMainHandItem().getCount()==1 && player.getInventory().countItem(Items.BOWL)==1,"Exactly one water serving consumed and its empty bowl returned");pig.discard();h.succeed();
    }
    @GameTest(template="empty") public static void metabolismRollOnlyRunsAtHundredTickBoundary(GameTestHelper h) {
        var pig=mob(h,EntityType.PIG); needs(pig,.5F,.5F,.5F); pig.tickCount=99;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(pig)); h.assertValueEqual(Livestock032.needs(pig),new LivestockRules032.Needs(.5F,.5F,.5F),"99 does not consume resource");
        pig.tickCount=100; pig.getRandom().setSeed(seed(r->r.nextInt(10)==0)); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(pig));
        h.assertValueEqual(Livestock032.needs(pig),new LivestockRules032.Needs(.5F,.5F,.5F),"Failed one-in-ten production roll skips entire wellness update"); pig.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void starvationFreezesBabyGrowthAndVesselRestoresIt(GameTestHelper h) {
        var pig=mob(h,EntityType.PIG); pig.setAge(-100); needs(pig,.049F,1,1); pig.aiStep(); h.assertValueEqual(pig.getAge(),-100,"Under .05 food stalls source baby");
        needs(pig,1,.049F,1); pig.aiStep(); h.assertValueEqual(pig.getAge(),-100,"Under .05 water separately stalls source baby");
        Livestock032.drink(pig,1); pig.aiStep(); h.assertValueEqual(pig.getAge(),-99,"Restored water resumes one tick growth"); pig.discard();h.succeed();
    }
    @GameTest(template="empty") public static void sourceNewbornAgeAndCooldownDoNotAffectModernRabbit(GameTestHelper h) {
        var pig=mob(h,EntityType.PIG); pig.setBaby(true); h.assertValueEqual(pig.getAge(),-64000,"Source newborn duration");
        var rabbit=mob(h,EntityType.RABBIT); rabbit.setBaby(true); h.assertValueEqual(rabbit.getAge(),-24000,"Modern species unchanged outside source animal list"); pig.discard();rabbit.discard();h.succeed();
    }
    @GameTest(template="empty") public static void horseYoungAgeAdvancesOnlyOnOffsetTwoHundredTicks(GameTestHelper h) {
        var horse=mob(h,EntityType.HORSE); horse.setAge(-500); horse.tickCount=-horse.getId()*47+1; horse.aiStep(); h.assertValueEqual(horse.getAge(),-500,"Non-boundary horse young age stays still");
        horse.tickCount=-horse.getId()*47+200; horse.aiStep();h.assertValueEqual(horse.getAge(),-300,"Horse source batch grows by200"); horse.discard();h.succeed();
    }
    @GameTest(template="empty") public static void sourceFeedingAgeUnitsAreFiveAndCauldronRoofBlocksAdultEmergence(GameTestHelper h) {
        var sheep=mob(h,EntityType.SHEEP); sheep.setAge(-1000); sheep.ageUp(60); h.assertValueEqual(sheep.getAge(),-700,"Source60 seconds are300 age ticks");
        var pos=sheep.blockPosition(); h.getLevel().setBlock(pos,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,1),3);
        h.getLevel().setBlock(pos.above(),Blocks.STONE.defaultBlockState(),3); sheep.setAge(-1); sheep.aiStep(); h.assertValueEqual(sheep.getAge(),-1,"Animal cannot become adult inside blocked cauldron");
        h.getLevel().setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3); sheep.aiStep(); h.assertValueEqual(sheep.getAge(),0,"Removing roof permits adult age"); sheep.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void emptyRoofedCauldronClampsLargeAgeAdvanceToMinusOne(GameTestHelper h) {
        var sheep=mob(h,EntityType.SHEEP); sheep.setAge(-100);
        var pos=sheep.blockPosition(); h.getLevel().setBlock(pos,Blocks.CAULDRON.defaultBlockState(),3);
        h.getLevel().setBlock(pos.above(),Blocks.STONE.defaultBlockState(),3);
        sheep.ageUp(60); h.assertValueEqual(sheep.getAge(),-1,"nk.c clamps to -1 in empty or filled source cauldron even when one update crosses adulthood");
        h.getLevel().setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3); sheep.aiStep();
        h.assertValueEqual(sheep.getAge(),0,"Removing obstruction grows on the next tick rather than losing the preceding age advance"); sheep.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void injuredAdultMealHealsBeforeLaterBreedingMeal(GameTestHelper h) {
        var cow=mob(h,EntityType.COW); needs(cow,.1F,1,1); cow.setHealth(10); var player=h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,2));
        cow.mobInteract(player,InteractionHand.MAIN_HAND); h.assertTrue(cow.getHealth()==20 && !cow.isInLove() && Livestock032.needs(cow).food()==.6F,"Source call order: full-health breeding gate precedes half-health heal");
        for(int tick=0;tick<LivestockRules032.RECOVERY_TICKS;tick++) Livestock032.tickRecovery(cow);
        cow.mobInteract(player,InteractionHand.MAIN_HAND); h.assertTrue(cow.isInLove() && cow.getInLoveTime()==600 && player.getMainHandItem().isEmpty(),"Next full healthy meal enters600 ticks love and spends once"); cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void waterDeficiencyAndBabyCannotBeBypassedWithInfiniteWheat(GameTestHelper h) {
        var cow=mob(h,EntityType.COW); needs(cow,1,.1F,1); var player=h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,2));
        cow.mobInteract(player,InteractionHand.MAIN_HAND); h.assertTrue(player.getMainHandItem().getCount()==2 && !cow.isInLove(),"Full food does not cure thirst by wasting wheat");
        cow.setAge(-300); needs(cow,1,1,1); cow.mobInteract(player,InteractionHand.MAIN_HAND); h.assertTrue(player.getMainHandItem().getCount()==2 && cow.getAge()==-300,"Source babies cannot consume handed meals for modern instant growth"); cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void actualBirthInheritsMinimumNeedsAndSixteenThousandParentCooldown(GameTestHelper h) {
        var first=mob(h,EntityType.COW); var second=mob(h,EntityType.COW); needs(first,.35F,.7F,.9F);needs(second,.8F,.4F,.6F);
        first.spawnChildFromBreeding(h.getLevel(),second);
        var babies=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,first.getBoundingBox().inflate(2),e->e.isBaby());
        h.assertValueEqual(babies.size(),1,"Actual native breeding produces one child"); var child=babies.getFirst();
        h.assertValueEqual(child.getAge(),-64000,"Actual child carries source newborn age"); h.assertValueEqual(Livestock032.needs(child),new LivestockRules032.Needs(.35F,.4F,.6F),"Each parental minimum carried through actual event");
        h.assertTrue(first.getAge()==16000 && second.getAge()==16000,"Both parents source16000 age cooldown");first.discard();second.discard();child.discard();h.succeed();
    }
    @GameTest(template="empty") public static void cowMilkAndLootReadOneLiveNeedState(GameTestHelper h) {
        var cow=mob(h,EntityType.COW); CowFoodState.set(cow,new CowFoodState(1,20,.3F,.4F,.5F));
        h.assertValueEqual(Livestock032.needs(cow),new LivestockRules032.Needs(.3F,.4F,.5F),"Food attachment bridge writes single source needs"); needs(cow,.1F,.4F,.5F);
        h.assertFalse(SourceVanillaMobLoot.well(cow),"Actual meat predicate sees current metabolism"); h.assertTrue(CowFoodState.get(cow).food()==.1F && CowFoodState.get(cow).milk()==20,"Milk view sees same resources without rerandomization");cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void cowProducesPendingMilkEvenWhenNextWellnessRollFails(GameTestHelper h) {
        var cow=mob(h,EntityType.COW); CowFoodState.set(cow,new CowFoodState(1,20,.6F,.6F,.6F)); var s=Livestock032.state(cow);s.putInt("production_counter",7); Livestock032.save(cow,s);
        cow.tickCount=100; cow.getRandom().setSeed(seed(r->r.nextInt(10)==0)); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(cow));
        h.assertTrue(CowFoodState.get(cow).milk()==27 && Livestock032.state(cow).getInt("production_counter")==0,"produceGoods executes outside skipped90% branch"); cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void chickensNeverUseModernUnconditionalEggTimer(GameTestHelper h) {
        Chicken chicken=mob(h,EntityType.CHICKEN); var drops=capture(chicken); chicken.eggTime=1; chicken.aiStep(); h.assertTrue(count(drops,Items.EGG)==0 && chicken.eggTime>1000000,"Native timer can never bypass source production_counter"); chicken.captureDrops(null);chicken.discard();h.succeed();
    }
    @GameTest(template="empty") public static void chickenRegainsMissingFeatherBeforeEggOnChosenSourceRoll(GameTestHelper h) {
        Chicken chicken=mob(h,EntityType.CHICKEN); var s=Livestock032.state(chicken);s.putInt("maximum_feathers",2);s.putInt("feathers",0);s.putInt("production_counter",200);Livestock032.save(chicken,s); var drops=capture(chicken);
        chicken.getRandom().setSeed(seed(r->r.nextInt(500)==0));Livestock032.produceGoods(chicken);
        h.assertTrue(SourceVanillaMobLoot.feathers(chicken)==1 && Livestock032.state(chicken).getInt("production_counter")==100 && count(drops,Items.EGG)==0,"Priority gainFeather consumes100 and returns before egg draw"); chicken.captureDrops(null);chicken.discard();h.succeed();
    }
    @GameTest(template="empty") public static void chickenFullFeatherCapacityProducesOneLooseFeather(GameTestHelper h) {
        Chicken chicken=mob(h,EntityType.CHICKEN); var s=Livestock032.state(chicken);s.putInt("maximum_feathers",1);s.putInt("feathers",1);s.putInt("production_counter",100);Livestock032.save(chicken,s);var drops=capture(chicken);
        chicken.getRandom().setSeed(seed(r->r.nextInt(500)==0));Livestock032.produceGoods(chicken);h.assertTrue(count(drops,Items.FEATHER)==1 && SourceVanillaMobLoot.feathers(chicken)==1 && Livestock032.state(chicken).getInt("production_counter")==0,"Source full feather inventory emits one instead of losing counter");chicken.captureDrops(null);chicken.discard();h.succeed();
    }
    @GameTest(template="empty") public static void chickenEggRequiresTwoHundredAndFivePercentRoll(GameTestHelper h) {
        Chicken chicken=mob(h,EntityType.CHICKEN);var s=Livestock032.state(chicken);s.putInt("production_counter",200);Livestock032.save(chicken,s);var drops=capture(chicken);
        chicken.getRandom().setSeed(seed(r->r.nextInt(500)!=0 && r.nextInt(20)==0));Livestock032.produceGoods(chicken);
        h.assertTrue(count(drops,Items.EGG)==1 && Livestock032.state(chicken).getInt("production_counter")==0,"Literal rq200+nextInt20 after failedfeather draw");chicken.captureDrops(null);chicken.discard();h.succeed();
    }
    @GameTest(template="empty") public static void passiveFeatherDropRetainsOneButCombatCanLoseLast(GameTestHelper h) {
        Chicken chicken=mob(h,EntityType.CHICKEN);SourceVanillaMobLoot.setFeathers(chicken,1);var drops=capture(chicken);
        h.assertFalse(Livestock032.tryDropFeather(chicken,true),"Passive molt retains last feather");h.assertTrue(Livestock032.tryDropFeather(chicken,false),"Combat shedding may lose last");h.assertTrue(count(drops,Items.FEATHER)==1 && SourceVanillaMobLoot.feathers(chicken)==0,"Both use saved death-loot feather stock");chicken.captureDrops(null);chicken.discard();h.succeed();
    }
    @GameTest(template="empty") public static void manurePeriodsAndHungerGateUseEveryAdultSpecies(GameTestHelper h) {
        for(EntityType<? extends Animal> type:List.of(EntityType.COW,EntityType.PIG,EntityType.SHEEP,EntityType.CHICKEN)) {
            var e=mob(h,type);var s=Livestock032.state(e);s.putInt("manure_countdown",1);Livestock032.save(e,s);var drops=capture(e);needs(e,.049F,1,1);e.tickCount=1;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(e));h.assertTrue(count(drops,MiteFoods.MANURE.get())==0 && Livestock032.state(e).getInt("manure_countdown")==1,"Desperate hunger pauses manure timer "+type);
            needs(e,.05F,1,1);NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(e));int timer=Livestock032.state(e).getInt("manure_countdown");int period=Livestock032.manurePeriod(e);
            h.assertTrue(count(drops,MiteFoods.MANURE.get())==1 && timer>=period/2 && timer<period/2+period,"Inclusive.05 source reset range for "+type);e.captureDrops(null);e.discard();
        } h.succeed();
    }
    @GameTest(template="empty") public static void sheepChewingStopsForSourceFleeDecisionButNotStandalonePanic(GameTestHelper h) {
        var sheep=mob(h,EntityType.SHEEP); var goal=new LivestockGrazingGoal032(sheep); goal.start();
        R196AnimalAi032.setSpooked(sheep,h.getLevel().getGameTime()+200); R196AnimalAi032.state(sheep).putInt("panic_countdown",150);
        h.assertTrue(goal.canContinueToUse(),"pn does not directly reject spooked/panic flags; actual MOVE task competition remains separate");
        R196AnimalAi032.state(sheep).putBoolean("decided_to_flee",true);
        h.assertFalse(goal.canContinueToUse(),"pn explicitly rejects has_decided_to_flee"); sheep.discard();h.succeed();
    }
    @GameTest(template="empty") public static void grazingActualBiteRegrowsWoolAndAddsHalfFood(GameTestHelper h) {
        Sheep sheep=mob(h,EntityType.SHEEP);sheep.setSheared(true);needs(sheep,.1F,1,1);var pos=sheep.blockPosition();h.getLevel().setBlock(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        var goal=new LivestockGrazingGoal032(sheep);goal.start();for(int i=0;i<35;i++)goal.tick();h.assertTrue(sheep.isSheared(),"No wool before source timer4");goal.tick();
        h.assertTrue(!sheep.isSheared() && Livestock032.needs(sheep).food()==.6F && h.getLevel().getBlockState(pos.below()).is(Blocks.DIRT),"Actual source bite turns grassdirt, restores wool and food");sheep.discard();h.succeed();
    }
    @GameTest(template="empty") public static void grazingBabyAteAddsOnlyThreeHundredAgeTicks(GameTestHelper h) {
        Sheep sheep=mob(h,EntityType.SHEEP);sheep.setAge(-1000);needs(sheep,.2F,1,1);sheep.ate();h.assertTrue(sheep.getAge()==-700 && Livestock032.needs(sheep).food()==.7F,"rz.n routes through source age5, half-food once");sheep.discard();h.succeed();
    }
    @GameTest(template="empty") public static void disabledMobGriefingBlocksActualGrazingAndFreeWool(GameTestHelper h) {
        Sheep sheep=mob(h,EntityType.SHEEP);sheep.setSheared(true);needs(sheep,.2F,1,1);var pos=sheep.blockPosition();h.getLevel().setBlock(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        var rule=h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);boolean previous=rule.get();rule.set(false,h.getLevel().getServer());
        try {var goal=new LivestockGrazingGoal032(sheep);goal.start();for(int i=0;i<36;i++)goal.tick();h.assertTrue(sheep.isSheared() && Livestock032.needs(sheep).food()==.2F && h.getLevel().getBlockState(pos.below()).is(Blocks.GRASS_BLOCK),"Cancelled grief neither removesgrass nor invents food/wool");}
        finally {rule.set(previous,h.getLevel().getServer());sheep.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void sixFireHitsBurnOffWoolAndSourceCounterResetsOnLoad(GameTestHelper h) {
        Sheep sheep=mob(h,EntityType.SHEEP);for(int i=0;i<5;i++)Livestock032.damaged(sheep,sheep.damageSources().onFire(),1);
        h.assertFalse(sheep.isSheared(),"Five source wool events are retained");var saved=new CompoundTag();sheep.saveWithoutId(saved);Sheep other=EntityType.SHEEP.create(h.getLevel());other.load(saved);
        h.assertTrue(((LivestockSheep032View)other).mite$woolFireDamage()==0,"Source rz does not serialize partial wool fire accumulation");
        Livestock032.damaged(sheep,sheep.damageSources().onFire(),1);h.assertTrue(sheep.isSheared() && ((LivestockSheep032View)sheep).mite$woolFireDamage()==0,"Sixth event on same sheep burns wool and clears counter");sheep.discard();other.discard();h.succeed();
    }
    @GameTest(template="empty") public static void sourcePigTramplesFlowerWithoutDropWhileChickenReturnsIt(GameTestHelper h) {
        var pig=mob(h,EntityType.PIG); var pos=pig.blockPosition().below();
        h.getLevel().setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState().setValue(LivestockGrass032.TRAMPLINGS,14),3);
        h.getLevel().setBlock(pos.above(),Blocks.DANDELION.defaultBlockState(),3);
        pig.getRandom().setSeed(seed(r->r.nextFloat()<.5F)); LivestockGrass032.trampleUnder(pig);
        h.assertTrue(h.getLevel().getBlockState(pos.above()).isAir(),"Pig tramples source flower");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,pig.getBoundingBox().inflate(2)).stream().noneMatch(i->i.getItem().is(Items.DANDELION)),"ane excludes pig/sheep/cow flower drop");
        pig.discard(); var chicken=mob(h,EntityType.CHICKEN);
        h.getLevel().setBlock(pos.above(),Blocks.DANDELION.defaultBlockState(),3);
        chicken.getRandom().setSeed(seed(r->r.nextFloat()<.5F)); LivestockGrass032.trampleUnder(chicken);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,chicken.getBoundingBox().inflate(2)).stream().anyMatch(i->i.getItem().is(Items.DANDELION)),"Chicken source trampling can return flower"); chicken.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void shortGrassCandidateSpiralKeepsSourceVerticalAndCandidateLimit(GameTestHelper h) {
        var cow=mob(h,EntityType.COW);var pos=cow.blockPosition();h.getLevel().setBlock(pos.above(),Blocks.DANDELION.defaultBlockState(),3);h.getLevel().setBlock(pos.east(),Blocks.SHORT_GRASS.defaultBlockState(),3);
        var candidates=new LivestockSeeking032(cow,false).candidates(16,2,1);h.assertTrue(candidates.size()==1 && candidates.getFirst().equals(pos.above()),"abw spiral examines central vertical column before east, returns immediately atmaxCandidates");cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void hungryGoalWaitsAtFoodWithoutDrainingMovementState(GameTestHelper h) {
        var cow=mob(h,EntityType.COW);h.getLevel().setBlock(cow.blockPosition().east(),Blocks.DANDELION.defaultBlockState(),3);needs(cow,.2F,1,1);var goal=new LivestockSeeking032(cow,false);
        h.assertTrue(goal.canUse(),"Hungry animal already atfood starts withoutrandom roll");goal.start();h.assertTrue(goal.canContinueToUse() && cow.getNavigation().isDone(),"Goal waits atsource until wellness updates restorefood");needs(cow,.96F,1,1);h.assertFalse(goal.canContinueToUse(),"Strict .95 endpoint stops onlyafterrestored");goal.stop();cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void actualMooshroomStewConsumesTwentyFiveMilkPerBowl(GameTestHelper h) {
        MushroomCow cow=mob(h,EntityType.MOOSHROOM);CowFoodState.set(cow,new CowFoodState(1,100,1,1,1));var player=h.makeMockPlayer(GameType.SURVIVAL);
        for(int i=0;i<4;i++) {player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOWL));cow.mobInteract(player,InteractionHand.MAIN_HAND);h.assertTrue(player.getMainHandItem().is(Items.MUSHROOM_STEW) && CowFoodState.get(cow).milk()==75-i*25,"Modern retained stew spends one source milk volume");}
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOWL));h.assertFalse(cow.mobInteract(player,InteractionHand.MAIN_HAND).consumesAction(),"Fifth stew rejected withoutbudget");h.assertTrue(player.getMainHandItem().is(Items.BOWL),"Rejected stew keeps bowl");cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void brownMooshroomFlowerEffectSurvivesDeniedStewUntilMilkRestored(GameTestHelper h) {
        MushroomCow cow=mob(h,EntityType.MOOSHROOM);cow.setVariant(MushroomCow.MushroomType.BROWN);CowFoodState.set(cow,new CowFoodState(1,0,1,1,1));var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DANDELION));cow.mobInteract(player,InteractionHand.MAIN_HAND);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOWL));cow.mobInteract(player,InteractionHand.MAIN_HAND);
        h.assertTrue(player.getMainHandItem().is(Items.BOWL),"Insufficientmilk rejectswithoutspending flowerstate");CowFoodState.set(cow,CowFoodState.get(cow).milk(25));cow.mobInteract(player,InteractionHand.MAIN_HAND);
        h.assertTrue(player.getMainHandItem().is(Items.SUSPICIOUS_STEW) && player.getMainHandItem().has(DataComponents.SUSPICIOUS_STEW_EFFECTS) && CowFoodState.get(cow).milk()==0,"Retained modern flower ingredient works after restoredbudget");cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void mooshroomShearingConversionRetainsResourcesAndProduction(GameTestHelper h) {
        MushroomCow original=mob(h,EntityType.MOOSHROOM);CowFoodState.set(original,new CowFoodState(1,37,.32F,.41F,.52F));var s=Livestock032.state(original);s.putInt("production_counter",17);s.putInt("manure_countdown",981);Livestock032.save(original,s);
        original.shear(net.minecraft.sounds.SoundSource.PLAYERS);var cows=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,original.getBoundingBox().inflate(1),e->e.getType()==EntityType.COW);
        h.assertValueEqual(cows.size(),1,"Actual modern shear creates one cow");var cow=cows.getFirst();h.assertValueEqual(CowFoodState.get(cow),new CowFoodState(1,37,.32F,.41F,.52F),"Modern conversion preserves source production resources");h.assertTrue(Livestock032.state(cow).getInt("production_counter")==17 && Livestock032.state(cow).getInt("manure_countdown")==981,"Counter and manure are transferred not rerolled");cow.discard();h.succeed();
    }
}
