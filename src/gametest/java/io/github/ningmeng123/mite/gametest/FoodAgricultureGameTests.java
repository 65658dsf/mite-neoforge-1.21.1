package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.OnionCropBlock;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.ChestWormComposting;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent source yields, acquisition boundaries, fertilizer and worm-container conservation. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodAgricultureGameTests {
    @GameTest(template = "empty")
    public static void onionsReturnTheirSeedOnlyAtAgeZeroAndTwoOrThreeOnlyWhenHealthyAndMature(GameTestHelper helper) {
        var crop = MiteFoodPlants.ONIONS.get();
        for (int age = 0; age < 8; age++) {
            var state = crop.defaultBlockState().setValue(CropBlock.AGE, age);
            var drops = drops(helper, state, ItemStack.EMPTY, 196L);
            if (age == 0) helper.assertValueEqual(count(drops, MiteFoods.get("onion")), 1, "An unsprouted onion returns exactly one planting bulb");
            else if (age != 7) helper.assertTrue(drops.isEmpty(), "Intermediate crop stages lose the bulb when harvested");
            else helper.assertTrue(count(drops, MiteFoods.get("onion")) == 2 || count(drops, MiteFoods.get("onion")) == 3,
                    "Only the verified two-or-three mature yield is allowed");
            helper.assertTrue(drops(helper, state.setValue(OnionCropBlock.DEAD, true), ItemStack.EMPTY, 196L).isEmpty(), "Dead onion crops return no food");
            helper.assertTrue(drops(helper, state.setValue(OnionCropBlock.BLIGHTED, true), ItemStack.EMPTY, 196L).isEmpty(), "Blighted onion crops return no food");
        }
        var mature = crop.defaultBlockState().setValue(CropBlock.AGE, 7);
        helper.assertValueEqual(count(drops(helper, mature, ItemStack.EMPTY, new DropRandom(.249F, true)), MiteFoods.get("onion")),
                3, "The actual mature loot table adds a third onion for a draw below the source 25 percent threshold");
        helper.assertValueEqual(count(drops(helper, mature, ItemStack.EMPTY, new DropRandom(.25F, true)), MiteFoods.get("onion")),
                2, "The actual mature loot table yields two onions at the source 25 percent boundary");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blueberryLootRequiresMaturityAndOnlyShearsRecoverTheBush(GameTestHelper helper) {
        for (int age = 0; age < 4; age++) {
            var state = MiteFoodPlants.BLUEBERRY_BUSH.get().defaultBlockState().setValue(SweetBerryBushBlock.AGE, age);
            var ordinary = drops(helper, state, ItemStack.EMPTY, 196L);
            helper.assertValueEqual(count(ordinary, MiteFoods.get("blueberries")), age == 3 ? 1 : 0, "Maturity yields one berry, not vanilla sweet-berry quantities");
            helper.assertValueEqual(count(ordinary, MiteFoodPlants.BLUEBERRY_BUSH_ITEM.get()), 0, "An ordinary break cannot replant the bush");
            var sheared = drops(helper, state, new ItemStack(Items.SHEARS), 196L);
            helper.assertValueEqual(count(sheared, MiteFoodPlants.BLUEBERRY_BUSH_ITEM.get()), 1, "Shearing returns exactly one independently registered bush item");
            helper.assertValueEqual(count(sheared, MiteFoods.get("blueberries")), age == 3 ? 1 : 0, "Shearing preserves the same single mature-berry yield");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFarmlandPersistsFertilizationAndIncreasesOnionGrowthByOnePointFive(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState original = helper.getLevel().getBlockState(pos.below());
        try {
            BlockState soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
            helper.assertTrue(soil.hasProperty(FoodAgriculture.FERTILIZED), "The fertilizer flag belongs to native farmland's saved state");
            helper.getLevel().setBlock(pos.below(), soil, 2);
            var crop = MiteFoodPlants.ONIONS.get().defaultBlockState();
            float unfertilized = OnionCropBlock.growthRate(crop, helper.getLevel(), pos);
            helper.getLevel().setBlock(pos.below(), soil.setValue(FoodAgriculture.FERTILIZED, true), 2);
            float fertilized = OnionCropBlock.growthRate(crop, helper.getLevel(), pos);
            helper.assertTrue(unfertilized > 0F && fertilized == unfertilized * 1.5F, "Verified manure multiplies the same growth formula by exactly 1.5");
            BlockState restored = NbtUtils.readBlockState(helper.getLevel().holderLookup(Registries.BLOCK),
                    NbtUtils.writeBlockState(soil.setValue(FoodAgriculture.FERTILIZED, true)));
            helper.assertTrue(restored.getValue(FoodAgriculture.FERTILIZED), "Chunk-compatible NBT round trip keeps fertilization");
            helper.getLevel().setBlock(pos.below(), soil.setValue(FarmBlock.MOISTURE, 0), 2);
            helper.assertValueEqual(OnionCropBlock.growthRate(crop, helper.getLevel(), pos), 0F, "Dry farmland stops onion growth");
            helper.assertFalse(MiteFoodPlants.ONIONS.get().isValidBonemealTarget(helper.getLevel(), pos, crop), "Healthy onions cannot be instantly advanced with bone meal");
            var infected = crop.setValue(CropBlock.AGE, 4).setValue(OnionCropBlock.BLIGHTED, true);
            helper.getLevel().setBlock(pos, infected, 2);
            MiteFoodPlants.ONIONS.get().performBonemeal(helper.getLevel(), RandomSource.create(196), pos, infected);
            var cured = helper.getLevel().getBlockState(pos);
            helper.assertValueEqual(cured.getValue(CropBlock.AGE), 4, "Curing blight does not add growth stages");
            helper.assertFalse(cured.getValue(OnionCropBlock.BLIGHTED), "Bone meal clears living onion blight");
        } finally {
            helper.getLevel().removeBlock(pos, false);
            helper.getLevel().setBlock(pos.below(), original, 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onionsAndBlueberriesRequireWeatherAdjustedDaylightExactlyFifteen(GameTestHelper helper) {
        var level = helper.getLevel();
        var reference = helper.absolutePos(new BlockPos(2, 2, 2));
        // Isolate four floating cells from concurrent ground-level torch tests. The vertical
        // section center also keeps fuzzy biome samples inside the single restored palette.
        int skyFixtureY = ((level.getMaxBuildHeight() - 32) & ~15) + 8;
        var onionPos = new BlockPos((reference.getX() & ~15) + 8, skyFixtureY, (reference.getZ() & ~15) + 8);
        var berryPos = onionPos.east();
        var positions = new BlockPos[]{onionPos, onionPos.below(), berryPos, berryPos.below()};
        var previous = new BlockState[positions.length];
        for (int index = 0; index < positions.length; index++) previous[index] = level.getBlockState(positions[index]);
        var chunk = level.getChunkAt(onionPos);
        var section = chunk.getSection(chunk.getSectionIndex(onionPos.getY()));
        var biomes = section.getBiomes();
        int quartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
        int quartY = QuartPos.fromBlock(onionPos.getY()) & ~3;
        int quartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        var sampler = level.getChunkSource().randomState().sampler();
        var forest = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.FOREST);
        long dayTime = level.getDayTime();
        float rain = level.getRainLevel(1F);
        // getThunderLevel is multiplied by rain; unit rain exposes the raw level for exact restoration.
        level.setRainLevel(1F);
        float thunder = level.getThunderLevel(1F);
        level.setRainLevel(rain);
        try {
            section.fillBiomesFromNoise((x, y, z, climate) -> forest, sampler, quartX, quartY, quartZ);
            helper.assertTrue(level.getBiome(berryPos).is(io.github.ningmeng123.mite.block.BlueberryBushBlock.HABITAT),
                    "The real blueberry tick uses a legal native forest biome");
            helper.assertTrue(level.canSeeSky(onionPos.above()) && level.canSeeSky(berryPos.above()),
                    "The light fixture is outdoors rather than protected by a test structure roof");
            level.setBlock(onionPos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 2);
            level.setBlock(berryPos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            var onion = MiteFoodPlants.ONIONS.get().defaultBlockState().setValue(CropBlock.AGE, 2);
            var berry = MiteFoodPlants.BLUEBERRY_BUSH.get().defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1);
            long[] times = {6000L, 18000L, 6000L, 6000L};
            float[] rainfall = {0F, 0F, .3F, 1F};
            int[] lightLevels = {15, 4, 14, 12};
            for (int index = 0; index < times.length; index++) {
                level.setDayTime(times[index]);
                level.setRainLevel(rainfall[index]);
                level.setThunderLevel(0F);
                level.updateSkyBrightness();
                helper.assertValueEqual(level.getBrightness(LightLayer.BLOCK, onionPos.above()), 0,
                        "The isolated sky fixture has no block light from other tests");
                helper.assertValueEqual(level.getBrightness(LightLayer.BLOCK, berryPos.above()), 0,
                        "Neither floating plant receives block light from other tests");
                helper.assertValueEqual(level.getSkyDarken(), 15 - lightLevels[index],
                        "The source day and weather scenarios produce independently verified native sky subtraction");
                helper.assertValueEqual(level.getRawBrightness(onionPos.above(), 0), 15,
                        "Raw skylight remains 15 even when the actual day or weather light is lower");
                helper.assertValueEqual(level.getMaxLocalRawBrightness(onionPos.above()), lightLevels[index],
                        "The native light engine and current sky subtraction determine growth light");
                helper.assertValueEqual(level.getMaxLocalRawBrightness(berryPos.above()), lightLevels[index],
                        "Both crops observe the same actual outdoor light");
                level.setBlock(onionPos, onion, 2);
                level.setBlock(berryPos, berry, 2);
                // No blight, and a winning growth draw: only the real light condition may block growth.
                onion.randomTick(level, onionPos, new DropRandom(.5F, true));
                berry.randomTick(level, berryPos, new DropRandom(0F, true));
                boolean grows = lightLevels[index] == 15;
                helper.assertValueEqual(level.getBlockState(onionPos).getValue(CropBlock.AGE), grows ? 3 : 2,
                        "Onions grow at level 15, but not at night, the 14 boundary or full rain");
                helper.assertValueEqual(level.getBlockState(berryPos).getValue(SweetBerryBushBlock.AGE), grows ? 2 : 1,
                        "Blueberries grow at level 15, but not at night, the 14 boundary or full rain");
            }
        } finally {
            level.setDayTime(dayTime);
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
            level.updateSkyBrightness();
            for (int index = 0; index < positions.length; index++) level.setBlock(positions[index], previous[index], 2);
            section.fillBiomesFromNoise((x, y, z, climate) -> biomes.get(x & 3, y & 3, z & 3), sampler, quartX, quartY, quartZ);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualManureInteractionRejectsSpectatorsCooldownsAndDisabledItemUseWithoutConsuming(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var original = level.getBlockState(pos);
        var soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        try {
            for (int denied = 0; denied < 5; denied++) {
                level.setBlock(pos, soil, 2);
                var player = helper.makeMockPlayer(denied == 0 ? GameType.SPECTATOR : denied == 4 ? GameType.ADVENTURE : GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.get("manure"), 2));
                if (denied == 1) player.getCooldowns().addCooldown(MiteFoods.get("manure"), 20);
                if (denied == 4) {
                    player.getAbilities().mayBuild = false;
                    helper.assertFalse(player.getMainHandItem().has(net.minecraft.core.component.DataComponents.CAN_PLACE_ON),
                            "The adventure fixture explicitly lacks permission to place manure on any block");
                }
                var hit = new BlockHitResult(pos.getCenter(), net.minecraft.core.Direction.UP, pos, false);
                var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
                if (denied == 2) event.setUseItem(TriState.FALSE);
                if (denied == 3) event.setCanceled(true);
                NeoForge.EVENT_BUS.post(event);
                helper.assertFalse(level.getBlockState(pos).getValue(FoodAgriculture.FERTILIZED),
                        "The real right-click event rejects spectator, cooldown, denied useItem, cancellation and adventure without CanPlaceOn");
                helper.assertValueEqual(player.getMainHandItem().getCount(), 2,
                        "Rejected fertilization cannot consume either held manure item");
            }
            // A positive event control ensures the negative cases test the installed listener.
            level.setBlock(pos, soil, 2);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteFoods.get("manure"), 2));
            var hit = new BlockHitResult(pos.getCenter(), net.minecraft.core.Direction.UP, pos, false);
            var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
            NeoForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled() && level.getBlockState(pos).getValue(FoodAgriculture.FERTILIZED),
                    "An approved actual event reaches the registered manure interaction once");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Approved fertilization consumes exactly one manure");
        } finally {
            level.setBlock(pos, original, 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeBeetrootAgeThreeGrowthCanLoseFertilityWithoutReadingAgeSeven(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var original = level.getBlockState(pos);
        var originalSoil = level.getBlockState(pos.below());
        var crop = (CropBlock) Blocks.BEETROOTS;
        try {
            var soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7).setValue(FoodAgriculture.FERTILIZED, true);
            level.setBlock(pos.below(), soil, 2);
            var state = crop.getStateForAge(1);
            helper.assertFalse(state.hasProperty(CropBlock.AGE), "The real modern beetroot has AGE_3 rather than wheat's AGE_7");
            level.setBlock(pos, state, 2);
            state.randomTick(level, pos, new BeetrootGrowthRandom());
            helper.assertValueEqual(crop.getAge(level.getBlockState(pos)), 2, "Actual native beetroot random tick advances its own age property");
            helper.assertFalse(level.getBlockState(pos.below()).getValue(FoodAgriculture.FERTILIZED),
                    "The winning source 1/256 fertility-loss draw applies after actual beetroot growth");
        } finally {
            level.setBlock(pos, original, 2);
            level.setBlock(pos.below(), originalSoil, 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realOnionHarvestSoilLossDroughtAndExplosionsHonorDistinctDropCauses(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var original = level.getBlockState(pos);
        var originalSoil = level.getBlockState(pos.below());
        var crop = MiteFoodPlants.ONIONS.get();
        var mature = crop.getStateForAge(7);
        var seed = crop.getStateForAge(0);
        var soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var area = new AABB(pos).inflate(2D);
        var originalDrops = level.getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        try {
            level.setBlock(pos.below(), soil, 2);
            level.setBlock(pos, mature, 2);
            level.removeBlock(pos, false);
            crop.playerDestroy(level, player, pos, mature, null, ItemStack.EMPTY);
            int harvested = collectNewOnions(helper, area, originalDrops);
            helper.assertTrue(harvested == 2 || harvested == 3, "Actual native player harvest yields the source two or three onions");
            for (var state : new BlockState[]{mature, seed}) {
                level.setBlock(pos.below(), soil, 2);
                level.setBlock(pos, state, 2);
                level.destroyBlock(pos.below(), false, player);
                helper.assertTrue(level.getBlockState(pos).isAir(), "Breaking actual supporting soil invokes native plant self-removal");
                helper.assertValueEqual(collectNewOnions(helper, area, originalDrops), crop.getAge(state) == 0 ? 1 : 0,
                        "A seed self-drop returns one bulb; a mature self-drop must not become a harvest");
            }
            level.setBlock(pos.below(), soil.setValue(FarmBlock.MOISTURE, 0), 2);
            level.setBlock(pos, mature, 2);
            helper.assertFalse(FoodAgriculture.hasWater(level, pos.below()), "The actual drought fixture has no irrigation water");
            mature.randomTick(level, pos, new DropRandom(0F, true));
            helper.assertTrue(level.getBlockState(pos).isAir(), "A winning drought death removes the actual mature plant");
            int dried = collectNewOnions(helper, area, originalDrops);
            helper.assertTrue(dried == 2 || dried == 3, "Legal mature drought drops retain two or three onions without a player loot parameter");
            helper.assertTrue(Block.getDrops(mature, level, pos, null).isEmpty(), "The drought-only drop allowance is cleared after the real drop callback");
            for (var interaction : new Explosion.BlockInteraction[]{Explosion.BlockInteraction.DESTROY, Explosion.BlockInteraction.DESTROY_WITH_DECAY}) {
                for (var state : new BlockState[]{seed, mature}) {
                    level.setBlock(pos.below(), soil, 2);
                    level.setBlock(pos, state, 2);
                    var explosion = new Explosion(level, null, pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D, 1F, false, interaction);
                    var exploded = new java.util.ArrayList<ItemStack>();
                    state.onExplosionHit(level, pos, explosion, (stack, at) -> exploded.add(stack));
                    helper.assertTrue(exploded.isEmpty() && level.getBlockState(pos).isAir(),
                            "Actual native explosion callbacks drop neither seed nor mature onions with or without decay");
                }
            }
        } finally {
            collectNewOnions(helper, area, originalDrops);
            level.setBlock(pos, original, 2);
            level.setBlock(pos.below(), originalSoil, 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoWormsCompostTwoWheatIntoOneManureWithoutConsumingTheWorms(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper);
        chest.setItem(0, new ItemStack(MiteFoods.get("raw_worm"), 2));
        chest.setItem(1, new ItemStack(Items.WHEAT, 2));
        ChestWormComposting.process(chest, new AlwaysSuccess());
        helper.assertValueEqual(chest.countItem(MiteFoods.get("raw_worm")), 2, "Live worms remain after both independent feeding attempts");
        helper.assertValueEqual(chest.countItem(Items.WHEAT), 0, "Each source wheat contributes exactly 0.5 compost");
        helper.assertValueEqual(chest.countItem(MiteFoods.get("manure")), 1, "A full compost unit produces one manure item");
        helper.assertValueEqual(chest.getPersistentData().getFloat(ChestWormComposting.COMPOST_KEY), 0F, "No consumed whole compost unit is retained for duplication");
        chest.setItem(1, new ItemStack(Items.WHEAT));
        ChestWormComposting.process(chest, new AlwaysSuccess());
        var saved = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
        ChestBlockEntity restored = chest(helper);
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.getPersistentData().getFloat(ChestWormComposting.COMPOST_KEY), .5F, "Fractional compost survives native block-entity NBT");
        helper.assertValueEqual(restored.countItem(MiteFoods.get("raw_worm")), 2, "Persisted inventory retains the live worms");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullChestCannotDiscardASoupContainerToMakeManure(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper);
        chest.setItem(0, new ItemStack(MiteFoods.get("raw_worm")));
        chest.setItem(1, new ItemStack(Items.MUSHROOM_STEW));
        for (int slot = 2; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.STONE, 64));
        ChestWormComposting.process(chest, new AlwaysSuccess());
        helper.assertValueEqual(chest.countItem(Items.MUSHROOM_STEW), 1, "A single freed slot cannot hold both the empty bowl and manure");
        helper.assertValueEqual(chest.countItem(MiteFoods.get("manure")), 0, "Rejected composting does not create fertilizer");
        helper.assertValueEqual(chest.countItem(Items.BOWL), 0, "Rejected composting neither creates nor loses a container");
        helper.assertValueEqual(chest.getPersistentData().getFloat(ChestWormComposting.COMPOST_KEY), 0F, "Rejected feeding adds no fractional progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realNaturalLootHonorsPlacedLeavesSilkTouchAndSingleWormReplacement(GameTestHelper helper) {
        for (var leaves : new net.minecraft.world.level.block.Block[]{Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES}) {
            BlockState placed = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            for (int seed = 1; seed <= 128; seed++) helper.assertTrue(drops(helper, placed, ItemStack.EMPTY, seed).isEmpty(), "Player-placed old leaf species produce no saplings, sticks or fruit");
            var sheared = drops(helper, placed, new ItemStack(Items.SHEARS), 196L);
            helper.assertValueEqual(count(sheared, leaves.asItem()), 1, "Native shears still recover player-placed leaf blocks themselves");
        }
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (boolean replace : new boolean[]{true, false}) {
            var state = Blocks.GRASS_BLOCK.defaultBlockState();
            LootParams params = base(helper, state, ItemStack.EMPTY).withParameter(LootContextParams.THIS_ENTITY, player).create(LootContextParamSets.BLOCK);
            var loot = helper.getLevel().getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable())
                    .getRandomItems(params, new DropRandom(.5F, replace));
            helper.assertValueEqual(loot.size(), 1, "Harvested grass produces one worm OR one dirt stack");
            helper.assertValueEqual(loot.getFirst().getCount(), 1, "A worm never duplicates the ordinary dirt drop");
            helper.assertTrue(loot.getFirst().is(replace ? MiteFoods.get("raw_worm") : Items.DIRT),
                    "The registered natural loot modifier replaces only the winning integer draw with one worm");
        }
        ItemStack silk = new ItemStack(Items.DIAMOND_SHOVEL);
        silk.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        var protectedDrop = drops(helper, Blocks.GRASS_BLOCK.defaultBlockState(), silk, 196L);
        helper.assertValueEqual(count(protectedDrop, Items.GRASS_BLOCK), 1, "Silk Touch preserves the grass block instead of extracting a worm");
        helper.succeed();
    }
    private static ChestBlockEntity chest(GameTestHelper helper) {
        var chest = new ChestBlockEntity(helper.absolutePos(new BlockPos(2, 2, 2)), Blocks.CHEST.defaultBlockState());
        chest.setLevel(helper.getLevel()); return chest;
    }
    private static int collectNewOnions(GameTestHelper helper, AABB area, Set<Integer> original) {
        int onions = 0;
        for (var drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) if (!original.contains(drop.getId())) {
            if (drop.getItem().is(MiteFoods.get("onion"))) onions += drop.getItem().getCount();
            drop.discard();
        }
        return onions;
    }
    private static LootParams.Builder base(GameTestHelper helper, BlockState state, ItemStack tool) {
        return new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN,
                helper.absolutePos(new BlockPos(2, 2, 2)).getCenter()).withParameter(LootContextParams.BLOCK_STATE, state).withParameter(LootContextParams.TOOL, tool);
    }
    private static List<ItemStack> drops(GameTestHelper helper, BlockState state, ItemStack tool, long seed) {
        return drops(helper, state, tool, RandomSource.create(seed));
    }
    private static List<ItemStack> drops(GameTestHelper helper, BlockState state, ItemStack tool, RandomSource random) {
        return helper.getLevel().getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable())
                .getRandomItems(base(helper, state, tool).create(LootContextParamSets.BLOCK), random);
    }
    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static final class AlwaysSuccess extends LegacyRandomSource {
        AlwaysSuccess() { super(196L); }
        @Override public int nextInt(int bound) { return 0; }
    }
    private static final class BeetrootGrowthRandom extends LegacyRandomSource {
        private int draws;
        BeetrootGrowthRandom() { super(196L); }
        @Override public int nextInt(int bound) {
            // The beetroot's first draw must enter its native superclass tick; following
            // draws win native growth and the independently verified 1/256 fertilizer loss.
            return draws++ == 0 ? 1 : 0;
        }
    }
    /** Independent boundary draws avoid correlated first draws of freshly initialized nearby seeds. */
    private static final class DropRandom extends LegacyRandomSource {
        private final float floatDraw;
        private final boolean firstInteger;
        DropRandom(float floatDraw, boolean firstInteger) {
            super(196L);
            this.floatDraw = floatDraw;
            this.firstInteger = firstInteger;
        }
        @Override public float nextFloat() { return floatDraw; }
        @Override public int nextInt(int bound) { return firstInteger ? 0 : bound - 1; }
    }
}
