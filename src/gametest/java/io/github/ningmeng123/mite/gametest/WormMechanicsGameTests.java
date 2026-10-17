package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.survival.ChestWormComposting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source compost amounts, atomic container transactions and actual chest random-tick dispatch. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WormMechanicsGameTests {
    @GameTest(template = "empty")
    public static void blockMaterialsHaveTheirIndependentSourceCompostingValues(GameTestHelper h) {
        Map<Item, Float> expected = Map.ofEntries(
                Map.entry(Items.PAPER, .1F), Map.entry(Items.HAY_BLOCK, 4.5F),
                Map.entry(Items.OAK_LEAVES, 1F), Map.entry(Items.SPRUCE_LEAVES, 1F),
                Map.entry(Items.BIRCH_LEAVES, 1F), Map.entry(Items.JUNGLE_LEAVES, 1F), Map.entry(Items.VINE, 1F),
                Map.entry(Items.MELON, 2F), Map.entry(Items.PUMPKIN, 2F),
                Map.entry(Items.CARVED_PUMPKIN, 2F), Map.entry(Items.JACK_O_LANTERN, 2F),
                Map.entry(Items.RED_MUSHROOM, .5F), Map.entry(Items.BROWN_MUSHROOM, .5F),
                Map.entry(Items.SHORT_GRASS, .5F), Map.entry(Items.FERN, .5F), Map.entry(Items.LILY_PAD, .5F),
                Map.entry(Items.DANDELION, .25F), Map.entry(Items.POPPY, .25F), Map.entry(Items.CAKE, 2.4F));
        expected.forEach((item, value) -> h.assertValueEqual(ChestWormComposting.compostingValue(new ItemStack(item)), value,
                "Literal source block compost amount for " + item));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wormsSeedsMilkGoldenFoodsAndUnlistedPlantsRemainInedibleToWorms(GameTestHelper h) {
        List<Item> forbidden = new ArrayList<>(List.of(MiteFoods.get("raw_worm"), Items.WHEAT_SEEDS,
                Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS, Items.TORCHFLOWER_SEEDS,
                Items.PITCHER_POD, Items.EGG, Items.MILK_BUCKET, MiteFoods.get("milk_bowl"),
                Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT,
                Items.DEAD_BUSH, Items.OAK_SAPLING, Items.BIRCH_SAPLING, Items.STONE));
        for (String material : MiteFoods.METALS)
            forbidden.add(MiteFoods.get(material + "_milk_bucket"));
        for (Item item : forbidden) {
            ItemStack input = new ItemStack(item);
            h.assertValueEqual(ChestWormComposting.compostingValue(input), 0F, "Source exclusion for " + item);
            ChestBlockEntity chest = chest(h); chest.setItem(0, worms(2)); chest.setItem(1, input.copy());
            ChestWormComposting.process(chest, new WinningRandom());
            h.assertTrue(ItemStack.matches(chest.getItem(1), input) && chest.countItem(MiteFoods.get("manure")) == 0,
                    "Excluded items never disappear or create fertilizer: " + item);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cookedMeatUsesTheRawValueAndCookedWormsRemainFood(GameTestHelper h) {
        for (Item[] pair : List.of(new Item[]{Items.BEEF, Items.COOKED_BEEF}, new Item[]{Items.PORKCHOP, Items.COOKED_PORKCHOP},
                new Item[]{Items.CHICKEN, Items.COOKED_CHICKEN}, new Item[]{Items.COD, Items.COOKED_COD},
                new Item[]{Items.SALMON, Items.COOKED_SALMON}, new Item[]{Items.MUTTON, Items.COOKED_MUTTON},
                new Item[]{Items.RABBIT, Items.COOKED_RABBIT}, new Item[]{MiteFoods.get("raw_large_fish"), MiteFoods.get("cooked_large_fish")})) {
            float raw = ChestWormComposting.compostingValue(new ItemStack(pair[0]));
            h.assertTrue(raw > 0F, "The raw source meat has a positive actual registered food value");
            h.assertValueEqual(ChestWormComposting.compostingValue(new ItemStack(pair[1])), raw,
                    "Cooking cannot inflate compost yield for " + pair[1]);
        }
        h.assertValueEqual(ChestWormComposting.compostingValue(new ItemStack(MiteFoods.get("cooked_worm"))), .1F,
                "A cooked worm contributes the source tenth-unit value without becoming a live worker");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void melonAndPumpkinBlocksReturnOnlyTheirSourceSeedsAndLanternTorch(GameTestHelper h) {
        for (Item input : List.of(Items.MELON, Items.PUMPKIN, Items.CARVED_PUMPKIN, Items.JACK_O_LANTERN)) {
            ChestBlockEntity chest = chest(h); chest.setItem(0, worms(1)); chest.setItem(1, new ItemStack(input));
            ChestWormComposting.process(chest, new WinningRandom());
            h.assertValueEqual(chest.countItem(input), 0, "One source block is actually consumed: " + input);
            h.assertValueEqual(chest.countItem(MiteFoods.get("raw_worm")), 1, "The live worker survives feeding");
            h.assertValueEqual(chest.countItem(MiteFoods.get("manure")), 2, "The whole block contributes two complete compost units");
            h.assertValueEqual(chest.countItem(Items.MELON_SEEDS), input == Items.MELON ? 1 : 0, "Melon returns one melon seed");
            h.assertValueEqual(chest.countItem(Items.PUMPKIN_SEEDS), input == Items.MELON ? 0 : 1, "Pumpkin variants return one pumpkin seed");
            h.assertValueEqual(chest.countItem(Items.TORCH), input == Items.JACK_O_LANTERN ? 1 : 0, "Only a lantern returns its original torch");
            near(h, compost(chest), 0F, "No whole compost output is retained for duplication");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hayAndCakeProduceMultipleManureWithOnlyTheSourceFractionRetained(GameTestHelper h) {
        for (Item input : List.of(Items.HAY_BLOCK, Items.CAKE)) {
            ChestBlockEntity chest = chest(h); chest.setItem(0, worms(1)); chest.setItem(1, new ItemStack(input));
            ChestWormComposting.process(chest, new WinningRandom());
            h.assertValueEqual(chest.countItem(MiteFoods.get("manure")), input == Items.HAY_BLOCK ? 4 : 2,
                    "One registered source item yields every complete compost unit");
            near(h, compost(chest), input == Items.HAY_BLOCK ? .5F : .4F, "Only the source fractional remainder survives");
            h.assertValueEqual(chest.countItem(input), 0, "Exactly one input is consumed");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void eachIndividualWormReceivesOneIndependentSourceDenominatorRoll(GameTestHelper h) {
        ChestBlockEntity chest = chest(h); chest.setItem(0, worms(1)); chest.setItem(2, worms(2));
        chest.setItem(1, new ItemStack(Items.WHEAT, 3));
        WinningRandom random = new WinningRandom(); ChestWormComposting.process(chest, random);
        h.assertValueEqual(random.bounds, List.of(1, 50, 1, 50, 1, 50),
                "Three individual workers each select a food slot then draw the source 100-times-half-unit denominator");
        h.assertValueEqual(chest.countItem(MiteFoods.get("raw_worm")), 3, "Worker stack splitting never consumes or duplicates live worms");
        h.assertValueEqual(chest.countItem(Items.WHEAT), 0, "Three successful independent attempts consume exactly three wheat");
        h.assertValueEqual(chest.countItem(MiteFoods.get("manure")), 1, "Three half-units yield one complete item");
        near(h, compost(chest), .5F, "The final half-unit remains available for later feeding");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedSourceRollAndNoLiveWormCannotConsumeAFlower(GameTestHelper h) {
        ChestBlockEntity chest = chest(h); chest.setItem(0, worms(1)); chest.setItem(1, new ItemStack(Items.POPPY));
        WinningRandom failing = new WinningRandom() {
            @Override public int nextInt(int bound) { bounds.add(bound); return bound == 1 ? 0 : bound - 1; }
        };
        ChestWormComposting.process(chest, failing);
        h.assertValueEqual(failing.bounds, List.of(1, 25), "A quarter-unit flower uses the source twenty-five-way feeding roll");
        h.assertValueEqual(chest.countItem(Items.POPPY), 1, "A losing probability draw does not consume the selected flower");
        chest.setItem(0, new ItemStack(MiteFoods.get("cooked_worm")));
        WinningRandom noWorker = new WinningRandom(); ChestWormComposting.process(chest, noWorker);
        h.assertTrue(noWorker.bounds.isEmpty() && chest.countItem(Items.POPPY) == 1,
                "Cooked worms cannot act as workers and do not even initiate a feeding draw");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lanternCannotBeConsumedUnlessBothRemaindersAndManureFitAtomically(GameTestHelper h) {
        for (int emptySlots : new int[]{0, 1}) {
            ChestBlockEntity chest = full(h); chest.setItem(0, worms(1)); chest.setItem(1, new ItemStack(Items.JACK_O_LANTERN));
            if (emptySlots == 1) chest.setItem(2, ItemStack.EMPTY);
            ChestWormComposting.process(chest, new WinningRandom());
            h.assertValueEqual(chest.countItem(Items.JACK_O_LANTERN), 1, "The freed input slot plus at most one spare cannot hold three output kinds");
            h.assertTrue(chest.countItem(Items.PUMPKIN_SEEDS) == 0 && chest.countItem(Items.TORCH) == 0
                            && chest.countItem(MiteFoods.get("manure")) == 0,
                    "A rejected transaction cannot partially return seeds, torch or fertilizer");
            near(h, compost(chest), 0F, "Rejected feeding cannot grant stored progress");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void exactlyThreeOutputSlotsOrExistingMergeCapacityAcceptTheLanternWithoutLoss(GameTestHelper h) {
        ChestBlockEntity empty = full(h); empty.setItem(0, worms(1)); empty.setItem(1, new ItemStack(Items.JACK_O_LANTERN));
        empty.setItem(2, ItemStack.EMPTY); empty.setItem(3, ItemStack.EMPTY);
        ChestWormComposting.process(empty, new WinningRandom());
        h.assertTrue(empty.countItem(Items.JACK_O_LANTERN) == 0 && empty.countItem(Items.PUMPKIN_SEEDS) == 1
                        && empty.countItem(Items.TORCH) == 1 && empty.countItem(MiteFoods.get("manure")) == 2,
                "The consumed slot plus two empty slots fit exactly three output kinds");
        ChestBlockEntity merge = full(h); merge.setItem(0, worms(1)); merge.setItem(1, new ItemStack(Items.JACK_O_LANTERN, 2));
        ItemStack seeds = new ItemStack(Items.PUMPKIN_SEEDS), torch = new ItemStack(Items.TORCH), manure = new ItemStack(MiteFoods.get("manure"));
        seeds.setCount(seeds.getMaxStackSize() - 1); torch.setCount(torch.getMaxStackSize() - 1); manure.setCount(manure.getMaxStackSize() - 2);
        merge.setItem(2, seeds); merge.setItem(3, torch); merge.setItem(4, manure);
        ChestWormComposting.process(merge, new WinningRandom());
        h.assertValueEqual(merge.countItem(Items.JACK_O_LANTERN), 1, "A full inventory with merge capacity consumes exactly one lantern");
        h.assertValueEqual(merge.countItem(Items.PUMPKIN_SEEDS), seeds.getMaxStackSize(), "Returned seed uses the existing final free seed capacity");
        h.assertValueEqual(merge.countItem(Items.TORCH), torch.getMaxStackSize(), "Returned torch merges without requiring a new slot");
        h.assertValueEqual(merge.countItem(MiteFoods.get("manure")), manure.getMaxStackSize(), "Both whole manure units fit exactly in the existing stack");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedWholeCompostAndFractionalProgressSurviveNativeInventorySerialization(GameTestHelper h) {
        ChestBlockEntity chest = full(h); chest.setItem(0, worms(2));
        chest.getPersistentData().putFloat(ChestWormComposting.COMPOST_KEY, 1.5F);
        ChestWormComposting.process(chest, new WinningRandom());
        near(h, compost(chest), 1.5F, "A full chest retains its unconverted whole unit instead of losing it");
        ChestBlockEntity restored = chest(h);
        restored.loadWithComponents(chest.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertValueEqual(restored.countItem(MiteFoods.get("raw_worm")), 2, "Native NBT restores the actual worker stack count");
        near(h, compost(restored), 1.5F, "Native persistent block-entity data restores both whole and fractional pending compost");
        restored.setItem(1, ItemStack.EMPTY); ChestWormComposting.process(restored, new WinningRandom());
        h.assertValueEqual(restored.countItem(MiteFoods.get("manure")), 1, "Making room converts the pending unit exactly once");
        near(h, compost(restored), .5F, "The fractional part remains after flushing the restored whole unit");
        ChestWormComposting.process(restored, new WinningRandom());
        h.assertValueEqual(restored.countItem(MiteFoods.get("manure")), 1, "A second processing pass cannot duplicate the restored output");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void placedOrdinaryTrappedAndEveryMetalChestReceiveActualRegisteredRandomTicks(GameTestHelper h) {
        List<Block> variants = new ArrayList<>(List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST));
        MiteFunctionalBlocks.STRONGBOXES.values().forEach(holder -> variants.add(holder.get()));
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 2));
        try {
            for (Block block : variants) {
                removeChest(h, pos);
                h.getLevel().setBlock(pos, block.defaultBlockState(), 2);
                var state = h.getLevel().getBlockState(pos);
                var chest = (ChestBlockEntity)h.getLevel().getBlockEntity(pos);
                h.assertTrue(chest != null && state.isRandomlyTicking(), "Placed chest state participates in the native random-tick dispatcher: " + block);
                var chunk = h.getLevel().getChunkAt(pos);
                h.assertTrue(chunk.getSection(chunk.getSectionIndex(pos.getY())).isRandomlyTicking(),
                        "Real chunk-section counters include the placed random-ticking chest: " + block);
                chest.setItem(0, worms(2)); chest.setItem(1, new ItemStack(Items.WHEAT, 2));
                state.randomTick(h.getLevel(), pos, new WinningRandom());
                h.assertTrue(chest.countItem(Items.WHEAT) == 0 && chest.countItem(MiteFoods.get("manure")) == 1
                                && chest.countItem(MiteFoods.get("raw_worm")) == 2,
                        "Actual registered block-state randomTick executes feeding through the placed block entity: " + block);
            }
        } finally { removeChest(h, pos); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeTickChunkSelectionActuallyDispatchesThePlacedChestAndZeroSpeedDoesNot(GameTestHelper h) throws Exception {
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 2)); h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        try { tickSelectedChest(h, pos); }
        finally { removeChest(h, pos); }
        h.succeed();
    }

    private static void tickSelectedChest(GameTestHelper h, BlockPos pos) throws Exception {
        ChestBlockEntity chest = (ChestBlockEntity)h.getLevel().getBlockEntity(pos);
        chest.setItem(0, worms(1)); chest.getPersistentData().putFloat(ChestWormComposting.COMPOST_KEY, 1F);
        var level = h.getLevel(); var chunk = level.getChunkAt(pos);
        Field randomPosition = Level.class.getDeclaredField("randValue"); randomPosition.setAccessible(true);
        int oldPosition = randomPosition.getInt(level);
        int precedingSections = 0;
        for (int i = 0; i < chunk.getSectionIndex(pos.getY()); ++i)
            if (chunk.getSection(i).isRandomlyTicking()) ++precedingSections;
        int coordinateBits = (pos.getX() & 15) | ((pos.getZ() & 15) << 8) | ((pos.getY() & 15) << 16);
        int winningPositionSeed = coordinateBits << 2;
        // Invert the native 32-bit LCG; 0xAAAAAAAB is the multiplicative inverse of three.
        for (int step = 0; step <= precedingSections; ++step)
            winningPositionSeed = (winningPositionSeed - 1013904223) * 0xAAAAAAAB;
        long drySelectionSeed = 0;
        for (;; ++drySelectionSeed) {
            RandomSource oracle = RandomSource.create(drySelectionSeed);
            if (level.isRaining() && level.isThundering() && oracle.nextInt(100000) == 0) continue;
            if (oracle.nextInt(48) != 0) break;
        }
        try {
            level.random.setSeed(drySelectionSeed); level.tickChunk(chunk, 0);
            h.assertValueEqual(chest.countItem(MiteFoods.get("manure")), 0, "Native zero-speed chunk ticking does not process stored compost");
            randomPosition.setInt(level, winningPositionSeed);
            level.random.setSeed(drySelectionSeed); level.tickChunk(chunk, 1);
            h.assertValueEqual(chest.countItem(MiteFoods.get("manure")), 1,
                    "Native chunk selection reaches the real chest block and dispatches its registered randomTick without direct process calls");
            near(h, compost(chest), 0F, "The selected native block tick consumes the stored whole unit exactly once");
        } finally {
            randomPosition.setInt(level, oldPosition);
        }
    }

    @GameTest(template = "empty")
    public static void nativeRandomTickNeverUnpacksAnUnopenedPlayersLootTable(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 2)); h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        try {
            ChestBlockEntity chest = (ChestBlockEntity)h.getLevel().getBlockEntity(pos);
            chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, 196L);
            var before = chest.saveWithFullMetadata(h.getLevel().registryAccess());
            WinningRandom random = new WinningRandom();
            h.getLevel().getBlockState(pos).randomTick(h.getLevel(), pos, random);
            var after = chest.saveWithFullMetadata(h.getLevel().registryAccess());
            h.assertTrue(chest.getLootTable() == BuiltInLootTables.SIMPLE_DUNGEON
                            && after.getString("LootTable").equals(before.getString("LootTable"))
                            && after.getLong("LootTableSeed") == 196L,
                    "Random ticking preserves the actual deferred loot key and generation seed for the eventual opening player");
            h.assertFalse(after.contains("Items"), "The unopened chest still has deferred loot rather than a generated item inventory");
            h.assertTrue(random.bounds.isEmpty() && !chest.getPersistentData().contains(ChestWormComposting.COMPOST_KEY),
                    "An unopened loot chest initiates neither worker draws nor compost progress");
        } finally { removeChest(h, pos); }
        h.succeed();
    }

    private static void removeChest(GameTestHelper h, BlockPos pos) {
        if (h.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(null, 0L);
            chest.clearContent();
        }
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }
    private static ItemStack worms(int count) { return new ItemStack(MiteFoods.get("raw_worm"), count); }
    private static float compost(ChestBlockEntity chest) { return chest.getPersistentData().getFloat(ChestWormComposting.COMPOST_KEY); }
    private static void near(GameTestHelper h, float actual, float expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .00001F, message + ": expected " + expected + ", got " + actual);
    }
    private static ChestBlockEntity chest(GameTestHelper h) {
        var chest = new ChestBlockEntity(h.absolutePos(new BlockPos(2, 3, 2)), Blocks.CHEST.defaultBlockState());
        chest.setLevel(h.getLevel()); return chest;
    }
    private static ChestBlockEntity full(GameTestHelper h) {
        ChestBlockEntity chest = chest(h);
        for (int slot = 0; slot < chest.getContainerSize(); ++slot) chest.setItem(slot, new ItemStack(Items.STONE, 64));
        return chest;
    }
    private static class WinningRandom extends LegacyRandomSource {
        final List<Integer> bounds = new ArrayList<>();
        WinningRandom() { super(196L); }
        @Override public int nextInt(int bound) { bounds.add(bound); return 0; }
    }
}
