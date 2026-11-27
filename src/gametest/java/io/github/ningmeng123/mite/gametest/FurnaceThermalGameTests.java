package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.furnace.FurnaceThermalMenuAccess;
import net.minecraft.recipebook.ServerPlaceRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native inventories, menus, ticks and NBT; expectations independently read from R196 asg/SlotFuel. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FurnaceThermalGameTests {
    private static final String[] SOURCE_FURNACES = {"mite:clay_oven", "mite:large_clay_oven", "mite:sandstone_furnace",
            "minecraft:furnace", "mite:obsidian_furnace", "mite:netherrack_furnace"};
    private FurnaceThermalGameTests() {}

    @GameTest(template = "empty")
    public static void allSixSourceFurnaceRecipesKeepExactShapesQuantitiesAndWholeBatchDifficulty(GameTestHelper helper) {
        Item[] materials = {Items.CLAY, Items.TERRACOTTA, Items.SANDSTONE, Items.COBBLESTONE, Items.OBSIDIAN, Items.NETHERRACK};
        float[] difficulty = {320F, 1440F, 640F, 1600F, 1920.0001F, 1280F};
        int[] sourceOrder = {310, 92, 93, 104, 94, 95};
        for (int index = 0; index < SOURCE_FURNACES.length; index++) {
            List<ItemStack> inputs = new ArrayList<>();
            int width = index == 0 ? 2 : 3;
            for (int slot = 0; slot < width * width; slot++) inputs.add(width == 3 && slot == 4 ? ItemStack.EMPTY : new ItemStack(materials[index]));
            CraftingInput input = CraftingInput.of(width, width, inputs);
            var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(recipe -> recipe.value().matches(input, helper.getLevel())).toList();
            helper.assertValueEqual(recipes.size(), 1, "The exact source furnace shape has one unambiguous registered recipe");
            ItemStack output = recipes.getFirst().value().assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(output.is(item(SOURCE_FURNACES[index])) && output.getCount() == 1,
                    "Source furnace ingredients produce one furnace with its stable mapped identity");
            helper.assertValueEqual(output.getMaxStackSize(), 1, "Each source furnace has the original stack limit one");
            var timing = TimedCraftingRecipes.get(recipes.getFirst().id());
            helper.assertTrue(timing != null, "Every furnace manufacturing recipe loads real timed rules");
            helper.assertValueEqual(timing.difficulty(), difficulty[index], "Whole-batch source manufacturing difficulty stays independent of tool quality");
            helper.assertTrue(timing.requiredBench() == null, "The source uses shape size rather than a material-specific bench requirement");
            helper.assertValueEqual(timing.sourceOrder(), sourceOrder[index], "Each manufacturing rule retains its source registration order");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceFuelBurnTimesReachNativeEventIncludingRecipeTypeNull(GameTestHelper helper) {
        Map<Item, Integer> expected = new LinkedHashMap<>();
        expected.put(Items.CHARCOAL, 1600); expected.put(Items.COAL, 1600); expected.put(Items.COAL_BLOCK, 16000);
        expected.put(Items.LAVA_BUCKET, 3200); expected.put(Items.BLAZE_ROD, 2400); expected.put(Items.PAPER, 25);
        expected.put(Items.STICK, 100); expected.put(Items.ARROW, 100); expected.put(Items.BOOK, 100);
        expected.put(Items.WRITABLE_BOOK, 100); expected.put(Items.WRITTEN_BOOK, 100); expected.put(Items.ENCHANTED_BOOK, 100);
        expected.put(Items.OAK_DOOR, 400); expected.put(Items.OAK_LOG, 1600); expected.put(Items.OAK_PLANKS, 400);
        expected.put(Items.OAK_BUTTON, 400); expected.put(Items.OAK_SLAB, 200); expected.put(Items.OAK_SAPLING, 200);
        expected.put(Items.DEAD_BUSH, 200); expected.put(Items.TORCH, 800); expected.put(Items.REDSTONE_TORCH, 800);
        expected.put(Items.OAK_BOAT, 1200); expected.put(Items.BOWL, 200); expected.put(item("mite:manure"), 100);
        expected.put(Items.MUSHROOM_STEW, 0); expected.put(item("mite:vegetable_soup"), 0);
        for (var entry : expected.entrySet()) for (RecipeType<?> type : new RecipeType<?>[]{null, RecipeType.SMELTING,
                RecipeType.BLASTING, RecipeType.SMOKING})
            helper.assertValueEqual(new ItemStack(entry.getKey()).getBurnTime(type), entry.getValue(),
                    "The actual NeoForge fuel path must use the independently extracted source duration: "
                            + BuiltInRegistries.ITEM.getKey(entry.getKey()) + " / " + type);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSixNativeFurnaceMenusAndSidedFuelSlotsRespectMaximumHeat(GameTestHelper helper) {
        Item[] fuels = {Items.CHARCOAL, Items.COAL, Items.LAVA_BUCKET, Items.BLAZE_ROD};
        int[] maximum = {1, 1, 1, 2, 3, 4};
        for (int stage = 0; stage < SOURCE_FURNACES.length; stage++) try (Fixture f = new Fixture(helper, SOURCE_FURNACES[stage])) {
            for (int index = 0; index < fuels.length; index++) {
                ItemStack fuel = new ItemStack(fuels[index]);
                boolean expected = index + 1 <= maximum[stage];
                helper.assertTrue(f.furnace.canPlaceItem(1, fuel) == expected,
                        "Container fuel insertion rejects heat above the source stage: " + SOURCE_FURNACES[stage]);
                helper.assertTrue(f.furnace.canPlaceItemThroughFace(1, fuel, Direction.WEST) == expected,
                        "The actual sided container cannot bypass the furnace stage");
                helper.assertTrue(f.menu.getSlot(1).mayPlace(fuel) == expected,
                        "The player's native fuel slot uses the same stage rule");
            }
            helper.assertTrue(!f.menu.getSlot(1).mayPlace(new ItemStack(Items.MUSHROOM_STEW)), "Filled food bowls cannot serve as wood fuel");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSixPlacedFurnacesKeepOriginalHardnessResistanceAndLitBrightness(GameTestHelper helper) {
        float[] strength = {.5F, 1F, 1F, 2F, 4F, 8F};
        for (int index = 0; index < SOURCE_FURNACES.length; index++) try (Fixture f = new Fixture(helper, SOURCE_FURNACES[index])) {
            BlockState cold = helper.getLevel().getBlockState(f.pos);
            helper.assertValueEqual(cold.getDestroySpeed(helper.getLevel(), f.pos), strength[index],
                    "The physically placed source furnace keeps its independently exported hardness");
            helper.assertValueEqual(cold.getBlock().getExplosionResistance(), strength[index],
                    "The physically placed source furnace keeps the actual source explosion resistance");
            if (index >= 3) helper.assertTrue(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(cold),
                    "A native wooden pickaxe cannot harvest a source metal-tier stone furnace");
            helper.assertValueEqual(cold.getLightEmission(helper.getLevel(), f.pos), 0, "A cold source furnace emits no block light");
            f.input(Items.BEEF, 1); f.fuel(Items.CHARCOAL, 1); f.tick(1);
            helper.assertValueEqual(helper.getLevel().getBlockState(f.pos).getLightEmission(helper.getLevel(), f.pos), 13,
                    "The actual lit-state block uses original brightness thirteen");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smallClayOvenRejectsLargeInputsAndLargeFuelAcrossGuiAndCapability(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mite:clay_oven")) {
            helper.assertTrue(!f.furnace.canPlaceItem(0, new ItemStack(Items.SAND)), "Sand blocks are too large for the small clay oven");
            helper.assertTrue(!f.menu.getSlot(0).mayPlace(new ItemStack(Items.SAND)), "The GUI cannot insert the large block input");
            helper.assertTrue(!f.menu.getSlot(1).mayPlace(new ItemStack(Items.OAK_LOG)), "The small oven cannot insert a large log fuel");
            helper.assertTrue(f.menu.getSlot(0).mayPlace(new ItemStack(Items.BEEF)), "Small food remains accepted");
            helper.assertTrue(f.menu.getSlot(1).mayPlace(new ItemStack(Items.STICK)), "Small wood fuel remains accepted");
            var top = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, f.pos, Direction.UP);
            var side = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, f.pos, Direction.WEST);
            helper.assertTrue(top != null && side != null, "The new furnace exposes actual item capabilities");
            helper.assertValueEqual(top.insertItem(0, new ItemStack(Items.SAND), false).getCount(), 1,
                    "A real capability insertion returns the rejected large input");
            helper.assertValueEqual(side.insertItem(0, new ItemStack(Items.COAL), false).getCount(), 1,
                    "A real capability insertion returns the rejected over-hot fuel");
            helper.assertTrue(f.furnace.getItem(0).isEmpty() && f.furnace.getItem(1).isEmpty(), "Rejected capability transfers do not mutate either slot");
            ItemStack clay = new ItemStack(Items.CLAY);
            helper.assertTrue(f.menu.getSlot(0).mayPlace(clay), "The small oven accepts clay blocks in its input slot");
            helper.assertTrue(top.insertItem(0, clay, false).isEmpty() && f.furnace.getItem(0).is(Items.CLAY),
                    "A real top capability can insert a clay block into the small oven");
        }
        for (String id : new String[]{"mite:large_clay_oven", "mite:sandstone_furnace"}) try (Fixture f = new Fixture(helper, id)) {
            helper.assertTrue(f.menu.getSlot(0).mayPlace(new ItemStack(Items.SAND)), "The source's two other ovens accept large sand inputs");
            helper.assertTrue(f.menu.getSlot(1).mayPlace(new ItemStack(Items.OAK_LOG)), "The source's two other ovens accept large log fuels");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insufficientHeatCannotStartOrConsumeFuel(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
            f.input(Items.IRON_ORE, 1); f.fuel(Items.CHARCOAL, 1); f.tick(240);
            helper.assertValueEqual(f.furnace.getItem(0).getCount(), 1, "A heat-one fuel cannot smelt a source heat-two ore");
            helper.assertValueEqual(f.furnace.getItem(1).getCount(), 1, "The unusable low-heat fuel is preserved");
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Insufficient temperature creates no result");
            helper.assertValueEqual(f.saved().getInt("BurnTime"), 0, "Insufficient temperature cannot start burning");
            helper.assertValueEqual(f.saved().getInt("CookTime"), 0, "Insufficient temperature cannot bank partial progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void forcedHotFuelCannotBypassStageDuringTheNativeServerTick(GameTestHelper helper) {
        for (String id : new String[]{"mite:clay_oven", "mite:sandstone_furnace", "minecraft:furnace", "mite:obsidian_furnace"})
            try (Fixture f = new Fixture(helper, id)) {
                f.input(Items.BEEF, 1); f.fuel(Items.BLAZE_ROD, 1); f.tick(240);
                helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Even an externally forced fuel cannot make the source furnace overheat: " + id);
                helper.assertValueEqual(f.furnace.getItem(1).getCount(), 1, "The illegal over-hot fuel is not silently consumed or clamped");
            }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oreHeatThresholdsAndDeepVariantsAreEnforcedByTheActualFurnace(GameTestHelper helper) {
        for (String prefix : new String[]{"", "deepslate_"}) {
            checkOre(helper, "minecraft:furnace", "minecraft:" + prefix + "iron_ore", Items.COAL, Items.IRON_INGOT);
            checkOre(helper, "mite:obsidian_furnace", "mite:" + prefix + "mithril_ore", Items.LAVA_BUCKET, item("mite:mithril_ingot"));
            checkOre(helper, "mite:netherrack_furnace", "mite:" + prefix + "adamantium_ore", Items.BLAZE_ROD, item("mite:adamantium_ingot"));
            try (Fixture f = new Fixture(helper, "mite:netherrack_furnace")) {
                f.input(item("mite:" + prefix + "adamantium_ore"), 1); f.fuel(Items.LAVA_BUCKET, 1); f.tick(240);
                helper.assertTrue(f.furnace.getItem(2).isEmpty() && f.furnace.getItem(1).is(Items.LAVA_BUCKET),
                        "A level-four furnace still requires level-four fuel for adamantium");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryBatchCompletesAtTwoHundredTicksWithoutOverheatAcceleration(GameTestHelper helper) {
        for (String id : new String[]{"minecraft:furnace", "mite:netherrack_furnace"}) try (Fixture f = new Fixture(helper, id)) {
            f.input(Items.BEEF, 1); f.fuel(id.startsWith("mite:") ? Items.BLAZE_ROD : Items.CHARCOAL, 1);
            f.tick(199);
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "No source batch completes early at any sufficient temperature");
            helper.assertValueEqual(f.saved().getInt("CookTime"), 199, "The native server maintains precisely 199 progress ticks");
            f.tick(1);
            helper.assertTrue(f.furnace.getItem(2).is(Items.COOKED_BEEF), "The two-hundredth source tick creates the food");
            helper.assertValueEqual(f.saved().getInt("CookTime"), 0, "A completed batch clears progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceCoalLavaAndBlazeFuelCookEightSixteenAndTwelveBatches(GameTestHelper helper) {
        checkFuelBatches(helper, "minecraft:furnace", Items.COAL, 8);
        checkFuelBatches(helper, "mite:obsidian_furnace", Items.LAVA_BUCKET, 16);
        checkFuelBatches(helper, "mite:netherrack_furnace", Items.BLAZE_ROD, 12);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twentyFiveTickPaperContinuesAcrossEightFuelItemsWithoutLosingProgress(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mite:clay_oven")) {
            f.input(item("mite:dough"), 1); f.fuel(Items.PAPER, 8); f.tick(199);
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Eight short fuel items cannot complete before the source two-hundredth tick");
            helper.assertValueEqual(f.saved().getInt("CookTime"), 199, "A seamless short-fuel refill preserves accumulated progress");
            f.tick(1);
            helper.assertTrue(f.furnace.getItem(2).is(Items.BREAD), "Eight papers supply the exact uninterrupted batch duration");
            helper.assertTrue(f.furnace.getItem(1).isEmpty(), "Exactly eight fuel items were consumed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualFuelInterruptionClearsRatherThanSlowlyDecaysCookingProgress(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mite:clay_oven")) {
            f.input(Items.BEEF, 1); f.fuel(Items.PAPER, 7); f.tick(175);
            helper.assertValueEqual(f.saved().getInt("CookTime"), 175, "Seven papers supply the independent 175-tick source vector");
            f.tick(1);
            helper.assertValueEqual(f.saved().getInt("CookTime"), 0, "A genuine one-tick interruption immediately clears source progress");
            f.fuel(Items.CHARCOAL, 1); f.tick(199);
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Refueling cannot reclaim discarded earlier progress");
            f.tick(1); helper.assertTrue(f.furnace.getItem(2).is(Items.COOKED_BEEF), "The fresh complete batch still succeeds");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceInputReplacementPreservesValidProgressButAnInvalidInputResetsOnTheNextTick(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
            f.input(Items.BEEF, 1); f.fuel(Items.CHARCOAL, 1); f.tick(100);
            f.input(Items.PORKCHOP, 1);
            helper.assertValueEqual(f.saved().getInt("CookTime"), 100, "Original asg setItem does not reset cooking progress merely because the input identity changes");
            f.tick(1);
            helper.assertValueEqual(f.saved().getInt("CookTime"), 101, "The next tick continues the new equally valid food at source progress101");
            f.input(Items.IRON_ORE, 1); f.tick(1);
            helper.assertValueEqual(f.saved().getInt("CookTime"), 0, "A now-invalid heat-one input is cleared by the actual next source tick");
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Input changes cannot create an uncompleted cooking result");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realRecipeBookInsertionCannotPutAnOversizeSandBlockIntoTheSmallOven(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mite:clay_oven")) {
            f.player.getInventory().setItem(0, new ItemStack(Items.SAND, 4));
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                    new SingleRecipeInput(new ItemStack(Items.SAND)), helper.getLevel()).orElseThrow();
            f.player.awardRecipes(List.of(recipe));
            new ServerPlaceRecipe<SingleRecipeInput, AbstractCookingRecipe>(f.menu).recipeClicked(f.player,
                    new RecipeHolder<AbstractCookingRecipe>(recipe.id(), recipe.value()), false);
            helper.assertTrue(f.furnace.getItem(0).isEmpty(), "The actual recipe book honors the small oven's input slot size guard");
            helper.assertValueEqual(f.player.getInventory().countItem(Items.SAND), 4, "A rejected real recipe-book insertion preserves every sand input in inventory");
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Recipe-book placement cannot manufacture a source output");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sandRequiresFourInputsAndChangesOutputWithTheBurningFuelHeat(GameTestHelper helper) {
        for (Item sand : new Item[]{Items.SAND, Items.RED_SAND}) {
            try (Fixture f = new Fixture(helper, "mite:sandstone_furnace")) {
                f.input(sand, 3); f.fuel(Items.CHARCOAL, 1); f.tick(240);
                helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Fewer than four sand cannot produce any source batch");
                helper.assertValueEqual(f.furnace.getItem(1).getCount(), 1, "An incomplete sand batch cannot spend fuel");
                f.input(sand, 4); f.tick(200);
                helper.assertTrue(f.furnace.getItem(2).is(sand == Items.RED_SAND ? Items.RED_SANDSTONE : Items.SANDSTONE),
                        "A heat-one sand batch creates sandstone with the explicitly retained modern red-sand identity");
                helper.assertTrue(f.furnace.getItem(0).isEmpty(), "One sandstone consumes exactly four sand");
            }
            try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
                f.input(sand, 4); f.fuel(Items.COAL, 1); f.tick(200);
                helper.assertTrue(f.furnace.getItem(2).is(Items.GLASS), "Heat-two changes the same four-sand batch to one glass");
                helper.assertValueEqual(f.furnace.getItem(2).getCount(), 1, "A four-sand batch cannot retain the vanilla four-glass yield");
                helper.assertTrue(f.furnace.getItem(0).isEmpty(), "One glass consumes exactly four sand");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sandstoneNeedsHeatTwoAndCanThenProduceGlass(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
            f.input(Items.SANDSTONE, 1); f.fuel(Items.CHARCOAL, 1); f.tick(200);
            helper.assertTrue(f.furnace.getItem(2).isEmpty() && f.furnace.getItem(1).is(Items.CHARCOAL), "Sandstone itself is a source heat-two recipe");
            f.fuel(Items.COAL, 1); f.tick(200);
            helper.assertTrue(f.furnace.getItem(2).is(Items.GLASS) && f.furnace.getItem(2).getCount() == 1, "One sandstone produces one glass at sufficient heat");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clayBallBatchesConvertAtMostFourAndRespectRemainingOutputCapacity(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mite:clay_oven")) {
            f.input(Items.CLAY_BALL, 5); f.fuel(Items.CHARCOAL, 1); f.tick(200);
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 4, "The independently read source five-clay first batch produces four bricks");
            helper.assertValueEqual(f.furnace.getItem(0).getCount(), 1, "The fifth clay waits for a second batch");
            f.tick(200); helper.assertValueEqual(f.furnace.getItem(2).getCount(), 5, "The next batch processes the remaining one clay");
        }
        try (Fixture f = new Fixture(helper, "mite:clay_oven")) {
            int max = new ItemStack(Items.BRICK).getMaxStackSize();
            f.furnace.setItem(2, new ItemStack(Items.BRICK, max - 2));
            f.input(Items.CLAY_BALL, 5); f.fuel(Items.CHARCOAL, 1); f.tick(200);
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), max, "Available output space limits the batch to two bricks");
            helper.assertValueEqual(f.furnace.getItem(0).getCount(), 3, "No clay is consumed for blocked excess bricks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void multipleOutputRecipesCannotOverflowOrBurnWithAnIncompatibleResult(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
            int max = new ItemStack(Items.LAPIS_LAZULI).getMaxStackSize();
            f.input(Items.LAPIS_ORE, 1); f.fuel(Items.COAL, 1); f.furnace.setItem(2, new ItemStack(Items.LAPIS_LAZULI, max - 3));
            f.tick(240);
            helper.assertValueEqual(f.furnace.getItem(1).getCount(), 1, "The modern safety adaptation rejects the whole four-unit output before spending fuel");
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), max - 3, "The source's old output overflow is not reproduced");
            f.furnace.setItem(2, new ItemStack(Items.REDSTONE)); f.tick(240);
            helper.assertTrue(f.furnace.getItem(1).is(Items.COAL) && f.furnace.getItem(0).is(Items.LAPIS_ORE), "An incompatible result also preserves input and fuel");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frontWaterOrFullSolidFaceExtinguishesImmediatelyWithoutConsumingTheNextFuel(GameTestHelper helper) {
        for (Block obstruction : new Block[]{Blocks.WATER, Blocks.STONE}) try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
            f.input(Items.BEEF, 1); f.fuel(Items.CHARCOAL, 2); f.tick(100);
            helper.assertValueEqual(f.saved().getInt("CookTime"), 100, "The open-front source furnace has genuine partial progress");
            helper.getLevel().setBlockAndUpdate(f.front, obstruction.defaultBlockState()); f.tick(1);
            helper.assertValueEqual(f.saved().getInt("BurnTime"), 0, "Front water or a flat solid face extinguishes the source furnace");
            helper.assertValueEqual(f.saved().getInt("CookTime"), 0, "Extinguishing clears cooking progress");
            helper.assertValueEqual(f.furnace.getItem(1).getCount(), 1, "The queued second fuel item survives obstruction");
            helper.assertTrue(!helper.getLevel().getBlockState(f.pos).getValue(AbstractFurnaceBlock.LIT), "The real block's lit state is synchronized to extinguished");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyTheFrontFaceSmothersAndLastBurnTickRetainsItsOriginalException(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
            helper.getLevel().setBlockAndUpdate(f.back, Blocks.STONE.defaultBlockState());
            f.input(Items.BEEF, 1); f.fuel(Items.CHARCOAL, 1); f.tick(200);
            helper.assertTrue(f.furnace.getItem(2).is(Items.COOKED_BEEF), "A solid block behind the furnace does not smother the source front opening");
        }
        try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
            f.input(Items.BEEF, 1); f.fuel(Items.PAPER, 1); f.tick(25);
            helper.assertValueEqual(f.saved().getInt("BurnTime"), 1, "Twenty-five source cooking ticks leave the terminal burnTime-one state");
            f.fuel(Items.CHARCOAL, 1); helper.getLevel().setBlockAndUpdate(f.front, Blocks.STONE.defaultBlockState());
            f.tick(1);
            helper.assertValueEqual(f.saved().getInt("CookTime"), 26, "The source's terminal-tick exception allows same-tick refill before the next obstruction check");
            helper.assertTrue(f.furnace.getItem(1).isEmpty(), "The terminal exception spends exactly the one newly queued fuel");
            f.tick(1);
            helper.assertValueEqual(f.saved().getInt("BurnTime"), 0, "The next non-terminal tick performs the real obstruction check");
            helper.assertValueEqual(f.saved().getInt("CookTime"), 0, "The next tick clears the source progress after that exception");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedHighHeatAndConsumedBucketFuelSurviveBlockEntityReload(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mite:obsidian_furnace")) {
            f.input(item("mite:mithril_ore"), 1); f.fuel(Items.LAVA_BUCKET, 1); f.tick(123);
            helper.assertTrue(f.furnace.getItem(1).is(Items.BUCKET), "The source lava fuel returns its empty bucket on ignition");
            int burnBefore = f.saved().getInt("BurnTime");
            float fuelFractionBefore = f.menu.getLitProgress();
            f.reload();
            helper.assertValueEqual(f.saved().getInt("BurnTime"), burnBefore, "Native block-entity loading preserves remaining burn ticks");
            helper.assertValueEqual(f.menu.getLitProgress(), fuelFractionBefore,
                    "Native menu loading preserves the spent lava fuel's duration even though an empty bucket occupies the fuel slot");
            helper.assertValueEqual(f.saved().getInt("CookTime"), 123, "Native loading preserves the in-progress source batch");
            f.tick(76); helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Reload cannot prematurely finish the batch");
            f.tick(1);
            helper.assertTrue(f.furnace.getItem(2).is(item("mite:mithril_ingot")), "Stored heat-three continues to smelt even though only an empty bucket remains in fuel");
            helper.assertTrue(f.furnace.getItem(1).is(Items.BUCKET), "Continued cooking cannot consume the remainder bucket");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allFiveNewFurnacesLoadUnderTheirOwnRegisteredTypeWithInventoryAndProgress(GameTestHelper helper) {
        for (String id : SOURCE_FURNACES) try (Fixture f = new Fixture(helper, id)) {
            f.input(Items.BEEF, 2); f.fuel(Items.CHARCOAL, 2); f.tick(77);
            String type = f.furnace.saveWithFullMetadata(helper.getLevel().registryAccess()).getString("id");
            helper.assertTrue(type.equals(id.startsWith("mite:") ? "mite:r196_furnace" : "minecraft:furnace"),
                    "New source furnaces retain a compatible own block-entity registry identity");
            f.reload();
            helper.assertValueEqual(f.furnace.getItem(0).getCount(), 2, "The correct furnace inventory reloads");
            helper.assertValueEqual(f.furnace.getItem(1).getCount(), 1, "The queued source fuel reloads once");
            f.tick(123);
            helper.assertTrue(f.furnace.getItem(2).is(Items.COOKED_BEEF) && f.furnace.getItem(2).getCount() == 1,
                    "The correctly typed new block entity resumes precisely the source batch");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernBlastFurnaceSharesHeatLimitAndCannotSmeltMithrilWithOrdinaryCoal(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "minecraft:blast_furnace")) {
            f.input(item("mite:mithril_ore"), 1); f.fuel(Items.COAL, 1); f.tick(240);
            helper.assertTrue(f.furnace.getItem(2).isEmpty() && f.furnace.getItem(1).is(Items.COAL), "The modern blast furnace cannot bypass the source mithril heat requirement");
            helper.assertTrue(!f.menu.getSlot(1).mayPlace(new ItemStack(Items.LAVA_BUCKET)), "A modern blast furnace retains the ordinary stone furnace's heat-two capacity");
            f.input(Items.IRON_ORE, 1); f.tick(100);
            helper.assertTrue(f.furnace.getItem(2).is(Items.IRON_INGOT), "Modern blasting remains useful for source heat-two ores at its explicit faster duration");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernSmokerUsesSourceFuelAndSavedHeatWithoutVanillaFuelDurationHalving(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "minecraft:smoker")) {
            f.input(Items.BEEF, 16); f.fuel(Items.CHARCOAL, 1); f.tick(100);
            helper.assertTrue(f.furnace.getItem(2).is(Items.COOKED_BEEF), "The explicitly adapted modern smoker retains a hundred-tick food batch");
            helper.assertValueEqual(f.saved().getInt("BurnTime"), 1501, "All furnace types keep the real source charcoal duration instead of modern smoker halving");
            f.reload(); f.tick(1500);
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 16, "One source charcoal cooks sixteen batches with the modern smoker's retained speed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sideCapabilitiesAndShiftInsertionRejectOverhotFuelWithoutDestroyingIt(GameTestHelper helper) {
        for (String id : SOURCE_FURNACES) try (Fixture f = new Fixture(helper, id)) {
            var side = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, f.pos, Direction.WEST);
            helper.assertTrue(side != null, "Every source furnace has a real side item capability");
            Item fuel = id.equals("mite:netherrack_furnace") ? Items.CHARCOAL : Items.BLAZE_ROD;
            boolean legal = id.equals("mite:netherrack_furnace");
            ItemStack remainder = side.insertItem(0, new ItemStack(fuel), false);
            helper.assertTrue(remainder.isEmpty() == legal, "Actual capability insertion applies the same source maximum heat");
            f.furnace.setItem(1, ItemStack.EMPTY);
            f.player.getInventory().setItem(0, new ItemStack(fuel));
            f.menu.quickMoveStack(f.player, 30);
            helper.assertTrue(f.furnace.getItem(1).isEmpty() != legal, "Actual shift insertion uses the same stage rule");
            helper.assertValueEqual(f.player.getInventory().countItem(fuel) + f.furnace.getItem(1).getCount(), 1,
                    "An accepted or rejected real shift transfer conserves the one source fuel");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bottomFuelExtractionAcceptsOnlyActualEmptyBucketsIncludingMiteMetalPeers(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mite:obsidian_furnace")) {
            helper.assertTrue(f.furnace.canTakeItemThroughFace(1, new ItemStack(Items.BUCKET), Direction.DOWN), "An empty source bucket may leave the fuel slot below");
            helper.assertTrue(!f.furnace.canTakeItemThroughFace(1, new ItemStack(Items.WATER_BUCKET), Direction.DOWN), "A water bucket is not an empty source fuel remainder");
            helper.assertTrue(!f.furnace.canTakeItemThroughFace(1, new ItemStack(Items.COAL), Direction.DOWN), "The bottom fuel view does not drain unburned coal");
            for (String metal : new String[]{"copper", "silver", "gold", "mithril", "adamantium", "ancient_metal"}) {
                helper.assertTrue(f.furnace.canTakeItemThroughFace(1, new ItemStack(item("mite:" + metal + "_bucket")), Direction.DOWN),
                        "Every registered MITE metal empty bucket uses the source remainder rule");
                helper.assertTrue(!f.furnace.canTakeItemThroughFace(1, new ItemStack(item("mite:" + metal + "_water_bucket")), Direction.DOWN),
                        "A filled MITE metal bucket cannot leave the fuel view below");
            }
            f.furnace.setItem(1, new ItemStack(Items.WATER_BUCKET));
            var bottom = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, f.pos, Direction.DOWN);
            helper.assertTrue(bottom != null && bottom.getSlots() == 2, "The actual bottom capability exposes output and fuel views");
            helper.assertTrue(bottom.extractItem(1, 1, false).isEmpty(), "A real bottom capability cannot extract the filled fuel bucket");
            f.furnace.setItem(1, new ItemStack(Items.BUCKET));
            helper.assertTrue(bottom.extractItem(1, 1, false).is(Items.BUCKET), "The actual capability extracts the empty fuel remainder once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyAddedMetalLavaBucketBurnsAtHeatThreeAndPreservesItsOwnEmptyPeer(GameTestHelper helper) {
        for (String metal : new String[]{"copper", "silver", "gold", "mithril", "adamantium", "ancient_metal"}) {
            try (Fixture f = new Fixture(helper, "mite:obsidian_furnace")) {
                Item lava = item("mite:" + metal + "_lava_bucket");
                Item empty = item("mite:" + metal + "_bucket");
                helper.assertValueEqual(new ItemStack(lava).getBurnTime(RecipeType.SMELTING), 3200,
                        "Every metal lava vessel supplies the independently extracted source 3200 ticks");
                f.input(item("mite:mithril_ore"), 1); f.fuel(lava, 1); f.tick(200);
                helper.assertTrue(f.furnace.getItem(2).is(item("mite:mithril_ingot")), "Each actual metal lava vessel supplies source heat three to the real engine");
                helper.assertTrue(f.furnace.getItem(1).is(empty), "The actually consumed lava vessel preserves its own metal as remainder");
                var bottom = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, f.pos, Direction.DOWN);
                helper.assertTrue(bottom.extractItem(1, 1, false).is(empty), "A real bottom capability extracts this source empty bucket peer");
                f.reload();
                helper.assertTrue(f.furnace.getItem(1).isEmpty(), "A spent and extracted metal bucket cannot reappear after actual block-entity loading");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeMenuDataSyncTransmitsCurrentCapacityAndRequiredHeatToTheClientCopy(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mite:obsidian_furnace")) {
            f.input(item("mite:mithril_ore"), 1); f.fuel(Items.LAVA_BUCKET, 1);
            // A native client-side furnace menu has a SimpleContainer, rather than a server block entity.
            FurnaceMenu clientCopy = new FurnaceMenu(87, f.player.getInventory());
            helper.assertTrue(clientCopy instanceof FurnaceThermalMenuAccess, "The real ordinary client menu carries the synchronized thermal interface");
            f.menu.setSynchronizer(new ContainerSynchronizer() {
                @Override public void sendInitialData(AbstractContainerMenu serverMenu, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
                    helper.assertTrue(data.length >= 7, "The native initial-data stream contains four progress values and three thermal values");
                    for (int index = 0; index < data.length; index++) clientCopy.setData(index, data[index]);
                }
                @Override public void sendSlotChange(AbstractContainerMenu serverMenu, int slot, ItemStack item) {}
                @Override public void sendCarriedChange(AbstractContainerMenu serverMenu, ItemStack item) {}
                @Override public void sendDataChange(AbstractContainerMenu serverMenu, int index, int value) { clientCopy.setData(index, value); }
            });
            FurnaceThermalMenuAccess client = (FurnaceThermalMenuAccess) clientCopy;
            helper.assertValueEqual(client.mite$getHeat(), 0, "The initial cold furnace temperature is authoritative");
            helper.assertValueEqual(client.mite$getMaxHeat(), 3, "The ordinary client menu displays the new obsidian block's real capacity");
            helper.assertValueEqual(client.mite$getRequiredHeat(), 3, "The initial source mithril input requirement reaches the client");
            helper.assertTrue(clientCopy.getSlot(1).mayPlace(new ItemStack(Items.LAVA_BUCKET)),
                    "The client fuel slot accepts level-three fuel after actual capacity synchronization");
            helper.assertTrue(!clientCopy.getSlot(1).mayPlace(new ItemStack(Items.BLAZE_ROD)),
                    "The same synchronized client slot refuses level-four fuel");
            f.tick(1); f.menu.broadcastChanges();
            helper.assertValueEqual(client.mite$getHeat(), 3, "Ignition updates the native client-data copy to the actual burning lava heat");
            f.input(Items.BEEF, 1); f.menu.broadcastChanges();
            helper.assertValueEqual(client.mite$getRequiredHeat(), 1, "Changing source input immediately updates required heat without a custom client guess");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernWetSpongeUsesOneEmptyBucketAndConservesAllAdditionalMetalBuckets(GameTestHelper helper) {
        for (String emptyId : new String[]{"minecraft:bucket", "mite:copper_bucket", "mite:adamantium_bucket"})
            for (int buckets : new int[]{1, 8}) try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
                Item empty = item(emptyId);
                Item water = item(emptyId.replace("_bucket", "_water_bucket").equals(emptyId)
                        ? "minecraft:water_bucket" : emptyId.replace("_bucket", "_water_bucket"));
                f.input(Items.WET_SPONGE, 1); f.fuel(Items.CHARCOAL, 1); f.tick(1);
                helper.assertTrue(f.menu.getSlot(1).mayPlace(new ItemStack(empty, buckets)), "A modern wet-sponge input explicitly permits an empty bucket without classifying it as combustible");
                f.fuel(empty, buckets); f.tick(199);
                helper.assertTrue(f.furnace.getItem(2).is(Items.SPONGE), "The actual retained modern wet-sponge recipe produces one dry sponge");
                int emptyRemaining = f.furnace.getItem(1).is(empty) ? f.furnace.getItem(1).getCount() : 0;
                int filled = f.furnace.getItem(1).is(water) ? f.furnace.getItem(1).getCount() : 0;
                for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, f.area))
                    if (!f.oldDrops.contains(drop.getUUID()) && drop.getItem().is(water)) filled += drop.getItem().getCount();
                helper.assertValueEqual(emptyRemaining, buckets - 1, "The actual modern compatibility path consumes exactly one source empty vessel from an eight-bucket stack");
                helper.assertValueEqual(filled, 1, "Exactly one matching metal water vessel is produced without swallowing or duplicating the other empties");
            }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeClientMenuDataAlsoTransfersSmallOvenLargeItemRestrictions(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "mite:clay_oven")) {
            FurnaceMenu clientCopy = new FurnaceMenu(87, f.player.getInventory());
            f.menu.setSynchronizer(new ContainerSynchronizer() {
                @Override public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
                    helper.assertTrue(data.length >= 9, "The ordinary menu's native data stream also carries source large-item and managed-furnace flags");
                    for (int index = 0; index < data.length; index++) clientCopy.setData(index, data[index]);
                }
                @Override public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack item) {}
                @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack item) {}
                @Override public void sendDataChange(AbstractContainerMenu menu, int index, int value) { clientCopy.setData(index, value); }
            });
            helper.assertValueEqual(((FurnaceThermalMenuAccess) clientCopy).mite$getMaxHeat(), 1, "The actual client copy sees the small oven's source capacity");
            helper.assertTrue(!clientCopy.getSlot(0).mayPlace(new ItemStack(Items.SAND)), "A synced client menu rejects a too-large input before sending a click");
            helper.assertTrue(!clientCopy.getSlot(1).mayPlace(new ItemStack(Items.OAK_LOG)), "The synced client fuel slot also rejects too-large wood fuel");
            helper.assertTrue(clientCopy.getSlot(0).mayPlace(new ItemStack(Items.BEEF)), "The synced client input still accepts the small valid source food");
            helper.assertTrue(clientCopy.getSlot(1).mayPlace(new ItemStack(Items.CHARCOAL)), "The synced client fuel still accepts small source charcoal");
        }
        helper.succeed();
    }

    private static void checkOre(GameTestHelper helper, String furnace, String input, Item fuel, Item result) {
        try (Fixture f = new Fixture(helper, furnace)) {
            f.input(item(input), 1); f.fuel(fuel, 1); f.tick(200);
            helper.assertTrue(f.furnace.getItem(2).is(result) && f.furnace.getItem(2).getCount() == 1,
                    "The actual furnace applies source ore thresholds to " + input);
            helper.assertTrue(f.furnace.getItem(0).isEmpty(), "The genuine ore batch consumes one input");
        }
    }
    private static void checkFuelBatches(GameTestHelper helper, String furnace, Item fuel, int batches) {
        try (Fixture f = new Fixture(helper, furnace)) {
            // Each source cactus stack now holds four. Supply and collect legal
            // batches without spending extra game ticks between completed cycles.
            f.fuel(fuel, 1);
            int collected = 0;
            for (int batch = 0; batch < batches; batch++) {
                f.input(Items.CACTUS, 1);
                f.tick(199);
                helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Each ordinary source batch still needs the full two hundred cooking ticks");
                helper.assertValueEqual(f.furnace.getItem(0).getCount(), 1, "A legal single cactus remains until its complete source cycle");
                f.tick(1);
                ItemStack extracted = f.furnace.removeItem(2, 1);
                helper.assertTrue(extracted.is(Items.GREEN_DYE) && extracted.getCount() == 1,
                        "Each completed real source cycle yields one legally extracted green dye");
                collected += extracted.getCount();
                helper.assertTrue(f.furnace.getItem(0).isEmpty() && f.furnace.getItem(2).isEmpty(),
                        "Completed inputs are consumed once and collecting output leaves room for the next legal batch");
                helper.assertValueEqual(f.saved().getInt("BurnTime"), (batches - batch - 1) * 200 + 1,
                        "Source coal1600, lava3200 and blaze2400 keep their independently extracted remaining fuel duration");
            }
            helper.assertValueEqual(collected, batches, "The source fuel supports exactly the independently extracted eight, sixteen or twelve full ordinary batches");
            f.input(Items.CACTUS, 1);
            f.tick(200);
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The exhausted source fuel cannot fund an extra batch");
            helper.assertValueEqual(f.furnace.getItem(0).getCount(), 1, "The unfunded next input is preserved");
            helper.assertValueEqual(f.saved().getInt("BurnTime"), 0, "The single original fuel is exhausted without any refill");
            helper.assertValueEqual(f.saved().getInt("CookTime"), 0, "The unfunded next batch cannot retain cooking progress");
        }
    }
    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) throw new IllegalArgumentException("Unregistered source test item: " + id);
        return item;
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos pos, front, back;
        final BlockState old, oldFront, oldBack;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final AABB area;
        final Set<UUID> oldDrops = new HashSet<>();
        AbstractFurnaceBlockEntity furnace;
        AbstractFurnaceMenu menu;
        Fixture(GameTestHelper helper, String id) {
            this.helper = helper;
            pos = helper.absolutePos(new BlockPos(2, 2, 2)); front = pos.north(); back = pos.south();
            old = helper.getLevel().getBlockState(pos); oldFront = helper.getLevel().getBlockState(front); oldBack = helper.getLevel().getBlockState(back);
            area = new AABB(pos).inflate(5);
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(drop -> oldDrops.add(drop.getUUID()));
            helper.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(back, Blocks.AIR.defaultBlockState());
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            helper.assertTrue(block instanceof AbstractFurnaceBlock, "Every thermal test uses a genuinely registered source furnace");
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity, "A placed source furnace creates its real block entity");
            furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-thermal"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            new ServerGamePacketListenerImpl(player.server, connection, player, cookie) { @Override public void send(Packet<?> packet) {} };
            player.moveTo(pos.getCenter()); player.getInventory().clearContent(); open();
        }
        void open() { menu = (AbstractFurnaceMenu) furnace.createMenu(87, player.getInventory(), player); player.containerMenu = menu; }
        void input(Item item, int count) { furnace.setItem(0, new ItemStack(item, count)); }
        void fuel(Item item, int count) { furnace.setItem(1, new ItemStack(item, count)); }
        void tick(int count) { for (int i = 0; i < count; i++) AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), furnace); }
        CompoundTag saved() { return furnace.saveWithoutMetadata(helper.getLevel().registryAccess()); }
        void reload() {
            CompoundTag saved = furnace.saveWithFullMetadata(helper.getLevel().registryAccess());
            furnace = (AbstractFurnaceBlockEntity) BlockEntity.loadStatic(pos, helper.getLevel().getBlockState(pos), saved, helper.getLevel().registryAccess());
            helper.assertTrue(furnace != null, "Native serialized furnace metadata must resolve its registered type");
            helper.getLevel().setBlockEntity(furnace); open();
        }
        @Override public void close() {
            try {
                player.containerMenu = player.inventoryMenu; player.getInventory().clearContent(); player.getTextFilter().leave();
                furnace.clearContent();
                helper.getLevel().setBlockAndUpdate(pos, old);
                helper.getLevel().setBlockAndUpdate(front, oldFront); helper.getLevel().setBlockAndUpdate(back, oldBack);
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().filter(drop -> !oldDrops.contains(drop.getUUID())).forEach(ItemEntity::discard);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}
