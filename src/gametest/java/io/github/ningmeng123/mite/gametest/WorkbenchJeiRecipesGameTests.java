package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.BulkMaterialRecipe;
import io.github.ningmeng123.mite.crafting.FoodManufacturingRecipe;
import io.github.ningmeng123.mite.crafting.FoodShapedRecipe;
import io.github.ningmeng123.mite.crafting.FoodShapelessRecipe;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.WorkbenchDismantleRecipe;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipe;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipes;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ArmorDyeRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independently specified tier gates checked against viewer rows and real crafting menus. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WorkbenchJeiRecipesGameTests {
    private static final Set<String> ALL_TABLES = Set.of("mite:flint_crafting_table", "mite:obsidian_crafting_table",
            "mite:copper_crafting_table", "mite:silver_crafting_table", "mite:gold_crafting_table",
            "mite:iron_crafting_table", "mite:ancient_metal_crafting_table", "mite:mithril_crafting_table", "mite:adamantium_crafting_table");
    private static final Set<String> METAL_TABLES = Set.of("mite:copper_crafting_table", "mite:silver_crafting_table",
            "mite:gold_crafting_table", "mite:iron_crafting_table", "mite:ancient_metal_crafting_table",
            "mite:mithril_crafting_table", "mite:adamantium_crafting_table");
    private WorkbenchJeiRecipesGameTests() {}

    @GameTest(template = "empty")
    public static void commonViewerWorksWithoutJeiModOrItsCompileOnlyApi(GameTestHelper helper) {
        helper.assertTrue(!ModList.get().isLoaded("jei"), "The workbench integration remains an optional expansion");
        try {
            Class.forName("mezz.jei.api.IModPlugin", false, WorkbenchJeiRecipesGameTests.class.getClassLoader());
            helper.fail("The compile-only JEI API must be absent from the dedicated test runtime");
        } catch (ClassNotFoundException expected) {
            helper.assertTrue(!WorkbenchJeiRecipes.build(helper.getLevel()).isEmpty(), "Common workbench pages build with no client or JEI classes loaded");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allNineMaterialTablesAndTheTwoByTwoInventoryHaveIndependentProfiles(GameTestHelper helper) {
        Map<String, Float> capacities = Map.of("minecraft:inventory", 0F, "mite:flint_crafting_table", 1F,
                "mite:obsidian_crafting_table", 2F, "mite:copper_crafting_table", 4F, "mite:silver_crafting_table", 4F,
                "mite:gold_crafting_table", 4F, "mite:iron_crafting_table", 8F, "mite:ancient_metal_crafting_table", 16F,
                "mite:mithril_crafting_table", 64F, "mite:adamantium_crafting_table", 256F);
        var profiles = WorkbenchJeiRecipes.profiles();
        helper.assertTrue(profiles.keySet().equals(capacities.keySet()), "The viewer exposes exactly the nine enabled source benches and inventory crafting");
        capacities.forEach((id, capacity) -> {
            helper.assertValueEqual(profiles.get(id).capacity(), capacity, "Workbench material capacity comes from the independent source tier table: " + id);
            helper.assertValueEqual(profiles.get(id).gridSize(), id.equals("minecraft:inventory") ? 2 : 3,
                    "Inventory uses a physical 2x2 grid while every material table provides 3x3");
        });
        helper.assertTrue(WorkbenchJeiRecipes.build(helper.getLevel()).stream().map(WorkbenchJeiRecipe::workstationId)
                .collect(Collectors.toSet()).equals(capacities.keySet()), "Every profile has usable recipe pages");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void equipmentPagesUseNumericMaterialCapacityRatherThanNamesOrHarvestRanks(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        assertWorkstations(helper, pages, "mite:copper_pickaxe", METAL_TABLES);
        assertWorkstations(helper, pages, "minecraft:iron_pickaxe", Set.of("mite:iron_crafting_table", "mite:ancient_metal_crafting_table",
                "mite:mithril_crafting_table", "mite:adamantium_crafting_table"));
        assertWorkstations(helper, pages, "mite:mithril_pickaxe", Set.of("mite:mithril_crafting_table", "mite:adamantium_crafting_table"));
        assertWorkstations(helper, pages, "mite:adamantium_pickaxe", Set.of("mite:adamantium_crafting_table"));
        for (var page : matching(pages, "mite:copper_pickaxe")) {
            helper.assertValueEqual(page.requiredCapacity(), 4F, "All equal-capacity copper, silver and gold tables support copper equipment");
            helper.assertValueEqual(page.rule().difficulty(), 1250F, "Copper pickaxe keeps the independently extracted whole-batch difficulty");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void workbenchConstructionUsesThePreviousStageAndPrimitiveBenchesFitTheInventory(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        var primitive = new java.util.HashSet<>(ALL_TABLES); primitive.add("minecraft:inventory");
        assertWorkstations(helper, pages, "mite:flint_crafting_table", primitive);
        assertWorkstations(helper, pages, "mite:obsidian_crafting_table", primitive);
        assertWorkstations(helper, pages, "mite:copper_crafting_table", ALL_TABLES);
        assertWorkstations(helper, pages, "mite:iron_crafting_table", METAL_TABLES);
        assertWorkstations(helper, pages, "mite:mithril_crafting_table", Set.of("mite:ancient_metal_crafting_table",
                "mite:mithril_crafting_table", "mite:adamantium_crafting_table"));
        assertWorkstations(helper, pages, "mite:adamantium_crafting_table", Set.of("mite:mithril_crafting_table", "mite:adamantium_crafting_table"));
        for (String material : List.of("copper", "iron", "mithril", "adamantium")) {
            float expected = switch (material) { case "copper" -> 1F; case "iron" -> 4F; case "mithril" -> 16F; default -> 64F; };
            helper.assertTrue(matching(pages, "mite:" + material + "_crafting_table").stream().allMatch(page -> page.requiredCapacity() == expected),
                    "Constructing the next bench never incorrectly requires that same bench: " + material);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void physicalGridRequirementsAndDisabledWoodenRecipesRemainVisibleTruthfully(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        assertWorkstations(helper, pages, "minecraft:oak_planks", ALL_TABLES);
        for (String removed : List.of("minecraft:crafting_table", "minecraft:wooden_axe", "minecraft:wooden_pickaxe"))
            helper.assertTrue(matching(pages, removed).isEmpty(), "Disabled original recipes cannot reappear as optional JEI pages: " + removed);
        helper.assertTrue(pages.stream().filter(page -> page.workstationId().equals("minecraft:inventory"))
                .allMatch(page -> page.width() <= 2 && page.height() <= 2 && page.requiredCapacity() == 0F),
                "Inventory pages always fit the real 2x2 grid and require no placed bench");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shapedPatternsPreserveEmptySlotsTagsAndEveryDeclaredBindingAlternative(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        WorkbenchJeiRecipe pickaxe = page(pages, "mite:copper_crafting_table", "mite:copper_pickaxe");
        helper.assertValueEqual(pickaxe.width(), 3, "A pickaxe recipe spans three columns");
        helper.assertValueEqual(pickaxe.height(), 3, "A pickaxe recipe spans three rows");
        helper.assertTrue(!pickaxe.shapeless() && pickaxe.inputs().size() == 9, "Shaped recipes preserve all occupied and empty cells");
        for (int slot : new int[]{0, 1, 2}) assertAlternatives(helper, pickaxe.inputs().get(slot), Set.of("minecraft:copper_ingot"));
        for (int slot : new int[]{4, 7}) assertAlternatives(helper, pickaxe.inputs().get(slot), Set.of("minecraft:stick"));
        for (int slot : new int[]{3, 5, 6, 8}) helper.assertTrue(pickaxe.inputs().get(slot).isEmpty(), "Empty pickaxe-pattern cells stay empty");
        WorkbenchJeiRecipe flint = page(pages, "minecraft:inventory", "mite:flint_crafting_table");
        helper.assertValueEqual(flint.width(), 2, "The primitive workbench pattern fits inventory width");
        helper.assertValueEqual(flint.height(), 2, "The primitive workbench pattern fits inventory height");
        assertAlternatives(helper, flint.inputs().get(0), Set.of("minecraft:flint"));
        assertAlternatives(helper, flint.inputs().get(1), Set.of("minecraft:string", "mite:sinew"));
        assertAlternatives(helper, flint.inputs().get(2), Set.of("minecraft:stick"));
        helper.assertTrue(flint.inputs().get(3).stream().anyMatch(stack -> stack.is(Items.OAK_LOG))
                && flint.inputs().get(3).stream().anyMatch(stack -> stack.is(Items.CHERRY_LOG)), "A source log tag also exposes modern wood alternatives");
        for (ItemStack binding : flint.inputs().get(1)) for (ItemStack log : flint.inputs().get(3)) {
            CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.FLINT), binding.copy(), new ItemStack(Items.STICK), log.copy()));
            helper.assertTrue(flint.source().value().matches(input, helper.getLevel()), "Every shown tag/binding alternative fits its true loaded recipe");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foodMetadataAndMixedMilkBucketsAreRepresentedByActualManufacturingRules(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        assertWorkstations(helper, pages, "mite:copper_bucket", METAL_TABLES);
        assertWorkstations(helper, pages, "mite:ancient_metal_bucket", Set.of("mite:ancient_metal_crafting_table", "mite:mithril_crafting_table", "mite:adamantium_crafting_table"));
        assertWorkstations(helper, pages, "minecraft:golden_apple", METAL_TABLES);
        for (var copper : matching(pages, "mite:copper_bucket")) {
            helper.assertValueEqual(copper.rule().difficulty(), 1200F, "Food serializer metadata also retains metal-vessel crafting difficulty");
            helper.assertValueEqual(copper.requiredCapacity(), 4F, "Metal-vessel recipes cannot bypass a lower-capacity bench");
        }
        WorkbenchJeiRecipe cheese = pages.stream().filter(page -> page.workstationId().equals("minecraft:inventory")
                && mixedMilk(page) && page.output().getCount() == 1).findFirst().orElseThrow();
        helper.assertTrue(cheese.shapeless() && cheese.inputs().size() == 1 && cheese.output().getCount() == 1,
                "The one-milk-bucket recipe remains a one-output shapeless inventory batch");
        Set<String> milk = Set.of("minecraft:milk_bucket", "mite:copper_milk_bucket", "mite:silver_milk_bucket",
                "mite:gold_milk_bucket", "mite:ancient_metal_milk_bucket", "mite:mithril_milk_bucket", "mite:adamantium_milk_bucket");
        assertAlternatives(helper, cheese.inputs().getFirst(), milk);
        for (ItemStack bucket : cheese.inputs().getFirst()) {
            CraftingInput input = CraftingInput.of(1, 1, List.of(bucket.copy()));
            helper.assertTrue(cheese.source().value().matches(input, helper.getLevel()), "All displayed source milk buckets match the true custom shapeless rule");
            helper.assertTrue(ItemStack.matches(cheese.source().value().assemble(input, helper.getLevel().registryAccess()), cheese.output()),
                    "All milk alternatives produce the displayed exact cheese batch");
        }
        WorkbenchJeiRecipe mixed = pages.stream().filter(page -> page.workstationId().equals("mite:flint_crafting_table")
                && mixedMilk(page) && page.output().getCount() == 5).findFirst().orElseThrow();
        helper.assertTrue(mixed.inputs().stream().filter(slot -> !slot.isEmpty()).count() == 5 && mixed.output().getCount() == 5,
                "Five milk buckets remain five separate consumed inputs rather than one stack");
        List<ItemStack> mixedBuckets = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        for (int slot = 0; slot < 5; slot++) {
            assertAlternatives(helper, mixed.inputs().get(slot), milk);
            mixedBuckets.set(slot, mixed.inputs().get(slot).get(slot).copy());
        }
        CraftingInput mixedInput = CraftingInput.of(3, 3, mixedBuckets);
        helper.assertTrue(mixed.source().value().matches(mixedInput, helper.getLevel()), "A page's independently chosen different metals can be mixed in the true custom rule");
        helper.assertTrue(ItemStack.matches(mixed.source().value().assemble(mixedInput, helper.getLevel().registryAccess()), mixed.output()),
                "Mixed metal vessels keep the five-cheese batch output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mergedMilkPagesCoverEveryOriginalRecipeWithoutDuplicateQuantityPages(GameTestHelper helper) {
        var index = WorkbenchJeiRecipes.index(helper.getLevel());
        var originals = index.originals().stream().filter(holder -> holder.value() instanceof FoodManufacturingRecipe food
                && food.manufacturing().anyMilkBuckets()).toList();
        helper.assertValueEqual(originals.size(), 63, "All nine source cheese quantities and seven original metal bucket recipes remain covered for native JEI replacement");
        helper.assertValueEqual(originals.stream().map(RecipeHolder::id).distinct().count(), 63L,
                "The hidden-original index retains every unique milk recipe ID exactly once");
        var milkPages = index.recipes().stream().filter(WorkbenchJeiRecipesGameTests::mixedMilk).toList();
        helper.assertValueEqual(milkPages.size(), 85, "Each of nine tables has nine cheese quantities; the 2x2 inventory has four quantities");
        for (String station : WorkbenchJeiRecipes.profiles().keySet()) {
            int maximum = station.equals("minecraft:inventory") ? 4 : 9;
            var stationPages = milkPages.stream().filter(page -> page.workstationId().equals(station)).toList();
            helper.assertValueEqual(stationPages.size(), maximum, "Seven equivalent source recipes collapse to one page per executable batch quantity");
            for (int quantity = 1; quantity <= maximum; quantity++) {
                final int batch = quantity;
                helper.assertValueEqual(stationPages.stream().filter(page -> page.output().getCount() == batch).count(), 1L,
                        "The workbench has exactly one page for cheese quantity " + batch);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void readonlyPageBuildDoesNotReplaceRecipeManagerOrMutateSourceOutputs(GameTestHelper helper) {
        var before = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        var ids = before.stream().map(RecipeHolder::id).collect(Collectors.toSet());
        List<ItemStack> sourceOutputs = before.stream().map(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()).copy()).toList();
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        var after = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        helper.assertTrue(ids.equals(after.stream().map(RecipeHolder::id).collect(Collectors.toSet())), "Building optional pages never adds or removes server recipes");
        for (int index = 0; index < before.size(); index++) helper.assertTrue(ItemStack.matches(sourceOutputs.get(index),
                before.get(index).value().getResultItem(helper.getLevel().registryAccess())), "The source output and all its components remain unchanged");
        helper.assertTrue(pages.stream().allMatch(page -> ids.contains(page.source().id())), "Every shown craft references a loaded server recipe");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realPlacedTablesRejectAndManufactureTheSameCopperEquipmentShownInPages(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        try (Fixture low = new Fixture(helper, "mite:obsidian_crafting_table")) {
            low.loadCopperPickaxe();
            helper.assertTrue(low.menu.getSlot(0).getItem().isEmpty(), "A physical obsidian table cannot expose a copper pickaxe result");
            helper.assertTrue(!TimedCraftingController.start(low.player, low.menu), "Starting the actual manufacturing path also rejects the insufficient tier");
            helper.assertValueEqual(low.grid.getItem(0).getCount(), 1, "A rejected viewer tier never consumes metal");
        }
        for (String table : List.of("mite:copper_crafting_table", "mite:silver_crafting_table", "mite:iron_crafting_table")) {
            try (Fixture allowed = new Fixture(helper, table)) {
                allowed.loadCopperPickaxe();
                WorkbenchJeiRecipe shown = page(pages, table, "mite:copper_pickaxe");
                int expectedPeriod = table.equals("mite:iron_crafting_table") ? 272 : 293;
                helper.assertValueEqual(shown.baselineTicks(), expectedPeriod, "The viewer states the independently calculated level-zero average-quality duration");
                helper.assertValueEqual(TimedCraftingController.period(allowed.player, allowed.menu), expectedPeriod,
                        "The same duration drives a real physically placed table");
                TimedCraftingTestSupport.craftOne(helper, allowed.player, allowed.menu, ClickType.PICKUP);
                helper.assertValueEqual(allowed.player.getInventory().countItem(item("mite:copper_pickaxe")), 1,
                        "A valid page makes exactly one real equipment result through the normal timed click");
                helper.assertTrue(allowed.grid.isEmpty(), "Exactly the page's three ingots and two sticks are consumed");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primitiveDismantlingPagesKeepTheKnifeAndTheirRealWoodenRemainder(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        for (String material : List.of("flint", "obsidian")) {
            WorkbenchJeiRecipe shown = pages.stream().filter(page -> page.workstationId().equals("mite:flint_crafting_table")
                    && page.source().value() instanceof WorkbenchDismantleRecipe && page.output().is(item("mite:" + material + "_knife")))
                    .findFirst().orElseThrow();
            helper.assertValueEqual(shown.output().getCount(), 1, "Dismantling exposes exactly one matching primitive knife");
            helper.assertTrue(shown.remainders().size() == 1 && shown.remainders().getFirst().is(Items.OAK_LOG)
                    && shown.remainders().getFirst().getCount() == 1, "The page explicitly preserves the second output: one oak-log remainder");
            try (Fixture fixture = new Fixture(helper, "mite:flint_crafting_table")) {
                fixture.menu.getSlot(1).set(new ItemStack(item("mite:" + material + "_crafting_table")));
                TimedCraftingTestSupport.craftOne(helper, fixture.player, fixture.menu, ClickType.PICKUP);
                helper.assertValueEqual(fixture.player.getInventory().countItem(shown.output().getItem()), 1, "A real timed dismantling batch delivers the shown knife");
                helper.assertTrue(fixture.grid.getItem(0).is(Items.OAK_LOG) && fixture.grid.getItem(0).getCount() == 1,
                        "The exact displayed log is returned to the real input slot after consuming one bench");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bulkBlockUnpackingPagesNeverTruncateNineIngotsToTheirEightItemStackLimit(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        Map<Item, Item> blocks = Map.of(Items.COPPER_BLOCK, Items.COPPER_INGOT, Items.WAXED_COPPER_BLOCK, Items.COPPER_INGOT,
                Items.IRON_BLOCK, Items.IRON_INGOT, Items.GOLD_BLOCK, Items.GOLD_INGOT);
        for (var conversion : blocks.entrySet()) {
            WorkbenchJeiRecipe shown = pages.stream().filter(page -> page.workstationId().equals("mite:flint_crafting_table")
                    && page.source().value() instanceof BulkMaterialRecipe && page.inputs().size() == 1
                    && page.inputs().getFirst().stream().anyMatch(stack -> stack.is(conversion.getKey()))).findFirst().orElseThrow();
            helper.assertTrue(shown.output().is(conversion.getValue()), "The loaded material block produces its actual metal");
            helper.assertValueEqual(shown.output().getCount(), 9, "A complete one-block batch displays nine ingots");
            helper.assertValueEqual(shown.output().getMaxStackSize(), 8, "The special batch display must not silently apply the normal eight-ingot inventory limit");
            try (Fixture fixture = new Fixture(helper, "mite:flint_crafting_table")) {
                fixture.menu.getSlot(1).set(new ItemStack(conversion.getKey()));
                TimedCraftingTestSupport.craftOne(helper, fixture.player, fixture.menu, ClickType.PICKUP);
                helper.assertValueEqual(fixture.player.getInventory().countItem(conversion.getValue()), 9, "The actual timed click delivers the full displayed nine-ingot batch");
                helper.assertTrue(fixture.grid.isEmpty(), "One input block is consumed exactly once");
                for (int slot = 0; slot < fixture.player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = fixture.player.getInventory().getItem(slot);
                    helper.assertTrue(stack.isEmpty() || stack.getCount() <= stack.getMaxStackSize(), "Production splits oversized batch output into legal inventory stacks");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownRecipeSubclassesAndDynamicRecipesKeepTheirOwnJeiExtensions(GameTestHelper helper) {
        var loaded = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        Set<Class<?>> supported = Set.of(ShapedRecipe.class, ShapelessRecipe.class, FoodShapedRecipe.class,
                FoodShapelessRecipe.class, BulkMaterialRecipe.class, WorkbenchDismantleRecipe.class,
                io.github.ningmeng123.mite.crafting.RangedRodConversionRecipe.class);
        for (Class<?> type : supported) {
            CraftingRecipe actual = loaded.stream().map(RecipeHolder::value).filter(recipe -> recipe.getClass() == type).findFirst().orElseThrow();
            helper.assertTrue(WorkbenchJeiRecipes.supports(actual), "The viewer supports each explicitly integrated recipe class actually loaded by the server");
        }
        ShapedRecipe shaped = (ShapedRecipe) loaded.stream().filter(holder -> holder.id().equals(ResourceLocation.parse("mite:copper_pickaxe")))
                .findFirst().orElseThrow().value();
        ShapedRecipe differentAssembly = new ShapedRecipe(shaped.getGroup(), shaped.category(), shaped.pattern,
                shaped.getResultItem(helper.getLevel().registryAccess()), shaped.showNotification()) {
            @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return new ItemStack(Items.DIAMOND); }
        };
        CraftingInput pickaxeInput = CraftingInput.of(3, 3, List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY,
                ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY));
        helper.assertTrue(differentAssembly.matches(pickaxeInput, helper.getLevel())
                && differentAssembly.getResultItem(helper.getLevel().registryAccess()).is(item("mite:copper_pickaxe"))
                && differentAssembly.assemble(pickaxeInput, helper.getLevel().registryAccess()).is(Items.DIAMOND),
                "An unknown subclass can preserve declared ingredients/output while assembling a different result");
        helper.assertTrue(!WorkbenchJeiRecipes.supports(differentAssembly), "Unknown shaped subclasses must keep their original dynamic viewer extension");
        ShapelessRecipe shapeless = (ShapelessRecipe) loaded.stream().map(RecipeHolder::value)
                .filter(recipe -> recipe.getClass() == ShapelessRecipe.class).findFirst().orElseThrow();
        ShapelessRecipe differentMatching = new ShapelessRecipe(shapeless.getGroup(), shapeless.category(),
                shapeless.getResultItem(helper.getLevel().registryAccess()), shapeless.getIngredients()) {
            @Override public boolean matches(CraftingInput input, Level level) { return false; }
        };
        int width = Math.min(3, shapeless.getIngredients().size());
        int height = (shapeless.getIngredients().size() + width - 1) / width;
        List<ItemStack> ingredients = new ArrayList<>();
        for (var ingredient : shapeless.getIngredients()) ingredients.add(ingredient.getItems()[0].copy());
        while (ingredients.size() < width * height) ingredients.add(ItemStack.EMPTY);
        CraftingInput shapelessInput = CraftingInput.of(width, height, ingredients);
        helper.assertTrue(shapeless.matches(shapelessInput, helper.getLevel()) && !differentMatching.matches(shapelessInput, helper.getLevel()),
                "An unknown shapeless subclass can reject inputs accepted by the identical declared ingredients");
        helper.assertTrue(!WorkbenchJeiRecipes.supports(differentMatching), "Unknown shapeless subclasses also remain in their own viewer extension");
        helper.assertTrue(!WorkbenchJeiRecipes.supports(new RepairItemRecipe(CraftingBookCategory.MISC))
                && !WorkbenchJeiRecipes.supports(new ArmorDyeRecipe(CraftingBookCategory.MISC)),
                "Repair and dye results depend on input data and remain in JEI's existing dynamic recipe handling");
        helper.assertTrue(WorkbenchJeiRecipes.index(helper.getLevel()).originals().stream()
                .allMatch(holder -> supported.contains(holder.value().getClass())),
                "Only explicitly integrated recipe classes are hidden for replacement; dynamic and third-party recipes stay intact");
        helper.succeed();
    }

    private static boolean mixedMilk(WorkbenchJeiRecipe page) {
        return page.source().value() instanceof FoodManufacturingRecipe food && food.manufacturing().anyMilkBuckets();
    }

    private static List<WorkbenchJeiRecipe> matching(List<WorkbenchJeiRecipe> pages, String output) {
        Item expected = item(output);
        return pages.stream().filter(page -> page.output().is(expected)).toList();
    }

    private static WorkbenchJeiRecipe page(List<WorkbenchJeiRecipe> pages, String station, String recipe) {
        return pages.stream().filter(page -> page.workstationId().equals(station)
                && page.source().id().equals(ResourceLocation.parse(recipe))).findFirst().orElseThrow();
    }

    private static void assertWorkstations(GameTestHelper helper, List<WorkbenchJeiRecipe> pages, String output, Set<String> expected) {
        var actual = matching(pages, output).stream().map(WorkbenchJeiRecipe::workstationId).collect(Collectors.toSet());
        helper.assertTrue(actual.equals(expected), "The independently specified workstation set for " + output + " is " + expected + ", got " + actual);
    }

    private static void assertAlternatives(GameTestHelper helper, List<ItemStack> alternatives, Set<String> expected) {
        var actual = alternatives.stream().map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).collect(Collectors.toSet());
        helper.assertTrue(actual.equals(expected), "Slot ingredients retain exactly their usable alternatives: " + expected + ", got " + actual);
    }

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final AbstractContainerMenu menu;
        private final CraftingContainer grid;
        private final BlockPos table;
        private final BlockState original;

        private Fixture(GameTestHelper helper, String tableId) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-jei-table"), false);
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
            helper.getLevel().setBlockAndUpdate(table, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(tableId)).defaultBlockState());
            menu = new CraftingMenu(42, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            grid = (CraftingContainer) menu.getSlot(1).container;
            helper.assertTrue(menu.stillValid(player), "The test uses a real physically placed tiered table");
        }

        private void loadCopperPickaxe() {
            List<ItemStack> inputs = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
            for (int slot : new int[]{0, 1, 2}) inputs.set(slot, new ItemStack(Items.COPPER_INGOT));
            for (int slot : new int[]{4, 7}) inputs.set(slot, new ItemStack(Items.STICK));
            for (int slot = 0; slot < inputs.size(); slot++) menu.getSlot(slot + 1).set(inputs.get(slot));
        }

        @Override public void close() {
            try {
                TimedCraftingController.forget(player);
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
