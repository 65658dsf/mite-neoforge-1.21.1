package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Elemental;
import io.github.ningmeng123.mite.entity.R196Infestation;
import io.github.ningmeng123.mite.entity.R196Silverfish;
import io.github.ningmeng123.mite.registry.MiteMobBlocks;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteOreBlocks;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native snowball impacts, actual drop causes, persisted copper veins and host mapping remain source-correct. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class InfestationSnowballGameTests {
    private static final class ImpactSnowball extends Snowball {
        ImpactSnowball(ServerLevel level,LivingEntity owner) { super(level,owner); }
        void impact(Entity entity) { super.onHitEntity(new EntityHitResult(entity)); }
    }
    private static Player player(GameTestHelper h) { Player p=h.makeMockPlayer(GameType.SURVIVAL); p.moveTo(h.absolutePos(new BlockPos(2,3,2)).getCenter()); return p; }
    private static Mob mob(GameTestHelper h,MobKind kind) { Mob e=MiteMobs.type(kind).create(h.getLevel()); BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); e.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); e.setNoAi(true); e.setNoGravity(true); h.getLevel().addFreshEntity(e); return e; }
    private static int countFish(GameTestHelper h,BlockPos pos) { return h.getLevel().getEntitiesOfClass(Silverfish.class,new net.minecraft.world.phys.AABB(pos).inflate(2)).size(); }
    @GameTest(template="empty") public static void ordinarySnowballDoesOneDamage(GameTestHelper h) { Cow target=EntityType.COW.create(h.getLevel()); target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.setHealth(100); new ImpactSnowball(h.getLevel(),player(h)).impact(target); h.assertTrue(target.getHealth()==99,"Source general snowball damage1, not modern0"); h.succeed(); }
    @GameTest(template="empty") public static void fireElementalSnowballDoesThreeDamage(GameTestHelper h) { var target=mob(h,MobKind.FIRE_ELEMENTAL); float before=target.getHealth(); new ImpactSnowball(h.getLevel(),player(h)).impact(target); h.assertTrue(target.getHealth()==before-3,"Native source fire snowball3"); h.succeed(); }
    @GameTest(template="empty") public static void netherspawnSnowballDoesTwoDamage(GameTestHelper h) { var target=mob(h,MobKind.NETHERSPAWN); float before=target.getHealth(); new ImpactSnowball(h.getLevel(),player(h)).impact(target); h.assertTrue(target.getHealth()==before-2,"Native source netherspawn snowball2"); h.succeed(); }
    @GameTest(template="empty") public static void snowballImpactQuenchesEarth(GameTestHelper h) { R196Elemental earth=(R196Elemental)mob(h,MobKind.EARTH_ELEMENTAL); earth.convertToMagma(); new ImpactSnowball(h.getLevel(),player(h)).impact(earth); h.assertTrue(!earth.isMagma() && earth.heat()==0,"Actual onHitEntity quenches independently of physical immune result"); h.succeed(); }
    @GameTest(template="empty") public static void playerSnowballStillCannotDamagePudding(GameTestHelper h) { var target=mob(h,MobKind.PUDDING); float before=target.getHealth(); new ImpactSnowball(h.getLevel(),player(h)).impact(target); h.assertTrue(target.getHealth()==before,"New source1 general projectile amount retains pudding player-snowball capability gate"); h.succeed(); }
    @GameTest(template="empty") public static void copperspineDoesNotOverrideVanillaHostMap(GameTestHelper h) { h.assertTrue(InfestedBlock.infestedStateByHost(Blocks.STONE.defaultBlockState()).is(Blocks.INFESTED_STONE),"Registering subtype4 cannot replace global native stone host"); h.assertTrue(MiteMobBlocks.COPPERSPINE_STONE.get().getHostBlock()==Blocks.STONE,"Source host remains stone"); h.succeed(); }
    @GameTest(template="empty") public static void infestationNetherspawnSpecies(GameTestHelper h) { BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); var fish=R196Infestation.spawn(h.getLevel(),pos,MiteOreBlocks.INFESTED_NETHERRACK.get().defaultBlockState(),null); h.assertTrue(fish instanceof R196Silverfish r && r.miteKind()==MobKind.NETHERSPAWN,"Metadata3 actual inhabitant replaces prior modern silverfish adaptation"); h.succeed(); }
    @GameTest(template="empty") public static void playerBreakRetainsAggroTarget(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); Player p=player(h);
        var state=MiteMobBlocks.COPPERSPINE_STONE.get().defaultBlockState();
        // Observe this action's synchronous entity additions: nearby old fish and not-yet-visible chunk sections are unrelated.
        try (var spawned=new SpawnedEntityCapture(h.getLevel(),entity->entity instanceof R196Silverfish)) {
            CommonHooks.handleBlockDrops(h.getLevel(),pos,state,null,new java.util.ArrayList<>(),p,ItemStack.EMPTY);
            var fish=spawned.live(R196Silverfish.class);
            h.assertValueEqual(fish.size(),1,"The actual final player-drop hook releases exactly one new source fish");
            h.assertTrue(fish.get(0).miteKind()==MobKind.COPPERSPINE,"The new inhabitant retains subtype4");
            h.assertTrue(fish.get(0).getTarget()==p,"The new inhabitant targets the exact responsible player");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void explosionDropDoesNotReleaseFish(GameTestHelper h) { BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); int before=countFish(h,pos); CommonHooks.handleBlockDrops(h.getLevel(),pos,Blocks.INFESTED_STONE.defaultBlockState(),null,new java.util.ArrayList<>(),null,ItemStack.EMPTY); h.assertTrue(countFish(h,pos)==before,"Source drop cause without responsible player or fish cannot release inhabitants"); h.succeed(); }
    @GameTest(template="empty") public static void copperUpgradesOnlyConnectedInfestedVein(GameTestHelper h) {
        BlockPos start=h.absolutePos(new BlockPos(2,3,2)); h.getLevel().setBlockAndUpdate(start,Blocks.INFESTED_STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(start.east(),Blocks.INFESTED_STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(start.east(2),Blocks.INFESTED_STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(start.north().east(),Blocks.INFESTED_COBBLESTONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(start.north(2),Blocks.INFESTED_STONE.defaultBlockState());
        R196Infestation.upgradeVein(h.getLevel(),start);
        h.assertTrue(h.getLevel().getBlockState(start.east(2)).is(MiteMobBlocks.COPPERSPINE_STONE.get()),"Six-face same-metadata connectivity recursively upgrades vein");
        h.assertTrue(h.getLevel().getBlockState(start.north(2)).is(Blocks.INFESTED_STONE),"Disconnected stone stays original");
        h.assertTrue(h.getLevel().getBlockState(start.north().east()).is(Blocks.INFESTED_COBBLESTONE),"Other original metadata never bridges stone vein"); h.succeed();
    }
    @GameTest(template="empty") public static void copperSpawnUpgradesBrokenSeedsNeighbors(GameTestHelper h) { BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); h.getLevel().setBlockAndUpdate(pos.east(),Blocks.INFESTED_STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(pos.west(2),Blocks.COPPER_ORE.defaultBlockState()); h.getLevel().removeBlock(pos,false); var fish=R196Infestation.spawn(h.getLevel(),pos,Blocks.INFESTED_STONE.defaultBlockState(),null); h.assertTrue(fish instanceof R196Silverfish r && r.miteKind()==MobKind.COPPERSPINE,"Old removed block identity still selects copper"); h.assertTrue(h.getLevel().getBlockState(pos.east()).is(MiteMobBlocks.COPPERSPINE_STONE.get()),"Modern post-break order cannot lose adjacency upgrade"); h.succeed(); }
    @GameTest(template="empty") public static void copperIdentityPersistsAfterOreRemoval(GameTestHelper h) { BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); h.getLevel().setBlockAndUpdate(pos,Blocks.INFESTED_STONE.defaultBlockState()); R196Infestation.upgradeVein(h.getLevel(),pos); var state=h.getLevel().getBlockState(pos); var fish=R196Silverfish.createInfestedChild(h.getLevel(),pos,state); h.assertTrue(fish instanceof R196Silverfish r && r.miteKind()==MobKind.COPPERSPINE,"Persisted subtype4 does not need ore re-detection"); h.succeed(); }
    @GameTest(template="empty") public static void sourceInfestedHardnessIsFinalRegistrationValue(GameTestHelper h) { BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); for (var block:java.util.List.of(Blocks.INFESTED_STONE,Blocks.INFESTED_COBBLESTONE,Blocks.INFESTED_DEEPSLATE,MiteMobBlocks.COPPERSPINE_STONE.get())) h.assertTrue(block.defaultBlockState().getDestroySpeed(h.getLevel(),pos)==.75F,"aqz final registered infested hardness .75, not ctor0 or modernhost/2"); h.succeed(); }
    @GameTest(template="empty") public static void severeFallMarksElementalAndSuppressesMaterialDrop(GameTestHelper h) throws Exception {
        R196Elemental e=(R196Elemental)mob(h,MobKind.EARTH_ELEMENTAL); e.hurt(e.damageSources().fall(),15);
        h.assertTrue(e.getPersistentData().getBoolean("mite_massive_fall_031"),"Source massive fall at half max health");
        Method drop=R196Elemental.class.getDeclaredMethod("dropCustomDeathLoot",ServerLevel.class,DamageSource.class,boolean.class); drop.setAccessible(true);
        int before=h.getLevel().getEntitiesOfClass(ItemEntity.class,e.getBoundingBox().inflate(2),i->i.getItem().is(Items.COBBLESTONE)).size();
        e.getRandom().setSeed(0); h.assertTrue(new java.util.Random(0).nextFloat()>=.1F,"Independent R196 random fixture lies in suppression90%");
        drop.invoke(e,h.getLevel(),e.damageSources().generic(),true);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,e.getBoundingBox().inflate(2),i->i.getItem().is(Items.COBBLESTONE)).size()==before,"Source common massive fall suppresses body drop90%"); h.succeed();
    }
}
