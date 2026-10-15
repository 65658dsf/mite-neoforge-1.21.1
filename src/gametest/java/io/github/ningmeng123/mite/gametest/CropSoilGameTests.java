package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import io.github.ningmeng123.mite.survival.FoodVessels;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native soil ticks, item conversion, plant routing and protection cancellation. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CropSoilGameTests {
    @GameTest(template = "empty")
    public static void waterAndFrozenIrrigationUseDifferentSourceDistances(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            for (var frozen : new net.minecraft.world.level.block.Block[]{Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW, Blocks.SNOW_BLOCK}) {
                f.set(f.pos.offset(2, 1, 0), frozen.defaultBlockState());
                helper.assertFalse(FoodAgriculture.hasWater(f.level, f.pos), "Frozen water two cells away is outside the source radius one");
                f.set(f.pos.offset(2, 1, 0), Blocks.AIR.defaultBlockState());
                f.set(f.pos.offset(1, 1, 1), frozen.defaultBlockState());
                f.set(f.pos, soil(0, false));
                f.level.getBlockState(f.pos).randomTick(f.level, f.pos, new FixedDraw(.99F, 255));
                helper.assertValueEqual(f.level.getBlockState(f.pos).getValue(FarmBlock.MOISTURE), 7,
                        "A diagonal adjacent ice/snow block irrigates real native farmland");
                f.set(f.pos.offset(1, 1, 1), Blocks.AIR.defaultBlockState());
            }
            f.set(f.pos.offset(4, 1, 4), Blocks.WATER.defaultBlockState());
            f.set(f.pos, soil(0, true));
            f.level.getBlockState(f.pos).randomTick(f.level, f.pos, new FixedDraw(.99F, 255));
            helper.assertValueEqual(f.level.getBlockState(f.pos).getValue(FarmBlock.MOISTURE), 7,
                    "Liquid water includes the radius-four upper diagonal endpoint");
            helper.assertTrue(FoodAgriculture.fertilized(f.level.getBlockState(f.pos)), "Irrigation preserves the saved fertilizer flag");
            f.set(f.pos.offset(4, 1, 4), Blocks.AIR.defaultBlockState());
            f.set(f.pos.offset(0, -1, 0), Blocks.WATER.defaultBlockState());
            helper.assertFalse(FoodAgriculture.hasWater(f.level, f.pos), "Water below the soil does not count as source irrigation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rainAddsOneMoisturePerRandomTickAndDryingHonorsItsProbability(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            f.level.setRainLevel(1F);
            helper.assertTrue(f.level.isRainingAt(f.pos.above()), "The actual outdoor forest fixture receives rain");
            f.set(f.pos, soil(2, true));
            f.level.getBlockState(f.pos).randomTick(f.level, f.pos, new FixedDraw(.99F, 255));
            helper.assertValueEqual(f.level.getBlockState(f.pos).getValue(FarmBlock.MOISTURE), 3,
                    "Rain increments wetness once instead of instantly filling it to seven");
            f.level.setRainLevel(0F);
            f.level.getBlockState(f.pos).randomTick(f.level, f.pos, new FixedDraw(.99F, 255));
            helper.assertValueEqual(f.level.getBlockState(f.pos).getValue(FarmBlock.MOISTURE), 3, "A high forest draw does not evaporate moisture");
            f.level.getBlockState(f.pos).randomTick(f.level, f.pos, new FixedDraw(0F, 255));
            helper.assertValueEqual(f.level.getBlockState(f.pos).getValue(FarmBlock.MOISTURE), 2, "A winning forest draw evaporates exactly one unit");
            helper.assertTrue(FoodAgriculture.fertilized(f.level.getBlockState(f.pos)), "Rain and evaporation preserve fertility");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dryBareSoilStaysTilledBesideAnyCropButRevertsWhenThatCropIsGone(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            f.set(f.pos, soil(0, false));
            f.set(f.pos.east(), soil(0, false));
            f.set(f.pos.east().above(), Blocks.WHEAT.defaultBlockState());
            f.level.getBlockState(f.pos).randomTick(f.level, f.pos, new FixedDraw(0F, 255));
            helper.assertTrue(f.level.getBlockState(f.pos).is(Blocks.FARMLAND), "An adjacent crop preserves dry bare farmland");
            f.set(f.pos.east().above(), Blocks.AIR.defaultBlockState());
            f.level.getBlockState(f.pos).randomTick(f.level, f.pos, new FixedDraw(0F, 255));
            helper.assertTrue(f.level.getBlockState(f.pos).is(Blocks.DIRT), "After removing the last neighboring crop a winning dry tick restores dirt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deadFoodCropsDoNotSupplyTheLivingCropGroupingBonus(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var living = Blocks.WHEAT.defaultBlockState();
            var dead = living.setValue(CubeCropBlight.DEAD, true);
            for (int x = 0; x <= 2; x++) f.set(f.pos.offset(x, -1, 0), soil(7, false));
            f.set(f.pos, living);
            f.set(f.pos.east(), dead);
            helper.assertValueEqual(FoodAgriculture.proximity(f.level, f.pos, Blocks.WHEAT), .5F,
                    "An old separate dead-crop ID does not count as the living plant's only neighbor");
            f.set(f.pos.east(), living);
            f.set(f.pos.east(2), dead);
            helper.assertValueEqual(FoodAgriculture.proximity(f.level, f.pos, Blocks.WHEAT), .75F,
                    "A dead second neighbor cannot complete a living group of three");
            f.set(f.pos.east(2), living);
            helper.assertValueEqual(FoodAgriculture.proximity(f.level, f.pos, Blocks.WHEAT), 1F,
                    "Three living source crops remove the isolation penalty");
            f.set(f.pos.east(2), Blocks.AIR.defaultBlockState());
            f.set(f.pos.east(), living.setValue(CubeCropBlight.BLIGHTED, true));
            helper.assertValueEqual(FoodAgriculture.proximity(f.level, f.pos, Blocks.WHEAT), .75F,
                    "Living diseased crops retain their source ID and still count as neighbors");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wetOrFertilizedSoilResistsTramplingButThePlantDoesNot(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            for (var state : new BlockState[]{soil(7, false), soil(0, true), soil(0, false)}) {
                f.set(f.pos, state);
                f.set(f.pos.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
                Blocks.FARMLAND.fallOn(f.level, state, f.pos, player, 2F);
                helper.assertTrue(f.level.getBlockState(f.pos.above()).isAir(), "Trampling destroys the actual plant even when its soil is protected");
                helper.assertTrue(f.level.getBlockState(f.pos).is(state.getValue(FarmBlock.MOISTURE) > 0 || FoodAgriculture.fertilized(state)
                        ? Blocks.FARMLAND : Blocks.DIRT), "Only dry unfertilized farmland turns back into dirt");
            }
            var slime = EntityType.SLIME.create(f.level);
            helper.assertTrue(slime != null, "A real gelatinous cube is available");
            try {
                f.set(f.pos, soil(0, false));
                f.set(f.pos.above(), Blocks.WHEAT.defaultBlockState());
                Blocks.FARMLAND.fallOn(f.level, f.level.getBlockState(f.pos), f.pos, slime, 2F);
                helper.assertTrue(f.level.getBlockState(f.pos).is(Blocks.FARMLAND) && f.level.getBlockState(f.pos.above()).is(Blocks.WHEAT),
                        "Gelatinous cubes trample neither crops nor soil");
            } finally { slime.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void externalTrampleCancellationProtectsBothThePlantAndItsSoil(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            f.set(f.pos, soil(0, false));
            f.set(f.pos.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            Consumer<BlockEvent.FarmlandTrampleEvent> cancel = event -> { if (event.getPos().equals(f.pos)) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(BlockEvent.FarmlandTrampleEvent.class, cancel);
            try { Blocks.FARMLAND.fallOn(f.level, f.level.getBlockState(f.pos), f.pos, player, 2F); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(f.level.getBlockState(f.pos).is(Blocks.FARMLAND) && f.level.getBlockState(f.pos.above()).is(Blocks.WHEAT),
                    "NeoForge cancellation prevents both the crop and farmland mutations");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waterBottleWatersOnlyOneCellReturnsItsBottleAndRespectsFullMoisture(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            f.set(f.pos, soil(0, true));
            f.set(f.pos.east(), soil(0, false));
            var water = new ItemStack(Items.POTION);
            water.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
            player.setItemInHand(InteractionHand.MAIN_HAND, water.copy());
            var hit = new BlockHitResult(f.pos.getCenter(), Direction.UP, f.pos, false);
            var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, f.pos, hit));
            helper.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction(), "Registered item-use handles watering exactly once");
            helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "Watering returns exactly the empty bottle");
            helper.assertValueEqual(f.level.getBlockState(f.pos).getValue(FarmBlock.MOISTURE), 7, "A water bottle fills its targeted soil");
            helper.assertValueEqual(f.level.getBlockState(f.pos.east()).getValue(FarmBlock.MOISTURE), 0, "A water bottle never applies the bucket area");
            player.setItemInHand(InteractionHand.MAIN_HAND, water.copy());
            helper.assertFalse(FoodVessels.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                    "Already wet soil rejects a second use");
            helper.assertTrue(player.getMainHandItem().is(Items.POTION), "A rejected second use keeps its full water bottle");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wateringWithAFullInventoryDropsExactlyOneEmptyBowl(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(f.pos.getX() + .5D, f.pos.getY() + 1D, f.pos.getZ() + .5D);
            for (int slot = 0; slot < player.getInventory().items.size(); slot++)
                player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.WATER_BOWL.get(), 2));
            f.set(f.pos, soil(0, false));
            var bounds = new AABB(f.pos).inflate(4D);
            var oldDrops = f.level.getEntitiesOfClass(ItemEntity.class, bounds).stream().map(ItemEntity::getId)
                    .collect(java.util.stream.Collectors.toSet());
            try {
                var hit = new BlockHitResult(f.pos.getCenter(), Direction.UP, f.pos, false);
                helper.assertTrue(FoodVessels.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                        "A full inventory does not prevent legitimate single-cell watering");
                helper.assertTrue(player.getMainHandItem().is(MiteFoods.WATER_BOWL.get()) && player.getMainHandItem().getCount() == 1,
                        "Exactly one filled bowl is consumed from its stacked source vessel");
                int bowls = f.level.getEntitiesOfClass(ItemEntity.class, bounds).stream().filter(drop -> !oldDrops.contains(drop.getId()))
                        .filter(drop -> drop.getItem().is(Items.BOWL)).mapToInt(drop -> drop.getItem().getCount()).sum();
                helper.assertValueEqual(bowls, 1, "The exact empty container is dropped once rather than deleted or duplicated");
            } finally {
                for (var drop : f.level.getEntitiesOfClass(ItemEntity.class, bounds)) if (!oldDrops.contains(drop.getId())) drop.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clickingFarmlandDoesNotActivateTheUnusedSourceBoneMealRoute(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            f.set(f.pos, soil(7, false));
            f.set(f.pos.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3).setValue(CubeCropBlight.BLIGHTED, true));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL, 2));
            var hit = new BlockHitResult(f.pos.getCenter(), Direction.UP, f.pos, false);
            var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, f.pos, hit));
            helper.assertFalse(event.getCancellationResult().consumesAction(), "The active source item caller has no farmland bone-meal route");
            helper.assertTrue(CubeCropBlight.blighted(f.level.getBlockState(f.pos.above())), "Aiming at soil does not cure the crop above it");
            helper.assertValueEqual(f.level.getBlockState(f.pos.above()).getValue(CropBlock.AGE), 3, "No indirect treatment or growth occurs");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "An unused soil route spends no bone meal");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void manureTargetsPumpkinStemSoilAndARejectedSecondUseConsumesNothing(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            f.set(f.pos, soil(7, false));
            f.set(f.pos.above(), Blocks.PUMPKIN_STEM.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.get("manure"), 2));
            var hit = new BlockHitResult(f.pos.above().getCenter(), Direction.NORTH, f.pos.above(), false);
            var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, f.pos.above(), hit));
            helper.assertTrue(event.isCanceled() && FoodAgriculture.fertilized(f.level.getBlockState(f.pos)), "Clicking a source stem fertilizes its supporting farmland");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Fertilization consumes exactly one manure");
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, f.pos.above(), hit));
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Already fertilized soil rejects the second use without consumption");
        }
        helper.succeed();
    }

    private static BlockState soil(int moisture, boolean fertilized) {
        return Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture).setValue(FoodAgriculture.FERTILIZED, fertilized);
    }
    private static final class FixedDraw extends LegacyRandomSource {
        private final float floatDraw;
        private final int intDraw;
        FixedDraw(float floatDraw, int intDraw) { super(196L); this.floatDraw = floatDraw; this.intDraw = intDraw; }
        @Override public float nextFloat() { return floatDraw; }
        @Override public int nextInt(int bound) { return Math.min(bound - 1, intDraw); }
    }
    private static final class Fixture implements AutoCloseable {
        private final ServerLevel level;
        private final BlockPos pos;
        private final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        private final Runnable restoreBiome;
        private final float rain;
        private final float thunder;
        Fixture(GameTestHelper helper) {
            level = helper.getLevel();
            var reference = helper.absolutePos(new BlockPos(2, 2, 2));
            pos = new BlockPos((reference.getX() & ~15) + 8, reference.getY() + 32, (reference.getZ() & ~15) + 8);
            rain = level.getRainLevel(1F);
            level.setRainLevel(1F);
            thunder = level.getThunderLevel(1F);
            level.setRainLevel(0F);
            level.setThunderLevel(0F);
            var chunk = level.getChunkAt(pos);
            var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
            var old = section.getBiomes();
            int x = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
            int y = QuartPos.fromBlock(pos.getY()) & ~3;
            int z = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            var sampler = level.getChunkSource().randomState().sampler();
            var forest = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.FOREST);
            section.fillBiomesFromNoise((qx, qy, qz, climate) -> forest, sampler, x, y, z);
            restoreBiome = () -> section.fillBiomesFromNoise((qx, qy, qz, climate) -> old.get(qx & 3, qy & 3, qz & 3), sampler, x, y, z);
            for (var cell : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 2, 4))) set(cell, Blocks.AIR.defaultBlockState());
        }
        void set(BlockPos at, BlockState state) {
            saved.putIfAbsent(at.immutable(), level.getBlockState(at));
            level.setBlock(at, state, 2);
        }
        @Override public void close() {
            saved.forEach((at, state) -> level.setBlock(at, state, 2));
            restoreBiome.run();
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
        }
    }
}
