package io.github.ningmeng123.mite.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks carpet conservation and vanilla rail behavior during piston movement. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class CarpetDuplicationGameTests {
    private CarpetDuplicationGameTests() {}

    @GameTest(template = "empty")
    public static void pistonDoesNotDuplicateCarpetWhenItsSupportBreaks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos piston = placePiston(helper);
        BlockPos support = piston.east(3);
        BlockPos carpet = support.above();
        place(level, piston.east(), Blocks.STONE);
        place(level, support.west(), Blocks.SLIME_BLOCK);
        place(level, support, Blocks.FLOWER_POT);
        place(level, carpet.west(), Blocks.SLIME_BLOCK);
        place(level, support.east(), Blocks.STONE);
        place(level, support.west().south(), Blocks.STONE);
        place(level, support.south(), Blocks.STONE);
        placeCoralFan(level, carpet.south());
        place(level, carpet, Blocks.WHITE_CARPET);
        helper.assertTrue(level.getBlockState(carpet).is(Blocks.WHITE_CARPET), "One carpet is present before the piston starts");

        extendPiston(helper, piston);

        helper.runAfterDelay(5, () -> {
            helper.assertFalse(level.getBlockState(carpet).is(Blocks.WHITE_CARPET), "The carpet leaves its original position");
            int placed = level.getBlockState(carpet.east()).is(Blocks.WHITE_CARPET) ? 1 : 0;
            int dropped = countAndRemoveDrops(level, carpet, Blocks.WHITE_CARPET);
            helper.assertValueEqual(placed + dropped, 1, "The piston move conserves exactly one carpet");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void pistonStillMovesSupportedCarpet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos piston = placePiston(helper);
        BlockPos carpet = piston.east().above();
        place(level, carpet.below(), Blocks.SLIME_BLOCK);
        place(level, carpet, Blocks.RED_CARPET);

        extendPiston(helper, piston);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getBlockState(carpet.east()).is(Blocks.RED_CARPET), "A supported carpet moves with its slime block");
            helper.assertValueEqual(countAndRemoveDrops(level, carpet, Blocks.RED_CARPET), 0, "The moved carpet drops no item");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void pistonDropsUnsupportedCarpetOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos piston = placePiston(helper);
        BlockPos carpet = piston.east();
        place(level, carpet.below(), Blocks.STONE);
        place(level, carpet, Blocks.BLUE_CARPET);

        extendPiston(helper, piston);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getBlockState(carpet.east()).isAir(), "The carpet cannot land without support");
            helper.assertValueEqual(countAndRemoveDrops(level, carpet, Blocks.BLUE_CARPET), 1, "The unsupported carpet drops once");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void pistonStillDuplicatesRails(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos piston = placePiston(helper);
        BlockPos rail = piston.east(3).above();
        place(level, piston.east(), Blocks.STONE);
        place(level, piston.east(2), Blocks.STONE);
        place(level, rail.below(), Blocks.SLIME_BLOCK);
        placeCoralFan(level, rail.west());
        place(level, rail, Blocks.RAIL);

        extendPiston(helper, piston);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getBlockState(rail.east()).is(Blocks.RAIL), "The original rail reaches the destination");
            helper.assertValueEqual(countAndRemoveDrops(level, rail, Blocks.RAIL), 1, "The native rail duplication still drops a rail");
            helper.succeed();
        });
    }

    private static BlockPos placePiston(GameTestHelper helper) {
        BlockPos piston = helper.absolutePos(new BlockPos(0, 2, 2));
        helper.getLevel().setBlockAndUpdate(piston, Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.EAST));
        return piston;
    }

    private static void place(ServerLevel level, BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
    }

    private static void placeCoralFan(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    private static void extendPiston(GameTestHelper helper, BlockPos piston) {
        ServerLevel level = helper.getLevel();
        level.setBlock(piston.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE);
        helper.assertTrue(level.getBlockState(piston).triggerEvent(level, piston, 0, Direction.EAST.get3DDataValue()),
                "The piston starts extending");
    }

    private static int countAndRemoveDrops(ServerLevel level, BlockPos pos, Block block) {
        var collectionArea = new AABB(pos).inflate(1.5).expandTowards(6, 0, 0);
        var drops = level.getEntitiesOfClass(ItemEntity.class, collectionArea,
                entity -> entity.getItem().is(block.asItem()));
        int count = drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        drops.forEach(ItemEntity::discard);
        return count;
    }
}
