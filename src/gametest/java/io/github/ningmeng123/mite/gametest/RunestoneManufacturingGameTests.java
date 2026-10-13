package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.block.RunestoneBlock;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.item.RunestoneItem;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded source recipes, physical bench gates, actual right-click and timed consumption. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class RunestoneManufacturingGameTests {
    private record RuneCase(String metal, String priorBench, Item nugget, RunestoneItem item, RunestoneBlock block,
                            float difficulty, int sourceOrder) {}
    private static List<RuneCase> runes() {
        return List.of(new RuneCase("mithril", "ancient_metal", MiteMaterials.MITHRIL_NUGGET.get(), MiteWorldBlocks.MITHRIL_RUNESTONE_ITEM.get(),
                        MiteWorldBlocks.MITHRIL_RUNESTONE.get(), 3084.4443F, 102),
                new RuneCase("adamantium", "mithril", MiteMaterials.ADAMANTIUM_NUGGET.get(), MiteWorldBlocks.ADAMANTIUM_RUNESTONE_ITEM.get(),
                        MiteWorldBlocks.ADAMANTIUM_RUNESTONE.get(), 11617.777F, 103));
    }

    @GameTest(template = "empty")
    public static void bothLoadedRuneRecipesUseTheSourceCrossAndStartAtNul(GameTestHelper helper) {
        for (RuneCase r : runes()) {
            CraftingInput input = CraftingInput.of(3, 3, runeInputs(r, 1));
            List<RecipeHolder<CraftingRecipe>> matches = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(recipe -> recipe.value().matches(input, helper.getLevel())).toList();
            helper.assertValueEqual(matches.size(), 1, "Each metal has one source base recipe, rather than sixteen duplicate ingredient layouts");
            helper.assertValueEqual(matches.getFirst().id(), ResourceLocation.parse("mite:" + r.metal() + "_runestone"), "The rune recipe has a stable ID");
            ItemStack output = matches.getFirst().value().assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(output.is(r.item()) && output.getCount() == 1, "Four nuggets and one central obsidian produce one runestone");
            helper.assertValueEqual(RunestoneItem.rune(output), 0, "The raw recipe begins at Nul metadata zero");
            helper.assertValueEqual(output.getMaxStackSize(), 4, "Runestone items keep the source stack limit four");
            var rule = TimedCraftingRecipes.get(matches.getFirst().id());
            helper.assertValueEqual(rule.difficulty(), r.difficulty(), "Difficulty comes from independent R196 runtime recipe data");
            helper.assertValueEqual(rule.requiredBench(), r.metal(), "The same material workbench is required");
            helper.assertValueEqual(rule.sourceOrder(), r.sourceOrder(), "The source registration order remains traceable");
            List<ItemStack> invalid = runeInputs(r, 1);
            invalid.set(8, new ItemStack(r.nugget()));
            helper.assertFalse(matches.getFirst().value().matches(CraftingInput.of(3, 3, invalid), helper.getLevel()), "Extra corner material cannot match the cross");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rightClickCyclesAllSixteenSymbolsWithoutMaterialPayment(GameTestHelper helper) {
        for (RuneCase r : runes()) try (Fixture f = new Fixture(helper, r.metal())) {
            f.load(r, 2);
            for (int selection = 1; selection <= 16; selection++) {
                f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
                helper.assertValueEqual(RunestoneItem.rune(f.menu.getSlot(0).getItem()), selection % 16, "Actual output-slot right click cycles then wraps at sixteen");
                f.assertUnspent(2);
                helper.assertTrue(f.player.getInventory().isEmpty() && f.menu.getCarried().isEmpty(), "Selecting a symbol gives no output or cursor item");
                helper.assertFalse(TimedCraftingController.isActive(f.player), "Selection does not start manufacturing");
            }
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            f.grid.setItem(4, new ItemStack(Items.OBSIDIAN, 3));
            TimedCraftingController.period(f.player, f.menu);
            helper.assertValueEqual(RunestoneItem.rune(f.menu.getSlot(0).getItem()), 1, "Changing only ingredient quantity keeps selection, as required for continuous batches");
            f.grid.setItem(4, new ItemStack(Items.DIRT, 3));
            helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "Replacing obsidian with another material invalidates the rune recipe");
            f.grid.setItem(4, new ItemStack(Items.OBSIDIAN, 2));
            TimedCraftingController.period(f.player, f.menu);
            helper.assertValueEqual(RunestoneItem.rune(f.menu.getSlot(0).getItem()), 0, "Changed recipe inputs reset the previous symbol selection");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void selectedRuneCompletesOneTimedBatchAndSurvivesActualPlacement(GameTestHelper helper) {
        for (RuneCase r : runes()) try (Fixture f = new Fixture(helper, r.metal())) {
            f.load(r, 2);
            for (int i = 0; i < 15; i++) f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            helper.assertValueEqual(RunestoneItem.rune(f.menu.getSlot(0).getItem()), 15, "The test selects Sanct with fifteen real right clicks");
            TimedCraftingTestSupport.craftOne(helper, f.player, f.menu, ClickType.PICKUP);
            f.assertUnspent(1);
            helper.assertValueEqual(f.player.getInventory().countItem(r.item()), 1, "One full period consumes one cross and delivers exactly one rune");
            ItemStack made = f.player.getInventory().items.stream().filter(stack -> stack.is(r.item())).findFirst().orElseThrow();
            helper.assertValueEqual(RunestoneItem.rune(made), 15, "Completed manufacturing preserves the chosen symbol");
            f.player.getInventory().clearContent();
            f.player.setItemInHand(InteractionHand.MAIN_HAND, made.copy());
            BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 3));
            helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
            BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(floor.above()), Direction.UP, floor, false);
            helper.assertTrue(r.item().useOn(new UseOnContext(f.player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "The actual BlockItem.useOn places the selected rune");
            BlockState placed = helper.getLevel().getBlockState(floor.above());
            helper.assertTrue(placed.is(r.block()), "Placement retains the metal's block identity");
            helper.assertValueEqual(placed.getValue(RunestoneBlock.RUNE), 15, "The BLOCK_STATE component persists into placed rune metadata");
            helper.assertTrue(f.player.getMainHandItem().isEmpty(), "Survival placement consumes exactly the sole crafted rune");
            List<ItemStack> drops = Block.getDrops(placed, helper.getLevel(), floor.above(), null, f.player, new ItemStack(Items.IRON_PICKAXE));
            helper.assertValueEqual(drops.size(), 1, "Normal mined rune returns one item");
            helper.assertValueEqual(RunestoneItem.rune(drops.getFirst()), 15, "Normal loot preserves the placed symbol for future portal reconstruction");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lowerBenchAndMidCraftBenchDowngradeCannotConsumeRuneMaterials(GameTestHelper helper) {
        for (RuneCase r : runes()) try (Fixture f = new Fixture(helper, r.priorBench())) {
            f.load(r, 2);
            helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "The previous material stage has no usable rune output");
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu), "A physical lower bench cannot start manufacturing");
            f.assertUnspent(2);
            f.setBench(r.metal());
            helper.assertTrue(TimedCraftingController.period(f.player, f.menu) >= 25, "A qualified physical bench computes a real full period");
            helper.assertTrue(TimedCraftingController.start(f.player, f.menu), "The required bench starts the same unchanged ingredients");
            for (int tick = 0; tick < 7; tick++) TimedCraftingController.tick(f.player);
            f.setBench(r.priorBench());
            TimedCraftingController.tick(f.player);
            helper.assertFalse(TimedCraftingController.isActive(f.player), "A live bench downgrade cancels server manufacturing");
            f.assertUnspent(2);
            helper.assertTrue(f.player.getInventory().isEmpty(), "Cancelled work cannot publish or duplicate a rune");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runeRightClickDuringActiveWorkCancelsThenNextClickSelects(GameTestHelper helper) {
        RuneCase r = runes().getFirst();
        try (Fixture f = new Fixture(helper, r.metal())) {
            f.load(r, 2);
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            for (int tick = 0; tick < 10; tick++) TimedCraftingController.tick(f.player);
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            helper.assertFalse(TimedCraftingController.isActive(f.player), "First right click cancels an active continuous batch");
            helper.assertValueEqual(RunestoneItem.rune(f.menu.getSlot(0).getItem()), 0, "Cancellation itself does not silently change the rune");
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            helper.assertValueEqual(RunestoneItem.rune(f.menu.getSlot(0).getItem()), 1, "Next right click selects Quas");
            f.assertUnspent(2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftManufacturingPreservesTheSelectedSymbolAcrossTwoRealBatches(GameTestHelper helper) {
        RuneCase r = runes().getFirst();
        try (Fixture f = new Fixture(helper, r.metal())) {
            f.load(r, 2);
            for (int selection = 0; selection < 4; selection++) f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            int period = TimedCraftingController.period(f.player, f.menu);
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            for (int tick = 0; tick < period; tick++) TimedCraftingController.tick(f.player);
            f.assertUnspent(1);
            helper.assertValueEqual(RunestoneItem.rune(f.menu.getSlot(0).getItem()), 4, "A completed first batch does not reset Nox for unchanged remaining ingredients");
            for (int tick = 0; tick < period; tick++) TimedCraftingController.tick(f.player);
            helper.assertTrue(f.grid.isEmpty(), "The second full period consumes the last cross");
            helper.assertValueEqual(f.player.getInventory().countItem(r.item()), 2, "Two separate timed Shift batches produce two rune items");
            helper.assertTrue(f.player.getInventory().items.stream().filter(stack -> stack.is(r.item()))
                    .allMatch(stack -> RunestoneItem.rune(stack) == 4), "Every continuously manufactured rune keeps the player-selected Nox symbol");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickBlockPreservesEveryRuneAndTheRealAnvilCannotRenameThem(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (RuneCase r : runes()) try (Fixture f = new Fixture(helper, r.metal())) {
            for (int symbol = 0; symbol < 16; symbol++) {
                BlockState state = r.block().defaultBlockState().setValue(RunestoneBlock.RUNE, symbol);
                helper.getLevel().setBlockAndUpdate(pos, state);
                ItemStack picked = r.block().getCloneItemStack(helper.getLevel(), pos, state);
                helper.assertTrue(picked.is(r.item()), "Native pick-block keeps the rune metal");
                helper.assertValueEqual(RunestoneItem.rune(picked), symbol, "Native clone persists every symbol rather than always Nul");
                AnvilMenu anvil = new AnvilMenu(60, f.player.getInventory());
                anvil.getSlot(0).set(picked);
                anvil.setItemName("不可改名的符文");
                helper.assertTrue(anvil.getSlot(2).getItem().isEmpty(), "The real AnvilUpdateEvent prevents renaming source ItemRunestone");
                helper.assertValueEqual(RunestoneItem.rune(anvil.getSlot(0).getItem()), symbol, "Denied rename cannot change rune identity");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualRuneExplosionEmitsSixIndependentHalfChanceShardCandidates(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        // java.util.Random seed 196 yields six source draws with exactly two values below .5.
        // The oracle is a literal source RNG fixture, not the modern block's implementation.
        for (RuneCase r : runes()) for (Explosion.BlockInteraction interaction : new Explosion.BlockInteraction[]{
                Explosion.BlockInteraction.DESTROY, Explosion.BlockInteraction.DESTROY_WITH_DECAY}) {
            BlockState state = r.block().defaultBlockState().setValue(RunestoneBlock.RUNE, 15);
            helper.getLevel().setBlockAndUpdate(pos, state);
            Explosion explosion = new Explosion(helper.getLevel(), null, pos.getX(), pos.getY(), pos.getZ(), 4F, false, interaction);
            List<ItemStack> drops = new ArrayList<>();
            helper.getLevel().random.setSeed(196L);
            state.onExplosionHit(helper.getLevel(), pos, explosion, (stack, at) -> drops.add(stack));
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "The actual explosion callback removes the struck rune");
            helper.assertTrue(drops.stream().allMatch(stack -> stack.is(MiteShards.OBSIDIAN_SHARD.get())), "Exploded runes yield obsidian fragments rather than a whole rune");
            helper.assertValueEqual(drops.stream().mapToInt(ItemStack::getCount).sum(), 2, "Six source half-chance candidates retain their count regardless of native explosion decay mode");
        }
        for (Explosion.BlockInteraction interaction : new Explosion.BlockInteraction[]{Explosion.BlockInteraction.KEEP, Explosion.BlockInteraction.TRIGGER_BLOCK}) {
            BlockState state = runes().getFirst().block().defaultBlockState();
            helper.getLevel().setBlockAndUpdate(pos, state);
            List<ItemStack> drops = new ArrayList<>();
            state.onExplosionHit(helper.getLevel(), pos, new Explosion(helper.getLevel(), null, pos.getX(), pos.getY(), pos.getZ(), 4F, false, interaction), (stack, at) -> drops.add(stack));
            helper.assertTrue(helper.getLevel().getBlockState(pos).equals(state) && drops.isEmpty(), "Non-destructive explosion interactions preserve rune and emit no fragments");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flintAndSteelUsesOneIronNuggetSourceDurabilityAndAnIronBench(GameTestHelper helper) {
        CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.IRON_NUGGET), ItemStack.EMPTY,
                ItemStack.EMPTY, new ItemStack(Items.FLINT)));
        RecipeHolder<CraftingRecipe> recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        ItemStack result = recipe.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(Items.FLINT_AND_STEEL), "The source diagonal uses an iron nugget and whole flint");
        helper.assertValueEqual(result.getMaxDamage(), 16, "Default component patch replaces native 64 uses with R196 sixteen");
        helper.assertValueEqual(result.get(DataComponents.MAX_DAMAGE), 16, "The stored component also matches source durability");
        CraftingInput ingot = CraftingInput.of(2, 2, List.of(new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY,
                ItemStack.EMPTY, new ItemStack(Items.FLINT)));
        helper.assertFalse(recipe.value().matches(ingot, helper.getLevel()), "The former native ingot recipe no longer matches");
        helper.assertValueEqual(TimedCraftingRecipes.get(recipe.id()).difficulty(), 188.88889F, "The source full-batch difficulty is retained");
        for (String bench : new String[]{"copper", "iron"}) try (Fixture f = new Fixture(helper, bench)) {
            f.grid.setItem(0, new ItemStack(Items.IRON_NUGGET, 2));
            f.grid.setItem(4, new ItemStack(Items.FLINT, 2));
            if (bench.equals("copper")) {
                helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "Copper workbench cannot manufacture the iron ignition tool");
                helper.assertFalse(TimedCraftingController.start(f.player, f.menu), "Server clock rejects the weak physical bench");
            } else {
                TimedCraftingTestSupport.craftOne(helper, f.player, f.menu, ClickType.PICKUP);
                helper.assertValueEqual(f.player.getInventory().countItem(Items.FLINT_AND_STEEL), 1, "One timed recipe publishes one ignition tool");
                helper.assertValueEqual(f.grid.getItem(0).getCount(), 1, "One iron nugget is consumed");
                helper.assertValueEqual(f.grid.getItem(4).getCount(), 1, "One flint is consumed");
            }
        }
        helper.succeed();
    }

    private static List<ItemStack> runeInputs(RuneCase r, int count) {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        items.set(4, new ItemStack(Items.OBSIDIAN, count));
        for (int slot : new int[]{1, 3, 5, 7}) items.set(slot, new ItemStack(r.nugget(), count));
        return items;
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final CraftingMenu menu;
        final CraftingContainer grid;
        final BlockPos table;
        final BlockState original;
        Fixture(GameTestHelper helper, String material) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-rune-test"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
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
            setBench(material);
            menu = new CraftingMenu(59, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            grid = (CraftingContainer)menu.getSlot(1).container;
        }
        void setBench(String material) {
            helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get(material).get().defaultBlockState());
        }
        void load(RuneCase r, int count) {
            List<ItemStack> inputs = runeInputs(r, count);
            for (int slot = 0; slot < 9; slot++) grid.setItem(slot, inputs.get(slot));
            TimedCraftingController.period(player, menu);
            helper.assertTrue(menu.stillValid(player), "A physically placed material workbench keeps the native menu valid");
        }
        void assertUnspent(int count) {
            for (int slot : new int[]{1, 3, 4, 5, 7}) helper.assertValueEqual(grid.getItem(slot).getCount(), count, "Only completed manufacturing may consume cross slot " + slot);
            for (int slot : new int[]{0, 2, 6, 8}) helper.assertTrue(grid.getItem(slot).isEmpty(), "Empty cross corners stay empty");
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
