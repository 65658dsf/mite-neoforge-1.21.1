package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.world.CollapsePhysics;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source isSolid overrides and canBeReplacedBy exceptions, independent of modern full-face rules. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CollapseSupportGameTests {
    @GameTest(template = "empty")
    public static void legacyNonsolidFixturesStayNonsolidDespiteModernCollision(GameTestHelper h) {
        var level = h.getLevel(); BlockPos p = h.absolutePos(new BlockPos(2, 3, 2));
        for (var block : List.of(Blocks.STONE_PRESSURE_PLATE, Blocks.OAK_PRESSURE_PLATE,
                Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
                Blocks.STONE_BUTTON, Blocks.OAK_BUTTON, Blocks.LEVER, Blocks.WHITE_CARPET,
                Blocks.RED_CARPET, Blocks.RAIL, Blocks.REDSTONE_WIRE, Blocks.COBWEB,
                Blocks.OAK_SIGN, Blocks.OAK_WALL_SIGN, Blocks.TRIPWIRE_HOOK, Blocks.TRIPWIRE,
                Blocks.TORCH, Blocks.REDSTONE_TORCH, Blocks.VINE, Blocks.SUGAR_CANE)) {
            h.assertFalse(CollapsePhysics.solid(level, p, block.defaultBlockState()),
                    "The actual R196 isSolid override is false for " + block);
        }
        h.assertTrue(CollapsePhysics.solid(level, p, Blocks.OAK_FENCE_GATE.defaultBlockState()), "Closed source fence gate is solid");
        h.assertFalse(CollapsePhysics.solid(level, p, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true)),
                "The source gate isSolid override depends on the open bit");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyDefaultSolidityDoesNotRequireAFullCollisionCube(GameTestHelper h) {
        var level = h.getLevel(); BlockPos p = h.absolutePos(new BlockPos(2, 3, 2));
        for (BlockState state : List.of(Blocks.OAK_FENCE.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                Blocks.GLASS_PANE.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Blocks.OAK_DOOR.defaultBlockState(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true),
                Blocks.OAK_TRAPDOOR.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true),
                Blocks.RED_BED.defaultBlockState(), Blocks.LADDER.defaultBlockState(), Blocks.COCOA.defaultBlockState(),
                Blocks.FLOWER_POT.defaultBlockState(), Blocks.POTTED_CACTUS.defaultBlockState(), Blocks.MOVING_PISTON.defaultBlockState())) {
            h.assertTrue(CollapsePhysics.solid(level, p, state),
                    "This source class inherits true solidity independently of full-face support or modern noCollision: " + state);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void flowerPotsAndLilyPadsCannotBeCrushedByZeroHardnessShortcut(GameTestHelper h) {
        var level = h.getLevel(); BlockPos p = h.absolutePos(new BlockPos(2, 3, 2));
        for (var block : List.of(Blocks.FLOWER_POT, Blocks.POTTED_OAK_SAPLING, Blocks.POTTED_DANDELION,
                Blocks.POTTED_CACTUS, Blocks.LILY_PAD)) {
            level.setBlock(p, block.defaultBlockState(), 18);
            h.assertTrue(CollapsePhysics.solid(level, p, level.getBlockState(p)), "Source solidity of pot/pad is true");
            h.assertFalse(CollapsePhysics.canDislodgeOrCrush(level, p, Blocks.DIRT.defaultBlockState()),
                    "aoj refuses replacement even with source hardness0; a lily pad has source hardness.02: " + block);
            h.assertFalse(CollapsePhysics.canFallInto(level, p, Blocks.DIRT.defaultBlockState()), "The same source exception prevents starting a fall");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void snowDepthSkullsAndRailsUseTheirSeparateSourceConditions(GameTestHelper h) {
        var level = h.getLevel(); BlockPos p = h.absolutePos(new BlockPos(2, 3, 2));
        var dirt = Blocks.DIRT.defaultBlockState();
        for (int depth : new int[]{1, 2, 7, 8}) {
            level.setBlock(p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, depth), 18);
            h.assertTrue(CollapsePhysics.solid(level, p, level.getBlockState(p)) == (depth > 1), "arf.isSolid is exactly depth > 1");
            h.assertTrue(CollapsePhysics.canFallInto(level, p, dirt) == (depth == 1), "Modern replaceable snow layers must not override the source depth condition");
        }
        level.setBlock(p, Blocks.SKELETON_SKULL.defaultBlockState(), 18);
        h.assertTrue(CollapsePhysics.canDislodgeOrCrush(level, p, dirt), "aqk explicitly allows crushing a skull");
        level.setBlock(p, Blocks.RAIL.defaultBlockState(), 18);
        h.assertTrue(CollapsePhysics.canFallInto(level, p, dirt), "A rail permits the initial descent because source isSolid is false");
        h.assertFalse(CollapsePhysics.canDislodgeOrCrush(level, p, dirt), "amy separately vetoes dislodging at landing");
        h.succeed();
    }
}
