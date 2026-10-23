package io.github.ningmeng123.mite.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native travel and entity collisions verify player-only barrier jumping. @author NingMeng123 */
@GameTestHolder("mite_barrier")
@PrefixGameTestTemplate(false)
public final class PlayerBarrierJumpGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final Vec3 WALK_INPUT = new Vec3(1D, 0D, 0D);

    @GameTest(template = "empty")
    public static void ordinaryPlayerJumpClearsFencesWallsAndClosedGate(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            for (BlockState state : barriers()) {
                f.barrier(state);
                f.reset(f.player);
                double peak = jumpAndTravel(f.player);
                h.assertTrue(peak > f.origin.getY() + 1D && peak < f.origin.getY() + 1.3D,
                        "The actual native jump keeps its ordinary height: " + state);
                h.assertTrue(f.player.getBoundingBox().minX > f.obstacle.getX() + 1D,
                        "A normal player jump crosses the complete barrier through native travel/Entity.move: " + state);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void walkingPlayerAndJumpingMonsterStillCollide(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            for (BlockState state : barriers()) {
                f.barrier(state);
                f.reset(f.player);
                travel(f.player);
                h.assertTrue(f.player.horizontalCollision && f.player.getX() < f.obstacle.getX() + .5D,
                        "A grounded player cannot walk through the barrier: " + state);
                h.assertTrue(Math.abs(f.player.getY() - f.origin.getY()) < 1E-6D,
                        "Walking alone does not climb the barrier: " + state);
                f.reset(f.zombie);
                jumpAndTravel(f.zombie);
                h.assertTrue(f.zombie.horizontalCollision && f.zombie.getX() < f.obstacle.getX() + .5D,
                        "The same native jump leaves a monster behind the barrier: " + state);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void animalsEmptyContextAndSelectionKeepTheOriginalHeight(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            BlockState state = barriers()[0];
            f.barrier(state);
            var cow = EntityType.COW.create(f.level);
            try {
                h.assertValueEqual(state.getCollisionShape(f.level, f.obstacle).max(Direction.Axis.Y), 1.5D,
                        "The context-free collision query retains its original one-and-a-half block height");
                h.assertValueEqual(state.getCollisionShape(f.level, f.obstacle, CollisionContext.empty()).max(Direction.Axis.Y), 1.5D,
                        "An empty collision context retains the original height");
                h.assertValueEqual(state.getCollisionShape(f.level, f.obstacle, CollisionContext.of(cow)).max(Direction.Axis.Y), 1.5D,
                        "Animals retain the same tall collision as monsters");
                h.assertValueEqual(state.getShape(f.level, f.obstacle, CollisionContext.of(f.player)).toAabbs(),
                        state.getShape(f.level, f.obstacle, CollisionContext.empty()).toAabbs(),
                        "Player selection geometry remains identical to the normal visible block outline");
            } finally {
                cow.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void openGateAndOrdinaryStoneRetainNativeMovement(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            BlockState open = gate().setValue(FenceGateBlock.OPEN, true);
            f.barrier(open);
            for (LivingEntity entity : new LivingEntity[]{f.player, f.zombie}) {
                f.reset(entity);
                h.assertTrue(open.getCollisionShape(f.level, f.obstacle, CollisionContext.of(entity)).isEmpty(),
                        "An open gate has no collision for either players or monsters");
                double before = entity.getX();
                entity.move(MoverType.SELF, new Vec3(3D, 0D, 0D));
                h.assertTrue(Math.abs(entity.getX() - before - 3D) < 1E-6D,
                        "The actual move passes freely through an open gate");
            }
            f.barrier(Blocks.STONE.defaultBlockState());
            f.set(f.obstacle.above(), Blocks.STONE.defaultBlockState());
            f.reset(f.player);
            jumpAndTravel(f.player);
            h.assertTrue(f.player.horizontalCollision && f.player.getX() < f.obstacle.getX(),
                    "A normal two-block stone wall still stops an actual player jump");
            h.assertValueEqual(Blocks.STONE.defaultBlockState()
                            .getCollisionShape(f.level, f.obstacle, CollisionContext.of(f.player)).bounds(),
                    Blocks.STONE.defaultBlockState().getCollisionShape(f.level, f.obstacle).bounds(),
                    "Ordinary stone retains its complete native collision box");
        }
        h.succeed();
    }

    private static BlockState[] barriers() {
        return new BlockState[]{
                Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true),
                Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true),
                Blocks.COBBLESTONE_WALL.defaultBlockState().setValue(WallBlock.NORTH_WALL, WallSide.LOW)
                        .setValue(WallBlock.SOUTH_WALL, WallSide.LOW),
                gate()
        };
    }

    private static BlockState gate() {
        return Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST);
    }

    private static double jumpAndTravel(LivingEntity entity) {
        // Give both actors ordinary walking momentum, then let native travel own gravity and collision.
        entity.setDeltaMovement(.2D, 0D, 0D);
        entity.jumpFromGround();
        entity.setOnGround(false);
        double peak = entity.getY();
        for (int tick = 0; tick < 24; tick++) {
            entity.travel(WALK_INPUT);
            peak = Math.max(peak, entity.getY());
        }
        return peak;
    }

    private static void travel(LivingEntity entity) {
        for (int tick = 0; tick < 24; tick++) entity.travel(WALK_INPUT);
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final BlockPos origin;
        final BlockPos obstacle;
        final Player player;
        final Zombie zombie;
        final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();

        Fixture(GameTestHelper h) {
            level = h.getLevel();
            origin = new BlockPos(240_000 + IDS.getAndIncrement() * 32, h.absolutePos(BlockPos.ZERO).getY() + 4, 240_000);
            obstacle = origin.east();
            for (int x = -1; x <= 8; x++) for (int z = -1; z <= 1; z++) {
                set(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 4; y++) set(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            player = h.makeMockPlayer(GameType.SURVIVAL);
            zombie = EntityType.ZOMBIE.create(level);
        }

        void barrier(BlockState state) { set(obstacle, state); }

        void reset(LivingEntity entity) {
            entity.moveTo(origin.getX() + .2D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setOnGround(true);
            entity.setSpeed(.1F);
            entity.fallDistance = 0F;
        }

        void set(BlockPos pos, BlockState state) {
            saved.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlock(pos, state, 2);
        }

        @Override public void close() {
            player.discard();
            zombie.discard();
            saved.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
    }
}
