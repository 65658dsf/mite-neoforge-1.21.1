package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Wolf;
import io.github.ningmeng123.mite.entity.animal.NativeAnimalSpecial032;
import io.github.ningmeng123.mite.entity.animal.SourceAquatic032;
import io.github.ningmeng123.mite.entity.animal.SourcePetGoals032;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent sf/rx/rs/ro/sc/sq literals through installed goals and live interactions. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class NativeAnimals032GameTests {
    private static <T extends Mob> T mob(GameTestHelper h,EntityType<T> type) {
        T value=type.create(h.getLevel()); BlockPos p=h.absolutePos(new BlockPos(3,5,3));
        value.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0); value.setNoAi(true); value.setNoGravity(true); h.getLevel().addFreshEntity(value); return value;
    }
    private static Player player(GameTestHelper h,Mob near) { var p=h.makeMockPlayer(GameType.SURVIVAL); p.moveTo(near.position().add(1,0,0)); return p; }
    private static long seed(Predicate<Random> condition) { for(long i=0;i<100000;i++)if(condition.test(new Random(i)))return i; throw new AssertionError("No independent random fixture"); }
    private static boolean goal(Mob mob,Class<? extends Goal> type,int priority) { return mob.goalSelector.getAvailableGoals().stream().anyMatch(g->type.isInstance(g.getGoal())&&g.getPriority()==priority); }
    private static void post(Mob mob) { NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(mob)); }
    private static Boat boat(GameTestHelper h,Mob near) { var b=new Boat(h.getLevel(),near.getX(),near.getY(),near.getZ()); b.setNoGravity(true); h.getLevel().addFreshEntity(b); return b; }

    @GameTest(template="ranged_027") public static void wolfTamingHasSourceThresholdsAndLevelInfluence(GameTestHelper h) {
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.049F,1,100,false),-1,"First .05 remains aggressive irrespective of level");
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.05F,1,100,false),0,".05 starts unconditional plain failure");
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.9F,0,0,false),1,"Adjusted ordinary wolf threshold .8 succeeds");
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.4F,.5F,40,false),0,"Source float operations produce .79999995 at this boundary");
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.4F,.5F,41,false),1,"sf level bonus .02 raises adjusted roll above .8");
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.19F,1,100,true),-1,"Dire first .2 aggressive band");
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.39F,1,100,true),0,"Dire first .4 plain band");
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.6F,.5F,40,true),1,"Dire adjusted threshold1"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void wolfTamingLevelBenefitsStopAtTwoHundred(GameTestHelper h) {
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.4005F,.1F,199,false),0,"Ordinary wolf still improves from199 to200");
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.4005F,.1F,200,false),1,"Ordinary wolf reaches its capped level bonus at200");
        h.assertValueEqual(R196Wolf.tamingOutcome(.6F,.1F,199),0,"Dire wolf still improves from199 to200");
        h.assertValueEqual(R196Wolf.tamingOutcome(.6F,.1F,200),1,"Dire wolf reaches its capped level bonus at200");
        for(int level:new int[]{200,201,Integer.MAX_VALUE}) {
            h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.4F,.0999F,level,false),0,"Ordinary wolf cannot improve beyond its200-level bonus");
            h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.6F,.0999F,level,true),0,"Dire wolf delegated bonus stops at200");
            h.assertValueEqual(R196Wolf.tamingOutcome(.6F,.0999F,level),0,"Dire wolf direct bonus stops at200");
        }
        h.assertValueEqual(NativeAnimalSpecial032.tamingOutcome(.4F,.5F,-40,false),-1,"Ordinary negative-level penalty is retained");
        h.assertValueEqual(R196Wolf.tamingOutcome(.6F,.1F,-100),-1,"Dire negative-level penalty is retained");
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void actualWolfBoneFailureConsumesOnceAndCooldownBlocksReroll(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); var p=player(h,wolf); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BONE,3));
        wolf.getRandom().setSeed(seed(r->{float f=r.nextFloat();return f>=.05F&&f<.1F;})); wolf.mobInteract(p,InteractionHand.MAIN_HAND);
        h.assertTrue(!wolf.isTame()&&wolf.getPersistentData().getInt(NativeAnimalSpecial032.TAME_COOLDOWN)==100&&p.getMainHandItem().getCount()==2,"Actual source failed tame costs one bone and100ticks");
        wolf.mobInteract(p,InteractionHand.MAIN_HAND); h.assertValueEqual(p.getMainHandItem().getCount(),2,"Cooldown interaction cannot consume or reroll"); post(wolf);
        h.assertValueEqual(wolf.getPersistentData().getInt(NativeAnimalSpecial032.TAME_COOLDOWN),99,"Real post tick decrements cooldown once"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void successfulWolfTamingPreservesMissingHealth(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); var p=player(h,wolf); wolf.setHealth(3); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BONE,2));
        wolf.getRandom().setSeed(seed(r->r.nextFloat()>.9F)); wolf.mobInteract(p,InteractionHand.MAIN_HAND);
        h.assertTrue(wolf.isTame()&&wolf.isOrderedToSit()&&wolf.getMaxHealth()==12&&wolf.getHealth()==7,"sf wild8 to tame12 adds4 health retaining five missing");
        h.assertValueEqual(p.getMainHandItem().getCount(),1,"One successful bone consumed"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void successfulDireTamingPreservesMissingHealth(GameTestHelper h) {
        R196Wolf wolf=(R196Wolf)mob(h,MiteMobs.type(MobKind.DIRE_WOLF)); var p=player(h,wolf); wolf.setHealth(5); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BONE));
        wolf.getRandom().setSeed(seed(r->r.nextFloat()>.95F)); wolf.mobInteract(p,InteractionHand.MAIN_HAND);
        h.assertTrue(wolf.isTame()&&wolf.getMaxHealth()==24&&wolf.getHealth()==13,"Dire wild16 to tame24 preserves eleven missing health"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void hostileWolfDoesNotAcceptBoneOrFood(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); var p=player(h,wolf); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BONE,2)); wolf.getPersistentData().putBoolean(NativeAnimalSpecial032.HOSTILE,true);
        wolf.mobInteract(p,InteractionHand.MAIN_HAND); h.assertTrue(!wolf.isTame()&&p.getMainHandItem().getCount()==2,"Hostile wolf denies tame before consuming"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void wolfMeatPickupGatesHalfHealthAndBreeding(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); wolf.setTame(true,true); var meat=new ItemStack(Items.BEEF);
        wolf.setHealth(7); h.assertFalse(NativeAnimalSpecial032.shouldEat(wolf,meat),"Tame health above one-half refrains from dropped food");
        wolf.setHealth(6); h.assertTrue(NativeAnimalSpecial032.shouldEat(wolf,meat),"Exactly one-half accepts dropped source meat");
        wolf.setOrderedToSit(true); h.assertTrue(NativeAnimalSpecial032.shouldEat(wolf,meat),"Source sitting blocks seeking, not already touching food"); wolf.setOrderedToSit(false); wolf.setAge(1);
        h.assertFalse(NativeAnimalSpecial032.shouldEat(wolf,meat),"Positive breeding cooldown denies food"); wolf.setAge(0);wolf.setHealth(wolf.getMaxHealth());wolf.setInLove(null);wolf.setHealth(6);
        h.assertFalse(NativeAnimalSpecial032.shouldEat(wolf,meat),"Existing love denies food"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sittingInjuredWolfCanEatAlreadyTouchingDroppedMeat(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); wolf.setTame(true,true); wolf.setOrderedToSit(true); wolf.setHealth(6);
        ItemEntity dropped=new ItemEntity(h.getLevel(),wolf.getX(),wolf.getY(),wolf.getZ(),new ItemStack(Items.BEEF,2)); dropped.setNoPickUpDelay(); h.getLevel().addFreshEntity(dropped);
        io.github.ningmeng123.mite.entity.R196NpcNeeds.pickUp(wolf,dropped);
        h.assertTrue(wolf.getHealth()==12&&dropped.getItem().getCount()==1&&wolf.isOrderedToSit(),"og contact pickup can heal sitting tame pet and consume one meat without movement"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void droppedFishHealsHalfWithoutCreatingLoveBeforeFullHealth(GameTestHelper h) {
        var cat=mob(h,EntityType.CAT); cat.setHealth(cat.getMaxHealth()*.25F); NativeAnimalSpecial032.onFoodEaten(cat,new ItemStack(Items.COD));
        h.assertTrue(cat.getHealth()==cat.getMaxHealth()*.75F&&!cat.isInLove(),"rp tests full health for love before og half-max heal");
        cat.setHealth(cat.getMaxHealth()); NativeAnimalSpecial032.onFoodEaten(cat,new ItemStack(Items.COD)); h.assertTrue(cat.isInLove(),"Full healthy fish feeding starts love"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void rottenFleshDoesNotStartWolfLove(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); wolf.setHealth(wolf.getMaxHealth()); NativeAnimalSpecial032.onFoodEaten(wolf,new ItemStack(Items.ROTTEN_FLESH));
        h.assertFalse(wolf.isInLove(),"rp skips bX for source rotten flesh"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void realNonPlayerDamageUsesWolfHalfPlusOneFormula(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); var attacker=mob(h,EntityType.COW); float before=wolf.getHealth(); wolf.hurt(wolf.damageSources().mobAttack(attacker),3);
        h.assertValueEqual(wolf.getHealth(),before-2,"sf immediate nonplayer nonarrow damage=(3+1)/2"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void hellhoundInheritsSourceWolfNonPlayerDamageFraction(GameTestHelper h) {
        var wolf=mob(h,MiteMobs.type(MobKind.HELLHOUND));var attacker=mob(h,EntityType.COW);float before=wolf.getHealth();wolf.hurt(wolf.damageSources().mobAttack(attacker),3);
        h.assertValueEqual(wolf.getHealth(),before-2,"EntityHellhound inherits sf damage fraction while remaining untamable");h.succeed();
    }
    @GameTest(template="ranged_027") public static void wolfTargetCountdownIsClampedSavedAndCleared(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); var prey=mob(h,EntityType.CHICKEN); wolf.setTarget(prey); int count=wolf.getPersistentData().getInt(NativeAnimalSpecial032.WOLF_TARGET_TIME);
        h.assertTrue(count>=701&&count<=800,"Source clamp of800+nextInt100-nextInt100"); wolf.getPersistentData().putInt(NativeAnimalSpecial032.WOLF_TARGET_TIME,3);
        CompoundTag saved=new CompoundTag(); wolf.saveWithoutId(saved); var restored=EntityType.WOLF.create(h.getLevel()); restored.load(saved);
        h.assertValueEqual(restored.getPersistentData().getInt(NativeAnimalSpecial032.WOLF_TARGET_TIME),3,"Target timer persists without reroll");
        for(int i=0;i<3;i++)post(wolf); h.assertTrue(wolf.getTarget()==null&&!wolf.getPersistentData().getBoolean(NativeAnimalSpecial032.HOSTILE),"Expiry clears target and hostile state"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void successfulWolfAttackRefreshesActualTargetTimer(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); var cow=mob(h,EntityType.COW); wolf.setTarget(cow); wolf.getPersistentData().putInt(NativeAnimalSpecial032.WOLF_TARGET_TIME,7);
        h.assertTrue(wolf.doHurtTarget(cow),"Actual wolf melee damage succeeds"); int timer=wolf.getPersistentData().getInt(NativeAnimalSpecial032.WOLF_TARGET_TIME);
        h.assertTrue(timer>=701&&timer<=800,"sf successful attack on surviving prey restarts independently clamped source countdown"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void tamedPetThreatMemoryRequiresHostileActualTargetAndExpires(GameTestHelper h) {
        var cat=mob(h,EntityType.CAT); cat.setTame(true,true); var zombie=mob(h,EntityType.ZOMBIE);var cow=mob(h,EntityType.COW);zombie.setTarget(cat);cow.setTarget(cat);
        h.assertTrue(NativeAnimalSpecial032.isThreat(cat,zombie),"oq hostile marker with current attack target and sight");
        h.assertFalse(NativeAnimalSpecial032.isThreat(cat,cow),"Actual livestock target does not invent hostile marker");
        long value=4;cat.getRandom().setSeed(value);NativeAnimalSpecial032.noticeThreat(cat,zombie);int duration=(new Random(value).nextInt(10)+5)*10;
        cat.tickCount=-cat.getId()*47+1;for(int i=0;i<duration-1;i++)NativeAnimalSpecial032.tickThreat(cat);
        h.assertTrue(NativeAnimalSpecial032.threateningEntity(cat)==zombie,"Transient source warning remembers living threat through duration-1");NativeAnimalSpecial032.tickThreat(cat);
        h.assertTrue(NativeAnimalSpecial032.threateningEntity(cat)==null,"Source50..140 countdown expires exactly");NativeAnimalSpecial032.noticeThreat(cat,zombie);cat.setTarget(zombie);
        h.assertTrue(NativeAnimalSpecial032.threateningEntity(cat)==null,"Pet's own attack target ends passive threat warning"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceTemptRetainsPreviousVisiblePlayerWithFourBlockWeight(GameTestHelper h) {
        var cow=mob(h,EntityType.COW);var old=h.makeMockServerPlayerInLevel();var newer=h.makeMockServerPlayerInLevel();
        try {
            old.moveTo(cow.position().add(5,0,0));old.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT));newer.moveTo(cow.position().add(11,0,0));newer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT));
            var goal=new SourcePetGoals032.Tempt(cow,1.25,stack->stack.is(Items.WHEAT),false);h.assertTrue(goal.canUse()&&goal.temptingPlayer()==old,"Source10block scan finds first visible holding player");goal.start();
            newer.moveTo(cow.position().add(2,0,0));h.assertTrue(goal.canContinueToUse()&&goal.temptingPlayer()==old,"Previous visible distance5-4 wins newcomer distance2");
            newer.moveTo(cow.position().add(.5,0,0));h.assertTrue(goal.canContinueToUse()&&goal.temptingPlayer()==newer,"Distance .5 finally beats weighted old distance1");goal.stop();
        } finally { old.server.getPlayerList().remove(old);newer.server.getPlayerList().remove(newer); }
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceLivestockTemptWellnessWaterRestoreAndHundredCooldown(GameTestHelper h) {
        var cow=mob(h,EntityType.COW);var p=h.makeMockServerPlayerInLevel();
        try {
            p.moveTo(cow.position().add(4,0,0));p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT));
            var goal=new SourcePetGoals032.Tempt(cow,1.25,stack->stack.is(Items.WHEAT),false);
            io.github.ningmeng123.mite.entity.animal.Livestock032.needs(cow,new io.github.ningmeng123.mite.entity.animal.LivestockRules032.Needs(.7F,.1F,1));
            h.assertFalse(goal.canUse(),"qu full-health unwell nonhungry livestock refuses temptation");
            io.github.ningmeng123.mite.entity.animal.Livestock032.needs(cow,new io.github.ningmeng123.mite.entity.animal.LivestockRules032.Needs(.4F,.1F,1));
            h.assertTrue(goal.canUse(),"Hungry livestock passes source wellness refusal gate");cow.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER,8);goal.start();
            h.assertValueEqual(cow.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER),0F,"qu start temporarily permits water");
            p.setYRot(90);h.assertTrue(goal.canContinueToUse(),"Generic livestock scared=false ignores close player head motion");goal.stop();
            h.assertValueEqual(cow.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER),8F,"qu stop restores prior water cost");
            for(int i=0;i<100;i++)h.assertFalse(goal.canUse(),"qu exact100 activation attempts remain on cooldown");h.assertTrue(goal.canUse(),"Attempt101 can activate again");
        } finally { p.server.getPlayerList().remove(p); }
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceWolfAndCatGoalsReplaceModernDefaults(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF); var cat=mob(h,EntityType.CAT); var ocelot=mob(h,EntityType.OCELOT);
        h.assertTrue(goal(wolf,SourcePetGoals032.FollowOwner.class,5)&&goal(wolf,SourcePetGoals032.Beg.class,8)&&goal(wolf,SourcePetGoals032.Leap.class,3),"sf source pet movement goal set");
        h.assertTrue(goal(cat,SourcePetGoals032.FollowOwner.class,5)&&goal(cat,SourcePetGoals032.CatPerch.class,6)&&goal(cat,SourcePetGoals032.CatAttack.class,8),"rx source tame cat movement goal set");
        h.assertValueEqual(ocelot.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal() instanceof SourcePetGoals032.Tempt).count(),2L,"Both source fish tempt goals survive bq overwrite"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourcePreyStartAttacksLivingMountAndRefreshesDespawnGrace(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF);var chicken=mob(h,EntityType.CHICKEN);var pig=mob(h,EntityType.PIG);chicken.startRiding(pig,true);
        var goal=new SourcePetGoals032.Prey(wolf,net.minecraft.world.entity.animal.Chicken.class,1,false);h.assertTrue(goal.canUse(),"Source rb accepts mature hungry wild wolf and poultry candidate");goal.start();
        h.assertTrue(wolf.getTarget()==pig,"qy switches candidate to attackable LivingEntity mount before target assignment");
        h.assertValueEqual(io.github.ningmeng123.mite.entity.animal.SourceAnimalDespawn032.counter(wolf),-1200,"re.start lowers source despawn counter to -1200");h.succeed();
    }
    @GameTest(template="ranged_027") public static void sightedWolfPreySurvivesSixtyHiddenChecksThenStops(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF);var skeleton=mob(h,EntityType.SKELETON);skeleton.setPos(wolf.getX()+4,wolf.getY(),wolf.getZ());
        var goal=new SourcePetGoals032.Prey(wolf,net.minecraft.world.entity.monster.Skeleton.class,1,true);h.assertTrue(goal.canUse(),"Initial source sighted prey accepted");goal.start();
        for(int y=0;y<3;y++)h.getLevel().setBlockAndUpdate(wolf.blockPosition().offset(2,y,0),Blocks.STONE.defaultBlockState());wolf.getSensing().tick();
        for(int i=0;i<60;i++)h.assertTrue(goal.canContinueToUse(),"re.b keeps target for first sixty consecutive unseen checks");
        h.assertFalse(goal.canContinueToUse(),"Check61 exceeds source unseen limit60");h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourcePreyOneInFortyUpdateRetargetsCloserVisibleAnimal(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF);var first=mob(h,EntityType.CHICKEN);first.setPos(wolf.getX()+5,wolf.getY(),wolf.getZ());
        var goal=new SourcePetGoals032.Prey(wolf,net.minecraft.world.entity.animal.Chicken.class,1,false);h.assertTrue(goal.canUse(),"Original candidate selected before closer prey exists");goal.start();
        var nearer=mob(h,EntityType.CHICKEN);nearer.setPos(wolf.getX()+2,wolf.getY(),wolf.getZ());wolf.getSensing().tick();wolf.getRandom().setSeed(seed(r->r.nextInt(40)==0));goal.tick();
        h.assertTrue(wolf.getTarget()==nearer,"qy.e exact one-in-forty update selects nearer visible candidate");h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceWildWolfBreedingAllowsWildSameSpeciesOnly(GameTestHelper h) {
        Wolf first=mob(h,EntityType.WOLF),second=mob(h,EntityType.WOLF),dire=(Wolf)mob(h,MiteMobs.type(MobKind.DIRE_WOLF)); first.setInLove(null);second.setInLove(null);dire.setInLove(null);
        h.assertTrue(first.canMate(second),"sf source canMate has no modern tame requirement"); h.assertFalse(first.canMate(dire),"rp exact source class prevents Wolf x Dire"); second.setOrderedToSit(true);
        h.assertFalse(first.canMate(second),"Source mate sitting disallows"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceCatFoodAcceptsCodAndLargeFishOnly(GameTestHelper h) {
        var cat=mob(h,EntityType.CAT); var ocelot=mob(h,EntityType.OCELOT);
        for(var animal:List.of(cat,ocelot)) { h.assertTrue(animal.isFood(new ItemStack(Items.COD))&&animal.isFood(new ItemStack(MiteFoods.RAW_LARGE_FISH.get())),"rx two raw fish IDs");
            h.assertFalse(animal.isFood(new ItemStack(Items.SALMON))||animal.isFood(new ItemStack(Items.COOKED_COD)),"Modern salmon/cooked fish do not replace source raw-fish list"); }
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void inactiveLargeFishTemptationBlocksOcelotTaming(GameTestHelper h) {
        var ocelot=mob(h,EntityType.OCELOT); var p=player(h,ocelot); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MiteFoods.RAW_LARGE_FISH.get(),2));
        ocelot.mobInteract(p,InteractionHand.MAIN_HAND); h.assertTrue(p.getMainHandItem().getCount()==2&&ocelot.isAlive()&&ocelot.getPersistentData().getInt(NativeAnimalSpecial032.TAME_COOLDOWN)==0,"rx requires actual second temptation to be running, proximity alone insufficient"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void actualCatDamageCancelsOwnerSitOrder(GameTestHelper h) {
        var cat=mob(h,EntityType.CAT);var cow=mob(h,EntityType.COW);cat.setTame(true,true);cat.setOrderedToSit(true);cat.hurt(cat.damageSources().mobAttack(cow),1);
        h.assertFalse(cat.isOrderedToSit(),"rx negative damage releases source sitting controller");h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceCatOffspringCopiesMotherAppearanceAndOwner(GameTestHelper h) {
        var mother=mob(h,EntityType.CAT);var father=mob(h,EntityType.CAT);mother.setTame(true,true);mother.setOwnerUUID(java.util.UUID.randomUUID());
        mother.setVariant(net.minecraft.core.registries.BuiltInRegistries.CAT_VARIANT.getHolderOrThrow(net.minecraft.world.entity.animal.CatVariant.BLACK));
        father.setVariant(net.minecraft.core.registries.BuiltInRegistries.CAT_VARIANT.getHolderOrThrow(net.minecraft.world.entity.animal.CatVariant.RED));
        for(int i=0;i<10;i++) { var child=mother.getBreedOffspring(h.getLevel(),father);h.assertTrue(child.isTame()&&child.getVariant().equals(mother.getVariant())&&child.getOwnerUUID().equals(mother.getOwnerUUID()),"rx always inherits source mother cat type and owner"); }
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void wildOcelotLoveCannotActivateSourceBreedingGoal(GameTestHelper h) {
        var first=mob(h,EntityType.OCELOT);var second=mob(h,EntityType.OCELOT);first.setInLove(null);second.setInLove(null);
        var goal=first.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g instanceof SourcePetGoals032.CatBreed).findFirst().orElseThrow();
        h.assertFalse(goal.canUse(),"rx canMate requires tame state even if wild dropped feeding set love");h.succeed();
    }
    @GameTest(template="ranged_027") public static void actualOcelotFinalizeSpawnAddsTwoSourceNewbornsOnWorldRoll(GameTestHelper h) {
        var parent=mob(h,EntityType.OCELOT);long value=seed(r->{r.nextDouble();r.nextDouble();r.nextFloat();return r.nextInt(7)==0;});h.getLevel().getRandom().setSeed(value);
        parent.finalizeSpawn(h.getLevel(),h.getLevel().getCurrentDifficultyAt(parent.blockPosition()),net.minecraft.world.entity.MobSpawnType.NATURAL,null);
        var children=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Ocelot.class,parent.getBoundingBox().inflate(1),e->e!=parent);
        h.assertTrue(parent.getAge()==0&&children.size()==2&&children.stream().allMatch(c->c.getAge()==-64000),"rx one-in-seven after pinned modern Mob finalize draws: parent="+parent.getAge()+", child ages="+children.stream().map(c->c.getAge()).toList());children.forEach(Mob::discard);h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceHorseFoodTableRetainsAllSevenRows(GameTestHelper h) {
        var items=List.of(Items.WHEAT,Items.SUGAR,Items.BREAD,Items.HAY_BLOCK,Items.APPLE,Items.GOLDEN_CARROT,Items.GOLDEN_APPLE);
        int[][] rows={{2,60,3},{1,30,3},{7,180,3},{20,180,0},{3,60,3},{4,60,5},{10,240,10}};
        for(int i=0;i<items.size();i++) { var food=NativeAnimalSpecial032.horseFood(new ItemStack(items.get(i))); h.assertTrue(food.healing()==rows[i][0]&&food.growth()==rows[i][1]&&food.temper()==rows[i][2],"rs exact food row"+i); }
        h.assertTrue(NativeAnimalSpecial032.horseFood(new ItemStack(Items.CARROT))==null,"Plain modern carrot absent source horse row"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void actualHorseSugarUsesFiveTickGrowthUnits(GameTestHelper h) {
        Horse horse=mob(h,EntityType.HORSE); horse.setAge(-1000); var p=player(h,horse); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SUGAR,2)); horse.mobInteract(p,InteractionHand.MAIN_HAND);
        h.assertTrue(horse.getAge()==-850&&horse.getTemper()==3&&p.getMainHandItem().getCount()==1,"rs sugar30 growth input becomes150ticks and3temper");
        h.assertValueEqual(horse.getPersistentData().getInt(NativeAnimalSpecial032.HORSE_EATING),4000,"Untamed accepted nonhealing food creates rebellion timer"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void rebelliousHorseRefusesFoodUntilHealingNeeded(GameTestHelper h) {
        Horse horse=mob(h,EntityType.HORSE); var p=player(h,horse); var sugar=new ItemStack(Items.SUGAR); horse.getPersistentData().putInt(NativeAnimalSpecial032.HORSE_EATING,4000);
        h.assertFalse(NativeAnimalSpecial032.horseEat(horse,p,sugar),"Full untamed rebellious horse refuses repeat feed"); horse.setHealth(horse.getMaxHealth()-4);
        h.assertTrue(NativeAnimalSpecial032.horseEat(horse,p,sugar),"Healing bypasses source eating rebellion"); h.assertValueEqual(horse.getHealth(),horse.getMaxHealth()-3,"Sugar heals1"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void healthyTamedGoldenHorseFoodStartsBreeding(GameTestHelper h) {
        Horse horse=mob(h,EntityType.HORSE); horse.setTamed(true); var p=player(h,horse); horse.setHealth(horse.getMaxHealth());
        h.assertTrue(NativeAnimalSpecial032.horseEat(horse,p,new ItemStack(Items.GOLDEN_CARROT))&&horse.isInLove(),"Golden breeding only mature full-health tamed horse"); horse.resetLove();horse.setHealth(horse.getMaxHealth()-2);
        NativeAnimalSpecial032.horseEat(horse,p,new ItemStack(Items.GOLDEN_APPLE)); h.assertFalse(horse.isInLove(),"Love is checked before food heal, injured parent only heals"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceHorseRejectsDroppedFoodAndUndeadHandFood(GameTestHelper h) {
        Horse horse=mob(h,EntityType.HORSE); var undead=mob(h,EntityType.ZOMBIE_HORSE); var p=player(h,horse);
        h.assertFalse(NativeAnimalSpecial032.shouldEat(horse,new ItemStack(Items.WHEAT)),"rs has no isFoodItem override so dropped wheat not edible");
        h.assertFalse(NativeAnimalSpecial032.horseEat(undead,p,new ItemStack(Items.WHEAT)),"Undead source variants cannot eat"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void failedHorseMountEjectsAndSetsFourThousandTicks(GameTestHelper h) {
        Horse horse=mob(h,EntityType.HORSE); var p=player(h,horse); p.startRiding(horse,true); horse.setTemper(0); SourcePetGoals032.resolveRideAttempt(horse);
        h.assertTrue(!horse.isTamed()&&!p.isPassenger()&&horse.getTemper()==5&&horse.getPersistentData().getInt(NativeAnimalSpecial032.HORSE_RIDING)==4000,"qq failed tame gives5temper, eject and4000riding refusal"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void tameHorseClearsBothRebellionCounters(GameTestHelper h) {
        Horse horse=mob(h,EntityType.HORSE); horse.getPersistentData().putInt(NativeAnimalSpecial032.HORSE_EATING,4000); horse.getPersistentData().putInt(NativeAnimalSpecial032.HORSE_RIDING,2000); horse.setTamed(true); post(horse);
        h.assertTrue(horse.getPersistentData().getInt(NativeAnimalSpecial032.HORSE_EATING)==0&&horse.getPersistentData().getInt(NativeAnimalSpecial032.HORSE_RIDING)==0,"Tamed source living update clears both timers"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void actualHorseBreedingMutatesMotherAndLeavesSourceChildDefaults(GameTestHelper h) {
        Horse mother=mob(h,EntityType.HORSE),father=mob(h,EntityType.HORSE); mother.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);father.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
        // Independent JavaRandom seed2 draws coat4, markings2, then health7+1: (40+60+15+7+1)/3=41.
        // Unseeded health draws summing to5 legitimately leave the mother at40; exact41 also avoids float range rounding.
        mother.getRandom().setSeed(2L);
        var child=(AbstractHorse)mother.getBreedOffspring(h.getLevel(),father);
        h.assertTrue(child.getMaxHealth()==53&&child.getAttributeValue(Attributes.MOVEMENT_SPEED)==.225&&child.getAttributeValue(Attributes.JUMP_STRENGTH)==.7,"Source constructor defaults remain on newborn");
        h.assertTrue(mother.getMaxHealth()==41&&mother.getAttribute(Attributes.MAX_HEALTH).getBaseValue()==41&&mother.getMaxHealth()!=40&&father.getMaxHealth()==60&&father.getAttribute(Attributes.MAX_HEALTH).getBaseValue()==60,"Javap confirmed exact averaged health receiver is mother; father unchanged"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void horseAndDonkeyProduceMuleThroughActualBreedingMixin(GameTestHelper h) {
        Horse horse=mob(h,EntityType.HORSE); var donkey=mob(h,EntityType.DONKEY);
        h.assertTrue(horse.getBreedOffspring(h.getLevel(),donkey).getType()==EntityType.MULE&&donkey.getBreedOffspring(h.getLevel(),horse).getType()==EntityType.MULE,"rs mixed type creates pack mule both directions"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void ordinaryBatStartsFlyingAndOnlySolidNonGlassCeilingAllowsHanging(GameTestHelper h) {
        Bat bat=mob(h,EntityType.BAT); h.assertFalse(bat.isResting(),"ro Byte0 constructor starts flying"); BlockPos ceiling=bat.blockPosition().above();
        h.getLevel().setBlockAndUpdate(ceiling,Blocks.GLASS.defaultBlockState()); h.assertFalse(SourceAquatic032.canHang(bat,ceiling),"ro excludes glass20 even if solid bottom");
        h.getLevel().setBlockAndUpdate(ceiling,Blocks.STONE.defaultBlockState()); h.assertTrue(SourceAquatic032.canHang(bat,ceiling),"Source flat solid bottom and empty below");
        h.getLevel().setBlockAndUpdate(ceiling.below(),Blocks.STONE.defaultBlockState());h.assertFalse(SourceAquatic032.canHang(bat,ceiling),"Occupied body space prevents hanging"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void batDetachCreatesSixtyTickDelayAndExactHangingHeight(GameTestHelper h) {
        Bat bat=mob(h,EntityType.BAT); int y=bat.blockPosition().above().getY(); bat.getPersistentData().putInt("mite_bat_hang_y_032",y); bat.setResting(true); SourceAquatic032.batTick(bat);
        h.assertTrue(Math.abs(bat.getY()-(y-bat.getBbHeight()-.00625))<1e-6&&bat.getDeltaMovement().equals(Vec3.ZERO),"Ordinary hanging position uses current ceiling and zero velocity"); bat.setResting(false);
        h.assertValueEqual(bat.getPersistentData().getInt("mite_bat_prevent_hang_032"),60,"ro transition from hang to flight prevents hanging60ticks"); SourceAquatic032.batTick(bat);
        h.assertValueEqual(bat.getPersistentData().getInt("mite_bat_prevent_hang_032"),59,"Actual ordinary bat tick helper decrements once"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void batFlightTargetSupportsModernNegativeHeight(GameTestHelper h) {
        Bat bat=mob(h,EntityType.BAT); bat.setPos(bat.getX(),-20,bat.getZ()); bat.getPersistentData().putInt("mite_bat_prevent_hang_032",60); SourceAquatic032.batAi(bat);
        int target=bat.getPersistentData().getInt("mite_bat_flight_y_032"); h.assertTrue(target<0&&target>=h.getLevel().getMinBuildHeight()+1,"Modern minHeight replaces old flight clamp1 while preserving nearby target"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void squidFullWaterRayChecksIntermediateBlocksAndFlowLevel(GameTestHelper h) {
        BlockPos a=h.absolutePos(new BlockPos(2,5,2)),b=a.offset(4,0,0); for(int i=0;i<=4;i++)h.getLevel().setBlockAndUpdate(a.offset(i,0,0),Blocks.WATER.defaultBlockState());
        h.assertTrue(SourceAquatic032.fullWaterRay(h.getLevel(),Vec3.atCenterOf(a),Vec3.atCenterOf(b)),"All five crossed cells full source water"); h.getLevel().setBlockAndUpdate(a.offset(2,0,0),Blocks.STONE.defaultBlockState());
        h.assertFalse(SourceAquatic032.fullWaterRay(h.getLevel(),Vec3.atCenterOf(a),Vec3.atCenterOf(b)),"Solid middle blocks ray even with wet endpoints"); h.getLevel().setBlockAndUpdate(a.offset(2,0,0),Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1));
        h.assertFalse(SourceAquatic032.fullWaterRay(h.getLevel(),Vec3.atCenterOf(a),Vec3.atCenterOf(b)),"Low flowing water is not old full-water metadata"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void squidUsesSourceSwimGoalAndExcludesWaterCreatures(GameTestHelper h) {
        var squid=mob(h,EntityType.SQUID); var cod=mob(h,EntityType.COD); var glow=mob(h,EntityType.GLOW_SQUID);
        h.assertTrue(goal(squid,SourceAquatic032.SquidSwim.class,0),"sc dedicated movement installed through join");
        h.assertFalse(SourceAquatic032.prey(squid,cod)||SourceAquatic032.prey(squid,glow),"sc never preys on old water lineage"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void squidContactAddsTenSecondSlownessThree(GameTestHelper h) {
        var squid=mob(h,EntityType.SQUID); var pig=mob(h,EntityType.PIG); SourceAquatic032.touch(squid); var effect=pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(effect!=null&&effect.getDuration()==200&&effect.getAmplifier()==2,"sc real overlapping nonplayer contact applies200tick SlowIII"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void squidPlayerContactSlowIsIndependentOfShallowPreyGate(GameTestHelper h) {
        var squid=mob(h,EntityType.SQUID);var p=player(h,squid);p.moveTo(squid.position().add(.5,0,0));p.setOnGround(true);SourceAquatic032.touchPlayer(squid,p);
        var effect=p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);h.assertTrue(SourceAquatic032.playerShallow(p)&&effect!=null&&effect.getDuration()==200&&effect.getAmplifier()==2,"sc.b_(uf) nearby player contact is independent of hunting eligibility");
        p.removeAllEffects();var boat=boat(h,squid);p.startRiding(boat,true);SourceAquatic032.touchPlayer(squid,p);h.assertFalse(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Boat passenger exempt from source player contact slow");h.succeed();
    }
    @GameTest(template="ranged_027") public static void blueMoonClearsSquidHuntingButKeepsPlayerContactEffect(GameTestHelper h) {
        long time=h.getLevel().getDayTime();var squid=mob(h,EntityType.SQUID);var pig=mob(h,EntityType.PIG);var p=player(h,squid);
        try {
            h.getLevel().setDayTime(127L*24000+18000);squid.setTarget(pig);new SourceAquatic032.SquidSwim(squid).tick();
            h.assertTrue(SourceAquatic032.blueNight(h.getLevel())&&!SourceAquatic032.prey(squid,pig)&&squid.getTarget()==null,"sc blue moon night clears animal prey");
            p.moveTo(squid.position().add(.5,0,0));SourceAquatic032.touchPlayer(squid,p);h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"sc separate player-contact path remains enabled on Blue Moon");
        } finally { h.getLevel().setDayTime(time); }
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceShallowPlayerRuleDistinguishesOneAndTwoWaterLayers(GameTestHelper h) {
        var squid=mob(h,EntityType.SQUID); var p=player(h,squid); p.setOnGround(true); h.assertTrue(SourceAquatic032.playerShallow(p),"Grounded dry-eye player excluded"); p.setOnGround(false);
        var eye=BlockPos.containing(p.getX(),p.getEyeY(),p.getZ()); h.getLevel().setBlockAndUpdate(eye.below(),Blocks.WATER.defaultBlockState()); h.getLevel().setBlockAndUpdate(eye.below(2),Blocks.AIR.defaultBlockState());
        h.assertTrue(SourceAquatic032.playerShallow(p),"One layer below eye is shallow"); h.getLevel().setBlockAndUpdate(eye.below(2),Blocks.WATER.defaultBlockState());
        h.assertFalse(SourceAquatic032.playerShallow(p),"Two water layers below dry eye qualify deep water"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void targetedSquidSixBoatContactsDestroyAndDropSourceMaterials(GameTestHelper h) {
        var squid=mob(h,EntityType.SQUID); var p=player(h,squid); Boat boat=boat(h,squid); p.startRiding(boat,true); squid.setTarget(p); List<ItemEntity> drops=new ArrayList<>(); boat.captureDrops(drops);
        for(int i=0;i<5;i++)boat.push(squid); h.assertTrue(boat.isAlive()&&boat.getPersistentData().getInt("mite_squid_collisions_032")==5,"Five real push calls do not destroy source boat"); boat.push(squid);
        h.assertFalse(boat.isAlive(),"Sixth actual collision destroys targeted passenger boat");
        h.assertValueEqual(drops.stream().filter(e->e.getItem().is(Items.OAK_PLANKS)).mapToInt(e->e.getItem().getCount()).sum(),3,"sq drops three source planks");
        h.assertValueEqual(drops.stream().filter(e->e.getItem().is(Items.STICK)).mapToInt(e->e.getItem().getCount()).sum(),2,"sq drops two sticks"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void squidBoatContactsRequireTargetedRiderAndPersistCounter(GameTestHelper h) {
        var squid=mob(h,EntityType.SQUID); Boat boat=boat(h,squid); boat.push(squid); h.assertValueEqual(boat.getPersistentData().getInt("mite_squid_collisions_032"),0,"Untargeted squid does not count");
        boat.getPersistentData().putInt("mite_squid_collisions_032",4); CompoundTag saved=new CompoundTag();boat.saveWithoutId(saved); Boat restored=new Boat(h.getLevel(),boat.getX(),boat.getY(),boat.getZ());restored.load(saved);
        h.assertValueEqual(restored.getPersistentData().getInt("mite_squid_collisions_032"),4,"Real boat NBT keeps collision counter"); boat.tickCount=-boat.getId()*47+200; boat.tick();
        h.assertValueEqual(boat.getPersistentData().getInt("mite_squid_collisions_032"),3,"Actual tick200 offset decreases one recent hit"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void actualAnimalUnloadClearsGoalBindingsAndReloadInstallsFreshOnes(GameTestHelper h) {
        var original=mob(h,EntityType.OCELOT);original.getPersistentData().putInt(NativeAnimalSpecial032.TAME_COOLDOWN,123);CompoundTag saved=new CompoundTag();original.saveWithoutId(saved);
        h.assertTrue(NativeAnimalSpecial032.installed(original),"Real join records source goal binding");original.discard();
        h.assertFalse(NativeAnimalSpecial032.installed(original),"Real leave releases source goal binding and its animal-holding temptation value");
        var restored=EntityType.OCELOT.create(h.getLevel());restored.load(saved);h.getLevel().addFreshEntity(restored);
        h.assertTrue(NativeAnimalSpecial032.installed(restored)&&restored.getPersistentData().getInt(NativeAnimalSpecial032.TAME_COOLDOWN)==123,"Reload creates new source goal bindings while preserving authoritative saved cooldown");h.succeed();
    }
}
