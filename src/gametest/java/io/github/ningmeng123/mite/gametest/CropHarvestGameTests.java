package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.survival.CropHarvest;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent source yield/reason fixtures exercise native loot, explosion and cancellation paths. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CropHarvestGameTests {
    @GameTest(template="empty")
    public static void allSourceCropsReturnSeedsOnlyBeforeSproutingAndFoodOnlyWhenMature(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            for(var block:List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,MiteFoodPlants.ONIONS.get())) {
                var crop=(CropBlock)block;
                for(int age=0;age<=7;age++) {
                    var state=crop.getStateForAge(age);
                    var drops=f.loot(state,CropHarvest.Reason.PLAYER,ItemStack.EMPTY);
                    int total=total(drops);
                    if(age==0)h.assertValueEqual(total,1,"Unstarted source crops return exactly one planting item");
                    else if(age<7)h.assertValueEqual(total,block==Blocks.POTATOES?1:0,"Only source potatoes return poison when prematurely harvested");
                    else if(block==Blocks.WHEAT) {
                        h.assertValueEqual(count(drops,Items.WHEAT),1,"Mature source wheat is exactly one grain");
                        h.assertValueEqual(total,1,"Mature source wheat adds no modern seed bonus");
                    } else h.assertTrue(total==2||total==3,"Mature source roots produce two or three vegetables");
                    if(block==Blocks.POTATOES&&age>0&&age<7)
                        h.assertValueEqual(count(drops,Items.POISONOUS_POTATO),1,"Immature potato output is poison, not food");
                }
            }
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void environmentalCausesKeepSourcePotatoExceptionAndExcludeDeadCrops(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            for(var reason:List.of(CropHarvest.Reason.TRAMPLE,CropHarvest.Reason.ILLEGAL,CropHarvest.Reason.FLOOD)) {
                for(var block:List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,MiteFoodPlants.ONIONS.get())) {
                    var crop=(CropBlock)block;
                    for(int age:new int[]{0,3,7}) {
                        var state=crop.getStateForAge(age);
                        h.assertValueEqual(total(f.loot(state,reason,ItemStack.EMPTY)),block==Blocks.POTATOES&&age==3?1:0,
                                "Environmental destruction excludes ordinary seeds and food but retains apt poison override");
                        h.assertTrue(f.loot(CropLifecycle.deadState(state,false),reason,ItemStack.EMPTY).isEmpty(),"Dead food crops never return planting material");
                    }
                }
            }
            for(var reason:List.of(CropHarvest.Reason.SNOW,CropHarvest.Reason.DROUGHT)) {
                h.assertValueEqual(count(f.loot(Blocks.WHEAT.defaultBlockState(),reason,ItemStack.EMPTY),Items.WHEAT_SEEDS),1,"Source snow preserves a stage-zero seed");
                h.assertValueEqual(count(f.loot(((CropBlock)Blocks.WHEAT).getStateForAge(7),reason,ItemStack.EMPTY),Items.WHEAT),1,"Source drought/snow retain mature wheat yield");
            }
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void liveIllPotatoesGiveOnePoisonButOtherIllAndAllDeadCropsGiveNothing(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            for(var block:List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,MiteFoodPlants.ONIONS.get(),Blocks.BEETROOTS)) {
                var crop=(CropBlock)block;var state=crop.getStateForAge(crop.getMaxAge());
                f.set(f.pos,state);CubeCropBlight.infect(f.level,f.pos);state=f.level.getBlockState(f.pos);
                var drops=f.loot(state,CropHarvest.Reason.PLAYER,ItemStack.EMPTY);
                h.assertValueEqual(total(drops),block==Blocks.POTATOES?1:0,"Only living diseased potatoes yield poison");
                if(block==Blocks.POTATOES)h.assertValueEqual(count(drops,Items.POISONOUS_POTATO),1,"Disease never returns an edible potato");
                h.assertTrue(f.loot(CropLifecycle.deadState(state,true),CropHarvest.Reason.PLAYER,ItemStack.EMPTY).isEmpty(),"Every dead food crop suppresses all native loot");
            }
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void realExplosionsReturnOneFruitSeedAndNoFoodCropWithOrWithoutDecay(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            for(var mode:List.of(Explosion.BlockInteraction.DESTROY,Explosion.BlockInteraction.DESTROY_WITH_DECAY))
                for(var block:List.of(Blocks.MELON,Blocks.PUMPKIN,Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,MiteFoodPlants.ONIONS.get())) {
                    var state=block instanceof CropBlock c?c.getStateForAge(c.getMaxAge()):block.defaultBlockState();
                    f.set(f.pos,state);List<ItemStack> drops=new ArrayList<>();
                    var explosion=new Explosion(f.level,null,f.pos.getX(),f.pos.getY(),f.pos.getZ(),4F,false,mode);
                    state.onExplosionHit(f.level,f.pos,explosion,(stack,pos)->drops.add(stack));
                    h.assertTrue(f.level.getBlockState(f.pos).isAir(),"Native explosion callback destroys the tested crop/fruit");
                    if(block==Blocks.MELON||block==Blocks.PUMPKIN) {
                        h.assertValueEqual(total(drops),1,"Fruit explosion yields exactly one seed, even with decay");
                        h.assertValueEqual(count(drops,block==Blocks.MELON?Items.MELON_SEEDS:Items.PUMPKIN_SEEDS),1,"Explosion seed matches the source fruit");
                    } else h.assertTrue(drops.isEmpty(),"Food crops never drop resources through TNT or decay explosions");
                }
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void melonIsFourSlicesWithoutFortuneAndSilkStillRecoversTheBlock(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            for(int level:new int[]{0,3}) {
                var tool=new ItemStack(Items.DIAMOND_AXE);
                if(level>0)tool.enchant(f.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE),level);
                for(int repeat=0;repeat<16;repeat++)h.assertValueEqual(count(f.loot(Blocks.MELON.defaultBlockState(),CropHarvest.Reason.PLAYER,tool),Items.MELON_SLICE),4,"Source melon yield ignores Fortune and is fixed at four");
            }
            var silk=new ItemStack(Items.DIAMOND_AXE);silk.enchant(f.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH),1);
            h.assertValueEqual(count(f.loot(Blocks.MELON.defaultBlockState(),CropHarvest.Reason.PLAYER,silk),Items.MELON),1,"Silk Touch retains the source whole melon path");
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void canceledAndClearedNativePoisonDropsAreNeverRecreated(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var state=((CropBlock)Blocks.POTATOES).getStateForAge(3);var player=h.makeMockPlayer(GameType.SURVIVAL);
            for(boolean cancel:new boolean[]{false,true}) {
                Consumer<BlockDropsEvent> listener=e->{if(e.getPos().equals(f.pos)){e.getDrops().clear();if(cancel)e.setCanceled(true);}};
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,BlockDropsEvent.class,listener);
                try {Block.dropResources(state,f.level,f.pos,null,player,ItemStack.EMPTY);}
                finally {NeoForge.EVENT_BUS.unregister(listener);}
                h.assertTrue(f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.pos).inflate(1)).isEmpty(),"Downstream drop suppression remains authoritative");
            }
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void sourceCropStateAndFertilityRoundTripThroughNativeBlockNbt(GameTestHelper h) {
        for(var block:List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,Blocks.BEETROOTS,MiteFoodPlants.ONIONS.get())) {
            var crop=(CropBlock)block;var original=CropLifecycle.deadState(crop.getStateForAge(crop.getMaxAge()),true);
            var restored=NbtUtils.readBlockState(h.getLevel().holderLookup(Registries.BLOCK),NbtUtils.writeBlockState(original));
            h.assertTrue(restored.equals(original)&&CubeCropBlight.dead(restored)&&CubeCropBlight.blighted(restored),"Saved native states retain species, age and both disease flags");
        }
        var soil=Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,4).setValue(FoodAgriculture.FERTILIZED,true);
        h.assertTrue(soil.equals(NbtUtils.readBlockState(h.getLevel().holderLookup(Registries.BLOCK),NbtUtils.writeBlockState(soil))),"Modern farmland saves both moisture and source fertilizer");
        h.succeed();
    }
    private static int total(List<ItemStack> drops){return drops.stream().mapToInt(ItemStack::getCount).sum();}
    private static int count(List<ItemStack> drops,Item item){return drops.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;final BlockPos pos;final GameTestHelper h;final Map<BlockPos,BlockState> saved=new LinkedHashMap<>();
        Fixture(GameTestHelper h){this.h=h;level=h.getLevel();pos=h.absolutePos(new BlockPos(2,3,2));set(pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));}
        void set(BlockPos p,BlockState state){saved.putIfAbsent(p,level.getBlockState(p));level.setBlock(p,state,2);}
        List<ItemStack> loot(BlockState state,CropHarvest.Reason reason,ItemStack tool){
            var builder=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).withParameter(LootContextParams.TOOL,tool)
                    .withParameter(LootContextParams.THIS_ENTITY,h.makeMockPlayer(GameType.SURVIVAL));
            List<ItemStack> result=new ArrayList<>();CropHarvest.withReason(reason,level,pos,()->result.addAll(state.getDrops(builder)));return result;
        }
        @Override public void close(){level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1)).forEach(ItemEntity::discard);saved.forEach((p,s)->level.setBlock(p,s,2));}
    }
}
