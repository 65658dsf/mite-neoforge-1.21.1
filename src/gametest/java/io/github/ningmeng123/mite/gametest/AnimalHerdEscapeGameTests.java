package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.animal.AnimalCommon032Goals;
import io.github.ningmeng123.mite.entity.animal.AnimalEscapePath;
import io.github.ningmeng123.mite.entity.animal.R196AnimalAi032;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real damage propagation and native navigation regressions for herd escape. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AnimalHerdEscapeGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    @GameTest(template = "ranged_027")
    public static void actualPlayerHitScaresMixedHerdAndStopsBreedingImmediately(GameTestHelper h) {
        try (Fixture f = new Fixture(h, false)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            List<Animal> herd = List.of(cow, f.mob(EntityType.MOOSHROOM, 2, 0), f.mob(EntityType.SHEEP, 3, 0),
                    f.mob(EntityType.PIG, 4, 0), f.mob(EntityType.CHICKEN, 5, 0), f.mob(EntityType.HORSE, 6, 0),
                    f.mob(EntityType.DONKEY, 7, 0), f.mob(EntityType.MULE, 8, 0));
            for (var animal : herd) animal.setInLove(null);
            h.assertTrue(cow.hurt(cow.damageSources().playerAttack(f.player), 1F), "Actual player damage is accepted");
            for (var animal : herd) {
                h.assertTrue(R196AnimalAi032.lastAttacker(animal) == f.player,
                        "Every nearby livestock species remembers the same attacking player: " + animal.getType());
                h.assertTrue(R196AnimalAi032.spooked(animal) && R196AnimalAi032.state(animal).getBoolean("decided_to_flee")
                                && !animal.isInLove(), "Fear and cancelled breeding are immediate, before an entity tick");
            }
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void herdAlarmUsesSixteenBlockDistanceAndExcludesPetsAndUndead(GameTestHelper h) {
        try (Fixture f = new Fixture(h, false)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            var boundary = f.mob(EntityType.SHEEP, 16, 0);
            var outside = f.mob(EntityType.PIG, 16.01D, 0);
            var corner = f.mob(EntityType.CHICKEN, 12, 12);
            var wolf = f.mob(EntityType.WOLF, 3, 2);
            var pet = f.mob(EntityType.WOLF, 4, 2); pet.setTame(true, false);
            var cat = f.mob(EntityType.CAT, 5, 2); cat.setTame(true, false);
            var zombieHorse = f.mob(EntityType.ZOMBIE_HORSE, 6, 2);
            var skeletonHorse = f.mob(EntityType.SKELETON_HORSE, 7, 2);
            var rabbit = f.mob(EntityType.RABBIT, 8, 2);
            cow.hurt(cow.damageSources().playerAttack(f.player), 1F);
            h.assertTrue(R196AnimalAi032.lastAttacker(boundary) == f.player && R196AnimalAi032.spooked(boundary),
                    "A peer exactly 16 blocks from the victim hears the alarm");
            for (var ignored : List.of(outside, corner, wolf, pet, cat, zombieHorse, skeletonHorse, rabbit))
                h.assertTrue(!R196AnimalAi032.isSpookedOrFleeing(ignored) && R196AnimalAi032.lastAttacker(ignored) == null,
                        "Peers outside the spherical radius, pets, predators and undead are excluded: " + ignored.getType());
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void zeroedAndCancelledPlayerDamageCannotAlarmHerd(GameTestHelper h) {
        try (Fixture f = new Fixture(h, false)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            var sheep = f.mob(EntityType.SHEEP, 2, 0); cow.setInLove(null); sheep.setInLove(null);
            float health = cow.getHealth();
            int[] interventions = new int[2];
            Consumer<LivingDamageEvent.Pre> zero = event -> {
                if (event.getEntity() == cow) { interventions[0]++; event.setNewDamage(0F); }
            };
            NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Pre.class, zero);
            try { cow.hurt(cow.damageSources().playerAttack(f.player), 1F); }
            finally { NeoForge.EVENT_BUS.unregister(zero); }
            cow.invulnerableTime = 0;
            Consumer<LivingIncomingDamageEvent> cancel = event -> {
                if (event.getEntity() == cow) { interventions[1]++; event.setCanceled(true); }
            };
            NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, cancel);
            try { cow.hurt(cow.damageSources().playerAttack(f.player), 1F); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(interventions[0] == 1 && interventions[1] == 1, "Both real damage interventions ran");
            h.assertTrue(cow.getHealth() == health && cow.isInLove() && sheep.isInLove(),
                    "Rejected damage preserves health and breeding");
            h.assertTrue(!R196AnimalAi032.isSpookedOrFleeing(cow) && !R196AnimalAi032.isSpookedOrFleeing(sheep)
                            && R196AnimalAi032.lastAttacker(cow) == null && R196AnimalAi032.lastAttacker(sheep) == null,
                    "Zero final damage and cancelled attacks create no fear in the victim or its herd");
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void playerArrowAlarmRemembersShooterInsteadOfProjectile(GameTestHelper h) {
        try (Fixture f = new Fixture(h, false)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            var sheep = f.mob(EntityType.SHEEP, 2, 0);
            Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel()); arrow.setOwner(f.player);
            h.assertTrue(cow.hurt(cow.damageSources().arrow(arrow, f.player), 1F), "Real projectile damage is accepted");
            h.assertTrue(R196AnimalAi032.lastAttacker(cow) == f.player && R196AnimalAi032.lastAttacker(sheep) == f.player
                            && R196AnimalAi032.spooked(sheep), "The herd escapes from the player who fired the arrow");
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void fatalPlayerHitStillAlarmsSurvivingHerd(GameTestHelper h) {
        try (Fixture f = new Fixture(h, false)) {
            var victim = f.mob(EntityType.COW, 0, 0);
            var survivor = f.mob(EntityType.SHEEP, 2, 0); survivor.setInLove(null);
            victim.setHealth(1F);
            h.assertTrue(victim.hurt(victim.damageSources().playerAttack(f.player), 2F) && !victim.isAlive(),
                    "The player hit is fatal to the animal that emits the alarm");
            h.assertTrue(survivor.isAlive() && R196AnimalAi032.lastAttacker(survivor) == f.player
                            && R196AnimalAi032.spooked(survivor) && !survivor.isInLove(),
                    "A fatal attack still warns living peers immediately and interrupts breeding");
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void alarmDoesNotGiveModernAnimalsUnmanagedFearMemory(GameTestHelper h) {
        try (Fixture f = new Fixture(h, false)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            var modern = List.of(f.mob(EntityType.LLAMA, 2, 0), f.mob(EntityType.TRADER_LLAMA, 3, 0),
                    f.mob(EntityType.CAMEL, 4, 0), f.mob(EntityType.RABBIT, 5, 0), f.mob(EntityType.GOAT, 6, 0));
            for (var animal : modern)
                h.assertTrue(!R196AnimalAi032.herdAnimal(animal) && !animal.getPersistentData().contains(R196AnimalAi032.STATE),
                        "An unmanaged modern species begins without the custom fear state: " + animal.getType());
            cow.hurt(cow.damageSources().playerAttack(f.player), 1F);
            for (var animal : modern)
                h.assertTrue(!animal.getPersistentData().contains(R196AnimalAi032.STATE),
                        "Alarm propagation cannot write fear timers into a species whose custom timers never tick: " + animal.getType());
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void frightenedAnimalImmediatelyStopsSeekingAvailableFood(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            Livestock032.needs(cow, new LivestockRules032.Needs(.4F, .1F, 1F));
            ItemEntity wheat = new ItemEntity(h.getLevel(), cow.getX() + 3D, cow.getY(), cow.getZ(), new ItemStack(Items.WHEAT));
            wheat.setNoPickUpDelay(); wheat.setNoGravity(true); h.getLevel().addFreshEntity(wheat);
            try {
                var food = new AnimalCommon032Goals.FoodItems(cow);
                long seed = 0L; while (new Random(seed).nextInt(40) != 0) seed++;
                cow.getRandom().setSeed(seed);
                h.assertTrue(food.canUse(), "The hungry animal can seek an actual nearby edible dropped item");
                food.start();
                h.assertTrue(food.canContinueToUse(), "The active food goal has a real reachable item target before the alarm");
                cow.hurt(cow.damageSources().playerAttack(f.player), 1F);
                h.assertTrue(wheat.isAlive() && !food.canContinueToUse() && !food.canUse(),
                        "Fear immediately interrupts the active food goal and blocks reactivation while food remains present");
                food.stop();
            } finally { wheat.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void safeDistanceClearsFleeDecisionAndRemainingPanicCanFinish(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            cow.hurt(cow.damageSources().playerAttack(f.player), 1F);
            var flee = new AnimalCommon032Goals.Flee(cow, 1.6F, 0F); flee.start();
            // The short herd alarm has expired, leaving only the goal's remaining panic countdown.
            R196AnimalAi032.state(cow).putLong("spooked_until", h.getLevel().getGameTime());
            R196AnimalAi032.state(cow).putInt("panic_countdown", 3);
            f.player.setPos(cow.getX() - 40.01D, cow.getY(), cow.getZ());
            h.assertTrue(flee.canContinueToUse(), "The animal can finish a remaining short panic after reaching safety");
            h.assertTrue(!R196AnimalAi032.state(cow).getBoolean("decided_to_flee")
                            && !R196AnimalAi032.state(cow).getBoolean("fleeing"),
                    "More than 40 blocks of separation clears both persistent flee flags");
            for (int tick = 0; tick < 3; tick++) flee.tick();
            h.assertTrue(!flee.canContinueToUse() && !R196AnimalAi032.isSpookedOrFleeing(cow),
                    "Panic ending at a safe distance permits the escape goal to stop instead of running indefinitely");
            flee.stop();
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void chainedHerdAlarmCopiesThreatAndRemainingMemoryWithoutExtendingDeadline(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var victim = f.mob(EntityType.COW, 0, 0);
            var relay = f.mob(EntityType.HORSE, 15, 0);
            var downstream = f.mob(EntityType.COW, 21, 0);
            var finalPeer = f.mob(EntityType.SHEEP, 25, 0);
            victim.hurt(victim.damageSources().playerAttack(f.player), 1F);
            long deadline = R196AnimalAi032.state(relay).getLong("herd_alarm_until");
            h.assertTrue(R196AnimalAi032.spooked(relay) && deadline > h.getLevel().getGameTime()
                            && R196AnimalAi032.lastAttacker(downstream) == null && R196AnimalAi032.lastAttacker(finalPeer) == null,
                    "Only the first relay lies inside the directly attacked animal's 16-block alarm radius");
            R196AnimalAi032.state(relay).putInt("attacker_memory", 123);
            relay.setNoAi(false); relay.tickCount = Math.floorMod(-relay.getId() * 47, 20);
            R196AnimalAi032.tickSource(relay);
            h.assertTrue(R196AnimalAi032.lastAttacker(downstream) == f.player
                            && R196AnimalAi032.state(downstream).getInt("attacker_memory") == 122
                            && R196AnimalAi032.state(downstream).getLong("herd_alarm_until") == deadline
                            && R196AnimalAi032.state(downstream).getLong("spooked_until") == deadline,
                    "A reachable relay copies the original player, remaining memory and absolute alarm deadline");
            downstream.setNoAi(false); downstream.tickCount = Math.floorMod(-downstream.getId() * 47, 20);
            R196AnimalAi032.tickSource(downstream);
            h.assertTrue(R196AnimalAi032.lastAttacker(finalPeer) == f.player
                            && R196AnimalAi032.state(finalPeer).getInt("attacker_memory") == 121
                            && R196AnimalAi032.state(finalPeer).getLong("herd_alarm_until") == deadline
                            && R196AnimalAi032.state(finalPeer).getLong("spooked_until") == deadline,
                    "A second relay preserves the same deadline without resetting fear or attacker memory");
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void nativeEscapePathImmediatelyGainsDistanceOnOpenGround(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            Path path = AnimalEscapePath.find(cow, f.player);
            assertAway(h, cow.position(), f.player.position(), path);
            h.assertTrue(nodePosition(path, Math.min(4, path.getNodeCount() - 1)).x > cow.getX() + 1D,
                    "The first few native path nodes move along the open direction away from the player");
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void nativeEscapePathFindsSafeRouteAroundSolidWall(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            for (int z = -4; z <= 4; z++) for (int y = 0; y < 3; y++)
                h.getLevel().setBlock(f.origin.offset(3, y, z), Blocks.STONE.defaultBlockState(), 2);
            Path path = AnimalEscapePath.find(cow, f.player);
            assertAway(h, cow.position(), f.player.position(), path);
            boolean goesAround = false;
            for (int i = 0; i < path.getNodeCount(); i++) {
                var node = path.getNode(i);
                h.assertTrue(!h.getLevel().getBlockState(node.asBlockPos()).blocksMotion(),
                        "Native escape nodes remain walkable instead of selecting a target through the wall");
                if (Math.abs(node.z - f.origin.getZ()) > 4) goesAround = true;
            }
            h.assertTrue(goesAround, "A blocked direct escape uses the open space beside the wall");
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void knownPlayerPanicStillEscapesAndReplansForMovingPlayer(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            cow.hurt(cow.damageSources().playerAttack(f.player), 1F);
            var goal = new AnimalCommon032Goals.Flee(cow, 1.6F, 1F);
            goal.start();
            h.assertTrue(goal.panicking(), "The fixture is actively panicking");
            Path original = cow.getNavigation().getPath();
            assertAway(h, cow.position(), f.player.position(), original);
            // Moving the threat across the stationary animal makes the former escape direction unsafe.
            f.player.setPos(cow.getX() + 6D, cow.getY(), cow.getZ());
            for (int tick = 0; tick < 10; tick++) goal.tick();
            Path replanned = cow.getNavigation().getPath();
            h.assertTrue(replanned != original, "A player movement greater than two blocks replans within ten goal ticks");
            assertAway(h, cow.position(), f.player.position(), replanned);
            goal.tick();
            h.assertTrue(goal.stationaryTicks() > 0, "Fleeing also updates the movement parent's stuck detection");
            goal.stop();
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void panicSpeedUpdatesAndRepeatedFallbackRestoresWaterCost(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            R196AnimalAi032.rememberAttacker(cow, f.player);
            var goal = new AnimalCommon032Goals.Flee(cow, 1.6F, 0F);
            goal.startPanicking(); goal.start(); goal.tick(); cow.getNavigation().tick();
            h.assertTrue(Math.abs(cow.getMoveControl().getSpeedModifier() - 1.8D) < .0001D,
                    "Native navigation receives the panic speed boost");
            R196AnimalAi032.state(cow).putInt("panic_countdown", 0);
            goal.tick(); cow.getNavigation().tick();
            h.assertTrue(Math.abs(cow.getMoveControl().getSpeedModifier() - 1.6D) < .0001D,
                    "Panic expiring updates the current native navigation speed immediately");
            goal.stop();
            // A sealed three-high cell guarantees that neither dry nor swimming path attempts can leave.
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0)
                for (int y = 0; y < 3; y++) f.wall(f.origin.offset(x, y, z));
            cow.setPathfindingMalus(PathType.WATER, 8F); goal.start();
            h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER), 0F, "Unreachable escape temporarily permits swimming fallback");
            for (int tick = 0; tick < 40; tick++) goal.tick();
            goal.stop();
            h.assertValueEqual(cow.getPathfindingMalus(PathType.WATER), 8F,
                    "Repeated failed replanning never overwrites the original water cost");
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void stuckEscapeReplansActiveSameTargetPathAfterTerrainChanges(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            R196AnimalAi032.rememberAttacker(cow, f.player);
            var flee = new AnimalCommon032Goals.Flee(cow, 1.6F, 0F); flee.start();
            Path original = cow.getNavigation().getPath();
            assertAway(h, cow.position(), f.player.position(), original);
            for (int z = -4; z <= 4; z++) for (int y = 0; y < 3; y++) f.wall(f.origin.offset(3, y, z));
            h.assertTrue(!original.isDone() && original.getTarget().equals(cow.getNavigation().getTargetPos()),
                    "The former direct path is still active with the same escape target after the new wall is built");
            for (int tick = 0; tick < 60; tick++) flee.tick();
            Path replanned = cow.getNavigation().getPath();
            h.assertTrue(replanned != original, "Stuck detection forces a fresh search despite the unchanged threat and active native target");
            assertAway(h, cow.position(), f.player.position(), replanned);
            boolean goesAround = false;
            for (int i = 0; i < replanned.getNodeCount(); i++) {
                var node = replanned.getNode(i);
                h.assertTrue(!h.getLevel().getBlockState(node.asBlockPos()).blocksMotion(),
                        "Replanning cannot keep the former straight path through the newly placed wall");
                if (Math.abs(node.z - f.origin.getZ()) > 4) goesAround = true;
            }
            h.assertTrue(goesAround, "The fresh native path uses space beside the new obstruction");
            flee.stop();
        }
        h.succeed();
    }

    private static void assertAway(GameTestHelper h, Vec3 start, Vec3 threat, Path path) {
        h.assertTrue(path != null && path.getNodeCount() > 2 && path.getEndNode() != null,
                "A real native navigation route exists on the prepared terrain");
        double initial = start.distanceTo(threat);
        h.assertTrue(nodePosition(path, path.getNodeCount() - 1).distanceTo(threat) > initial + 4D,
                "The selected route ends substantially farther from the player");
        h.assertTrue(nodePosition(path, Math.min(4, path.getNodeCount() - 1)).distanceTo(threat) > initial + .5D,
                "The route already gains distance in its early section");
        for (int i = 0; i < path.getNodeCount(); i++)
            h.assertTrue(nodePosition(path, i).distanceTo(threat) >= initial - 1.5D,
                    "Escape does not first take a shortcut toward the player");
    }

    private static Vec3 nodePosition(Path path, int index) {
        var node = path.getNode(index);
        return new Vec3(node.x + .5D, node.y, node.z + .5D);
    }

    @SuppressWarnings("removal")
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos origin;
        final ServerPlayer player;
        final List<Mob> animals = new ArrayList<>();

        Fixture(GameTestHelper helper, boolean floor) {
            this.helper = helper;
            origin = new BlockPos(-2_100_000 - IDS.incrementAndGet() * 128, 161, -2_100_000);
            for (int x = -32; x <= 32; x += 16) for (int z = -32; z <= 32; z += 16)
                helper.getLevel().getChunkAt(origin.offset(x, 0, z));
            if (floor) for (int x = -25; x <= 25; x++) for (int z = -25; z <= 25; z++) {
                helper.getLevel().setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y < 4; y++) helper.getLevel().setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
            player = helper.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin.getX() - 3.5D, origin.getY(), origin.getZ() + .5D);
            player.setNoGravity(true);
        }

        <T extends Mob> T mob(EntityType<T> type, double x, double z) {
            T animal = type.create(helper.getLevel());
            animal.moveTo(origin.getX() + x + .5D, origin.getY(), origin.getZ() + z + .5D);
            animal.setNoAi(true); animal.setNoGravity(true); animal.setOnGround(true);
            helper.getLevel().addFreshEntity(animal); animals.add(animal);
            return animal;
        }

        void wall(BlockPos pos) {
            helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            // This remote synchronous fixture is not block-ticking, so block changes need explicit native cache invalidation.
            helper.getLevel().getPathTypeCache().invalidate(pos);
        }

        @Override public void close() {
            for (var animal : animals) animal.discard();
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }
}
