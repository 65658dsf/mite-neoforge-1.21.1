package io.github.ningmeng123.mite.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ningmeng123.mite.command.ClearBlocksCommand;
import io.github.ningmeng123.mite.registry.MiteOreBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real command parsing and world edits, isolated from the ordinary test grid. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ClearBlocksCommandGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    private ClearBlocksCommandGameTests() {}

    @GameTest(template = "empty")
    public static void evenDimensionsClearExactlyTheSpecifiedCubeAndOnlyTheSelectedBlock(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            BlockPos min = f.center.offset(-5, -5, -5);
            BlockPos max = f.center.offset(4, 4, 4);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) f.set(pos, Blocks.STONE.defaultBlockState());
            f.set(f.center, Blocks.GRANITE.defaultBlockState());
            BlockPos[] outside = {f.center.offset(-6, 0, 0), f.center.offset(5, 0, 0),
                    f.center.offset(0, -6, 0), f.center.offset(0, 5, 0),
                    f.center.offset(0, 0, -6), f.center.offset(0, 0, 5)};
            for (BlockPos pos : outside) f.set(pos, Blocks.STONE.defaultBlockState());
            f.execute("mite clearblocks 10 10 10 stone");
            f.finish();
            for (BlockPos pos : BlockPos.betweenClosed(min, max))
                helper.assertTrue(pos.equals(f.center) ? f.level.getBlockState(pos).is(Blocks.GRANITE)
                        : f.level.getBlockState(pos).isAir(), "Exactly the 10 x 10 x 10 volume is filtered");
            for (BlockPos pos : outside) helper.assertTrue(f.level.getBlockState(pos).is(Blocks.STONE),
                    "Each immediate outer face remains untouched");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oddNonCubicDimensionsMapLengthWidthHeightToXZYAtNegativePlayerCoordinates(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            BlockPos min = f.center.offset(-1, 0, -2);
            BlockPos max = f.center.offset(1, 0, 2);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) f.set(pos, Blocks.STONE.defaultBlockState());
            BlockPos[] outside = {f.center.west(2), f.center.east(2), f.center.above(), f.center.below(),
                    f.center.north(3), f.center.south(3)};
            for (BlockPos pos : outside) f.set(pos, Blocks.STONE.defaultBlockState());
            f.player.setPos(f.center.getX() + 0.8, f.center.getY() + 0.8, f.center.getZ() + 0.8);
            f.execute("mite clearblocks 3 5 1 minecraft:stone");
            f.finish();
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) helper.assertTrue(f.level.getBlockState(pos).isAir(),
                    "Negative fractional coordinates floor correctly and the odd dimensions remain symmetric");
            for (BlockPos pos : outside) helper.assertTrue(f.level.getBlockState(pos).is(Blocks.STONE),
                    "Length is X, width is Z, and height is Y");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitBlockPropertiesFilterStatesAndAnOmittedPropertyMatchesEveryState(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            f.set(f.center.west(), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            f.set(f.center, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            f.set(f.center.east(), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            f.execute("mite clearblocks 3 1 1 oak_log[axis=x]");
            f.finish();
            helper.assertTrue(f.level.getBlockState(f.center.west()).isAir(), "The explicitly selected log axis is removed");
            helper.assertTrue(f.level.getBlockState(f.center).is(Blocks.OAK_LOG)
                    && f.level.getBlockState(f.center.east()).is(Blocks.OAK_LOG), "Other log axes survive the property filter");
            f.execute("mite clearblocks 3 1 1 oak_log");
            f.finish();
            helper.assertTrue(f.level.getBlockState(f.center).isAir() && f.level.getBlockState(f.center.east()).isAir(),
                    "A plain block ID removes every state of that block");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredMiteBlocksAreAcceptedWithoutASeparateIdList(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            f.set(f.center, MiteOreBlocks.MITHRIL_ORE.get().defaultBlockState());
            f.set(f.center.east(), MiteOreBlocks.SILVER_ORE.get().defaultBlockState());
            f.execute("mite clearblocks 3 1 1 mite:mithril_ore");
            f.finish();
            helper.assertTrue(f.level.getBlockState(f.center).isAir(), "A registered MITE mineral is cleared");
            helper.assertTrue(f.level.getBlockState(f.center.east()).is(MiteOreBlocks.SILVER_ORE.get()),
                    "A different mod mineral survives");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void permissionAndPlayerChecksRejectWithoutChangingBlocksAndFoodCommandsStillExist(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            f.set(f.center, Blocks.STONE.defaultBlockState());
            reject(helper, f.level, f.source().withPermission(0), "mite clearblocks 1 1 1 stone");
            reject(helper, f.level, f.level.getServer().createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    "mite clearblocks 1 1 1 stone");
            helper.assertTrue(f.level.getBlockState(f.center).is(Blocks.STONE), "Rejected callers make no world changes");
            var root = f.level.getServer().getCommands().getDispatcher().getRoot().getChild("mite");
            helper.assertTrue(root != null && root.getChild("food") != null && root.getChild("clearblocks") != null,
                    "Adding this command preserves the existing food diagnostics subtree");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidDimensionsIdsAndOutOfWorldRangesAreRejectedBeforeMutation(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            f.set(f.center, Blocks.STONE.defaultBlockState());
            for (String command : new String[]{"mite clearblocks 0 1 1 stone", "mite clearblocks 1 0 1 stone",
                    "mite clearblocks 1 1 0 stone", "mite clearblocks -1 1 1 stone",
                    "mite clearblocks 1 1 1 mite:nonexistent_block", "mite clearblocks 2147483647 2147483647 2147483647 stone"}) {
                reject(helper, f.level, f.source(), command);
                helper.assertTrue(f.level.getBlockState(f.center).is(Blocks.STONE), "Invalid input cannot partially clear the world");
            }
            BlockPos bottom = new BlockPos(f.center.getX(), f.level.getMinBuildHeight(), f.center.getZ());
            f.set(bottom, Blocks.STONE.defaultBlockState());
            f.player.setPos(bottom.getX() + 0.5, bottom.getY(), bottom.getZ() + 0.5);
            reject(helper, f.level, f.source(), "mite clearblocks 1 1 3 stone");
            helper.assertTrue(f.level.getBlockState(bottom).is(Blocks.STONE), "A range below the dimension floor is rejected atomically");
            BlockPos top = new BlockPos(f.center.getX(), f.level.getMaxBuildHeight() - 1, f.center.getZ());
            f.set(top, Blocks.STONE.defaultBlockState());
            f.player.setPos(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
            reject(helper, f.level, f.source(), "mite clearblocks 1 1 3 stone");
            helper.assertTrue(f.level.getBlockState(top).is(Blocks.STONE), "A range above the dimension ceiling is rejected atomically");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clearingAContainerDeletesItsContentsWithoutSpawningItems(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper); SpawnedEntityCapture capture = new SpawnedEntityCapture(f.level,
                entity -> entity instanceof ItemEntity && entity.blockPosition().distSqr(f.center) < 100)) {
            f.set(f.center, Blocks.CHEST.defaultBlockState());
            ((ChestBlockEntity)f.level.getBlockEntity(f.center)).setItem(0, new ItemStack(Items.DIAMOND, 3));
            f.set(f.center.east(), Blocks.STONE.defaultBlockState());
            f.execute("mite clearblocks 3 1 1 chest");
            f.finish();
            helper.assertTrue(f.level.getBlockState(f.center).isAir() && f.level.getBlockEntity(f.center) == null,
                    "The chest and its block entity are removed");
            helper.assertTrue(f.level.getBlockState(f.center.east()).is(Blocks.STONE), "Unselected neighboring blocks survive");
            helper.assertTrue(capture.live(ItemEntity.class).isEmpty(), "Neither a chest item nor inventory contents are dropped");
            f.execute("mite clearblocks 3 1 1 stone");
            f.finish();
            helper.assertTrue(capture.live(ItemEntity.class).isEmpty(), "Ordinary selected blocks are removed without drops too");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void volumesBeyondTheVanillaFillLimitStillCompleteAcrossInitiallyUnloadedChunks(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            BlockPos last = f.center.offset(16, 16, 16);
            f.set(f.center, Blocks.STONE.defaultBlockState());
            f.set(last, Blocks.STONE.defaultBlockState());
            helper.assertTrue(!f.level.hasChunkAt(f.center.offset(-16, 0, -16)),
                    "The 33 cubed fixture contains a chunk not loaded by its placed targets");
            helper.assertValueEqual(f.execute("mite clearblocks 33 33 33 stone"), 1,
                    "A volume of 35937 is accepted independently of the vanilla fill limit");
            helper.assertTrue(ClearBlocksCommand.hasPendingTask(f.player)
                    && f.level.getBlockState(f.center).is(Blocks.STONE), "Scheduling leaves world edits for the server task");
            f.finish();
            helper.assertTrue(f.level.getBlockState(f.center).isAir() && f.level.getBlockState(last).isAir(),
                    "The queued job loads its range as needed and reaches the positive corner");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneTallChunkContinuesFromItsCursorAcrossMultipleTickSlices(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            BlockPos first = f.center.offset(-8, -64, -8);
            BlockPos last = f.center.offset(7, 64, 7);
            f.set(first, Blocks.STONE.defaultBlockState());
            f.set(last, Blocks.STONE.defaultBlockState());
            f.execute("mite clearblocks 16 16 129 stone");
            ClearBlocksCommand.processPending(f.level.getServer());
            helper.assertTrue(ClearBlocksCommand.hasPendingTask(f.player), "33024 cells do not finish within one 32768-cell tick slice");
            helper.assertTrue(f.level.getBlockState(first).isAir() && f.level.getBlockState(last).is(Blocks.STONE),
                    "The first slice clears its beginning and leaves the final layer queued");
            f.finish();
            helper.assertTrue(f.level.getBlockState(last).isAir(), "The second slice resumes at the remaining layer rather than restarting");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualPlayerLogoutCancelsTheirQueuedTask(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            f.set(f.center, Blocks.STONE.defaultBlockState());
            f.execute("mite clearblocks 10 10 10 stone");
            f.logout();
            ClearBlocksCommand.processPending(f.level.getServer());
            helper.assertTrue(!ClearBlocksCommand.hasPendingTask(f.player)
                    && f.level.getBlockState(f.center).is(Blocks.STONE), "The logout event cancels work before the next world edit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicateJobsAreRejectedAndCancellationStopsQueuedEdits(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            f.set(f.center, Blocks.STONE.defaultBlockState());
            f.execute("mite clearblocks 10 10 10 stone");
            reject(helper, f.level, f.source(), "mite clearblocks 1 1 1 stone");
            helper.assertValueEqual(f.execute("mite clearblocks cancel"), 1, "An owner can cancel their queued task");
            ClearBlocksCommand.processPending(f.level.getServer());
            helper.assertTrue(!ClearBlocksCommand.hasPendingTask(f.player)
                    && f.level.getBlockState(f.center).is(Blocks.STONE), "Canceled work never removes the selected block");
            helper.assertValueEqual(f.execute("mite clearblocks cancel"), 0, "Canceling without queued work is harmless");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aNoMatchTaskCompletesAndAllowsTheNextCommand(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            f.set(f.center, Blocks.GRANITE.defaultBlockState());
            f.execute("mite clearblocks 1 1 1 stone");
            f.finish();
            helper.assertTrue(f.level.getBlockState(f.center).is(Blocks.GRANITE), "No-match completion preserves the other block");
            f.execute("mite clearblocks 1 1 1 granite");
            f.finish();
            helper.assertTrue(f.level.getBlockState(f.center).isAir(), "A completed no-match task does not leave a player locked out");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void queuedBoundsUseThePlayersInitialPositionAndDoNotFollowExecutePositionOrMovement(GameTestHelper helper) throws CommandSyntaxException {
        try (Fixture f = new Fixture(helper)) {
            BlockPos moved = f.center.east(32);
            f.set(f.center, Blocks.STONE.defaultBlockState());
            f.set(moved, Blocks.STONE.defaultBlockState());
            f.level.getServer().getCommands().getDispatcher().execute("mite clearblocks 1 1 1 stone",
                    f.source().withPosition(Vec3.atCenterOf(moved)));
            f.player.setPos(moved.getX() + 0.5, moved.getY(), moved.getZ() + 0.5);
            f.finish();
            helper.assertTrue(f.level.getBlockState(f.center).isAir(), "The command captured the player's initial block position");
            helper.assertTrue(f.level.getBlockState(moved).is(Blocks.STONE),
                    "An execute position override and later player movement cannot change the accepted bounds");
        }
        helper.succeed();
    }

    private static void reject(GameTestHelper helper, ServerLevel level, CommandSourceStack source, String command) {
        try {
            level.getServer().getCommands().getDispatcher().execute(command, source);
            throw new AssertionError("The command must reject: " + command);
        } catch (CommandSyntaxException expected) {
            helper.assertTrue(expected.getRawMessage() != null, "The rejected command provides a useful explanation");
        }
    }

    private record Previous(BlockState state, CompoundTag data) {}

    @SuppressWarnings("removal")
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos center;
        final ServerPlayer player;
        final Map<BlockPos, Previous> previous = new LinkedHashMap<>();
        private boolean loggedOut;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            center = new BlockPos(-2_200_000 - IDS.incrementAndGet() * 128 + 8, 160, -2_200_000 + 8);
            level.getChunkAt(center);
            player = helper.makeMockServerPlayerInLevel();
            player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            player.setNoGravity(true);
        }

        CommandSourceStack source() { return player.createCommandSourceStack().withPermission(2).withSuppressedOutput(); }

        int execute(String command) throws CommandSyntaxException {
            return level.getServer().getCommands().getDispatcher().execute(command, source());
        }

        void finish() {
            // The production command processes the same queue once per real server tick.
            for (int step = 0; step < 2000 && ClearBlocksCommand.hasPendingTask(player); step++)
                ClearBlocksCommand.processPending(level.getServer());
            helper.assertTrue(!ClearBlocksCommand.hasPendingTask(player), "The bounded server task eventually completes");
        }

        void set(BlockPos pos, BlockState state) {
            BlockPos key = pos.immutable();
            previous.computeIfAbsent(key, ignored -> {
                BlockEntity entity = level.getBlockEntity(key);
                return new Previous(level.getBlockState(key), entity == null ? null : entity.saveWithFullMetadata(level.registryAccess()));
            });
            level.setBlock(key, state, 2);
        }

        void logout() {
            if (!loggedOut) {
                level.getServer().getPlayerList().remove(player);
                loggedOut = true;
            }
        }

        @Override public void close() {
            if (!loggedOut) {
                try { execute("mite clearblocks cancel"); }
                catch (CommandSyntaxException failure) { throw new IllegalStateException("Fixture cancellation failed", failure); }
            }
            logout();
            for (BlockPos pos : previous.keySet()) {
                Clearable.tryClear(level.getBlockEntity(pos));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            for (var entry : previous.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue().state(), 2);
                if (entry.getValue().data() != null) {
                    BlockEntity restored = BlockEntity.loadStatic(entry.getKey(), entry.getValue().state(), entry.getValue().data(), level.registryAccess());
                    if (restored != null) level.setBlockEntity(restored);
                }
            }
        }
    }
}
