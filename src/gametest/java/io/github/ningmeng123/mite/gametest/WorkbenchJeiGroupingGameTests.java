package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.FoodManufacturingRecipe;
import io.github.ningmeng123.mite.integration.jei.SuspiciousStewJeiRecipes;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipe;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipes;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent catalyst expectations and lossless transformation checks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WorkbenchJeiGroupingGameTests {
    private static final String INVENTORY = "minecraft:inventory";
    private static final String FLINT = "mite:flint_crafting_table";
    private static final String COPPER = "mite:copper_crafting_table";
    private static final String IRON = "mite:iron_crafting_table";
    private static final String MITHRIL = "mite:mithril_crafting_table";
    private static final String ADAMANTIUM = "mite:adamantium_crafting_table";
    private static final Set<String> ALL_TABLES = Set.of(FLINT, "mite:obsidian_crafting_table",
            COPPER, "mite:silver_crafting_table", "mite:gold_crafting_table", IRON,
            "mite:ancient_metal_crafting_table", MITHRIL, ADAMANTIUM);
    private static final Set<String> METAL_TABLES = Set.of(COPPER, "mite:silver_crafting_table",
            "mite:gold_crafting_table", IRON, "mite:ancient_metal_crafting_table", MITHRIL, ADAMANTIUM);

    private WorkbenchJeiGroupingGameTests() {}

    @GameTest(template = "empty")
    public static void equalCapacityMetalsShareOneCategoryWithoutLosingTheirCatalysts(GameTestHelper helper) {
        var categories = WorkbenchJeiRecipes.categoryProfiles();
        helper.assertTrue(categories.keySet().equals(Set.of(INVENTORY, FLINT, "mite:obsidian_crafting_table",
                COPPER, IRON, "mite:ancient_metal_crafting_table", MITHRIL, ADAMANTIUM)),
                "Seven distinct table capacities and inventory produce exactly eight categories");
        helper.assertTrue(!categories.containsKey("mite:silver_crafting_table")
                && !categories.containsKey("mite:gold_crafting_table"),
                "Equal-capacity silver and gold do not add duplicate top tabs");
        helper.assertTrue(Set.copyOf(WorkbenchJeiRecipes.categoryWorkstations(COPPER)).equals(METAL_TABLES),
                "The copper category includes copper, silver, gold and every stronger table on its left");
        helper.assertTrue(WorkbenchJeiRecipes.categoryWorkstations(INVENTORY).isEmpty(),
                "Inventory crafting never claims that a placed workbench is required");
        helper.assertValueEqual(categories.get(INVENTORY).gridSize(), 2, "Inventory retains its real two-by-two grid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void equipmentAndPlanksShowPreciselyTheirIndependentlySpecifiedTables(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.groupedIndex(helper.getLevel()).recipes();
        assertOutputGroup(helper, pages, "mite:copper_pickaxe", COPPER, METAL_TABLES);
        assertOutputGroup(helper, pages, "minecraft:iron_pickaxe", IRON,
                Set.of(IRON, "mite:ancient_metal_crafting_table", MITHRIL, ADAMANTIUM));
        assertOutputGroup(helper, pages, "mite:mithril_pickaxe", MITHRIL, Set.of(MITHRIL, ADAMANTIUM));
        assertOutputGroup(helper, pages, "mite:adamantium_pickaxe", ADAMANTIUM, Set.of(ADAMANTIUM));
        assertOutputGroup(helper, pages, "minecraft:oak_planks", FLINT, ALL_TABLES);
        helper.assertTrue(pages.stream().noneMatch(page -> page.workstationId().equals(INVENTORY)
                && page.output().is(Items.OAK_PLANKS)), "Combining categories does not introduce forbidden inventory plank crafting");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyRecipeRetainsItsExactSourceAndEveryValidCatalystWithoutDuplicateTablePages(GameTestHelper helper) {
        var source = WorkbenchJeiRecipes.index(helper.getLevel());
        var grouped = WorkbenchJeiRecipes.groupedIndex(source);
        var sourceTables = source.recipes().stream().filter(WorkbenchJeiGroupingGameTests::isTable)
                .collect(Collectors.groupingBy(WorkbenchJeiRecipe::displayId));
        var tablePages = grouped.recipes().stream().filter(WorkbenchJeiGroupingGameTests::isTable).toList();
        helper.assertValueEqual(tablePages.size(), sourceTables.size(),
                "Each loaded display recipe has exactly one table page instead of one page per compatible table");
        helper.assertValueEqual(tablePages.stream().map(WorkbenchJeiRecipe::displayId).distinct().count(),
                (long) tablePages.size(), "Two table categories never duplicate the same display recipe");
        helper.assertTrue(tablePages.stream().map(WorkbenchJeiRecipe::displayId).collect(Collectors.toSet())
                .equals(sourceTables.keySet()), "No table display ID is lost or invented by grouping");
        helper.assertTrue(grouped.originals().equals(source.originals()),
                "The complete exact original-recipe replacement list is unchanged");
        for (var page : tablePages) {
            var originals = sourceTables.get(page.displayId());
            Set<String> originalTables = originals.stream().map(WorkbenchJeiRecipe::workstationId).collect(Collectors.toSet());
            helper.assertTrue(new HashSet<>(WorkbenchJeiRecipes.categoryWorkstations(page.workstationId())).equals(originalTables),
                    "Every left catalyst supports this recipe and every supporting table remains present: " + page.displayId());
            helper.assertTrue(WorkbenchJeiRecipes.categoryProfiles().containsKey(page.workstationId()),
                    "Each table page belongs to a category registered by the plugin");
            var selected = originals.stream().filter(original -> original.workstationId().equals(page.workstationId()))
                    .findFirst().orElseThrow();
            assertPageUnchanged(helper, selected, page);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryMilkBatchesAndFlowerVariantsRemainSeparateAndComplete(GameTestHelper helper) {
        var source = WorkbenchJeiRecipes.index(helper.getLevel());
        var grouped = WorkbenchJeiRecipes.groupedIndex(source);
        var inventory = source.recipes().stream().filter(page -> !isTable(page)).toList();
        var groupedInventory = grouped.recipes().stream().filter(page -> !isTable(page)).toList();
        helper.assertTrue(groupedInventory.equals(inventory),
                "Every original inventory page remains separate, with unchanged shape, components and source");
        helper.assertTrue(groupedInventory.stream().allMatch(page -> page.width() <= 2 && page.height() <= 2),
                "The standalone inventory category cannot acquire three-by-three table patterns");
        var milk = grouped.recipes().stream().filter(WorkbenchJeiGroupingGameTests::mixedMilk).toList();
        var tableMilk = milk.stream().filter(WorkbenchJeiGroupingGameTests::isTable).toList();
        var inventoryMilk = milk.stream().filter(page -> !isTable(page)).toList();
        helper.assertValueEqual(tableMilk.size(), 9, "All nine distinct cheese quantities remain after combining tables");
        helper.assertValueEqual(inventoryMilk.size(), 4, "Inventory retains exactly its four executable cheese quantities");
        helper.assertTrue(tableMilk.stream().map(page -> page.output().getCount()).collect(Collectors.toSet())
                .equals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9)), "Different cheese batch sizes never collapse into one display ID");
        helper.assertTrue(inventoryMilk.stream().map(page -> page.output().getCount()).collect(Collectors.toSet())
                .equals(Set.of(1, 2, 3, 4)), "Inventory cheese batches retain the two-by-two limit");
        var originalStews = source.recipes().stream().filter(page -> isTable(page)
                && SuspiciousStewJeiRecipes.isDisplayVariant(page.displayId())).toList();
        var groupedStews = grouped.recipes().stream().filter(page -> isTable(page)
                && SuspiciousStewJeiRecipes.isDisplayVariant(page.displayId())).toList();
        helper.assertTrue(!groupedStews.isEmpty() && groupedStews.stream().map(WorkbenchJeiRecipe::displayId).collect(Collectors.toSet())
                .equals(originalStews.stream().map(WorkbenchJeiRecipe::displayId).collect(Collectors.toSet())),
                "Every flower-specific stew display ID survives rather than collapsing by shared source recipe or output item");
        var dandelion = groupedStews.stream().filter(page -> hasIngredient(page, Items.DANDELION)).findFirst().orElseThrow();
        var witherRose = groupedStews.stream().filter(page -> hasIngredient(page, Items.WITHER_ROSE)).findFirst().orElseThrow();
        helper.assertTrue(!dandelion.displayId().equals(witherRose.displayId())
                && !ItemStack.matches(dandelion.output(), witherRose.output()),
                "Dandelion and wither rose keep different display pages and actual food effects");
        for (var page : groupedStews) {
            var input = CraftingInput.of(page.width(), page.height(), page.inputs().stream()
                    .map(slot -> slot.isEmpty() ? ItemStack.EMPTY : slot.getFirst().copy()).toList());
            helper.assertTrue(page.source().value().matches(input, helper.getLevel())
                    && ItemStack.matches(page.output(), page.source().value().assemble(input, helper.getLevel().registryAccess())),
                    "The displayed variant still assembles its exact effect-bearing item through the original dynamic recipe");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eachCatalystTimeMatchesItsOwnOriginalTablePage(GameTestHelper helper) {
        var source = WorkbenchJeiRecipes.index(helper.getLevel());
        var sourceTables = source.recipes().stream().filter(WorkbenchJeiGroupingGameTests::isTable)
                .collect(Collectors.groupingBy(WorkbenchJeiRecipe::displayId));
        var grouped = WorkbenchJeiRecipes.groupedIndex(source);
        for (var page : grouped.recipes().stream().filter(WorkbenchJeiGroupingGameTests::isTable).toList()) {
            for (var original : sourceTables.get(page.displayId())) {
                helper.assertValueEqual(page.baselineTicks(original.workstationId()), original.baselineTicks(),
                        "The per-catalyst time keeps the original actual table speed: " + page.displayId() + " / " + original.workstationId());
            }
            var minimum = sourceTables.get(page.displayId()).stream()
                    .mapToDouble(original -> WorkbenchJeiRecipes.profiles().get(original.workstationId()).capacity()).min().orElseThrow();
            helper.assertValueEqual((double) WorkbenchJeiRecipes.profiles().get(page.workstationId()).capacity(), minimum,
                    "The category's default time is explicitly based on its lowest compatible table");
        }
        helper.succeed();
    }

    private static boolean isTable(WorkbenchJeiRecipe page) { return !page.workstationId().equals(INVENTORY); }

    private static boolean mixedMilk(WorkbenchJeiRecipe page) {
        return page.source().value() instanceof FoodManufacturingRecipe food && food.manufacturing().anyMilkBuckets();
    }

    private static boolean hasIngredient(WorkbenchJeiRecipe page, net.minecraft.world.item.Item item) {
        return page.inputs().stream().flatMap(List::stream).anyMatch(stack -> stack.is(item));
    }

    private static void assertOutputGroup(GameTestHelper helper, List<WorkbenchJeiRecipe> pages,
            String output, String category, Set<String> catalysts) {
        var expectedItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(output));
        var matches = pages.stream().filter(WorkbenchJeiGroupingGameTests::isTable).filter(page -> page.output().is(expectedItem)).toList();
        helper.assertTrue(!matches.isEmpty() && matches.stream().allMatch(page -> page.workstationId().equals(category)),
                "The independently specified lowest category for " + output + " is " + category);
        helper.assertTrue(Set.copyOf(WorkbenchJeiRecipes.categoryWorkstations(category)).equals(catalysts),
                "The left-side table set for " + output + " matches its independently specified material gate");
    }

    private static void assertPageUnchanged(GameTestHelper helper, WorkbenchJeiRecipe expected, WorkbenchJeiRecipe actual) {
        helper.assertTrue(expected.source() == actual.source() && expected.displayId().equals(actual.displayId()),
                "The real source recipe and stable display ID remain intact");
        helper.assertTrue(expected.width() == actual.width() && expected.height() == actual.height()
                && expected.shapeless() == actual.shapeless() && expected.requiredCapacity() == actual.requiredCapacity()
                && Objects.equals(expected.rule(), actual.rule()), "Shape, material gate and manufacturing rule remain intact");
        helper.assertTrue(ItemStack.matches(expected.output(), actual.output()), "Output count and all output components remain intact");
        helper.assertValueEqual(actual.inputs().size(), expected.inputs().size(), "The full input grid including empty slots remains intact");
        for (int slot = 0; slot < expected.inputs().size(); slot++) {
            helper.assertValueEqual(actual.inputs().get(slot).size(), expected.inputs().get(slot).size(),
                    "No ingredient alternatives are lost or invented at slot " + slot);
            for (int alternative = 0; alternative < expected.inputs().get(slot).size(); alternative++)
                helper.assertTrue(ItemStack.matches(expected.inputs().get(slot).get(alternative), actual.inputs().get(slot).get(alternative)),
                        "Ingredient item counts and components remain intact at slot " + slot);
        }
    }
}
