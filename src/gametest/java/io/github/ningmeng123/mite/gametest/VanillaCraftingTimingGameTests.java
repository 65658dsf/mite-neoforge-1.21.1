package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipes;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded vanilla recipes must use the R196 clock through every real extraction path. @author NingMeng123 */
@GameTestHolder("mite_crafting_audit")
@PrefixGameTestTemplate(false)
public final class VanillaCraftingTimingGameTests {
    private record RecipeCase(String id, Item output, int outputCount, float difficulty,
            int[] slots, Item[] ingredients) {}

    private static RecipeCase chest() {
        return planks("chest", Items.CHEST, 1, 640F, 0, 1, 2, 3, 5, 6, 7, 8);
    }

    private static RecipeCase stick(boolean workbench) {
        return planks("stick", Items.STICK, 4, 160F, 0, workbench ? 3 : 2);
    }

    private static RecipeCase bowl() {
        return planks("bowl", Items.BOWL, 4, 240F, 0, 2, 4);
    }

    private static RecipeCase planks(String id, Item output, int count, float difficulty, int... slots) {
        Item[] inputs = new Item[slots.length];
        java.util.Arrays.fill(inputs, Items.OAK_PLANKS);
        return new RecipeCase(id, output, count, difficulty, slots, inputs);
    }

    private static RecipeCase bed(String color) {
        Item wool = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color + "_wool"));
        Item output = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color + "_bed"));
        return new RecipeCase(color + "_bed", output, 1, 480F, new int[]{0, 1, 2, 3, 4, 5},
                new Item[]{wool, wool, wool, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS});
    }

    private static List<RecipeCase> workbenchCases() {
        return List.of(chest(), stick(true), bowl(), bed("white"));
    }

    private static RecipeCase stone(String id, Item input, Item output, int count, float difficulty, int... slots) {
        Item[] ingredients = new Item[slots.length];
        java.util.Arrays.fill(ingredients, input);
        return new RecipeCase(id, output, count, difficulty, slots, ingredients);
    }

    private static List<RecipeCase> deepslateCases() {
        List<RecipeCase> result = new ArrayList<>();
        for (String family : List.of("cobbled_deepslate", "polished_deepslate", "deepslate_brick", "deepslate_tile")) {
            String block = family.equals("deepslate_brick") || family.equals("deepslate_tile") ? family + "s" : family;
            Item input = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(block));
            for (String shape : List.of("slab", "stairs", "wall")) {
                String id = family + "_" + shape;
                Item output = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(id));
                result.add(switch (shape) {
                    case "slab" -> stone(id, input, output, 6, 600F, 0, 1, 2);
                    case "stairs" -> stone(id, input, output, 4, 1200F, 0, 3, 4, 6, 7, 8);
                    default -> stone(id, input, output, 8, 1200F, 0, 1, 2, 3, 4, 5);
                });
            }
        }
        result.add(stone("polished_deepslate", Items.COBBLED_DEEPSLATE, Items.POLISHED_DEEPSLATE, 4, 960.00006F, 0, 1, 3, 4));
        result.add(stone("deepslate_bricks", Items.POLISHED_DEEPSLATE, Items.DEEPSLATE_BRICKS, 4, 960.00006F, 0, 1, 3, 4));
        result.add(stone("deepslate_tiles", Items.DEEPSLATE_BRICKS, Items.DEEPSLATE_TILES, 4, 960.00006F, 0, 1, 3, 4));
        result.add(stone("chiseled_deepslate", Items.COBBLED_DEEPSLATE_SLAB, Items.CHISELED_DEEPSLATE, 1, 100F, 0, 3));
        return result;
    }

    @GameTest(template = "empty")
    public static void modernWorkstationsWaitForManufactureAcrossRealExtractionPaths(GameTestHelper helper) throws Exception {
        try (var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(
                VanillaCraftingTimingGameTests.class.getResourceAsStream("/mite/timed-crafting-modern-workstations.json")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            var rows = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("recipes");
            for (var row : rows.entrySet()) {
                var id = ResourceLocation.parse(row.getKey());
                var holder = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
                var actual = holder.value();
                var output = actual.getResultItem(helper.getLevel().registryAccess());
                int width = actual instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
                var slots = new ArrayList<Integer>();
                var ingredients = new ArrayList<Item>();
                for (int index = 0; index < actual.getIngredients().size(); index++) {
                    var ingredient = actual.getIngredients().get(index);
                    if (ingredient.isEmpty()) continue;
                    slots.add(index / width * 3 + index % width);
                    ingredients.add(ingredient.getItems()[0].getItem());
                }
                var recipe = new RecipeCase(id.getPath(), output.getItem(), output.getCount(),
                        row.getValue().getAsJsonObject().get("difficulty").getAsFloat(),
                        slots.stream().mapToInt(Integer::intValue).toArray(), ingredients.toArray(Item[]::new));
                checkOrdinary(helper, recipe, true);
                checkContinuous(helper, recipe, true);
                if (output.is(Items.GRINDSTONE) || output.is(Items.SMITHING_TABLE)) {
                    checkQuickMove(helper, recipe, true);
                    checkOtherClicks(helper, recipe, true);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cobblestoneWallVariantsProduceEightOnlyAfterTheCompletePeriod(GameTestHelper helper) {
        checkOrdinary(helper, stone("cobblestone_wall", Items.COBBLESTONE, Items.COBBLESTONE_WALL, 8,
                1200F, 0, 1, 2, 3, 4, 5), true);
        checkContinuous(helper, stone("mossy_cobblestone_wall", Items.MOSSY_COBBLESTONE, Items.MOSSY_COBBLESTONE_WALL, 8,
                1200F, 0, 1, 2, 3, 4, 5), true);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernStoneWallsWaitForTheCompleteCobblestoneWallPeriod(GameTestHelper helper) {
        checkOrdinary(helper, stone("andesite_wall", Items.ANDESITE, Items.ANDESITE_WALL, 8,
                1200F, 0, 1, 2, 3, 4, 5), true);
        checkContinuous(helper, stone("diorite_wall", Items.DIORITE, Items.DIORITE_WALL, 8,
                1200F, 0, 1, 2, 3, 4, 5), true);
        checkOrdinary(helper, stone("tuff_wall", Items.TUFF, Items.TUFF_WALL, 8,
                1200F, 0, 1, 2, 3, 4, 5), true);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyNewStoneShapeWaitsForNormalAndShiftManufacturing(GameTestHelper helper) throws Exception {
        checkSupplementalStoneRecipes(helper, true);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyNewStoneProcessingRecipeWaitsForNormalAndShiftManufacturing(GameTestHelper helper) throws Exception {
        checkSupplementalStoneRecipes(helper, false);
        // Small stone controls remain available in the real inventory grid, with its own speed.
        checkOrdinary(helper, stone("polished_blackstone_button", Items.POLISHED_BLACKSTONE,
                Items.POLISHED_BLACKSTONE_BUTTON, 1, 240.00002F, 0), false);
        checkContinuous(helper, stone("polished_blackstone_pressure_plate", Items.POLISHED_BLACKSTONE,
                Items.POLISHED_BLACKSTONE_PRESSURE_PLATE, 1, 480.00003F, 0, 1), false);
        helper.succeed();
    }

    private static void checkSupplementalStoneRecipes(GameTestHelper helper, boolean shapes) throws Exception {
        var data = StoneCraftingCoverageGameTests.supplemental();
        int checked = 0;
        for (var row : data.entrySet()) {
            var id = ResourceLocation.parse(row.getKey());
            boolean shape = id.getPath().endsWith("_wall") || id.getPath().endsWith("_stairs") || id.getPath().endsWith("_slab");
            if (shape != shapes) continue;
            var holder = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
            var actual = holder.value();
            var output = actual.getResultItem(helper.getLevel().registryAccess());
            var slots = new ArrayList<Integer>();
            var ingredients = new ArrayList<Item>();
            int width = actual instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            for (int index = 0; index < actual.getIngredients().size(); index++) {
                var ingredient = actual.getIngredients().get(index);
                if (ingredient.isEmpty()) continue;
                helper.assertTrue(ingredient.getItems().length > 0, "The loaded stone recipe has a real ingredient: " + id);
                slots.add(index / width * 3 + index % width);
                ingredients.add(ingredient.getItems()[0].getItem());
            }
            var recipe = new RecipeCase(id.getPath(), output.getItem(), output.getCount(),
                    row.getValue().getAsJsonObject().get("difficulty").getAsFloat(),
                    slots.stream().mapToInt(Integer::intValue).toArray(), ingredients.toArray(Item[]::new));
            // Exercise both click modes for every newly covered loaded recipe. The same
            // real native menu checks N-1, payment, full-batch output and repeat limits.
            checkOrdinary(helper, recipe, true);
            checkContinuous(helper, recipe, true);
            checked++;
        }
        helper.assertTrue(checked > 20, "The matrix covers a substantial loaded stone operation family");
    }

    @GameTest(template = "empty")
    public static void everyLoadedDeepslateCraftWaitsAndItsViewerShowsTheSameTime(GameTestHelper helper) {
        var recipes = deepslateCases();
        long loaded = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(holder -> BuiltInRegistries.ITEM.getKey(holder.value().getResultItem(helper.getLevel().registryAccess())
                        .getItem()).getPath().contains("deepslate")).count();
        helper.assertValueEqual(loaded, (long) recipes.size(), "The cases cover every loaded deepslate crafting output");
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        for (RecipeCase recipe : recipes) {
            checkOrdinary(helper, recipe, true);
            var page = pages.stream().filter(value -> value.source().id().equals(ResourceLocation.withDefaultNamespace(recipe.id))
                    && value.workstationId().equals("mite:flint_crafting_table")).findFirst().orElseThrow();
            helper.assertValueEqual(page.baselineTicks(), TimedCraftingRules.period(recipe.difficulty, 0, .2F, false),
                    "JEI exposes the same nonzero whole-batch period as the real menu: " + recipe.id);
            helper.assertValueEqual(page.output().getCount(), recipe.outputCount, "Deepslate keeps its real output batch");
        }
        checkContinuous(helper, recipes.getFirst(), true);
        checkQuickMove(helper, recipes.get(1), true);
        checkOtherClicks(helper, recipes.get(2), true);
        checkOrdinary(helper, stone("polished_deepslate", Items.COBBLED_DEEPSLATE, Items.POLISHED_DEEPSLATE,
                4, 960.00006F, 0, 1, 2, 3), false);
        checkOrdinary(helper, stone("chiseled_deepslate", Items.COBBLED_DEEPSLATE_SLAB, Items.CHISELED_DEEPSLATE,
                1, 100F, 0, 2), false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryClicksWaitForTheWholeR196PeriodAndProduceOneBatch(GameTestHelper helper) {
        for (RecipeCase recipe : workbenchCases()) checkOrdinary(helper, recipe, true);
        checkOrdinary(helper, stick(false), false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyVanillaBedColorUsesTheSameR196ManufacturingDifficulty(GameTestHelper helper) {
        for (String color : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
                "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            checkOrdinary(helper, bed(color), true);
        }
        helper.succeed();
    }

    private static void checkOrdinary(GameTestHelper helper, RecipeCase recipe, boolean workbench) {
        try (Fixture fixture = new Fixture(helper, recipe, workbench, 2)) {
            fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
            fixture.assertAmounts(0);
            helper.assertTrue(TimedCraftingController.isActive(fixture.player), "An ordinary result click starts manufacturing");
            fixture.finishBatch(0);
            fixture.tick(fixture.period * 2);
            fixture.assertAmounts(1);
            helper.assertFalse(TimedCraftingController.isActive(fixture.player), "An ordinary click cannot repeat a remaining recipe");
        }
    }

    @GameTest(template = "empty")
    public static void shiftClicksConsumeOneBatchOnlyAtEachCompletePeriod(GameTestHelper helper) {
        for (RecipeCase recipe : workbenchCases()) checkContinuous(helper, recipe, true);
        checkContinuous(helper, stick(false), false);
        helper.succeed();
    }

    private static void checkContinuous(GameTestHelper helper, RecipeCase recipe, boolean workbench) {
        try (Fixture fixture = new Fixture(helper, recipe, workbench, 3)) {
            fixture.menu.clicked(0, 0, ClickType.QUICK_MOVE, fixture.player);
            fixture.assertAmounts(0);
            for (int completed = 0; completed < 3; completed++) fixture.finishBatch(completed);
            fixture.tick(fixture.period * 2);
            fixture.assertAmounts(3);
            helper.assertFalse(TimedCraftingController.isActive(fixture.player), "Continuous manufacturing ends after the last input batch");
        }
    }

    @GameTest(template = "empty")
    public static void directQuickMoveCannotReturnOrConsumeAnInstantVanillaResult(GameTestHelper helper) {
        for (RecipeCase recipe : workbenchCases()) checkQuickMove(helper, recipe, true);
        checkQuickMove(helper, stick(false), false);
        helper.succeed();
    }

    private static void checkQuickMove(GameTestHelper helper, RecipeCase recipe, boolean workbench) {
        try (Fixture fixture = new Fixture(helper, recipe, workbench, 2)) {
            helper.assertTrue(fixture.menu.quickMoveStack(fixture.player, 0).isEmpty(),
                    "Calling the real menu quickMoveStack cannot return an unfinished result");
            fixture.assertAmounts(0);
            fixture.finishBatch(0);
            fixture.tick(fixture.period * 2);
            fixture.assertAmounts(1);
        }
    }

    @GameTest(template = "empty")
    public static void numberKeysAndOtherResultClicksCannotBypassTheManufacturingClock(GameTestHelper helper) {
        for (RecipeCase recipe : workbenchCases()) checkOtherClicks(helper, recipe, true);
        checkOtherClicks(helper, stick(false), false);
        helper.succeed();
    }

    private static void checkOtherClicks(GameTestHelper helper, RecipeCase recipe, boolean workbench) {
        try (Fixture fixture = new Fixture(helper, recipe, workbench, 2)) {
            fixture.player.getInventory().setItem(0, new ItemStack(Items.DIRT));
            for (int hotbar = 0; hotbar < 9; hotbar++) fixture.menu.clicked(0, hotbar, ClickType.SWAP, fixture.player);
            fixture.menu.clicked(0, 40, ClickType.SWAP, fixture.player);
            fixture.menu.clicked(0, 0, ClickType.THROW, fixture.player);
            fixture.menu.clicked(0, 1, ClickType.THROW, fixture.player);
            fixture.menu.clicked(0, 0, ClickType.PICKUP_ALL, fixture.player);
            fixture.menu.clicked(0, 2, ClickType.CLONE, fixture.player);
            fixture.tick(fixture.period + 1);
            fixture.assertAmounts(0);
            helper.assertTrue(fixture.player.getInventory().getItem(0).is(Items.DIRT), "Number keys do not exchange the result for a hotbar item");
            helper.assertFalse(TimedCraftingController.isActive(fixture.player), "Unsupported result clicks cannot queue a batch");
            helper.assertFalse(fixture.menu.getSlot(0).mayPickup(fixture.player), "The actual result slot cannot be picked up before manufacture");
            fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
            fixture.tick(1);
            fixture.menu.clicked(0, 0, ClickType.SWAP, fixture.player);
            fixture.menu.clicked(0, 40, ClickType.SWAP, fixture.player);
            fixture.menu.quickMoveStack(fixture.player, 0);
            helper.assertValueEqual(TimedCraftingController.progress(fixture.player), 1, "Alternative extraction paths cannot advance or reset an active period");
            fixture.finishBatch(0);
            fixture.tick(fixture.period * 2);
            fixture.assertAmounts(1);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final RecipeCase recipe;
        private final int batches;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final AbstractContainerMenu menu;
        private final CraftingContainer grid;
        private final BlockPos table;
        private final BlockState original;
        private final List<ItemStack> inputs = new ArrayList<>();
        private final int period;

        private Fixture(GameTestHelper helper, RecipeCase recipe, boolean workbench, int batches) {
            this.helper = helper;
            this.recipe = recipe;
            this.batches = batches;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-audit-test"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(table);
            if (workbench) {
                helper.getLevel().setBlockAndUpdate(table, TimedCraftingTestSupport.flintTable().defaultBlockState());
                menu = new CraftingMenu(42, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            } else menu = player.inventoryMenu;
            player.containerMenu = menu;
            grid = (CraftingContainer) menu.getSlot(1).container;
            for (int index = 0; index < recipe.slots.length; index++) {
                menu.getSlot(recipe.slots[index] + 1).set(new ItemStack(recipe.ingredients[index], batches));
            }
            for (int slot = 0; slot < grid.getContainerSize(); slot++) inputs.add(grid.getItem(slot).copy());
            var actual = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), helper.getLevel()).orElseThrow();
            ResourceLocation id = ResourceLocation.withDefaultNamespace(recipe.id);
            helper.assertValueEqual(actual.id(), id, "The ingredients match the real loaded vanilla recipe " + id);
            helper.assertTrue(menu.stillValid(player), "The player uses a valid inventory or physically placed workbench");
            helper.assertTrue(menu.getSlot(0).getItem().is(recipe.output), "The real result slot previews the requested vanilla item");
            helper.assertValueEqual(menu.getSlot(0).getItem().getCount(), recipe.outputCount, "The result preview shows the expected full batch");
            var rule = TimedCraftingRecipes.get(id);
            helper.assertTrue(rule != null, "The vanilla recipe has an explicit R196 manufacturing rule");
            helper.assertValueEqual(rule.difficulty(), recipe.difficulty, "R196 difficulty is calculated for the complete output batch");
            helper.assertTrue(rule.requiredBench() == null, "These vanilla recipes need no special material workbench");
            period = TimedCraftingRules.period(recipe.difficulty, 0, workbench ? .2F : 0F, false);
            helper.assertValueEqual(TimedCraftingController.period(player, menu), period, "The real menu uses the R196 period, including its actual bench speed");
            assertAmounts(0);
        }

        private void tick(int amount) {
            for (int tick = 0; tick < amount; tick++) TimedCraftingController.tick(player);
        }

        private void finishBatch(int completed) {
            tick(period - 1 - TimedCraftingController.progress(player));
            helper.assertValueEqual(TimedCraftingController.progress(player), period - 1, "N-1 ticks cannot finish a vanilla recipe early");
            assertAmounts(completed);
            tick(1);
            assertAmounts(completed + 1);
            helper.assertValueEqual(TimedCraftingController.progress(player), 0, "The completed batch carries no progress into its successor");
        }

        private void assertAmounts(int completed) {
            for (int slot = 0; slot < grid.getContainerSize(); slot++) {
                ItemStack expected = inputs.get(slot).copy();
                if (!expected.isEmpty()) expected.setCount(batches - completed);
                helper.assertTrue(ItemStack.matches(grid.getItem(slot), expected),
                        recipe.id + " consumes exactly one set of real inputs per completed batch (slot " + slot + ")");
            }
            helper.assertValueEqual(player.getInventory().countItem(recipe.output), completed * recipe.outputCount,
                    recipe.id + " delivers exactly the whole vanilla output batch at completion");
            helper.assertTrue(menu.getCarried().isEmpty(), "An unfinished or duplicated result is never placed on the cursor");
            helper.assertTrue(player.getOffhandItem().isEmpty(), "An unfinished result is never delivered through offhand swapping");
        }

        @Override public void close() {
            try {
                TimedCraftingController.cancel(player);
                grid.clearContent();
                player.getInventory().clearContent();
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, original);
                channel.finishAndReleaseAll();
            }
        }
    }
}
