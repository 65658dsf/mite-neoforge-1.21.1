package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.SkeletonFiringPositions;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real map routes and installed goal integration around opaque cover. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SkeletonFiringPositionGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    @GameTest(template = "empty")
    public static void installedBowGoalFindsAnExposedAngleAroundATreeTrunk(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.wall(2, 0, Blocks.OAK_LOG);
            h.assertFalse(SourceRangedAttackGoal.clearShot(f.skeleton, f.player), "The actual trunk blocks every initial body ray");
            Vec3 initial = f.skeleton.position();
            SourceRangedAttackGoal goal = f.skeleton.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal()).filter(SourceRangedAttackGoal.class::isInstance)
                    .map(SourceRangedAttackGoal.class::cast).findFirst().orElseThrow();
            h.assertTrue(goal.canUse(), "The skeleton's installed bow goal accepts the covered target");
            goal.start();
            for (int tick = 0; tick < 10; tick++) goal.tick();
            Path path = f.skeleton.getNavigation().getPath();
            assertExposedEndpoint(h, f, path);
            h.assertTrue(Math.abs(path.getEndNode().z - f.origin.getZ()) >= 2,
                    "The installed goal actually replaces direct pursuit with a lateral firing angle");
            h.assertTrue(f.skeleton.position().equals(initial), "Planning installs a walking route without teleporting the skeleton");
            f.skeleton.setPos(initial.add(1D, 0D, 0D));
            h.assertFalse(SourceRangedAttackGoal.clearShot(f.skeleton, f.player),
                    "The one-block intermediate position in front of the trunk still needs the existing detour");
            h.assertFalse(f.skeleton.getNavigation().isDone(), "The selected detour has not yet reached its firing position");
            for (int tick = 0; tick < 25; tick++) goal.tick();
            h.assertTrue(f.skeleton.getNavigation().getPath() == path && !f.skeleton.getNavigation().isDone(),
                    "Passing the twenty-tick replan interval preserves the unfinished detour instead of replacing it from a new sampling origin");
            for (int y = 0; y <= 4; y++) h.assertTrue(h.getLevel().getBlockState(f.origin.offset(2, y, 0)).is(Blocks.OAK_LOG),
                    "Firing-angle planning preserves every trunk block");
            goal.stop();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void finiteStoneWallProducesARealReachableLateralPath(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.wall(2, 2, Blocks.STONE);
            h.assertFalse(SourceRangedAttackGoal.clearShot(f.skeleton, f.player), "Stone cover initially hides the target");
            Path path = SkeletonFiringPositions.find(f.skeleton, f.player, 900D);
            assertExposedEndpoint(h, f, path);
            boolean sideways = false;
            for (int i = 0; i < path.getNodeCount(); i++) {
                var node = path.getNode(i);
                if (Math.abs(node.z - f.origin.getZ()) >= 3) sideways = true;
                h.assertTrue(!(node.x == f.origin.getX() + 2 && Math.abs(node.z - f.origin.getZ()) <= 2),
                        "The real native path never traverses a stone wall cell");
            }
            h.assertTrue(sideways, "Path nodes really step beyond the wall's width to reveal the target");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unreachablePositionsBehindAClosedWallAreNeverAccepted(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.wall(2, 12, Blocks.BEDROCK);
            h.assertFalse(SourceRangedAttackGoal.clearShot(f.skeleton, f.player), "The full-width wall blocks the starting ray");
            h.assertTrue(SkeletonFiringPositions.find(f.skeleton, f.player, 900D) == null,
                    "Visible samples on the opposite side are rejected when native navigation cannot actually reach them");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void openAirAndWaterAreNotInventedAsFiringPlatforms(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.wall(2, 0, Blocks.OAK_LOG);
            for (int x = -8; x <= 10; x++) for (int z = -8; z <= 8; z++) {
                if (x == 0 && z == 0 || x == 8 && z == 0) continue;
                h.getLevel().setBlock(f.origin.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 2);
            }
            h.assertTrue(SkeletonFiringPositions.find(f.skeleton, f.player, 900D) == null,
                    "Unobstructed rays over a pit do not turn unsupported air into a valid firing position");
            for (int x = -8; x <= 10; x++) for (int z = -8; z <= 8; z++) {
                if (x == 0 && z == 0 || x == 8 && z == 0) continue;
                h.getLevel().setBlock(f.origin.offset(x, -1, z), Blocks.WATER.defaultBlockState(), 2);
            }
            h.assertTrue(SkeletonFiringPositions.find(f.skeleton, f.player, 900D) == null,
                    "Water surfaces do not provide solid firing support either");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void transparentLeavesDoNotTriggerUnnecessaryRepositioning(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (int y = 0; y <= 4; y++) h.getLevel().setBlock(f.origin.offset(2, y, 0),
                    Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), 2);
            h.assertTrue(SourceRangedAttackGoal.clearShot(f.skeleton, f.player), "Skeleton arrows keep their existing leaf transparency");
            h.assertTrue(SkeletonFiringPositions.find(f.skeleton, f.player, 900D) == null,
                    "A target already exposed through leaves needs no firing-angle detour");
        }
        h.succeed();
    }

    private static void assertExposedEndpoint(GameTestHelper h, Fixture f, Path path) {
        h.assertTrue(path != null && path.canReach() && path.getNodeCount() > 1,
                "A complete real native route reaches a different firing position");
        Vec3 end = path.getEntityPosAtNode(f.skeleton, path.getNodeCount() - 1);
        Vec3 eyes = end.add(0D, f.skeleton.getEyeHeight(), 0D);
        h.assertTrue(SourceRangedAttackGoal.clearShot(f.skeleton, eyes, f.player.getEyePosition())
                        || SourceRangedAttackGoal.clearShot(f.skeleton, eyes,
                                f.player.position().add(0D, f.player.getBbHeight() * .5D, 0D)),
                "The actual endpoint has a clear firing ray to the player's exposed body");
        h.assertTrue(h.getLevel().getBlockState(BlockPos.containing(end).below()).is(Blocks.BEDROCK),
                "The chosen real endpoint stands on the fixture's solid ground");
    }

    private static final class Fixture implements AutoCloseable {
        final BlockPos origin;
        final AbstractSkeleton skeleton;
        final Player player;
        final GameTestHelper helper;

        Fixture(GameTestHelper h) {
            helper = h;
            origin = new BlockPos(190_000 + IDS.getAndIncrement() * 64, h.absolutePos(BlockPos.ZERO).getY() + 12, 190_000);
            for (int x = -8; x <= 10; x++) for (int z = -8; z <= 8; z++) {
                for (int y = -4; y <= 6; y++) h.getLevel().setBlock(origin.offset(x, y, z),
                        (y == -1 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            }
            skeleton = EntityType.SKELETON.create(h.getLevel());
            skeleton.moveTo(Vec3.atBottomCenterOf(origin));
            skeleton.setNoAi(true);
            skeleton.setNoGravity(true);
            skeleton.setOnGround(true);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            h.getLevel().addFreshEntity(skeleton);
            player = h.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(origin.east(8)));
            skeleton.setTarget(player);
        }

        void wall(int x, int halfWidth, Block block) {
            for (int z = -halfWidth; z <= halfWidth; z++) for (int y = 0; y <= 4; y++)
                helper.getLevel().setBlock(origin.offset(x, y, z), block.defaultBlockState(), 2);
        }

        @Override public void close() {
            skeleton.getNavigation().stop();
            skeleton.discard();
            player.discard();
        }
    }
}
