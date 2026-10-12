package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded recipes, real menu consumption and a redstone-driven crafter. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FlintRecipeGameTests {
    @GameTest(template = "empty")
    public static void materialConversionsLoadAndKeepLiteralCounts(GameTestHelper helper) {
        assertRecipe(helper, "flint_from_chips", 2, 2,
                List.of(chip(), chip(), chip(), chip()), Items.FLINT, 1);
        assertRecipe(helper, "flint_chips_from_flint", 1, 1,
                List.of(new ItemStack(Items.FLINT)), MiteItems.FLINT_CHIP.get(), 4);
        assertRecipe(helper, "sinew_from_leather", 1, 1,
                List.of(new ItemStack(Items.LEATHER)), MiteItems.SINEW.get(), 4);
        helper.assertValueEqual(hatchetRecipes(helper).size(), 13, "Thirteen ordinary shaped hatchet recipes are loaded");
        for (RecipeHolder<CraftingRecipe> recipe : hatchetRecipes(helper)) {
            helper.assertTrue(recipe.value() instanceof ShapedRecipe, "Every variant uses the vanilla shaped serializer");
            helper.assertValueEqual(recipe.value().getGroup(), "mite:flint_hatchet", "All variants share a recipe-book group");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyBindingPlacementMatchesOnlyTheR196Skeleton(GameTestHelper helper) {
        List<RecipeHolder<CraftingRecipe>> recipes = hatchetRecipes(helper);
        int accepted = 0;
        int inspected = 0;
        for (int width : new int[]{2, 3}) {
            int size = width * width;
            for (Item binder : new Item[]{Items.STRING, MiteItems.SINEW.get()}) {
                for (int flint = 0; flint < size; flint++) {
                    for (int stickA = 0; stickA < size; stickA++) {
                        for (int stickB = stickA + 1; stickB < size; stickB++) {
                            if (flint == stickA || flint == stickB) continue;
                            for (int binding = 0; binding < size; binding++) {
                                if (binding == flint || binding == stickA || binding == stickB) continue;
                                List<ItemStack> grid = emptyGrid(size);
                                grid.set(flint, new ItemStack(Items.FLINT));
                                grid.set(stickA, new ItemStack(Items.STICK));
                                grid.set(stickB, new ItemStack(Items.STICK));
                                grid.set(binding, new ItemStack(binder));
                                CraftingInput input = CraftingInput.of(width, width, grid);
                                // Independent spatial oracle: the sticks form a vertical pair,
                                // with the flint immediately beside their upper endpoint.
                                boolean expected = stickA % width == stickB % width
                                        && stickB / width == stickA / width + 1
                                        && flint / width == stickA / width
                                        && Math.abs(flint % width - stickA % width) == 1;
                                boolean actual = recipes.stream().anyMatch(r -> r.value().matches(input, helper.getLevel()));
                                helper.assertTrue(actual == expected, "Binding layout " + width + ": " + flint + ","
                                        + stickA + "," + stickB + "," + binding + " has the expected result");
                                if (expected) {
                                    ItemStack result = helper.getLevel().getRecipeManager()
                                            .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow()
                                            .value().assemble(input, helper.getLevel().registryAccess());
                                    helper.assertTrue(result.is(MiteItems.FLINT_HATCHET.get()) && result.getCount() == 1,
                                            "The complete RecipeManager selects one hatchet for every valid layout");
                                    accepted++;
                                }
                                inspected++;
                            }
                        }
                    }
                }
            }
        }
        helper.assertValueEqual(inspected, 3048, "All distinct placements of the four ingredients were inspected");
        helper.assertValueEqual(accepted, 100, "Four 2x2 and ninety-six 3x3 layouts accept both binders");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicateBindingAndExtraMaterialsCannotCreateAHatchet(GameTestHelper helper) {
        List<ItemStack> grid = emptyGrid(9);
        grid.set(0, new ItemStack(Items.FLINT));
        grid.set(1, new ItemStack(Items.STICK));
        grid.set(4, new ItemStack(Items.STICK));
        assertNoHatchet(helper, grid, "The binder is required");
        grid.set(8, new ItemStack(Items.STRING));
        grid.set(3, new ItemStack(MiteItems.SINEW.get()));
        assertNoHatchet(helper, grid, "Two occupied binding slots are rejected, even with different binders");
        grid.set(3, new ItemStack(Items.DIRT));
        assertNoHatchet(helper, grid, "An unrelated extra item is rejected");
        grid.set(3, ItemStack.EMPTY);
        grid.set(8, new ItemStack(Items.LEATHER));
        assertNoHatchet(helper, grid, "Leather must first be processed into sinew");
        grid.set(8, new ItemStack(Items.STRING));
        grid.set(0, chip());
        assertNoHatchet(helper, grid, "One chip cannot stand in for one whole flint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryQuickMoveConsumesOneFromEachOccupiedSlot(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.getInventory().clearContent();
            InventoryMenu menu = player.inventoryMenu;
            CraftingContainer grid = menu.getCraftSlots();
            grid.setItem(0, new ItemStack(Items.FLINT, 2));
            grid.setItem(1, new ItemStack(Items.STICK, 2));
            grid.setItem(2, new ItemStack(MiteItems.SINEW.get(), 2));
            grid.setItem(3, new ItemStack(Items.STICK, 2));
            helper.assertTrue(menu.getSlot(0).getItem().is(MiteItems.FLINT_HATCHET.get()), "The real InventoryMenu computes a result");
            ItemStack moved = menu.quickMoveStack(player, 0);
            helper.assertTrue(moved.is(MiteItems.FLINT_HATCHET.get()) && moved.getCount() == 1, "Quick move returns exactly one crafted hatchet");
            helper.assertValueEqual(count(player.getInventory(), MiteItems.FLINT_HATCHET.get()), 1, "Exactly one output reaches inventory");
            for (int slot = 0; slot < 4; slot++) {
                helper.assertValueEqual(grid.getItem(slot).getCount(), 1, "Exactly one input is consumed from slot " + slot);
            }
            menu.quickMoveStack(player, 0);
            helper.assertValueEqual(count(player.getInventory(), MiteItems.FLINT_HATCHET.get()), 2, "A second quick move creates only the second hatchet");
            helper.assertTrue(grid.isEmpty() && menu.getSlot(0).getItem().isEmpty(), "Exhausted ingredients leave no phantom result");
            helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "An extra quick move cannot duplicate the result");

            for (int slot = 0; slot < 4; slot++) grid.setItem(slot, new ItemStack(MiteItems.FLINT_CHIP.get(), 2));
            menu.quickMoveStack(player, 0);
            helper.assertValueEqual(count(player.getInventory(), Items.FLINT), 1, "Four occupied chip slots yield one flint");
            for (int slot = 0; slot < 4; slot++) {
                helper.assertValueEqual(grid.getItem(slot).getCount(), 1, "Each chip slot consumes one, not the legacy recipe's stored stack count");
            }
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void redstoneCrafterConsumesRemoteBindingAndOutputsOneHatchet(GameTestHelper helper) {
        BlockPos crafterPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(2, 1, 1);
        helper.setBlock(crafterPos, Blocks.CRAFTER.defaultBlockState()
                .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.EAST_UP));
        helper.setBlock(chestPos, Blocks.CHEST);
        CrafterBlockEntity crafter = (CrafterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(crafterPos));
        crafter.setItem(0, new ItemStack(Items.FLINT, 2));
        crafter.setItem(1, new ItemStack(Items.STICK, 2));
        crafter.setItem(4, new ItemStack(Items.STICK, 2));
        crafter.setItem(8, new ItemStack(Items.STRING, 2));
        helper.pulseRedstone(new BlockPos(0, 1, 1), 5);
        helper.runAfterDelay(9, () -> {
            Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
            helper.assertValueEqual(count(chest, MiteItems.FLINT_HATCHET.get()), 1, "A real redstone pulse creates one hatchet in the output chest");
            for (int slot = 0; slot < 9; slot++) {
                int expected = slot == 0 || slot == 1 || slot == 4 || slot == 8 ? 1 : 0;
                helper.assertValueEqual(crafter.getItem(slot).getCount(), expected, "The crafter consumes one per occupied input slot " + slot);
            }
            helper.succeed();
        });
    }

    private static void assertRecipe(GameTestHelper helper, String id, int width, int height,
            List<ItemStack> items, Item result, int amount) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("mite", id)).isPresent(),
                "The recipe is loaded under its stable ID: " + id);
        CraftingInput input = CraftingInput.of(width, height, items);
        RecipeHolder<CraftingRecipe> recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        ItemStack output = recipe.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(output.is(result) && output.getCount() == amount, "Literal output for " + id);
    }

    private static List<RecipeHolder<CraftingRecipe>> hatchetRecipes(GameTestHelper helper) {
        return helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(r -> r.id().getNamespace().equals("mite") && r.id().getPath().startsWith("flint_hatchet")).toList();
    }

    private static void assertNoHatchet(GameTestHelper helper, List<ItemStack> grid, String message) {
        CraftingInput input = CraftingInput.of(3, 3, grid);
        helper.assertTrue(hatchetRecipes(helper).stream().noneMatch(r -> r.value().matches(input, helper.getLevel())), message);
    }

    private static List<ItemStack> emptyGrid(int size) {
        return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
    }

    private static ItemStack chip() {
        return new ItemStack(MiteItems.FLINT_CHIP.get());
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) total += container.getItem(slot).getCount();
        }
        return total;
    }
}
