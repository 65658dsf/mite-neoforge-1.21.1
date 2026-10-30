package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.crafting.BulkMaterialRecipe;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Material identities, exact conversion consumption and legal inventory stack sizes. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MetalMaterialGameTests {
    private record Metal(String name, Item nugget, Item ingot, String namespace) {}
    private record BlockConversion(String recipe, Item block, Item ingot) {}

    private static List<Metal> metals() {
        return List.of(
                new Metal("copper", MiteMaterials.COPPER_NUGGET.get(), Items.COPPER_INGOT, "mite"),
                new Metal("silver", MiteMaterials.SILVER_NUGGET.get(), MiteMaterials.SILVER_INGOT.get(), "mite"),
                new Metal("gold", Items.GOLD_NUGGET, Items.GOLD_INGOT, "minecraft"),
                new Metal("iron", Items.IRON_NUGGET, Items.IRON_INGOT, "minecraft"),
                new Metal("ancient_metal", MiteMaterials.ANCIENT_METAL_NUGGET.get(), MiteMaterials.ANCIENT_METAL_INGOT.get(), "mite"),
                new Metal("mithril", MiteMaterials.MITHRIL_NUGGET.get(), MiteMaterials.MITHRIL_INGOT.get(), "mite"),
                new Metal("adamantium", MiteMaterials.ADAMANTIUM_NUGGET.get(), MiteMaterials.ADAMANTIUM_INGOT.get(), "mite"));
    }

    private static List<BlockConversion> blocks() {
        return List.of(new BlockConversion("copper_ingot", Items.COPPER_BLOCK, Items.COPPER_INGOT),
                new BlockConversion("copper_ingot_from_waxed_copper_block", Items.WAXED_COPPER_BLOCK, Items.COPPER_INGOT),
                new BlockConversion("iron_ingot_from_iron_block", Items.IRON_BLOCK, Items.IRON_INGOT),
                new BlockConversion("gold_ingot_from_gold_block", Items.GOLD_BLOCK, Items.GOLD_INGOT));
    }

    @GameTest(template = "empty")
    public static void sevenMetalIdentitiesStackLimitsAndFullPackingShapes(GameTestHelper helper) {
        for (Metal metal : metals()) {
            helper.assertValueEqual(new ItemStack(metal.nugget()).getMaxStackSize(), 64, metal.name() + " nuggets stack to 64");
            helper.assertValueEqual(new ItemStack(metal.ingot()).getMaxStackSize(), 8, metal.name() + " ingots stack to 8");
            ItemStack saved = new ItemStack(metal.ingot(), 8);
            ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), saved.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertTrue(ItemStack.isSameItemSameComponents(saved, loaded) && loaded.getCount() == 8,
                    metal.name() + " preserves item identity and stack components through storage");
            CraftingInput full = CraftingInput.of(3, 3, grid(metal.nugget(), 9, 1));
            RecipeHolder<CraftingRecipe> recipe = recipeFor(helper, full);
            helper.assertValueEqual(recipe.id(), ResourceLocation.fromNamespaceAndPath(metal.namespace(), metal.name() + "_ingot_from_nuggets"),
                    "Existing iron/gold packing recipe IDs are replaced rather than duplicated");
            helper.assertTrue(recipe.value().assemble(full, helper.getLevel().registryAccess()).is(metal.ingot()), "Nine equal nuggets make their ingot");
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                    CraftingInput.of(2, 2, grid(metal.nugget(), 4, 1)), helper.getLevel()).isEmpty(), "Four nuggets cannot compress in a 2x2 inventory grid");
            List<ItemStack> mixed = grid(metal.nugget(), 9, 1);
            mixed.set(4, new ItemStack(metal.nugget() == Items.IRON_NUGGET ? Items.GOLD_NUGGET : Items.IRON_NUGGET));
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                    CraftingInput.of(3, 3, mixed), helper.getLevel()).isEmpty(), "Mixed metals cannot satisfy the shaped packing recipe");
        }
        helper.assertValueEqual(new ItemStack(Items.RAW_COPPER).getMaxStackSize(), 4, "Modern raw copper follows the explicit source-metal family stack adaptation");
        helper.assertValueEqual(new ItemStack(Items.NETHERITE_INGOT).getMaxStackSize(), 8, "Modern netherite retains the eight-ingot family stack limit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSevenIngotsUnpackInInventoryUsingNormalAndShiftClicks(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            for (Metal metal : metals()) {
                reset(player);
                int initialExperience = player.totalExperience;
                InventoryMenu menu = player.inventoryMenu;
                menu.getCraftSlots().setItem(0, new ItemStack(metal.ingot(), 2));
                String path = metal.namespace().equals("minecraft") ? metal.name() + "_nugget" : metal.name() + "_nuggets_from_ingot";
                helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(metal.namespace(), path)).isPresent(),
                        "The expected unpacking recipe was loaded");
                helper.assertTrue(menu.getSlot(0).getItem().is(metal.nugget()) && menu.getSlot(0).getItem().getCount() == 9,
                        "A real 2x2 inventory result shows nine nuggets");
                TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.PICKUP);
                helper.assertTrue(menu.getCarried().isEmpty() && count(player.getInventory(), metal.nugget()) == 9, "The completed first manufacturing period delivers exactly nine nuggets");
                helper.assertValueEqual(menu.getCraftSlots().getItem(0).getCount(), 1, "Normal click consumes exactly one ingot");
                TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.QUICK_MOVE);
                helper.assertValueEqual(count(player.getInventory(), metal.nugget()), 18, "Shift consumes the second ingot for exactly nine more nuggets");
                helper.assertTrue(menu.getCraftSlots().isEmpty() && menu.getSlot(0).getItem().isEmpty(), "Both ingredients have been consumed once");
                helper.assertValueEqual(player.totalExperience, initialExperience, "Material conversion has no XP cost or award");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSevenNuggetsPackAtWorkbenchWithExactBulkConsumption(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState old = helper.getLevel().getBlockState(table);
        helper.getLevel().setBlockAndUpdate(table, TimedCraftingTestSupport.flintTable().defaultBlockState());
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            try {
                for (Metal metal : metals()) {
                    player.getInventory().clearContent();
                    menu.clearCraftingContent();
                    menu.setCarried(ItemStack.EMPTY);
                    for (int slot = 1; slot <= 9; slot++) menu.getSlot(slot).set(new ItemStack(metal.nugget(), 10));
                    helper.assertTrue(menu.getSlot(0).getItem().is(metal.ingot()), "Real workbench publishes the ingot");
                    TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.PICKUP);
                    helper.assertTrue(menu.getCarried().isEmpty() && count(player.getInventory(), metal.ingot()) == 1, "One complete timed batch packs one ingot");
                    for (int slot = 1; slot <= 9; slot++) helper.assertValueEqual(menu.getSlot(slot).getItem().getCount(), 9, "One nugget per grid cell was consumed");
                    TimedCraftingTestSupport.craftBatches(helper, player, menu, 9);
                    helper.assertValueEqual(count(player.getInventory(), metal.ingot()), 10, "Ninety nuggets pack into exactly ten ingots");
                    assertLegalStacks(helper, player.getInventory());
                    for (int slot = 0; slot <= 9; slot++) helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "Bulk packing empties all ingredients and the result");
                }
                for (BlockConversion block : blocks()) {
                    player.getInventory().clearContent();
                    menu.clearCraftingContent();
                    menu.getSlot(1).set(new ItemStack(block.block(), 2));
                    TimedCraftingTestSupport.craftBatches(helper, player, menu, 2);
                    helper.assertValueEqual(count(player.getInventory(), block.ingot()), 18, "Workbench Shift fills multiple empty slots with both complete bulk batches");
                    helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "Workbench bulk transfer consumes both blocks once");
                    assertLegalStacks(helper, player.getInventory());
                }
            } finally {
                menu.removed(player);
                player.containerMenu = player.inventoryMenu;
            }
        } finally {
            helper.getLevel().setBlockAndUpdate(table, old);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeBlockUnpackingLoadsAndNormalShiftRecipeBookConserveNine(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            for (BlockConversion block : blocks()) {
                reset(player);
                InventoryMenu menu = player.inventoryMenu;
                CraftingInput input = CraftingInput.of(1, 1, List.of(new ItemStack(block.block())));
                RecipeHolder<CraftingRecipe> recipe = recipeFor(helper, input);
                helper.assertValueEqual(recipe.id(), ResourceLocation.withDefaultNamespace(block.recipe()), "Every original bulk recipe ID remains loaded");
                helper.assertTrue(recipe.value() instanceof BulkMaterialRecipe && !recipe.value().isSpecial(), "Bulk recipes remain visible to the normal crafting book");
                var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
                var encoded = Recipe.CODEC.encodeStart(ops, recipe.value()).getOrThrow();
                Recipe<?> decoded = Recipe.CODEC.parse(ops, encoded).getOrThrow();
                helper.assertTrue(decoded instanceof BulkMaterialRecipe && decoded.getResultItem(helper.getLevel().registryAccess()).getCount() == 9,
                        "Actual registered recipe codec accepts nine ingots without a STRICT_CODEC load error");
                menu.getCraftSlots().setItem(0, new ItemStack(block.block(), 2));
                TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.PICKUP);
                helper.assertTrue(menu.getCarried().isEmpty(), "Completed manufacture routes the whole result to inventory, never an oversized cursor");
                helper.assertValueEqual(count(player.getInventory(), block.ingot()), 9, "Normal manufacture stores the legal eight-plus-one split");
                assertLegalStacks(helper, player.getInventory());
                TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.QUICK_MOVE);
                helper.assertValueEqual(count(player.getInventory(), block.ingot()), 18, "Shift keeps the full next nine-ingot batch in inventory");
                helper.assertTrue(menu.getCraftSlots().isEmpty(), "Exactly two blocks have been consumed");
                assertLegalStacks(helper, player.getInventory());

                reset(player);
                player.getInventory().setItem(0, new ItemStack(block.block(), 2));
                player.awardRecipes(List.of(recipe));
                menu.handlePlacement(true, recipe, player);
                helper.assertTrue(menu.getSlot(0).getItem().is(block.ingot()), "Recipe-book placement resolves the custom recipe");
                TimedCraftingTestSupport.craftBatches(helper, player, menu, 2);
                helper.assertValueEqual(count(player.getInventory(), block.ingot()), 18, "Craft-all from empty inventory produces all eighteen ingots");
                helper.assertValueEqual(count(player.getInventory(), block.block()), 0, "Recipe-book bulk conversion consumes exactly two blocks");
                assertLegalStacks(helper, player.getInventory());
            }
            for (Metal metal : metals()) {
                helper.assertTrue(BulkMaterialRecipe.isValidResult(new ItemStack(metal.ingot(), 9)), "Bulk exception includes each of the seven ingots");
                helper.assertFalse(BulkMaterialRecipe.isValidResult(new ItemStack(metal.ingot(), 10)), "The codec exception does not allow arbitrary oversized counts");
                helper.assertFalse(BulkMaterialRecipe.isValidResult(new ItemStack(metal.nugget(), 9)), "Ordinary nugget results need no bulk exception");
            }
            var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
            BulkMaterialRecipe invalid = new BulkMaterialRecipe("invalid", Ingredient.of(Items.DIRT), new ItemStack(Items.DIRT, 9));
            helper.assertTrue(Recipe.CODEC.encodeStart(ops, invalid).error().isPresent(), "Codec validation rejects unrelated item outputs");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void actualCrafterBulkUnpackingSplitsNineWithoutDroppingAnIngot(GameTestHelper helper) {
        BlockPos crafterPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(2, 1, 1);
        helper.setBlock(crafterPos, Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.EAST_UP));
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(crafterPos.south(), TimedCraftingTestSupport.flintTable());
        CrafterBlockEntity crafter = (CrafterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(crafterPos));
        crafter.setItem(0, new ItemStack(Items.COPPER_BLOCK, 2));
        helper.pulseRedstone(new BlockPos(0, 1, 1), 5);
        int period = io.github.ningmeng123.mite.crafting.TimedCraftingRules.period(3600F, 0, .2F, false);
        helper.runAfterDelay(period, () -> {
            Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
            helper.assertValueEqual(count(chest, Items.COPPER_INGOT), 0, "The real Crafter does not publish a batch before its manufacturing period");
        });
        helper.runAfterDelay(period + 9, () -> {
            Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
            helper.assertValueEqual(count(chest, Items.COPPER_INGOT), 9, "One real Crafter pulse outputs all nine ingots");
            helper.assertValueEqual(crafter.getItem(0).getCount(), 1, "Automation consumes one block per pulse");
            assertLegalStacks(helper, chest);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void resultSwapsCannotBypassTimingAndCompletedBatchesSplitLegallyInBothMenus(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState old = helper.getLevel().getBlockState(table);
        helper.getLevel().setBlockAndUpdate(table, TimedCraftingTestSupport.flintTable().defaultBlockState());
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            for (boolean workbench : new boolean[]{false, true}) {
                AbstractContainerMenu menu = workbench
                        ? new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table)) : player.inventoryMenu;
                player.containerMenu = menu;
                try {
                    for (BlockConversion block : blocks()) {
                        for (int target : new int[]{0, 40}) {
                            player.getInventory().clearContent();
                            clearCrafting(menu);
                            int craftedBefore = player.getStats().getValue(Stats.ITEM_CRAFTED.get(block.ingot()));
                            menu.getSlot(1).set(new ItemStack(block.block(), 2));
                            menu.clicked(0, target, ClickType.SWAP, player);
                            helper.assertValueEqual(count(player.getInventory(), block.ingot()), 0, "Hotbar/offhand result swaps cannot bypass manufacturing");
                            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 2, "Rejected instant swaps consume nothing");
                            TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.PICKUP);
                            helper.assertValueEqual(count(player.getInventory(), block.ingot()), 9, "One completed period preserves the entire nine-ingot batch");
                            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "One period consumes one block, not one block per split");
                            helper.assertValueEqual(player.getStats().getValue(Stats.ITEM_CRAFTED.get(block.ingot())) - craftedBefore, 9,
                                    "Craft statistics retain the whole batch count once");
                            assertLegalStacks(helper, player.getInventory());
                            menu.clicked(0, target, ClickType.SWAP, player);
                            helper.assertValueEqual(count(player.getInventory(), block.ingot()), 9, "Repeated result swaps cannot take the next batch early");
                            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "Rejected swaps never consume the next block");
                        }
                    }
                } finally {
                    clearCrafting(menu);
                    if (workbench) menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
            }
        } finally {
            helper.getLevel().setBlockAndUpdate(table, old);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryTimedTransfersConserveCompletedBatchesAndRejectInstantSwaps(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState old = helper.getLevel().getBlockState(table);
        helper.getLevel().setBlockAndUpdate(table, TimedCraftingTestSupport.flintTable().defaultBlockState());
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            for (boolean workbench : new boolean[]{false, true}) {
                AbstractContainerMenu menu = workbench
                        ? new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table)) : player.inventoryMenu;
                player.containerMenu = menu;
                try (var spawned = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity
                        && entity.getBoundingBox().intersects(player.getBoundingBox().inflate(2)))) {
                    player.getInventory().clearContent();
                    for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 4));
                    menu.getSlot(1).set(new ItemStack(Items.COPPER_BLOCK, 2));
                    TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.QUICK_MOVE);
                    helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "A completed full-inventory manufacturing period consumes one block");
                    helper.assertValueEqual(count(player.getInventory(), Items.COPPER_INGOT), 0, "A full inventory has no illegal ingot stack");
                    // Capture the real synchronous join event; the random-grid chunk's entity query may not be visible until the next tick.
                    helper.assertValueEqual(spawned.live(ItemEntity.class).stream().filter(item -> item.getItem().is(Items.COPPER_INGOT))
                            .mapToInt(item -> item.getItem().getCount()).sum(), 9, "Exactly the completed nine-ingot batch drops when no storage exists");
                    spawned.live(ItemEntity.class).forEach(ItemEntity::discard);
                    player.getInventory().setItem(8, ItemStack.EMPTY);
                    TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.QUICK_MOVE);
                    helper.assertValueEqual(count(player.getInventory(), Items.COPPER_INGOT), 8, "A single free slot receives eight ingots");
                    helper.assertValueEqual(spawned.live(ItemEntity.class).stream().filter(item -> item.getItem().is(Items.COPPER_INGOT))
                            .mapToInt(item -> item.getItem().getCount()).sum(), 1, "Only the remaining one ingot drops");
                    helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "Each of the two completed periods consumes exactly one input");
                    assertLegalStacks(helper, player.getInventory());
                    spawned.live(ItemEntity.class).forEach(ItemEntity::discard);
                    clearCrafting(menu);
                    for (int target : new int[]{0, 40}) {
                        player.getInventory().clearContent();
                        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 4));
                        player.getInventory().setItem(target, ItemStack.EMPTY);
                        menu.getSlot(1).set(new ItemStack(Items.COPPER_BLOCK));
                        menu.clicked(0, target, ClickType.SWAP, player);
                        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "Even with no space, an instantaneous result swap cannot consume the block");
                        helper.assertValueEqual(spawned.live(ItemEntity.class).stream().filter(item -> item.getItem().is(Items.COPPER_INGOT))
                                .mapToInt(item -> item.getItem().getCount()).sum(), 0, "Rejected swaps cannot create a free dropped batch");
                        clearCrafting(menu);
                    }
                } finally {
                    clearCrafting(menu);
                    if (workbench) menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
            }
        } finally {
            helper.getLevel().setBlockAndUpdate(table, old);
        }
        helper.succeed();
    }

    private static void clearCrafting(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu inventory) inventory.clearCraftingContent();
        else ((CraftingMenu) menu).clearCraftingContent();
        menu.setCarried(ItemStack.EMPTY);
    }

    private static List<ItemStack> grid(Item item, int slots, int count) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) result.add(new ItemStack(item, count));
        return result;
    }

    private static RecipeHolder<CraftingRecipe> recipeFor(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
    }

    private static int count(Container inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(item)) count += inventory.getItem(slot).getCount();
        return count;
    }

    private static void assertLegalStacks(GameTestHelper helper, Container inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            helper.assertTrue(stack.isEmpty() || stack.getCount() <= stack.getMaxStackSize(), "A persistent inventory slot never exceeds the item's stack limit");
        }
    }

    private static void reset(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
        player.getInventory().clearContent();
        player.inventoryMenu.clearCraftingContent();
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-materials"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            return new PlayerSession(player, channel);
        } catch (RuntimeException | Error failure) {
            ServerPlayer registered = player.server.getPlayerList().getPlayer(player.getUUID());
            if (registered != null) player.server.getPlayerList().remove(registered);
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
