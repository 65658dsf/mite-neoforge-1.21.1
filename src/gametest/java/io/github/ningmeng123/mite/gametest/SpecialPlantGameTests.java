package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import io.github.ningmeng123.mite.survival.FungalGrowth;
import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import io.github.ningmeng123.mite.survival.TropicalPlantGrowth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native mixed-in blocks exercise source special crops through real server world state. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SpecialPlantGameTests {
    private SpecialPlantGameTests() {}
    @GameTest(template="empty")
    public static void coldAndHotBiomesUseTheSameOptimalStemGrowthWithoutBypassingWater(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            f.daylight();
            for(var biome:List.of(Biomes.SNOWY_PLAINS,Biomes.PLAINS,Biomes.DESERT)) {
                f.biome(biome);
                for(BlockPos soil:BlockPos.betweenClosed(f.pos.offset(-1,-1,-1),f.pos.offset(1,-1,1)))
                    f.set(soil,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
                for(var block:List.of(Blocks.MELON_STEM,Blocks.PUMPKIN_STEM)) {
                    BlockState seed=block.defaultBlockState();
                    f.set(f.pos,seed); f.set(f.pos.east(),seed); f.set(f.pos.west(),seed);
                    f.set(f.pos.north(),Blocks.AIR.defaultBlockState()); f.set(f.pos.south(),Blocks.AIR.defaultBlockState());
                    h.assertValueEqual(SpecialPlantGrowth.stemGrowth(f.level,f.pos,seed),2.5F,
                            "Cold, temperate and hot dry biomes share the former optimal stem growth");
                    seed.randomTick(f.level,f.pos,new Draws(new int[]{0,1},new float[0]));
                    h.assertValueEqual(f.level.getBlockState(f.pos).getValue(StemBlock.AGE),1,
                            "An accepted real stem tick advances in every temperature zone");
                }
                f.set(f.pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,0));
                h.assertValueEqual(SpecialPlantGrowth.stemGrowth(f.level,f.pos,f.level.getBlockState(f.pos)),0F,
                        "Removing temperature requirements does not remove the wet-soil requirement");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void calibratedMelonAndPumpkinRowsIncludeActualFruitProduction(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            f.daylight();
            for(boolean melon:new boolean[]{false,true}) {
                var block=(StemBlock)(melon?Blocks.MELON_STEM:Blocks.PUMPKIN_STEM);
                BlockState seed=block.defaultBlockState().setValue(StemBlock.AGE,0);
                for(BlockPos soil:BlockPos.betweenClosed(f.pos.offset(-1,-1,-1),f.pos.offset(1,-1,1)))
                    f.set(soil,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
                for(Direction side:Direction.Plane.HORIZONTAL)f.set(f.pos.relative(side),Blocks.AIR.defaultBlockState());
                f.set(f.pos.west(),seed); f.set(f.pos.east(),seed); f.set(f.pos,seed);
                for(int age=0;age<7;age++) {
                    BlockState before=f.level.getBlockState(f.pos);
                    before.randomTick(f.level,f.pos,new Draws(new int[]{0,1},new float[0]));
                    h.assertValueEqual(f.level.getBlockState(f.pos).getValue(StemBlock.AGE),age+1,"Every accepted native stem update advances exactly one age");
                    h.assertTrue(f.level.getBlockState(f.pos.north()).isAir()&&f.level.getBlockState(f.pos.south()).isAir(),"Growing a stem to age seven does not yet deliver fruit");
                }
                BlockState mature=f.level.getBlockState(f.pos);
                mature.randomTick(f.level,f.pos,new Draws(new int[]{0,0},new float[0]));
                h.assertValueEqual(f.level.getBlockState(f.pos),mature,"The separate fifty-percent fruit gate can reject an accepted age-seven update");
                mature.randomTick(f.level,f.pos,new Draws(new int[]{0,1,0,1},new float[0]));
                h.assertValueEqual(f.level.getBlockState(f.pos),mature,"Choosing the occupied west side creates no fruit");
                mature.randomTick(f.level,f.pos,new Draws(new int[]{0,1,2,1},new float[]{.5F}));
                h.assertTrue(f.level.getBlockState(f.pos.north()).is(melon?Blocks.MELON:Blocks.PUMPKIN),"The accepted open-side attempt creates the real harvestable fruit");
                h.assertTrue(f.level.getBlockState(f.pos).is(melon?Blocks.ATTACHED_MELON_STEM:Blocks.ATTACHED_PUMPKIN_STEM),"Actual fruit generation attaches the live stem once");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void stemDeathIsSavedAndSeedRecoveryOnlyWorksBeforeSprouting(GameTestHelper h) {
        Player player=h.makeMockPlayer(GameType.SURVIVAL);
        for(var block:new StemBlock[]{(StemBlock)Blocks.MELON_STEM,(StemBlock)Blocks.PUMPKIN_STEM}) {
            for(int age=0;age<8;age++) {
                BlockState state=block.defaultBlockState().setValue(StemBlock.AGE,age);
                h.assertFalse(SpecialPlantGrowth.dead(state),"All new stems are healthy by default");
                h.assertValueEqual(drops(h,state,player).stream().mapToInt(ItemStack::getCount).sum(),age==0?1:0,"Only age-zero player harvests recover a seed");
                h.assertTrue(drops(h,state,null).isEmpty(),"Supporting-soil failure never yields source stem seeds");
                BlockState dead=state.setValue(CubeCropBlight.DEAD,true);
                h.assertTrue(drops(h,dead,player).isEmpty(),"Dead stems yield no seeds at any age");
                h.assertFalse(dead.isRandomlyTicking(),"Dead stems stop their lifecycle");
                BlockState restored=NbtUtils.readBlockState(h.getLevel().holderLookup(Registries.BLOCK),NbtUtils.writeBlockState(dead));
                h.assertTrue(SpecialPlantGrowth.dead(restored),"The chunk-state NBT round trip retains death");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void dryStemsDieAtFivePercentAndBoneMealCannotReviveThem(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            BlockState stem=Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE,4);
            f.set(f.pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,0));
            f.set(f.pos,stem);
            stem.randomTick(f.level,f.pos,new Draws(new int[0],new float[]{.05F}));
            h.assertFalse(SpecialPlantGrowth.dead(f.level.getBlockState(f.pos)),"The drought threshold is strictly below .05");
            stem.randomTick(f.level,f.pos,new Draws(new int[0],new float[]{.049F}));
            BlockState dead=f.level.getBlockState(f.pos);
            h.assertTrue(SpecialPlantGrowth.dead(dead),"A losing source drought draw records a dead stem");
            h.assertFalse(((StemBlock)stem.getBlock()).isValidBonemealTarget(f.level,f.pos,dead),"Bone meal cannot revive a dead stem");
            ((StemBlock)stem.getBlock()).performBonemeal(f.level,new Draws(),f.pos,dead);
            h.assertValueEqual(f.level.getBlockState(f.pos),dead,"Even a direct fertilizer call cannot bypass the dead-state guard");
            f.set(f.pos,stem); f.set(f.pos.below().east(4),Blocks.WATER.defaultBlockState());
            stem.randomTick(f.level,f.pos,new Draws(new int[0],new float[]{0F}));
            h.assertFalse(SpecialPlantGrowth.dead(f.level.getBlockState(f.pos)),"Dry metadata next to a real water source does not qualify for source drought death");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void stemManureModifiesGrowthButBoneMealRemainsInactive(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            BlockState wet=Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7);
            BlockState stem=Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE,5);
            for(BlockPos soil:BlockPos.betweenClosed(f.pos.offset(-1,-1,-1),f.pos.offset(1,-1,1))) f.set(soil,wet);
            f.set(f.pos,stem);
            float ordinary=SpecialPlantGrowth.stemGrowth(f.level,f.pos,stem);
            f.set(f.pos.below(),wet.setValue(FoodAgriculture.FERTILIZED,true));
            h.assertTrue(ordinary>0F,"Wet soil permits pumpkin growth");
            h.assertValueEqual(SpecialPlantGrowth.stemGrowth(f.level,f.pos,stem),ordinary*1.5F,"Stem fertilizer uses the exact source multiplier once");
            ItemStack boneMeal=new ItemStack(Items.BONE_MEAL,2);
            h.assertFalse(BoneMealItem.applyBonemeal(boneMeal,f.level,f.pos,h.makeMockPlayer(GameType.SURVIVAL)),"The actual bone-meal entry cannot reach the source's dormant stem-fertilizer helper");
            h.assertValueEqual(boneMeal.getCount(),2,"Rejected stem fertilization consumes no bone meal");
            ((StemBlock)stem.getBlock()).performBonemeal(f.level,new Draws(new int[]{0},new float[0]),f.pos,stem);
            h.assertValueEqual(f.level.getBlockState(f.pos),stem,"A direct modern fertilizer call cannot activate the unreachable source stem mechanic");
            for(Direction direction:Direction.Plane.HORIZONTAL) h.assertFalse(f.level.getBlockState(f.pos.relative(direction)).is(Blocks.PUMPKIN),"Bone meal cannot cause an immediate fruit tick");
            f.set(f.pos.below(),wet.setValue(FarmBlock.MOISTURE,0));
            h.assertValueEqual(SpecialPlantGrowth.stemGrowth(f.level,f.pos,stem),0F,"Dry soil completely stops the source stem formula");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void fruitProductionCanExhaustItsStemAndRemovalKeepsTheDeathState(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            f.daylight();
            BlockState stem=Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE,7);
            f.set(f.pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
            for(Direction direction:Direction.Plane.HORIZONTAL) { f.set(f.pos.relative(direction),Blocks.AIR.defaultBlockState()); f.set(f.pos.relative(direction).below(),Blocks.DIRT.defaultBlockState()); }
            f.set(f.pos,stem);
            stem.randomTick(f.level,f.pos,new Draws(new int[]{0,1,1,1},new float[]{.099F}));
            h.assertTrue(f.level.getBlockState(f.pos.east()).is(Blocks.PUMPKIN),"Source direction one produces fruit to the east");
            h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.ATTACHED_PUMPKIN_STEM)&&SpecialPlantGrowth.dead(f.level.getBlockState(f.pos)),"A fruit-production draw below ten percent kills the attached stem");
            f.set(f.pos.east(),Blocks.AIR.defaultBlockState());
            f.level.updateNeighborsAt(f.pos.east(),Blocks.PUMPKIN);
            h.assertTrue(SpecialPlantGrowth.dead(f.level.getBlockState(f.pos)),"Harvesting fruit cannot reset the plant to a healthy stem");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void sugarCaneUsesFullDaylightAndAnOpenSkyColumn(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            f.daylight();
            f.set(f.pos.below(),Blocks.SAND.defaultBlockState()); f.set(f.pos.below().east(),Blocks.WATER.defaultBlockState());
            BlockState cane=Blocks.SUGAR_CANE.defaultBlockState().setValue(SugarCaneBlock.AGE,14); f.set(f.pos,cane);
            cane.randomTick(f.level,f.pos,new Draws(new int[0],new float[]{.8F}));
            h.assertValueEqual(f.level.getBlockState(f.pos).getValue(SugarCaneBlock.AGE),15,"A winning independent slowdown draw advances cane once");
            f.set(f.pos.above(2),Blocks.STONE.defaultBlockState());
            BlockState grown=f.level.getBlockState(f.pos); grown.randomTick(f.level,f.pos,new Draws(new int[0],new float[]{.8F}));
            h.assertTrue(f.level.getBlockState(f.pos.above()).isAir(),"A covered column cannot grow even with one air block directly above the cane");
            f.set(f.pos.above(2),Blocks.GLASS.defaultBlockState());
            h.assertTrue(SpecialPlantGrowth.caneOpenSky(f.level,f.pos.above(2)),"Source zero-opacity glass remains transparent to the sky test");
            f.set(f.pos.above(2),Blocks.OAK_LEAVES.defaultBlockState());
            h.assertFalse(SpecialPlantGrowth.caneOpenSky(f.level,f.pos.above(2)),"Positive-opacity leaves block the sky test immediately, without waiting for light updates");
            f.set(f.pos.above(2),Blocks.AIR.defaultBlockState());
            grown.randomTick(f.level,f.pos,new Draws(new int[0],new float[]{.8F}));
            h.assertTrue(f.level.getBlockState(f.pos.above()).is(Blocks.SUGAR_CANE),"The sixteenth accepted update creates the next segment");
            h.assertValueEqual(f.level.getBlockState(f.pos).getValue(SugarCaneBlock.AGE),0,"The previous segment resets its age");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void coldAndHotSugarCaneKeepOnlyTheIndependentSlowdownRoll(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            f.daylight();
            f.set(f.pos.below(),Blocks.SAND.defaultBlockState());
            f.set(f.pos.below().east(),Blocks.WATER.defaultBlockState());
            BlockState cane=Blocks.SUGAR_CANE.defaultBlockState().setValue(SugarCaneBlock.AGE,14);
            for(var biome:List.of(Biomes.SNOWY_PLAINS,Biomes.DESERT)) {
                f.biome(biome); f.set(f.pos,cane);
                cane.randomTick(f.level,f.pos,new Draws(new int[0],new float[]{.7999F}));
                h.assertValueEqual(f.level.getBlockState(f.pos).getValue(SugarCaneBlock.AGE),14,
                        "The retained eighty-percent slowdown can reject growth in either biome");
                Draws accepted=new Draws(new int[0],new float[]{.8F});
                cane.randomTick(f.level,f.pos,accepted);
                h.assertValueEqual(f.level.getBlockState(f.pos).getValue(SugarCaneBlock.AGE),15,
                        "A boundary winning draw grows real cane in freezing and desert biomes alike");
                h.assertValueEqual(accepted.floatIndex,1,"Only the independent slowdown roll is consumed");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void blueberriesKeepTheSameRegrowthBoundaryAcrossTheirForestHabitat(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            f.daylight();
            f.set(f.pos.below(),Blocks.GRASS_BLOCK.defaultBlockState());
            var state=MiteFoodPlants.BLUEBERRY_BUSH.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE,1);
            for(var biome:List.of(Biomes.FOREST,Biomes.BIRCH_FOREST,Biomes.FLOWER_FOREST)) {
                f.biome(biome); f.set(f.pos,state);
                h.assertTrue(state.canSurvive(f.level,f.pos),"The regrowth comparison stays inside the existing forest habitat");
                state.randomTick(f.level,f.pos,new Draws(new int[0],new float[]{.025F}));
                h.assertValueEqual(f.level.getBlockState(f.pos).getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE),1,
                        "A draw exactly at the original optimal 2.5-percent boundary does not grow");
                state.randomTick(f.level,f.pos,new Draws(new int[0],new float[]{.024999F}));
                h.assertValueEqual(f.level.getBlockState(f.pos).getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE),2,
                        "Every permitted forest uses the same optimal regrowth chance despite its temperature");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void blueberryBoneMealRemainsInactiveWithoutConsumingItems(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            var bush=MiteFoodPlants.BLUEBERRY_BUSH.get();
            BlockState state=bush.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE,1);
            f.set(f.pos.below(),Blocks.GRASS_BLOCK.defaultBlockState()); f.set(f.pos,state);
            ItemStack boneMeal=new ItemStack(Items.BONE_MEAL,2);
            h.assertFalse(BoneMealItem.applyBonemeal(boneMeal,f.level,f.pos,h.makeMockPlayer(GameType.SURVIVAL)),"The actual item entry does not enable the dormant BlockBush fertilizer method");
            h.assertValueEqual(boneMeal.getCount(),2,"A rejected blueberry fertilizer attempt consumes no bone meal");
            bush.performBonemeal(f.level,new Draws(new int[]{0},new float[0]),f.pos,state);
            h.assertValueEqual(f.level.getBlockState(f.pos),state,"Even direct modern bone-meal calls leave blueberry regrowth unchanged");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void cocoaRequiresNaturalJungleCanopyAndOnlyMaturePodsDropBeans(GameTestHelper h) {
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            f.set(f.pos.above(),Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
            h.assertFalse(TropicalPlantGrowth.cocoaPlacement(f.level,f.pos),"Manually placed jungle leaves do not satisfy the natural-canopy condition");
            f.set(f.pos.above(),Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,false));
            h.assertTrue(TropicalPlantGrowth.cocoaPlacement(f.level,f.pos),"Naturally generated leaves immediately above permit planting");
            f.set(f.pos.above(),Blocks.COCOA.defaultBlockState()); f.set(f.pos.above(2),Blocks.COCOA.defaultBlockState());
            f.set(f.pos.above(3),Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,false));
            h.assertTrue(TropicalPlantGrowth.cocoaPlacement(f.level,f.pos),"Two cascading pods remain within the three-block canopy range");
            f.set(f.pos.above(3),Blocks.AIR.defaultBlockState());
            h.assertFalse(TropicalPlantGrowth.cocoaPlacement(f.level,f.pos),"A pod chain cannot replace the required canopy");
            for(int age=0;age<3;age++) {
                BlockState pod=Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE,age);
                int count=drops(h,pod,h.makeMockPlayer(GameType.SURVIVAL)).stream().mapToInt(ItemStack::getCount).sum();
                h.assertTrue(age==2?count==1||count==2:count==0,"Only mature source cocoa drops one or two beans");
                h.assertFalse(((CocoaBlock)Blocks.COCOA).isValidBonemealTarget(f.level,f.pos,pod),"Cocoa has no source bone-meal path");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void brownMushroomsCultureWetFertilizedSoilAndKeepThreeGiantStages(GameTestHelper h) {
        ServerLevel nether=h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether!=null,"The real dedicated-server registry includes the Nether");
        try(Fixture f=new Fixture(nether,h)) {
            f.set(f.pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7).setValue(FoodAgriculture.FERTILIZED,true));
            BlockState mushroom=Blocks.BROWN_MUSHROOM.defaultBlockState(); f.set(f.pos,mushroom);
            h.assertTrue(FungalGrowth.culture(nether,f.pos),"A shaded mushroom cultures hydrated enriched farmland into mycelium");
            h.assertTrue(nether.getBlockState(f.pos.below()).is(Blocks.MYCELIUM),"Culture changes the actual soil, not a detached flag");
            var block=(MushroomBlock)Blocks.BROWN_MUSHROOM;
            block.growMushroom(nether,f.pos,mushroom,new Draws());
            h.assertValueEqual(nether.getBlockState(f.pos).getValue(FungalGrowth.GROWTH),1,"The first giant-growth application records stage one");
            block.growMushroom(nether,f.pos,nether.getBlockState(f.pos),new Draws());
            BlockState stageTwo=nether.getBlockState(f.pos);
            h.assertValueEqual(stageTwo.getValue(FungalGrowth.GROWTH),2,"The second application still leaves a small mushroom");
            BlockState restored=NbtUtils.readBlockState(nether.holderLookup(Registries.BLOCK),NbtUtils.writeBlockState(stageTwo));
            h.assertValueEqual(restored.getValue(FungalGrowth.GROWTH),2,"Giant progress survives block-state serialization");
            h.assertFalse(block.isValidBonemealTarget(nether,f.pos,stageTwo),"Bone meal cannot trigger source giant mushrooms");
            block.performBonemeal(nether,new Draws(),f.pos,stageTwo);
            h.assertValueEqual(nether.getBlockState(f.pos),stageTwo,"A direct bone-meal call cannot bypass manure progression");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void myceliumCanSproutBrownMushroomsButDecaysUnderWater(GameTestHelper h) {
        ServerLevel nether=h.getLevel().getServer().getLevel(Level.NETHER);
        try(Fixture f=new Fixture(nether,h)) {
            f.set(f.pos,Blocks.MYCELIUM.defaultBlockState()); f.set(f.pos.above(),Blocks.AIR.defaultBlockState());
            f.level.getBlockState(f.pos).randomTick(f.level,f.pos,new Draws(new int[]{0,0},new float[0]));
            h.assertTrue(f.level.getBlockState(f.pos.above()).is(Blocks.BROWN_MUSHROOM),"The real mixed-in mycelium tick sprouts a mushroom after winning both source gates");
            f.set(f.pos.above(),Blocks.WATER.defaultBlockState());
            f.level.getBlockState(f.pos).randomTick(f.level,f.pos,new Draws());
            h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.DIRT),"Water above mycelium immediately destroys the culture");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void manureOnMyceliumAdvancesOnceAndConsumesOneItem(GameTestHelper h) {
        ServerLevel nether=h.getLevel().getServer().getLevel(Level.NETHER);
        try(Fixture f=new Fixture(nether,h)) {
            f.set(f.pos.below(),Blocks.MYCELIUM.defaultBlockState()); f.set(f.pos,Blocks.BROWN_MUSHROOM.defaultBlockState());
            var player=new net.neoforged.neoforge.common.util.FakePlayer(nether,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ManureCropTest"));
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MiteFoods.get("manure"),2));
            var hit=new BlockHitResult(f.pos.below().getCenter(),Direction.UP,f.pos.below(),false);
            var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,f.pos.below(),hit);
            NeoForge.EVENT_BUS.post(event);
            h.assertTrue(event.isCanceled(),"The real item event handled fungus fertilizer");
            h.assertValueEqual(player.getMainHandItem().getCount(),1,"Exactly one manure is consumed by one successful interaction");
            h.assertValueEqual(f.level.getBlockState(f.pos).getValue(FungalGrowth.GROWTH),1,"Direct mycelium application has no extra fifty-percent gate");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void netherWartUsesFortyRollsAndDiesOutsideItsHomeDimension(GameTestHelper h) {
        ServerLevel nether=h.getLevel().getServer().getLevel(Level.NETHER);
        try(Fixture f=new Fixture(nether,h)) {
            f.set(f.pos.below(),Blocks.SOUL_SAND.defaultBlockState()); f.set(f.pos,Blocks.NETHER_WART.defaultBlockState());
            Draws random=new Draws(new int[]{0},new float[0]);
            f.level.getBlockState(f.pos).randomTick(nether,f.pos,random);
            h.assertValueEqual(random.lastBound,40,"The actual mixed-in randomTick uses the source 1/40 denominator");
            h.assertValueEqual(nether.getBlockState(f.pos).getValue(NetherWartBlock.AGE),1,"A winning source draw advances exactly one stage");
        }
        try(Fixture f=new Fixture(h.getLevel(),h)) {
            f.set(f.pos.below(),Blocks.SOUL_SAND.defaultBlockState()); f.set(f.pos,Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE,3));
            h.assertTrue(f.level.getBlockState(f.pos).isRandomlyTicking(),"Even mature imported wart must validate its dimension");
            f.level.getBlockState(f.pos).randomTick(f.level,f.pos,new Draws());
            h.assertTrue(f.level.getBlockState(f.pos).isAir(),"A mature wart outside the Nether self-drops instead of bypassing dimension validation");
        }
        h.succeed();
    }
    private static List<ItemStack> drops(GameTestHelper h,BlockState state,Player player) {
        var builder=new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN,h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5,2.5,2.5)))
                .withParameter(LootContextParams.BLOCK_STATE,state).withParameter(LootContextParams.TOOL,ItemStack.EMPTY);
        if(player!=null) builder.withParameter(LootContextParams.THIS_ENTITY,player);
        return state.getDrops(builder);
    }
    private static final class Draws extends LegacyRandomSource {
        private final int[] ints; private final float[] floats; private int intIndex,floatIndex,lastBound;
        Draws() { this(new int[0],new float[0]); }
        Draws(int[] ints,float[] floats) { super(196); this.ints=ints; this.floats=floats; }
        @Override public int nextInt(int bound) { lastBound=bound; return Math.floorMod(intIndex<ints.length?ints[intIndex++]:1,bound); }
        @Override public float nextFloat() { int index=floatIndex++; return index<floats.length?floats[index]:.5F; }
    }
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level; final BlockPos pos; final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
        final long time; final float rain,thunder;
        final java.util.function.Consumer<ResourceKey<Biome>> fillBiome;
        final Runnable restoreBiomes;
        Fixture(ServerLevel level,GameTestHelper h) {
            this.level=level;
            BlockPos reference=h.absolutePos(new BlockPos(2,2,2));
            pos=new BlockPos((reference.getX()&~15)+8,level.getMaxBuildHeight()-40,(reference.getZ()&~15)+8);
            var chunk=level.getChunkAt(pos);
            var section=chunk.getSection(chunk.getSectionIndex(pos.getY()));
            var old=section.getBiomes();
            var snapshots=new ArrayList<Holder<Biome>>(64);
            for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<4;z++)snapshots.add(old.get(x,y,z));
            int quartX=QuartPos.fromBlock(chunk.getPos().getMinBlockX());
            int quartY=QuartPos.fromBlock(pos.getY())&~3;
            int quartZ=QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            var sampler=level.getChunkSource().randomState().sampler();
            fillBiome=biome->{
                var holder=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
                section.fillBiomesFromNoise((x,y,z,climate)->holder,sampler,quartX,quartY,quartZ);
            };
            restoreBiomes=()->section.fillBiomesFromNoise((x,y,z,climate)->
                    snapshots.get(((x&3)<<4)|((y&3)<<2)|(z&3)),sampler,quartX,quartY,quartZ);
            time=level.getDayTime(); rain=level.getRainLevel(1F);
            level.setRainLevel(1F); thunder=level.getThunderLevel(1F); level.setRainLevel(rain);
            for(BlockPos cell:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,4,1))) set(cell,Blocks.AIR.defaultBlockState());
        }
        void biome(ResourceKey<Biome> biome) { fillBiome.accept(biome); }
        void set(BlockPos pos,BlockState state) { originals.putIfAbsent(pos.immutable(),level.getBlockState(pos)); level.setBlock(pos,state,3); }
        void daylight() { level.setDayTime(6000); level.setRainLevel(0F); level.setThunderLevel(0F); level.updateSkyBrightness(); }
        @Override public void close() {
            level.setDayTime(time); level.setRainLevel(rain); level.setThunderLevel(thunder); level.updateSkyBrightness();
            originals.forEach((pos,state)->level.setBlock(pos,state,3));
            restoreBiomes.run();
        }
    }
}
