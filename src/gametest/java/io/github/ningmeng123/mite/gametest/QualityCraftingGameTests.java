package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent R196 XP boundaries exercised through actual result slots and server manufacture. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class QualityCraftingGameTests {
    @GameTest(template = "empty")
    public static void copperPickPreviewUsesMaterialRangeRegardlessOfExperienceAndWraps(GameTestHelper helper) {
        // Requested UI behavior exposes all material-supported qualities, including unaffordable ones.
        for (int experience : new int[]{0, 499, 500, 999, 1000}) {
            try (Fixture f = new Fixture(helper, experience)) {
                f.loadPickaxes(1);
                f.assertPreviewQuality(2);
                for (int quality = 3; quality <= 4; quality++) {
                    // The actual idle right-click path cycles one quality without starting a batch.
                    f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
                    f.assertPreviewQuality(quality);
                    helper.assertValueEqual(TimedCraftingController.progress(f.player), 0,
                            "Cycling quality alone must not begin manufacturing");
                }
                f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
                f.assertPreviewQuality(2);
                TimedCraftingController.selectQuality(f.player, f.menu, -1);
                f.assertPreviewQuality(4);
                TimedCraftingController.selectQuality(f.player, f.menu, 0);
                TimedCraftingController.selectQuality(f.player, f.menu, 7);
                f.assertPreviewQuality(4);
                helper.assertValueEqual(f.player.totalExperience, experience, "Previewing does not spend experience");
                f.assertPickInputs(1);
                f.assertOutputs(0);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unaffordablePreviewKeepsItsQualityUntilExperienceAllowsAnExplicitRetry(GameTestHelper helper) {
        // R196 copper pick D1250 still costs exactly 500 XP for fine and 1000 for excellent.
        for (int[] entry : new int[][]{{0, 3, 500}, {499, 3, 500}, {999, 4, 1000}}) {
            try (Fixture f = new Fixture(helper, entry[0])) {
                f.loadPickaxes(1);
                f.select(entry[1]);
                f.assertPreviewQuality(entry[1]);
                helper.assertFalse(TimedCraftingController.start(f.player, f.menu),
                        "Selecting a visible unaffordable quality must not authorize manufacturing");
                f.tick(f.period() * 2);
                helper.assertValueEqual(TimedCraftingController.progress(f.player), 0,
                        "Unaffordable selections do not accumulate manufacturing progress");
                helper.assertFalse(TimedCraftingController.isActive(f.player), "An unaffordable click creates no queued batch");
                f.assertPreviewQuality(entry[1]);
                f.assertPickInputs(1);
                f.assertOutputs(0);
                helper.assertValueEqual(f.player.totalExperience, entry[0], "Rejected manufacture changes no experience");

                f.player.giveExperiencePoints(entry[2] - entry[0]);
                f.tick(1);
                f.assertPreviewQuality(entry[1]);
                helper.assertFalse(TimedCraftingController.isActive(f.player),
                        "Restoring sufficient experience preserves selection but cannot start an old rejected request");
                int period = f.period();
                f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
                f.tick(period - 1);
                f.assertPickInputs(1);
                f.assertOutputs(0);
                helper.assertValueEqual(f.player.totalExperience, entry[2], "The explicit retry still charges only on completion");
                f.tick(1);
                f.assertPickInputs(0);
                f.assertOutputs(1);
                helper.assertValueEqual(EquipmentDurability.quality(f.firstOutput()).ordinal(), entry[1],
                        "The successful retry delivers the originally previewed higher quality");
                helper.assertValueEqual(f.player.totalExperience, 0, "The newly affordable batch pays its unchanged exact XP fee");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void resultClickVariantsCannotExtractAnUnaffordableExcellentPreview(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 0)) {
            f.loadPickaxes(2);
            f.select(4);
            f.assertPreviewQuality(4);
            var existingDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, f.player.getBoundingBox().inflate(3))
                    .stream().map(ItemEntity::getUUID).toList();
            for (ClickType click : new ClickType[]{ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP,
                    ClickType.THROW, ClickType.CLONE, ClickType.PICKUP_ALL, ClickType.QUICK_CRAFT}) {
                int button = click == ClickType.CLONE ? 2 : 0;
                f.menu.clicked(0, button, click, f.player);
                f.tick(1);
                f.assertPreviewQuality(4);
                f.assertPickInputs(2);
                f.assertOutputs(0);
                helper.assertValueEqual(TimedCraftingController.progress(f.player), 0,
                        "Result-slot " + click + " cannot bypass the XP check for a visible preview");
                helper.assertFalse(TimedCraftingController.isActive(f.player), "A rejected result click cannot queue manufacture");
            }
            f.menu.clicked(0, 40, ClickType.SWAP, f.player);
            f.menu.clicked(0, 1, ClickType.THROW, f.player);
            f.menu.quickMoveStack(f.player, 0);
            f.tick(f.period() * 2);
            f.assertPreviewQuality(4);
            f.assertPickInputs(2);
            f.assertOutputs(0);
            helper.assertTrue(f.player.getOffhandItem().isEmpty(), "Offhand swapping cannot extract the unpaid result");
            helper.assertFalse(helper.getLevel().getEntitiesOfClass(ItemEntity.class, f.player.getBoundingBox().inflate(3))
                            .stream().anyMatch(drop -> drop.getItem().is(f.output) && !existingDrops.contains(drop.getUUID())),
                    "Throwing the result cannot create an unpaid equipment drop outside the inventory");
            helper.assertValueEqual(f.player.totalExperience, 0, "No click path can convert a preview into negative-XP manufacturing");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paidQualityChargesOnlyAtCompletionAndShiftStopsAfterOneBatch(GameTestHelper helper) {
        for (int[] entry : new int[][]{{500, 3, 500, 7200}, {1000, 4, 1000, 9600}, {1000, 3, 500, 7200}}) {
            try (Fixture f = new Fixture(helper, entry[0])) {
                f.loadPickaxes(2);
                f.select(entry[1]);
                int period = f.period();
                f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
                f.tick(period - 1);
                helper.assertValueEqual(TimedCraftingController.progress(f.player), period - 1, "The paid recipe uses its complete production period");
                helper.assertValueEqual(f.player.totalExperience, entry[0], "No prepayment before the final tick");
                f.assertPickInputs(2);
                f.assertOutputs(0);
                f.tick(1);
                f.assertPickInputs(1);
                f.assertOutputs(1);
                helper.assertValueEqual(f.player.totalExperience, entry[0] - entry[2], "Exactly the R196 point cost is charged once");
                ItemStack output = f.firstOutput();
                helper.assertValueEqual(output.getOrDefault(EquipmentDurability.QUALITY.get(), -1), entry[1], "Output carries the selected serialized quality component");
                helper.assertValueEqual(output.getMaxDamage(), entry[3], "Produced copper pick durability uses its real quality");
                f.tick(period * 2);
                f.assertPickInputs(1);
                f.assertOutputs(1);
                helper.assertValueEqual(f.player.totalExperience, entry[0] - entry[2], "Even with enough XP remaining, paid Shift crafting never starts a second batch");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryQualityStillSupportsRepeatedShiftBatchesWithoutXpCost(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 1000)) {
            f.loadPickaxes(2);
            f.assertPreviewQuality(2);
            int period = f.period();
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            for (int batch = 0; batch < 2; batch++) {
                f.tick(period - 1);
                f.assertPickInputs(2 - batch);
                f.assertOutputs(batch);
                f.tick(1);
                f.assertPickInputs(1 - batch);
                f.assertOutputs(batch + 1);
            }
            for (ItemStack stack : f.player.getInventory().items) if (stack.is(f.output)) {
                helper.assertValueEqual(EquipmentDurability.quality(stack).ordinal(), 2, "Free continuous manufacture preserves ordinary quality");
                helper.assertValueEqual(stack.getMaxDamage(), 4800, "Ordinary copper picks keep base durability");
            }
            helper.assertValueEqual(f.player.totalExperience, 1000, "Having access to better quality does not charge for ordinary batches");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelingPaidManufacturePreservesInputsAndExperience(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 500)) {
            f.loadPickaxes(2);
            f.select(3);
            int period = f.period();
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(period / 2);
            helper.assertTrue(TimedCraftingController.progress(f.player) > 0, "The tested right-click interrupts a running batch");
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            helper.assertValueEqual(TimedCraftingController.progress(f.player), 0, "Running right-click cancels instead of switching quality");
            f.tick(period * 2);
            helper.assertValueEqual(f.player.totalExperience, 500, "Cancellation never pays a partial crafting cost");
            f.assertPickInputs(2);
            f.assertOutputs(0);
            // The next idle click has the distinct R196 quality-selection meaning.
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            f.assertPreviewQuality(4);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void losingXpDuringManufactureStopsWithoutDowngradingTheOutput(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 500)) {
            f.loadPickaxes(2);
            f.select(3);
            int period = f.period();
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            f.tick(period / 2);
            f.player.giveExperiencePoints(-1);
            helper.assertValueEqual(f.player.totalExperience, 499, "Another normal XP use crosses the quality boundary");
            f.tick(1);
            f.assertPreviewQuality(3);
            helper.assertValueEqual(TimedCraftingController.progress(f.player), 0, "An unaffordable chosen quality invalidates its progress");
            f.tick(period * 2);
            f.assertPickInputs(2);
            f.assertOutputs(0);
            helper.assertValueEqual(f.player.totalExperience, 499, "No crafting charge occurs after external XP loss");
            f.player.giveExperiencePoints(1);
            f.tick(period * 2);
            f.assertPreviewQuality(3);
            f.assertPickInputs(2);
            f.assertOutputs(0);
            helper.assertFalse(TimedCraftingController.isActive(f.player), "Recovered experience cannot resume the invalidated paid session automatically");
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(f.period());
            f.assertPickInputs(1);
            f.assertOutputs(1);
            helper.assertValueEqual(EquipmentDurability.quality(f.firstOutput()).ordinal(), 3,
                    "An explicit retry manufactures the retained fine selection");
            helper.assertValueEqual(f.player.totalExperience, 0, "Only the explicit completed retry spends 500 XP");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void negativeLevelsAndPrimitiveMaterialsConstrainActualQuality(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, -200)) {
            helper.assertValueEqual(f.player.experienceLevel, -10, "The fixture uses the real signed R196 XP curve");
            f.loadPickaxes(1);
            f.assertPreviewQuality(1);
            TimedCraftingController.selectQuality(f.player, f.menu, 1);
            f.assertPreviewQuality(2);
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu),
                    "Negative-level ability restrictions still reject ordinary manufacture even though its nominal XP fee is zero");
            f.select(3);
            f.assertPreviewQuality(3);
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu), "A player with negative XP may preview fine quality but cannot manufacture it");
            f.tick(f.period());
            f.assertPickInputs(1);
            f.assertOutputs(0);
            helper.assertValueEqual(f.player.totalExperience, -200, "Previewing an unaffordable quality never deepens XP debt");
            f.select(1);
            f.assertPreviewQuality(1);
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(f.period());
            f.assertOutputs(1);
            helper.assertValueEqual(f.firstOutput().getOrDefault(EquipmentDurability.QUALITY.get(), -1), 1,
                    "Level minus ten produces a real poor-quality copper pick");
            helper.assertValueEqual(f.firstOutput().getMaxDamage(), 3600, "Poor quality applies 0.75 durability");
            helper.assertValueEqual(f.player.totalExperience, -200, "Poor crafting does not deepen XP debt");
        }
        try (Fixture f = new Fixture(helper, 203000)) {
            f.loadPrimitive(false);
            f.assertPreviewQuality(2);
            TimedCraftingController.selectQuality(f.player, f.menu, 1);
            f.assertPreviewQuality(3);
            TimedCraftingController.selectQuality(f.player, f.menu, 1);
            f.assertPreviewQuality(2);
            f.loadPrimitive(true);
            f.assertPreviewQuality(2);
            TimedCraftingController.selectQuality(f.player, f.menu, -1);
            f.assertPreviewQuality(3);
            TimedCraftingController.selectQuality(f.player, f.menu, 1);
            f.assertPreviewQuality(2);
            helper.assertValueEqual(f.player.totalExperience, 203000, "Huge XP balances cannot bypass flint or wood material caps");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inputReplacementAndClosedMenusCannotRetainPaidProgress(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 500)) {
            f.loadPickaxes(2);
            f.select(3);
            int period = f.period();
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(period / 2);
            ItemStack removed = f.menu.getSlot(1).getItem().copy();
            f.menu.getSlot(1).set(ItemStack.EMPTY);
            f.menu.getSlot(1).set(removed);
            f.tick(1);
            helper.assertValueEqual(TimedCraftingController.progress(f.player), 0, "Same-tick remove/restore cannot preserve a paid session");
            f.assertPreviewQuality(2);
            f.tick(period * 2);
            f.assertPickInputs(2);
            f.assertOutputs(0);
            f.select(3);
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(period / 2);
            f.player.closeContainer();
            f.tick(period * 2);
            f.assertOutputs(0);
            helper.assertValueEqual(f.player.totalExperience, 500, "Closing a paid crafting menu does not charge XP");
            f.player.getInventory().clearContent();
            f.openMenu();
            f.loadPickaxes(1);
            f.assertPreviewQuality(2);
            helper.assertValueEqual(TimedCraftingController.progress(f.player), 0, "A new container session starts with minimum quality and zero progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledOrModifiedXpEventsCannotGrantFreeQuality(GameTestHelper helper) {
        // Both kinds of NeoForge event may veto payment; modified amounts cannot grant a partial discount.
        for (int mode = 0; mode < 4; mode++) {
            final int intervention = mode;
            try (Fixture f = new Fixture(helper, 500)) {
                f.loadPickaxes(1);
                f.select(3);
                int period = f.period();
                int score = f.player.getScore();
                int[] intercepted = {0};
                Consumer<PlayerXpEvent.XpChange> points = event -> {
                    if (event.getEntity() != f.player || event.getAmount() >= 0 || intervention > 1) return;
                    intercepted[0]++;
                    if (intervention == 0) event.setCanceled(true);
                    else event.setAmount(0);
                };
                Consumer<PlayerXpEvent.LevelChange> levels = event -> {
                    if (event.getEntity() != f.player || event.getLevels() >= 0 || intervention < 2) return;
                    intercepted[0]++;
                    if (intervention == 2) event.setCanceled(true);
                    else event.setLevels(0);
                };
                NeoForge.EVENT_BUS.addListener(PlayerXpEvent.XpChange.class, points);
                NeoForge.EVENT_BUS.addListener(PlayerXpEvent.LevelChange.class, levels);
                try {
                    f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
                    f.tick(period);
                    helper.assertTrue(intercepted[0] > 0, "The real completion transaction consults the relevant XP event");
                    f.assertPickInputs(1);
                    f.assertOutputs(0);
                    helper.assertValueEqual(f.player.totalExperience, 500, "Rejected or modified payment leaves the original balance intact");
                    helper.assertValueEqual(f.player.getScore(), score, "Rejected payment cannot partially update vanilla score");
                } finally {
                    NeoForge.EVENT_BUS.unregister(points);
                    NeoForge.EVENT_BUS.unregister(levels);
                }
                f.select(3);
                f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
                f.tick(f.period());
                f.assertPickInputs(0);
                f.assertOutputs(1);
                helper.assertValueEqual(f.player.totalExperience, 0, "Removing the veto restores exactly one paid craft");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legendaryAdamantiumUsesExperienceBeyondLevelTwoHundred(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 491840)) {
            helper.assertValueEqual(f.player.experienceLevel, 312, "The requested uncapped formula gives T312=491400 with 440 points remaining");
            helper.assertValueEqual(f.player.totalExperience, 491840, "Legendary adamantium pick cost remains available above 203000");
            helper.getLevel().setBlockAndUpdate(f.table, MiteWorkbenches.TABLES.get("adamantium").get().defaultBlockState());
            f.output = MiteTools.get("adamantium_pickaxe");
            for (int slot : new int[]{1, 2, 3}) f.menu.getSlot(slot).set(new ItemStack(MiteTools.get("adamantium_ingot")));
            for (int slot : new int[]{5, 8}) f.menu.getSlot(slot).set(new ItemStack(Items.STICK));
            f.tick(1);
            f.select(7);
            int period = f.period();
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            f.tick(period - 1);
            f.assertOutputs(0);
            helper.assertValueEqual(f.player.totalExperience, 491840, "The large fee is not prepaid");
            f.tick(1);
            f.assertOutputs(1);
            helper.assertValueEqual(EquipmentDurability.quality(f.firstOutput()).ordinal(), 7, "Actual legendary output can be crafted");
            helper.assertValueEqual(f.firstOutput().getMaxDamage(), 1075200, "307200 base times 3.5 is applied once");
            helper.assertValueEqual(f.player.totalExperience, 0, "The exact 491840-point fee is charged");
            helper.assertValueEqual(f.player.experienceLevel, 0, "Spending the balance recomputes the normal experience curve");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paymentListenerCannotCompleteARecipeAfterMakingThePlayerHungry(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 500)) {
            f.loadPickaxes(1);
            f.select(3);
            int period = f.period();
            Consumer<PlayerXpEvent.XpChange> hungry = event -> {
                if (event.getEntity() == f.player && event.getAmount() < 0) {
                    f.player.getFoodData().setFoodLevel(0);
                    ((io.github.ningmeng123.mite.survival.DualFoodData)f.player.getFoodData()).mite$setNutrition(0);
                }
            };
            NeoForge.EVENT_BUS.addListener(hungry);
            try {
                f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
                f.tick(period);
                f.assertOutputs(0);
                f.assertPickInputs(1);
                helper.assertValueEqual(f.player.totalExperience, 500, "The final hunger recheck occurs before payment");
                helper.assertTrue(TimedCraftingController.isActive(f.player), "Hunger pauses rather than discarding the batch");
            } finally {
                NeoForge.EVENT_BUS.unregister(hungry);
            }
            f.player.getFoodData().setFoodLevel(6);
            f.tick(1);
            f.assertOutputs(1);
            helper.assertValueEqual(f.player.totalExperience, 0, "After food returns only the single approved fee is paid");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos table;
        private final BlockState original;
        private CraftingMenu menu;
        private CraftingContainer grid;
        private Item output;

        private Fixture(GameTestHelper helper, int experience) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-quality"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, experience);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get("copper").get().defaultBlockState());
            openMenu();
        }

        private void openMenu() {
            menu = new CraftingMenu(43, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            grid = (CraftingContainer) menu.getSlot(1).container;
        }

        private void loadPickaxes(int count) {
            grid.clearContent();
            output = MiteTools.get("copper_pickaxe");
            for (int slot : new int[]{1, 2, 3}) menu.getSlot(slot).set(new ItemStack(Items.COPPER_INGOT, count));
            for (int slot : new int[]{5, 8}) menu.getSlot(slot).set(new ItemStack(Items.STICK, count));
            tick(1);
            helper.assertTrue(menu.stillValid(player), "The production menu uses a physically placed copper workbench");
            helper.assertTrue(menu.getSlot(0).getItem().is(output), "The actual R196 copper pick recipe matches");
        }

        private void loadPrimitive(boolean wood) {
            grid.clearContent();
            if (wood) {
                output = MiteTools.get("wooden_cudgel");
                menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
                menu.getSlot(4).set(new ItemStack(Items.STICK));
            } else {
                output = MiteTools.get("flint_hatchet");
                menu.getSlot(1).set(new ItemStack(Items.FLINT));
                menu.getSlot(2).set(new ItemStack(Items.STICK));
                menu.getSlot(4).set(new ItemStack(Items.STRING));
                menu.getSlot(5).set(new ItemStack(Items.STICK));
            }
            tick(1);
            helper.assertTrue(menu.getSlot(0).getItem().is(output), "The actual primitive tool recipe matches");
        }

        private void select(int wanted) {
            for (int attempt = 0; attempt < 8; attempt++) {
                if (EquipmentDurability.quality(menu.getSlot(0).getItem()).ordinal() == wanted) return;
                TimedCraftingController.selectQuality(player, menu, 1);
            }
            assertPreviewQuality(wanted);
        }

        private void assertPreviewQuality(int expected) {
            helper.assertTrue(menu.getSlot(0).getItem().is(output), "Quality changes preserve the matched recipe output type");
            helper.assertValueEqual(EquipmentDurability.quality(menu.getSlot(0).getItem()).ordinal(), expected,
                    "The server-owned result slot exposes the allowed selected quality");
            if (expected != 2) helper.assertValueEqual(menu.getSlot(0).getItem().getOrDefault(EquipmentDurability.QUALITY.get(), -1), expected,
                    "Non-default preview quality is an actual item data component");
        }

        private int period() {
            int period = TimedCraftingController.period(player, menu);
            helper.assertTrue(period >= 25, "The test advances complete production ticks, without a timing shortcut");
            return period;
        }

        private void tick(int ticks) {
            for (int tick = 0; tick < ticks; tick++) TimedCraftingController.tick(player);
        }

        private void assertPickInputs(int count) {
            for (int slot : new int[]{0, 1, 2, 4, 7}) helper.assertValueEqual(grid.getItem(slot).getCount(), count,
                    "Only a successfully completed batch consumes each copper pick ingredient");
            for (int slot : new int[]{3, 5, 6, 8}) helper.assertTrue(grid.getItem(slot).isEmpty(), "Unused recipe cells remain empty");
        }

        private void assertOutputs(int count) {
            helper.assertValueEqual(player.getInventory().countItem(output), count, "Exactly the completed result count reaches the player");
            helper.assertTrue(menu.getCarried().isEmpty(), "Quality selection or crafting does not duplicate output onto the cursor");
        }

        private ItemStack firstOutput() {
            for (ItemStack stack : player.getInventory().items) if (stack.is(output)) return stack;
            helper.fail("Expected a manufactured output in the inventory");
            return ItemStack.EMPTY;
        }

        @Override public void close() {
            try {
                TimedCraftingController.cancel(player);
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
