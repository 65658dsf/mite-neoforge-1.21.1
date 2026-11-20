package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.animal.AnimalCommon032Goals;
import io.github.ningmeng123.mite.entity.animal.AnimalEscapePath;
import io.github.ningmeng123.mite.entity.animal.R196AnimalAi032;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real damage propagation and native navigation regressions for herd escape. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AnimalHerdEscapeGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    @GameTest(template = "ranged_027", timeoutTicks = 1200)
    public static void orphanedChickenPanicExpiresExactlyDespiteRepeatedCompletedRouteReplans(GameTestHelper h) {
        if (MiteConfig.animalSearchBudgetEnabled()) { liveOrphanedPanic(h); return; }
        try (Fixture f = new Fixture(h, true)) {
            var chicken = f.mob(EntityType.CHICKEN, 0, 0);
            var state = R196AnimalAi032.state(chicken);
            state.remove("attacker"); state.putInt("attacker_memory", 0);
            state.putLong("spooked_until", h.getLevel().getGameTime());
            state.putBoolean("decided_to_flee", false); state.putBoolean("fleeing", false);
            state.putInt("panic_countdown", 120);
            var flee = new AnimalCommon032Goals.Flee(chicken, 1.3F, .75F);
            h.assertTrue(flee.canUse(), "Only an old remaining panic starts this episode, without an attacker, fire or active spook");
            flee.start();
            // Model a valid remaining old countdown after initial start decisions have already run.
            state.putInt("panic_countdown", 120);
            int replans = 0;
            for (int tick = 1; tick <= 120; tick++) {
                chicken.getNavigation().stop();
                // After replan's nextInt(20), this seed makes the old chicken .75 roll succeed.
                chicken.getRandom().setSeed(1L);
                flee.tick();
                if (flee.stationaryTicks() == 0) replans++;
                h.assertValueEqual(state.getInt("panic_countdown"), 120 - tick,
                        "A completed short path cannot restart the old panic: tick=" + tick + ", replans=" + replans);
            }
            h.assertTrue(replans == 12, "The actual flee goal replans twelve times at its unchanged ten-tick interval");
            h.assertFalse(flee.canContinueToUse() || R196AnimalAi032.isSpookedOrFleeing(chicken),
                    "The expired episode releases fleeing instead of keeping an undamaged chicken running");
            flee.stop();
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void startingChickenMovementRetainsBothExistingInitialPanicRolls(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var chicken = f.mob(EntityType.CHICKEN, 0, 0);
            chicken.setPathfindingMalus(PathType.WATER, 0F);
            var state = R196AnimalAi032.state(chicken);
            for (long seed : new long[]{0L, 1L, 2L, 3L, 4096L, 6144L, 10000L, 20000L}) {
                state.remove("attacker"); state.putInt("attacker_memory", 0);
                state.putLong("spooked_until", h.getLevel().getGameTime());
                state.putBoolean("done_panicking", false); state.putInt("panic_countdown", 0);
                Random expected = new Random(seed);
                int countdown = 0;
                if (expected.nextFloat() < .75F) countdown = 100 + expected.nextInt(100);
                expected.nextInt(20); // Native movement task's initial stuck threshold.
                if (expected.nextFloat() < .75F) countdown = 100 + expected.nextInt(100);
                chicken.getRandom().setSeed(seed);
                var flee = new AnimalCommon032Goals.Flee(chicken, 1.3F, .75F);
                flee.start();
                h.assertValueEqual(state.getInt("panic_countdown"), countdown,
                        "Initial start and initial path retain the previous random-draw order and .75 probabilities: seed=" + seed);
                flee.stop();
            }
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void aRealNewPlayerHitStillStartsFearAfterAnOldChickenPanicHasFinished(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var chicken = f.mob(EntityType.CHICKEN, 0, 0);
            var state = R196AnimalAi032.state(chicken);
            state.putBoolean("done_panicking", true); state.putInt("panic_countdown", 0);
            h.assertTrue(chicken.hurt(chicken.damageSources().playerAttack(f.player), 1F), "The new player hit causes actual damage");
            h.assertTrue(R196AnimalAi032.lastAttacker(chicken) == f.player && R196AnimalAi032.spooked(chicken),
                    "New real damage still records the player and starts the ordinary herd alarm");
            var flee = new AnimalCommon032Goals.Flee(chicken, 1.3F, .75F);
            h.assertTrue(flee.canUse(), "An old completed panic never blocks a genuine new threat");
            flee.start();
            for (int tick = 0; tick < 12; tick++) {
                flee.tick();
                h.assertTrue(state.getInt("panic_countdown") >= 99,
                        "An active real spook continues refreshing panic while the threat's alarm is current");
            }
            flee.stop();
        }
        h.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void anActuallyBurningChickenStillRefreshesPanicDuringRepeatedRouteChecks(GameTestHelper h) {
        try (Fixture f = new Fixture(h, true)) {
            var chicken = f.mob(EntityType.CHICKEN, 0, 0);
            var state = R196AnimalAi032.state(chicken);
            state.putBoolean("done_panicking", true); state.putInt("panic_countdown", 1);
            chicken.setRemainingFireTicks(100);
            var flee = new AnimalCommon032Goals.Flee(chicken, 1.3F, .75F);
            h.assertTrue(chicken.isOnFire() && flee.canUse(), "A real burning flag remains a panic trigger");
            flee.start();
            for (int tick = 0; tick < 12; tick++) {
                chicken.getNavigation().stop();
                flee.tick();
                h.assertTrue(state.getInt("panic_countdown") >= 100,
                        "Burning still refreshes the original 100-199 tick panic independently of replanning");
            }
            chicken.clearFire();
            flee.stop();
        }
        h.succeed();
    }

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

    @GameTest(template = "ranged_027", timeoutTicks = 1200)
    public static void knownPlayerPanicStillEscapesAndReplansForMovingPlayer(GameTestHelper h) {
        if (MiteConfig.animalSearchBudgetEnabled()) { liveMovingThreat(h); return; }
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

    @GameTest(template = "ranged_027", timeoutTicks = 1800)
    public static void panicSpeedUpdatesAndRepeatedFallbackRestoresWaterCost(GameTestHelper h) {
        if (MiteConfig.animalSearchBudgetEnabled()) { livePanicSpeedAndWaterFallback(h); return; }
        try (Fixture f = new Fixture(h, true)) {
            var cow = f.mob(EntityType.COW, 0, 0);
            R196AnimalAi032.rememberAttacker(cow, f.player);
            var goal = new AnimalCommon032Goals.Flee(cow, 1.6F, 0F);
            goal.startPanicking(); goal.start(); goal.tick(); cow.getNavigation().tick();
            h.assertTrue(Math.abs(cow.getMoveControl().getSpeedModifier() - 1.6245D) < .0001D,
                    "Native navigation receives 90.25% of the original speed including the panic boost");
            R196AnimalAi032.state(cow).putInt("panic_countdown", 0);
            goal.tick(); cow.getNavigation().tick();
            h.assertTrue(Math.abs(cow.getMoveControl().getSpeedModifier() - 1.444D) < .0001D,
                    "Panic expiring immediately restores 90.25% of the original normal flee speed");
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

    @GameTest(template = "ranged_027", timeoutTicks = 1200)
    public static void stuckEscapeReplansActiveSameTargetPathAfterTerrainChanges(GameTestHelper h) {
        if (MiteConfig.animalSearchBudgetEnabled()) { liveChangedTerrain(h); return; }
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

    private static void liveOrphanedPanic(GameTestHelper h) {
        LiveFixture f = new LiveFixture(h, true);
        f.sealCell();
        int[] episodeTick = {0}, observed = {0}, attempts = {0};
        f.begin(() -> {
            var state = R196AnimalAi032.state(f.mob);
            state.putBoolean("done_panicking", true); state.putInt("panic_countdown", 120);
        }).thenWaitUntil(() -> h.assertTrue(f.running(), "The installed orphan panic goal starts under native world ticks"))
                .thenExecute(() -> {
                    // Initial movement retains its original panic rolls. This is the old
                    // remaining countdown after that start, with no new fear stimulus.
                    R196AnimalAi032.state(f.mob).putInt("panic_countdown", 120);
                    episodeTick[0] = f.mob.tickCount; attempts[0] = f.navigation().attempts.size();
                    f.progressRestarts = 0;
                    f.tickCheck = () -> {
                        int elapsed = f.mob.tickCount - episodeTick[0];
                        h.assertValueEqual(R196AnimalAi032.state(f.mob).getInt("panic_countdown"), Math.max(0, 120 - elapsed),
                                "A genuinely blocked route retry cannot renew the old panic at real entity tick " + elapsed);
                        observed[0] = Math.max(observed[0], elapsed);
                    };
                }).thenWaitUntil(() -> h.assertTrue(f.mob.tickCount - episodeTick[0] >= 120,
                        "Let all 120 remaining panic ticks elapse naturally"))
                .thenExecute(() -> {
                    f.tickCheck.run(); f.tickCheck = null;
                    h.assertTrue(observed[0] >= 120 && f.progressRestarts > 0 && f.navigation().attempts.size() > attempts[0],
                            "The original remaining panic expires despite witnessed blocked native searches and actual retries: " + f.describe());
                    h.assertTrue(!f.flee.canContinueToUse() && !R196AnimalAi032.isSpookedOrFleeing(f.mob)
                                    && R196AnimalAi032.lastAttacker(f.mob) == null,
                            "Without fire, a threat or a new alarm, the orphan episode naturally releases fleeing");
                    f.close();
                }).thenSucceed();
    }

    private static void liveMovingThreat(GameTestHelper h) {
        LiveFixture f = new LiveFixture(h, false);
        Path[] original = {null};
        int[] changedAt = {0};
        f.begin(() -> f.hit()).thenWaitUntil(() -> {
            h.assertTrue(f.running(), "The installed Flee goal naturally starts from accepted player damage");
            assertAway(h, f.mob.position(), f.player.position(), f.mob.getNavigation().getPath());
        }).thenWaitUntil(() -> {
            // The initial real hit can lift the cow off the floor. Vanilla ground
            // navigation cannot create a route in midair, so measure the moved
            // threat response only after normal grounded walking has resumed.
            h.assertTrue(f.mob.onGround() && f.mob.tickCount - f.activeTick >= 20
                            && !f.mob.getNavigation().isDone() && f.mob.position().distanceTo(f.start) > .01D,
                    "The live cow lands and resumes ordinary walking before moving the known threat");
        }).thenExecute(() -> {
            h.assertTrue(f.flee.panicking(), "The actual known-player panic is active");
            original[0] = f.mob.getNavigation().getPath(); changedAt[0] = f.mob.tickCount;
            f.player.moveTo(f.mob.position().add(6D, 0D, 0D));
        }).thenWaitUntil(() -> {
            Path replanned = f.mob.getNavigation().getPath();
            h.assertTrue(replanned != original[0], "A moved real threat causes a fresh route rather than retaining the former direction");
            assertAway(h, f.mob.position(), f.player.position(), replanned);
        }).thenExecute(() -> {
            h.assertTrue(f.mob.tickCount > changedAt[0] && f.mob.tickCount - changedAt[0] <= 10,
                    "Moving the player more than two blocks replans within ten complete native animal ticks: " + f.describe());
            h.assertTrue(f.mob.position().distanceTo(f.start) > .01D,
                    "The installed goal and native controls advance the live animal while reacting to the moved threat");
            f.close();
        }).thenSucceed();
    }

    private static void livePanicSpeedAndWaterFallback(GameTestHelper h) {
        LiveFixture f = new LiveFixture(h, false);
        int[] boostEndedAt = {0};
        f.begin(() -> {
            R196AnimalAi032.rememberAttacker(f.mob, f.player);
            var state = R196AnimalAi032.state(f.mob);
            state.putBoolean("done_panicking", true); state.putInt("panic_countdown", 120);
        }).thenWaitUntil(() -> {
            h.assertTrue(f.running() && !f.mob.getNavigation().isDone(), "The real pending escape is delivered to native movement before checking its speed");
            h.assertTrue(Math.abs(f.mob.getMoveControl().getSpeedModifier() - 1.6245D) < .0001D,
                    "Actual native navigation receives 90.25% of the cow's 1.6 plus 0.2 panic speed");
        }).thenExecute(() -> {
            R196AnimalAi032.state(f.mob).putInt("panic_countdown", 0); boostEndedAt[0] = f.mob.tickCount;
        }).thenWaitUntil(() -> h.assertTrue(f.mob.tickCount > boostEndedAt[0]
                        && Math.abs(f.mob.getMoveControl().getSpeedModifier() - 1.444D) < .0001D,
                "The next real animal tick sends the original normal flee speed at 90.25% after its panic boost expires"))
                .thenExecute(() -> {
                    h.assertTrue(f.mob.tickCount - boostEndedAt[0] <= 2,
                            "Normal flee speed changes immediately, without waiting for a fresh route");
                    f.close();
                    LiveFixture water = new LiveFixture(h, false);
                    water.sealCell();
                    water.begin(() -> R196AnimalAi032.rememberAttacker(water.mob, water.player))
                            .thenExecute(() -> water.tickCheck = () -> h.assertValueEqual(
                                    water.mob.getPathfindingMalus(PathType.WATER), 8F,
                                    "A queued or completed failed candidate restores the exact permanent water cost between real ticks"))
                            .thenWaitUntil(() -> h.assertTrue(water.navigation().wetPhases() >= 2,
                                    "The genuine sealed terrain exhausts dry candidates and retries swimming in two separate escape attempts: " + water.describe()))
                            .thenExecute(() -> {
                                h.assertTrue(water.navigation().attempts.stream().anyMatch(attempt -> attempt.water() == 8F)
                                                && water.navigation().attempts.stream().anyMatch(attempt -> attempt.water() == 0F),
                                        "Actual native queries observe both original dry cost and temporary swimming allowance");
                                h.assertValueEqual(water.mob.getPathfindingMalus(PathType.WATER), 8F,
                                        "Native query and scoring finally blocks restore the original cost before publishing failure");
                                water.mob.goalSelector.removeGoal(water.flee);
                                h.assertValueEqual(water.mob.getPathfindingMalus(PathType.WATER), 8F,
                                        "Stopping the installed escape goal after repeated failed wet phases restores the exact original water cost");
                                water.tickCheck = null; water.close();
                            }).thenSucceed();
                });
    }

    private static void liveChangedTerrain(GameTestHelper h) {
        LiveFixture f = new LiveFixture(h, false);
        Path[] original = {null};
        int[] attempts = {0};
        f.begin(() -> R196AnimalAi032.rememberAttacker(f.mob, f.player)).thenWaitUntil(() -> {
            h.assertTrue(f.running(), "The installed goal starts from the remembered player under normal entity ticks");
            assertAway(h, f.mob.position(), f.player.position(), f.mob.getNavigation().getPath());
        }).thenExecute(() -> {
            original[0] = f.mob.getNavigation().getPath(); attempts[0] = f.navigation().attempts.size();
            for (int z = -4; z <= 4; z++) for (int y = 0; y < 3; y++) f.wall(f.origin.offset(3, y, z));
            h.assertTrue(!original[0].isDone() && original[0].getTarget().equals(f.mob.getNavigation().getTargetPos()),
                    "The active native route initially retains its original target when real terrain blocks its next section");
        }).thenWaitUntil(() -> {
            Path replanned = f.mob.getNavigation().getPath();
            h.assertTrue(replanned != original[0] && f.navigation().attempts.size() > attempts[0],
                    "Actual blockage causes a fresh native path search despite the unchanged attacker");
            assertAway(h, f.mob.position(), f.player.position(), replanned);
            boolean around = false;
            for (int i = 0; i < replanned.getNodeCount(); i++) {
                var node = replanned.getNode(i);
                h.assertTrue(!h.getLevel().getBlockState(node.asBlockPos()).blocksMotion(),
                        "Fresh native nodes cannot preserve the former shortcut through the new solid wall");
                if (Math.abs(node.z - f.origin.getZ()) > 4) around = true;
            }
            h.assertTrue(around, "The live new route uses real walkable space beside the obstruction");
        }).thenExecute(() -> {
            h.assertTrue(f.mob.tickCount > f.activeTick && f.mob.isAlive(),
                    "Terrain recovery is driven by actual entity and navigation ticks");
            f.close();
        }).thenSucceed();
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

    private record LiveAttempt(float water, int nodes) {}

    /** Native navigation is only observed; its pathfinder and returned routes are unchanged. */
    private static final class LiveNavigation extends GroundPathNavigation {
        final List<LiveAttempt> attempts = new ArrayList<>();
        LiveNavigation(Mob mob, Level level) { super(mob, level); }
        @Override public Path createPath(BlockPos destination, int accuracy, int range) {
            float water = mob.getPathfindingMalus(PathType.WATER);
            Path result = super.createPath(destination, accuracy, range);
            attempts.add(new LiveAttempt(water, result == null ? 0 : result.getNodeCount()));
            return result;
        }
        int wetPhases() {
            int phases = 0; boolean wet = false;
            for (LiveAttempt attempt : attempts) {
                boolean now = attempt.water() == 0F;
                if (now && !wet) phases++;
                wet = now;
            }
            return phases;
        }
    }

    private static final class LiveCow extends Cow {
        LiveCow(Level level) { super(EntityType.COW, level); }
        @Override protected PathNavigation createNavigation(Level level) { return new LiveNavigation(this, level); }
    }

    private static final class LiveChicken extends Chicken {
        LiveChicken(Level level) { super(EntityType.CHICKEN, level); }
        @Override protected PathNavigation createNavigation(Level level) { return new LiveNavigation(this, level); }
    }

    @SuppressWarnings("removal")
    private static final class LiveFixture implements AutoCloseable {
        private static final TicketType<Integer> TICKET = TicketType.create("mite_live_herd_escape_test", Integer::compareTo);
        final GameTestHelper h;
        final int key = IDS.getAndIncrement();
        final BlockPos origin = new BlockPos(-3_300_000 - key * 192, 161, -3_300_000);
        final List<ChunkPos> loaded = new ArrayList<>(), ticking = new ArrayList<>();
        final PathfinderMob mob;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final AnimalCommon032Goals.Flee flee;
        final Vec3 start;
        int activeTick, progressRestarts, previousStationary, observedEntityTick = Integer.MIN_VALUE;
        boolean active, closed;
        Runnable tickCheck;

        LiveFixture(GameTestHelper h, boolean chicken) {
            this.h = h;
            ChunkPos low = new ChunkPos(origin.offset(-64, 0, -64)), high = new ChunkPos(origin.offset(64, 0, 64));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); loaded.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 0, key);
                h.getLevel().getChunk(x, z, ChunkStatus.FULL, true);
            }
            for (int x = -25; x <= 25; x++) for (int z = -25; z <= 25; z++) for (int y = -1; y <= 4; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), (y == -1 || y == 4 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            low = new ChunkPos(origin.offset(-25, 0, -25)); high = new ChunkPos(origin.offset(25, 0, 25));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); ticking.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, key);
            }
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "live-herd-" + key), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.CREATIVE); player.moveTo(Vec3.atBottomCenterOf(origin.west(4)));
            mob = chicken ? new LiveChicken(h.getLevel()) : new LiveCow(h.getLevel());
            mob.moveTo(Vec3.atBottomCenterOf(origin));
            EventHooks.finalizeMobSpawn(mob, h.getLevel(), h.getLevel().getCurrentDifficultyAt(origin), MobSpawnType.SPAWN_EGG, null);
            ((Animal)mob).setAge(0); mob.setNoAi(true); mob.setPersistenceRequired(); mob.setOnGround(true);
            h.assertTrue(h.getLevel().addFreshEntity(mob), "The actual animal enters the native server level and installs its ordinary goals");
            Livestock032.needs(mob, new LivestockRules032.Needs(1F, 1F, 1F)); mob.getRandom().setSeed(311101L);
            flee = (AnimalCommon032Goals.Flee)mob.goalSelector.getAvailableGoals().stream().map(goal -> goal.getGoal())
                    .filter(goal -> goal instanceof AnimalCommon032Goals.Flee).findFirst().orElseThrow();
            start = mob.position();
            h.onEachTick(() -> {
                if (!active || closed) return;
                if (mob.tickCount != observedEntityTick) {
                    observedEntityTick = mob.tickCount;
                    int stationary = flee.stationaryTicks();
                    if (previousStationary > 0 && stationary == 0) progressRestarts++;
                    previousStationary = stationary;
                }
                if (tickCheck != null) tickCheck.run();
            });
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) {
                    if (!closed) System.out.println("LIVE HERD ESCAPE FAILURE " + describe());
                    close();
                }
            });
        }

        GameTestSequence begin(Runnable stimulus) {
            return h.startSequence().thenWaitUntil(() -> {
                for (int tasks = 0; tasks < 64 && h.getLevel().getChunkSource().pollTask(); tasks++) {}
                h.assertTrue(loaded.stream().allMatch(chunk -> h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null),
                        "All native escape-query chunks are published before starting live AI");
                h.assertTrue(ticking.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                        "Every fixture terrain chunk reaches actual entity-ticking status");
                h.assertTrue(h.getLevel().getEntity(mob.getUUID()) == mob && h.getLevel().getEntity(player.getUUID()) == player,
                        "The live animal and real remembered player are registered in this server level");
            }).thenExecute(() -> {
                h.assertTrue(h.getLevel().noCollision(mob, mob.getBoundingBox().deflate(.001D)), "The starting animal body fits the actual terrain");
                player.setGameMode(GameType.SURVIVAL); stimulus.run();
                activeTick = mob.tickCount; active = true; mob.setNoAi(false);
            });
        }

        LiveNavigation navigation() { return (LiveNavigation)mob.getNavigation(); }
        boolean running() { return mob.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() == flee && goal.isRunning()); }
        void wall(BlockPos pos) { h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3); }
        void sealCell() {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0)
                for (int y = 0; y < 3; y++) wall(origin.offset(x, y, z));
        }
        void hit() {
            h.assertTrue(mob.hurt(mob.damageSources().playerAttack(player), 1F), "Real accepted player damage starts the known-threat episode");
        }
        String describe() {
            return "type=" + mob.getType() + ", age=" + (mob.tickCount - activeTick) + ", running=" + running()
                    + ", pos=" + mob.position().subtract(start) + ", progressRestarts=" + progressRestarts
                    + ", stationary=" + flee.stationaryTicks() + ", attempts=" + navigation().attempts
                    + ", water=" + mob.getPathfindingMalus(PathType.WATER) + ", fear=" + R196AnimalAi032.state(mob)
                    + ", path=" + mob.getNavigation().getPath();
        }
        @Override public void close() {
            if (closed) return; closed = true; tickCheck = null;
            mob.goalSelector.removeGoal(flee); mob.discard();
            player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
            for (ChunkPos chunk : ticking) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, key);
            for (ChunkPos chunk : loaded) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 0, key);
        }
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
