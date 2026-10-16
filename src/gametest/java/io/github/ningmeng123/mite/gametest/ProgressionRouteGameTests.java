package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.advancement.MiteAchievements;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native crafting and End portal transactions; not natural acquisition or a complete dragon fight. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ProgressionRouteGameTests {
    private ProgressionRouteGameTests() {}

    @GameTest(template = "empty")
    public static void actualInventoryCraftingConsumesRodAndPearlToProduceAnEye(GameTestHelper h) {
        try (var session = new Session(h)) {
            ServerPlayer player = session.player();
            var menu = player.inventoryMenu;
            var grid = menu.getCraftSlots();
            h.assertValueEqual(grid.getWidth(), 2, "The actual player menu provides only two crafting columns");
            h.assertValueEqual(grid.getHeight(), 2, "The actual player menu provides only two crafting rows");
            int powderCrafts = player.getStats().getValue(Stats.ITEM_CRAFTED.get(Items.BLAZE_POWDER));
            int eyeCrafts = player.getStats().getValue(Stats.ITEM_CRAFTED.get(Items.ENDER_EYE));

            grid.setItem(0, new ItemStack(Items.BLAZE_ROD));
            h.assertTrue(menu.getSlot(0).getItem().is(Items.BLAZE_POWDER), "The native recipe resolves in the real 2x2 menu");
            h.assertFalse(TimedCraftingController.isTimed(player, menu), "The currently retained vanilla powder recipe uses the ordinary pickup path");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            h.assertTrue(menu.getCarried().is(Items.BLAZE_POWDER) && menu.getCarried().getCount() == 2,
                    "Actual result-slot pickup produces two powder");
            h.assertTrue(grid.isEmpty(), "Actual native crafting consumed the blaze rod");
            h.assertValueEqual(player.getStats().getValue(Stats.ITEM_CRAFTED.get(Items.BLAZE_POWDER)), powderCrafts + 2,
                    "Native crafting statistics count the produced powder");

            menu.clicked(9, 0, ClickType.PICKUP, player); // Store both powder in the native main inventory slot.
            menu.clicked(9, 1, ClickType.PICKUP, player); // Pick up one of the two.
            menu.clicked(1, 0, ClickType.PICKUP, player); // Put it into the first crafting slot.
            menu.getSlot(10).set(new ItemStack(Items.ENDER_PEARL));
            menu.clicked(10, 0, ClickType.PICKUP, player);
            menu.clicked(2, 0, ClickType.PICKUP, player);
            h.assertTrue(menu.getCarried().isEmpty(), "Both ingredients are placed through native cursor transactions");
            h.assertTrue(menu.getSlot(0).getItem().is(Items.ENDER_EYE), "Powder and pearl resolve to the actual eye recipe");
            h.assertTrue(WorkbenchRules.allows(menu, menu.getSlot(0).getItem()), "A real inventory menu satisfies the eye's current equipment requirements");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            h.assertTrue(menu.getCarried().is(Items.ENDER_EYE) && menu.getCarried().getCount() == 1,
                    "Native result-slot pickup completes one eye without a workbench");
            h.assertTrue(grid.isEmpty(), "Actual crafting consumes both the pearl and one powder");
            h.assertTrue(menu.getSlot(9).getItem().is(Items.BLAZE_POWDER) && menu.getSlot(9).getItem().getCount() == 1,
                    "Exactly one unconsumed powder remains from the original rod");
            h.assertValueEqual(player.getStats().getValue(Stats.ITEM_CRAFTED.get(Items.ENDER_EYE)), eyeCrafts + 1,
                    "The native eye crafting statistic increments once");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void finalEyeUseActivatesTheActualTwelveFramePortal(GameTestHelper h) {
        BlockPos center = highCenter(h);
        try (var area = new BlockPatch(h.getLevel(), center.offset(-3, -1, -3), center.offset(3, 3, 3));
             var session = new Session(h)) {
            ServerLevel level = h.getLevel();
            area.clear();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, -1, 3)))
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            for (int offset = -1; offset <= 1; offset++) {
                frame(level, center.offset(offset, 0, -2), Direction.SOUTH, true);
                frame(level, center.offset(offset, 0, 2), Direction.NORTH, true);
                frame(level, center.offset(-2, 0, offset), Direction.EAST, true);
                frame(level, center.offset(2, 0, offset), Direction.WEST, true);
            }
            BlockPos last = center.north(2);
            frame(level, last, Direction.SOUTH, false);
            h.assertTrue(EndPortalFrameBlock.getOrCreatePortalShape().find(level, last) == null,
                    "Eleven filled frames do not yet form an active native frame pattern");
            ServerPlayer player = session.player();
            player.moveTo(last.getX() + .5D, last.getY(), last.getZ() - 1D);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE, 2));
            int uses = player.getStats().getValue(Stats.ITEM_USED.get(Items.ENDER_EYE));
            var hit = new BlockHitResult(Vec3.atCenterOf(last).add(0D, .5D, 0D), Direction.UP, last, false);
            var result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            h.assertTrue(result.consumesAction(), "The native server item-use transaction accepts the final EnderEyeItem use");
            h.assertTrue(level.getBlockState(last).getValue(EndPortalFrameBlock.HAS_EYE), "The real frame receives its eye");
            h.assertValueEqual(player.getMainHandItem().getCount(), 1, "Activation consumes exactly one eye in survival mode");
            h.assertValueEqual(player.getStats().getValue(Stats.ITEM_USED.get(Items.ENDER_EYE)), uses + 1,
                    "The native use event records exactly one eye use");
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
                h.assertTrue(level.getBlockState(pos).is(Blocks.END_PORTAL), "All nine actual portal blocks are created: " + pos);
                h.assertTrue(level.getBlockEntity(pos) != null, "Each portal block has its native block entity");
            }
            h.assertTrue(EndPortalFrameBlock.getOrCreatePortalShape().find(level, last) != null,
                    "The completed frame pattern is valid with the real inward facings");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void nativeEndPortalCollisionAndExitPreservePlayerAndAwardRouteProgress(GameTestHelper h) throws ReflectiveOperationException {
        ServerLevel overworld = h.getLevel(), end = overworld.getServer().getLevel(Level.END);
        h.assertTrue(end != null, "The standard End dimension is available");
        BlockPos entrance = highCenter(h), home = entrance.above(2);
        BlockPos platform = ServerLevel.END_SPAWN_POINT.below(), exit = ServerLevel.END_SPAWN_POINT.above(4);
        // Only the normal 5x5 entrance platform and one nearby test exit are touched in the End.
        // No world tick, EndDragonFight tick, dragon death, or central arena generation is requested.
        try (var sourceArea = new BlockPatch(overworld, entrance.offset(-1, -1, -1), entrance.offset(1, 4, 1));
             var destinationArea = new BlockPatch(end, platform.offset(-2, -1, -2), exit.offset(2, 2, 2));
             var session = new Session(h)) {
            sourceArea.clear(); destinationArea.clear();
            ServerPlayer player = session.player();
            UUID uuid = player.getUUID();
            player.getInventory().setItem(8, new ItemStack(Items.COBBLESTONE, 7));
            player.setRespawnPosition(Level.OVERWORLD, home, 0F, true, false);
            overworld.setBlock(entrance, Blocks.END_PORTAL.defaultBlockState(), 2);
            player.moveTo(entrance.getX() + .5D, entrance.getY() + .375D, entrance.getZ() + .5D);
            overworld.getBlockState(entrance).entityInside(overworld, entrance, player);
            h.assertTrue(player.portalProcess != null && player.portalProcess.isSamePortal((net.minecraft.world.level.block.Portal) Blocks.END_PORTAL),
                    "Actual native collision starts the End portal processor");
            Method handlePortal = Entity.class.getDeclaredMethod("handlePortal");
            handlePortal.setAccessible(true);
            handlePortal.invoke(player);
            h.assertTrue(player.serverLevel() == end, "Native portal processing really transfers the ServerPlayer to the End");
            h.assertTrue(session.player() == player && player.getUUID().equals(uuid), "Entering the End preserves the actual player identity");
            h.assertTrue(end.getBlockState(platform.below()).is(Blocks.OBSIDIAN), "The native End transition creates its entrance platform");
            h.assertTrue(earned(player, "theEnd"), "Actual dimension-change events award the R196 Enter the End advancement");

            // Embedded connections have no client teleport acknowledgement. Complete that native
            // acknowledgement explicitly, then let native portal handling expire its own cooldown.
            player.hasChangedDimension();
            for (int tick = 0; tick < player.getDimensionChangingDelay(); tick++) handlePortal.invoke(player);
            h.assertFalse(player.isOnPortalCooldown(), "The native player cooldown expires before testing the exit collision");
            player.seenCredits = true; // Exercise the ordinary exit route rather than opening a credits screen.
            end.setBlock(exit, Blocks.END_PORTAL.defaultBlockState(), 2);
            player.moveTo(exit.getX() + .5D, exit.getY() + .375D, exit.getZ() + .5D);
            end.getBlockState(exit).entityInside(end, exit, player);
            handlePortal.invoke(player);
            h.assertTrue(player.serverLevel() == overworld, "The real End exit portal returns to the Overworld");
            h.assertTrue(session.player() == player && player.getUUID().equals(uuid), "Returning preserves the same player and network listener");
            h.assertTrue(player.position().distanceToSqr(Vec3.atBottomCenterOf(home)) < 1D,
                    "The native exit resolves the player's forced respawn point instead of the End entrance");
            h.assertTrue(player.getInventory().getItem(8).is(Items.COBBLESTONE) && player.getInventory().getItem(8).getCount() == 7,
                    "The inventory marker survives both actual native dimension transitions");
            h.assertTrue(earned(player, "theEnd2"), "Actual return events award the R196 End-return advancement");
        }
        h.succeed();
    }

    private static boolean earned(ServerPlayer player, String name) {
        var holder = player.server.getAdvancements().get(MiteAchievements.catalog().get(name).id());
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static BlockPos highCenter(GameTestHelper h) { return h.absolutePos(new BlockPos(2, 128, 2)); }

    private static void frame(ServerLevel level, BlockPos pos, Direction inward, boolean eye) {
        level.setBlock(pos, Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, inward)
                .setValue(EndPortalFrameBlock.HAS_EYE, eye), 2);
    }

    private record Previous(BlockState state, CompoundTag blockEntity) {}

    /** Restores source blocks, portal block entities, and any pre-existing platform blocks. */
    private static final class BlockPatch implements AutoCloseable {
        private final ServerLevel level;
        private final Map<BlockPos, Previous> previous = new LinkedHashMap<>();
        BlockPatch(ServerLevel level, BlockPos from, BlockPos to) {
            this.level = level;
            for (BlockPos mutable : BlockPos.betweenClosed(from, to)) {
                BlockPos pos = mutable.immutable();
                var blockEntity = level.getBlockEntity(pos);
                previous.put(pos, new Previous(level.getBlockState(pos),
                        blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess())));
            }
        }
        void clear() { previous.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)); }
        @Override public void close() {
            previous.forEach((pos, saved) -> level.setBlock(pos, saved.state(), 2));
            previous.forEach((pos, saved) -> {
                var blockEntity = level.getBlockEntity(pos);
                if (saved.blockEntity() != null && blockEntity != null) blockEntity.loadWithComponents(saved.blockEntity(), level.registryAccess());
            });
        }
    }

    private static final class Session implements AutoCloseable {
        private final GameProfile profile = new GameProfile(UUID.randomUUID(), "route-" + UUID.randomUUID().toString().substring(0, 8));
        private final EmbeddedChannel channel;
        private final ServerGamePacketListenerImpl listener;
        Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(profile, false);
            ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            listener = player.connection;
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.getInventory().clearContent();
            BlockPos pos = highCenter(h);
            player.moveTo(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
        }
        ServerPlayer player() { return listener.player; }
        @Override public void close() {
            ServerPlayer current = player().server.getPlayerList().getPlayer(profile.getId());
            if (current != null) {
                current.inventoryMenu.setCarried(ItemStack.EMPTY);
                current.inventoryMenu.getCraftSlots().clearContent();
                current.getInventory().clearContent();
                current.server.getPlayerList().remove(current);
            }
            channel.finishAndReleaseAll();
        }
    }
}
