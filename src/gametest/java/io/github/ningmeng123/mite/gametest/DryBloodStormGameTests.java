package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real engine lightning and initial weather packets remain independent of wet rain. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DryBloodStormGameTests {
    private static final long BLOOD_NIGHT = 31L * 24000L + 18000L;
    private DryBloodStormGameTests() {}

    @GameTest(template = "empty")
    public static void nativeThunderRetainsInterpolationWithoutPretendingThatItRains(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.setDayTime(BLOOD_NIGHT);
            level.setWeatherParameters(0, 6000, false, true);
            level.setRainLevel(0F);
            level.oThunderLevel = .2F; level.thunderLevel = .8F;
            helper.assertTrue(Math.abs(level.getThunderLevel(.5F) - .5F) < .0001F,
                    "A dry blood night retains the real old/current thunder interpolation");
            level.setThunderLevel(1F);
            helper.assertTrue(level.isThundering(), "Native isThundering sees the dry blood-night storm");
            helper.assertFalse(level.isRaining(), "The common thunder hook never fabricates global rain");
            for (long time : new long[]{31L * 24000L + 6000L, 30L * 24000L + 18000L, 127L * 24000L + 18000L}) {
                level.setDayTime(time);
                helper.assertTrue(level.getThunderLevel(1F) == 0F && !level.isThundering(),
                        "Daytime, ordinary nights and blue moon retain the native rain multiplier at " + time);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualChunkLightningWorksWithoutRainAndRetainsNativeSurfaceAndBiomeChecks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos reference = helper.absolutePos(BlockPos.ZERO);
        BlockPos surface = new BlockPos((reference.getX() & ~15) + 8, 310, (reference.getZ() & ~15) + 8);
        var chunk = level.getChunkAt(surface);
        var section = chunk.getSection(chunk.getSectionIndex(surface.getY()));
        var originalBiomes = section.getBiomes();
        var sampler = level.getChunkSource().randomState().sampler();
        int quartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
        int quartY = QuartPos.fromBlock(surface.getY()) & ~3;
        int quartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        var floor = level.getBlockState(surface);
        var air = level.getBlockState(surface.above());
        var above = level.getBlockState(surface.above(2));
        AABB area = new AABB(surface).inflate(32);
        List<LightningBolt> initial = level.getEntitiesOfClass(LightningBolt.class, area);
        boolean mobSpawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        try (WeatherSnapshot saved = new WeatherSnapshot(level); ChunkRandomSnapshot random = new ChunkRandomSnapshot(level)) {
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            level.setBlock(surface, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(surface.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(surface.above(2), Blocks.AIR.defaultBlockState(), 2);
            level.setDayTime(BLOOD_NIGHT);
            level.setWeatherParameters(0, 6000, false, true);
            level.setRainLevel(0F); level.setThunderLevel(1F);
            helper.assertValueEqual(RandomSource.create(5781L).nextInt(20000), 0,
                    "The independent native RNG fixture enters the existing blood lightning branch");
            for (var biomeKey : List.of(Biomes.PLAINS, Biomes.SNOWY_PLAINS, Biomes.DESERT)) {
                var biome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biomeKey);
                section.fillBiomesFromNoise((x,y,z,climate) -> biome, sampler, quartX, quartY, quartZ);
                helper.assertTrue(level.getBiome(surface.above()).is(biomeKey), "The actual surface lookup uses the selected biome");
                helper.assertFalse(level.isRainingAt(surface.above()), "The exposed strike position stays genuinely dry");
                random.forceLightningDraw();
                level.tickChunk(chunk, 0);
                List<LightningBolt> strikes = level.getEntitiesOfClass(LightningBolt.class, area).stream()
                        .filter(bolt -> !initial.contains(bolt)).toList();
                boolean allowed = !biomeKey.equals(Biomes.SNOWY_PLAINS);
                helper.assertValueEqual(strikes.size(), allowed ? 1 : 0,
                        "Actual tickChunk permits dry plains/desert lightning and retains the native snowy-biome exclusion");
                for (LightningBolt bolt : strikes) {
                    helper.assertTrue(bolt.blockPosition().equals(surface.above()), "The native heightmap chooses the exposed surface above the floor");
                    bolt.discard();
                }
            }
            level.setDayTime(30L * 24000L + 18000L);
            random.forceLightningDraw(); level.tickChunk(chunk, 0);
            helper.assertTrue(level.getEntitiesOfClass(LightningBolt.class, area).stream().allMatch(initial::contains),
                    "The same dry chunk and forced RNG cannot produce ordinary-night lightning");
        } finally {
            level.getEntitiesOfClass(LightningBolt.class, area).stream().filter(bolt -> !initial.contains(bolt)).forEach(LightningBolt::discard);
            level.setBlock(surface, floor, 2); level.setBlock(surface.above(), air, 2); level.setBlock(surface.above(2), above, 2);
            section.fillBiomesFromNoise((x,y,z,climate) -> originalBiomes.get(x & 3,y & 3,z & 3), sampler, quartX, quartY, quartZ);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(mobSpawning, level.getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realLoginAndRepeatedLevelSnapshotsSendDryThunderAndPreserveTheRainFade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "dry-blood-sync"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.setDayTime(BLOOD_NIGHT);
            level.setWeatherParameters(0, 6000, false, true);
            level.setRainLevel(0F); level.setThunderLevel(1F);
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            List<ClientboundGameEventPacket> login = weatherPackets(channel);
            assertWeatherSnapshot(helper, login, 0F, 1F);
            helper.assertTrue(login.stream().noneMatch(packet -> packet.getEvent() == ClientboundGameEventPacket.START_RAINING),
                    "A real late join does not falsely start wet rain during a dry storm");
            level.setRainLevel(.15F); level.setThunderLevel(.75F);
            level.getServer().getPlayerList().sendLevelInfo(player, level);
            List<ClientboundGameEventPacket> returning = weatherPackets(channel);
            assertWeatherSnapshot(helper, returning, .15F, .75F);
            helper.assertValueEqual(returning.size(), 2, "A level snapshot below native rain's boolean threshold sends exactly the two missing strengths");
            level.setDayTime(30L * 24000L + 18000L); level.setRainLevel(0F);
            level.getServer().getPlayerList().sendLevelInfo(player, level);
            helper.assertTrue(weatherPackets(channel).isEmpty(), "Ordinary dry nights do not gain special lunar weather packets");
        } finally {
            level.getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    private static void assertWeatherSnapshot(GameTestHelper helper, List<ClientboundGameEventPacket> packets, float rain, float thunder) {
        helper.assertTrue(packets.stream().anyMatch(packet -> packet.getEvent() == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE
                        && Math.abs(packet.getParam() - rain) < .0001F), "The initial snapshot includes the current rain interpolation rather than forcing a visual jump");
        helper.assertTrue(packets.stream().anyMatch(packet -> packet.getEvent() == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE
                        && Math.abs(packet.getParam() - thunder) < .0001F), "The initial snapshot includes dry thunder even though native isRaining is false");
    }

    private static List<ClientboundGameEventPacket> weatherPackets(EmbeddedChannel channel) {
        channel.runPendingTasks(); channel.flushOutbound();
        List<ClientboundGameEventPacket> result = new ArrayList<>();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            if (message instanceof ClientboundGameEventPacket packet && (packet.getEvent() == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE
                    || packet.getEvent() == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE
                    || packet.getEvent() == ClientboundGameEventPacket.START_RAINING
                    || packet.getEvent() == ClientboundGameEventPacket.STOP_RAINING)) result.add(packet);
        }
        return result;
    }

    private static final class ChunkRandomSnapshot implements AutoCloseable {
        private final ServerLevel level;
        private final Field positionRandom;
        private final AtomicLong randomSeed;
        private final int oldPosition;
        private final long oldSeed;
        ChunkRandomSnapshot(ServerLevel level) {
            this.level = level;
            try {
                positionRandom = Level.class.getDeclaredField("randValue"); positionRandom.setAccessible(true);
                Field seed = LegacyRandomSource.class.getDeclaredField("seed"); seed.setAccessible(true);
                randomSeed = (AtomicLong)seed.get(level.getRandom());
                oldPosition = positionRandom.getInt(level); oldSeed = randomSeed.get();
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        void forceLightningDraw() {
            try {
                // Native getBlockRandomPos's independent LCG selects the isolated chunk center (8,8).
                positionRandom.setInt(level, 3819);
                randomSeed.set((5781L ^ 25214903917L) & 281474976710655L);
            } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
        @Override public void close() {
            try { positionRandom.setInt(level, oldPosition); randomSeed.set(oldSeed); }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
    }

    private static final class WeatherSnapshot implements AutoCloseable {
        private final ServerLevel level;
        private final ServerLevelData data;
        private final long time;
        private final int clearTime, rainTime, thunderTime;
        private final boolean raining, thundering;
        private final float rain, oldRain, thunder, oldThunder;
        WeatherSnapshot(ServerLevel level) {
            this.level = level; data = (ServerLevelData)level.getLevelData(); time = level.getDayTime();
            clearTime = data.getClearWeatherTime(); rainTime = data.getRainTime(); thunderTime = data.getThunderTime();
            raining = data.isRaining(); thundering = data.isThundering();
            rain = level.rainLevel; oldRain = level.oRainLevel; thunder = level.thunderLevel; oldThunder = level.oThunderLevel;
        }
        @Override public void close() {
            level.setDayTime(time); data.setClearWeatherTime(clearTime); data.setRainTime(rainTime); data.setThunderTime(thunderTime);
            data.setRaining(raining); data.setThundering(thundering);
            level.rainLevel = rain; level.oRainLevel = oldRain; level.thunderLevel = thunder; level.oThunderLevel = oldThunder;
            level.updateSkyBrightness();
        }
    }
}
