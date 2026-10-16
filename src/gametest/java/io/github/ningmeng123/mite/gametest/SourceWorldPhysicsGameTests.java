package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.survival.DeathExperienceOrbs;
import io.github.ningmeng123.mite.world.SourceExplosionItems;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
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
