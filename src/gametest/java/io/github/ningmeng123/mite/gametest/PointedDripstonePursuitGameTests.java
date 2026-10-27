package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.VanillaMeleePursuit;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical cave obstacles, native hazard costs and full world-tick melee pursuit. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class PointedDripstonePursuitGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_dripstone_pursuit_test", Integer::compareTo);

    private PointedDripstonePursuitGameTests() {}

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=420)
    public static void sourceZombieActuallyPassesASmallStalagmiteWithSafeSideRoutesAndHits(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, Layout.OPEN);
        f.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        f.tip(f.origin.offset(7, 0, 0), Direction.UP, DripstoneThickness.TIP);
        f.attack();
    }

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=420)
    public static void nativeVindicatorActuallyPassesASmallStalagmiteWithSafeSideRoutesAndHits(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, Layout.OPEN);
        f.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        f.tip(f.origin.offset(7, 0, 0), Direction.UP, DripstoneThickness.TIP);
        f.attack();
    }

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=420)
    public static void sourceZombieUsesTheCollisionFreeDryRimBesideACautiousDripstoneCell(GameTestHelper h) {
        new Fixture(h, EntityType.ZOMBIE, Layout.DRY_RIM).attack();
    }

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=420)
    public static void nativeVindicatorUsesTheCollisionFreeDryRimBesideACautiousDripstoneCell(GameTestHelper h) {
        new Fixture(h, EntityType.VINDICATOR, Layout.DRY_RIM).attack();
    }

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=420)
    public static void aSourceZombieAlreadyBesideTheTipCanLeaveItsCautiousStartAndHit(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, Layout.OPEN);
        f.tip(f.origin.offset(1, 0, 1), Direction.UP, DripstoneThickness.TIP);
        f.attack();
    }

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=420)
    public static void sourceZombieAvoidsAHeadHeightStalactiteAndActuallyHits(GameTestHelper h) {
        new Fixture(h, EntityType.ZOMBIE, Layout.HANGING).attack();
    }

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=420)
    public static void nativeVindicatorAvoidsAHeadHeightStalactiteAndActuallyHits(GameTestHelper h) {
        new Fixture(h, EntityType.VINDICATOR, Layout.HANGING).attack();
    }

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=460)
    public static void insertingASmallStalagmiteIntoAnActiveZombiePursuitStillAllowsARealHit(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE, Layout.OPEN);
        f.begin().thenWaitUntil(f::assertRealInitialProgress).thenExecute(() -> {
            h.assertTrue(f.mob.getX() < f.origin.getX() + 5D, "The new obstacle is inserted ahead of the genuinely walking zombie");
            f.tip(f.origin.offset(7, 0, 0), Direction.UP, DripstoneThickness.TIP);
            f.probe = f.nativeProbe();
        }).thenWaitUntil(f::assertHit).thenExecute(f::assertResult).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=500)
    public static void anActuallyBlockedNativePursuitCannotPhaseThroughTipsAndResumesAfterTheyAreRemoved(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, Layout.LOW_CORRIDOR);
        BlockPos lower = f.origin.offset(7, 0, 0);
        f.begin().thenWaitUntil(f::assertRealInitialProgress).thenExecute(() -> {
            h.assertTrue(f.mob.getX() < lower.getX() - 1D, "The native mob has not reached the new physical obstruction");
            f.tip(lower, Direction.UP, DripstoneThickness.FRUSTUM);
            f.tip(lower.above(), Direction.UP, DripstoneThickness.TIP);
            h.assertFalse(h.getLevel().noCollision(f.mob, f.mob.getBoundingBox()
                            .move(Vec3.atBottomCenterOf(lower).subtract(f.mob.position())).deflate(.001D)),
                    "Two real dripstone shapes below the two-block ceiling physically block this corridor");
        }).thenIdle(45).thenExecute(() -> {
            h.assertTrue(f.hits == 0 && f.mob.getX() < lower.getX(),
                    "A cached former path does not let the native mob phase through the obstructed corridor: " + f.diagnostics());
            f.assertObstaclesIntact();
            h.getLevel().setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(lower.above(), Blocks.AIR.defaultBlockState(), 3);
            f.obstacles.clear();
        }).thenWaitUntil(f::assertHit).thenExecute(f::assertResult).thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="dripstone_pursuit_live", timeoutTicks=420)
    public static void aWitherRoseStillRequiresARealSafeBypassRatherThanAnImmuneCautiousPath(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, Layout.OPEN);
        BlockPos rose = f.origin.offset(7, 0, 0);
        h.getLevel().setBlock(rose.below(), Blocks.DIRT.defaultBlockState(), 2);
        h.getLevel().setBlock(rose, Blocks.WITHER_ROSE.defaultBlockState(), 2);
        f.obstacles.add(rose);
        f.rose = rose;
        f.attack();
    }

    private enum Layout { OPEN, DRY_RIM, HANGING, LOW_CORRIDOR }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int key = IDS.getAndIncrement();
        final BlockPos origin;
        final PathfinderMob mob;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final Layout layout;
        final List<ChunkPos> chunks = new ArrayList<>();
        final List<BlockPos> obstacles = new ArrayList<>();
        final Consumer<LivingDamageEvent.Post> damage;
        BlockPos rose;
        float startingMobHealth, mobDamage;
        double furthestX, furthestSide;
        int hits, activeTick, nativeCautious;
        boolean active, enteredRose, closed, nativeReaches;
        String probe = "not yet measured";

        Fixture(GameTestHelper h, EntityType<? extends PathfinderMob> type, Layout layout) {
            this.h = h; this.layout = layout;
            origin = new BlockPos(705_000 + key * 96, h.absolutePos(BlockPos.ZERO).getY() + 12, 705_000);
            for (int x = -5; x <= 23; x++) for (int z = -7; z <= 7; z++) for (int y = -2; y <= 5; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), (y <= -1 || y == 5 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            if (layout == Layout.DRY_RIM) {
                // A one-block dry rim crosses a two-block pit with water at its bottom. The tip is in the
                // adjacent cell, so the rim's feet stay supported and its real body never touches it.
                for (int x = 3; x <= 12; x++) for (int z = -4; z <= 4; z++) if (z != 0) {
                    h.getLevel().setBlock(origin.offset(x, -3, z), Blocks.BEDROCK.defaultBlockState(), 2);
                    h.getLevel().setBlock(origin.offset(x, -2, z), Blocks.WATER.defaultBlockState(), 2);
                    h.getLevel().setBlock(origin.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 2);
                }
                h.getLevel().setBlock(origin.offset(7, -1, 1), Blocks.BEDROCK.defaultBlockState(), 2);
                tip(origin.offset(7, 0, 1), Direction.UP, DripstoneThickness.TIP);
            } else if (layout == Layout.HANGING) {
                h.getLevel().setBlock(origin.offset(7, 2, 0), Blocks.BEDROCK.defaultBlockState(), 2);
                tip(origin.offset(7, 1, 0), Direction.DOWN, DripstoneThickness.TIP);
            } else if (layout == Layout.LOW_CORRIDOR) {
                for (int x = -3; x <= 20; x++) {
                    h.getLevel().setBlock(origin.offset(x, 2, 0), Blocks.BEDROCK.defaultBlockState(), 2);
                    for (int y = 0; y <= 3; y++) for (int z : new int[]{-1, 1})
                        h.getLevel().setBlock(origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
            ChunkPos low = new ChunkPos(origin.offset(-5, 0, -7)), high = new ChunkPos(origin.offset(23, 0, 7));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, key);
                for (int cx = x - 2; cx <= x + 2; cx++) for (int cz = z - 2; cz <= z + 2; cz++)
                    h.getLevel().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
            }
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "dripstone-" + key), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.CREATIVE);
            player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "dripstone_test_health"), 100D, AttributeModifier.Operation.ADD_VALUE));
            player.setHealth(player.getMaxHealth());
            // Initial native acquisition needs eye-to-eye sight. Keep the low collider in the
            // middle row while exposing the player just beside its shadow, before any AI runs.
            player.moveTo(Vec3.atBottomCenterOf(origin.east(16)).add(0D, 0D, layout == Layout.HANGING ? 1D : 0D));
            mob = type.create(h.getLevel()); mob.moveTo(Vec3.atBottomCenterOf(origin));
            EventHooks.finalizeMobSpawn(mob, h.getLevel(), h.getLevel().getCurrentDifficultyAt(origin), MobSpawnType.SPAWN_EGG, null);
            if (mob instanceof Zombie zombie) zombie.setBaby(false);
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            mob.setNoAi(true); mob.setPersistenceRequired(); h.getLevel().addFreshEntity(mob);
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            damage = event -> {
                if (active && event.getEntity() == player && event.getSource().getEntity() == mob && event.getNewDamage() > 0F) hits++;
                if (active && event.getEntity() == mob && event.getNewDamage() > 0F) mobDamage += event.getNewDamage();
            };
            NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, damage);
            h.onEachTick(() -> {
                if (!active || closed) return;
                furthestX = Math.max(furthestX, mob.getX() - origin.getX());
                furthestSide = Math.max(furthestSide, Math.abs(mob.getZ() - origin.getZ() - .5D));
                if (rose != null && mob.getBoundingBox().intersects(rose.getX(), rose.getY(), rose.getZ(),
                        rose.getX() + 1D, rose.getY() + 1D, rose.getZ() + 1D)) enteredRose = true;
            });
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        void tip(BlockPos pos, Direction direction, DripstoneThickness thickness) {
            BlockState state = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, direction).setValue(PointedDripstoneBlock.THICKNESS, thickness);
            h.getLevel().setBlock(pos, state, 2); obstacles.add(pos);
        }

        GameTestSequence begin() {
            return h.startSequence().thenWaitUntil(() -> {
                for (int tasks = 0; tasks < 64 && h.getLevel().getChunkSource().pollTask(); tasks++) {}
                for (ChunkPos chunk : chunks) h.assertTrue(h.getLevel().isPositionEntityTicking(chunk.getWorldPosition()),
                        "Real cave terrain is entity-ticking before pursuit: " + chunk);
                h.assertTrue(h.getLevel().getEntity(mob.getUUID()) == mob && h.getLevel().players().contains(player),
                        "Both the actual monster and registered player are present in the real server level");
            }).thenExecute(() -> {
                h.assertTrue(mob instanceof Zombie ? SourceMeleeGoal.first(mob) != null
                                : SourceMeleeGoal.first(mob) == null && mob.goalSelector.getAvailableGoals().stream()
                                        .anyMatch(w -> w.getGoal() instanceof MeleeAttackGoal),
                        "The fixture exercises the installed source zombie or original native melee controller");
                h.assertTrue(h.getLevel().noCollision(mob, mob.getBoundingBox().deflate(.001D)), "The actual starting body is collision-free");
                if (layout == Layout.HANGING) {
                    var blockedBody = mob.getBoundingBox().move(Vec3.atBottomCenterOf(origin.east(7)).subtract(mob.position())).deflate(.001D);
                    h.assertFalse(h.getLevel().noCollision(mob, blockedBody),
                            "The unchanged middle-row stalactite genuinely collides with the walking monster's head");
                    var originalSight = h.getLevel().clip(new ClipContext(mob.getEyePosition(), player.getEyePosition().add(0D, 0D, -1D),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
                    h.assertTrue(originalSight.getType() == HitResult.Type.BLOCK
                                    && originalSight.getBlockPos().equals(origin.offset(7, 1, 0)),
                            "The former straight-on arrangement blocks initial native eye-to-eye acquisition at the actual low tip");
                    h.assertTrue(mob.hasLineOfSight(player),
                            "The side-offset player is initially visible while the real head-height obstacle and safe side route remain");
                }
                if (layout == Layout.DRY_RIM) for (int x = 3; x <= 12; x++) {
                    var body = mob.getBoundingBox().move(Vec3.atBottomCenterOf(origin.east(x)).subtract(mob.position())).deflate(.001D);
                    h.assertTrue(h.getLevel().noCollision(mob, body) && !h.getLevel().containsAnyLiquid(body),
                            "Every dry rim standing body fits beside the pointed stone and above the water");
                }
                startingMobHealth = mob.getHealth(); activeTick = mob.tickCount;
                probe = nativeProbe();
                if (layout == Layout.DRY_RIM) h.assertTrue(nativeReaches && nativeCautious > 0,
                        "The unmodified native query genuinely reaches the player through collision-free cautious rim cells: " + probe);
                System.out.println("DRIPSTONE PURSUIT PROBE " + diagnostics());
                player.setGameMode(GameType.SURVIVAL); mob.setNoAi(false); active = true;
            }).thenWaitUntil(() -> h.assertTrue(mob.getTarget() == player,
                    "The real controller naturally discovers its survival player without an assigned target: " + diagnostics()));
        }

        void attack() {
            begin().thenWaitUntil(this::assertHit).thenExecute(this::assertResult).thenExecute(this::close).thenSucceed();
        }

        void assertRealInitialProgress() {
            h.assertTrue(mob.getX() >= origin.getX() + 1.5D && mob.tickCount > activeTick,
                    "Complete natural world ticks must advance the active pursuit before changing its terrain: " + diagnostics());
        }

        void assertHit() {
            h.assertTrue(hits > 0, "The real melee attack must eventually damage the player after physical walking: " + diagnostics());
        }

        void assertResult() {
            h.assertTrue(mob.tickCount > activeTick + 10 && furthestX > 12D,
                    "Success requires sustained real world ticks and physical progress across the obstacle area: " + diagnostics());
            h.assertTrue(mobDamage == 0F && mob.getHealth() >= startingMobHealth && !enteredRose,
                    "A valid safe route never requires dripstone damage or entering the wither rose: " + diagnostics());
            h.assertTrue(h.getLevel().noCollision(mob, mob.getBoundingBox().deflate(.001D)),
                    "The attacking body still fits the real cave collision shapes");
            assertObstaclesIntact();
        }

        void assertObstaclesIntact() {
            for (BlockPos pos : obstacles) h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.POINTED_DRIPSTONE)
                            || h.getLevel().getBlockState(pos).is(Blocks.WITHER_ROSE),
                    "Walking around a valid obstacle never silently destroys it: " + pos);
        }

        String nativeProbe() {
            var region = new PathNavigationRegion(h.getLevel(), origin.offset(-24, -8, -24), origin.offset(40, 12, 24));
            var evaluator = new WalkNodeEvaluator();
            evaluator.setCanPassDoors(mob.getNavigation().getNodeEvaluator().canPassDoors());
            evaluator.setCanOpenDoors(mob.getNavigation().getNodeEvaluator().canOpenDoors());
            evaluator.setCanFloat(mob.getNavigation().getNodeEvaluator().canFloat());
            Path nativePath = new PathFinder(evaluator, 2048).findPath(region, mob, Set.of(player.blockPosition()), 48F, 1, 1F);
            int cautious = 0;
            if (nativePath != null) for (int i = 0; i < nativePath.getNodeCount(); i++)
                if (nativePath.getNode(i).type == PathType.DAMAGE_CAUTIOUS) cautious++;
            nativeCautious = cautious; nativeReaches = nativePath != null && nativePath.canReach();
            return "nativeReach=" + nativeReaches + ", cautiousNodes=" + cautious
                    + ", native=" + describe(nativePath) + ", cautiousMalus=" + mob.getPathfindingMalus(PathType.DAMAGE_CAUTIOUS);
        }

        String diagnostics() {
            return "kind=" + mob.getType() + ", layout=" + layout + ", tick=" + mob.tickCount + ", target=" + mob.getTarget()
                    + ", pos=" + mob.position().subtract(Vec3.atBottomCenterOf(origin)) + ", maxX=" + furthestX + ", maxSide=" + furthestSide
                    + ", hits=" + hits + ", health=" + mob.getHealth() + ", mobDamage=" + mobDamage + ", path=" + describe(mob.getNavigation().getPath())
                    + ", searches=" + MeleePursuitPaths.searchStats(mob) + ", " + probe;
        }

        String describe(Path path) {
            if (path == null) return "null";
            StringBuilder result = new StringBuilder("reach=").append(path.canReach()).append("[");
            for (int i = 0; i < path.getNodeCount(); i++) {
                Node node = path.getNode(i);
                if (i > 0) result.append(';');
                result.append(node.x - origin.getX()).append(',').append(node.y - origin.getY()).append(',')
                        .append(node.z - origin.getZ()).append(':').append(node.type);
            }
            return result.append(']').toString();
        }

        @Override public void close() {
            if (closed) return;
            closed = true; NeoForge.EVENT_BUS.unregister(damage);
            VanillaMeleePursuit.clear(mob); MeleePursuitPaths.invalidate(mob);
            mob.discard(); player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
            for (ChunkPos chunk : chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, key);
        }
    }
}
