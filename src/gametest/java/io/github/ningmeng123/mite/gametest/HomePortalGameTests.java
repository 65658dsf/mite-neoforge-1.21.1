package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.portal.MitePortalFrame;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Surface-only home landing through the actual ordinary portal block. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class HomePortalGameTests {
    private HomePortalGameTests() {}

    @GameTest(template = "empty")
    public static void homeGateSkipsWetSpawnSurfaceAndFindsNearbyDrySurfaceInsteadOfACave(GameTestHelper helper) {
        try (HomeFixture fixture = new HomeFixture(helper)) {
            BlockPos drySurface = fixture.spawn.east();
            fixture.level.setBlock(drySurface.below(), Blocks.STONE.defaultBlockState(), 2);
            DimensionTransition destination = fixture.destination();
            helper.assertTrue(destination != null && destination.newLevel() == fixture.level, "Home portal keeps its same-dimension route");
            helper.assertValueEqual(destination.pos(), Vec3.atBottomCenterOf(drySurface), "An unsafe centre column is skipped in favour of the neighbouring surface, not its underground cave");
            helper.assertTrue(fixture.level.getBlockState(fixture.spawn.below()).is(Blocks.WATER), "Home landing does not drain or carve the unsafe surface");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void homeGateFailsWithoutCarvingWhenOnlyUndergroundSpaceIsSafe(GameTestHelper helper) {
        try (HomeFixture fixture = new HomeFixture(helper)) {
            helper.assertTrue(fixture.destination() == null, "A dry cave below the spawn does not replace an unavailable surface landing");
            helper.assertTrue(fixture.level.getBlockState(fixture.spawn.below()).is(Blocks.WATER), "Failure leaves the surface intact");
            helper.assertTrue(fixture.level.getBlockState(fixture.spawn.below(2)).is(Blocks.STONE), "Failure leaves the cave roof intact");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void homeGateKeepsTheSpawnColumnWhenItsSurfaceIsSafe(GameTestHelper helper) {
        try (HomeFixture fixture = new HomeFixture(helper)) {
            fixture.level.setBlock(fixture.spawn.below(), Blocks.STONE.defaultBlockState(), 2);
            DimensionTransition destination = fixture.destination();
            helper.assertTrue(destination != null, "A dry spawn surface accepts the player");
            helper.assertValueEqual(destination.pos(), Vec3.atBottomCenterOf(fixture.spawn), "The closest safe surface remains the preferred home destination");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void enteringHomeGateReturnsPlayerToUnloadedSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WritableLevelData levelData = (WritableLevelData)level.getLevelData();
        BlockPos oldSpawn = levelData.getSpawnPos();
        float oldAngle = levelData.getSpawnAngle();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos entrance = placeEntrance(level, new BlockPos(origin.getX(), 80, origin.getZ())).bottomLeft();
        int surfaceY = level.getChunkSource().getGenerator().getSpawnHeight(level);
        BlockPos spawn = new BlockPos(24_000_008, surfaceY, 24_000_008);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(Vec3.atBottomCenterOf(entrance));
            // Updating only the stored spawn simulates disabled spawn chunk loading.
            levelData.setSpawn(spawn, oldAngle);
            helper.assertFalse(level.hasChunkAt(spawn), "Spawn must start outside loaded chunks");
            level.getBlockState(entrance).entityInside(level, entrance, player);
            player.baseTick();
            helper.assertValueEqual(player.position(), Vec3.atBottomCenterOf(spawn), "Entering the home portal loads the spawn surface and teleports the player there");
            helper.assertTrue(player.serverLevel() == level, "The home portal keeps the player in the Overworld");
        } finally {
            levelData.setSpawn(oldSpawn, oldAngle);
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static MitePortalFrame placeEntrance(ServerLevel level, BlockPos bottomLeft) {
        MitePortalFrame frame = new MitePortalFrame(bottomLeft, Direction.Axis.X);
        for (int x = -1; x <= 2; x++) for (int y = -1; y <= 3; y++) {
            level.setBlock(frame.at(x, y), x == -1 || x == 2 || y == -1 || y == 3
                    ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        PortalShape.findEmptyPortalShape(level, frame.bottomLeft(), frame.axis()).orElseThrow().createPortalBlocks();
        return frame;
    }

    private static final class HomeFixture implements AutoCloseable {
        final ServerLevel level;
        final ServerPlayer player;
        final BlockPos spawn;
        final MitePortalFrame entrance;
        final BlockPos oldSpawn;
        final float oldAngle;

        HomeFixture(GameTestHelper helper) {
            level = helper.getLevel();
            oldSpawn = level.getSharedSpawnPos();
            oldAngle = level.getSharedSpawnAngle();
            BlockPos testOrigin = helper.absolutePos(BlockPos.ZERO);
            spawn = new BlockPos(12_000_000 + testOrigin.getX(), 91, testOrigin.getZ());
            // Every surface column within the home search is unsafe except a neighbour enabled by a test.
            // The centre has a dry, two-block-high cave which the old full-column search accepted first.
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(spawn.offset(dx, -1, dz), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
            }
            level.setBlock(spawn.below(), Blocks.WATER.defaultBlockState(), 2);
            level.setBlock(spawn.below(2), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(spawn.below(3), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(spawn.below(4), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(spawn.below(5), Blocks.STONE.defaultBlockState(), 2);
            entrance = placeEntrance(level, spawn.west(32).below(10));
            // Resolving a destination needs a server player, but never logs in or executes the transition.
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "home-portal-test"));
            player.setPos(Vec3.atBottomCenterOf(entrance.bottomLeft()));
            ((WritableLevelData)level.getLevelData()).setSpawn(spawn, oldAngle);
        }

        DimensionTransition destination() {
            return ((NetherPortalBlock)Blocks.NETHER_PORTAL).getPortalDestination(level, player, entrance.bottomLeft());
        }

        @Override public void close() {
            ((WritableLevelData)level.getLevelData()).setSpawn(oldSpawn, oldAngle);
        }
    }
}
