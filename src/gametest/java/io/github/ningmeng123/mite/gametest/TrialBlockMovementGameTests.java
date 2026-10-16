package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual vanilla piston transactions cannot launder natural chamber blocks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class TrialBlockMovementGameTests {
    private TrialBlockMovementGameTests() {}

    @GameTest(template = "empty")
    public static void nativePistonMixedChainPreservesEachBlocksOwnSource(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 0)) {
            fixture.place(0, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), false);
            fixture.place(1, Blocks.COPPER_BLOCK.defaultBlockState(), true);
            fixture.place(2, Blocks.WAXED_COPPER_BLOCK.defaultBlockState(), false);
            fixture.place(3, Blocks.CUT_COPPER.defaultBlockState(), true);
            fixture.power(true);
            helper.assertTrue(fixture.event(0), "Native piston really extends a mixed three-block chain");
            fixture.natural(1, false, "The new piston head cannot inherit the first original block's source");
            fixture.natural(2, true, "The first original block carries provenance into the moving block");
            fixture.natural(3, false, "A player block pushed into a natural source position remains a player block");
            fixture.natural(4, true, "The far original block carries its own source beyond the overlapping chain");
            fixture.finishByTick(1, 2, 3, 4);
            fixture.block(2, Blocks.COPPER_BLOCK);
            fixture.block(3, Blocks.WAXED_COPPER_BLOCK);
            fixture.block(4, Blocks.CUT_COPPER);
            fixture.natural(2, true, "Native tick completion retains the first source");
            fixture.natural(3, false, "Native completion never promotes the player block");
            fixture.natural(4, true, "Native tick completion retains the last source");
            helper.assertTrue(fixture.loot(2).isEmpty() && fixture.loot(4).isEmpty(),
                    "Moved natural copper cannot drop materials outside its starting position");
            helper.assertTrue(!fixture.loot(3).isEmpty(), "Moved player copper still has its normal block loot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeStickyRetractionReturnsSourceWithoutMarkingPiston(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            fixture.place(0, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), false);
            fixture.place(1, Blocks.COPPER_BLOCK.defaultBlockState(), true);
            fixture.power(true);
            helper.assertTrue(fixture.event(0), "The native sticky piston extends");
            fixture.finishByTick(1, 2);
            fixture.natural(2, true, "The extended natural block retains its identity");
            fixture.power(false);
            helper.assertTrue(fixture.event(1), "The actual powered-off sticky piston retracts");
            fixture.natural(0, false, "The retracting piston base is not a natural chamber block");
            fixture.natural(1, true, "Sticky pull transfers provenance to the returning moving block");
            fixture.natural(2, false, "The old source is cleared on actual retraction");
            fixture.finishByTick(0, 1);
            fixture.block(0, Blocks.STICKY_PISTON);
            fixture.block(1, Blocks.COPPER_BLOCK);
            fixture.natural(1, true, "The returned copper remains natural after completion");
            helper.assertTrue(fixture.loot(1).isEmpty(), "Push/pull cycles cannot unlock the original copper drop");
            fixture.place(2, Blocks.COPPER_BLOCK.defaultBlockState(), false);
            fixture.natural(2, false, "A new player block in the vacated position remains unrelated");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedMovingBlockAndEarlyFinalTickKeepSourceAndNativeLootRules(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 2)) {
            fixture.place(0, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), false);
            fixture.place(1, Blocks.COPPER_BLOCK.defaultBlockState(), true);
            fixture.place(2, Blocks.CUT_COPPER.defaultBlockState(), false);
            fixture.power(true);
            helper.assertTrue(fixture.event(0), "The real piston starts moving original and player blocks");
            helper.assertTrue(fixture.loot(2).isEmpty(), "Breaking a still-moving original cannot drop its moved block");
            helper.assertTrue(!fixture.loot(3).isEmpty(), "Breaking a still-moving player block retains native loot");
            for (int index : new int[] {2, 3}) {
                BlockPos pos = fixture.pos(index);
                BlockEntity original = fixture.level.getBlockEntity(pos);
                helper.assertTrue(original instanceof PistonMovingBlockEntity, "The fixture contains a real native moving block entity");
                CompoundTag saved = original.saveWithFullMetadata(fixture.level.registryAccess());
                BlockEntity loaded = BlockEntity.loadStatic(pos, fixture.level.getBlockState(pos), saved, fixture.level.registryAccess());
                helper.assertTrue(loaded instanceof PistonMovingBlockEntity, "Native block entity NBT reload retains the moving block");
                fixture.level.removeBlockEntity(pos);
                fixture.level.setBlockEntity(loaded);
                ((PistonMovingBlockEntity)loaded).finalTick();
            }
            fixture.block(2, Blocks.COPPER_BLOCK);
            fixture.block(3, Blocks.CUT_COPPER);
            fixture.natural(2, true, "Reload and early finalTick cannot discard the chunk's original source");
            fixture.natural(3, false, "Reload does not manufacture source identity for a player block");
            helper.assertTrue(fixture.loot(2).isEmpty() && !fixture.loot(3).isEmpty(),
                    "Persisted moving originals remain drop-free while persisted player blocks keep drops");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedPistonDoesNotMoveOrManufactureAnySource(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 3)) {
            fixture.place(0, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), false);
            fixture.place(1, Blocks.COPPER_BLOCK.defaultBlockState(), true);
            fixture.place(2, Blocks.OBSIDIAN.defaultBlockState(), false);
            fixture.power(true);
            helper.assertFalse(fixture.event(0), "Native resolver still rejects an immovable block");
            fixture.block(1, Blocks.COPPER_BLOCK);
            fixture.block(2, Blocks.OBSIDIAN);
            fixture.natural(1, true, "An unsuccessful push retains the actual original source");
            fixture.natural(2, false, "An unsuccessful push cannot mark the unmoved obstruction");
            fixture.natural(3, false, "An unsuccessful push cannot mark a speculative destination");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos piston;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();

        private Fixture(GameTestHelper helper, int slot) {
            this.helper = helper;
            level = helper.getLevel();
            piston = new BlockPos(17_200_000 + slot * 32, 200, 17_200_000);
            for (int x = -1; x <= 6; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = piston.offset(x, 0, z);
                    original.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        private BlockPos pos(int index) {
            return piston.east(index);
        }

        private void place(int index, BlockState state, boolean natural) {
            BlockPos pos = pos(index);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos, state, 2);
            if (natural) NaturalTrialBlocks.record(level.getChunkAt(pos), pos);
        }

        private void power(boolean powered) {
            level.setBlock(piston.south(), powered ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }

        private boolean event(int event) {
            BlockState state = level.getBlockState(piston);
            return state.triggerEvent(level, piston, event, Direction.EAST.get3DDataValue());
        }

        private void finishByTick(int... positions) {
            for (int tick = 0; tick < 3; tick++) {
                for (int index : positions) {
                    BlockPos pos = pos(index);
                    if (level.getBlockEntity(pos) instanceof PistonMovingBlockEntity moving) {
                        PistonMovingBlockEntity.tick(level, pos, level.getBlockState(pos), moving);
                    }
                }
            }
        }

        private void block(int index, Block block) {
            helper.assertTrue(level.getBlockState(pos(index)).is(block), "Native movement produced the expected block at " + index + ": " + block);
        }

        private void natural(int index, boolean expected, String message) {
            helper.assertValueEqual(NaturalTrialBlocks.isNatural(level, pos(index)), expected, message);
        }

        private List<ItemStack> loot(int index) {
            BlockPos pos = pos(index);
            return Block.getDrops(level.getBlockState(pos), level, pos, level.getBlockEntity(pos), null, new ItemStack(Items.NETHERITE_PICKAXE));
        }

        @Override
        public void close() {
            for (BlockPos pos : original.keySet()) {
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            original.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
    }
}
