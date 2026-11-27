package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.integration.jei.FurnaceJeiRecipe;
import io.github.ningmeng123.mite.integration.jei.FurnaceJeiRecipes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Viewer batches run headless; source literals and actual ticking furnaces verify display consistency. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FurnaceJeiRecipesGameTests {
    private static final Set<String> SOURCE_DEVICES = Set.of("mite:clay_oven", "mite:large_clay_oven",
            "mite:sandstone_furnace", "minecraft:furnace", "mite:obsidian_furnace", "mite:netherrack_furnace");
    private static final Set<String> HEAT_TWO_DEVICES = Set.of("minecraft:furnace", "mite:obsidian_furnace",
            "mite:netherrack_furnace", "minecraft:blast_furnace");
    private FurnaceJeiRecipesGameTests() {}

    @GameTest(template = "empty")
    public static void optionalJeiApiIsAbsentAndCommonBuilderStillWorksOnDedicatedTestServer(GameTestHelper helper) {
        helper.assertTrue(!ModList.get().isLoaded("jei"), "This dedicated optional-dependency run must contain no JEI mod");
        try {
            Class.forName("mezz.jei.api.IModPlugin", false, FurnaceJeiRecipesGameTests.class.getClassLoader());
            helper.fail("Compile-only JEI API must not leak into this dedicated runtime");
        } catch (ClassNotFoundException expected) {
            helper.assertTrue(!FurnaceJeiRecipes.build(helper.getLevel()).isEmpty(), "The common thermal recipe builder operates with JEI classes unavailable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void viewerBuildsAllEightDevicesWithoutLoadingJeiOrMinecraftClient(GameTestHelper helper) {
        List<FurnaceJeiRecipe> recipes = FurnaceJeiRecipes.build(helper.getLevel());
        Set<String> expected = new HashSet<>(SOURCE_DEVICES);
        expected.add("minecraft:smoker"); expected.add("minecraft:blast_furnace");
        helper.assertTrue(recipes.stream().map(FurnaceJeiRecipe::furnaceId).collect(Collectors.toSet()).equals(expected),
                "The optional viewer's common builder exposes all six source devices plus both modern devices in a headless server");
        for (var recipe : recipes) {
            helper.assertTrue(!recipe.inputs().isEmpty() && !recipe.fuels().isEmpty() && !recipe.output().isEmpty(),
                    "Every visible batch has an input, a producible result and at least one legal fuel");
            helper.assertTrue(recipe.sourceRecipe() != null, "Every batch keeps its source recipe or explicit synthetic thermal conversion identity");
            int expectedTicks = recipe.furnaceId().equals("minecraft:smoker") || recipe.furnaceId().equals("minecraft:blast_furnace") ? 100 : 200;
            helper.assertValueEqual(recipe.cycleTicks(), expectedTicks, "The displayed cycle is the independently specified source or modern cycle");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyDisplayedAlternativeAndFuelProducesExactlyItsShownBatch(GameTestHelper helper) {
        for (var shown : FurnaceJeiRecipes.build(helper.getLevel())) {
            var type = FurnaceJeiRecipes.recipeType(shown.furnaceId());
            var profile = FurnaceThermalController.profile(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(shown.furnaceId())).defaultBlockState());
            for (ItemStack input : shown.inputs()) {
                helper.assertTrue(FurnaceThermalController.canInput(helper.getLevel(), type, profile, input),
                        "Every displayed input alternative can enter the real device: " + shown.furnaceId() + " / " + input);
                var actualRecipe = recipe(helper, type, input);
                for (ItemStack fuel : shown.fuels()) {
                    helper.assertTrue(FurnaceThermalController.canFuel(profile, fuel), "No displayed fuel bypasses the real size or maximum-heat gate");
                    var actualFuel = FurnaceThermalController.fuel(fuel);
                    helper.assertTrue(actualFuel.heat() >= shown.heat(), "A visible fuel is hot enough for the shown batch");
                    helper.assertValueEqual(fuel.getCount(), (shown.cycleTicks() + actualFuel.ticks() - 1) / actualFuel.ticks(),
                            "Fuel ingredient quantities cover a full uninterrupted cycle rather than implying that one short fuel is enough");
                    var batch = FurnaceThermalController.preview(helper.getLevel(), type, profile, input,
                            ItemStack.EMPTY, 64, actualFuel.heat(), actualRecipe);
                    helper.assertTrue(batch != null, "Every input/fuel pairing on one page is a real executable processing batch");
                    helper.assertValueEqual(batch.consumed(), input.getCount(), "The page shows the exact consumed input quantity");
                    helper.assertTrue(ItemStack.isSameItemSameComponents(batch.result(), shown.output()), "The page preserves actual output components");
                    helper.assertValueEqual(batch.result().getCount(), shown.output().getCount(), "The page shows exact output quantity");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceOreHeatStagesAndEveryDeepVariantAppearOnlyUnderUsableDevices(GameTestHelper helper) {
        var recipes = FurnaceJeiRecipes.build(helper.getLevel());
        for (String prefix : List.of("", "deepslate_")) {
            for (String metal : List.of("copper", "iron", "gold"))
                assertOre(helper, recipes, "minecraft:" + prefix + metal + "_ore", "minecraft:" + metal + "_ingot", 2, 1, HEAT_TWO_DEVICES);
            assertOre(helper, recipes, "mite:" + prefix + "silver_ore", "mite:silver_ingot", 2, 1, HEAT_TWO_DEVICES);
            assertOre(helper, recipes, "mite:" + prefix + "mithril_ore", "mite:mithril_ingot", 3, 1,
                    Set.of("mite:obsidian_furnace", "mite:netherrack_furnace"));
            assertOre(helper, recipes, "mite:" + prefix + "adamantium_ore", "mite:adamantium_ingot", 4, 1, Set.of("mite:netherrack_furnace"));
            for (String mineral : List.of("diamond", "emerald"))
                assertOre(helper, recipes, "minecraft:" + prefix + mineral + "_ore", "minecraft:" + mineral, 2, 1, HEAT_TWO_DEVICES);
            assertOre(helper, recipes, "minecraft:" + prefix + "lapis_ore", "minecraft:lapis_lazuli", 2, 4, HEAT_TWO_DEVICES);
            assertOre(helper, recipes, "minecraft:" + prefix + "redstone_ore", "minecraft:redstone", 2, 4, HEAT_TWO_DEVICES);
        }
        assertOre(helper, recipes, "minecraft:nether_quartz_ore", "minecraft:quartz", 2, 1, HEAT_TWO_DEVICES);
        assertOre(helper, recipes, "minecraft:nether_gold_ore", "minecraft:gold_ingot", 2, 1, HEAT_TWO_DEVICES);
        assertOre(helper, recipes, "minecraft:ancient_debris", "minecraft:netherite_scrap", 4, 1, Set.of("mite:netherrack_furnace"));
        for (String metal : List.of("copper", "iron", "gold"))
            assertOre(helper, recipes, "minecraft:raw_" + metal, "minecraft:" + metal + "_ingot", 2, 1, HEAT_TWO_DEVICES);
        assertOre(helper, recipes, "mite:raw_silver", "mite:silver_ingot", 2, 1, HEAT_TWO_DEVICES);
        assertOre(helper, recipes, "mite:raw_mithril", "mite:mithril_ingot", 3, 1,
                Set.of("mite:obsidian_furnace", "mite:netherrack_furnace"));
        assertOre(helper, recipes, "mite:raw_adamantium", "mite:adamantium_ingot", 4, 1, Set.of("mite:netherrack_furnace"));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lowHeatCoalOreKeepsLargeItemRestrictionAndModernBlastingType(GameTestHelper helper) {
        Set<String> expected = new HashSet<>(SOURCE_DEVICES); expected.remove("mite:clay_oven"); expected.add("minecraft:blast_furnace");
        var recipes = FurnaceJeiRecipes.build(helper.getLevel());
        for (String prefix : List.of("", "deepslate_"))
            assertOre(helper, recipes, "minecraft:" + prefix + "coal_ore", "minecraft:coal", 1, 1, expected);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sandShowsSeparateFourInputLowHeatSandstoneAndHighHeatGlassBatches(GameTestHelper helper) {
        var recipes = FurnaceJeiRecipes.build(helper.getLevel());
        for (boolean red : List.of(false, true)) {
            Item sand = red ? Items.RED_SAND : Items.SAND;
            Item sandstone = red ? Items.RED_SANDSTONE : Items.SANDSTONE;
            Set<String> expectedSandstone = new HashSet<>(SOURCE_DEVICES); expectedSandstone.remove("mite:clay_oven");
            Set<String> actualSandstone = matching(recipes, sand, sandstone).stream().map(FurnaceJeiRecipe::furnaceId).collect(Collectors.toSet());
            helper.assertTrue(actualSandstone.equals(expectedSandstone), "Every large-input source furnace retains the low-temperature four-sand conversion");
            Set<String> actualGlass = matching(recipes, sand, Items.GLASS).stream().map(FurnaceJeiRecipe::furnaceId).collect(Collectors.toSet());
            helper.assertTrue(actualGlass.equals(Set.of("minecraft:furnace", "mite:obsidian_furnace", "mite:netherrack_furnace")),
                    "Only source smelting devices with heat at least two display the sand-to-glass alternative");
            for (var shown : matching(recipes, sand, sandstone)) {
                helper.assertValueEqual(shown.heat(), 1, "Low-temperature sand makes sandstone");
                helper.assertTrue(shown.fuels().stream().allMatch(fuel -> FurnaceThermalController.fuel(fuel).heat() == 1),
                        "Sandstone pages cannot include a higher-heat fuel that would actually make glass");
                helper.assertValueEqual(input(shown, sand).getCount(), 4, "The source sand conversion consumes four blocks");
                helper.assertValueEqual(shown.output().getCount(), 1, "Four sand produce exactly one sandstone");
            }
            for (var shown : matching(recipes, sand, Items.GLASS)) {
                helper.assertValueEqual(shown.heat(), 2, "Glass has a minimum effective sand-conversion heat two");
                helper.assertTrue(shown.fuels().stream().allMatch(fuel -> FurnaceThermalController.fuel(fuel).heat() >= 2),
                        "Glass pages cannot imply that charcoal produces glass from sand");
                helper.assertValueEqual(input(shown, sand).getCount(), 4, "High-temperature glass also consumes four sand");
                helper.assertValueEqual(shown.output().getCount(), 1, "Four sand produce exactly one glass");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothSandstoneVariantsRequireHeatTwoInViewerAndInRealTickingFurnaces(GameTestHelper helper) {
        var recipes = FurnaceJeiRecipes.build(helper.getLevel());
        for (Item sandstone : List.of(Items.SANDSTONE, Items.RED_SANDSTONE)) {
            var pages = matching(recipes, sandstone, Items.GLASS);
            helper.assertTrue(pages.stream().map(FurnaceJeiRecipe::furnaceId).collect(Collectors.toSet())
                    .equals(Set.of("minecraft:furnace", "mite:obsidian_furnace", "mite:netherrack_furnace")),
                    "Neither sandstone variant can appear under a low-temperature oven");
            for (var shown : pages) {
                helper.assertValueEqual(shown.heat(), 2, "Both sandstone variants require independent heat-two adaptation");
                helper.assertValueEqual(input(shown, sandstone).getCount(), 1, "Sandstone converts directly one-for-one");
            }
            try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
                f.set(sandstone, 1, Items.CHARCOAL, 1); f.tick(200);
                helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The actual native tick must reject heat-one red sandstone as well as ordinary sandstone");
                helper.assertValueEqual(f.furnace.getItem(1).getCount(), 1, "Insufficient heat preserves the unignited fuel");
                helper.assertValueEqual(f.saved().getInt("BurnTime"), 0, "A viewer-only correction cannot conceal an actual low-temperature ignition");
                f.furnace.setItem(1, new ItemStack(Items.COAL)); f.tick(199);
                helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The corrected material still waits a full source cycle");
                f.tick(1); helper.assertTrue(f.furnace.getItem(2).is(Items.GLASS), "Heat-two native cooking matches the corrected viewer entry");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smallClayOvenShowsAndFiresClayBlocksIntoTerracotta(GameTestHelper helper) {
        var recipes = FurnaceJeiRecipes.build(helper.getLevel());
        var pages = matching(recipes, Items.CLAY, Items.TERRACOTTA);
        helper.assertTrue(pages.stream().map(FurnaceJeiRecipe::furnaceId).collect(Collectors.toSet()).equals(SOURCE_DEVICES),
                "Terracotta is available in all six smelting furnaces, including the small clay oven");
        var smallOvenPage = pages.stream().filter(page -> page.furnaceId().equals("mite:clay_oven")).findFirst().orElseThrow();
        helper.assertValueEqual(smallOvenPage.heat(), 1, "The small oven shows heat one for terracotta");
        helper.assertValueEqual(smallOvenPage.cycleTicks(), 200, "The small oven shows a ten-second terracotta cycle");
        helper.assertValueEqual(input(smallOvenPage, Items.CLAY).getCount(), 1, "The terracotta recipe takes one clay block");
        helper.assertValueEqual(smallOvenPage.output().getCount(), 1, "The terracotta recipe produces one block");
        tickShown(helper, recipes, "mite:clay_oven", Items.CLAY, Items.TERRACOTTA, Items.CHARCOAL);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clayPagesKeepEveryOneThroughFourInputBatchAndRealOutputRoomLimit(GameTestHelper helper) {
        var recipes = FurnaceJeiRecipes.build(helper.getLevel());
        for (String furnaceId : SOURCE_DEVICES) {
            var pages = matching(recipes, Items.CLAY_BALL, Items.BRICK).stream().filter(page -> page.furnaceId().equals(furnaceId)).toList();
            helper.assertTrue(pages.stream().map(page -> input(page, Items.CLAY_BALL).getCount()).collect(Collectors.toSet()).equals(Set.of(1, 2, 3, 4)),
                    "Every source furnace can show the exact one, two, three and four-clay batch quantities");
            for (var page : pages) helper.assertValueEqual(page.output().getCount(), input(page, Items.CLAY_BALL).getCount(),
                    "Every displayed clay batch returns the same number of bricks");
        }
        try (Fixture f = new Fixture(helper, "mite:clay_oven")) {
            f.set(Items.CLAY_BALL, 4, Items.CHARCOAL, 1); f.furnace.setItem(2, new ItemStack(Items.BRICK, 7)); f.tick(200);
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 8, "A source-limit eight-brick output slot can receive only one more brick");
            helper.assertValueEqual(f.furnace.getItem(0).getCount(), 3, "The real engine preserves clay that cannot fit, even though a full batch viewer page shows four");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceFoodIncludesAllOvensAndModernSmokerButNeverTheBlastFurnace(GameTestHelper helper) {
        Set<String> expected = new HashSet<>(SOURCE_DEVICES); expected.add("minecraft:smoker");
        var recipes = FurnaceJeiRecipes.build(helper.getLevel());
        for (var pair : List.of(new Item[]{Items.BEEF, Items.COOKED_BEEF}, new Item[]{Items.PORKCHOP, Items.COOKED_PORKCHOP},
                new Item[]{item("mite:dough"), Items.BREAD}, new Item[]{item("mite:raw_large_fish"), item("mite:cooked_large_fish")})) {
            var pages = matching(recipes, pair[0], pair[1]);
            helper.assertTrue(pages.stream().map(FurnaceJeiRecipe::furnaceId).collect(Collectors.toSet()).equals(expected),
                    "The actual source food cooking category is retained for all ovens and the modern smoker only");
            for (var page : pages) helper.assertValueEqual(page.heat(), 1, "Source food requires low heat one");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shortFuelQuantitiesCoverActualCyclesAndSmallOvenExcludesLargeLogs(GameTestHelper helper) {
        var pages = matching(FurnaceJeiRecipes.build(helper.getLevel()), Items.BEEF, Items.COOKED_BEEF);
        for (var shown : pages) {
            helper.assertValueEqual(fuel(shown, Items.PAPER).getCount(), shown.cycleTicks() == 100 ? 4 : 8,
                    "The viewer includes the real number of 25-tick papers for a full native cycle");
            helper.assertValueEqual(fuel(shown, Items.STICK).getCount(), shown.cycleTicks() == 100 ? 1 : 2,
                    "The viewer includes the real number of 100-tick sticks for a full native cycle");
            helper.assertTrue(shown.fuels().stream().noneMatch(stack -> stack.is(Items.MUSHROOM_STEW)), "Food-filled bowls must never be displayed as fuel");
            if (shown.furnaceId().equals("mite:clay_oven")) {
                helper.assertTrue(shown.fuels().stream().noneMatch(stack -> stack.is(Items.OAK_LOG) || stack.is(Items.COAL)),
                        "A small oven page excludes both oversized logs and over-hot coal");
            } else helper.assertTrue(shown.fuels().stream().anyMatch(stack -> stack.is(Items.OAK_LOG)), "Larger ovens display valid heat-one logs");
        }
        try (Fixture f = new Fixture(helper, "mite:clay_oven")) {
            f.set(Items.BEEF, 1, Items.PAPER, 8); f.tick(199);
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The shown eight papers do not finish a source cycle early");
            f.tick(1); helper.assertTrue(f.furnace.getItem(2).is(Items.COOKED_BEEF), "The displayed short-fuel quantity completes an actual uninterrupted batch");
            helper.assertTrue(f.furnace.getItem(1).isEmpty(), "The actual short-fuel batch consumes precisely the advertised quantity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shownHighTierOrePagesCompleteInActualNativeFurnacesWithTheirListedFuel(GameTestHelper helper) {
        var recipes = FurnaceJeiRecipes.build(helper.getLevel());
        for (String prefix : List.of("", "deepslate_")) {
            tickShown(helper, recipes, "minecraft:furnace", item("minecraft:" + prefix + "iron_ore"), Items.IRON_INGOT, Items.COAL);
            tickShown(helper, recipes, "minecraft:blast_furnace", item("minecraft:" + prefix + "iron_ore"), Items.IRON_INGOT, Items.COAL);
            tickShown(helper, recipes, "mite:obsidian_furnace", item("mite:" + prefix + "mithril_ore"), item("mite:mithril_ingot"), Items.LAVA_BUCKET);
            tickShown(helper, recipes, "mite:netherrack_furnace", item("mite:" + prefix + "adamantium_ore"), item("mite:adamantium_ingot"), Items.BLAZE_ROD);
        }
        helper.succeed();
    }

    private static void assertOre(GameTestHelper helper, List<FurnaceJeiRecipe> recipes, String raw, String result,
            int heat, int quantity, Set<String> devices) {
        Item input = item(raw), output = item(result);
        var pages = matching(recipes, input, output);
        helper.assertTrue(pages.stream().map(FurnaceJeiRecipe::furnaceId).collect(Collectors.toSet()).equals(devices),
                "The viewer lists exactly the independently expected usable devices for " + raw);
        for (var shown : pages) {
            helper.assertValueEqual(shown.heat(), heat, "The displayed source ore threshold is independently specified");
            helper.assertValueEqual(input(shown, input).getCount(), 1, "An ore page consumes one ore block");
            helper.assertValueEqual(shown.output().getCount(), quantity, "An ore page preserves exact mineral yield");
        }
    }
    private static List<FurnaceJeiRecipe> matching(List<FurnaceJeiRecipe> recipes, Item input, Item output) {
        return recipes.stream().filter(page -> page.output().is(output) && page.inputs().stream().anyMatch(stack -> stack.is(input))).toList();
    }
    private static ItemStack input(FurnaceJeiRecipe page, Item item) { return page.inputs().stream().filter(stack -> stack.is(item)).findFirst().orElseThrow(); }
    private static ItemStack fuel(FurnaceJeiRecipe page, Item item) { return page.fuels().stream().filter(stack -> stack.is(item)).findFirst().orElseThrow(); }
    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) throw new IllegalArgumentException("Unregistered viewer test item: " + id);
        return item;
    }
    @SuppressWarnings("unchecked")
    private static RecipeHolder<? extends AbstractCookingRecipe> recipe(GameTestHelper helper,
            RecipeType<? extends AbstractCookingRecipe> type, ItemStack input) {
        return helper.getLevel().getRecipeManager().getRecipeFor((RecipeType<AbstractCookingRecipe>) type,
                new SingleRecipeInput(input), helper.getLevel()).orElse(null);
    }
    private static void tickShown(GameTestHelper helper, List<FurnaceJeiRecipe> recipes,
            String furnace, Item input, Item output, Item fuel) {
        var page = matching(recipes, input, output).stream().filter(shown -> shown.furnaceId().equals(furnace)).findFirst().orElseThrow();
        try (Fixture f = new Fixture(helper, furnace)) {
            f.set(input, input(page, input).getCount(), fuel, fuel(page, fuel).getCount()); f.tick(page.cycleTicks() - 1);
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The actual device cannot complete its shown recipe ahead of the advertised cycle");
            f.tick(1);
            helper.assertTrue(ItemStack.isSameItemSameComponents(f.furnace.getItem(2), page.output()), "Actual furnace output agrees with the shown page");
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), page.output().getCount(), "Actual output count agrees with the shown page");
            helper.assertTrue(f.furnace.getItem(0).isEmpty(), "Actual input consumption agrees with the shown page");
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos pos, front;
        final BlockState old, oldFront;
        final AbstractFurnaceBlockEntity furnace;
        Fixture(GameTestHelper helper, String furnaceId) {
            this.helper = helper; pos = helper.absolutePos(new BlockPos(2, 2, 2)); front = pos.north();
            old = helper.getLevel().getBlockState(pos); oldFront = helper.getLevel().getBlockState(front);
            helper.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(furnaceId));
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
            furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(furnace != null, "The comparison uses an actual placed furnace block entity");
        }
        void set(Item input, int amount, Item fuel, int fuelCount) {
            furnace.setItem(0, new ItemStack(input, amount)); furnace.setItem(1, new ItemStack(fuel, fuelCount));
        }
        void tick(int amount) { for (int i = 0; i < amount; i++) AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), furnace); }
        CompoundTag saved() { return furnace.saveWithoutMetadata(helper.getLevel().registryAccess()); }
        @Override public void close() { furnace.clearContent(); helper.getLevel().setBlockAndUpdate(pos, old); helper.getLevel().setBlockAndUpdate(front, oldFront); }
    }
}
