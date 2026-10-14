package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.entity.R196Wolf;
import io.github.ningmeng123.mite.entity.animal.AnimalCommon032Goals;
import io.github.ningmeng123.mite.entity.animal.R196AnimalAi032;
import io.github.ningmeng123.mite.entity.animal.SourceMovement032Goal;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent source literals verify enabled goals, actual damage memory and persisted fear. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class AnimalCommon032GameTests {
    private static <T extends Mob> T mob(GameTestHelper h,EntityType<T> type,int x,int z) {
        T entity=type.create(h.getLevel()); BlockPos pos=h.absolutePos(new BlockPos(x,3,z));
        entity.moveTo(pos.getX()+.5D,pos.getY(),pos.getZ()+.5D); entity.setNoAi(true); entity.setNoGravity(true);
        h.getLevel().addFreshEntity(entity); return entity;
    }
    private static boolean has(Mob mob,Class<? extends Goal> type,int priority) {
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(g->type.isInstance(g.getGoal()) && g.getPriority()==priority);
    }
    @GameTest(template="ranged_027") public static void cowCommonGoalsMatchSourceDefaultPriorities(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2);
        h.assertTrue(has(cow,AnimalCommon032Goals.Float.class,0) && has(cow,AnimalCommon032Goals.Flee.class,1)
                && has(cow,AnimalCommon032Goals.FoodItems.class,1) && has(cow,AnimalCommon032Goals.AvoidFire.class,2)
                && has(cow,AnimalCommon032Goals.AvoidPredators.class,2) && has(cow,AnimalCommon032Goals.RainShelter.class,4)
                && has(cow,AnimalCommon032Goals.LeaveFluid.class,4),"rp/EntityLivestock/rr enabled source priorities installed through real join");
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceHellhoundHasLavaExitWithoutAnimalFoodOrFear(GameTestHelper h) {
        var type=MiteMobs.type(MobKind.HELLHOUND); var wolf=mob(h,type,2,2);
        long fluidGoals=wolf.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal() instanceof AnimalCommon032Goals.LeaveFluid).count();
        h.assertTrue(fluidGoals==2 && has(wolf,AnimalCommon032Goals.Float.class,1),"Hellhound constructor explicitly registers both fluid exits");
        h.assertTrue(has(wolf,io.github.ningmeng123.mite.entity.animal.SourcePetGoals032.Leap.class,2),"Hellhound explicitly registers source pw leap at2");
        h.assertTrue(wolf.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof AnimalCommon032Goals.Flee
                || g.getGoal() instanceof AnimalCommon032Goals.FoodItems || g.getGoal() instanceof AnimalCommon032Goals.AvoidFire),"Source clearing its inherited goals is respected"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void cowSourceInsertionOrderPutsWaterBeforeBreeding(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2);
        var order=cow.goalSelector.getAvailableGoals().stream().map(w->{var goal=w.getGoal();
            return goal instanceof io.github.ningmeng123.mite.entity.animal.LivestockSeeking032 seek?(seek.seeksWater()?"water":"food"):goal.getClass().getSimpleName();}).toList();
        h.assertValueEqual(order,List.of("FoodItems","AvoidFire","food","water","LivestockFreedomGoal032","AvoidPredators","RainShelter","LeaveFluid",
                "Float","BreedGoal","Tempt","FollowParentGoal","WaterAvoidingRandomStrollGoal","LookAtPlayerGoal","RandomLookAroundGoal","Flee"),
                "rp, EntityLivestock and rr constructors preserve actual insertion order, independent of priority sort"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void pigKeepsItsSourceConstructorPriorities(GameTestHelper h) {
        var pig=mob(h,EntityType.PIG,2,2);
        h.assertTrue(has(pig,net.minecraft.world.entity.ai.goal.BreedGoal.class,3) && has(pig,io.github.ningmeng123.mite.entity.animal.SourcePetGoals032.Tempt.class,4)
                && has(pig,net.minecraft.world.entity.ai.goal.FollowParentGoal.class,5) && has(pig,net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal.class,6)
                && has(pig,net.minecraft.world.entity.ai.goal.LookAtPlayerGoal.class,7) && has(pig,net.minecraft.world.entity.ai.goal.RandomLookAroundGoal.class,8),
                "ry constructor priorities 3/4/5/6/7/8"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void modernRabbitIsOutsideSourceAnimalGoalReplacement(GameTestHelper h) {
        var rabbit=mob(h,EntityType.RABBIT,2,2);
        h.assertTrue(!R196AnimalAi032.managed(rabbit) && rabbit.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof AnimalCommon032Goals.Marker),"No old identity: native rabbit goals remain native"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void actualCowHurtRemembersAttackerAndBreaksLove(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2),attacker=mob(h,EntityType.COW,3,2); cow.setInLove(null); cow.getRandom().setSeed(13);
        h.assertTrue(cow.hurt(cow.damageSources().mobAttack(attacker),1),"Real source NPC damage transaction");
        var state=R196AnimalAi032.state(cow);
        h.assertTrue(!cow.isInLove() && state.getBoolean("decided_to_flee") && R196AnimalAi032.lastAttacker(cow)==attacker,"Actual damage retains attacker and stops breeding");
        h.assertTrue(state.getInt("attacker_memory")>=900 && state.getInt("attacker_memory")<=1099,"of memory lasts 900 plus nextInt200"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void preEventZeroDamageDoesNotSpookCow(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2),attacker=mob(h,EntityType.COW,3,2); cow.setInLove(null);
        h.assertTrue(cow.isInLove(),"Cow love fixture is full-health and has source healthy needs"); float before=cow.getHealth();
        Consumer<LivingDamageEvent.Pre> listener=e->{if(e.getEntity()==cow)e.setNewDamage(0);}; NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Pre.class,listener);
        try { cow.hurt(cow.damageSources().mobAttack(attacker),1); } finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.assertTrue(cow.getHealth()==before,"Pre-zero health before="+before+" after="+cow.getHealth());
        h.assertTrue(cow.isInLove(),"Pre-zero must not break love");
        h.assertTrue(R196AnimalAi032.lastAttacker(cow)==null && !R196AnimalAi032.isSpookedOrFleeing(cow),"Pre-zero must not create fear: "+R196AnimalAi032.state(cow)); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceCowFleeStartHasStrictThirtyTwoBlockBoundary(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2),attacker=mob(h,EntityType.COW,3,2); R196AnimalAi032.rememberAttacker(cow,attacker);
        attacker.setPos(cow.getX()+32,cow.getY(),cow.getZ()); h.assertTrue(!R196AnimalAi032.considerFleeing(cow),"Livestock distance exactly32 rejects");
        attacker.setPos(cow.getX()+31.99D,cow.getY(),cow.getZ()); h.assertTrue(R196AnimalAi032.considerFleeing(cow),"Livestock just inside32 flees regardless of health"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceWolfRequiresBelowTwentyPercentHealthToFlee(GameTestHelper h) {
        Wolf wolf=mob(h,EntityType.WOLF,2,2); Cow attacker=mob(h,EntityType.COW,3,2); R196AnimalAi032.rememberAttacker(wolf,attacker);
        wolf.setHealth(wolf.getMaxHealth()*.2F); h.assertTrue(!R196AnimalAi032.considerFleeing(wolf),"of equality at .2 is not badly wounded");
        long seed=0; while(new Random(seed).nextInt(2)!=0) seed++;
        wolf.setHealth(wolf.getMaxHealth()*.19F); wolf.getRandom().setSeed(seed);
        h.assertTrue(R196AnimalAi032.considerFleeing(wolf),"Source below .2 passes independent one-half draw"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void peerWarningStaysWithinSourceSpecies(GameTestHelper h) {
        Cow source=mob(h,EntityType.COW,2,2),peer=mob(h,EntityType.COW,3,2),attacker=mob(h,EntityType.COW,5,2);
        var sheep=mob(h,EntityType.SHEEP,3,3); R196AnimalAi032.warnPeers(source,attacker);
        h.assertTrue(R196AnimalAi032.lastAttacker(peer)==attacker && R196AnimalAi032.state(peer).getBoolean("decided_to_flee"),"Same source concrete species warned in8x4x8");
        h.assertTrue(R196AnimalAi032.lastAttacker(sheep)==null,"Different livestock species does not receive direct attacker warning"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void fearMemoryPersistsAndExpiresWithoutReloadReset(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2),attacker=mob(h,EntityType.COW,3,2); R196AnimalAi032.rememberAttacker(cow,attacker); R196AnimalAi032.considerFleeing(cow);
        var state=R196AnimalAi032.state(cow); state.putInt("attacker_memory",7); state.putInt("panic_countdown",123);
        R196AnimalAi032.setSpooked(cow,h.getLevel().getGameTime()+550);
        CompoundTag saved=new CompoundTag(); cow.saveWithoutId(saved); Cow restored=EntityType.COW.create(h.getLevel()); restored.load(saved);
        h.assertTrue(R196AnimalAi032.state(restored).getInt("version")==1 && R196AnimalAi032.state(restored).getInt("panic_countdown")==0
                && R196AnimalAi032.lastAttacker(restored)==attacker && R196AnimalAi032.spooked(restored),"Actual ForgeData keeps source attacker UUID/fear timers; goal-local panic resets on load");
        for(int i=0;i<7;i++)R196AnimalAi032.tickSource(restored);
        h.assertTrue(R196AnimalAi032.lastAttacker(restored)==null && !R196AnimalAi032.state(restored).getBoolean("decided_to_flee"),"Saved remaining memory expires after7ticks, never rerolls"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourcePredatorPredicateDistinguishesWolvesAndOcelotTargetGoals(GameTestHelper h) {
        var chicken=mob(h,EntityType.CHICKEN,2,2); Wolf wolf=mob(h,EntityType.WOLF,3,2);
        h.assertTrue(R196AnimalAi032.potentialPredator(chicken,wolf),"sf adult wild wolf preysUpon animals");
        wolf.setTame(true,false); h.assertTrue(!R196AnimalAi032.potentialPredator(chicken,wolf),"Tame wolf is not potential predator");
        var cat=mob(h,EntityType.OCELOT,4,2); cat.setTarget(chicken);
        h.assertTrue(!R196AnimalAi032.potentialPredator(chicken,cat),"rx hunting goal does not invent missing source preysUpon override");
        var spider=mob(h,EntityType.SPIDER,4,3); h.assertTrue(R196AnimalAi032.potentialPredator(chicken,spider),"EntityArachnid explicitly preysUpon chicken");
        var fox=mob(h,EntityType.FOX,4,4); h.assertTrue(!R196AnimalAi032.potentialPredator(chicken,fox),"No source identity is not guessed from species"); fox.setTarget(chicken);
        h.assertTrue(R196AnimalAi032.potentialPredator(chicken,fox),"Modern adaptation respects an actual new predator target"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourcePredatorIncludesZombiesSilverfishAndGelatinousFamilies(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2); var hell=mob(h,MiteMobs.type(MobKind.HELLHOUND),4,4);
        var zombie=mob(h,EntityType.ZOMBIE,3,2); var silverfish=mob(h,EntityType.SILVERFISH,3,3);
        h.assertTrue(R196AnimalAi032.potentialPredator(cow,zombie) && R196AnimalAi032.potentialPredator(hell,zombie),"tw source accepts rp including hellhound");
        h.assertTrue(R196AnimalAi032.potentialPredator(cow,silverfish) && !R196AnimalAi032.potentialPredator(hell,silverfish),"tq source accepts only true animals");
        for(var kind:List.of(MobKind.GHOUL,MobKind.REVENANT,MobKind.JELLY,MobKind.BLOB,MobKind.OOZE,MobKind.PUDDING,MobKind.NETHERSPAWN,MobKind.COPPERSPINE,MobKind.HOARY_SILVERFISH))
            h.assertTrue(R196AnimalAi032.potentialPredator(cow,mob(h,MiteMobs.type(kind),5,5)),"Actual source preysUpon inheritance: "+kind);
        h.assertTrue(!R196AnimalAi032.potentialPredator(cow,mob(h,EntityType.SLIME,6,6)) && !R196AnimalAi032.potentialPredator(cow,mob(h,EntityType.MAGMA_CUBE,7,7)),"tl source attacksAnimals=false"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceWallCollisionLetsParalyzedAnimalFloat(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2); BlockPos origin=cow.blockPosition();
        for(int x=-1;x<=1;x++)for(int y=0;y<=2;y++)for(int z=-1;z<=1;z++)h.getLevel().setBlockAndUpdate(origin.offset(x,y,z),Blocks.WATER.defaultBlockState());
        cow.baseTick(); cow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,200,2)); cow.horizontalCollision=true;
        h.assertTrue(new AnimalCommon032Goals.Float(cow).canUse(),"pp first branch G=horizontalCollision precedes negative potion movement cutoff"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourcePanicDurationAndCompletedPanicFlagSurviveStop(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2); var goal=new AnimalCommon032Goals.Flee(cow,1.6F,.5F); cow.getRandom().setSeed(2); goal.startPanicking();
        h.assertValueEqual(R196AnimalAi032.state(cow).getInt("panic_countdown"),100+new Random(2).nextInt(100),"EntityAIFlee literal 100+nextInt100");
        goal.stop(); h.assertTrue(R196AnimalAi032.state(cow).getInt("panic_countdown")>=100,"Stopping movement does not reroll/reset source panic"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceMovementStuckThresholdAndWaterFallbackRestore(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2); cow.setPathfindingMalus(PathType.WATER,8F);
        var goal=new SourceMovement032Goal(cow,1F,true) {
            @Override public boolean canUse(){return true;}
            @Override protected Path movementPath(){return null;}
        };
        cow.getRandom().setSeed(3); goal.start();
        h.assertValueEqual(goal.stuckThreshold(),30+new Random(3).nextInt(20),"Source movement timeout30..49");
        h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER),0F,"Failed dry path temporarily permits source swim fallback");
        for(int i=0;i<50;i++)goal.tick(); h.assertValueEqual(goal.stationaryTicks(),50,"Movement tracks actual stationary x/z");
        goal.stop(); h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER),8F,"Stop restores prior water cost even with null path"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceFloatParalysisGateUsesSpeedMinusSlowAndSilverResistance(GameTestHelper h) {
        Cow cow=mob(h,EntityType.COW,2,2); BlockPos origin=cow.blockPosition();
        for(int x=-1;x<=1;x++)for(int y=0;y<=2;y++)for(int z=-1;z<=1;z++)h.getLevel().setBlockAndUpdate(origin.offset(x,y,z),Blocks.WATER.defaultBlockState());
        // update fluid flags through the actual entity base tick, without running its goals.
        cow.baseTick(); var goal=new AnimalCommon032Goals.Float(cow);
        h.assertTrue(goal.canUse(),"Full source water block permits swimming: inWater="+cow.isInWater()+", eyes="+cow.isEyeInFluid(net.minecraft.tags.FluidTags.WATER));
        cow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,200,2));
        h.assertTrue(!goal.canUse(),"Three source slow levels give -.6, below swimming cutoff -.5");
        cow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,200,1));
        h.assertTrue(goal.canUse(),"Two speed levels offset -.6 by+.4 and permit swimming"); h.succeed();
    }
}
