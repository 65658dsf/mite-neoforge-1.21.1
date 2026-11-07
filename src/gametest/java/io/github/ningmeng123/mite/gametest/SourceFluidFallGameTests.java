package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.world.SourceFluidFall;
import io.github.ningmeng123.mite.world.TemporaryFluidChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native fluid ticks across unsupported flowing cells and descending terrain. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class SourceFluidFallGameTests {
    private SourceFluidFallGameTests() {}

    @GameTest(template = "fluid_fall")
    public static void everyFlowingWaterAndLavaLevelDrainsWithoutBecomingAFallingSource(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            BlockPos target = fixture.pos(3, 4, 3);
            var changes = TemporaryFluidChanges.get(helper.getLevel());
            for (boolean water : new boolean[]{true, false}) {
                for (int liquidLevel = 1; liquidLevel <= 15; liquidLevel++) {
                    fixture.clear();
                    var block = water ? Blocks.WATER : Blocks.LAVA;
                    helper.getLevel().setBlock(target, block.defaultBlockState().setValue(LiquidBlock.LEVEL, liquidLevel), 2);
                    var state = helper.getLevel().getFluidState(target);
                    helper.assertFalse(state.isSource(), "Every tested block level is actual non-source liquid");
                    helper.assertFalse(SourceFluidFall.tryFall(helper.getLevel(), target, state),
                            "Flowing liquid level " + liquidLevel + " cannot enter whole-source freefall");
                    state.tick(helper.getLevel(), target);
                    helper.assertTrue(helper.getLevel().getFluidState(target).isEmpty(),
                            "An isolated flowing cell drains through its native fluid tick");
                    helper.assertTrue(helper.getLevel().getFluidState(target.below()).isEmpty(),
                            "Draining a flowing cell cannot create a source underneath it");
                    helper.assertTrue(changes.at(target) == null && changes.at(target.below()) == null,
                            "Ordinary flowing liquid creates no whole-source decay task");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "fluid_fall")
    public static void downhillWaterNeverCreatesNewSourcesAndDrainsAfterItsUpstreamIsRemoved(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            for (int x = 1; x <= 5; x++) {
                for (int y = 1; y <= 5 - x; y++) {
                    for (int z = 1; z <= 5; z++) {
                        helper.getLevel().setBlock(fixture.pos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                    }
                }
            }
            BlockPos source = fixture.pos(1, 5, 3);
            helper.getLevel().setBlock(source, Blocks.WATER.defaultBlockState(), 2);
            for (int pass = 0; pass < 12; pass++) fixture.tickWater(source);
            helper.assertFalse(helper.getLevel().getFluidState(fixture.pos(5, 1, 3)).isEmpty(),
                    "Native spreading reaches the bottom of the descending stone slope");
            helper.getLevel().setBlock(source, Blocks.AIR.defaultBlockState(), 3);
            for (int pass = 0; pass < 40; pass++) fixture.tickWater(source);
            for (BlockPos pos : BlockPos.betweenClosed(fixture.pos(1, 1, 1), fixture.pos(5, 5, 5))) {
                helper.assertTrue(helper.getLevel().getFluidState(pos).isEmpty(),
                        "All downhill water drains once the original high source is removed");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "fluid_fall")
    public static void genuineWaterAndLavaSourcesStillFallWithFiniteDecay(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            BlockPos target = fixture.pos(3, 4, 3);
            var changes = TemporaryFluidChanges.get(helper.getLevel());
            for (boolean water : new boolean[]{true, false}) {
                fixture.clear();
                helper.getLevel().setBlock(target.below(2), Blocks.STONE.defaultBlockState(), 2);
                var fluid = water ? Fluids.WATER : Fluids.LAVA;
                helper.getLevel().setBlock(target, fluid.defaultFluidState().createLegacyBlock(), 2);
                fluid.tick(helper.getLevel(), target, helper.getLevel().getFluidState(target));
                helper.assertTrue(helper.getLevel().getBlockState(target).isAir(),
                        "A genuine unsupported source still moves down one block");
                helper.assertTrue(helper.getLevel().getFluidState(target.below()).isSource(),
                        "Whole-source freefall preserves the full cell during its finite delay");
                var pending = changes.at(target.below());
                helper.assertTrue(pending != null && pending.water() == water,
                        "The moved source retains its liquid-specific decay task");
                helper.assertValueEqual(pending.due(), helper.getLevel().getGameTime()
                                + SourceFluidFall.decayDelay(water, helper.getLevel().dimensionType().ultraWarm()),
                        "Whole-source freefall keeps the existing decay delay");
                changes.schedule(target.below(), water, helper.getLevel().getGameTime());
                changes.tick(helper.getLevel());
                var state = helper.getLevel().getFluidState(target.below());
                helper.assertFalse(state.isSource(), "The moved source becomes flowing liquid at its deadline");
                state.tick(helper.getLevel(), target.below());
                helper.assertTrue(helper.getLevel().getFluidState(target.below()).isEmpty(),
                        "The expired isolated source drains through its native fluid tick");
            }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;

        @SuppressWarnings("removal")
        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            BlockPos position = pos(3, 5, 3);
            player.moveTo(position.getX() + .5, position.getY() + 2, position.getZ() + .5, 0, 0);
            player.setNoGravity(true);
            clear();
        }

        private BlockPos pos(int x, int y, int z) {
            return helper.absolutePos(new BlockPos(x, y, z));
        }

        private void clear() {
            var changes = TemporaryFluidChanges.get(helper.getLevel());
            for (BlockPos relative : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(6, 6, 6))) {
                BlockPos pos = helper.absolutePos(relative);
                changes.cancel(pos);
                boolean wall = relative.getY() == 0 || relative.getY() == 6
                        || relative.getX() == 0 || relative.getX() == 6
                        || relative.getZ() == 0 || relative.getZ() == 6;
                helper.getLevel().setBlock(pos, (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            }
        }

        private void tickWater(BlockPos source) {
            for (int y = 5; y >= 1; y--) {
                for (int x = 1; x <= 5; x++) {
                    for (int z = 1; z <= 5; z++) {
                        BlockPos pos = pos(x, y, z);
                        var state = helper.getLevel().getFluidState(pos);
                        helper.assertTrue(!state.isSource() || pos.equals(source),
                                "Downhill spreading cannot manufacture another source at " + new BlockPos(x, y, z));
                        if (!state.isEmpty()) state.tick(helper.getLevel(), pos);
                    }
                }
            }
        }

        @Override
        public void close() {
            clear();
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }
}
