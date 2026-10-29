package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.LegacyStackTransfers;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.VanillaInventoryCodeHooks;

/** Native inventory, menu, automation and serialization paths conserve items after lower source limits. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StackingTransferGameTests {
    private StackingTransferGameTests() {}

    @GameTest(template = "empty")
    public static void survivalPickupDistributesStoneAcrossFourItemSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickup = new ItemStack(Items.STONE, 63);
        helper.assertTrue(player.getInventory().add(pickup), "Native pickup inserts its permitted portion");
        helper.assertTrue(pickup.isEmpty(), "An empty inventory accepts all sixty-three stones");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 63, "Pickup loses no material");
        assertBounded(helper, player.getInventory(), Items.STONE, 4);
        helper.assertValueEqual(occupied(player.getInventory(), Items.STONE), 16, "Source four-item limit requires sixteen slots");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalFullInventoryPreservesUnacceptedPickupRemainder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        fill(player, Items.IRON_SWORD);
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
        ItemStack pickup = new ItemStack(Items.STONE, 9);
        player.getInventory().add(pickup);
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 4, "Only the one available stone space is filled");
        helper.assertValueEqual(pickup.getCount(), 8, "All unaccepted survival items remain available to the caller");
        helper.assertValueEqual(pickup.getCount() + player.getInventory().countItem(Items.STONE), 12, "Partial pickup conserves all twelve stones");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void componentDifferencesKeepSeparateStacksDuringInventoryPickup(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack existing = new ItemStack(Items.STONE, 2);
        existing.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 A"));
        player.getInventory().setItem(0, existing);
        ItemStack incoming = new ItemStack(Items.STONE, 5);
        incoming.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 B"));
        player.getInventory().add(incoming);
        helper.assertValueEqual(existing.getCount(), 2, "Names remain part of native component equality");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 7, "Component isolation retains every item");
        helper.assertValueEqual(occupied(player.getInventory(), Items.STONE), 3, "Different names cannot use the old partial stack");
        assertBounded(helper, player.getInventory(), Items.STONE, 4);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leftAndRightClickUseSourceLimitAndPreserveTheCursor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), chest);
        chest.setItem(0, new ItemStack(Items.STONE, 4));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertValueEqual(chest.getItem(0).getCount(), 2, "Native secondary pickup takes half a four-item stack");
        helper.assertValueEqual(menu.getCarried().getCount(), 2, "The removed half reaches the cursor");
        menu.clicked(1, 1, ClickType.PICKUP, player);
        helper.assertValueEqual(chest.getItem(1).getCount(), 1, "Secondary insertion places exactly one item");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertValueEqual(chest.getItem(0).getCount(), 3, "Primary insertion merges the remaining one item");
        helper.assertTrue(menu.getCarried().isEmpty(), "No extra cursor copy remains");
        helper.assertValueEqual(chest.countItem(Items.STONE), 4, "Both click paths conserve the original stack");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oversizedCursorPlacesOnlyAvailableSpaceThenSplitsIntoEmptySlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(2, player.getInventory(), chest);
        chest.setItem(0, new ItemStack(Items.STONE, 3));
        menu.setCarried(new ItemStack(Items.STONE, 10));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertValueEqual(chest.getItem(0).getCount(), 4, "A partial destination accepts only one source-limited item");
        helper.assertValueEqual(menu.getCarried().getCount(), 9, "The cursor keeps all nine unplaced items");
        menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertValueEqual(chest.getItem(1).getCount(), 4, "An empty destination accepts only four");
        helper.assertValueEqual(menu.getCarried().getCount(), 5, "The native cursor preserves the remaining five");
        helper.assertValueEqual(chest.countItem(Items.STONE) + menu.getCarried().getCount(), 13, "No count is truncated at either insertion");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftClickSplitsALegacySixtyFourStackAcrossInventorySlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer chest = legacyChest(new ItemStack(Items.STONE, 64));
        ChestMenu menu = ChestMenu.threeRows(3, player.getInventory(), chest);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(chest.getItem(0).isEmpty(), "The real Shift loop drains its legacy source stack");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 64, "Every one of the old sixty-four items reaches the player");
        helper.assertValueEqual(occupied(player.getInventory(), Items.STONE), 16, "Shift movement creates sixteen legal four-item stacks");
        assertBounded(helper, player.getInventory(), Items.STONE, 4);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void doubleClickCollectsOnlyUpToTheSourceMaximum(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(4, player.getInventory(), chest);
        chest.setItem(0, new ItemStack(Items.STONE, 1));
        chest.setItem(1, new ItemStack(Items.STONE, 2));
        chest.setItem(2, new ItemStack(Items.STONE, 4));
        menu.setCarried(new ItemStack(Items.STONE));
        menu.clicked(8, 0, ClickType.PICKUP_ALL, player);
        helper.assertValueEqual(menu.getCarried().getCount(), 4, "Native double-click collection respects the four-item source maximum");
        helper.assertValueEqual(chest.countItem(Items.STONE), 4, "The remainder stays in original slots");
        helper.assertValueEqual(chest.countItem(Items.STONE) + menu.getCarried().getCount(), 8, "Double-click does not duplicate the collected items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dragDistributionUsesNativeLeftAndRightModesWithoutOverflow(GameTestHelper helper) {
        for (int mode : new int[]{0, 1}) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            SimpleContainer chest = new SimpleContainer(27);
            ChestMenu menu = ChestMenu.threeRows(5, player.getInventory(), chest);
            menu.setCarried(new ItemStack(Items.STONE, 9));
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, mode), ClickType.QUICK_CRAFT, player);
            for (int slot = 0; slot < 3; slot++) menu.clicked(slot, AbstractContainerMenu.getQuickcraftMask(1, mode), ClickType.QUICK_CRAFT, player);
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, mode), ClickType.QUICK_CRAFT, player);
            for (int slot = 0; slot < 3; slot++) helper.assertValueEqual(chest.getItem(slot).getCount(), mode == 0 ? 3 : 1,
                    "Left drag distributes evenly; right drag deposits one per selected slot");
            helper.assertValueEqual(chest.countItem(Items.STONE) + menu.getCarried().getCount(), 9, "Drag retains all nine cursor items");
            assertBounded(helper, chest, Items.STONE, 4);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldOverfullInventoryStacksCannotReceiveAdditionalPickup(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().items.set(0, new ItemStack(Items.STONE, 64));
        ItemStack incoming = new ItemStack(Items.STONE, 2);
        player.getInventory().add(incoming);
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 64, "Legacy stacks are retained until explicitly moved");
        helper.assertValueEqual(player.getInventory().getItem(1).getCount(), 2, "Additional items use a separate legal stack");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 66, "Rejecting the legacy merge does not destroy the new pickup");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insertionIntoAnOldOverfullSlotCannotReverseTheTransfer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer chest = legacyChest(new ItemStack(Items.STONE, 64));
        ChestMenu menu = ChestMenu.threeRows(6, player.getInventory(), chest);
        menu.setCarried(new ItemStack(Items.STONE));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertValueEqual(chest.getItem(0).getCount(), 64, "Insertion cannot silently withdraw sixty items from an overfull legacy stack");
        helper.assertValueEqual(menu.getCarried().getCount(), 1, "The one-item cursor remains unchanged when there is no destination space");
        helper.assertValueEqual(chest.countItem(Items.STONE) + menu.getCarried().getCount(), 65, "Refused insertion retains the complete quantity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void negativeWithdrawalCapacityCannotGrowTheSourceSlot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer container = new SimpleContainer(new ItemStack(Items.STONE, 4));
        Slot slot = new Slot(container, 0, 0, 0);
        helper.assertTrue(slot.tryRemove(4, -60, player).isEmpty(), "An overfull cursor's negative free space cannot be a withdrawal quantity");
        helper.assertTrue(slot.tryRemove(-1, 4, player).isEmpty(), "A negative requested count cannot turn removal into item creation");
        helper.assertValueEqual(container.getItem(0).getCount(), 4, "Both invalid withdrawal requests leave all original items unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stackAndPlayerInventoryNbtRetainLegacyCountsAndComponents(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack original = new ItemStack(Items.STONE, 64);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 legacy"));
        ItemStack parsed = ItemStack.parse(helper.getLevel().registryAccess(), original.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(ItemStack.matches(original, parsed), "Normal item NBT preserves old overfull counts and components");
        player.getInventory().items.set(0, parsed);
        ListTag saved = player.getInventory().save(new ListTag());
        player.getInventory().clearContent();
        player.getInventory().load(saved);
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 64, "Loading a pre-change player inventory cannot truncate old material");
        helper.assertTrue(ItemStack.matches(original, player.getInventory().getItem(0)), "The legacy source slot is preserved on reconnect/restart");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shulkerAndBundleNbtRetainNestedLegacyStacksWithoutTruncation(GameTestHelper helper) {
        ItemStack old = new ItemStack(Items.STONE, 64);
        old.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 nested legacy"));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(old)));
        ItemStack loadedShulker = ItemStack.parse(helper.getLevel().registryAccess(), shulker.save(helper.getLevel().registryAccess())).orElseThrow();
        ItemStack retainedContainer = loadedShulker.get(DataComponents.CONTAINER).nonEmptyStream().findFirst().orElseThrow();
        helper.assertTrue(ItemStack.matches(old, retainedContainer), "Container data components preserve the count and name of nested legacy items");
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(old)));
        ItemStack loadedBundle = ItemStack.parse(helper.getLevel().registryAccess(), bundle.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(ItemStack.matches(old, loadedBundle.get(DataComponents.BUNDLE_CONTENTS).items().iterator().next()),
                "Bundle data components retain all old nested items even when their recalculated weight exceeds the new capacity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chestNbtRetainsOldStacksForLaterSafeManualSplitting(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
        CompoundTag saved = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
        CompoundTag stack = (CompoundTag) new ItemStack(Items.STONE, 64).save(helper.getLevel().registryAccess());
        stack.putByte("Slot", (byte) 0);
        ListTag items = new ListTag();
        items.add(stack);
        saved.put("Items", items);
        ChestBlockEntity loaded = (ChestBlockEntity) BlockEntity.loadStatic(pos, Blocks.CHEST.defaultBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded != null, "Native block-entity NBT resolves the actual chest type");
        helper.assertValueEqual(loaded.getItem(0).getCount(), 64, "Chest load bypasses insertion clamping and retains legacy items");
        ChestBlockEntity reloaded = (ChestBlockEntity) BlockEntity.loadStatic(pos, Blocks.CHEST.defaultBlockState(),
                loaded.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(reloaded != null && reloaded.getItem(0).getCount() == 64, "Repeated server saves preserve the same complete source quantity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hopperEmptySlotInsertionPreservesOversizedPickupRemainders(GameTestHelper helper) {
        SimpleContainer destination = new SimpleContainer(2);
        ItemStack remaining = HopperBlockEntity.addItem(null, destination, new ItemStack(Items.STONE, 64), Direction.DOWN);
        helper.assertValueEqual(destination.countItem(Items.STONE), 8, "The native empty-slot transfer stores only two legal four-item stacks");
        helper.assertValueEqual(remaining.getCount(), 56, "Hopper insertion returns all fifty-six unaccepted old items");
        helper.assertValueEqual(destination.countItem(Items.STONE) + remaining.getCount(), 64, "Container clamping cannot destroy a hopper pickup's overflow");
        assertBounded(helper, destination, Items.STONE, 4);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hopperMergesPartialSlotsAndRespectsContainerPlacementRestrictions(GameTestHelper helper) {
        SimpleContainer destination = new SimpleContainer(3) {
            @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot != 0; }
        };
        destination.setItem(1, new ItemStack(Items.STONE, 3));
        ItemStack remaining = HopperBlockEntity.addItem(null, destination, new ItemStack(Items.STONE, 10), Direction.DOWN);
        helper.assertTrue(destination.getItem(0).isEmpty(), "The overflow correction retains the destination's native placement rejection");
        helper.assertValueEqual(destination.countItem(Items.STONE), 8, "Native partial merge and one accepted empty slot reach the two-slot capacity");
        helper.assertValueEqual(remaining.getCount(), 5, "Unaccepted items remain available after a partial transfer");
        helper.assertValueEqual(destination.countItem(Items.STONE) + remaining.getCount(), 13, "Restricted hopper insertion conserves old and incoming amounts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hopperOccupiedSlotsRespectSmallerContainerCapacity(GameTestHelper helper) {
        SimpleContainer destination = new SimpleContainer(2) {
            @Override public int getMaxStackSize() { return 1; }
        };
        destination.setItem(0, new ItemStack(Items.STONE));
        ItemStack remaining = HopperBlockEntity.addItem(null, destination, new ItemStack(Items.STONE, 4), Direction.DOWN);
        helper.assertValueEqual(destination.getItem(0).getCount(), 1, "A full one-item container slot cannot use the item's larger four-item capacity");
        helper.assertValueEqual(destination.getItem(1).getCount(), 1, "The empty slot uses the same restricted container capacity");
        helper.assertValueEqual(remaining.getCount(), 3, "All three unaccepted stones remain with the native caller");
        helper.assertValueEqual(destination.countItem(Items.STONE) + remaining.getCount(), 5, "Smaller container limits cannot duplicate or discard items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groundPickupIntoARealHopperCannotEraseAnOldLargeItemEntity(GameTestHelper helper) {
        BlockPos local = new BlockPos(1, 1, 1);
        helper.setBlock(local, Blocks.HOPPER);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(local));
        ItemEntity entity = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.STONE, 64));
        helper.assertFalse(HopperBlockEntity.addItem(hopper, entity), "Native suction reports incomplete collection while retaining the remaining ground items");
        helper.assertValueEqual(hopper.countItem(Items.STONE), 20, "Five hopper slots each accept four stones");
        helper.assertTrue(entity.isAlive(), "An incompletely collected ground stack remains alive");
        helper.assertValueEqual(entity.getItem().getCount(), 44, "All forty-four uncollected ground items remain in their entity");
        helper.assertValueEqual(hopper.countItem(Items.STONE) + entity.getItem().getCount(), 64, "Actual ground suction loses no legacy item count");
        assertBounded(helper, hopper, Items.STONE, 4);
        entity.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void itemEntityMergeRespectsSourceCapacityAndComponentIdentity(GameTestHelper helper) {
        ItemStack target = new ItemStack(Items.STONE, 3);
        ItemStack donor = new ItemStack(Items.STONE, 3);
        ItemStack merged = ItemEntity.merge(target, donor, 64);
        helper.assertValueEqual(merged.getCount(), 4, "The native merge caps at the item's four-item maximum even with a generic sixty-four parameter");
        helper.assertValueEqual(donor.getCount(), 2, "The donor keeps its unmerged two items");
        helper.assertValueEqual(merged.getCount() + donor.getCount(), 6, "Partial entity merge conserves the combined quantity");
        ItemStack other = new ItemStack(Items.STONE);
        other.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 different"));
        helper.assertFalse(ItemEntity.areMergable(new ItemStack(Items.STONE), other), "Native entity merge still compares full data components");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realGroundEntitiesOnlyMergeWhenTheirEntireCombinedStackFits(GameTestHelper helper) {
        Vec3 center = helper.absolutePos(new BlockPos(2, 3, 2)).getCenter();
        for (int count : new int[]{3, 2}) {
            ItemEntity first = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, new ItemStack(Items.STONE, count));
            ItemEntity second = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, new ItemStack(Items.STONE, count));
            for (ItemEntity entity : List.of(first, second)) {
                entity.setNoGravity(true);
                entity.setDeltaMovement(Vec3.ZERO);
                helper.getLevel().addFreshEntity(entity);
            }
            try {
                for (int tick = 0; tick < 40; tick++) {
                    if (first.isAlive()) first.tick();
                    if (second.isAlive()) second.tick();
                }
                int alive = (first.isAlive() ? 1 : 0) + (second.isAlive() ? 1 : 0);
                int retained = (first.isAlive() ? first.getItem().getCount() : 0) + (second.isAlive() ? second.getItem().getCount() : 0);
                helper.assertValueEqual(alive, count == 3 ? 2 : 1,
                        "Actual neighbor ticks keep source three-plus-three separate and combine source two-plus-two completely");
                helper.assertValueEqual(retained, count * 2, "Whole-stack ground merge cannot create or destroy source items");
                if (count == 3) helper.assertTrue(first.getItem().getCount() == 3 && second.getItem().getCount() == 3,
                        "An oversized combined pair cannot be partially reshuffled into four-plus-two");
            } finally {
                first.discard();
                second.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void furnaceStopsAtFourIronIngotsAndResumesAfterOneWithdrawal(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 2, 2);
        BlockPos absolute = helper.absolutePos(local);
        helper.setBlock(local, Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(absolute);
        furnace.setItem(0, new ItemStack(Items.IRON_ORE));
        furnace.setItem(1, new ItemStack(Items.COAL));
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 4));
        for (int tick = 0; tick < 240; tick++) AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), absolute,
                helper.getLevel().getBlockState(absolute), furnace);
        helper.assertValueEqual(furnace.getItem(2).getCount(), 4, "Full source ingot output cannot grow beyond four");
        helper.assertValueEqual(furnace.getItem(0).getCount(), 1, "A full output retains the unprocessed ore");
        furnace.removeItem(2, 1);
        for (int tick = 0; tick < 200; tick++) AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), absolute,
                helper.getLevel().getBlockState(absolute), furnace);
        helper.assertTrue(furnace.getItem(0).isEmpty(), "One available output space permits exactly one source ore batch");
        helper.assertValueEqual(furnace.getItem(2).getCount(), 4, "Refilled output stops at the same source limit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nineIngotRecipeBatchSplitsIntoFourFourAndOneWithoutLosingTheNinth(GameTestHelper helper) {
        try (CraftFixture fixture = new CraftFixture(helper, GameType.SURVIVAL)) {
            fixture.loadIronBlock();
            TimedCraftingTestSupport.craftOne(helper, fixture.player, fixture.menu, ClickType.PICKUP);
            helper.assertTrue(fixture.grid.isEmpty(), "The one completed source recipe consumes exactly one iron block");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.IRON_INGOT), 9, "The nine-item batch is retained before inventory splitting");
            helper.assertValueEqual(occupied(fixture.player.getInventory(), Items.IRON_INGOT), 3, "The output occupies four-plus-four-plus-one inventory slots");
            assertBounded(helper, fixture.player.getInventory(), Items.IRON_INGOT, 4);
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void fullSurvivalAndCreativeInventoriesDropEveryIngotAsLegalStacks(GameTestHelper helper) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) try (CraftFixture fixture = new CraftFixture(helper, mode)) {
            fill(fixture.player, Items.IRON_SWORD);
            fixture.loadIronBlock();
            TimedCraftingTestSupport.craftOne(helper, fixture.player, fixture.menu, ClickType.PICKUP);
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, fixture.player.getBoundingBox().inflate(3),
                    entity -> entity.getItem().is(Items.IRON_INGOT));
            helper.assertValueEqual(drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(), 9,
                    "Full inventory output retains every paid/crafted ingot in both game modes");
            helper.assertValueEqual(drops.size(), 3, "The ground receives two four-item and one one-item stack");
            for (ItemEntity entity : drops) {
                helper.assertTrue(entity.getItem().getCount() <= 4, "Manufacturing never drops an overfull result batch");
                entity.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1150)
    public static void realCrafterPlacesANineIngotBatchAcrossSourceLimitedChestSlots(GameTestHelper helper) {
        BlockPos local = new BlockPos(1, 1, 1);
        helper.setBlock(local, Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.EAST_UP));
        helper.setBlock(local.east(), Blocks.CHEST);
        CrafterBlockEntity crafter = (CrafterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(local));
        Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(local.east()));
        crafter.setItem(0, new ItemStack(Items.IRON_BLOCK));
        helper.pulseRedstone(local.west(), 5);
        helper.runAfterDelay(1120, () -> {
            helper.assertTrue(crafter.isEmpty(), "One actual redstone manufacturing request consumes one iron block after its full source period");
            helper.assertValueEqual(chest.countItem(Items.IRON_INGOT), 9, "Native crafter output conserves all nine ingots");
            helper.assertValueEqual(occupied(chest, Items.IRON_INGOT), 3, "Automatic output inserts the batch into four-plus-four-plus-one chest slots");
            assertBounded(helper, chest, Items.IRON_INGOT, 4);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void capabilityHopperRefusalPreservesEveryLegacyItemAcrossRepeatedRollbacks(GameTestHelper helper) {
        BlockPos local = new BlockPos(1, 1, 1);
        helper.setBlock(local, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(local.east(), Blocks.CHEST);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(local));
        Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(local.east()));
        loadLegacy(helper, hopper, new ItemStack(Items.STONE, 64));
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.STONE, 4));
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(local.east()), Direction.WEST) != null, "The target exposes the real NeoForge chest item-handler path");
        for (int attempt = 0; attempt < 3; attempt++) {
            helper.assertFalse(VanillaInventoryCodeHooks.insertHook(hopper), "A source-limited full chest refuses the real capability insertion");
            helper.assertValueEqual(hopper.getItem(0).getCount(), 64, "Each native capability rollback retains all sixty-four legacy source items");
            helper.assertValueEqual(chest.countItem(Items.STONE), 108, "Failed insertion cannot alter any full destination slot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capabilityHopperSuccessAndLaterRefusalConserveItsOldSourceStack(GameTestHelper helper) {
        BlockPos local = new BlockPos(1, 1, 1);
        helper.setBlock(local, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(local.east(), Blocks.CHEST);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(local));
        Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(local.east()));
        loadLegacy(helper, hopper, new ItemStack(Items.STONE, 64));
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.STONE, slot == 0 ? 3 : 4));
        helper.assertTrue(VanillaInventoryCodeHooks.insertHook(hopper), "The real capability moves one item into the available destination space");
        helper.assertValueEqual(hopper.getItem(0).getCount(), 63, "Successful extraction leaves the complete sixty-three-item legacy remainder");
        helper.assertValueEqual(chest.countItem(Items.STONE), 108, "Exactly one stone reaches the target chest");
        helper.assertFalse(VanillaInventoryCodeHooks.insertHook(hopper), "The now-full destination rejects the next native capability attempt");
        helper.assertValueEqual(hopper.getItem(0).getCount(), 63, "A subsequent rejected extraction restores the previous remainder without another debit");
        helper.assertValueEqual(chest.countItem(Items.STONE) + hopper.countItem(Items.STONE), 171, "Success followed by failure preserves the original combined quantity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capabilityDropperFailureAndSuccessPreserveItsSavedOversizedRemainder(GameTestHelper helper) {
        BlockPos local = new BlockPos(1, 1, 1);
        helper.setBlock(local, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.setBlock(local.east(), Blocks.CHEST);
        DispenserBlockEntity dropper = (DispenserBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(local));
        Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(local.east()));
        loadLegacy(helper, dropper, new ItemStack(Items.STONE, 64));
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.STONE, 4));
        helper.assertFalse(VanillaInventoryCodeHooks.dropperInsertHook(helper.getLevel(), helper.absolutePos(local), dropper, 0, dropper.getItem(0)),
                "NeoForge handles the dropper dispatch itself when a real item-handler target exists");
        helper.assertValueEqual(dropper.getItem(0).getCount(), 64, "A rejected capability dispatch cannot clamp the restored old source stack");
        chest.setItem(0, ItemStack.EMPTY);
        helper.assertFalse(VanillaInventoryCodeHooks.dropperInsertHook(helper.getLevel(), helper.absolutePos(local), dropper, 0, dropper.getItem(0)),
                "The successful capability dispatch also prevents a duplicate native fallback dispatch");
        helper.assertValueEqual(dropper.getItem(0).getCount(), 63, "Success preserves every item of the old sixty-three-item source remainder");
        helper.assertValueEqual(chest.getItem(0).getCount(), 1, "Exactly the dispatched one item reaches the previously empty target slot");
        helper.assertValueEqual(dropper.countItem(Items.STONE) + chest.countItem(Items.STONE), 168, "The failed and successful capability operations conserve all original items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeDispenserAndDropperDispatchRetainSixtyThreeSavedSourceItems(GameTestHelper helper) {
        BlockPos local = new BlockPos(1, 2, 1);
        BlockPos absolute = helper.absolutePos(local);
        for (Block machine : List.of(Blocks.DISPENSER, Blocks.DROPPER)) {
            helper.setBlock(local, machine.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
            helper.setBlock(local.east(), Blocks.AIR);
            DispenserBlockEntity container = (DispenserBlockEntity) helper.getLevel().getBlockEntity(absolute);
            ItemStack source = new ItemStack(Items.STONE, 64);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 saved dispenser source"));
            loadLegacy(helper, container, source);
            try (var capture = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity item
                    && item.getItem().is(Items.STONE)
                    && new net.minecraft.world.phys.AABB(absolute).inflate(2).contains(entity.position()))) {
                helper.getLevel().getBlockState(absolute).tick(helper.getLevel(), absolute, helper.getLevel().random);
                helper.assertValueEqual(container.getItem(0).getCount(), 63, "One actual native scheduled block dispatch retains every remaining legacy source item");
                helper.assertTrue(ItemStack.isSameItemSameComponents(source, container.getItem(0)), "Dispatch preserves the old source's complete data-component identity");
                List<ItemEntity> emitted = capture.live(ItemEntity.class);
                helper.assertValueEqual(emitted.stream().mapToInt(entity -> entity.getItem().getCount()).sum(), 1,
                        "Exactly one legal item is emitted by the dispenser or native dropper fallback");
                DispenserBlockEntity reloaded = (DispenserBlockEntity) BlockEntity.loadStatic(absolute, helper.getLevel().getBlockState(absolute),
                        container.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                helper.assertTrue(reloaded != null && reloaded.getItem(0).getCount() == 63, "Saving after native dispatch still preserves the complete old remainder");
                emitted.forEach(ItemEntity::discard);
                container.clearContent();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyPreservationCannotCreateNewOversizedStacksOrRestoreUnrelatedComponents(GameTestHelper helper) {
        SimpleContainer container = new SimpleContainer(new ItemStack(Items.STONE, 4));
        ItemStack claimedOld = new ItemStack(Items.STONE, 64);
        helper.assertFalse(LegacyStackTransfers.preserve(container, 0, claimedOld.copy(), claimedOld),
                "A legal current source slot cannot be promoted into a newly invented old sixty-four-item stack");
        helper.assertValueEqual(container.getItem(0).getCount(), 4, "Refused promotion leaves the legal current stack unchanged");
        ItemStack old = new ItemStack(Items.STONE, 64);
        old.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 original"));
        SimpleContainer legacy = new SimpleContainer(old.copyWithCount(63));
        ItemStack different = old.copy();
        different.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 replacement"));
        helper.assertFalse(LegacyStackTransfers.preserve(legacy, 0, different, old), "A different data-component identity cannot inherit old source permission");
        helper.assertFalse(LegacyStackTransfers.preserve(legacy, 0, old.copyWithCount(65), old), "Legacy preservation cannot increase beyond the captured pre-transfer count");
        legacy.getItem(0).setCount(61);
        helper.assertFalse(LegacyStackTransfers.preserve(legacy, 0, old.copy(), old), "The rollback guard cannot restore an unexplained multi-item source change");
        helper.assertValueEqual(legacy.getItem(0).getCount(), 61, "All refused restore operations leave the actual remaining source untouched");
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void fullInventoriesKeepEveryDisplacedItemWhenSwappingALegacyHotbarStack(GameTestHelper helper) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) for (int sourceCount : new int[]{5, 64})
                for (int displacedCount : new int[]{4, 64}) try (CraftFixture fixture = new CraftFixture(helper, mode)) {
            fill(fixture.player, Items.IRON_SWORD);
            fixture.player.getInventory().items.set(0, new ItemStack(Items.STONE, sourceCount));
            SimpleContainer chest = legacyChest(new ItemStack(Items.DIRT, displacedCount));
            ChestMenu menu = ChestMenu.threeRows(90, fixture.player.getInventory(), chest);
            fixture.player.containerMenu = menu;
            menu.clicked(0, 0, ClickType.SWAP, fixture.player);
            helper.assertTrue(chest.getItem(0).is(Items.STONE) && chest.getItem(0).getCount() == 4,
                    "The ordinary container's native hotbar swap accepts exactly one legal source-sized stack");
            helper.assertValueEqual(fixture.player.getInventory().getItem(0).getCount(), sourceCount - 4,
                    "The hotbar retains the complete untransferred old source remainder, including a newly legal one-item remainder");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.STONE) + chest.countItem(Items.STONE), sourceCount,
                    "Legacy hotbar swapping conserves every original stone");
            List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, fixture.player.getBoundingBox().inflate(3),
                    entity -> entity.getItem().is(Items.DIRT));
            helper.assertValueEqual(dropped.stream().mapToInt(entity -> entity.getItem().getCount()).sum(), displacedCount,
                    "Neither survival nor creative overflow can destroy the complete displaced container stack");
            for (ItemEntity entity : dropped) {
                helper.assertTrue(entity.getItem().getCount() <= 4, "Even a displaced old stack is split into legal new ground stacks");
                entity.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryLegalHotbarSwapKeepsItsNativeDirectExchange(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        fill(player, Items.IRON_SWORD);
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 4));
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.DIRT, 4));
        ChestMenu menu = ChestMenu.threeRows(91, player.getInventory(), chest);
        menu.clicked(0, 0, ClickType.SWAP, player);
        helper.assertTrue(chest.getItem(0).is(Items.STONE) && chest.getItem(0).getCount() == 4, "A legal hotbar stack moves directly to the native container slot");
        helper.assertTrue(player.getInventory().getItem(0).is(Items.DIRT) && player.getInventory().getItem(0).getCount() == 4,
                "The unrelated container stack directly replaces the legal hotbar stack without entering overflow handling");
        helper.assertValueEqual(player.getInventory().countItem(Items.IRON_SWORD), 35, "A normal direct swap does not alter any other inventory slot");
        helper.succeed();
    }

    private static void loadLegacy(GameTestHelper helper, BlockEntity entity, ItemStack stack) {
        CompoundTag saved = entity.saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag entry = (CompoundTag) stack.save(helper.getLevel().registryAccess());
        entry.putByte("Slot", (byte) 0);
        ListTag items = new ListTag();
        items.add(entry);
        saved.put("Items", items);
        entity.loadWithComponents(saved, helper.getLevel().registryAccess());
    }

    private static SimpleContainer legacyChest(ItemStack first) {
        ItemStack[] stacks = new ItemStack[27];
        Arrays.fill(stacks, ItemStack.EMPTY);
        stacks[0] = first;
        return new SimpleContainer(stacks);
    }

    private static void fill(Player player, Item item) {
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().setItem(slot, new ItemStack(item));
    }

    private static int occupied(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(item)) count++;
        return count;
    }

    private static void assertBounded(GameTestHelper helper, Container container, Item item, int sourceLimit) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                helper.assertValueEqual(stack.getMaxStackSize(), sourceLimit, "Actual item instances expose the independent R196 stack limit");
                helper.assertTrue(stack.getCount() <= sourceLimit, "Every new destination stack fits its source limit");
            }
        }
    }

    private static final class CraftFixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final AbstractContainerMenu menu;
        final CraftingContainer grid;

        CraftFixture(GameTestHelper helper, GameType mode) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-stacking"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(mode);
            player.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            menu = player.inventoryMenu;
            player.containerMenu = menu;
            grid = (CraftingContainer) menu.getSlot(1).container;
        }

        void loadIronBlock() {
            menu.getSlot(1).set(new ItemStack(Items.IRON_BLOCK));
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), helper.getLevel()).isPresent(),
                    "The real recipe manager recognizes the source iron-block unpacking recipe");
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_INGOT) && menu.getSlot(0).getItem().getCount() == 9,
                    "The result slot keeps the full nine-item manufacturing batch before it is delivered");
        }

        @Override public void close() {
            try {
                TimedCraftingController.cancel(player);
                grid.clearContent();
                player.getInventory().clearContent();
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}
