package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Bat;
import io.github.ningmeng123.mite.entity.animal.SourceAquatic032;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real flying carrier AI and natural-spawn companion rollback. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class FlyingMountGameTests {
    private FlyingMountGameTests() {}

    @GameTest(template = "empty")
    public static void ordinaryAndVampireCarriersStayAirborneAndSteerToTheirRidersPreyFeet(GameTestHelper helper)
            throws ReflectiveOperationException {
        Method aiStep = Bat.class.getDeclaredMethod("customServerAiStep");
        aiStep.setAccessible(true);
        try (Arena arena = new Arena(helper)) {
            for (EntityType<? extends Bat> batType : batTypes()) {
                for (EntityType<? extends Mob> riderType : List.of(EntityType.SKELETON, EntityType.CREEPER)) {
                    Bat bat = arena.bat(batType);
                    Mob rider = arena.mob(riderType);
                    Player prey = helper.makeMockPlayer(GameType.SURVIVAL);
                    arena.entities.add(prey);
                    BlockPos ceiling = arena.position.above(5);
                    arena.set(ceiling, Blocks.STONE.defaultBlockState());
                    arena.set(ceiling.below(), Blocks.AIR.defaultBlockState());
                    double flyingY = ceiling.getY() - bat.getBbHeight() - .00625D;
                    bat.moveTo(arena.position.getX() + .5D, flyingY, arena.position.getZ() + .5D, 0F, 0F);
                    rider.moveTo(bat.position());
                    helper.assertTrue(rider.startRiding(bat, true), "The actual combat mob mounts its real bat carrier");
                    helper.assertTrue(SourceAquatic032.combatRider(bat) == rider,
                            "Skeleton and creeper passengers are recognized through their native combat families");
                    helper.assertTrue(SourceAquatic032.canHang(bat, ceiling), "The fixture has a valid solid hanging ceiling");
                    aiStep.invoke(bat);
                    helper.assertFalse(bat.isResting(), "A loaded combat carrier cannot select an otherwise valid hanging position");

                    if (bat instanceof R196Bat source) source.initializeHangingAt(ceiling);
                    else {
                        bat.setResting(true);
                        bat.getPersistentData().putInt("mite_bat_hang_y_032", ceiling.getY() + 5);
                    }
                    double previousY = bat.getY();
                    bat.setDeltaMovement(Vec3.ZERO);
                    bat.tick();
                    helper.assertFalse(bat.isResting(), "The native tick clears a loaded carrier's resting flag before movement");
                    helper.assertTrue(Math.abs(bat.getY() - previousY) < .00001D,
                            "A mounted tick does not snap the bat or rider to its old hanging height");

                    prey.moveTo(bat.getX() + 4D, bat.getY() - .8D, bat.getZ(), 0F, 0F);
                    rider.setTarget(prey);
                    bat.setTarget(prey);
                    helper.assertTrue(prey.getY() < bat.getY() && prey.getEyeY() > bat.getY(),
                            "The prey's feet and eyes require opposite vertical steering");
                    bat.setDeltaMovement(Vec3.ZERO);
                    aiStep.invoke(bat);
                    helper.assertTrue(bat.getDeltaMovement().x > 0D && bat.getDeltaMovement().y < 0D,
                            "A combat carrier approaches the rider's prey feet through its real server AI step");
                    rider.stopRiding();
                    bat.setDeltaMovement(Vec3.ZERO);
                    aiStep.invoke(bat);
                    helper.assertTrue(bat.getDeltaMovement().y > 0D,
                            "The same bat uses its ordinary eye-height pursuit after its rider dismounts");
                    rider.discard();
                    bat.discard();
                    prey.discard();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalFinalizationCommitsEnlistedBatVehiclesAndTheirRidersExactlyOnce(GameTestHelper helper) {
        try (Arena arena = new Arena(helper)) {
            for (EntityType<? extends Bat> batType : batTypes()) {
                Mob owner = arena.mob(EntityType.CREEPER);
                try (MountTrace trace = new MountTrace(arena, owner, batType, Failure.NONE)) {
                    var result = R196MobSpawning.finalizeNaturalMonster(arena.level, owner, null, 1);
                    helper.assertTrue(trace.enlisted, "Finalization enlists the fresh bat in its owner's actual natural transaction");
                    helper.assertValueEqual(result.monsters(), 1, "The one hostile rider spends one monster place");
                    helper.assertTrue(trace.bat != null && trace.bat.isAddedToLevel() && owner.isAddedToLevel(),
                            "Both the vehicle and its rider are registered through the transaction");
                    helper.assertTrue(owner.getVehicle() == trace.bat && trace.bat.getFirstPassenger() == owner,
                            "The committed natural entities retain their mounted relationship");
                    helper.assertValueEqual(trace.batJoins, 1, "The enlisted bat vehicle enters the world exactly once");
                    helper.assertValueEqual(trace.ownerJoins, 1, "The hostile owner enters the world exactly once");
                    helper.assertTrue(owner.getSpawnType() == MobSpawnType.NATURAL && trace.bat.getSpawnType() == MobSpawnType.NATURAL,
                            "The rider and companion retain their real natural-spawn finalization reasons");
                    helper.assertFalse(R196MobSpawning.enlistNaturalSpawn(owner, trace.bat),
                            "A finished transaction cannot retain or accept companion enlistments");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledDiscardedOrFailedNaturalRidersLeaveNoEnlistedBatOrphans(GameTestHelper helper) {
        try (Arena arena = new Arena(helper)) {
            for (Failure failure : Failure.values()) {
                if (failure == Failure.NONE) continue;
                Mob owner = arena.mob(EntityType.CREEPER);
                try (MountTrace trace = new MountTrace(arena, owner, EntityType.BAT, failure)) {
                    try {
                        var result = R196MobSpawning.finalizeNaturalMonster(arena.level, owner, null,
                                failure == Failure.NO_ALLOWANCE ? 0 : 1);
                        helper.assertTrue(failure != Failure.FINALIZE_EXCEPTION, "The injected finalization failure must propagate");
                        helper.assertValueEqual(result.monsters(), 0, "An unsuccessful mounted transaction consumes no monster place");
                    } catch (IllegalStateException exception) {
                        helper.assertTrue(failure == Failure.FINALIZE_EXCEPTION && exception == trace.expectedException,
                                "The finalization exception is propagated after rollback");
                    }
                    helper.assertTrue(trace.enlisted && trace.bat != null, "Each failed case really constructed and enlisted a vehicle");
                    helper.assertTrue(owner.isRemoved() && trace.bat.isRemoved(),
                            "Cancellation, discard, zero allowance and exceptions discard every owned entity");
                    helper.assertTrue(owner.getVehicle() == null && trace.bat.getPassengers().isEmpty(),
                            "Rollback leaves neither a live mount nor a detached riding relationship");
                    helper.assertFalse(R196MobSpawning.enlistNaturalSpawn(owner, trace.bat),
                            "Rollback always releases the owner's transaction enlistment");
                    if (failure == Failure.OWNER_JOIN_CANCEL) {
                        helper.assertValueEqual(trace.batJoins, 1, "The companion was already submitted before its rider's join was cancelled");
                        helper.assertValueEqual(trace.ownerJoins, 1, "The actual rider join callback was cancelled after dismounting");
                    }
                    if (failure == Failure.BAT_JOIN_CANCEL)
                        helper.assertValueEqual(trace.batJoins, 1, "The actual new vehicle's join event was cancelled");
                }
            }
        }
        helper.succeed();
    }

    private static List<EntityType<? extends Bat>> batTypes() {
        @SuppressWarnings("unchecked")
        EntityType<? extends Bat> vampire = (EntityType<? extends Bat>)(EntityType<?>)MiteMobs.type(MobKind.VAMPIRE_BAT);
        return List.of(EntityType.BAT, vampire);
    }

    private enum Failure { NONE, OWNER_FINALIZE_CANCEL, OWNER_FINALIZE_DISCARD, OWNER_JOIN_CANCEL,
        BAT_JOIN_CANCEL, NO_ALLOWANCE, FINALIZE_EXCEPTION }

    private static final class MountTrace implements AutoCloseable {
        final Mob owner;
        final Consumer<FinalizeSpawnEvent> finalizing;
        final Consumer<EntityJoinLevelEvent> joining;
        final IllegalStateException expectedException = new IllegalStateException("Expected enlisted vehicle finalization failure");
        Bat bat;
        boolean enlisted;
        int ownerJoins, batJoins;

        MountTrace(Arena arena, Mob owner, EntityType<? extends Bat> type, Failure failure) {
            this.owner = owner;
            finalizing = event -> {
                if (event.getEntity() != owner) return;
                bat = arena.bat(type);
                bat.moveTo(owner.position());
                enlisted = R196MobSpawning.enlistNaturalSpawn(owner, bat);
                EventHooks.finalizeMobSpawn(bat, arena.level, arena.level.getCurrentDifficultyAt(bat.blockPosition()), MobSpawnType.NATURAL, null);
                if (!owner.startRiding(bat, true)) throw new AssertionError("The natural owner must mount its enlisted vehicle");
                if (failure == Failure.OWNER_FINALIZE_CANCEL) {
                    owner.stopRiding();
                    event.setCanceled(true);
                    event.setSpawnCancelled(true);
                }
                if (failure == Failure.OWNER_FINALIZE_DISCARD) {
                    owner.stopRiding();
                    owner.discard();
                }
                if (failure == Failure.FINALIZE_EXCEPTION) {
                    arena.level.addFreshEntity(bat);
                    owner.stopRiding();
                    throw expectedException;
                }
            };
            joining = event -> {
                if (event.getEntity() == bat) {
                    batJoins++;
                    if (failure == Failure.BAT_JOIN_CANCEL) event.setCanceled(true);
                }
                if (event.getEntity() == owner) {
                    ownerJoins++;
                    if (failure == Failure.OWNER_JOIN_CANCEL) {
                        owner.stopRiding();
                        owner.discard();
                        event.setCanceled(true);
                    }
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FinalizeSpawnEvent.class, finalizing);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, joining);
        }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(finalizing);
            NeoForge.EVENT_BUS.unregister(joining);
            owner.discard();
            if (bat != null) bat.discard();
        }
    }

    private static final class Arena implements AutoCloseable {
        final ServerLevel level;
        final BlockPos position;
        final long time;
        final List<Entity> entities = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        Arena(GameTestHelper helper) {
            level = helper.getLevel();
            position = helper.absolutePos(new BlockPos(2, 3, 2));
            time = level.getDayTime();
            level.setDayTime(18000L);
        }

        Mob mob(EntityType<? extends Mob> type) {
            Mob mob = type.create(level);
            if (mob == null) throw new AssertionError("The registered flying rider creates");
            mob.moveTo(Vec3.atBottomCenterOf(position));
            mob.setNoAi(true);
            mob.setNoGravity(true);
            entities.add(mob);
            return mob;
        }

        Bat bat(EntityType<? extends Bat> type) { return (Bat)mob(type); }

        void set(BlockPos pos, BlockState state) {
            blocks.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, 2);
        }

        @Override public void close() {
            entities.forEach(Entity::discard);
            blocks.forEach((pos, state) -> level.setBlock(pos, state, 2));
            level.setDayTime(time);
        }
    }
}
