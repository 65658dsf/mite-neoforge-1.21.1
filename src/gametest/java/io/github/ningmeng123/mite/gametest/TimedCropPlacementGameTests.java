package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.survival.FoodAgriculture;
import io.github.ningmeng123.mite.survival.TimedCropGrowth;
import io.github.ningmeng123.mite.survival.TimedCropGrowth.Data;
import io.github.ningmeng123.mite.survival.TimedCropGrowth.Entry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Committed replacement and cancelled NeoForge placement snapshots retain distinct crop identities. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class TimedCropPlacementGameTests {
    private static final long PREVIOUS_WORK = 1_400_000_000L;

    private TimedCropPlacementGameTests() {}

    @GameTest(template = "empty", batch = "timed_crop_placement")
    public static void committedSameStateReplantStartsANewClock(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            helper.assertValueEqual(fixture.work(), PREVIOUS_WORK, "The original age-zero crop already owns legitimate partial-stage work");
            fixture.replant(false);
            helper.assertValueEqual(fixture.level.getBlockState(fixture.pos), Blocks.WHEAT.defaultBlockState(), "A successful transaction ends in the exact same block state");
            TimedCropGrowth.tickChunk(fixture.level, fixture.chunk);
            helper.assertValueEqual(fixture.work(), 0L, "Removing and replanting the same age-zero crop commits a new identity rather than inheriting work");
            helper.assertValueEqual(fixture.remaining(), 72_000L, "The replacement starts a full sixty-minute unboosted clock");
            fixture.advancePaused(20);
            helper.assertValueEqual(fixture.work(), 0L, "Paused scheduling cannot restore the removed crop's former work");
            helper.assertValueEqual(fixture.remaining(), 72_000L, "Processing an entire scheduling interval leaves the replacement's full clock intact while paused");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "timed_crop_placement")
    public static void cancelledSameStateReplantRestoresTheOriginalClock(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.replant(true);
            helper.assertValueEqual(fixture.level.getBlockState(fixture.pos), Blocks.WHEAT.defaultBlockState(), "Reverse snapshot restoration returns the original crop state");
            TimedCropGrowth.tickChunk(fixture.level, fixture.chunk);
            helper.assertValueEqual(fixture.work(), PREVIOUS_WORK, "A cancelled transaction keeps the original crop's exact partial-stage work");
            helper.assertValueEqual(fixture.remaining(), 71_800L, "Rollback keeps the original remaining time instead of charging or resetting it");
            fixture.advancePaused(20);
            helper.assertValueEqual(fixture.work(), PREVIOUS_WORK, "A whole paused scheduling interval neither loses nor adds work after rollback");
            helper.assertValueEqual(fixture.remaining(), 71_800L, "The original countdown remains stable through scheduling after rollback");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final LevelChunk chunk;
        final BlockPos pos;
        final ServerLevelData levelData;
        final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        final Data previousClocks;
        final List<BlockSnapshot> previousSnapshots;
        final boolean previousCapture;
        final boolean previousRestoring;
        final long previousGameTime;
        final int previousRandomTicks;

        Fixture(GameTestHelper helper) {
            level = helper.getLevel();
            BlockPos reference = helper.absolutePos(BlockPos.ZERO);
            pos = new BlockPos((reference.getX() & ~15) + 8, level.getMaxBuildHeight() - 48, (reference.getZ() & ~15) + 8);
            chunk = level.getChunkAt(pos);
            levelData = (ServerLevelData)level.getLevelData();
            Data clocks = chunk.getExistingDataOrNull(TimedCropGrowth.CLOCKS.get());
            previousClocks = clocks == null ? null : new Data(clocks.entries());
            previousSnapshots = new ArrayList<>(level.capturedBlockSnapshots);
            previousCapture = level.captureBlockSnapshots;
            previousRestoring = level.restoringBlockSnapshots;
            previousGameTime = level.getGameTime();
            previousRandomTicks = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            level.captureBlockSnapshots = false;
            level.restoringBlockSnapshots = false;
            level.capturedBlockSnapshots.clear();
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
            for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) set(cell, Blocks.AIR.defaultBlockState());
            BlockState wet = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7).setValue(FoodAgriculture.FERTILIZED, false);
            for (BlockPos soil : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, -1, 1))) set(soil, wet);
            set(pos, Blocks.WHEAT.defaultBlockState());
            chunk.setData(TimedCropGrowth.CLOCKS.get(), new Data(List.of(new Entry(pos.asLong(), "minecraft:wheat", PREVIOUS_WORK))));
        }

        void set(BlockPos position, BlockState state) {
            originals.putIfAbsent(position.immutable(), level.getBlockState(position));
            level.setBlock(position, state, Block.UPDATE_CLIENTS);
        }

        void replant(boolean cancel) {
            try {
                level.captureBlockSnapshots = true;
                // Level.setBlock invokes the real LevelChunk replacement hook and creates real snapshots.
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(pos, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_CLIENTS);
            } finally {
                level.captureBlockSnapshots = false;
            }
            List<BlockSnapshot> snapshots = new ArrayList<>(level.capturedBlockSnapshots);
            level.capturedBlockSnapshots.clear();
            if (cancel) {
                try {
                    level.restoringBlockSnapshots = true;
                    for (int index = snapshots.size() - 1; index >= 0; index--) {
                        BlockSnapshot snapshot = snapshots.get(index);
                        snapshot.restore(snapshot.getFlags() | Block.UPDATE_CLIENTS);
                    }
                } finally {
                    level.restoringBlockSnapshots = false;
                }
            } else {
                for (BlockSnapshot snapshot : snapshots) {
                    // CommonHooks commits placements with their delayed native onPlace callbacks.
                    level.getBlockState(snapshot.getPos()).onPlace(level, snapshot.getPos(), snapshot.getState(), false);
                }
            }
        }

        long work() { return TimedCropGrowth.work(chunk, pos); }
        long remaining() { return TimedCropGrowth.remainingTicks(level, pos, level.getBlockState(pos)); }
        void advancePaused(int ticks) {
            for (int index = 0; index < ticks; index++) {
                levelData.setGameTime(level.getGameTime() + 1L);
                TimedCropGrowth.tickChunk(level, chunk);
            }
        }

        @Override public void close() {
            level.captureBlockSnapshots = false;
            level.restoringBlockSnapshots = false;
            originals.forEach((position, state) -> level.setBlock(position, state, Block.UPDATE_CLIENTS));
            if (previousClocks == null) chunk.removeData(TimedCropGrowth.CLOCKS.get());
            else chunk.setData(TimedCropGrowth.CLOCKS.get(), previousClocks);
            levelData.setGameTime(previousGameTime);
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(previousRandomTicks, level.getServer());
            level.capturedBlockSnapshots.clear();
            level.capturedBlockSnapshots.addAll(previousSnapshots);
            level.captureBlockSnapshots = previousCapture;
            level.restoringBlockSnapshots = previousRestoring;
        }
    }
}
