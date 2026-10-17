package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.survival.GrindstoneExperienceAccess;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real grindstone transactions replace their native random XP reward with a doubled point cost. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class GrindstoneExperienceGameTests {
    private GrindstoneExperienceGameTests() {}

    @GameTest(template = "empty")
    public static void ordinaryPickupChargesExactlyTwiceTheNativeRandomReward(GameTestHelper helper) {
        for (long seed : new long[] {196L, 7481L, 12913L}) {
            try (Fixture f = new Fixture(helper, 1000)) {
                ItemStack input = enchanted(helper, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 4);
                input.setDamageValue(123);
                helper.assertValueEqual(minimumCost(helper, Enchantments.EFFICIENCY, 4), 31,
                        "Efficiency four supplies the existing native cost 31");
                int originalReward = reward(31, seed);
                f.seed(seed);
                f.menu.getSlot(0).set(input);
                helper.assertValueEqual(f.cost(), 2 * originalReward, "The preview caches twice its one native random draw");
                f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
                helper.assertTrue(f.menu.getCarried().is(Items.IRON_PICKAXE), "Ordinary result pickup succeeds");
                helper.assertFalse(f.menu.getCarried().isEnchanted(), "Ordinary pickup removes the enchantment");
                helper.assertValueEqual(f.menu.getCarried().getDamageValue(), 123, "Disenchanting preserves existing wear");
                helper.assertValueEqual(f.owner.totalExperience, 1000 - 2 * originalReward,
                        "The actual seeded random reward is doubled and subtracted as points");
                f.assertConsumed();
                f.assertNoExperienceOrbs();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftClickAndDirectQuickTransferChargeOnlyOnce(GameTestHelper helper) {
        for (boolean direct : new boolean[] {false, true}) {
            try (Fixture f = new Fixture(helper, 1000)) {
                long seed = 3041L;
                int originalReward = reward(21, seed);
                f.seed(seed);
                f.menu.getSlot(0).set(enchanted(helper, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 3));
                helper.assertValueEqual(f.cost(), 2 * originalReward, "Both shift entry points see the preview's cached fee");
                if (direct) {
                    helper.assertFalse(f.menu.quickMoveStack(f.owner, 2).isEmpty(), "Direct native quick transfer succeeds");
                } else {
                    f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.owner);
                }
                helper.assertValueEqual(f.owner.totalExperience, 1000 - 2 * originalReward,
                        "Each shift-transfer entry point charges the one completed result exactly once");
                helper.assertValueEqual(f.owner.getInventory().countItem(Items.IRON_PICKAXE), 1,
                        "Shift transfer adds exactly one cleaned item to the inventory");
                helper.assertTrue(f.menu.getCarried().isEmpty(), "Shift transfer leaves the carried stack empty");
                f.assertConsumed();
                helper.assertTrue(f.menu.quickMoveStack(f.owner, 2).isEmpty(), "Repeating an empty quick transfer produces nothing");
                helper.assertValueEqual(f.owner.totalExperience, 1000 - 2 * originalReward,
                        "Retrying the empty result slot incurs no second payment");
                f.assertNoExperienceOrbs();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothInputsContributeNonCurseCostsBeforeTheSingleRandomDraw(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 203000)) {
            ItemStack top = enchanted(helper, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 3);
            ItemStack bottom = enchanted(helper, Items.IRON_PICKAXE, Enchantments.UNBREAKING, 2);
            enchant(helper, top, Enchantments.VANISHING_CURSE, 1);
            enchant(helper, bottom, Enchantments.BINDING_CURSE, 1);
            top.setDamageValue(1000);
            bottom.setDamageValue(2000);
            f.menu.getSlot(0).set(top);
            helper.assertValueEqual(minimumCost(helper, Enchantments.EFFICIENCY, 3), 21, "Top non-curse cost is 21");
            helper.assertValueEqual(minimumCost(helper, Enchantments.UNBREAKING, 2), 11, "Bottom non-curse cost is 11");
            long seed = 6136L;
            int originalReward = reward(32, seed);
            f.seed(seed);
            f.menu.getSlot(1).set(bottom);
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "The real two-input merge has a result");
            helper.assertValueEqual(f.cost(), 2 * originalReward, "Changing the second input recomputes the combined fee once");
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            ItemStack result = f.menu.getCarried();
            helper.assertTrue(result.is(Items.IRON_PICKAXE), "The two-input result can actually be taken");
            helper.assertValueEqual(f.owner.totalExperience, 203000 - 2 * originalReward,
                    "Only 21 plus 11 enter the native summed-cost random calculation");
            helper.assertValueEqual(level(helper, result, Enchantments.EFFICIENCY), 0, "The top ordinary enchantment is removed");
            helper.assertValueEqual(level(helper, result, Enchantments.UNBREAKING), 0, "The bottom ordinary enchantment is removed");
            helper.assertValueEqual(level(helper, result, Enchantments.VANISHING_CURSE), 1, "The top curse is retained");
            helper.assertValueEqual(level(helper, result, Enchantments.BINDING_CURSE), 1, "The bottom curse is retained");
            f.assertConsumed();
            f.assertNoExperienceOrbs();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bottomOnlyEnchantedBookUsesStoredEnchantmentsAndBecomesAnOrdinaryBook(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 100)) {
            f.menu.getSlot(1).set(enchanted(helper, Items.ENCHANTED_BOOK, Enchantments.EFFICIENCY, 1));
            helper.assertValueEqual(minimumCost(helper, Enchantments.EFFICIENCY, 1), 1, "The level-one reward is deterministically one point");
            helper.assertValueEqual(f.cost(), 2, "The result exposes the stored-enchantment fee before pickup");
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            helper.assertTrue(f.menu.getCarried().is(Items.BOOK), "A bottom-only stored enchantment produces an ordinary book");
            helper.assertValueEqual(f.owner.totalExperience, 98, "A former one-point reward now costs exactly two points");
            f.assertConsumed();
            f.assertNoExperienceOrbs();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void curseOnlyInputAndUnenchantedRepairHaveNoExperienceCost(GameTestHelper helper) {
        for (boolean curseOnly : new boolean[] {true, false}) {
            try (Fixture f = new Fixture(helper, 203000)) {
                if (curseOnly) {
                    f.menu.getSlot(0).set(enchanted(helper, Items.IRON_PICKAXE, Enchantments.VANISHING_CURSE, 1));
                } else {
                    ItemStack top = new ItemStack(Items.IRON_PICKAXE);
                    ItemStack bottom = new ItemStack(Items.IRON_PICKAXE);
                    top.setDamageValue(8000);
                    bottom.setDamageValue(8000);
                    f.menu.getSlot(0).set(top);
                    f.menu.getSlot(1).set(bottom);
                }
                helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "A zero-reward transaction still offers its normal result");
                helper.assertValueEqual(f.cost(), 0, "Curses and ordinary maintenance publish a zero-point fee");
                f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
                ItemStack result = f.menu.getCarried();
                helper.assertTrue(result.is(Items.IRON_PICKAXE), "A zero-reward result can actually be taken");
                helper.assertValueEqual(f.owner.totalExperience, 203000, "Zero native reward means zero point cost");
                if (curseOnly) {
                    helper.assertValueEqual(level(helper, result, Enchantments.VANISHING_CURSE), 1, "Grindstones still retain curses");
                } else {
                    helper.assertTrue(result.getDamageValue() < 8000, "The unenchanted operation really repairs the equipment");
                }
                f.assertConsumed();
                f.assertNoExperienceOrbs();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void previewRecalculationInputRemovalAndMenuCloseNeverSpendExperience(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 1000)) {
            f.seed(9209L);
            f.menu.getSlot(0).set(enchanted(helper, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 4));
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "A disenchant preview is available");
            int cachedCost = f.cost();
            ItemStack preview = f.menu.getSlot(2).getItem().copy();
            for (int update = 0; update < 3; update++) {
                helper.getLevel().random.nextInt();
                f.menu.slotsChanged(f.menu.getSlot(0).container);
                f.menu.broadcastChanges();
                helper.assertValueEqual(f.cost(), cachedCost, "Identical input refreshes cannot reroll the displayed random fee");
                helper.assertTrue(ItemStack.matches(preview, f.menu.getSlot(2).getItem()), "Refreshing keeps the same preview");
            }
            helper.assertValueEqual(f.owner.totalExperience, 1000, "Repeated preview updates do not spend points");
            f.menu.getSlot(0).set(ItemStack.EMPTY);
            helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "Removing an input removes the preview");
            helper.assertValueEqual(f.cost(), -1, "Removing the final input clears the published fee");
            helper.assertValueEqual(f.owner.totalExperience, 1000, "Removing input without taking output is free");
            f.menu.getSlot(1).set(enchanted(helper, Items.ENCHANTED_BOOK, Enchantments.EFFICIENCY, 3));
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "Closing is tested with a nonempty result preview");
            f.menu.removed(f.owner);
            helper.assertValueEqual(f.owner.totalExperience, 1000, "Closing a menu with a preview never charges the player");
            f.assertNoExperienceOrbs();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactPointBalanceAllowsOrdinaryAndShiftPickupWithoutDebt(GameTestHelper helper) {
        for (boolean shift : new boolean[] {false, true}) {
            try (Fixture f = new Fixture(helper, 1000)) {
                f.seed(671L);
                f.menu.getSlot(0).set(enchanted(helper, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 4));
                int cost = f.cost();
                PlayerExperience.setTotal(f.owner, cost);
                helper.assertTrue(f.access().mite$canTake(f.owner), "The exact published point fee is sufficient");
                f.menu.clicked(2, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP, f.owner);
                helper.assertTrue(shift ? f.owner.getInventory().countItem(Items.IRON_PICKAXE) == 1
                        : f.menu.getCarried().is(Items.IRON_PICKAXE), "Exact affordability completes either result pickup path");
                helper.assertValueEqual(f.owner.totalExperience, 0, "Paying the exact fee leaves zero points without creating debt");
                f.assertConsumed();
                f.assertNoExperienceOrbs();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onePointShortCannotTakeUsingPickupShiftSwapDropOrCollect(GameTestHelper helper) {
        for (String operation : new String[] {"pickup", "rightPickup", "shift", "directShift", "hotbar", "offhand", "drop", "dropStack", "collect", "creativeClone"}) {
            try (Fixture f = new Fixture(helper, 1)) {
                f.menu.getSlot(0).set(enchanted(helper, Items.ENCHANTED_BOOK, Enchantments.EFFICIENCY, 1));
                helper.assertValueEqual(f.cost(), 2, "This input's displayed fee is exactly one point above the balance");
                helper.assertFalse(f.access().mite$canTake(f.owner), "One point below the required fee is unaffordable");
                ItemStack input = f.menu.getSlot(0).getItem().copy();
                ItemStack preview = f.menu.getSlot(2).getItem().copy();
                helper.assertTrue(preview.is(Items.BOOK), "Insufficient experience keeps the cleaned result visible for its tooltip");
                if (operation.equals("collect")) f.menu.setCarried(new ItemStack(Items.BOOK));
                if (operation.equals("creativeClone")) f.owner.getAbilities().instabuild = true;
                ItemStack carried = f.menu.getCarried().copy();
                switch (operation) {
                    case "pickup" -> f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
                    case "rightPickup" -> f.menu.clicked(2, 1, ClickType.PICKUP, f.owner);
                    case "shift" -> f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.owner);
                    case "directShift" -> helper.assertTrue(f.menu.quickMoveStack(f.owner, 2).isEmpty(), "Direct shift refuses an unpaid result");
                    case "hotbar" -> f.menu.clicked(2, 0, ClickType.SWAP, f.owner);
                    case "offhand" -> f.menu.clicked(2, 40, ClickType.SWAP, f.owner);
                    case "drop" -> f.menu.clicked(2, 0, ClickType.THROW, f.owner);
                    case "dropStack" -> f.menu.clicked(2, 1, ClickType.THROW, f.owner);
                    case "collect" -> f.menu.clicked(2, 0, ClickType.PICKUP_ALL, f.owner);
                    case "creativeClone" -> f.menu.clicked(2, 2, ClickType.CLONE, f.owner);
                    default -> throw new IllegalArgumentException(operation);
                }
                helper.assertValueEqual(f.owner.totalExperience, 1, operation + " never spends an unaffordable fee");
                helper.assertTrue(ItemStack.matches(input, f.menu.getSlot(0).getItem()), operation + " preserves the enchanted input");
                helper.assertTrue(ItemStack.matches(preview, f.menu.getSlot(2).getItem()), operation + " keeps the same preview");
                helper.assertTrue(ItemStack.matches(carried, f.menu.getCarried()), operation + " never adds an unpaid result to the cursor");
                helper.assertValueEqual(f.owner.getInventory().countItem(Items.BOOK), 0, operation + " never transfers a result to inventory or offhand");
                helper.assertValueEqual(f.cost(), 2, operation + " does not reroll or erase the quoted cost");
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(f.pos).inflate(2)).isEmpty(),
                        operation + " never creates an unpaid output drop");
                f.assertNoExperienceOrbs();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void experienceLostAfterPreviewBlocksPaymentUntilTheSameQuotedFeeIsAffordable(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 1000)) {
            f.seed(9019L);
            f.menu.getSlot(0).set(enchanted(helper, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 4));
            int cost = f.cost();
            ItemStack input = f.menu.getSlot(0).getItem().copy();
            ItemStack preview = f.menu.getSlot(2).getItem().copy();
            helper.assertTrue(f.access().mite$canTake(f.owner), "The initial result is affordable");
            PlayerExperience.setTotal(f.owner, cost - 1);
            helper.assertFalse(f.access().mite$canTake(f.owner), "Affordability reads current XP after the preview was created");
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            helper.assertTrue(f.menu.quickMoveStack(f.owner, 2).isEmpty(), "A direct quick transfer also rechecks the reduced balance");
            helper.assertTrue(f.menu.getCarried().isEmpty(), "An old affordable preview cannot leak a result after XP loss");
            helper.assertValueEqual(f.owner.getInventory().countItem(Items.IRON_PICKAXE), 0, "The rejected shift transfer adds no inventory item");
            helper.assertTrue(ItemStack.matches(input, f.menu.getSlot(0).getItem()), "Rejected payment consumes no enchanted input");
            helper.assertTrue(ItemStack.matches(preview, f.menu.getSlot(2).getItem()), "Rejected payment does not hide or mutate its preview");
            helper.assertValueEqual(f.cost(), cost, "Experience changes and failed attempts cannot reroll the fee");
            helper.assertValueEqual(f.owner.totalExperience, cost - 1, "Failed payment leaves all remaining XP untouched");
            f.owner.giveExperiencePoints(1);
            helper.assertTrue(f.access().mite$canTake(f.owner), "Adding the missing point unlocks the existing result");
            helper.assertValueEqual(f.cost(), cost, "Topping up XP preserves the exact original quote");
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            helper.assertTrue(f.menu.getCarried().is(Items.IRON_PICKAXE), "The same preview becomes obtainable after topping up");
            helper.assertValueEqual(f.owner.totalExperience, 0, "The successful transaction charges exactly the previously shown fee");
            f.assertConsumed();
            f.assertNoExperienceOrbs();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroExperienceAndExistingDebtNeverCreateFurtherDisenchantingDebt(GameTestHelper helper) {
        for (int startingExperience : new int[] {0, -1, -799, -800}) {
            try (Fixture f = new Fixture(helper, startingExperience)) {
                f.menu.getSlot(0).set(enchanted(helper, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 1));
                ItemStack input = f.menu.getSlot(0).getItem().copy();
                helper.assertFalse(f.access().mite$canTake(f.owner), "Zero or negative XP cannot pay a positive fee");
                helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "The unpaid result remains visible for its expense tooltip");
                f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
                helper.assertTrue(f.menu.getCarried().isEmpty(), "An unpaid transaction cannot produce its output");
                helper.assertTrue(ItemStack.matches(input, f.menu.getSlot(0).getItem()), "An unpaid transaction preserves its original enchanted input");
                helper.assertValueEqual(f.owner.totalExperience, startingExperience, "Disenchanting never introduces or increases experience debt");
                f.assertNoExperienceOrbs();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroFeeRepairAndCurseOnlyTransactionsRemainAvailableWithExistingDebt(GameTestHelper helper) {
        for (boolean repair : new boolean[] {false, true}) {
            try (Fixture f = new Fixture(helper, -800)) {
                if (repair) {
                    ItemStack top = new ItemStack(Items.BRUSH);
                    ItemStack bottom = new ItemStack(Items.BRUSH);
                    top.setDamageValue(50);
                    bottom.setDamageValue(50);
                    f.menu.getSlot(0).set(top);
                    f.menu.getSlot(1).set(bottom);
                } else {
                    f.menu.getSlot(0).set(enchanted(helper, Items.IRON_PICKAXE, Enchantments.VANISHING_CURSE, 1));
                }
                helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "Existing debt keeps an otherwise valid zero-fee result");
                helper.assertValueEqual(f.cost(), 0, "The operation has no removable non-curse experience value");
                helper.assertTrue(f.access().mite$canTake(f.owner), "A zero fee does not introduce a new experience gate");
                f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
                helper.assertTrue(f.menu.getCarried().is(repair ? Items.BRUSH : Items.IRON_PICKAXE), "A free result is obtainable despite preexisting debt");
                if (repair) helper.assertTrue(f.menu.getCarried().getDamageValue() < 50, "The free brush operation actually repairs its inputs");
                else helper.assertValueEqual(level(helper, f.menu.getCarried(), Enchantments.VANISHING_CURSE), 1, "The curse-only transaction retains its curse");
                helper.assertValueEqual(f.owner.totalExperience, -800, "A free transaction leaves the preexisting debt unchanged");
                f.assertConsumed();
                f.assertNoExperienceOrbs();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inputChangesRerollTheFeeAndNativeDataPacketsPublishItToTheRemoteMenu(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 1000)) {
            GrindstoneMenu client = new GrindstoneMenu(72, f.owner.getInventory());
            helper.assertValueEqual(access(client).mite$experienceCost(), -1, "An unsynchronized remote menu starts with no quoted fee");
            f.menu.addSlotListener(new DataBridge(client));
            f.seed(6089L);
            f.menu.getSlot(0).set(enchanted(helper, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 4));
            int firstCost = 2 * reward(31, 6089L);
            helper.assertValueEqual(f.cost(), firstCost, "The initial input receives its seeded preview fee");
            helper.assertValueEqual(access(client).mite$experienceCost(), firstCost, "Native menu data packets deliver the initial tooltip fee");
            f.seed(8123L);
            f.menu.getSlot(0).set(enchanted(helper, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 3));
            int replacementCost = 2 * reward(21, 8123L);
            helper.assertValueEqual(f.cost(), replacementCost, "A genuinely different input receives a new random quote");
            helper.assertValueEqual(access(client).mite$experienceCost(), replacementCost, "The remote tooltip receives the replacement's quote");
            f.menu.getSlot(0).set(ItemStack.EMPTY);
            helper.assertValueEqual(access(client).mite$experienceCost(), -1, "The remote tooltip fee clears after inputs are removed");
            helper.assertValueEqual(f.owner.totalExperience, 1000, "Synchronization and preview changes do not spend experience");
            f.assertNoExperienceOrbs();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void largeCustomRewardFeeSurvivesTheActualSignedShortMenuPacketCodec(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 200000)) {
            GrindstoneMenu client = new GrindstoneMenu(72, f.owner.getInventory());
            f.menu.addSlotListener(new DataBridge(client));
            ItemStack input = enchanted(helper, Items.ENCHANTED_BOOK, Enchantments.EFFICIENCY, 1);
            Consumer<GrindstoneEvent.OnPlaceItem> customReward = event -> {
                if (event.getTopItem() == input) {
                    event.setOutput(new ItemStack(Items.BOOK));
                    event.setXp(65535);
                }
            };
            NeoForge.EVENT_BUS.addListener(GrindstoneEvent.OnPlaceItem.class, customReward);
            try {
                f.menu.getSlot(0).set(input);
                helper.assertValueEqual(f.cost(), 131070, "An existing custom native reward is also doubled as an exact point fee");
                helper.assertValueEqual(access(client).mite$experienceCost(), 131070,
                        "Two real signed-short packets retain the high word and restore low-word 65534 decoded as minus two");
            } finally {
                NeoForge.EVENT_BUS.unregister(customReward);
            }
            helper.assertValueEqual(f.owner.totalExperience, 200000, "Publishing a large quote spends no experience");
            f.assertNoExperienceOrbs();
        }
        helper.succeed();
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        enchant(helper, stack, key, level);
        return stack;
    }

    private static void enchant(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key, int level) {
        stack.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key), level);
    }

    private static int level(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack)
                .getLevel(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key));
    }

    private static int minimumCost(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key).value().getMinCost(level);
    }

    private static int reward(int summedMinimumCost, long seed) {
        int lower = (summedMinimumCost + 1) / 2;
        return lower + RandomSource.create(seed).nextInt(lower);
    }

    private static GrindstoneExperienceAccess access(GrindstoneMenu menu) {
        return (GrindstoneExperienceAccess) menu;
    }

    private static final class DataBridge implements ContainerListener {
        final GrindstoneMenu client;

        DataBridge(GrindstoneMenu client) {
            this.client = client;
        }

        @Override
        public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {
            // Both test menus share a mock ServerLevel; mirroring inputs would roll server RNG again.
            // Real ClientLevel menus do not quote locally. Exercise only the native data-packet channel here.
        }

        @Override
        public void dataChanged(AbstractContainerMenu menu, int index, int value) {
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundContainerSetDataPacket.STREAM_CODEC.encode(bytes,
                        new ClientboundContainerSetDataPacket(menu.containerId, index, value));
                ClientboundContainerSetDataPacket packet = ClientboundContainerSetDataPacket.STREAM_CODEC.decode(bytes);
                client.setData(packet.getId(), packet.getValue());
            } finally {
                bytes.release();
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final Player owner;
        final BlockPos pos;
        final GrindstoneMenu menu;
        final BlockState original;
        final BlockState below;

        Fixture(GameTestHelper helper, int experience) {
            this.helper = helper;
            pos = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(pos);
            below = helper.getLevel().getBlockState(pos.below());
            helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, Blocks.GRINDSTONE.defaultBlockState(), 3);
            owner = helper.makeMockPlayer(GameType.SURVIVAL);
            owner.moveTo(pos.getCenter());
            PlayerExperience.setTotal(owner, experience);
            menu = new GrindstoneMenu(72, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        }

        void seed(long seed) {
            helper.getLevel().random.setSeed(seed);
        }

        GrindstoneExperienceAccess access() {
            return GrindstoneExperienceGameTests.access(menu);
        }

        int cost() {
            return access().mite$experienceCost();
        }

        void assertConsumed() {
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(),
                    "A completed transaction consumes the real grindstone inputs");
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Taking the result clears the real output slot");
        }

        void assertNoExperienceOrbs() {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(2)).isEmpty(),
                    "The actual server-side grindstone transaction never creates experience orbs");
        }

        @Override
        public void close() {
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            owner.getInventory().clearContent();
            helper.getLevel().setBlock(pos, original, 3);
            helper.getLevel().setBlock(pos.below(), below, 3);
        }
    }
}
