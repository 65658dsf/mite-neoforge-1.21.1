package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.survival.DeathExperienceOrbs;
import io.github.ningmeng123.mite.world.ExperienceOrbPhysics;
import io.github.ningmeng123.mite.world.SourceExplosionBlocks;
import io.github.ningmeng123.mite.world.SourceExplosionItems;
import io.github.ningmeng123.mite.world.SourceFluidFall;
import io.github.ningmeng123.mite.world.TemporaryFluidChanges;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.github.ningmeng123.mite.worldgen.UnderworldSpringGeneration;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native explosion/fluid/orb entry points, plus independent save and generation boundary checks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SourceWorldPhysicsGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualBlastKeepsMetalAndRedstoneButShattersWholeGems(GameTestHelper h) {
        BlockPos origin=h.absolutePos(BlockPos.ZERO);
        ChunkPos chunk=new ChunkPos(origin);
        // Keep the entire native radius*2+1 query inside one visible chunk, above neighboring test cleanup boxes.
        Vec3 center=new Vec3(chunk.getMinBlockX()+8.5,origin.getY()+80.5,chunk.getMinBlockZ()+8.5);
        AABB affected=new AABB(Mth.floor(center.x-7),Mth.floor(center.y-7),Mth.floor(center.z-7),
                Mth.floor(center.x+7),Mth.floor(center.y+7),Mth.floor(center.z+7));
        boolean forcedBefore=h.getLevel().getForcedChunks().contains(chunk.toLong());
        h.getLevel().setChunkForced(chunk.x,chunk.z,true);
        h.getLevel().getChunkAt(BlockPos.containing(center));
        h.runAfterDelay(10,()->{
            try (SpawnedEntityCapture spawned=new SpawnedEntityCapture(h.getLevel(),
                    e->e instanceof ItemEntity&&e.getBoundingBox().intersects(affected))) {
                h.assertTrue(h.getLevel().isPositionEntityTicking(BlockPos.containing(center)),
                        "Native blast fixture chunk reaches entity-ticking visibility before adding items");
                ItemEntity metal=item(h,Items.IRON_INGOT,3,center.add(1,0,0));
                ItemEntity redstone=item(h,Items.REDSTONE,5,center.add(0,0,1));
                ItemEntity diamond=item(h,Items.DIAMOND,2,center.add(-1,0,0));
                ItemEntity emerald=item(h,Items.EMERALD,3,center.add(0,0,-1));
                ItemEntity flint=item(h,Items.FLINT,4,center.add(1,0,1));
                ItemEntity string=item(h,Items.STRING,3,center.add(-1,0,-1));
                List<Entity> nativeQuery=h.getLevel().getEntities((Entity)null,affected);
                for (ItemEntity original:List.of(metal,redstone,diamond,emerald,flint,string)) {
                    h.assertTrue(nativeQuery.contains(original),"Native explosion affected-entity query includes "
                            +original.getItem().getItem()+" id="+original.getId()+" at "+original.position());
                }
                h.getLevel().explode(null,center.x,center.y,center.z,3F,Level.ExplosionInteraction.NONE);
                h.assertFalse(metal.isRemoved(),"Actual native detonation preserves minecraft:iron_ingot");
                h.assertFalse(redstone.isRemoved(),"Actual native detonation preserves minecraft:redstone");
                h.assertValueEqual(metal.getItem().getCount(),3,"Metal survival does not spend the stack");
                h.assertTrue(metal.getDeltaMovement().lengthSqr()>0,"Even immune drops receive source explosion motion");
                for (ItemEntity original:List.of(diamond,emerald,flint,string)) {
                    h.assertTrue(original.isRemoved(),"Actual native detonation consumes nonimmune original "
                            +original.getItem().getItem()+" id="+original.getId()+" at "+original.position());
                }
                List<ItemEntity> products=h.getLevel().getEntitiesOfClass(ItemEntity.class,affected);
                h.assertValueEqual(products.stream().filter(e->e.getItem().is(MiteShards.DIAMOND_SHARD.get())).mapToInt(e->e.getItem().getCount()).sum(),2,
                        "Each whole diamond becomes one shard in the native world query");
                h.assertValueEqual(products.stream().filter(e->e.getItem().is(MiteShards.EMERALD_SHARD.get())).mapToInt(e->e.getItem().getCount()).sum(),3,
                        "Each whole emerald becomes one shard in the native world query");
                h.assertValueEqual(products.stream().filter(e->e.getItem().is(MiteItems.FLINT_CHIP.get())).mapToInt(e->e.getItem().getCount()).sum(),4,
                        "Each whole flint becomes one chip in the native world query");
            } finally {
                if (!forcedBefore) h.getLevel().setChunkForced(chunk.x,chunk.z,false);
            }
            h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void droppedEquipmentSpendsDurabilityWhileWeakForceLeavesOrdinaryItems(GameTestHelper h) {
        Vec3 center=point(h,1.5,3,2.5);
        ItemEntity tool=item(h,Items.IRON_SWORD,1,center.add(4,0,0));
        ItemEntity string=item(h,Items.STRING,2,center.add(4,0,0));
        Explosion blast=blast(h,center,2F,Explosion.BlockInteraction.KEEP);
        try {
            h.assertFalse(SourceExplosionItems.immune(tool.getItem()),"A metal tool is not an immune loose ingot");
            SourceExplosionItems.handle(tool,blast);
            h.assertFalse(tool.isRemoved(),"The source 1,600 wear does not erase the full iron sword");
            h.assertValueEqual(tool.getItem().getDamageValue(),1600,"2/(16^.75)*64*100 source tool wear");
            SourceExplosionItems.handle(string,blast);
            h.assertTrue(string.isRemoved(),"Force .25 is above the source ordinary-item .2 boundary");
            ItemEntity weak=item(h,Items.STRING,2,center.add(4,0,0));
            SourceExplosionItems.handle(weak,blast(h,center,1F,Explosion.BlockInteraction.KEEP));
            h.assertFalse(weak.isRemoved(),"Force .125 leaves nondamageable ordinary items intact");
        } finally { clearDrops(h); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void metalFamiliesAndCarriedBlocksFollowIndependentResistance(GameTestHelper h) {
        Vec3 center=point(h,1.5,3,2.5);
        try {
            for (Item material:List.of(Items.COPPER_INGOT,Items.GOLD_NUGGET,MiteMaterials.ADAMANTIUM_INGOT.get(),MiteMaterials.MITHRIL_NUGGET.get())) {
                ItemEntity entity=item(h,material,2,center.add(1,0,0));
                SourceExplosionItems.handle(entity,blast(h,center,4F,Explosion.BlockInteraction.KEEP));
                h.assertFalse(entity.isRemoved(),"Explicit source metal identity survives: "+material);
            }
            ItemEntity stone=item(h,Items.STONE,2,center.add(4,0,0));
            SourceExplosionItems.handle(stone,blast(h,center,1F,Explosion.BlockInteraction.KEEP));
            h.assertFalse(stone.isRemoved(),"Carried stone withstands force*2=.25 below source stone resistance2.4");
        } finally { clearDrops(h); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void preBlastClearsSoftBlocksAndConvertsMyceliumWithoutExcavatingStone(GameTestHelper h) {
        BlockPos soft=pos(h,2,3,2),soil=pos(h,3,2,2),wall=pos(h,4,3,2);
        h.getLevel().setBlock(soft.below(),Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(soft,Blocks.SHORT_GRASS.defaultBlockState(),3);
        h.getLevel().setBlock(soil.below(),Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(soil,Blocks.MYCELIUM.defaultBlockState(),3);
        h.getLevel().setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        SourceExplosionBlocks.prepare(h.getLevel(),blast(h,Vec3.atCenterOf(soft),3F,Explosion.BlockInteraction.DESTROY));
        h.assertTrue(h.getLevel().getBlockState(soft).isAir(),"The actual preliminary rays clear zero/low resistance plants");
        h.assertTrue(h.getLevel().getBlockState(soil).is(Blocks.DIRT),"A downward preliminary ray strips mycelium to supported dirt");
        h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"The preliminary pass stops at source resistance above .1");
        h.assertValueEqual(blast(h,Vec3.atCenterOf(soft),3F,Explosion.BlockInteraction.DESTROY).radius(),3F,"The modern main blast power is unchanged");
        clearDrops(h);h.succeed();
    }

    @GameTest(template="empty")
    public static void preBlastHonorsKeepAndNaturalTrialDropProvenance(GameTestHelper h) {
        BlockPos carpet=pos(h,2,3,2),natural=pos(h,4,3,2);
        h.getLevel().setBlock(carpet.below(),Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(natural.below(),Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(carpet,Blocks.WHITE_CARPET.defaultBlockState(),3);
        h.getLevel().setBlock(natural,Blocks.WHITE_CARPET.defaultBlockState(),3);
        NaturalTrialBlocks.record(h.getLevel().getChunkAt(natural),natural);
        try {
            SourceExplosionBlocks.prepare(h.getLevel(),blast(h,Vec3.atCenterOf(carpet),3F,Explosion.BlockInteraction.KEEP));
            h.assertTrue(h.getLevel().getBlockState(carpet).is(Blocks.WHITE_CARPET),"No-block-damage explosion does not run the preliminary destruction");
            SourceExplosionBlocks.destroy(h.getLevel(),carpet,h.getLevel().getBlockState(carpet),blast(h,Vec3.atCenterOf(carpet),3F,Explosion.BlockInteraction.DESTROY));
            SourceExplosionBlocks.destroy(h.getLevel(),natural,h.getLevel().getBlockState(natural),blast(h,Vec3.atCenterOf(natural),3F,Explosion.BlockInteraction.DESTROY));
            h.assertTrue(h.getLevel().getBlockState(carpet).isAir()&&h.getLevel().getBlockState(natural).isAir(),"Both actual block transactions run");
            h.assertValueEqual(total(h,Items.WHITE_CARPET),1,"Only the ordinary carpet drops; natural trial provenance survives the prepass");
        } finally { clearDrops(h); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void springRequiresSourceThreeStoneOneAirCavity(GameTestHelper h) {
        BlockPos p=pos(h,3,3,3);
        h.getLevel().setBlock(p.above(),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        for (Direction d:Direction.Plane.HORIZONTAL) h.getLevel().setBlock(p.relative(d),Blocks.STONE.defaultBlockState(),2);
        h.assertFalse(UnderworldSpringGeneration.generate(h.getLevel(),p,true),"Four stone sides provide no cave opening");
        h.getLevel().setBlock(p.east(),Blocks.AIR.defaultBlockState(),2);
        h.assertTrue(UnderworldSpringGeneration.generate(h.getLevel(),p,true),"Exactly three stone sides and one air side place water");
        h.assertTrue(h.getLevel().getFluidState(p).isSource(),"Natural spring is a persistent source");
        h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(UnderworldSpringGeneration.generate(h.getLevel(),p,false),"The same cavity supports the selected lava variant");
        h.assertTrue(h.getLevel().getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA),"Lava selection reaches the actual world");
        h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),2);h.succeed();
    }

    @GameTest(template="empty")
    public static void naturalMyceliumFlagPersistsAndSuppressesAllSeventyCandidates(GameTestHelper h) {
        BlockPos p=pos(h,3,3,3);var chunk=h.getLevel().getChunkAt(p);
        Boolean before=chunk.getExistingDataOrNull(UnderworldSpringGeneration.NATURAL_MYCELIUM.get());
        try {
            UnderworldSpringGeneration.markMycelium(h.getLevel(),p);
            h.assertTrue(UnderworldSpringGeneration.neighboringMycelium(h.getLevel(),p.getX()>>4,p.getZ()>>4),"Natural-mycelium origin is visible to the neighboring decorator");
            h.assertValueEqual(UnderworldSpringGeneration.decorate(h.getLevel(),p.getX()>>4,p.getZ()>>4,196),0,"The marked neighbor suppresses the entire source spring loop");
            h.assertTrue(chunk.getData(UnderworldSpringGeneration.NATURAL_MYCELIUM.get()),"The persistent chunk attachment records natural origin");
            var saved=net.minecraft.world.level.chunk.storage.ChunkSerializer.write(h.getLevel(),chunk);
            var loaded=net.minecraft.world.level.chunk.storage.ChunkSerializer.read(h.getLevel(),h.getLevel().getPoiManager(),
                    new net.minecraft.world.level.chunk.storage.RegionStorageInfo("mite-spring-test",h.getLevel().dimension(),"chunk"),chunk.getPos(),saved);
            h.assertTrue(loaded.getData(UnderworldSpringGeneration.NATURAL_MYCELIUM.get()),"Native chunk save/read retains the natural mycelium exclusion flag");
        } finally {
            if (before==null) chunk.removeData(UnderworldSpringGeneration.NATURAL_MYCELIUM.get());
            else chunk.setData(UnderworldSpringGeneration.NATURAL_MYCELIUM.get(),before);
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void scheduledNativeFluidTickMovesTheWholeCellAndPersistsItsDecay(GameTestHelper h) {
        BlockPos p=pos(h,3,4,3);ServerPlayer player=player(h,point(h,3.5,2,3.5));
        try {
            h.getLevel().setBlock(p,Blocks.WATER.defaultBlockState(),2);
            h.getLevel().setBlock(p.below(),Blocks.AIR.defaultBlockState(),2);
            h.getLevel().setBlock(p.below(2),Blocks.STONE.defaultBlockState(),2);
            Fluids.WATER.tick(h.getLevel(),p,h.getLevel().getFluidState(p));
            h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Real native tick vacates the original cell rather than leaving a modern waterfall source");
            h.assertTrue(h.getLevel().getFluidState(p.below()).isSource(),"The entire liquid cell moves down one block");
            var saved=TemporaryFluidChanges.get(h.getLevel()).save(new CompoundTag(),h.getLevel().registryAccess());
            var restored=TemporaryFluidChanges.load(saved,h.getLevel().registryAccess());
            h.assertValueEqual(restored.pending().get(p.below().asLong()).due(),h.getLevel().getGameTime()+8,"Water's deferred flowing conversion survives a SavedData roundtrip");
        } finally {
            TemporaryFluidChanges.get(h.getLevel()).cancel(p.below());h.getLevel().setBlock(p.below(),Blocks.AIR.defaultBlockState(),2);
            removePlayer(h,player);
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void neighboringSourceAndOverheadLiquidVetoWholeCellFalling(GameTestHelper h) {
        BlockPos p=pos(h,3,4,3);ServerPlayer player=player(h,point(h,3.5,2,3.5));
        try {
            h.getLevel().setBlock(p,Blocks.WATER.defaultBlockState(),2);h.getLevel().setBlock(p.below(),Blocks.AIR.defaultBlockState(),2);
            h.getLevel().setBlock(p.east(),Blocks.WATER.defaultBlockState(),2);
            h.assertFalse(SourceFluidFall.tryFall(h.getLevel(),p,h.getLevel().getFluidState(p)),"A stationary source peer holds the liquid cell in place");
            h.getLevel().setBlock(p.east(),Blocks.AIR.defaultBlockState(),2);h.getLevel().setBlock(p.above(),Blocks.WATER.defaultBlockState(),2);
            h.assertFalse(SourceFluidFall.tryFall(h.getLevel(),p,h.getLevel().getFluidState(p)),"A continuous liquid column does not freefall");
            h.getLevel().setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);
            player.moveTo(point(h,40,2,40));
            h.assertFalse(SourceFluidFall.tryFall(h.getLevel(),p,h.getLevel().getFluidState(p)),"No nearby player means ordinary fluid logic remains responsible");
        } finally { h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),2);h.getLevel().setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);removePlayer(h,player); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void deferredFluidConversionHonorsTimeIdentityAndUnloadedChunks(GameTestHelper h) {
        BlockPos p=pos(h,3,4,3),unloaded=new BlockPos(29_000_000,80,29_000_000);
        TemporaryFluidChanges data=new TemporaryFluidChanges();long now=h.getLevel().getGameTime();
        h.getLevel().setBlock(p,Blocks.WATER.defaultBlockState(),2);
        data.schedule(p,true,now+1);data.tick(h.getLevel());
        h.assertTrue(h.getLevel().getFluidState(p).isSource(),"The tick before the absolute deadline cannot consume the source");
        data.schedule(p,true,now);data.schedule(unloaded,false,now);
        data=TemporaryFluidChanges.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        data.tick(h.getLevel());
        h.assertFalse(h.getLevel().getFluidState(p).isSource(),"The deadline converts to source legacy level1, without changing world time");
        h.assertValueEqual(h.getLevel().getFluidState(p).getAmount(),7,"Legacy level1 corresponds to modern amount7");
        h.assertTrue(data.pending().containsKey(unloaded.asLong()),"An unloaded destination remains pending without force-loading its chunk");
        h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);data.schedule(p,true,now);data.tick(h.getLevel());
        h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE),"A stale deferred change cannot overwrite a replacement solid block");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void orbPickupUsesThreePhysicalRaysBeforeSpendingXpOrCooldown(GameTestHelper h) {
        ServerPlayer player=player(h,point(h,6.5,2,2.5));
        ExperienceOrb orb=new ExperienceOrb(h.getLevel(),point(h,1.5,2.5,2.5).x,point(h,1.5,2.5,2.5).y,point(h,1.5,2.5,2.5).z,7);
        try {
            for (int y=1;y<=4;y++) h.getLevel().setBlock(pos(h,5,y,2),Blocks.STONE.defaultBlockState(),3);
            int xp=player.totalExperience;player.takeXpDelay=0;
            h.assertFalse(ExperienceOrbPhysics.canReach(orb,player),"All three target height rays are blocked by stone");
            orb.playerTouch(player);
            h.assertValueEqual(player.totalExperience,xp,"A blocked native pickup does not award experience");
            h.assertValueEqual(player.takeXpDelay,0,"A blocked native pickup does not spend the player cooldown");
            h.assertFalse(orb.isRemoved(),"Blocked pickup does not consume the orb");
            h.getLevel().setBlock(pos(h,5,3,2),Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(ExperienceOrbPhysics.canReach(orb,player),"A clear .75-height ray is sufficient while lower rays remain blocked");
            orb.playerTouch(player);
            h.assertTrue(orb.isRemoved(),"The same real native pickup succeeds through the newly opened upper ray");
            h.assertValueEqual(player.takeXpDelay,2,"Accepted pickup retains native delay");
            h.assertValueEqual(player.totalExperience,xp+7,"Accepted pickup awards exactly the saved value");
        } finally { orb.discard();removePlayer(h,player); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void narrowOrbRaysIgnoreMetalBarsButGlassAndLeavesStillObstruct(GameTestHelper h) {
        ServerPlayer player=player(h,point(h,6.5,2,2.5));Vec3 p=point(h,1.5,2.5,2.5);
        ExperienceOrb orb=new ExperienceOrb(h.getLevel(),p.x,p.y,p.z,1);
        try {
            for (int y=1;y<=4;y++) h.getLevel().setBlock(pos(h,5,y,2),Blocks.IRON_BARS.defaultBlockState(),3);
            h.assertTrue(ExperienceOrbPhysics.canReach(orb,player),"Source narrow physical policy explicitly ignores metal bars");
            for (int y=1;y<=4;y++) h.getLevel().setBlock(pos(h,5,y,2),Blocks.GLASS.defaultBlockState(),3);
            h.assertFalse(ExperienceOrbPhysics.canReach(orb,player),"Physical pickup cannot borrow the monster vision exception for glass");
            for (int y=1;y<=4;y++) h.getLevel().setBlock(pos(h,5,y,2),Blocks.OAK_LEAVES.defaultBlockState(),3);
            h.assertFalse(ExperienceOrbPhysics.canReach(orb,player),"Unlike dropped-item pickup, XP's narrow source policy does not ignore leaves");
        } finally { orb.discard();removePlayer(h,player); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void realOrbTickSinksInWaterDampsInFullLavaAndRejectsFireDamage(GameTestHelper h) {
        Vec3 p=point(h,3.5,3.2,3.5);BlockPos at=BlockPos.containing(p);
        ExperienceOrb orb=new ExperienceOrb(h.getLevel(),p.x,p.y,p.z,3);DeathExperienceOrbs.mark(orb,UUID.randomUUID());
        try {
            h.getLevel().setBlock(at,Blocks.WATER.defaultBlockState(),2);orb.setDeltaMovement(Vec3.ZERO);orb.tick();
            h.assertTrue(orb.getDeltaMovement().y<0,"The real transformed orb tick applies source gravity underwater, not modern buoyancy");
            orb.setPos(p);h.getLevel().setBlock(at,Blocks.LAVA.defaultBlockState(),2);orb.setDeltaMovement(.2,-.1,.1);orb.tick();
            h.assertTrue(Math.abs(orb.getDeltaMovement().y)<.000001,"Full lava removes vertical velocity instead of modern random bouncing");
            h.assertTrue(orb.getDeltaMovement().x>0&&orb.getDeltaMovement().x<.2,"Lava damps horizontal movement by .95 before common .98 friction");
            h.assertFalse(orb.hurt(h.getLevel().damageSources().lava(),100),"Source orbs are immune to lava damage");
            h.assertFalse(orb.isRemoved(),"Lava contact does not destroy the experience value");
        } finally { orb.discard();h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),2); }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void remoteOrUnloadedPickupCannotGenerateChunksOrSpendExperience(GameTestHelper h) {
        BlockPos distant=new BlockPos(29_000_000,80,29_000_000);
        ServerPlayer player=player(h,point(h,3.5,2,3.5));
        ExperienceOrb remote=new ExperienceOrb(h.getLevel(),distant.getX()+.5,distant.getY(),distant.getZ()+.5,19);
        try {
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(distant.getX()>>4,distant.getZ()>>4)==null,"The distant chunk is initially unloaded");
            h.assertTrue(ExperienceOrbPhysics.nonLoadingView(h.getLevel()).getBlockState(distant).is(Blocks.BARRIER),"Unloaded geometry blocks the ray without acquiring the chunk");
            player.takeXpDelay=0;int xp=player.totalExperience;
            remote.playerTouch(player);
            h.assertValueEqual(player.totalExperience,xp,"A direct remote pickup cannot award experience");
            h.assertValueEqual(player.takeXpDelay,0,"A rejected remote pickup has no cooldown side effect");
            h.assertFalse(remote.isRemoved(),"The inaccessible orb is not consumed");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(distant.getX()>>4,distant.getZ()>>4)==null,"Neither geometry nor pickup loaded the distant chunk");
        } finally { remote.discard();removePlayer(h,player); }
        h.succeed();
    }

    private static Explosion blast(GameTestHelper h,Vec3 p,float power,Explosion.BlockInteraction mode) {
        return new Explosion(h.getLevel(),null,p.x,p.y,p.z,power,false,mode);
    }
    private static BlockPos pos(GameTestHelper h,int x,int y,int z) { return h.absolutePos(new BlockPos(x,y,z)); }
    private static Vec3 point(GameTestHelper h,double x,double y,double z) {
        BlockPos origin=h.absolutePos(BlockPos.ZERO);return new Vec3(origin.getX()+x,origin.getY()+y,origin.getZ()+z);
    }
    private static ItemEntity item(GameTestHelper h,Item item,int count,Vec3 p) {
        ItemEntity entity=new ItemEntity(h.getLevel(),p.x,p.y,p.z,new ItemStack(item,count));h.getLevel().addFreshEntity(entity);return entity;
    }
    private static List<ItemEntity> drops(GameTestHelper h) { return h.getLevel().getEntitiesOfClass(ItemEntity.class,AABB.encapsulatingFullBlocks(pos(h,-3,-3,-3),pos(h,12,12,12))); }
    private static int total(GameTestHelper h,Item item) { return drops(h).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum(); }
    private static void clearDrops(GameTestHelper h) { drops(h).forEach(ItemEntity::discard); }
    @SuppressWarnings("removal") private static ServerPlayer player(GameTestHelper h,Vec3 p) {
        ServerPlayer player=h.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);player.moveTo(p);return player;
    }
    private static void removePlayer(GameTestHelper h,ServerPlayer player) { h.getLevel().getServer().getPlayerList().remove(player); }
}
