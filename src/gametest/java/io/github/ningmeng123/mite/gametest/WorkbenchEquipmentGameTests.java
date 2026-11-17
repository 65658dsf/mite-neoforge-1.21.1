package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual placed equipment, result synchronization and legacy-table rejection. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WorkbenchEquipmentGameTests {
    @GameTest(template = "empty")
    public static void declaredToolTierBlocksBothProgressAndFinalRemoval(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        try (Session session = player(helper)) {
            var player = session.player();
            var ore = Blocks.DIAMOND_ORE.defaultBlockState();
            helper.getLevel().setBlockAndUpdate(pos, ore);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("copper_pickaxe")));
            helper.assertValueEqual(io.github.ningmeng123.mite.mining.MiningProgress.destroyProgress(player, ore, helper.getLevel(), pos), 0F,
                    "A copper pickaxe cannot accumulate diamond-ore mining progress");
            helper.assertTrue(!player.gameMode.destroyBlock(pos) && helper.getLevel().getBlockState(pos).is(Blocks.DIAMOND_ORE),
                    "A direct final removal request still rejects the insufficient tool tier");
            helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 0, "Rejected harvest cannot consume durability");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mithril_pickaxe")));
            helper.assertTrue(player.gameMode.destroyBlock(pos), "The source-declared mithril tier may harvest diamond ore");
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void allTenTablesOpenValidMenusAndHaveExpectedCapacity(GameTestHelper helper) {
        String[] materials = {"flint", "obsidian", "copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"};
        float[] capacities = {1, 2, 4, 4, 4, 8, 16, 64, 256, 512};
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        try (Session session = player(helper)) {
            var player = session.player();
            for (int i = 0; i < materials.length; i++) {
                var state = MiteWorkbenches.TABLES.get(materials[i]).get().defaultBlockState();
                helper.getLevel().setBlockAndUpdate(pos, state);
                helper.assertValueEqual(WorkbenchRules.capacity(state), capacities[i], materials[i] + " capacity");
                var menu = state.getMenuProvider(helper.getLevel(), pos).createMenu(10, player.getInventory(), player);
                player.containerMenu = menu;
                helper.assertTrue(menu instanceof CraftingMenu && menu.stillValid(player), materials[i] + " opens modern crafting menu");
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                helper.assertTrue(!menu.stillValid(player), "Removing the real table invalidates its menu");
                player.containerMenu = player.inventoryMenu;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void metalRecipeUsesCurrentPhysicalTierAndRejectsStaleQuickMove(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        try (Session session = player(helper)) {
            var player = session.player();
            helper.getLevel().setBlockAndUpdate(pos, MiteWorkbenches.TABLES.get("flint").get().defaultBlockState());
            CraftingMenu menu = new CraftingMenu(20, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
            player.containerMenu = menu;
            menu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT));
            menu.getSlot(2).set(new ItemStack(Items.COPPER_INGOT));
            menu.getSlot(4).set(new ItemStack(Items.COPPER_INGOT));
            menu.getSlot(5).set(new ItemStack(Items.STICK));
            menu.getSlot(8).set(new ItemStack(Items.STICK));
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Flint table must not publish copper axe result");
            helper.getLevel().setBlockAndUpdate(pos, MiteWorkbenches.TABLES.get("copper").get().defaultBlockState());
            menu.slotsChanged(menu.getSlot(1).container);
            helper.assertTrue(menu.getSlot(0).getItem().is(MiteTools.get("copper_axe")), "Copper table publishes its axe recipe");
            helper.getLevel().setBlockAndUpdate(pos, MiteWorkbenches.TABLES.get("flint").get().defaultBlockState());
            helper.assertTrue(!menu.getSlot(0).mayPickup(player), "Stale result cannot be picked up after downgrade");
            helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "Direct Shift caller rechecks physical tier");
            helper.assertTrue(!TimedCraftingController.start(player, menu), "Timed manufacturing cannot start after downgrade");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "Blocked transfer preserves input");
            menu.clearCraftingContent();
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstMetalBenchRequiresFlintEquipmentAndHigherTablesRequirePreviousStage(GameTestHelper helper) {
        try (Session session = player(helper)) {
            var player = session.player();
            var copper = new ItemStack(MiteWorkbenches.TABLES.get("copper").get());
            var iron = new ItemStack(MiteWorkbenches.TABLES.get("iron").get());
            var mithril = new ItemStack(MiteWorkbenches.TABLES.get("mithril").get());
            helper.assertTrue(!WorkbenchRules.allows(player.inventoryMenu, copper), "2x2 input shape does not grant metal-bench equipment");
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            CraftingMenu menu = new CraftingMenu(30, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
            helper.getLevel().setBlockAndUpdate(pos, MiteWorkbenches.TABLES.get("flint").get().defaultBlockState());
            helper.assertTrue(WorkbenchRules.allows(menu, copper) && !WorkbenchRules.allows(menu, iron), "First metal stage is attainable but iron is gated");
            helper.assertTrue(!WorkbenchRules.allows(menu, new ItemStack(Items.IRON_CHESTPLATE)), "Retained iron armor cannot bypass metal equipment requirements");
            helper.getLevel().setBlockAndUpdate(pos, MiteWorkbenches.TABLES.get("gold").get().defaultBlockState());
            helper.assertTrue(WorkbenchRules.allows(menu, iron) && !WorkbenchRules.allows(menu, mithril), "Equal copper/silver/gold stage can process iron");
            helper.getLevel().setBlockAndUpdate(pos, MiteWorkbenches.TABLES.get("ancient_metal").get().defaultBlockState());
            helper.assertTrue(WorkbenchRules.allows(menu, mithril), "Ancient metal stage enables mithril table");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaTableCannotOpenOrBeCraftedAndOldBlockDropsFlintTable(GameTestHelper helper) {
        try (Session session = player(helper)) {
            var player = session.player();
            var input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                    new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS)));
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isEmpty(),
                    "Four planks cannot make the disabled table");
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            var state = Blocks.CRAFTING_TABLE.defaultBlockState();
            helper.getLevel().setBlockAndUpdate(pos, state);
            player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Old table interaction does not open a menu");
            var drops = net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(), pos, null, player, ItemStack.EMPTY);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(MiteWorkbenches.TABLES.get("flint").get().asItem()),
                    "Existing old-ID tables migrate safely by breaking them");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void retiredCrafterCannotBeEnabledByAnyAdjacentMaterialWorkbench(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var result = new ItemStack(MiteTools.get("minecraft:iron_axe"));
        for (var entry : MiteWorkbenches.TABLES.entrySet()) {
            helper.getLevel().setBlockAndUpdate(pos.east(), entry.getValue().get().defaultBlockState());
            helper.assertFalse(WorkbenchRules.allowsCrafter(helper.getLevel(), pos, result),
                    "A retired crafter remains disabled beside the " + entry.getKey() + " workbench");
        }
        helper.getLevel().setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    private static Session player(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-bench-tier"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
        player.getFoodData().setFoodLevel(6);
        return new Session(player, channel);
    }
    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            TimedCraftingController.cancel(player);
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
