package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.*;
import io.github.ningmeng123.mite.registry.MiteMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent constructor/event, source arithmetic and live ranged attack integration. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class MonsterIntegrationGameTests {
    private static <T extends Mob> T spawn(GameTestHelper h,EntityType<T> type) {
        T mob=type.create(h.getLevel());BlockPos p=h.absolutePos(new BlockPos(1,10,1));
        mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);mob.setNoAi(true);mob.setNoGravity(true);h.getLevel().addFreshEntity(mob);return mob;
    }
    @GameTest(template="empty") public static void zombiesReceiveSourceDoorMiningAndThreeMeleeTasks(GameTestHelper h) {
        var zombie=spawn(h,EntityType.ZOMBIE);
        h.assertTrue(zombie.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof SourceBreakPortalGoal),"Source portal pounding is installed without a spawn chance");
        h.assertTrue(zombie.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof BreakDoorGoal),"Native 240-tick goal cannot race the source goal");
        h.assertValueEqual(zombie.goalSelector.getAvailableGoals().stream().filter(g -> g.getGoal() instanceof SourceMeleeGoal).count(),3L,"Player, villager and animal melee have their source priorities");
        h.assertTrue(R196DiggerGoal.first(zombie)!=null,"Source watcher mining remains independent");
        h.assertTrue(((GroundPathNavigation)zombie.getNavigation()).getNodeEvaluator().canOpenDoors(),"The actual closed-door navigation flag is enabled");
        zombie.discard();h.succeed();
    }
    @GameTest(template="empty") public static void oldAiPigZombiesAndModernHusksDoNotGainNewSourceTasks(GameTestHelper h) {
        for(var type:java.util.List.of(EntityType.ZOMBIFIED_PIGLIN,EntityType.HUSK,EntityType.DROWNED)) {
            var mob=spawn(h,type);h.assertTrue(!R196MonsterAi.managed(mob),"Only source-enabled goal families are installed");
            h.assertTrue(mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SourceBreakPortalGoal || g.getGoal() instanceof R196DiggerGoal),"No inherited-but-disabled watcher task leaks into the entity");mob.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void sourceSmartnessRollAndSaveAreStable(GameTestHelper h) {
        var zombie=spawn(h,EntityType.ZOMBIE);
        for(long seed=0;seed<32;seed++) { boolean expected=RandomSource.create(seed).nextInt(8)==0;zombie.getPersistentData().remove(R196MonsterAi.SMART);zombie.getRandom().setSeed(seed);h.assertValueEqual(R196MonsterAi.smart(zombie),expected,"Source initial intelligence uses exactly one of eight outcomes"); }
        zombie.getPersistentData().putBoolean(R196MonsterAi.SMART,true);CompoundTag tag=new CompoundTag();zombie.saveWithoutId(tag);
        var loaded=EntityType.ZOMBIE.create(h.getLevel());loaded.load(tag);h.assertTrue(R196MonsterAi.smart(loaded),"Intelligence survives real full NBT load");zombie.discard();h.succeed();
    }
    @GameTest(template="empty") public static void playerDamageTeachesButZeroDamageDoesNot(GameTestHelper h) {
        var zombie=spawn(h,EntityType.ZOMBIE);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);zombie.getPersistentData().putBoolean(R196MonsterAi.SMART,false);
        zombie.hurt(zombie.damageSources().playerAttack(player),0F);h.assertTrue(!R196MonsterAi.smart(zombie),"A zero-damage attempt does not teach intelligence");
        zombie.hurt(zombie.damageSources().playerAttack(player),1F);h.assertTrue(R196MonsterAi.smart(zombie),"Accepted player damage permanently enables smart digging");zombie.discard();h.succeed();
    }
    @GameTest(template="empty") public static void revenantsAreSmartAndCreepersLoseTheVanillaSwellGoal(GameTestHelper h) {
        var revenant=(Zombie)spawn(h,MiteMobs.type(MobKind.REVENANT));h.assertTrue(R196MonsterAi.smart(revenant),"Source revenant intelligence is unconditional");
        var creeper=spawn(h,EntityType.CREEPER);h.assertTrue(creeper.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SwellGoal),"Source swell completely replaces vanilla's distance trigger");revenant.discard();creeper.discard();h.succeed();
    }
    @GameTest(template="empty") public static void bloodMoonAndBoneLordShareOneHalfBaseDamageBonus(GameTestHelper h) {
        long time=h.getLevel().getDayTime();var skeleton=spawn(h,EntityType.SKELETON);var enderman=spawn(h,EntityType.ENDERMAN);
        try {
            h.getLevel().setDayTime(31L*24000L+18000L);R196MonsterAi.refreshFrenzyDamage(skeleton);
            double base=skeleton.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();h.assertValueEqual(skeleton.getAttributeValue(Attributes.ATTACK_DAMAGE),base*1.5D,"Blood moon adds half the unmodified attribute");
            double walk=skeleton.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();h.assertValueEqual(skeleton.getAttributeValue(Attributes.MOVEMENT_SPEED),walk*1.2D,"Source MoveHelper blood frenzy multiplies movement by 1.2");
            ((BoneLordFrenzy)skeleton).mite$setBoneLordFrenzy(null,20);h.assertValueEqual(skeleton.getAttributeValue(Attributes.ATTACK_DAMAGE),base*1.5D,"Bone-lord and blood-moon frenzy do not stack twice");
            h.assertValueEqual(skeleton.getAttributeValue(Attributes.MOVEMENT_SPEED),walk*1.2D,"Bone-lord and blood frenzy do not double the movement bonus");
            h.assertTrue(!R196MonsterAi.frenzied(enderman),"Source enderman overrides frenzy false");
            ((BoneLordFrenzy)skeleton).mite$setBoneLordFrenzy(null,0);h.getLevel().setDayTime(31L*24000L+6000L);R196MonsterAi.refreshFrenzyDamage(skeleton);
            h.assertValueEqual(skeleton.getAttributeValue(Attributes.ATTACK_DAMAGE),base,"Daylight removes the transient blood bonus");
            h.assertValueEqual(skeleton.getAttributeValue(Attributes.MOVEMENT_SPEED),walk,"Daylight removes transient frenzy speed");
            h.getLevel().setDayTime(127L*24000L+18000L);h.assertTrue(!R196MonsterAi.frenzied(skeleton),"Blue-moon exception is not a blood frenzy");
        } finally { h.getLevel().setDayTime(time);skeleton.discard();enderman.discard(); }h.succeed();
    }
    @GameTest(template="empty") public static void sourceRangedUsesThirtyBlockRadiusAndFixedSixtyRawTicks(GameTestHelper h) {
        Skeleton skeleton=spawn(h,EntityType.SKELETON);skeleton.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
        var target=spawn(h,EntityType.COW);target.setPos(skeleton.getX()+20,skeleton.getY(),skeleton.getZ());skeleton.setTarget(target);
        SourceRangedAttackGoal goal=new SourceRangedAttackGoal(skeleton,1D,60,15F);
        h.assertValueEqual(goal.rangeSquared(),900D,"Source nominal 15 range is squared then multiplied by four");h.assertTrue(goal.canUse(),"A distant current target can enter ranged AI");
        for(int tick=0;tick<60;tick++) goal.tick();
        h.assertValueEqual(h.getLevel().getEntitiesOfClass(AbstractArrow.class,skeleton.getBoundingBox().inflate(32),a -> a.getOwner()==skeleton).size(),0,"Initial fixed cooldown has not fired early");
        goal.tick();h.assertValueEqual(h.getLevel().getEntitiesOfClass(AbstractArrow.class,skeleton.getBoundingBox().inflate(32),a -> a.getOwner()==skeleton).size(),1,"The actual source arrow launches after sixty countdown ticks");
        h.getLevel().getEntitiesOfClass(AbstractArrow.class,skeleton.getBoundingBox().inflate(32),a -> a.getOwner()==skeleton).forEach(a -> a.discard());skeleton.discard();target.discard();h.succeed();
    }
    @GameTest(template="empty") public static void rangedFrenzyUsesFortyTicksAndStopsAtSolidWalls(GameTestHelper h) {
        long time=h.getLevel().getDayTime();var skeleton=spawn(h,EntityType.SKELETON);var target=spawn(h,EntityType.COW);target.setPos(skeleton.getX()+3,skeleton.getY(),skeleton.getZ());
        try {h.getLevel().setDayTime(31L*24000L+18000L);var goal=new SourceRangedAttackGoal(skeleton,1,60,15);
            h.assertValueEqual(goal.attackInterval(),40,"Source int(60*.67f) is forty raw ticks");
            BlockPos wall=skeleton.blockPosition().east();h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(wall.above(),Blocks.STONE.defaultBlockState());
            h.assertTrue(!SourceRangedAttackGoal.clearShot(skeleton,target),"Both eye and quarter-body projectile rays are blocked by solid stone");
            h.getLevel().setBlockAndUpdate(wall,Blocks.IRON_BARS.defaultBlockState());h.getLevel().setBlockAndUpdate(wall.above(),Blocks.IRON_BARS.defaultBlockState());
            h.assertTrue(SourceRangedAttackGoal.clearShot(skeleton,target),"Piercing-projectile policies ignore bars");
            h.getLevel().setBlockAndUpdate(wall,Blocks.GLASS_PANE.defaultBlockState());h.getLevel().setBlockAndUpdate(wall.above(),Blocks.GLASS_PANE.defaultBlockState());
            h.assertTrue(!SourceRangedAttackGoal.clearShot(skeleton,target),"Modern glass pane's IronBarsBlock inheritance cannot grant metal-bar projectile policy");
            h.getLevel().setBlockAndUpdate(wall,Blocks.OAK_LEAVES.defaultBlockState());h.getLevel().setBlockAndUpdate(wall.above(),Blocks.OAK_LEAVES.defaultBlockState());
            skeleton.getRandom().setSeed(196035L);var random=RandomSource.create(196035L);
            for(int i=0;i<20;i++) h.assertTrue(SourceRangedAttackGoal.clearShot(skeleton,target),"Null-originator projectile prediction always ignores leaves");
            h.assertValueEqual(skeleton.getRandom().nextInt(),random.nextInt(),"Source ray does not consume a projectile obstruction random draw");
        } finally {h.getLevel().setDayTime(time);skeleton.discard();target.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void closedWoodenGatesArePlannedButMetalDoorsAreNot(GameTestHelper h) {
        var zombie=spawn(h,EntityType.ZOMBIE);BlockPos p=zombie.blockPosition().east();
        var nav=(GroundPathNavigation)zombie.getNavigation();var eval=nav.getNodeEvaluator();
        var region=new net.minecraft.world.level.PathNavigationRegion(h.getLevel(),p.offset(-4,-2,-4),p.offset(4,3,4));
        eval.prepare(region,zombie);
        try {
            h.getLevel().setBlockAndUpdate(p,Blocks.OAK_FENCE_GATE.defaultBlockState());
            h.assertTrue(eval.getPathType(new PathfindingContext(region,zombie),p.getX(),p.getY(),p.getZ())==PathType.WALKABLE_DOOR,"Source flag makes the closed wooden gate a portal route");
            h.getLevel().setBlockAndUpdate(p,Blocks.IRON_DOOR.defaultBlockState());
            h.assertTrue(eval.getPathType(new PathfindingContext(region,zombie),p.getX(),p.getY(),p.getZ())!=PathType.WALKABLE_DOOR,"Metal door remains a blocked route");
        } finally {eval.done();zombie.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void witherSkeletonWeaponChangesKeepSourceTasks(GameTestHelper h) {
        var skeleton=spawn(h,EntityType.WITHER_SKELETON);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));skeleton.reassessWeaponGoal();
        h.assertValueEqual(skeleton.goalSelector.getAvailableGoals().stream().filter(g -> g.getGoal() instanceof SourceRangedAttackGoal).count(),1L,"Original wither-skeleton maps to source tr bow AI on reassessment");
        skeleton.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_SWORD));skeleton.reassessWeaponGoal();
        h.assertValueEqual(skeleton.goalSelector.getAvailableGoals().stream().filter(g -> g.getGoal() instanceof SourceMeleeGoal).count(),1L,"Switching back installs exactly one source melee goal");
        h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SourceRangedAttackGoal),"Bow goal is removed on melee switch");
        var stray=spawn(h,EntityType.STRAY);stray.reassessWeaponGoal();
        h.assertTrue(stray.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SourceMeleeGoal || g.getGoal() instanceof SourceRangedAttackGoal),"Modern stray keeps its independently scoped behavior");
        skeleton.discard();stray.discard();h.succeed();
    }
}
