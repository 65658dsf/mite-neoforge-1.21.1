package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.VanillaMeleePursuit;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real thin blocks and vehicles must not stall full world-tick pursuit. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class ThinObstaclePursuitGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_thin_obstacle_test", Integer::compareTo);

    private ThinObstaclePursuitGameTests() {}

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void sourceZombieActuallyCrossesARailInARealNarrowCorridorAndHits(GameTestHelper h) {
        new Fixture(h, EntityType.ZOMBIE, Layout.RAIL).attack();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void nativeVindicatorActuallyCrossesARailInARealNarrowCorridorAndHits(GameTestHelper h) {
        new Fixture(h, EntityType.VINDICATOR, Layout.RAIL).attack();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void sourceZombieWalksAcrossThinCarpetAndItsRaisedStepAndHits(GameTestHelper h) {
        new Fixture(h, EntityType.ZOMBIE, Layout.CARPET).attack();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void nativeVindicatorWalksAcrossThinCarpetAndItsRaisedStepAndHits(GameTestHelper h) {
        new Fixture(h, EntityType.VINDICATOR, Layout.CARPET).attack();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void sourceZombieGoesAroundAnActualSolidBoatWithoutPushingOrBoardingIt(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, Layout.OPEN);
        f.spawnVehicle(true);
        f.attack();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void nativeVindicatorGoesAroundAnActualSolidBoatWithoutPushingOrBoardingIt(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, Layout.OPEN);
        f.spawnVehicle(true);
        f.attack();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void sourceZombieAvoidsAMinecartPushAndRideTrapWithoutInventingHardCollision(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, Layout.OPEN);
        f.spawnVehicle(false);
        f.attack();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void nativeVindicatorAvoidsAMinecartPushAndRideTrapWithoutInventingHardCollision(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, Layout.OPEN);
        f.spawnVehicle(false);
        f.attack();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=550)
    public static void aBoatInsertedIntoAnActualActiveZombieRouteReceivesARealBypass(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, Layout.OPEN);
        f.begin().thenWaitUntil(f::assertInitialProgress).thenExecute(() -> {
            h.assertTrue(f.mob.getX() < f.origin.getX() + 5D, "The boat is inserted ahead of the genuinely walking zombie");
            f.spawnVehicle(true);
        }).thenWaitUntil(f::assertHit).thenExecute(f::assertResult).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=550)
    public static void aProtectedBoatInALowNarrowCorridorCannotBePhasedThroughAndRemovalRestoresPursuit(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, Layout.LOW_CORRIDOR);
        Consumer<EntityMobGriefingEvent> deny = event -> { if (event.getEntity() == f.mob) event.setCanGrief(false); };
        NeoForge.EVENT_BUS.addListener(EntityMobGriefingEvent.class, deny);
        f.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(deny));
        f.begin().thenExecute(() -> h.assertFalse(EventHooks.canEntityGrief(h.getLevel(), f.mob),
                        "This obstacle-removal fixture explicitly protects the boat from the new road-destruction policy"))
                .thenWaitUntil(f::assertInitialProgress).thenExecute(() -> {
            h.assertTrue(f.mob.getX() < f.origin.getX() + 5D, "The native mob is still in front of the new boat");
            f.spawnVehicle(true);
        }).thenIdle(60).thenExecute(() -> {
            h.assertTrue(f.hits == 0 && f.mob.getX() < f.origin.getX() + 6.1D,
                    "The low ceiling prevents jumping onto the solid boat and walls prevent a side bypass: " + f.diagnostics());
            f.assertVehicleIntegrity();
            f.vehicle.discard(); f.vehicle = null;
        }).thenWaitUntil(f::assertHit).thenExecute(f::assertResult).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=550)
    public static void aBoatMovingUnderItsOwnTicksOutOfTheRouteDoesNotLeaveAStalePermanentWall(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, Layout.OPEN);
        f.spawnVehicle(true);
        f.begin().thenExecute(() -> {
            f.freezeVehicle = false;
            // Bedrock applies the native on-land friction every tick. A .35 impulse
            // travels only about half a block; this finite impulse actually clears the lane.
            f.vehicle.setDeltaMovement(0D, 0D, 2D);
        }).thenWaitUntil(() -> h.assertTrue(f.vehicle.getZ() - f.vehicleStart.z >= 2.5D,
                "The real boat tick and collision travel move it out of the original lane, without teleporting it: " + f.diagnostics()))
                .thenWaitUntil(f::assertHit).thenExecute(f::assertResult).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=550)
    public static void sourceZombieCanActuallyHitAPlayerControllingABoatWithoutBeingKeptOutsideAttackReach(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, Layout.OPEN);
        f.spawnVehicle(true, f.player.position());
        f.freezeVehicle = false;
        h.assertTrue(f.player.startRiding(f.vehicle, true), "The actual player occupies and controls the target boat");
        f.begin().thenWaitUntil(f::assertHit).thenExecute(() -> {
            f.assertResult();
            h.assertTrue(f.player.getVehicle() == f.vehicle,
                    "Real melee damage reaches the player while the original boat is still occupied: " + f.diagnostics());
        }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=550)
    public static void sourceZombieStartingInsideAParkedSoftMinecartCanLeaveItAndActuallyHitWithoutReturningInside(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, Layout.OPEN);
        f.spawnVehicle(false, f.mob.position());
        // Hold the parked cart with the same vehicle-only Pre fixture used for road obstacles.
        // The monster still runs complete native world ticks to leave the initial soft volume.
        f.allowInitialVehicleOverlap = true;
        h.assertTrue(f.mob.getBoundingBox().deflate(.001D).intersects(f.vehicle.getBoundingBox().deflate(.001D)),
                "The real initial monster body actually starts inside the soft minecart volume");
        f.begin().thenWaitUntil(f::assertHit).thenExecute(() -> {
            f.assertResult();
            h.assertTrue(f.escapedInitialVehicle,
                    "Actual world-tick movement leaves the original overlap before attacking, without teleporting the monster: " + f.diagnostics());
        }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void nativeVindicatorBypassesCarpetThatWouldPutItsHeadInsideALowCeiling(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, Layout.OPEN);
        for (int x = 6; x <= 10; x++) {
            h.getLevel().setBlock(f.origin.east(x), Blocks.RED_CARPET.defaultBlockState(), 2);
            for (int z = -3; z <= 3; z++) h.getLevel().setBlock(f.origin.offset(x, 2, z), Blocks.BEDROCK.defaultBlockState(), 2);
        }
        boolean[] crossedLowSide = {false}, clippedRoof = {false};
        h.onEachTick(() -> {
            if (!f.active || f.closed) return;
            var body = f.mob.getBoundingBox().deflate(.001D);
            if (body.intersects(f.origin.getX() + 6D, f.origin.getY() + 2D, f.origin.getZ() - 3D,
                    f.origin.getX() + 11D, f.origin.getY() + 3D, f.origin.getZ() + 4D)) clippedRoof[0] = true;
            if (f.mob.getX() > f.origin.getX() + 6D && f.mob.getX() < f.origin.getX() + 11D
                    && Math.abs(f.mob.getZ() - f.origin.getZ() - .5D) > .6D
                    && f.mob.getY() < f.origin.getY() + .02D && body.maxY < f.origin.getY() + 2D)
                crossedLowSide[0] = true;
        });
        f.begin().thenExecute(() -> {
            var carpetFeet = Vec3.atBottomCenterOf(f.origin.east(8)).add(0D, 1D / 16D, 0D);
            var raisedBody = f.mob.getBoundingBox().move(carpetFeet.subtract(f.mob.position())).deflate(.001D);
            h.assertFalse(h.getLevel().noCollision(f.mob, raisedBody),
                    "Standing on the real one-sixteenth carpet leaves too little headroom beneath this two-block ceiling");
            var sideFeet = Vec3.atBottomCenterOf(f.origin.offset(8, 0, 1));
            var sideBody = f.mob.getBoundingBox().move(sideFeet.subtract(f.mob.position())).deflate(.001D);
            h.assertTrue(h.getLevel().noCollision(f.mob, sideBody),
                    "The adjacent uncarpeted low passage genuinely fits the same native monster body");
        }).thenWaitUntil(f::assertHit).thenExecute(() -> {
            f.assertResult();
            h.assertTrue(crossedLowSide[0] && !clippedRoof[0],
                    "Actual movement uses the low safe side passage without putting the head through its ceiling: " + f.diagnostics());
            for (int x = 6; x <= 10; x++) h.assertTrue(h.getLevel().getBlockState(f.origin.east(x)).is(Blocks.RED_CARPET),
                    "The blocked central carpet remains intact while the actual monster walks around it");
        }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void sourceZombieUsesASafeBypassBesideActualMagmaFloorWithoutSteppingOnIt(GameTestHelper h) {
        magmaBypass(h, EntityType.ZOMBIE);
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void nativeVindicatorUsesASafeBypassBesideActualMagmaFloorWithoutSteppingOnIt(GameTestHelper h) {
        magmaBypass(h, EntityType.VINDICATOR);
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=550)
    public static void nativeVindicatorCrossesTheOnlyHotFloorRouteWithoutRemovingTheMagma(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, Layout.LOW_CORRIDOR);
        // Seal both corridor ends so walking around the outside is not a hidden alternative.
        for (int x : new int[]{-4, 23}) for (int y = 0; y <= 4; y++)
            h.getLevel().setBlock(f.origin.offset(x, y, 0), Blocks.BEDROCK.defaultBlockState(), 2);
        boolean[] steppedOnMagma = magmaFloor(f, 6, 8);
        f.begin().thenExecute(() -> {
            assertMagmaClassification(f, 6, 8);
            h.assertFalse(f.mob.fireImmune(), "The real native vindicator has no special fire immunity");
        }).thenWaitUntil(f::assertHit).thenExecute(() -> {
            h.assertTrue(f.mob.tickCount > f.activeTick + 10 && f.furthestX > 12D && steppedOnMagma[0],
                    "With no safe alternative, actual world ticks cross the original magma and reach the player: " + f.diagnostics());
            h.assertTrue(f.mob.isAlive() && f.mobDamage == f.hotFloorDamage,
                    "Only the actual hot-floor damage is allowed during this necessary hazardous crossing: " + f.diagnostics());
            h.assertTrue(h.getLevel().noCollision(f.mob, f.mob.getBoundingBox().deflate(.001D)),
                    "Accepting a hot route still preserves physical body collision");
            for (int x = 6; x <= 8; x++) h.assertTrue(h.getLevel().getBlockState(f.origin.offset(x, -1, 0)).is(Blocks.MAGMA_BLOCK),
                    "No replacement or removal of the original magma can fake the successful crossing");
        }).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="thin_obstacle_pursuit", timeoutTicks=500)
    public static void aRealFireImmuneSourceElementalCrossesTheOnlyMagmaFloorRouteAndActuallyAttacks(GameTestHelper h) {
        Fixture f = new Fixture(h, MiteMobs.type(MobKind.FIRE_ELEMENTAL), Layout.LOW_CORRIDOR);
        f.mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        for (int x : new int[]{-4, 23}) for (int y = 0; y <= 4; y++)
            h.getLevel().setBlock(f.origin.offset(x, y, 0), Blocks.BEDROCK.defaultBlockState(), 2);
        boolean[] steppedOnMagma = magmaFloor(f, 6, 10);
        f.begin().thenExecute(() -> {
            h.assertTrue(f.mob.getType() == MiteMobs.type(MobKind.FIRE_ELEMENTAL) && f.mob.fireImmune(),
                    "The actual registered source fire elemental retains its real fire immunity");
            h.assertTrue(f.mob.getBbHeight() < 2F && f.mob.getMainHandItem().isEmpty(),
                    "Its native body genuinely fits the low corridor and uses its natural unarmed attack");
            assertMagmaClassification(f, 6, 10);
        }).thenWaitUntil(f::assertHit).thenExecute(() -> {
            h.assertTrue(f.mob.tickCount > f.activeTick + 10 && f.furthestX > 12D && steppedOnMagma[0],
                    "Complete world ticks must physically cross the unique magma floor and actually reach the player: " + f.diagnostics());
            // This source species deliberately takes a mite:water pulse every forty ticks,
            // even when dry. Preserve that behavior while measuring hot-floor immunity separately.
            h.assertTrue(f.hotFloorDamage == 0F && f.mob.isAlive(),
                    "The real fire-immune elemental receives no hot-floor damage while its native self-pulse remains enabled: " + f.diagnostics());
            h.assertTrue(h.getLevel().noCollision(f.mob, f.mob.getBoundingBox().deflate(.001D)),
                    "The actual attacking elemental still fits the low corridor's physical shapes");
            for (int x = 6; x <= 10; x++) h.assertTrue(h.getLevel().getBlockState(f.origin.offset(x, -1, 0)).is(Blocks.MAGMA_BLOCK),
                    "The immune species crosses actual surviving magma rather than replacing or removing the hazard");
        }).thenExecute(f::close).thenSucceed();
    }

    private static void magmaBypass(GameTestHelper h, EntityType<? extends PathfinderMob> type) {
        Fixture f = new Fixture(h, type, Layout.OPEN);
        boolean[] steppedOnMagma = magmaFloor(f, 6, 10);
        f.begin().thenExecute(() -> assertMagmaClassification(f, 6, 10)).thenWaitUntil(f::assertHit).thenExecute(() -> {
            f.assertResult();
            h.assertTrue(f.furthestSide > .6D && !steppedOnMagma[0],
                    "Actual complete world ticks use the safe side of the hot strip and never stand on magma: " + f.diagnostics());
            for (int x = 6; x <= 10; x++) h.assertTrue(h.getLevel().getBlockState(f.origin.offset(x, -1, 0)).is(Blocks.MAGMA_BLOCK),
                    "The real hot blocks remain in place throughout the safe bypass");
        }).thenExecute(f::close).thenSucceed();
    }

    private static boolean[] magmaFloor(Fixture f, int first, int last) {
        for (int x = first; x <= last; x++) f.h.getLevel().setBlock(f.origin.offset(x, -1, 0), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        boolean[] steppedOnMagma = {false};
        f.h.onEachTick(() -> {
            if (!f.active || f.closed || !f.mob.onGround()) return;
            BlockPos support = BlockPos.containing(f.mob.getX(), f.mob.getY() - .01D, f.mob.getZ());
            if (f.h.getLevel().getBlockState(support).is(Blocks.MAGMA_BLOCK)) steppedOnMagma[0] = true;
        });
        return steppedOnMagma;
    }

    private static void assertMagmaClassification(Fixture f, int first, int last) {
        for (int x = first; x <= last; x++) {
            BlockPos floor = f.origin.offset(x, -1, 0);
            f.h.assertTrue(f.h.getLevel().getBlockState(floor).is(Blocks.MAGMA_BLOCK)
                            && RawTerrain.type(f.h.getLevel(), floor) == PathType.DAMAGE_FIRE,
                    "The fixture uses actual magma whose uncached native raw path type is fire damage");
            f.h.assertTrue(WalkNodeEvaluator.getPathTypeStatic(f.mob, floor.above()) == PathType.DAMAGE_FIRE,
                    "The native walking node above the real magma also carries DAMAGE_FIRE before enhanced pursuit");
        }
    }

    private static final class RawTerrain extends WalkNodeEvaluator {
        static PathType type(BlockGetter level, BlockPos pos) { return getPathTypeFromState(level, pos); }
    }

    private enum Layout { OPEN, RAIL, CARPET, LOW_CORRIDOR }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int key = IDS.getAndIncrement();
        final Layout layout;
        final BlockPos origin;
        final PathfinderMob mob;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final List<ChunkPos> chunks = new ArrayList<>();
        final List<Runnable> cleanup = new ArrayList<>();
        final Consumer<LivingDamageEvent.Post> damage;
        final Consumer<EntityTickEvent.Pre> holdVehicle;
        Entity vehicle;
        Vec3 vehicleStart;
        boolean freezeVehicle = true, hadVehicle, enteredVehicle, rodeVehicle, movedHeldVehicle;
        boolean allowInitialVehicleOverlap, escapedInitialVehicle;
        boolean active, closed, crossedThinCarpet;
        double furthestX, furthestSide, highestFeet;
        float mobDamage, hotFloorDamage;
        int hits, activeTick;

        Fixture(GameTestHelper h, EntityType<? extends Mob> type, Layout layout) {
            this.h = h; this.layout = layout;
            origin = new BlockPos(745_000 + key * 96, h.absolutePos(BlockPos.ZERO).getY() + 12, 745_000);
            for (int x = -5; x <= 24; x++) for (int z = -7; z <= 7; z++) for (int y = -1; y <= 5; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), (y == -1 || y == 5 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            if (layout != Layout.OPEN) {
                for (int x = -4; x <= 23; x++) for (int y = 0; y <= 4; y++) for (int z : new int[]{-1, 1})
                    h.getLevel().setBlock(origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
                if (layout == Layout.LOW_CORRIDOR) for (int x = -4; x <= 23; x++)
                    h.getLevel().setBlock(origin.offset(x, 2, 0), Blocks.BEDROCK.defaultBlockState(), 2);
            }
            if (layout == Layout.RAIL) h.getLevel().setBlock(origin.east(7), Blocks.RAIL.defaultBlockState()
                    .setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH), 2);
            if (layout == Layout.CARPET) {
                for (int x = 6; x <= 8; x++) h.getLevel().setBlock(origin.east(x), Blocks.RED_CARPET.defaultBlockState(), 2);
                for (int x = 9; x <= 10; x++) {
                    h.getLevel().setBlock(origin.east(x), Blocks.BEDROCK.defaultBlockState(), 2);
                    h.getLevel().setBlock(origin.offset(x, 1, 0), Blocks.RED_CARPET.defaultBlockState(), 2);
                }
            }
            ChunkPos low = new ChunkPos(origin.offset(-5, 0, -7)), high = new ChunkPos(origin.offset(24, 0, 7));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, key);
                for (int cx = x - 2; cx <= x + 2; cx++) for (int cz = z - 2; cz <= z + 2; cz++)
                    h.getLevel().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
            }
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "thin-obst-" + key), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.CREATIVE);
            player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "thin_obstacle_test_health"), 100D, AttributeModifier.Operation.ADD_VALUE));
            player.setHealth(player.getMaxHealth()); player.moveTo(Vec3.atBottomCenterOf(origin.east(16)));
            var created = type.create(h.getLevel());
            h.assertTrue(created instanceof PathfinderMob, "The registered fixture species has native pathfinding");
            mob = (PathfinderMob)created; mob.moveTo(Vec3.atBottomCenterOf(origin));
            EventHooks.finalizeMobSpawn(mob, h.getLevel(), h.getLevel().getCurrentDifficultyAt(origin), MobSpawnType.SPAWN_EGG, null);
            if (mob instanceof Zombie zombie) zombie.setBaby(false);
            mob.setNoAi(true); mob.setPersistenceRequired();
            h.assertTrue(h.getLevel().addFreshEntity(mob), "The finalized actual monster enters the server level");
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(mob instanceof Zombie ? Items.IRON_SWORD : Items.IRON_AXE));
            mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            damage = event -> {
                if (active && event.getEntity() == player && event.getSource().getEntity() == mob && event.getNewDamage() > 0F) hits++;
                if (active && event.getEntity() == mob && event.getNewDamage() > 0F) {
                    mobDamage += event.getNewDamage();
                    if (event.getSource().is(DamageTypes.HOT_FLOOR)) hotFloorDamage += event.getNewDamage();
                }
            };
            // Freeze only a deliberately parked obstacle. The monster, player, world and their
            // complete native controllers continue ticking, so pushing the vehicle cannot pass the test.
            holdVehicle = event -> { if (freezeVehicle && event.getEntity() == vehicle) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, damage);
            NeoForge.EVENT_BUS.addListener(EntityTickEvent.Pre.class, holdVehicle);
            h.onEachTick(() -> {
                if (!active || closed) return;
                furthestX = Math.max(furthestX, mob.getX() - origin.getX());
                furthestSide = Math.max(furthestSide, Math.abs(mob.getZ() - origin.getZ() - .5D));
                highestFeet = Math.max(highestFeet, mob.getY() - origin.getY());
                if (layout == Layout.CARPET && mob.getX() > origin.getX() + 6.1D && mob.getX() < origin.getX() + 8.9D
                        && mob.getY() >= origin.getY() + .05D) crossedThinCarpet = true;
                if (mob.isPassenger()) rodeVehicle = true;
                if (vehicle != null) {
                    boolean overlaps = mob.getBoundingBox().deflate(.001D).intersects(vehicle.getBoundingBox().deflate(.001D));
                    if (overlaps && (!allowInitialVehicleOverlap || escapedInitialVehicle)) enteredVehicle = true;
                    if (!overlaps && allowInitialVehicleOverlap) escapedInitialVehicle = true;
                    if (freezeVehicle && vehicle.position().distanceToSqr(vehicleStart) > 1.0E-6D) movedHeldVehicle = true;
                }
            });
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        void spawnVehicle(boolean boat) {
            spawnVehicle(boat, Vec3.atBottomCenterOf(origin.east(7)));
        }

        void spawnVehicle(boolean boat, Vec3 position) {
            h.assertTrue(vehicle == null, "Only one independently tracked real vehicle is installed");
            vehicle = boat ? EntityType.BOAT.create(h.getLevel()) : EntityType.MINECART.create(h.getLevel());
            vehicleStart = position;
            vehicle.moveTo(vehicleStart); vehicle.setNoGravity(true);
            h.assertTrue(h.getLevel().addFreshEntity(vehicle), "The actual vehicle enters the level");
            hadVehicle = true;
            var crossingBody = mob.getBoundingBox().move(vehicleStart.subtract(mob.position())).deflate(.001D);
            boolean hardCollision = !h.getLevel().getEntityCollisions(mob, crossingBody).isEmpty();
            h.assertValueEqual(hardCollision, boat,
                    "Vanilla boats are hard mob colliders; ordinary minecarts are push/ride obstacles, not hard walls");
            h.assertValueEqual(mob.canCollideWith(vehicle), boat,
                    "The fixture preserves the real directed mob-to-vehicle collision predicate");
            h.assertTrue(vehicle.canCollideWith(mob), "Both vehicles may react to a pushing or boarding mob in the reverse direction");
        }

        GameTestSequence begin() {
            return h.startSequence().thenWaitUntil(() -> {
                for (int tasks = 0; tasks < 64 && h.getLevel().getChunkSource().pollTask(); tasks++) {}
                for (ChunkPos chunk : chunks) h.assertTrue(h.getLevel().isPositionEntityTicking(chunk.getWorldPosition()),
                        "The actual obstacle terrain is entity-ticking: " + chunk);
                h.assertTrue(h.getLevel().getEntity(mob.getUUID()) == mob && h.getLevel().players().contains(player),
                        "The real monster and registered player are present");
            }).thenExecute(() -> {
                h.assertTrue(R196MonsterAi.managed(mob) ? SourceMeleeGoal.first(mob) != null
                                : SourceMeleeGoal.first(mob) == null && mob.goalSelector.getAvailableGoals().stream()
                                        .anyMatch(w -> w.getGoal() instanceof MeleeAttackGoal),
                        "The actual source or native melee controller is installed");
                h.assertTrue(h.getLevel().noCollision(mob, mob.getBoundingBox().deflate(.001D)), "The starting body fits");
                h.assertTrue(mob.hasLineOfSight(player), "The low obstacles do not block initial player acquisition");
                activeTick = mob.tickCount; player.setGameMode(GameType.SURVIVAL); mob.setNoAi(false); active = true;
                System.out.println("THIN OBSTACLE PURSUIT PROBE " + diagnostics());
            }).thenWaitUntil(() -> h.assertTrue(mob.getTarget() == player,
                    "Full native ticks naturally acquire the visible survival player without assigning a target: " + diagnostics()));
        }

        void attack() {
            begin().thenWaitUntil(this::assertHit).thenExecute(this::assertResult).thenExecute(this::close).thenSucceed();
        }

        void assertInitialProgress() {
            h.assertTrue(mob.getX() >= origin.getX() + 1.5D && mob.tickCount > activeTick,
                    "The pursuit must physically advance before changing an obstacle: " + diagnostics());
        }

        void assertHit() {
            h.assertTrue(hits > 0, "Real movement past the obstacle must eventually produce actual melee damage: " + diagnostics());
        }

        void assertResult() {
            h.assertTrue(mob.tickCount > activeTick + 10 && furthestX > 12D,
                    "Success requires sustained real world ticks and crossing the obstacle area: " + diagnostics());
            h.assertTrue(mobDamage == 0F, "A safe route does not require fall or environmental damage: " + diagnostics());
            h.assertTrue(h.getLevel().noCollision(mob, mob.getBoundingBox().deflate(.001D)), "The final attacking body fits");
            if (layout == Layout.RAIL) h.assertTrue(h.getLevel().getBlockState(origin.east(7)).is(Blocks.RAIL),
                    "The rail was crossed without breaking or removing it");
            if (layout == Layout.CARPET) {
                h.assertTrue(crossedThinCarpet && highestFeet >= 1.03D,
                        "Actual feet cross the thin carpet and climb its real carpet-covered step: " + diagnostics());
                for (int x = 6; x <= 10; x++) h.assertTrue(h.getLevel().getBlockState(origin.offset(x, x < 9 ? 0 : 1, 0)).is(Blocks.RED_CARPET),
                        "All original carpet blocks survive the crossing");
            }
            assertVehicleIntegrity();
            if (hadVehicle && layout == Layout.OPEN && freezeVehicle && !allowInitialVehicleOverlap) h.assertTrue(furthestSide > .6D,
                    "The mob took a physical side bypass rather than entering or pushing the vehicle: " + diagnostics());
        }

        void assertVehicleIntegrity() {
            h.assertTrue(!enteredVehicle && !rodeVehicle && !movedHeldVehicle,
                    "No body overlap, involuntary riding or pushing a parked vehicle counts as a successful route: " + diagnostics());
            if (vehicle != null) h.assertTrue(vehicle.isAlive(), "The obstacle is not destroyed to fake a bypass");
        }

        String diagnostics() {
            Path path = mob.getNavigation().getPath();
            return "kind=" + mob.getType() + ", layout=" + layout + ", tick=" + mob.tickCount + ", target=" + mob.getTarget()
                    + ", pos=" + mob.position().subtract(Vec3.atBottomCenterOf(origin)) + ", maxX=" + furthestX + ", maxSide=" + furthestSide
                    + ", maxFeet=" + highestFeet + ", hits=" + hits + ", mobDamage=" + mobDamage + ", hotFloorDamage=" + hotFloorDamage
                    + ", vehicle=" + (vehicle == null ? "none" : vehicle.getType() + "@" + vehicle.position().subtract(Vec3.atBottomCenterOf(origin)))
                    + ", overlap=" + enteredVehicle + ", riding=" + rodeVehicle + ", movedParked=" + movedHeldVehicle
                    + ", initialOverlapAllowed=" + allowInitialVehicleOverlap + ", escapedInitial=" + escapedInitialVehicle
                    + ", path=" + (path == null ? "null" : path.canReach() + "/" + path.getNextNodeIndex() + "/" + path.getNodeCount())
                    + ", searches=" + MeleePursuitPaths.searchStats(mob);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            for (Runnable action : cleanup) action.run();
            NeoForge.EVENT_BUS.unregister(damage); NeoForge.EVENT_BUS.unregister(holdVehicle);
            VanillaMeleePursuit.clear(mob); MeleePursuitPaths.invalidate(mob);
            if (vehicle != null) vehicle.discard();
            mob.discard(); player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
            for (ChunkPos chunk : chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, key);
        }
    }
}
