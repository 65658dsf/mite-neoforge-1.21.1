package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.PlayerFoodEvents;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual output withdrawals and hopper transfers distinguish source-food XP from recipe history. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodFurnaceExperienceGameTests {
    private FoodFurnaceExperienceGameTests() {}

    @GameTest(template = "empty")
    public static void partialFoodWithdrawalAwardsOnlyTheTakenCountAndPaysRemainingFoodLater(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, false)) {
            f.cook(Items.BEEF, 10);
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 10, "The actual furnace produced ten cooked beef");
            f.menu.clicked(2, 0, ClickType.THROW, f.player);
            helper.assertValueEqual(f.droppedFood(Items.COOKED_BEEF), 1, "The real result-slot Q action removes and drops just one item");
            helper.assertValueEqual(f.xp(), 4, "One taken beef rewards four, not all forty production-history XP");
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 9, "The nine unclaimed foods remain in the furnace");
            helper.assertValueEqual(f.take(9).getCount(), 9, "The same result slot later removes all remaining food");
            helper.assertValueEqual(f.xp(), 40, "The later nine-food withdrawal rewards thirty-six more after native history was already cleared");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftAndHotbarWithdrawalsPayExactlyOnceOnFurnaceAndSmoker(GameTestHelper helper) {
        for (boolean smoker : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(helper, smoker)) {
                f.cook(Items.PORKCHOP, 4);
                f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.player);
                helper.assertValueEqual(f.player.getInventory().countItem(Items.COOKED_PORKCHOP), 4,
                        "Real shift movement transfers four cooked porkchops");
                helper.assertValueEqual(f.xp(), 12, "Quick-craft plus onTake callbacks pay the actual quantity once, with no native-bank duplicate");
                f.cook(Items.COD, 2);
                helper.assertTrue(f.player.getInventory().getItem(0).isEmpty(), "The hotbar-swap destination starts empty");
                f.menu.clicked(2, 0, ClickType.SWAP, f.player);
                helper.assertTrue(f.player.getInventory().getItem(0).is(Items.COOKED_COD), "The real hotbar swap removes the two fish");
                helper.assertValueEqual(f.player.getInventory().getItem(0).getCount(), 2, "The swap transfers both fish");
                helper.assertValueEqual(f.xp(), 18, "The swap-only callback rewards six more XP for the two fish");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void successfulHopperFoodWithdrawalsPaySourceExperienceWithoutSavingADuplicateBalance(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, false)) {
            f.cook(Items.BEEF, 5);
            helper.getLevel().setBlockAndUpdate(f.hopperPosition, Blocks.HOPPER.defaultBlockState());
            HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(f.hopperPosition);
            for (int tick = 0; tick < 40; tick++)
                HopperBlockEntity.pushItemsTick(helper.getLevel(), f.hopperPosition, helper.getLevel().getBlockState(f.hopperPosition), hopper);
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The real hopper withdrew all five food outputs");
            int collected = 0;
            for (int slot = 0; slot < hopper.getContainerSize(); slot++)
                if (hopper.getItem(slot).is(Items.COOKED_BEEF)) collected += hopper.getItem(slot).getCount();
            helper.assertValueEqual(collected, 5, "Exactly five cooked foods reached the hopper");
            helper.assertValueEqual(f.xp(), 20, "Source asi awards four XP for each of the five successfully extracted cooked beef");
            CompoundTag saved = f.furnace.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.assertValueEqual(saved.getCompound("RecipesUsed").getInt("minecraft:cooked_beef"), 5,
                    "Native recipe history remains available for unlocking, but it is not a food XP balance");
            f.reload(saved);
            f.cook(Items.BEEF, 1);
            f.take(1);
            helper.assertValueEqual(f.xp(), 24, "After saving and reloading the new manual beef adds four; already paid hopper food cannot pay a second history reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyFoodBankIsSuppressedWhileMineralAndModernFoodBanksRetainTheirRewards(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, false)) {
            CompoundTag saved = f.furnace.saveWithoutMetadata(helper.getLevel().registryAccess());
            CompoundTag legacyHistory = new CompoundTag();
            legacyHistory.putInt("minecraft:cooked_cod", 11);
            legacyHistory.putInt("minecraft:netherite_scrap", 3);
            legacyHistory.putInt("minecraft:dried_kelp_from_smelting", 10);
            // This existing key is a crafting recipe, so a stale/replaced history entry must
            // not enter native AbstractCookingRecipe's cast or fabricate cooking experience.
            legacyHistory.putInt("minecraft:dried_kelp", 30);
            saved.put("RecipesUsed", legacyHistory);
            f.reload(saved);
            f.furnace.awardUsedRecipesAndPopExperience(f.player);
            helper.assertValueEqual(f.xp(), 7,
                    "Legacy source fish history pays zero; native scrap 3×2 and modern kelp 10×0.1 still pay seven total");
            helper.assertTrue(f.furnace.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("RecipesUsed").isEmpty(),
                    "The native award path still clears recipe history after unlocking");
            f.furnace.awardUsedRecipesAndPopExperience(f.player);
            helper.assertValueEqual(f.xp(), 7, "A repeated native history claim cannot reproduce cleared XP");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceBreadWithdrawalHasNoRewardOrStoredExperience(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, false)) {
            f.cook(MiteFoods.get("dough"), 2);
            helper.assertTrue(f.take(2).is(Items.BREAD), "The actual dough cooking output is withdrawn from its result slot");
            helper.assertValueEqual(f.xp(), 0, "Bread's explicit source zero reward is respected by both withdrawal and historical-bank paths");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allNineSourceCookedFoodHopperRewardsUseActuallyTransferredQuantities(GameTestHelper helper) {
        record Cooking(Item input, Item output, int experience) {}
        for (Cooking source : List.of(new Cooking(Items.PORKCHOP, Items.COOKED_PORKCHOP, 3),
                new Cooking(Items.COD, Items.COOKED_COD, 3), new Cooking(Items.BEEF, Items.COOKED_BEEF, 4),
                new Cooking(Items.CHICKEN, Items.COOKED_CHICKEN, 3), new Cooking(Items.MUTTON, Items.COOKED_MUTTON, 2),
                new Cooking(Items.POTATO, Items.BAKED_POTATO, 0), new Cooking(MiteFoods.get("dough"), Items.BREAD, 0),
                new Cooking(MiteFoods.get("raw_large_fish"), MiteFoods.get("cooked_large_fish"), 4),
                new Cooking(MiteFoods.get("raw_worm"), MiteFoods.get("cooked_worm"), 0))) {
            try (Fixture f = new Fixture(helper, false)) {
                f.cook(source.input(), 3);
                helper.getLevel().setBlockAndUpdate(f.hopperPosition, Blocks.HOPPER.defaultBlockState());
                HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(f.hopperPosition);
                for (int tick = 0; tick < 40; tick++)
                    HopperBlockEntity.pushItemsTick(helper.getLevel(), f.hopperPosition,
                            helper.getLevel().getBlockState(f.hopperPosition), hopper);
                helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The actual hopper transfers all three source cooked foods");
                int transferred = 0;
                for (int slot = 0; slot < hopper.getContainerSize(); slot++)
                    if (hopper.getItem(slot).is(source.output())) transferred += hopper.getItem(slot).getCount();
                helper.assertValueEqual(transferred, 3, "Three actual source food results reach the hopper without duplication");
                helper.assertValueEqual(f.xp(), source.experience() * 3,
                        "Successful source asi extraction uses the independently read per-item food reward");
                f.furnace.awardUsedRecipesAndPopExperience(f.player);
                helper.assertValueEqual(f.xp(), source.experience() * 3, "Native recipe history cannot duplicate already paid source food transfer rewards");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeRecipeBookClearingACompletedFoodPaysOnlyTheActuallyInsertedQuantity(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, false)) {
            f.cook(Items.BEEF, 1); f.furnace.setItem(1, ItemStack.EMPTY);
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                    new SingleRecipeInput(new ItemStack(Items.PORKCHOP)), helper.getLevel()).orElseThrow();
            f.player.awardRecipes(List.of(recipe));
            new ServerPlaceRecipe<SingleRecipeInput, AbstractCookingRecipe>(f.menu).recipeClicked(f.player,
                    new RecipeHolder<AbstractCookingRecipe>(recipe.id(), recipe.value()), false);
            helper.assertValueEqual(f.player.getInventory().countItem(Items.COOKED_BEEF), 1,
                    "The real recipe-book clear-grid action transfers the completed beef to inventory");
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The actual clear-grid action empties the result slot");
            helper.assertValueEqual(f.xp(), 4, "Clearing an actual completed source beef through the recipe book pays its one-item reward");
            f.furnace.awardUsedRecipesAndPopExperience(f.player);
            helper.assertValueEqual(f.xp(), 4, "Recipe-book transfer rewards cannot later become duplicate recipe-history rewards");
        }
        try (Fixture f = new Fixture(helper, false)) {
            f.cook(Items.BEEF, 3); f.furnace.setItem(1, ItemStack.EMPTY);
            for (int slot = 0; slot < 36; slot++) {
                ItemStack full = new ItemStack(Items.COBBLESTONE);
                full.setCount(full.getMaxStackSize()); f.player.getInventory().setItem(slot, full);
            }
            int maximum = new ItemStack(Items.COOKED_BEEF).getMaxStackSize();
            f.player.getInventory().setItem(0, new ItemStack(Items.COOKED_BEEF, maximum - 1));
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                    new SingleRecipeInput(new ItemStack(Items.PORKCHOP)), helper.getLevel()).orElseThrow();
            f.player.awardRecipes(List.of(recipe));
            new ServerPlaceRecipe<SingleRecipeInput, AbstractCookingRecipe>(f.menu).recipeClicked(f.player,
                    new RecipeHolder<AbstractCookingRecipe>(recipe.id(), recipe.value()), false);
            helper.assertValueEqual(f.player.getInventory().countItem(Items.COOKED_BEEF), maximum - 1,
                    "An actual recipe-book click atomically refuses clearing when all three source results cannot fit");
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 3, "A rejected real recipe-book clear preserves all completed source foods");
            helper.assertValueEqual(f.xp(), 0, "The rejected native recipe-book action has no successful transfer reward");
            class ClearGridProbe extends ServerPlaceRecipe<SingleRecipeInput, AbstractCookingRecipe> {
                ClearGridProbe() { super(f.menu); }
                void clearDirectly() { inventory = f.player.getInventory(); clearGrid(); }
            }
            // Exercise the underlying transfer hook as well, independent of the public preflight guard.
            new ClearGridProbe().clearDirectly();
            helper.assertValueEqual(f.player.getInventory().countItem(Items.COOKED_BEEF), maximum,
                    "The underlying partial clear-grid transfer inserts only the one available item");
            helper.assertValueEqual(f.xp(), 4, "The partial recipe-book transfer rewards only the one inserted source result");
            helper.assertValueEqual(f.droppedFood(Items.COOKED_BEEF) + f.furnace.getItem(2).getCount(), 2,
                    "Uninserted native clear-grid leftovers are conserved rather than destroyed or duplicated");
            f.furnace.awardUsedRecipesAndPopExperience(f.player);
            helper.assertValueEqual(f.xp(), 4, "Claiming native history cannot pay either the already inserted food or the two uninserted leftovers");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final boolean smoker;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos furnacePosition;
        private final BlockPos hopperPosition;
        private final BlockPos frontPosition;
        private final BlockState previousFurnace;
        private final BlockState previousHopper;
        private final BlockState previousFront;
        private final AABB area;
        private final SpawnedEntityCapture spawned;
        private final Set<UUID> oldOrbs = new HashSet<>();
        private final Set<UUID> oldItems = new HashSet<>();
        private AbstractFurnaceBlockEntity furnace;
        private AbstractFurnaceMenu menu;

        private Fixture(GameTestHelper helper, boolean smoker) {
            this.helper = helper;
            this.smoker = smoker;
            furnacePosition = helper.absolutePos(new BlockPos(1, 2, 1));
            hopperPosition = furnacePosition.below();
            frontPosition = furnacePosition.north();
            previousFurnace = helper.getLevel().getBlockState(furnacePosition);
            previousHopper = helper.getLevel().getBlockState(hopperPosition);
            previousFront = helper.getLevel().getBlockState(frontPosition);
            area = new AABB(furnacePosition).inflate(5D);
            spawned = new SpawnedEntityCapture(helper.getLevel(), entity -> (entity instanceof ExperienceOrb || entity instanceof ItemEntity) && entity.getBoundingBox().intersects(area));
            for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area)) oldOrbs.add(orb.getUUID());
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) oldItems.add(item.getUUID());
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-food-xp"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            // The real ServerPlayer recipe-award path needs a listener, but these server-only tests
            // deliberately observe world XP rather than transmitting UI packets to a client.
            new ServerGamePacketListenerImpl(player.server, connection, player, cookie) {
                @Override public void send(Packet<?> packet) {}
            };
            player.moveTo(helper.absolutePos(new BlockPos(1, 3, 2)).getCenter());
            player.getInventory().clearContent();
            // This fixture sits against the template wall; source furnaces need a real open front.
            helper.getLevel().setBlockAndUpdate(frontPosition, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(furnacePosition, smoker ? Blocks.SMOKER.defaultBlockState() : Blocks.FURNACE.defaultBlockState());
            furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(furnacePosition);
            openMenu();
        }
        private void openMenu() {
            menu = smoker ? new SmokerMenu(72, player.getInventory(), furnace, new SimpleContainerData(4))
                    : new FurnaceMenu(72, player.getInventory(), furnace, new SimpleContainerData(4));
            player.containerMenu = menu;
        }
        private void cook(Item raw, int amount) {
            helper.assertTrue(furnace.getItem(2).isEmpty(), "Cooking starts with no unclaimed previous output");
            furnace.setItem(0, new ItemStack(raw, amount));
            furnace.setItem(1, new ItemStack(smoker ? Items.CHARCOAL : Items.COAL, 8));
            for (int tick = 0; tick < amount * 200; tick++)
                AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), furnacePosition, helper.getLevel().getBlockState(furnacePosition), furnace);
            helper.assertValueEqual(furnace.getItem(2).getCount(), amount, "Actual two-hundred-tick recipes produced the requested source quantity");
        }
        private ItemStack take(int amount) { return menu.getSlot(2).safeTake(amount, amount, player); }
        private int droppedFood(Item food) {
            int total = 0;
            for (ItemEntity item : spawned.live(ItemEntity.class))
                if (!oldItems.contains(item.getUUID()) && item.getItem().is(food)) total += item.getItem().getCount();
            return total;
        }
        private int xp() {
            int total = 0;
            for (ExperienceOrb orb : spawned.live(ExperienceOrb.class)) {
                if (oldOrbs.contains(orb.getUUID())) continue;
                // Native XP award may merge equal-valued orbs. Saved Count is its actual persisted
                // multiplicity, so this check remains deterministic without accessing private fields.
                CompoundTag saved = orb.saveWithoutId(new CompoundTag());
                total += orb.getValue() * Math.max(saved.getInt("Count"), 1);
            }
            return total;
        }
        private void reload(CompoundTag saved) {
            helper.assertFalse(smoker, "This persistence fixture deliberately exercises the ordinary furnace type");
            FurnaceBlockEntity restored = new FurnaceBlockEntity(furnacePosition, helper.getLevel().getBlockState(furnacePosition));
            restored.loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.getLevel().setBlockEntity(restored);
            furnace = restored;
            openMenu();
        }
        @Override public void close() {
            try {
                player.containerMenu = player.inventoryMenu;
                player.getInventory().clearContent();
                PlayerFoodEvents.loggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
                player.getTextFilter().leave();
                furnace.clearContent();
                if (helper.getLevel().getBlockEntity(hopperPosition) instanceof HopperBlockEntity hopper) hopper.clearContent();
                helper.getLevel().setBlockAndUpdate(furnacePosition, previousFurnace);
                helper.getLevel().setBlockAndUpdate(hopperPosition, previousHopper);
                helper.getLevel().setBlockAndUpdate(frontPosition, previousFront);
                helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area).stream()
                        .filter(orb -> !oldOrbs.contains(orb.getUUID())).forEach(ExperienceOrb::discard);
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                        .filter(item -> !oldItems.contains(item.getUUID())).forEach(ItemEntity::discard);
            } finally { spawned.close(); channel.finishAndReleaseAll(); }
        }
    }
}
