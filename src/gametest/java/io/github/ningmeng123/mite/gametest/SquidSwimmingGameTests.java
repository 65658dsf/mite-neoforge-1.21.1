package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.animal.SourceAquatic032;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual squid contact and native Player.travel verify severe-slowness swimming on the movement owner. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SquidSwimmingGameTests {
    private SquidSwimmingGameTests() {}

    @GameTest(template = "empty")
    public static void squidContactSinksTheLocalSwimmerDespiteRepeatedFluidJumpAndUpwardLook(GameTestHelper h) {
        try (Pool pool = new Pool(h)) {
            Player player = pool.swimmer(GameType.SURVIVAL);
            Squid squid = EntityType.SQUID.create(h.getLevel());
            h.assertTrue(squid != null, "A real squid is available");
            try {
                squid.moveTo(player.position());
                SourceAquatic032.touchPlayer(squid, player);
                var effect = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                h.assertTrue(effect != null && effect.getAmplifier() == 2 && effect.getDuration() == 200,
                        "Actual contact retains slowness III for two hundred ticks");
                player.setSwimming(true); player.setXRot(-80F);
                double initial = player.getY();
                for (int tick = 0; tick < 8; tick++) {
                    double previous = player.getY();
                    player.jumpInFluid(NeoForgeMod.WATER_TYPE.value());
                    player.travel(new Vec3(0D, 0D, .05D));
                    h.assertTrue(player.getY() < previous, "Native travel still descends while jump and upward swimming try to lift the player");
                }
                h.assertTrue(player.getY() < initial - .5D, "Severe slowness produces sustained sinking rather than one overwritten velocity change");
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                player.setDeltaMovement(Vec3.ZERO);
                double released = player.getY();
                for (int tick = 0; tick < 4; tick++) {
                    player.jumpInFluid(NeoForgeMod.WATER_TYPE.value());
                    player.travel(Vec3.ZERO);
                }
                h.assertTrue(player.getY() > released, "Removing the synchronized status restores the ordinary ability to swim upward");
            } finally { squid.discard(); player.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void weakSlownessAndSpeedCancellationKeepNormalBuoyancy(GameTestHelper h) {
        try (Pool pool = new Pool(h)) {
            Player player = pool.swimmer(GameType.SURVIVAL);
            try {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                double initial = player.getY();
                player.setDeltaMovement(.12D, .2D, .08D); player.travel(Vec3.ZERO);
                h.assertTrue(player.getY() > initial, "Slowness II stays below the severe drag threshold");
                h.assertTrue(player.getDeltaMovement().x > 0 && player.getDeltaMovement().z > 0,
                        "Swimming retains its ordinary horizontal velocity");
                player.removeAllEffects(); pool.reset(player);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
                initial = player.getY();
                player.setDeltaMovement(0D, .2D, 0D); player.travel(Vec3.ZERO);
                h.assertTrue(player.getY() > initial, "Speed I offsets enough of slowness III to avoid severe buoyancy loss");
                player.removeEffect(MobEffects.MOVEMENT_SPEED); pool.reset(player);
                initial = player.getY();
                player.setDeltaMovement(.12D, -.4D, .08D); player.travel(Vec3.ZERO);
                h.assertTrue(player.getY() <= initial - .39D, "The sink limit preserves existing faster downward movement");
                h.assertTrue(player.getDeltaMovement().x > 0D && player.getDeltaMovement().z > 0D,
                        "Even the severe drag changes only vertical buoyancy before normal water damping");
            } finally { player.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeFlyingNoGravityAndBoatPassengersDoNotAcquireForcedSinking(GameTestHelper h) {
        try (Pool pool = new Pool(h)) {
            for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
                Player protectedPlayer = pool.swimmer(mode);
                try { assertCanRise(h, protectedPlayer, "Creative and spectator swimming keep their ordinary movement despite the status"); }
                finally { protectedPlayer.discard(); }
            }
            Player player = pool.swimmer(GameType.SURVIVAL);
            Boat boat = EntityType.BOAT.create(h.getLevel());
            h.assertTrue(boat != null, "A real boat is available");
            try {
                player.setNoGravity(true);
                assertCanRise(h, player, "A no-gravity entity is not forced downward");
                player.setNoGravity(false); pool.reset(player); player.getAbilities().flying = true;
                assertCanRise(h, player, "Ability flight remains in control of vertical motion");
                player.getAbilities().flying = false; pool.reset(player);
                boat.moveTo(player.position());
                h.assertTrue(player.startRiding(boat, true), "The native mount operation gives the player a boat vehicle");
                player.removeAllEffects();
                Squid squid = EntityType.SQUID.create(h.getLevel());
                h.assertTrue(squid != null, "A squid is available beside the boat");
                try {
                    squid.moveTo(player.position()); SourceAquatic032.touchPlayer(squid, player);
                    h.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Actual squid contact keeps the existing boat protection");
                    assertCanRise(h, player, "A passenger is not assigned forced downward swimming movement");
                } finally { squid.discard(); }
            } finally { player.stopRiding(); player.discard(); boat.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void severeSlownessOnDryLandDoesNotCreateWaterDrag(GameTestHelper h) {
        try (Pool pool = new Pool(h)) {
            Player player = pool.swimmer(GameType.SURVIVAL);
            try {
                player.moveTo(pool.center.add(0D, 10D, 0D)); player.tick();
                player.setOnGround(false); player.setSwimming(false);
                h.assertFalse(player.isInWater(), "Native fluid state refresh observes the player above the pool");
                assertCanRise(h, player, "The sinking rule does not replace ordinary dry-land airborne movement");
            } finally { player.discard(); }
        }
        h.succeed();
    }

    private static void assertCanRise(GameTestHelper h, Player player, String reason) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
        player.setSwimming(false); player.setDeltaMovement(0D, .2D, 0D);
        double initial = player.getY();
        player.travel(Vec3.ZERO);
        // Mounted entities may leave movement to their vehicle. Their vertical velocity must
        // still remain positive rather than receiving the negative forced-swimming limit.
        h.assertTrue(player.isPassenger() ? player.getDeltaMovement().y >= 0D : player.getY() > initial, reason);
    }

    private static final class Pool implements AutoCloseable {
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final GameTestHelper h;
        final Vec3 center;
        Pool(GameTestHelper h) {
            this.h = h;
            BlockPos anchor = h.absolutePos(new BlockPos(2, 2, 2));
            center = Vec3.atBottomCenterOf(anchor.above(5));
            for (BlockPos mutable : BlockPos.betweenClosed(anchor.offset(-2, -1, -2), anchor.offset(2, 12, 2))) {
                BlockPos pos = mutable.immutable(); previous.put(pos, h.getLevel().getBlockState(pos));
                boolean wall = Math.abs(pos.getX() - anchor.getX()) == 2 || Math.abs(pos.getZ() - anchor.getZ()) == 2 || pos.getY() < anchor.getY();
                h.getLevel().setBlock(pos, wall ? Blocks.BEDROCK.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
            }
        }
        Player swimmer(GameType mode) {
            Player player = h.makeMockPlayer(mode); mode.updatePlayerAbilities(player.getAbilities());
            h.assertTrue(player.isLocalPlayer(), "The native player travel branch runs on the same logical movement owner as a client");
            reset(player);
            return player;
        }
        void reset(Player player) {
            player.moveTo(center); player.setDeltaMovement(Vec3.ZERO); player.setOnGround(false); player.setSwimming(false);
            player.tick(); player.setOnGround(false); player.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(player.isInWater(), "The native player tick refreshes water contact before travel is tested");
        }
        @Override public void close() { previous.forEach((pos, state) -> h.getLevel().setBlock(pos, state, 2)); }
    }
}
