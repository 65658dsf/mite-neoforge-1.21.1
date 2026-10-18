package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Cube;
import io.github.ningmeng123.mite.registry.MiteMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual crawling displacement derived from the source .15/.25 input and .1/.02 acceleration. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CubeMovementRegressionGameTests {
    private static R196Cube cube(GameTestHelper helper, int size) {
        R196Cube cube = (R196Cube) MiteMobs.type(MobKind.OOZE).create(helper.getLevel());
        cube.setSize(size, true);
        prepare(helper, cube);
        return cube;
    }

    private static void prepare(GameTestHelper helper, R196Cube cube) {
        BlockPos feet = helper.absolutePos(new BlockPos(3, 3, 3));
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, -1, 12)))
            helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        cube.setNoAi(false);
        cube.setNoGravity(true);
        cube.setOnGround(true);
        cube.moveTo(feet.getX() + .5D, feet.getY(), feet.getZ() + .5D, 0F, 0F);
        cube.setDeltaMovement(Vec3.ZERO);
    }

    private static Player target(GameTestHelper helper, R196Cube cube) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(cube.getX(), cube.getY(), cube.getZ() + 10D, 0F, 0F);
        cube.setTarget(player);
        return player;
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .00001D, message + ": " + actual + " vs " + expected);
    }

    private static double controlledStep(R196Cube cube) {
        cube.getMoveControl().tick();
        double before = cube.getZ();
        // LivingEntity.aiStep damps directional input before invoking travel.
        cube.travel(new Vec3(cube.xxa * .98F, 0D, cube.zza * .98F));
        return cube.getZ() - before;
    }

    @GameTest(template = "cube_movement")
    public static void idleOozeGroundDisplacementIsNotSquared(GameTestHelper helper) {
        for (int size : new int[]{1, 2}) {
            R196Cube cube = cube(helper, size);
            close(helper, controlledStep(cube), .1D * .15D * .98D,
                    "Size " + size + " idle ooze uses the source ground step");
        }
        helper.succeed();
    }

    @GameTest(template = "cube_movement")
    public static void pursuingOozeGroundDisplacementIsNotSquared(GameTestHelper helper) {
        for (int size : new int[]{1, 2}) {
            R196Cube cube = cube(helper, size);
            target(helper, cube);
            close(helper, controlledStep(cube), .1D * .25D * .98D,
                    "Size " + size + " pursuing ooze uses the source ground step");
        }
        helper.succeed();
    }

    @GameTest(template = "cube_movement")
    public static void savedOozeResumesCorrectGroundMovement(GameTestHelper helper) {
        R196Cube original = cube(helper, 2);
        double baseSpeed = original.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        CompoundTag saved = new CompoundTag();
        original.saveWithoutId(saved);
        R196Cube restored = cube(helper, 1);
        restored.load(saved);
        prepare(helper, restored);
        target(helper, restored);
        helper.assertTrue(restored.getSize() == 2, "The saved ooze size survives loading");
        close(helper, controlledStep(restored), .1D * .25D * .98D,
                "Existing saved ooze receives corrected movement immediately");
        close(helper, restored.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), baseSpeed,
                "Movement correction preserves the saved source attribute");
        helper.succeed();
    }

    @GameTest(template = "cube_movement")
    public static void airborneOozeRetainsSourceAirAcceleration(GameTestHelper helper) {
        for (int size : new int[]{1, 2}) {
            R196Cube cube = cube(helper, size);
            cube.setPos(cube.getX(), cube.getY() + 2D, cube.getZ());
            cube.setOnGround(false);
            target(helper, cube);
            close(helper, controlledStep(cube), .02D * .25D * .98D,
                    "Size " + size + " airborne crawl keeps the source .02 acceleration");
        }
        helper.succeed();
    }

    @GameTest(template = "cube_movement")
    public static void oozeStillClimbsAfterRealWallCollision(GameTestHelper helper) {
        for (int size : new int[]{1, 2}) {
            R196Cube cube = cube(helper, size);
            BlockPos wall = helper.absolutePos(new BlockPos(3, 3, 4));
            for (BlockPos pos : BlockPos.betweenClosed(wall.offset(-2, 0, 0), wall.offset(2, 4, 0)))
                helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            cube.setPos(cube.getX(), cube.getY(), wall.getZ() - cube.getBbWidth() / 2D);
            target(helper, cube);
            controlledStep(cube);
            helper.assertTrue(cube.horizontalCollision, "Ooze physically collides with the stone wall");
            cube.aiStep();
            helper.assertTrue(cube.onClimbable(), "Wall contact enables the ooze climbing state");
            close(helper, cube.getDeltaMovement().y, .2D, "Source upward climbing impulse is preserved");
            double before = cube.getY();
            cube.aiStep();
            helper.assertTrue(cube.getY() > before, "Ooze actually moves upward along the wall");
            // Each size uses a fresh floor-and-wall fixture.
            for (BlockPos pos : BlockPos.betweenClosed(wall.offset(-2, 0, 0), wall.offset(2, 4, 0)))
                helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }
}
