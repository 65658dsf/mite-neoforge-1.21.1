package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.FoodManufacturingRecipe;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real inventory/workbench result transactions enforce the complete salad preparation period. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SaladManufacturingGameTests {
    private SaladManufacturingGameTests() {}

    @GameTest(template = "empty")
    public static void waterSaladAcceptsAllDandelionDaisyMixturesAndConsumesItsOnlyVessel(GameTestHelper h) {
        RecipeHolder<?> holder = h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("mite:salad_from_water_bowl")).orElseThrow();
        h.assertTrue(holder.value() instanceof FoodManufacturingRecipe, "The actual loaded water salad carries manufacturing metadata");
        h.assertValueEqual(((FoodManufacturingRecipe) holder.value()).manufacturing().difficulty(), 100F, "The recipe declares a five-second base preparation");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        for (int bits = 0; bits < 8; bits++) {
            CraftingInput input = CraftingInput.of(2, 2, Arrays.asList(new ItemStack((bits & 1) == 0 ? Items.DANDELION : Items.OXEYE_DAISY),
                    new ItemStack((bits & 2) == 0 ? Items.DANDELION : Items.OXEYE_DAISY),
                    new ItemStack((bits & 4) == 0 ? Items.DANDELION : Items.OXEYE_DAISY), new ItemStack(MiteFoods.WATER_BOWL.get())));
            h.assertTrue(recipe.matches(input, h.getLevel()), "Every mixture of three accepted flowers matches in a real two-by-two grid");
            ItemStack output = recipe.assemble(input, h.getLevel().registryAccess());
            h.assertTrue(output.is(MiteFoods.SALAD.get()) && output.getCount() == 1, "One ingredient set makes exactly one salad");
            h.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "The bowl stays in the salad instead of returning a duplicated vessel");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryPickupWaitsOneHundredTicksAndConsumesExactlyOneMixedFlowerBatch(GameTestHelper h) {
        try (Fixture f = new Fixture(h, false)) {
            f.load(true, true);
            h.assertValueEqual(TimedCraftingController.period(f.player, f.menu), 100, "Unskilled inventory crafting requires one hundred production ticks");
            TimedCraftingTestSupport.craftOne(h, f.player, f.menu, ClickType.PICKUP);
            f.assertOneBatch(MiteFoods.WATER_BOWL.get());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void workbenchShiftClickWaitsItsFullPeriodBeforePublishingOneSalad(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            f.load(true, true);
            h.assertValueEqual(TimedCraftingController.period(f.player, f.menu), 83, "A flint workbench keeps its normal twenty-percent speed benefit");
            TimedCraftingTestSupport.craftOne(h, f.player, f.menu, ClickType.QUICK_MOVE);
            f.assertOneBatch(MiteFoods.WATER_BOWL.get());
            h.assertTrue(f.menu.getCarried().isEmpty(), "A completed shift-click batch enters inventory without a second cursor output");
            PlayerExperience.setLevels(f.player, 10);
            h.assertTrue(TimedCraftingController.period(f.player, f.menu) < 83, "Experience-level bonuses continue to shorten the preparation period");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyEmptyBowlSaladCannotBypassTheSamePreparationPeriod(GameTestHelper h) {
        try (Fixture f = new Fixture(h, false)) {
            f.load(false, false);
            h.assertValueEqual(TimedCraftingController.period(f.player, f.menu), 100, "The loaded historical three-dandelion and empty-bowl recipe is also timed");
            TimedCraftingTestSupport.craftOne(h, f.player, f.menu, ClickType.QUICK_MOVE);
            f.assertOneBatch(Items.BOWL);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void changingIngredientsCancelsAnIncompleteShiftBatchWithoutAnyConsumption(GameTestHelper h) {
        try (Fixture f = new Fixture(h, false)) {
            f.load(true, true);
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            h.assertTrue(TimedCraftingController.isActive(f.player) && f.count(MiteFoods.SALAD.get()) == 0,
                    "Shift-click starts manufacture and cannot take the preview immediately");
            f.tick(50);
            ItemStack removed = f.grid.removeItemNoUpdate(0);
            f.menu.slotsChanged(f.grid);
            f.tick(150);
            h.assertFalse(TimedCraftingController.isActive(f.player), "A changed input snapshot cancels the in-progress batch");
            h.assertValueEqual(f.count(MiteFoods.SALAD.get()), 0, "A canceled preview cannot later appear in cursor or inventory");
            h.assertValueEqual(f.count(MiteFoods.WATER_BOWL.get()), 2, "Canceled preparation consumes neither available water bowl");
            f.grid.setItem(0, removed);
            h.assertValueEqual(TimedCraftingController.progress(f.player), 0, "Restoring the flowers does not restore canceled progress");
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(99);
            h.assertValueEqual(f.count(MiteFoods.SALAD.get()), 0, "The replacement session must wait a fresh complete period");
            f.tick(1);
            f.assertOneBatch(MiteFoods.WATER_BOWL.get());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryDatapackSaladOutputInheritsTheMinimumDifficultyWithoutFoodMetadata(GameTestHelper h) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Items.DANDELION));
        CraftingRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(MiteFoods.SALAD.get()), ingredients);
        var holder = new RecipeHolder<>(ResourceLocation.parse("mite:test_plain_salad_alternative"), recipe);
        var input = CraftingInput.of(1, 1, java.util.List.of(new ItemStack(Items.DANDELION)));
        var rule = CraftingQuality.rule(holder, recipe.assemble(input, h.getLevel().registryAccess()), input);
        h.assertTrue(rule != null && rule.difficulty() == 100F, "An ordinary custom salad recipe cannot escape timing by omitting manufacturing metadata");
        h.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final AbstractContainerMenu menu;
        final CraftingContainer grid;
        final BlockPos table;
        final BlockState previous;
        Fixture(GameTestHelper h, boolean workbench) {
            this.h = h;
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-salad"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6); player.setNoGravity(true); player.setOnGround(true);
            table = h.absolutePos(new BlockPos(1, 1, 1)); previous = h.getLevel().getBlockState(table);
            player.moveTo(table.above().getCenter());
            if (workbench) {
                h.getLevel().setBlockAndUpdate(table, TimedCraftingTestSupport.flintTable().defaultBlockState());
                menu = new CraftingMenu(67, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), table));
            } else menu = player.inventoryMenu;
            player.containerMenu = menu;
            grid = (CraftingContainer) menu.getSlot(1).container;
        }
        void load(boolean water, boolean mixed) {
            TimedCraftingController.cancel(player); grid.clearContent(); player.getInventory().clearContent(); menu.setCarried(ItemStack.EMPTY);
            Item[] ingredients = {Items.DANDELION, mixed ? Items.OXEYE_DAISY : Items.DANDELION,
                    Items.DANDELION, water ? MiteFoods.WATER_BOWL.get() : Items.BOWL};
            for (int slot = 0; slot < ingredients.length; slot++) grid.setItem(slot, new ItemStack(ingredients[slot], 2));
            menu.slotsChanged(grid);
            h.assertTrue(menu.getSlot(0).getItem().is(MiteFoods.SALAD.get()), "The actual menu finds a loaded salad recipe for these inputs");
        }
        void tick(int ticks) { for (int i = 0; i < ticks; i++) TimedCraftingController.tick(player); }
        int count(Item item) {
            int result = player.getInventory().countItem(item) + (menu.getCarried().is(item) ? menu.getCarried().getCount() : 0);
            for (int i = 0; i < grid.getContainerSize(); i++) if (grid.getItem(i).is(item)) result += grid.getItem(i).getCount();
            return result;
        }
        void assertOneBatch(Item vessel) {
            h.assertValueEqual(count(MiteFoods.SALAD.get()), 1, "One completed transaction supplies exactly one salad");
            h.assertValueEqual(count(vessel), 1, "Exactly one supplied vessel is consumed and the second batch remains");
            if (vessel != Items.BOWL) h.assertValueEqual(count(Items.BOWL), 0, "Water-bowl preparation cannot duplicate an empty bowl");
            for (int slot = 0; slot < 4; slot++) h.assertValueEqual(grid.getItem(slot).getCount(), 1, "The real result transaction consumes one of each input slot");
        }
        @Override public void close() {
            try {
                TimedCraftingController.forget(player); grid.clearContent(); menu.setCarried(ItemStack.EMPTY);
                player.getInventory().clearContent(); player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally { h.getLevel().setBlockAndUpdate(table, previous); channel.finishAndReleaseAll(); }
        }
    }
}
