package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingAccessRules;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real inventory/workbench/recipe-book access checks and material consumption. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FlintWorkbenchGameTests {
    private record PlankCase(Item input, Item output, int amount) {}

    // Every plank-producing vanilla recipe in the pinned 1.21.1 resource jar.
    private static final List<PlankCase> PLANKS = List.of(
            new PlankCase(Items.OAK_LOG, Items.OAK_PLANKS, 4),
            new PlankCase(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS, 4),
            new PlankCase(Items.BIRCH_LOG, Items.BIRCH_PLANKS, 4),
            new PlankCase(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS, 4),
            new PlankCase(Items.ACACIA_LOG, Items.ACACIA_PLANKS, 4),
            new PlankCase(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS, 4),
            new PlankCase(Items.CRIMSON_STEM, Items.CRIMSON_PLANKS, 4),
            new PlankCase(Items.WARPED_STEM, Items.WARPED_PLANKS, 4),
            new PlankCase(Items.MANGROVE_LOG, Items.MANGROVE_PLANKS, 4),
            new PlankCase(Items.BAMBOO_BLOCK, Items.BAMBOO_PLANKS, 2),
            new PlankCase(Items.CHERRY_LOG, Items.CHERRY_PLANKS, 4));

    @GameTest(template = "empty")
    public static void workbenchRecipeAcceptsBothBindersAndOnlyItsMirroredShape(GameTestHelper helper) {
        int accepted = 0;
        int examined = 0;
        for (Item binder : new Item[]{Items.STRING, MiteItems.SINEW.get()}) {
            for (int flint = 0; flint < 4; flint++) {
                for (int binding = 0; binding < 4; binding++) {
                    for (int stick = 0; stick < 4; stick++) {
                        if (flint == binding || flint == stick || binding == stick) continue;
                        int log = 6 - flint - binding - stick;
                        List<ItemStack> grid = emptyGrid(4);
                        grid.set(flint, new ItemStack(Items.FLINT));
                        grid.set(binding, new ItemStack(binder));
                        grid.set(stick, new ItemStack(Items.STICK));
                        grid.set(log, new ItemStack(Items.OAK_LOG));
                        boolean expected = flint / 2 == 0 && binding / 2 == 0 && stick == flint + 2 && log == binding + 2;
                        ItemStack result = resultFor(helper, 2, 2, grid);
                        helper.assertValueEqual(result.is(TimedCraftingTestSupport.flintTableItem()), expected,
                                "Only FB/SL or its horizontal mirror is a workbench: " + flint + "," + binding + "," + stick + "," + log);
                        if (expected) {
                            helper.assertValueEqual(result.getCount(), 1, "Each valid shape yields exactly one workbench");
                            accepted++;
                        }
                        examined++;
                    }
                }
            }
            for (Item log : new Item[]{Items.STRIPPED_OAK_WOOD, Items.CHERRY_LOG, Items.CRIMSON_STEM, Items.STRIPPED_WARPED_HYPHAE}) {
                helper.assertTrue(new ItemStack(log).is(ItemTags.LOGS), "The modern adaptation accepts a member of the log item tag");
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 2; y++) {
                        List<ItemStack> grid = emptyGrid(9);
                        grid.set(x + y * 3, new ItemStack(Items.FLINT));
                        grid.set(x + 1 + y * 3, new ItemStack(binder));
                        grid.set(x + (y + 1) * 3, new ItemStack(Items.STICK));
                        grid.set(x + 1 + (y + 1) * 3, new ItemStack(log));
                        helper.assertTrue(resultFor(helper, 3, 3, grid).is(TimedCraftingTestSupport.flintTableItem()), "The 2x2 recipe can translate inside a real 3x3 input");
                        Collections.swap(grid, x + y * 3, x + 1 + y * 3);
                        Collections.swap(grid, x + (y + 1) * 3, x + 1 + (y + 1) * 3);
                        helper.assertTrue(resultFor(helper, 3, 3, grid).is(TimedCraftingTestSupport.flintTableItem()),
                                "The translated pattern also accepts its horizontal mirror inside a 3x3 input");
                        grid.set((x == 0 ? 2 : 0) + y * 3, new ItemStack(Items.DIRT));
                        helper.assertFalse(resultFor(helper, 3, 3, grid).is(TimedCraftingTestSupport.flintTableItem()), "An unrelated extra ingredient is rejected");
                    }
                }
            }
            for (Item bamboo : new Item[]{Items.BAMBOO_BLOCK, Items.STRIPPED_BAMBOO_BLOCK}) {
                helper.assertFalse(new ItemStack(bamboo).is(ItemTags.LOGS), "Bamboo blocks are not silently added to the log ingredient tag");
                helper.assertFalse(resultFor(helper, 2, 2, List.of(new ItemStack(Items.FLINT), new ItemStack(binder),
                        new ItemStack(Items.STICK), new ItemStack(bamboo))).is(TimedCraftingTestSupport.flintTableItem()),
                        "A bamboo block cannot substitute for the workbench's required log");
            }
            List<ItemStack> remoteBinding = emptyGrid(9);
            remoteBinding.set(0, new ItemStack(Items.FLINT));
            remoteBinding.set(3, new ItemStack(Items.STICK));
            remoteBinding.set(4, new ItemStack(Items.OAK_LOG));
            remoteBinding.set(8, new ItemStack(binder));
            helper.assertFalse(resultFor(helper, 3, 3, remoteBinding).is(TimedCraftingTestSupport.flintTableItem()),
                    "Unlike the hatchet recipe, this workbench cannot move its binding material outside the fixed shape");
        }
        helper.assertValueEqual(examined, 48, "All permutations of four distinct ingredients were checked for both binders");
        helper.assertValueEqual(accepted, 4, "Exactly two mirrored shapes per binder are valid");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("mite:flint_crafting_table")).isPresent(),
                "The independent flint workbench has its own registered recipe ID");
        helper.assertFalse(resultFor(helper, 2, 2, List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS))).is(TimedCraftingTestSupport.flintTableItem()),
                "The former four-plank workbench recipe is no longer usable");
        helper.assertFalse(resultFor(helper, 2, 2, List.of(new ItemStack(MiteItems.FLINT_CHIP.get()), new ItemStack(Items.STRING),
                new ItemStack(Items.STICK), new ItemStack(Items.OAK_LOG))).is(TimedCraftingTestSupport.flintTableItem()), "A chip cannot replace whole flint");
        helper.assertFalse(resultFor(helper, 2, 2, List.of(new ItemStack(Items.FLINT), new ItemStack(Items.LEATHER),
                new ItemStack(Items.STICK), new ItemStack(Items.OAK_LOG))).is(TimedCraftingTestSupport.flintTableItem()), "Leather must first become sinew");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualInventoryCraftingConsumesOneOfEachWorkbenchIngredient(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            InventoryMenu menu = player.inventoryMenu;
            for (Item binder : new Item[]{Items.STRING, MiteItems.SINEW.get()}) {
                resetInventory(player);
                CraftingContainer grid = menu.getCraftSlots();
                grid.setItem(0, new ItemStack(Items.FLINT, 2));
                grid.setItem(1, new ItemStack(binder, 2));
                grid.setItem(2, new ItemStack(Items.STICK, 2));
                grid.setItem(3, new ItemStack(Items.OAK_LOG, 2));
                helper.assertTrue(menu.getSlot(0).getItem().is(TimedCraftingTestSupport.flintTableItem()), "The actual inventory menu publishes the flint-workbench result");
                TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.PICKUP);
                helper.assertTrue(menu.getCarried().isEmpty() && count(player.getInventory(), TimedCraftingTestSupport.flintTableItem()) == 1,
                        "One real manufacturing period delivers exactly one independent flint workbench");
                for (int slot = 0; slot < 4; slot++) helper.assertValueEqual(grid.getItem(slot).getCount(), 1, "One item per ingredient slot is consumed");
                TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.QUICK_MOVE);
                helper.assertValueEqual(count(player.getInventory(), TimedCraftingTestSupport.flintTableItem()), 2, "A real Shift-click transfers only the second workbench");
                helper.assertTrue(grid.isEmpty() && menu.getSlot(0).getItem().isEmpty() && menu.getCarried().isEmpty(),
                        "Exhausted ingredients leave no phantom result or carried item");
                helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "A repeated direct quick move cannot duplicate workbenches");
            }
            for (int[] size : new int[][]{{1, 1}, {2, 2}, {2, 3}, {3, 2}, {3, 3}, {4, 4}}) {
                CraftingContainer grid = new TransientCraftingContainer(menu, size[0], size[1]);
                helper.assertValueEqual(CraftingAccessRules.canCraft(grid, new ItemStack(Items.OAK_PLANKS)), size[0] >= 3 && size[1] >= 3,
                        "Both physical dimensions must reach three for wood processing");
                helper.assertTrue(CraftingAccessRules.canCraft(grid, ItemStack.EMPTY)
                                && CraftingAccessRules.canCraft(grid, new ItemStack(TimedCraftingTestSupport.flintTableItem())),
                        "An empty result and the flint-workbench bootstrap are permitted even in a small grid");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyPlankRecipeAndStaleResultAreBlockedInTheInventory(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            InventoryMenu menu = player.inventoryMenu;
            for (PlankCase test : PLANKS) {
                for (int inputSlot = 0; inputSlot < 4; inputSlot++) {
                    resetInventory(player);
                    menu.getCraftSlots().setItem(inputSlot, new ItemStack(test.input(), 2));
                    helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Every placement of the material has no 2x2 plank result: " + test.input());
                    ItemStack recipeResult = resultFor(helper, 1, 1, List.of(new ItemStack(test.input())));
                    helper.assertTrue(recipeResult.is(test.output()) && recipeResult.getCount() == test.amount(),
                            "The recipe remains globally registered for equipped crafting contexts");

                    // Fault injection tests the final guards, independently of normal result filtering.
                    // The fake result is never transferred by the fixture itself.
                    for (ClickType click : new ClickType[]{ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.THROW}) {
                        menu.getSlot(0).set(new ItemStack(test.output(), test.amount()));
                        helper.assertFalse(menu.getSlot(0).mayPickup(player), "A stale plank result cannot be picked up");
                        menu.clicked(0, 0, click, player);
                        assertRejectedResult(helper, player, test, inputSlot, "Actual menu action " + click);
                    }
                    menu.getSlot(0).set(new ItemStack(test.output(), test.amount()));
                    helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "A direct quickMoveStack call cannot bypass mayPickup");
                    assertRejectedResult(helper, player, test, inputSlot, "Direct quickMoveStack");
                    helper.assertTrue(menu.getSlot(0).safeTake(test.amount(), Integer.MAX_VALUE, player).isEmpty(),
                            "The normal safeTake route is blocked before onTake consumes ingredients");
                    assertRejectedResult(helper, player, test, inputSlot, "Direct safeTake");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeBookPlacementCannotCreateInventoryPlanksOrLoseMaterials(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            InventoryMenu menu = player.inventoryMenu;
            for (PlankCase test : PLANKS) {
                RecipeHolder<CraftingRecipe> recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                        CraftingInput.of(1, 1, List.of(new ItemStack(test.input()))), helper.getLevel()).orElseThrow();
                player.awardRecipes(List.of(recipe));
                helper.assertTrue(player.getRecipeBook().contains(recipe), "The placement test uses an actually unlocked recipe");
                for (boolean craftAll : new boolean[]{false, true}) {
                    resetInventory(player);
                    player.getInventory().setItem(0, new ItemStack(test.input(), 3));
                    menu.handlePlacement(craftAll, recipe, player);
                    helper.assertValueEqual(count(menu.getCraftSlots(), test.input()), craftAll ? 3 : 1,
                            "The actual recipe-book route really moves its requested ingredients into the grid");
                    helper.assertValueEqual(count(menu.getCraftSlots(), test.input()) + count(player.getInventory(), test.input()), 3,
                            "Automatic placement conserves all three input materials");
                    helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Automatic placement still publishes no forbidden plank output");
                    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                    helper.assertValueEqual(count(player.getInventory(), test.output()), 0, "Shift after recipe-book placement cannot obtain planks");
                    menu.removed(player);
                    helper.assertValueEqual(count(player.getInventory(), test.input()), 3, "Closing the inventory returns all unconsumed inputs");
                    helper.assertTrue(menu.getCraftSlots().isEmpty(), "Closing leaves the crafting grid empty");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realWorkbenchProcessesAllPlanksAndKeepsRecipeBookQuickMove(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState previous = helper.getLevel().getBlockState(table);
        helper.getLevel().setBlockAndUpdate(table, TimedCraftingTestSupport.flintTable().defaultBlockState());
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            try {
                for (PlankCase test : PLANKS) {
                    player.getInventory().clearContent();
                    menu.clearCraftingContent();
                    menu.setCarried(ItemStack.EMPTY);
                    menu.getSlot(1).set(new ItemStack(test.input(), 2));
                    helper.assertTrue(menu.getSlot(0).getItem().is(test.output()), "A physical 3x3 menu publishes the retained plank recipe");
                    helper.assertFalse(menu.getSlot(0).mayPickup(player), "The unfinished timed result cannot be picked up immediately");
                    TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.PICKUP);
                    helper.assertTrue(menu.getCarried().isEmpty() && count(player.getInventory(), test.output()) == test.amount(),
                            "One real manufacturing period delivers the original plank count");
                    helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "Normal crafting consumes exactly one material");
                    TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.QUICK_MOVE);
                    helper.assertValueEqual(count(player.getInventory(), test.output()), test.amount() * 2,
                            "Workbench Shift-click transfers precisely the remaining craft");
                    helper.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(0).getItem().isEmpty(), "Both input materials are consumed once");
                }
                player.getInventory().clearContent();
                menu.clearCraftingContent();
                player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));
                RecipeHolder<CraftingRecipe> recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                        CraftingInput.of(1, 1, List.of(new ItemStack(Items.OAK_LOG))), helper.getLevel()).orElseThrow();
                player.awardRecipes(List.of(recipe));
                menu.handlePlacement(true, recipe, player);
                helper.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS), "Workbench finishPlacingRecipe also permits the result");
                TimedCraftingTestSupport.craftBatches(helper, player, menu, 3);
                helper.assertValueEqual(count(player.getInventory(), Items.OAK_PLANKS), 12, "Recipe-book craft-all followed by actual Shift consumes three logs for twelve planks");
                helper.assertValueEqual(count(player.getInventory(), Items.OAK_LOG), 0, "No input log remains after equipped bulk crafting");
            } finally {
                menu.removed(player);
                player.containerMenu = player.inventoryMenu;
            }
        } finally {
            helper.getLevel().setBlockAndUpdate(table, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primitiveWorkbenchDismantlingReturnsBothKnifeAndWoodAfterItsPeriod(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            for (String material : new String[]{"flint", "obsidian"}) {
                resetInventory(player);
                Item table = io.github.ningmeng123.mite.registry.MiteTools.get(material + "_crafting_table");
                Item knife = io.github.ningmeng123.mite.registry.MiteTools.get(material + "_knife");
                InventoryMenu menu = player.inventoryMenu;
                menu.getCraftSlots().setItem(0, new ItemStack(table));
                helper.assertTrue(menu.getSlot(0).getItem().is(knife), "The primitive table dismantles to its own material knife");
                var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                        menu.getCraftSlots().asCraftInput(), helper.getLevel()).orElseThrow();
                var ops = helper.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                var encoded = net.minecraft.world.item.crafting.Recipe.CODEC.encodeStart(ops, recipe.value()).getOrThrow();
                helper.assertTrue(net.minecraft.world.item.crafting.Recipe.CODEC.parse(ops, encoded).getOrThrow()
                                instanceof io.github.ningmeng123.mite.crafting.WorkbenchDismantleRecipe,
                        "The custom remaining-item recipe survives the real recipe codec");
                TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.PICKUP);
                helper.assertValueEqual(count(player.getInventory(), knife), 1, "One completed period returns exactly one knife");
                helper.assertTrue(menu.getCraftSlots().getItem(0).is(Items.OAK_LOG)
                                && menu.getCraftSlots().getItem(0).getCount() == 1,
                        "The original wooden body is returned as one oak log under the unified modern wood mapping");
                helper.assertValueEqual(count(player.getInventory(), table) + count(menu.getCraftSlots(), table), 0,
                        "The dismantled table has been consumed exactly once");
                helper.assertTrue(menu.getCarried().isEmpty(), "No duplicate result is left on the cursor");
            }
        }
        helper.succeed();
    }

    private static List<ItemStack> emptyGrid(int size) {
        return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
    }

    private static ItemStack resultFor(GameTestHelper helper, int width, int height, List<ItemStack> grid) {
        CraftingInput input = CraftingInput.of(width, height, grid);
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(recipe -> recipe.value().assemble(input, helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }

    private static void resetInventory(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
        player.getInventory().clearContent();
        player.inventoryMenu.clearCraftingContent();
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
    }

    private static int count(Container container, Item item) {
        int amount = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) amount += container.getItem(slot).getCount();
        }
        return amount;
    }

    private static void assertRejectedResult(GameTestHelper helper, ServerPlayer player, PlankCase test, int inputSlot, String action) {
        InventoryMenu menu = player.inventoryMenu;
        helper.assertTrue(menu.getCarried().isEmpty(), action + " cannot put forbidden planks on the cursor");
        helper.assertValueEqual(count(player.getInventory(), test.output()), 0, action + " cannot put forbidden planks in inventory or hotbar");
        helper.assertValueEqual(menu.getCraftSlots().getItem(inputSlot).getCount(), 2, action + " cannot consume blocked ingredients");
        helper.assertTrue(menu.getSlot(0).getItem().is(test.output()) && menu.getSlot(0).getItem().getCount() == test.amount(),
                action + " rejects the stale output before removing or dropping any part of it");
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-workbench"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            return new PlayerSession(player, channel);
        } catch (RuntimeException | Error failure) {
            ServerPlayer registered = helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID());
            if (registered != null) helper.getLevel().getServer().getPlayerList().remove(registered);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record PlayerSession(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override
        public void close() {
            try {
                ServerPlayer registered = player.server.getPlayerList().getPlayer(player.getUUID());
                if (registered != null) player.server.getPlayerList().remove(registered);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}
