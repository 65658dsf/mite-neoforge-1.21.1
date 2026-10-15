package io.github.ningmeng123.mite.gametest;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Invoke the actual corridor support placement, with independent controlled torch probability draws. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MineshaftTorchGameTests {
    private MineshaftTorchGameTests() {}

    @GameTest(template = "empty")
    public static void theLastTickBeforeDayTenConsumesBothRollsButPlacesNoTorches(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        long previousTime = level.getDayTime();
        try {
            level.setDayTime(209999L);
            Fixture fixture = new Fixture(helper);
            TorchRolls rolls = new TorchRolls(0F, 0F);
            fixture.placeSupport(rolls);
            helper.assertTrue(level.getBlockState(fixture.firstTorch).isAir()
                    && level.getBlockState(fixture.secondTorch).isAir(), "Even two successful native probability draws cannot light a mine before day ten");
            helper.assertValueEqual(rolls.draws, 2, "Both source torch draws are still consumed before day ten");
            fixture.assertSupport(helper);
        } finally {
            level.setDayTime(previousTime);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactlyDayTenRestoresTheNativeFivePercentThreshold(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        long previousTime = level.getDayTime();
        try {
            level.setDayTime(210000L);
            Fixture fixture = new Fixture(helper);
            TorchRolls rolls = new TorchRolls(.049999F, .05F);
            fixture.placeSupport(rolls);
            helper.assertTrue(level.getBlockState(fixture.firstTorch).is(Blocks.WALL_TORCH), "At display day ten the native roll below five percent places a real wall torch");
            helper.assertTrue(level.getBlockState(fixture.secondTorch).isAir(), "The native strict five-percent cutoff rejects a roll exactly equal to 0.05");
            helper.assertValueEqual(rolls.draws, 2, "The unlocked native implementation consumes the same two random draws");
            fixture.assertSupport(helper);
        } finally {
            level.setDayTime(previousTime);
        }
        helper.succeed();
    }

    private static final class Fixture {
        private final ServerLevel level;
        private final BlockPos base;
        private final BoundingBox writable;
        private final MineshaftPieces.MineShaftCorridor corridor;
        private final BlockPos firstTorch;
        private final BlockPos secondTorch;

        Fixture(GameTestHelper helper) {
            level = helper.getLevel();
            base = helper.absolutePos(new BlockPos(2, 1, 2));
            BoundingBox corridorBox = new BoundingBox(base.getX(), base.getY(), base.getZ(),
                    base.getX() + 2, base.getY() + 2, base.getZ() + 4);
            writable = new BoundingBox(base.getX(), base.getY(), base.getZ(),
                    base.getX() + 2, base.getY() + 3, base.getZ() + 4);
            corridor = new MineshaftPieces.MineShaftCorridor(0, RandomSource.create(196L), corridorBox,
                    Direction.SOUTH, MineshaftStructure.Type.NORMAL);
            firstTorch = base.offset(1, 2, 1);
            secondTorch = base.offset(1, 2, 3);
            for (int x = 0; x <= 2; x++)
                level.setBlock(base.offset(x, 3, 2), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(firstTorch, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(secondTorch, Blocks.AIR.defaultBlockState(), 2);
        }

        void placeSupport(RandomSource random) throws ReflectiveOperationException {
            Method method = MineshaftPieces.MineShaftCorridor.class.getDeclaredMethod("placeSupport", WorldGenLevel.class,
                    BoundingBox.class, int.class, int.class, int.class, int.class, int.class, RandomSource.class);
            method.setAccessible(true);
            method.invoke(corridor, level, writable, 0, 0, 2, 2, 2, random);
        }

        void assertSupport(GameTestHelper helper) {
            for (int x : new int[]{0, 2}) {
                helper.assertTrue(level.getBlockState(base.offset(x, 0, 2)).is(Blocks.OAK_FENCE)
                        && level.getBlockState(base.offset(x, 1, 2)).is(Blocks.OAK_FENCE), "The native support fence posts remain unchanged");
            }
            for (int x = 0; x <= 2; x++)
                helper.assertTrue(level.getBlockState(base.offset(x, 2, 2)).is(Blocks.OAK_PLANKS), "The native full support beam remains unchanged");
        }
    }

    private static final class TorchRolls extends LegacyRandomSource {
        private final float[] values;
        private int draws;

        TorchRolls(float... values) {
            super(196L);
            this.values = values;
        }

        @Override public int nextInt(int bound) {
            // A complete native beam invokes both independent torch attempts.
            return Math.min(1, bound - 1);
        }

        @Override public float nextFloat() {
            if (draws >= values.length) throw new AssertionError("Unexpected extra native probability draw");
            return values[draws++];
        }
    }
}
