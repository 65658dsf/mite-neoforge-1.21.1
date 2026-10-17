package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.item.RunestoneItem;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import io.github.ningmeng123.mite.survival.AutoRestock;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real authoritative actions prove the registered hooks; supplied clock values isolate source time boundaries. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AutoRestockGameTests {
    private AutoRestockGameTests() {}

    @GameTest(template = "empty")
    public static void nativeUseItemDepletionSelectsFirstCircularHotbarPeerWithoutMovingItsStack(GameTestHelper h) {
        try (var f = new Session(h)) {
            f.player.getInventory().selected = 7;
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SNOWBALL));
            ItemStack next = marked(Items.SNOWBALL, 3, "first circular peer");
            ItemStack later = marked(Items.SNOWBALL, 4, "later circular peer");
            ItemStack inventory = marked(Items.SNOWBALL, 5, "inventory peer");
            f.player.getInventory().setItem(8, next);
            f.player.getInventory().setItem(1, later);
            f.player.getInventory().setItem(12, inventory);
            int before = total(f.player);
            h.assertTrue(f.player.gameMode.useItem(f.player, h.getLevel(), f.player.getMainHandItem(), InteractionHand.MAIN_HAND).consumesAction(),
                    "The actual ServerPlayerGameMode use consumes the last thrown snowball");
            h.assertTrue(f.player.getInventory().getItem(7).isEmpty(), "The real action depleted the previous selected slot");
            AutoRestock.tick(f.player, Util.getMillis());
            h.assertValueEqual(f.player.getInventory().selected, 8, "The registered useItem hook authorizes the first circular same-item hotbar peer");
            h.assertTrue(f.player.getInventory().getItem(8) == next && f.player.getInventory().getItem(1) == later
                            && f.player.getInventory().getItem(12) == inventory,
                    "Hotbar selection does not recreate, split or relocate any replacement stack");
            h.assertValueEqual(total(f.player), before - 1, "Only the actual projectile action consumes an item");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativePlacementRefillsTheDepletedHandFromTheActualInventoryStack(GameTestHelper h) {
        BlockPos support = h.absolutePos(new BlockPos(3, 2, 3)), placed = support.above();
        var originalSupport = h.getLevel().getBlockState(support);
        var originalPlaced = h.getLevel().getBlockState(placed);
        try (var f = new Session(h)) {
            h.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(placed, Blocks.AIR.defaultBlockState());
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
            ItemStack spare = marked(Items.COBBLESTONE, 4, "placed inventory refill");
            ItemStack expected = spare.copy();
            f.player.getInventory().setItem(13, spare);
            int before = total(f.player);
            var result = f.player.gameMode.useItemOn(f.player, h.getLevel(), f.player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support).add(0, .5D, 0), Direction.UP, support, false));
            h.assertTrue(result.consumesAction() && h.getLevel().getBlockState(placed).is(Blocks.COBBLESTONE),
                    "The real useItemOn transaction placed the block");
            AutoRestock.tick(f.player, Util.getMillis() + 250L);
            h.assertTrue(f.player.getMainHandItem() == spare && ItemStack.matches(f.player.getMainHandItem(), expected)
                            && f.player.getInventory().getItem(13).isEmpty(),
                    "The registered placement hook moves the existing full stack and preserves every custom component");
            h.assertValueEqual(total(f.player), before - 1, "The placed block is the only inventory loss");
        } finally {
            h.getLevel().setBlockAndUpdate(placed, originalPlaced);
            h.getLevel().setBlockAndUpdate(support, originalSupport);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualFoodCompletionRatherThanUseStartAuthorizesTheNextFoodStack(GameTestHelper h) {
        try (var f = new Session(h)) {
            f.resources(0, 0);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            ItemStack spare = marked(Items.APPLE, 3, "completed food refill");
            f.player.getInventory().setItem(2, spare);
            f.player.gameMode.useItem(f.player, h.getLevel(), f.player.getMainHandItem(), InteractionHand.MAIN_HAND);
            h.assertTrue(f.player.isUsingItem(), "The real server use starts the eating lifecycle");
            AutoRestock.tick(f.player, Util.getMillis());
            h.assertValueEqual(f.player.getInventory().selected, 0, "Starting a bite does not authorize refilling");
            for (int tick = 0; tick < 31; tick++) f.player.doTick();
            h.assertTrue(f.player.getInventory().getItem(0).is(Items.APPLE), "The apple remains through the first31 actual use ticks");
            f.player.doTick();
            AutoRestock.tick(f.player, Util.getMillis());
            h.assertTrue(!f.player.isUsingItem() && f.player.getInventory().getItem(0).isEmpty(), "The native completed bite consumes the last apple");
            h.assertValueEqual(f.player.getInventory().selected, 2, "The registered completeUsingItem hook selects the replacement");
            h.assertTrue(f.player.getMainHandItem() == spare && spare.getCount() == 3, "Food refilling preserves the existing named stack without eating a second bite");
            h.assertValueEqual(total(f.player), 3, "Exactly one of the four initial apples was eaten");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualMiningToolBreakAuthorizesItsIntactBackup(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(3, 3, 2));
        var original = h.getLevel().getBlockState(pos);
        try (var f = new Session(h)) {
            h.getLevel().setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
            ItemStack tool = new ItemStack(MiteItems.FLINT_HATCHET.get());
            tool.setDamageValue(tool.getMaxDamage() - 1);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            ItemStack spare = marked(MiteItems.FLINT_HATCHET.get(), 1, "mining tool refill");
            spare.setDamageValue(17);
            ItemStack expected = spare.copy();
            f.player.getInventory().setItem(4, spare);
            h.assertTrue(f.player.gameMode.destroyBlock(pos), "Real survival destruction removes the log using its last-durability tool");
            h.assertTrue(f.player.getInventory().getItem(0).isEmpty(), "The actual mining wear destroyed the previous tool");
            AutoRestock.tick(f.player, Util.getMillis());
            h.assertValueEqual(f.player.getInventory().selected, 4, "The registered destroyBlock hook selects the backup");
            h.assertTrue(ItemStack.matches(f.player.getMainHandItem(), expected), "The backup keeps its wear and custom data rather than receiving the broken tool's damage");
        } finally { h.getLevel().setBlockAndUpdate(pos, original); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualMeleeWeaponBreakAlsoAuthorizesItsBackup(GameTestHelper h) {
        try (var f = new Session(h)) {
            var cow = h.spawn(EntityType.COW, new BlockPos(3, 3, 2));
            cow.setNoAi(true); cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); cow.setHealth(200F);
            try {
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                sword.setDamageValue(sword.getMaxDamage() - 1);
                f.player.setItemInHand(InteractionHand.MAIN_HAND, sword);
                ItemStack spare = marked(Items.IRON_SWORD, 1, "melee weapon refill");
                f.player.getInventory().setItem(5, spare);
                for (int tick = 0; tick < 21; tick++) f.player.doTick();
                f.player.attack(cow);
                h.assertTrue(cow.getHealth() < 200F && f.player.getInventory().getItem(0).isEmpty(),
                        "A real charged melee hit hurts the cow and destroys the last-durability weapon");
                AutoRestock.tick(f.player, Util.getMillis());
                h.assertValueEqual(f.player.getInventory().selected, 5, "The registered Player.attack hook selects the intact backup");
                h.assertTrue(f.player.getMainHandItem() == spare && spare.getDamageValue() == 0, "Melee refilling does not transfer wear or consume the backup");
            } finally { cow.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inventorySameItemOutranksHotbarSimilarItemAndWaitsExactly250ms(GameTestHelper h) {
        try (var f = new Session(h)) {
            f.player.getInventory().selected = 6;
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            ItemStack similar = marked(Items.IRON_SWORD, 1, "similar hotbar weapon");
            ItemStack exact = marked(Items.IRON_PICKAXE, 1, "exact inventory tool");
            exact.setDamageValue(23);
            ItemStack expected = exact.copy();
            f.player.getInventory().setItem(7, similar);
            f.player.getInventory().setItem(14, exact);
            var before = AutoRestock.before(f.player, InteractionHand.MAIN_HAND);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AutoRestock.after(before, 10_000L);
            AutoRestock.tick(f.player, 10_249L);
            h.assertTrue(f.player.getMainHandItem().isEmpty() && f.player.getInventory().selected == 6,
                    "A higher-scoring inventory exact match waits rather than selecting a lesser hotbar alternative");
            AutoRestock.tick(f.player, 10_250L);
            h.assertTrue(f.player.getInventory().selected == 6 && f.player.getMainHandItem() == exact
                            && ItemStack.matches(exact, expected) && f.player.getInventory().getItem(14).isEmpty(),
                    "At exactly250ms the original exact-match stack moves into the depleted hand");
            h.assertTrue(f.player.getInventory().getItem(7) == similar && total(f.player) == 2,
                    "The unused similar stack and total surviving item count remain unchanged");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aHotbarSimilarToolCanBeSelectedImmediatelyWhenNoExactStockExists(GameTestHelper h) {
        try (var f = new Session(h)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            ItemStack similar = marked(Items.IRON_SWORD, 1, "fallback weapon");
            f.player.getInventory().setItem(3, similar);
            var before = AutoRestock.before(f.player, InteractionHand.MAIN_HAND);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AutoRestock.after(before, 10_000L); AutoRestock.tick(f.player, 10_000L);
            h.assertTrue(f.player.getInventory().selected == 3 && f.player.getMainHandItem() == similar,
                    "A source-similar hotbar alternative needs no250ms inventory-move delay");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pendingRefillAccepts2999msButExpiresAt3000ms(GameTestHelper h) {
        for (int elapsed : new int[]{2999, 3000}) try (var f = new Session(h)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            ItemStack spare = marked(Items.APPLE, 2, "expiry boundary " + elapsed);
            f.player.getInventory().setItem(12, spare);
            var before = AutoRestock.before(f.player, InteractionHand.MAIN_HAND);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AutoRestock.after(before, 10_000L); AutoRestock.tick(f.player, 10_000L + elapsed);
            h.assertTrue((f.player.getMainHandItem() == spare) == (elapsed == 2999), "Source refill deadline at elapsed=" + elapsed);
            if (elapsed == 3000) {
                h.assertTrue(f.player.getInventory().getItem(12) == spare, "Expiry leaves the original inventory stock untouched");
                AutoRestock.tick(f.player, 13_001L);
                h.assertTrue(f.player.getMainHandItem().isEmpty(), "An expired request cannot revive on a later tick");
            }
            h.assertValueEqual(total(f.player), 2, "The timing boundary never creates or removes replacement stock");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void manualSlotAndContainerChangesCancelThePendingRequestPermanently(GameTestHelper h) {
        for (boolean menu : new boolean[]{false, true}) try (var f = new Session(h)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            ItemStack spare = marked(Items.APPLE, 2, "canceled request");
            f.player.getInventory().setItem(12, spare);
            var before = AutoRestock.before(f.player, InteractionHand.MAIN_HAND);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AutoRestock.after(before, 10_000L);
            if (menu) f.player.containerMenu = ChestMenu.oneRow(41, f.player.getInventory());
            else f.player.getInventory().selected = 1;
            AutoRestock.tick(f.player, 10_250L);
            f.player.containerMenu = f.player.inventoryMenu; f.player.getInventory().selected = 0;
            AutoRestock.tick(f.player, 10_251L);
            h.assertTrue(f.player.getMainHandItem().isEmpty() && f.player.getInventory().getItem(12) == spare,
                    "Returning from " + (menu ? "a real container menu" : "a manually selected slot") + " does not revive a canceled request");
            h.assertValueEqual(total(f.player), 2, "Cancellation preserves stock and its components");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualBowlAndBucketReturnsAreNeverOverwrittenByInventoryRefills(GameTestHelper h) {
        for (boolean bucket : new boolean[]{false, true}) for (boolean hotbar : new boolean[]{false, true}) try (var f = new Session(h)) {
            f.resources(0, 0);
            Item filled = bucket ? Items.MILK_BUCKET : MiteFoods.MILK_BOWL.get();
            Item empty = bucket ? Items.BUCKET : Items.BOWL;
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(filled));
            ItemStack spare = marked(filled, 1, "vessel replacement");
            int slot = hotbar ? 2 : 12;
            f.player.getInventory().setItem(slot, spare);
            f.player.gameMode.useItem(f.player, h.getLevel(), f.player.getMainHandItem(), InteractionHand.MAIN_HAND);
            h.assertTrue(f.player.isUsingItem(), "Actual filled vessel starts drinking");
            for (int tick = 0; tick < 32; tick++) f.player.doTick();
            AutoRestock.tick(f.player, Util.getMillis() + 250L);
            h.assertTrue(f.player.getInventory().getItem(0).is(empty) && f.player.getInventory().getItem(0).getCount() == 1,
                    "The real " + (bucket ? "bucket" : "bowl") + " remainder survives exactly once in the original slot");
            h.assertTrue(f.player.getInventory().getItem(slot) == spare, "An occupied returned-vessel slot never consumes or relocates replacement stock");
            h.assertValueEqual(f.player.getInventory().selected, hotbar ? slot : 0, "Hotbar refill may switch selection; inventory refill must leave the returned vessel selected");
            h.assertValueEqual(total(f.player), 2, "One returned vessel and one full replacement conserve container count");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeOffhandDepletionDoesNotAuthorizeAMainhandRefill(GameTestHelper h) {
        try (var f = new Session(h)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SNOWBALL));
            ItemStack spare = marked(Items.SNOWBALL, 2, "offhand must not select");
            f.player.getInventory().setItem(4, spare);
            f.player.gameMode.useItem(f.player, h.getLevel(), f.player.getOffhandItem(), InteractionHand.OFF_HAND);
            AutoRestock.tick(f.player, Util.getMillis() + 250L);
            h.assertTrue(f.player.getOffhandItem().isEmpty() && f.player.getMainHandItem().isEmpty()
                            && f.player.getInventory().selected == 0 && f.player.getInventory().getItem(4) == spare,
                    "A genuine depleted offhand is ignored without changing mainhand selection or stock");
            h.assertValueEqual(total(f.player), 2, "Only the offhand projectile consumes an item");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void differentRuneSymbolsAndFishingMaterialsAreNotAutomaticSubstitutes(GameTestHelper h) {
        for (boolean rod : new boolean[]{false, true}) try (var f = new Session(h)) {
            ItemStack previous = rod ? new ItemStack(MiteRangedItems.rod(Material.FLINT))
                    : RunestoneItem.withRune(new ItemStack(MiteWorldBlocks.MITHRIL_RUNESTONE_ITEM.get()), 3);
            ItemStack replacement = rod ? marked(MiteRangedItems.rod(Material.IRON), 1, "different hook metal")
                    : RunestoneItem.withRune(marked(MiteWorldBlocks.MITHRIL_RUNESTONE_ITEM.get(), 2, "different rune symbol"), 4);
            ItemStack expected = replacement.copy();
            f.player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            f.player.getInventory().setItem(3, replacement);
            var before = AutoRestock.before(f.player, InteractionHand.MAIN_HAND);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AutoRestock.after(before, 10_000L); AutoRestock.tick(f.player, 10_250L);
            h.assertTrue(f.player.getMainHandItem().isEmpty() && f.player.getInventory().selected == 0
                            && f.player.getInventory().getItem(3) == replacement && ItemStack.matches(replacement, expected),
                    "No automatic replacement crosses a source " + (rod ? "fishing-hook material identity" : "runestone symbol subtype"));
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void matchingRuneSubtypeCanRefillWithoutChangingItsSymbolOrCustomComponents(GameTestHelper h) {
        try (var f = new Session(h)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND,
                    RunestoneItem.withRune(new ItemStack(MiteWorldBlocks.MITHRIL_RUNESTONE_ITEM.get()), 3));
            ItemStack wrong = RunestoneItem.withRune(marked(MiteWorldBlocks.MITHRIL_RUNESTONE_ITEM.get(), 2, "wrong symbol peer"), 4);
            ItemStack right = RunestoneItem.withRune(marked(MiteWorldBlocks.MITHRIL_RUNESTONE_ITEM.get(), 3, "exact symbol inventory"), 3);
            ItemStack expected = right.copy();
            f.player.getInventory().setItem(2, wrong); f.player.getInventory().setItem(12, right);
            var before = AutoRestock.before(f.player, InteractionHand.MAIN_HAND);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AutoRestock.after(before, 10_000L); AutoRestock.tick(f.player, 10_250L);
            h.assertTrue(f.player.getMainHandItem() == right && ItemStack.matches(right, expected)
                            && RunestoneItem.rune(right) == 3 && f.player.getInventory().getItem(2) == wrong,
                    "An exact runestone subtype moves as its complete original stack while the wrong hotbar subtype stays untouched");
            h.assertValueEqual(total(f.player), 5, "Both surviving rune stacks retain their original counts");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void screenPauseArrivingBeforeActualFoodCompletionPreventsLateRegistration(GameTestHelper h) {
        try (var f = new Session(h)) {
            f.resources(0, 0);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            ItemStack spare = marked(Items.APPLE, 2, "paused completed bite");
            f.player.getInventory().setItem(2, spare);
            f.player.gameMode.useItem(f.player, h.getLevel(), f.player.getMainHandItem(), InteractionHand.MAIN_HAND);
            h.assertTrue(f.player.isUsingItem(), "Actual food use has started before the inventory-screen pause arrives");
            AutoRestock.pause(f.player, true);
            h.assertTrue(AutoRestock.before(f.player, InteractionHand.MAIN_HAND) == null,
                    "An inventory-screen pause suppresses registration even while an earlier action is finishing");
            for (int tick = 0; tick < 32; tick++) f.player.doTick();
            h.assertTrue(!f.player.isUsingItem() && f.player.getMainHandItem().isEmpty(), "The already-started bite may finish normally");
            AutoRestock.tick(f.player, Util.getMillis() + 250L);
            h.assertValueEqual(f.player.getInventory().selected, 0, "Completion while paused does not select backup food");
            AutoRestock.pause(f.player, false);
            AutoRestock.tick(f.player, Util.getMillis() + 251L);
            h.assertTrue(f.player.getMainHandItem().isEmpty() && f.player.getInventory().getItem(2) == spare,
                    "Closing the screen cannot recover a completion request that was suppressed while open");
            h.assertValueEqual(total(f.player), 2, "The real bite consumes one apple and the untouched replacement remains intact");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void screenPauseRejectsAnEarlierBeforeSnapshotAndDoesNotResumeItOnClose(GameTestHelper h) {
        try (var f = new Session(h)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            ItemStack spare = marked(Items.APPLE, 2, "paused after snapshot");
            f.player.getInventory().setItem(12, spare);
            var before = AutoRestock.before(f.player, InteractionHand.MAIN_HAND);
            AutoRestock.pause(f.player, true);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AutoRestock.after(before, 10_000L);
            AutoRestock.pause(f.player, false); AutoRestock.tick(f.player, 10_250L);
            h.assertTrue(f.player.getMainHandItem().isEmpty() && f.player.getInventory().getItem(12) == spare,
                    "Pause also rejects a result whose action snapshot predates the open-screen notification");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void openingScreenCancelsExistingPendingButClosingAllowsOnlyFreshActions(GameTestHelper h) {
        try (var f = new Session(h)) {
            ItemStack spare = marked(Items.APPLE, 2, "pending canceled by inventory screen");
            f.player.getInventory().setItem(12, spare);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            var before = AutoRestock.before(f.player, InteractionHand.MAIN_HAND);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AutoRestock.after(before, 10_000L);
            AutoRestock.pause(f.player, true); AutoRestock.pause(f.player, false);
            AutoRestock.tick(f.player, 10_250L);
            h.assertTrue(f.player.getMainHandItem().isEmpty() && f.player.getInventory().getItem(12) == spare,
                    "Opening permanently cancels the old pending request; closing does not resume it");
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            before = AutoRestock.before(f.player, InteractionHand.MAIN_HAND);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AutoRestock.after(before, 10_400L); AutoRestock.tick(f.player, 10_650L);
            h.assertTrue(f.player.getMainHandItem() == spare && f.player.getInventory().getItem(12).isEmpty(),
                    "The close notification lifts suppression for a newly authorized action");
        }
        h.succeed();
    }

    private static ItemStack marked(Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        CompoundTag tag = new CompoundTag(); tag.putString("restock_marker", name); tag.putInt("payload", 196);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static int total(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) count += player.getInventory().getItem(slot).getCount();
        return count;
    }

    private static final class Session implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final SpawnedEntityCapture spawned;
        private Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-restock"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(h.absolutePos(new BlockPos(2, 3, 2)).getCenter());
            player.setNoGravity(true); player.setOnGround(true); player.removeAllEffects();
            player.getInventory().clearContent(); player.getInventory().selected = 0;
            NutritionState.set(player, NutritionState.INITIAL); resources(6, 6);
            spawned = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof Projectile projectile && projectile.getOwner() == player
                    || entity instanceof ItemEntity && entity.distanceToSqr(player) < 36D);
        }
        private void resources(int satiation, int nutrition) {
            player.getFoodData().setFoodLevel(satiation);
            ((DualFoodData)player.getFoodData()).mite$setNutrition(nutrition);
        }
        @Override public void close() {
            try {
                player.stopUsingItem(); player.containerMenu = player.inventoryMenu;
                AutoRestock.pause(player, false);
                AutoRestock.tick(player, Long.MAX_VALUE);
                spawned.live(net.minecraft.world.entity.Entity.class).forEach(net.minecraft.world.entity.Entity::discard);
                player.getInventory().clearContent(); player.server.getPlayerList().remove(player);
            } finally { spawned.close(); channel.finishAndReleaseAll(); }
        }
    }
}
