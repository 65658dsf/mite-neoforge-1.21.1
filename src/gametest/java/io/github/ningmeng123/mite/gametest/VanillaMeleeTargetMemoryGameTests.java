package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import io.github.ningmeng123.mite.entity.VanillaMeleePursuit;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real vanilla target goals remember only acquired players and bounded progressing detours. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class VanillaMeleeTargetMemoryGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_vanilla_memory_test", Integer::compareTo);

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void movingVindicatorKeepsAcquiredHiddenPlayerPastNativeThirtyUpdates(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> f.acquireThenHide());
        int[] updates = {0};
        h.onEachTick(() -> f.guard(() -> {
            if (!f.started) return;
            f.advanceAlongRoute(.08D);
            h.assertTrue(f.selector.canContinueToUse(),
                    "The real vanilla player goal retains its moving hidden target beyond thirty unseen updates");
            h.assertTrue(f.mob.getTarget() == f.player, "The accepted player remains the actual mob target");
            if (++updates[0] != 90) return;
            h.assertTrue(f.mob.position().distanceToSqr(f.initial) > 16D,
                    "The vindicator made real progress along the complete native detour");
            f.mob.getNavigation().stop();
            h.assertFalse(f.selector.canContinueToUse(),
                    "Stopping that real route immediately removes extended unseen memory");
            f.selector.stop();
            h.assertTrue(f.mob.getTarget() == null, "The real target goal releases the lost player");
            f.close();
            h.succeed();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 190)
    public static void stalledVindicatorLosesExtendedMemoryAfterSixtyRealGameTicks(GameTestHelper h) {
        Fixture f = new Fixture(h);
        long[] startedAt = {0L};
        f.whenReady(() -> {
            f.acquireThenHide();
            startedAt[0] = h.getLevel().getGameTime();
            for (int update = 0; update < 30; ++update)
                h.assertTrue(f.selector.canContinueToUse(), "The original thirty unseen updates remain available");
        });
        h.onEachTick(() -> f.guard(() -> {
            if (!f.started) return;
            // Real game time advances normally; repeated attack ticks cannot invent position progress.
            f.mob.getSensing().tick();
            f.melee.tick();
            h.assertFalse(f.mob.hasLineOfSight(f.player), "The stationary player remains hidden by the wall");
            if (h.getLevel().getGameTime() - startedAt[0] < 65L) return;
            h.assertTrue(f.mob.position().distanceToSqr(f.initial) < .001D,
                    "The fixture never moved the vindicator along its otherwise complete route");
            h.assertFalse(f.selector.canContinueToUse(),
                    "A stale route cannot retain the player on the next unseen update after sixty real ticks");
            f.selector.stop();
            h.assertTrue(f.mob.getTarget() == null, "The original target goal clears the stalled chase");
            f.close();
            h.succeed();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 430)
    public static void continuouslyMovingVindicatorStillStopsAtThreeHundredUnseenUpdates(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> f.acquireThenHide());
        int[] updates = {0};
        h.onEachTick(() -> f.guard(() -> {
            if (!f.started) return;
            // Slow, continuous movement keeps the finite wall opaque for the entire native memory limit.
            f.advanceAlongRoute(.03D);
            ++updates[0];
            if (updates[0] <= 300) {
                h.assertTrue(f.selector.canContinueToUse(),
                        "The native reducedTickDelay retains up to three hundred progressing unseen updates");
                h.assertTrue(f.mob.getTarget() == f.player, "The real selector continues the acquired player");
                return;
            }
            h.assertTrue(f.mob.position().distanceToSqr(f.initial) > 16D,
                    "The finite limit is reached with actual ongoing route progress");
            h.assertTrue(VanillaMeleePursuit.followsReachableRoute(f.mob, f.player),
                    "The route remains fully verified and progressing when its target memory expires");
            h.assertFalse(f.selector.canContinueToUse(),
                    "Even a progressing detour cannot exceed the native reduced three-hundred-update limit");
            f.selector.stop();
            h.assertTrue(f.mob.getTarget() == null, "Expiry clears the actual vanilla target");
            f.close();
            h.succeed();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void completeDetourDoesNotMakeAnInitiallyHiddenPlayerVisible(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> {
            f.hidePlayer();
            Path preview = MeleePursuitPaths.findVanilla(f.mob, f.player, null);
            h.assertTrue(MeleePursuitPaths.completeVanillaRouteClear(f.mob, preview, f.player),
                    "The initially hidden player really has a complete loaded walking detour");
            for (int attempt = 0; attempt < 64; ++attempt) {
                f.mob.getSensing().tick();
                h.assertFalse(f.selector.canUse(),
                        "The actual vanilla acquisition goal still requires first-hand sight of a new player");
            }
            h.assertTrue(f.mob.getTarget() == null, "A path preview cannot acquire an unseen player");
            f.close();
            h.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final int ticketKey = IDS.getAndIncrement();
        final BlockPos origin;
        final Vec3 initial;
        final Vindicator mob;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final MeleeAttackGoal melee;
        final NearestAttackableTargetGoal<?> selector;
        final List<ChunkPos> chunks = new ArrayList<>();
        boolean started, closed;

        Fixture(GameTestHelper h) {
            helper = h;
            origin = new BlockPos(870_000 + ticketKey * 96, h.absolutePos(BlockPos.ZERO).getY() + 10, 870_000);
            for (int x = -4; x <= 12; ++x) for (int z = -18; z <= 18; ++z) for (int y = -1; y <= 4; ++y)
                h.getLevel().setBlock(origin.offset(x, y, z),
                        (y == -1 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            ChunkPos low = new ChunkPos(origin.offset(-4, 0, -18)), high = new ChunkPos(origin.offset(12, 0, 18));
            for (int x = low.x; x <= high.x; ++x) for (int z = low.z; z <= high.z; ++z) {
                ChunkPos chunk = new ChunkPos(x, z);
                chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketKey);
            }
            mob = EntityType.VINDICATOR.create(h.getLevel());
            h.assertTrue(mob != null, "The actual vanilla vindicator is available");
            initial = Vec3.atBottomCenterOf(origin);
            mob.moveTo(initial);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setOnGround(true);
            mob.setPersistenceRequired();
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64D);
            h.getLevel().addFreshEntity(mob);
            melee = mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(MeleeAttackGoal.class::isInstance).map(MeleeAttackGoal.class::cast).findFirst().orElseThrow();
            selector = mob.targetSelector.getAvailableGoals().stream()
                    .filter(wrapped -> wrapped.getPriority() == 2)
                    .map(wrapped -> wrapped.getGoal()).filter(NearestAttackableTargetGoal.class::isInstance)
                    .map(goal -> (NearestAttackableTargetGoal<?>)goal).findFirst().orElseThrow();
            UUID playerId = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId,
                    "vmem-" + playerId.toString().substring(0, 6)), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
            guard(() -> {
                NetworkRegistry.configureMockConnection(connection);
                h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
                player.setGameMode(GameType.SURVIVAL);
                player.moveTo(Vec3.atBottomCenterOf(origin.east(8)));
                player.setNoGravity(true);
                player.setOnGround(true);
                h.assertTrue(VanillaMeleePursuit.supports(mob), "The native melee bridge supports this unmodified vindicator goal");
            });
        }

        void whenReady(Runnable action) {
            helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(chunks.stream().allMatch(chunk ->
                                helper.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null),
                        "Every fixture chunk is published before the loaded-only native route is queried");
            }).thenExecute(() -> guard(action));
        }

        void acquireThenHide() {
            mob.getSensing().tick();
            helper.assertTrue(mob.hasLineOfSight(player), "The player was physically visible before acquisition");
            boolean acquired = false;
            for (int attempt = 0; attempt < 64 && !acquired; ++attempt) acquired = selector.canUse();
            helper.assertTrue(acquired, "The real installed nearest-player goal acquires the registered survival player");
            selector.start();
            helper.assertTrue(mob.getTarget() == player, "The real native goal assigns its previously visible player");
            hidePlayer();
            helper.assertTrue(melee.canUse(), "The installed vanilla melee goal accepts the complete route around the wall");
            melee.start();
            melee.tick();
            Path path = mob.getNavigation().getPath();
            helper.assertTrue(MeleePursuitPaths.completeVanillaRouteClear(mob, path, player),
                    "The actual native melee navigation owns a full physically verified detour");
            started = true;
        }

        void hidePlayer() {
            for (int z = -14; z <= 14; ++z) for (int y = 0; y <= 4; ++y) {
                BlockPos pos = origin.offset(2, y, z);
                helper.getLevel().setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 2);
                helper.getLevel().getPathTypeCache().invalidate(pos);
            }
            mob.getSensing().tick();
            helper.assertFalse(mob.hasLineOfSight(player), "The finite solid wall hides the actual player");
        }

        void advanceAlongRoute(double distance) {
            mob.getNavigation().tick();
            Path path = mob.getNavigation().getPath();
            helper.assertTrue(path != null && !path.isDone(), "The real detour remains unfinished while tracking the hidden player");
            Vec3 delta = path.getNextEntityPos(mob).subtract(mob.position());
            if (delta.lengthSqr() > 0D) mob.setPos(mob.position().add(delta.normalize().scale(Math.min(distance, delta.length()))));
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setOnGround(true);
            mob.getSensing().tick();
            melee.tick();
            helper.assertFalse(mob.hasLineOfSight(player), "The player remains hidden along this portion of the native detour");
        }

        void guard(Runnable action) {
            if (closed) return;
            try { action.run(); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            melee.stop();
            selector.stop();
            VanillaMeleePursuit.clear(mob);
            mob.discard();
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null)
                    player.server.getPlayerList().remove(player);
            } finally {
                channel.finishAndReleaseAll();
                for (ChunkPos chunk : chunks)
                    helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketKey);
            }
        }
    }
}
