package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real insomnia scheduling, ordinary spawn checks and actual phantom admission. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class PhantomSpawningGameTests {
    private static final AtomicInteger SITES = new AtomicInteger();
    private static final TicketType<Integer> LIGHT_TICKET = TicketType.create("mite_phantom_light_test", Integer::compareTo);
    private static final int ATTEMPT_TICKS = 2400 * 16;

    private PhantomSpawningGameTests() {}

    @GameTest(template = "empty")
    public static void nativeInsomniaSpawnerAddsPhantomsThroughTheNormalNaturalEntry(GameTestHelper helper) {
        try (Site site = new Site(helper); var preset = new SpawnPreset(true)) {
            site.assertNativeConditions(helper);
            site.level.getRandom().setSeed(73628419L);
            PhantomSpawner spawner = new PhantomSpawner();
            int reported = site.run(spawner, ATTEMPT_TICKS);
            helper.assertTrue(!site.joined.isEmpty(),
                    "A sleepless outdoor survival player receives actual naturally admitted phantoms, reported=" + reported);
            helper.assertTrue(site.joined.stream().allMatch(phantom -> phantom.isAddedToLevel()
                            && phantom.getSpawnType() == MobSpawnType.NATURAL),
                    "The native spawner's phantoms complete real natural finalization and world admission");
            int present = site.joined.size();
            helper.assertValueEqual(spawner.tick(site.level, true, true), 0,
                    "The native sixty-to-one-hundred-nineteen-second scheduling cooldown starts after its attempt");
            helper.assertValueEqual(site.joined.size(), present, "The next scheduler tick keeps the current group count");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeInsomniaRestSkyAltitudeAndDaylightConditionsControlSpawning(GameTestHelper helper) {
        try (Site site = new Site(helper)) {
            site.assertNativeConditions(helper);
            site.level.getRandom().setSeed(68294731L);
            site.insomnia(false);
            site.run(new PhantomSpawner(), ATTEMPT_TICKS);
            helper.assertTrue(site.joined.isEmpty(), "The native insomnia game rule controls phantom generation");

            site.insomnia(true);
            site.rest(72000);
            site.run(new PhantomSpawner(), ATTEMPT_TICKS);
            helper.assertTrue(site.joined.isEmpty(), "Exactly three days of unrest still fails the native strict random threshold");

            site.rest(2_000_000);
            site.time(6000L);
            site.run(new PhantomSpawner(), ATTEMPT_TICKS);
            helper.assertTrue(site.joined.isEmpty(), "The native sky-darkening check rejects daytime attempts");

            site.time(18000L);
            site.player.setPos(site.player.getX(), 50D, site.player.getZ());
            site.run(new PhantomSpawner(), ATTEMPT_TICKS);
            helper.assertTrue(site.joined.isEmpty(), "A player below the native sea level fails the altitude condition");

            site.player.setPos(site.player.getX(), 100D, site.player.getZ());
            site.level.getRandom().setSeed(73628419L);
            site.run(new PhantomSpawner(), ATTEMPT_TICKS);
            helper.assertTrue(!site.joined.isEmpty(), "Restoring the ordinary outdoor night conditions admits a real phantom group");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void aRealRoofAndItsRemovalUpdateTheNativePhantomSkyCondition(GameTestHelper helper) {
        Site site = new Site(helper);
        BlockPos roof = site.player.blockPosition().above(2);
        BlockState previousRoof = site.level.getBlockState(roof);
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            private void close() { site.level.setBlock(roof, previousRoof, 2); site.close(); }
        });
        site.assertNativeConditions(helper);
        site.level.setBlock(roof, Blocks.STONE.defaultBlockState(), 2);
        CompletableFuture<?> covered = site.lightingBarrier();
        CompletableFuture<?>[] opened = new CompletableFuture<?>[1];
        helper.startSequence().thenWaitUntil(() -> site.requireLightingReady(helper, covered, false)).thenExecute(() -> {
            site.time(18000L);
            site.run(new PhantomSpawner(), ATTEMPT_TICKS);
            helper.assertTrue(site.joined.isEmpty(), "The real covered player fails the native open-sky condition");
            site.level.setBlock(roof, previousRoof, 2);
            opened[0] = site.lightingBarrier();
        }).thenWaitUntil(() -> site.requireLightingReady(helper, opened[0], true)).thenExecute(() -> {
            site.time(18000L);
            site.level.getRandom().setSeed(73628419L);
            site.run(new PhantomSpawner(), ATTEMPT_TICKS);
            helper.assertTrue(!site.joined.isEmpty(), "Removing the real roof restores natural phantom admission");
        }).thenSucceed();
    }

    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final BlockPos origin;
        final List<Phantom> joined = new ArrayList<>();
        final Consumer<EntityJoinLevelEvent> capture;
        final int id;
        final long previousTime, dayStart;
        final Difficulty previousDifficulty;
        final boolean previousInsomnia, previousMobSpawning;

        Site(GameTestHelper helper) {
            level = helper.getLevel();
            previousTime = level.getDayTime();
            dayStart = previousTime - Math.floorMod(previousTime, 24000L);
            previousDifficulty = level.getDifficulty();
            previousInsomnia = level.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA);
            previousMobSpawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            id = SITES.getAndIncrement();
            origin = new BlockPos(3_700_008 + id * 128, 100, 3_700_008);
            level.getChunkAt(origin);
            level.getChunkSource().addRegionTicket(LIGHT_TICKET, new ChunkPos(origin), 2, id);
            level.getServer().getWorldData().setDifficulty(Difficulty.HARD);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
            insomnia(true);
            time(18000L);

            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "phantom-audit"), false);
            player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
            rest(2_000_000);

            capture = event -> {
                if (!event.isCanceled() && event.getLevel() == level && event.getEntity() instanceof Phantom phantom
                        && Math.abs(phantom.getX() - origin.getX()) < 32D && Math.abs(phantom.getZ() - origin.getZ()) < 32D)
                    joined.add(phantom);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, capture);
        }

        void assertNativeConditions(GameTestHelper helper) {
            helper.assertTrue(player.isAlive() && !player.isCreative() && !player.isSpectator(),
                    "The real registered phantom candidate is a living survival player");
            helper.assertTrue(level.canSeeSky(player.blockPosition()), "The isolated native spawn candidate has an open sky column");
            helper.assertTrue(level.getSkyDarken() >= 5, "The fixture has native nighttime sky darkening");
        }

        void insomnia(boolean enabled) {
            level.getGameRules().getRule(GameRules.RULE_DOINSOMNIA).set(enabled, level.getServer());
        }

        void rest(int ticks) { player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), ticks); }

        void time(long timeOfDay) {
            level.setDayTime(dayStart + timeOfDay);
            level.updateSkyBrightness();
        }

        CompletableFuture<?> lightingBarrier() {
            var light = level.getChunkSource().getLightEngine();
            light.checkBlock(origin);
            var chunk = new ChunkPos(origin);
            CompletableFuture<?> barrier = light.waitForPendingTasks(chunk.x, chunk.z);
            light.tryScheduleUpdate();
            return barrier;
        }

        void requireLightingReady(GameTestHelper helper, CompletableFuture<?> barrier, boolean openSky) {
            level.getChunkSource().getLightEngine().tryScheduleUpdate();
            for (int tasks = 0; tasks < 64 && level.getChunkSource().pollTask(); tasks++) {}
            helper.assertTrue(level.isPositionEntityTicking(origin) && barrier != null && barrier.isDone(),
                    "The native phantom habitat is ticking and its light updates have completed");
            barrier.join();
            helper.assertValueEqual(level.canSeeSky(player.blockPosition()), openSky,
                    "The native sky-light lookup reflects the actual roof state");
        }

        int run(PhantomSpawner spawner, int ticks) {
            int reported = 0;
            for (int tick = 0; tick < ticks && joined.isEmpty(); tick++) reported += spawner.tick(level, true, true);
            return reported;
        }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(capture);
            joined.forEach(Phantom::discard);
            try {
                player.server.getPlayerList().remove(player);
            } finally {
                channel.finishAndReleaseAll();
                level.getChunkSource().removeRegionTicket(LIGHT_TICKET, new ChunkPos(origin), 2, id);
                level.getServer().getWorldData().setDifficulty(previousDifficulty);
                level.getGameRules().getRule(GameRules.RULE_DOINSOMNIA).set(previousInsomnia, level.getServer());
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(previousMobSpawning, level.getServer());
                level.setDayTime(previousTime);
                level.updateSkyBrightness();
            }
        }
    }
}
